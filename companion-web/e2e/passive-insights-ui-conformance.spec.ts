import { expect, test, type Page, type Route } from '@playwright/test';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import type { CatalogTheme } from '../src/models';
import { deriveSemanticTheme, semanticThemeCssVariables } from '../src/themeContrast';
import { baseState, battle, boundedForecast, catalog, emptyArea, exactForecast, specimens, themeControls } from './passive-insights-ui-fixture';

interface RouteControl {
  id: string;
  family: 'baseline' | 'party-analysis' | 'area-guide' | 'trainer-progress' | 'specimens' | 'damage-forecast' | 'challenges';
  state: string;
  pattern: 'grid' | 'paper' | 'map';
  scrollOwner: string | null;
  baseline: string;
}

interface ConformanceManifest {
  schemaVersion: number;
  viewport: { width: number; height: number };
  fontScales: number[];
  themes: { id: string; kind: 'GAME' | 'FIXED'; control: string }[];
  routes: RouteControl[];
  expectedMatrixRows: number;
}

interface RowEvidence {
  routeId: string;
  routeFamily: string;
  state: string;
  themeId: string;
  fontScale: number;
  pattern: string;
  expectedScrollOwner: string | null;
  text: { count: number; minimumPx: number; maximumPx: number; averagePx: number; smallest: string[] };
  computedStyles: Record<string, Record<string, string> | null>;
  checks: {
    bodyOverflow: boolean;
    missingAccessibleNames: string[];
    missingNonColorStatusCues: string[];
    undersizedTouchTargets: string[];
    contrastFailures: string[];
    clippedText: string[];
    diagnosticLeaks: string[];
    activeScrollOwners: string[];
    expectedScrollOwnerVisible: boolean;
    focusVisible: boolean;
  };
  screenshot: { path: string; sha256: string };
}

type SpecimenMode = 'MULTIPLE' | 'SINGLE' | 'EMPTY' | 'UNAVAILABLE' | 'LOADING';

const manifestPath = join(process.cwd(), '..', 'docs', 'reports', 'passive-insights-progress', 'ui-conformance-route-matrix.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as ConformanceManifest;
const artifactRoot = process.env.DUALDEX_UI_CONFORMANCE_DIR ?? 'D:/Temp/dualdex-stage7-ui-conformance';
const tinyPng = readFileSync(join(process.cwd(), '..', 'app', 'src', 'main', 'assets', 'icon-lowest.png'));
const diagnosticPattern = /\b(?:ROM identity|CRC32|SHA-?256|parser|capability|provenance|raw offset|memory address|compiled source|SaveRAM|recovery source|cache invalidation|NO_CONTENT|UNVERIFIED)\b/i;
const themeTokenNames = ['field', 'field-pattern', 'header', 'header-shadow', 'menu', 'menu-shadow', 'panel', 'border', 'text', 'text-shadow', 'accent', 'accent-text'];
const themeTokenKeys = ['field', 'fieldPattern', 'header', 'headerShadow', 'menu', 'menuShadow', 'panel', 'border', 'text', 'textShadow', 'accent', 'accentText'] as const;
const semanticTokenNames = Object.keys(semanticThemeCssVariables(deriveSemanticTheme(catalog.theme.tokens as CatalogTheme['tokens'])));

test('freezes the complete Stage 7 route, theme, and font-scale contract', () => {
  const routeIds = new Set(manifest.routes.map(route => route.id));
  expect(manifest.schemaVersion).toBe(1);
  expect(manifest.viewport).toEqual({ width: 1024, height: 768 });
  expect(manifest.fontScales).toEqual([0.85, 1, 1.35]);
  expect(manifest.themes.map(theme => theme.id)).toEqual([
    'game-gen1', 'game-gen2', 'game-gen3', 'game-modern-emerald', 'game-unbound', 'game-odyssey',
    'light', 'dark', 'high-contrast',
  ]);
  expect(routeIds).toEqual(new Set([
    'baseline-pokedex', 'baseline-party', 'baseline-trainer-card', 'baseline-atlas', 'baseline-battle',
    'party-analysis-summary', 'party-analysis-comparison', 'party-analysis-linked-detail',
    'area-guide-collapsed', 'area-guide-populated', 'area-guide-empty',
    'progress-metrics', 'progress-challenges', 'progress-challenges-empty', 'progress-timeline', 'progress-timeline-empty',
    'specimens-loading', 'specimens-unavailable', 'specimens-empty', 'specimens-single', 'specimens-multiple', 'specimens-detail',
    'damage-exact', 'damage-bounded', 'damage-withheld', 'damage-unavailable',
    'challenge-expansion-list', 'challenge-expansion-detail',
  ]));
  expect(manifest.expectedMatrixRows).toBe(manifest.routes.length * manifest.themes.length * manifest.fontScales.length);
  expect(manifest.routes.every(route => route.baseline.length > 0)).toBe(true);
  expect(manifest.routes.filter(route => route.scrollOwner != null).every(route => route.scrollOwner!.startsWith('.'))).toBe(true);
});

for (const routeControl of manifest.routes) {
  test(`${routeControl.id} renders every required theme and font-scale row`, async ({ page }) => {
    await page.setViewportSize(manifest.viewport);
    const harness = await installHarness(page);
    await harness.show(routeControl.id);
    const rows: RowEvidence[] = [];
    const failures: string[] = [];

    for (const theme of manifest.themes) {
      for (const fontScale of manifest.fontScales) {
        await applyVisualControl(page, theme.id, fontScale);
        const screenshotPath = join(artifactRoot, 'screenshots', routeControl.id, `${theme.id}-${fontScaleName(fontScale)}.png`);
        mkdirSync(dirname(screenshotPath), { recursive: true });
        const evidence = await measureRow(page, routeControl, theme.id, fontScale);
        await page.screenshot({ path: screenshotPath });
        const screenshotSha = createHash('sha256').update(readFileSync(screenshotPath)).digest('hex');
        rows.push({ ...evidence, screenshot: { path: screenshotPath.replaceAll('\\', '/'), sha256: screenshotSha } });

        const row = `${routeControl.id}/${theme.id}/${fontScale}`;
        if (evidence.text.count === 0) failures.push(`${row}: no visible copy`);
        if (evidence.text.minimumPx < 11.2) failures.push(`${row}: ${evidence.text.smallest.join(' | ')}`);
        if (evidence.text.averagePx < 12) failures.push(`${row}: average font ${evidence.text.averagePx}px`);
        if (evidence.checks.bodyOverflow) failures.push(`${row}: body overflow`);
        if (evidence.checks.missingAccessibleNames.length) failures.push(`${row}: unnamed ${evidence.checks.missingAccessibleNames.join(' | ')}`);
        if (evidence.checks.missingNonColorStatusCues.length) failures.push(`${row}: color-only status ${evidence.checks.missingNonColorStatusCues.join(' | ')}`);
        if (evidence.checks.undersizedTouchTargets.length) failures.push(`${row}: touch ${evidence.checks.undersizedTouchTargets.join(' | ')}`);
        if (evidence.checks.contrastFailures.length) failures.push(`${row}: contrast ${evidence.checks.contrastFailures.join(' | ')}`);
        if (evidence.checks.clippedText.length) failures.push(`${row}: clipping ${evidence.checks.clippedText.join(' | ')}`);
        if (evidence.checks.diagnosticLeaks.length) failures.push(`${row}: diagnostics ${evidence.checks.diagnosticLeaks.join(' | ')}`);
        if (evidence.checks.activeScrollOwners.length > 1) failures.push(`${row}: nested scroll ${evidence.checks.activeScrollOwners.join(' | ')}`);
        if (evidence.checks.activeScrollOwners.length === 1 && routeControl.scrollOwner && !evidence.checks.activeScrollOwners[0].split('.').includes(routeControl.scrollOwner.slice(1))) failures.push(`${row}: unexpected scroll owner ${evidence.checks.activeScrollOwners[0]}`);
        if (!evidence.checks.expectedScrollOwnerVisible) failures.push(`${row}: missing scroll owner ${routeControl.scrollOwner}`);
        if (!evidence.checks.focusVisible) failures.push(`${row}: no visible keyboard focus`);
      }
    }

    const output = join(artifactRoot, 'rows', `${routeControl.id}.json`);
    mkdirSync(dirname(output), { recursive: true });
    writeFileSync(output, `${JSON.stringify(rows, null, 2)}\n`);
    expect(rows).toHaveLength(manifest.themes.length * manifest.fontScales.length);
    expect(failures, failures.join('\n')).toEqual([]);
  });
}

async function installHarness(page: Page) {
  let serverState: Record<string, unknown> = structuredClone(baseState);
  let specimenMode: SpecimenMode = 'MULTIPLE';

  await page.route('**/api/bootstrap', route => json(route, { catalog, state: serverState }));
  await page.route('**/api/state?*', route => json(route, serverState));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: tinyPng }));
  await page.route('**/api/sprites/**', route => route.fulfill({ contentType: 'image/png', body: tinyPng }));
  await page.route('**/api/trainer-assets/**', route => route.fulfill({ contentType: 'image/png', body: tinyPng }));
  await page.route('**/api/specimens?*', route => {
    if (specimenMode === 'LOADING') return;
    if (specimenMode === 'UNAVAILABLE') return route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
    const body = specimenMode === 'EMPTY' ? { ...specimens, specimens: [] }
      : specimenMode === 'SINGLE' ? { ...specimens, specimens: specimens.specimens.slice(0, 1) }
        : specimens;
    return json(route, body);
  });
  await page.route('**/api/actions', async route => {
    const action = route.request().postDataJSON() as Record<string, unknown>;
    const current = serverState as any;
    if (action.type === 'TAB') current.battleTab = action.tab;
    if (action.type === 'SCREEN') current.screen = action.screen;
    if (action.type === 'OPEN_SPECIES') { current.screen = 'DETAIL'; current.selectedSpeciesId = action.speciesId; }
    if (action.type === 'TRAINER_DESTINATION') current.trainerProgress.selectedDestination = action.value;
    if (action.type === 'PROGRESS_SECTION') current.trainerProgress.selectedSection = action.value;
    if (action.type === 'BACK') current.screen = current.priorScreen ?? 'POKEDEX';
    current.version += 1;
    await json(route, current);
  });

  return {
    show: async (routeId: string) => {
      const scenario = scenarioFor(routeId);
      serverState = scenario.state;
      specimenMode = scenario.specimenMode;
      await page.goto('/');
      await expect(page.locator('.production-device')).toBeVisible();
      await expect(page.locator('.screen:not(.welcome-screen)')).toBeVisible();
      await scenario.enter(page);
      await expect(page.locator('.screen')).toBeVisible();
    },
  };
}

function scenarioFor(routeId: string): { state: Record<string, any>; specimenMode: SpecimenMode; enter: (page: Page) => Promise<void> } {
  const state = structuredClone(baseState) as Record<string, any>;
  let specimenMode: SpecimenMode = 'MULTIPLE';
  let enter = async (_page: Page) => {};
  const setProgress = (section: string, empty = false) => {
    state.screen = 'TRAINER';
    state.trainerProgress.selectedDestination = 'PROGRESS';
    state.trainerProgress.selectedSection = section;
    if (empty && section === 'CHALLENGES') { state.trainerProgress.challenges = []; state.trainerProgress.challengeSummary = { completed: 0, applicable: 0, completionPercent: null }; }
    if (empty && section === 'TIMELINE') state.trainerProgress.timeline = [];
  };
  const enterMap = async (page: Page, openGuide: boolean) => {
    await page.getByRole('button', { name: 'Open Map' }).click();
    await expect(page.locator('.map-screen')).toBeVisible();
    if (openGuide) { await page.getByRole('button', { name: 'Area Guide' }).click(); await expect(page.getByRole('complementary', { name: 'Area guide' })).toBeVisible(); }
  };
  const enterSpecimens = async (page: Page, detail: boolean) => {
    await page.getByRole('tab', { name: 'MORE' }).click();
    await page.getByRole('button', { name: 'VIEW SPECIMENS' }).click();
    await expect(page.locator('.specimens-screen')).toBeVisible();
    if (detail) { await page.getByRole('button', { name: 'Open SPARK details' }).click(); await expect(page.getByRole('dialog', { name: 'SPARK details' })).toBeVisible(); }
  };

  switch (routeId) {
    case 'baseline-party': state.screen = 'PARTY'; break;
    case 'baseline-trainer-card': state.screen = 'TRAINER'; break;
    case 'baseline-atlas': enter = page => enterMap(page, false); break;
    case 'baseline-battle': state.screen = 'BATTLE'; state.battle = structuredClone(battle); break;
    case 'party-analysis-summary':
    case 'party-analysis-comparison': state.screen = 'PARTY'; enter = async page => { await page.getByRole('button', { name: 'Party Analysis' }).click(); await expect(page.locator('.party-analysis-content')).toBeVisible(); }; break;
    case 'party-analysis-linked-detail': state.screen = 'PARTY'; enter = async page => { await page.getByRole('button', { name: 'Party Analysis' }).click(); await page.getByRole('button', { name: 'Open SPARK details' }).first().click(); await expect(page.getByRole('dialog', { name: 'SPARK details' })).toBeVisible(); }; break;
    case 'area-guide-collapsed': enter = page => enterMap(page, false); break;
    case 'area-guide-populated': enter = page => enterMap(page, true); break;
    case 'area-guide-empty': state.areaGuide.areas = [structuredClone(emptyArea)]; enter = page => enterMap(page, true); break;
    case 'progress-metrics': setProgress('METRICS'); break;
    case 'progress-challenges':
    case 'challenge-expansion-list':
    case 'challenge-expansion-detail': setProgress('CHALLENGES'); break;
    case 'progress-challenges-empty': setProgress('CHALLENGES', true); break;
    case 'progress-timeline': setProgress('TIMELINE'); break;
    case 'progress-timeline-empty': setProgress('TIMELINE', true); break;
    case 'specimens-loading': specimenMode = 'LOADING'; state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, false); break;
    case 'specimens-unavailable': specimenMode = 'UNAVAILABLE'; state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, false); break;
    case 'specimens-empty': specimenMode = 'EMPTY'; state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, false); break;
    case 'specimens-single': specimenMode = 'SINGLE'; state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, false); break;
    case 'specimens-multiple': state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, false); break;
    case 'specimens-detail': state.screen = 'DETAIL'; state.selectedSpeciesId = 25; enter = page => enterSpecimens(page, true); break;
    case 'damage-exact': state.screen = 'BATTLE'; state.battle = { ...structuredClone(battle), damageForecast: exactForecast() }; state.battleTab = 'ATTACK'; break;
    case 'damage-bounded': state.screen = 'BATTLE'; state.battle = { ...structuredClone(battle), damageForecast: boundedForecast() }; state.battleTab = 'ATTACK'; break;
    case 'damage-withheld': state.screen = 'BATTLE'; state.battle = { ...structuredClone(battle), damageForecast: null, effectivenessKnown: false, effectiveness: null }; state.battleTab = 'ATTACK'; break;
    case 'damage-unavailable': state.screen = 'BATTLE'; state.battle = { ...structuredClone(battle), damageForecast: null }; state.battleTab = 'ATTACK'; break;
  }
  return { state, specimenMode, enter };
}

async function applyVisualControl(page: Page, themeId: string, fontScale: number) {
  const rawTokens = themeControls[themeId as keyof typeof themeControls] ?? null;
  const semanticTokens = rawTokens == null
    ? null
    : semanticThemeCssVariables(deriveSemanticTheme(Object.fromEntries(
      themeTokenKeys.map((key, index) => [key, rawTokens[index]]),
    ) as CatalogTheme['tokens']));
  await page.locator('.production-device').evaluate((node, control) => {
    const element = node as HTMLElement;
    element.style.setProperty('--font-scale', String(control.fontScale));
    for (const token of control.tokenNames) element.style.removeProperty(`--theme-${token}`);
    for (const token of control.semanticTokenNames) element.style.removeProperty(token);
    if (control.themeId.startsWith('game-')) {
      element.dataset.theme = 'game';
      element.dataset.contrast = 'normal';
      control.tokens!.forEach((value, index) => element.style.setProperty(`--theme-${control.tokenNames[index]}`, value));
      Object.entries(control.semanticTokens!).forEach(([name, value]) => element.style.setProperty(name, value));
    } else if (control.themeId === 'dark') {
      element.dataset.theme = 'dark'; element.dataset.contrast = 'normal';
    } else if (control.themeId === 'high-contrast') {
      element.dataset.theme = 'light'; element.dataset.contrast = 'high';
    } else {
      element.dataset.theme = 'light'; element.dataset.contrast = 'normal';
    }
  }, { themeId, fontScale, tokenNames: themeTokenNames, tokens: rawTokens, semanticTokenNames, semanticTokens });
  await page.locator('.screen').evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => resolve())));
}

async function measureRow(page: Page, route: RouteControl, themeId: string, fontScale: number): Promise<Omit<RowEvidence, 'screenshot'>> {
  await page.locator('.screen').evaluate(root => {
    const element = root as HTMLElement;
    element.tabIndex = -1;
    element.focus();
    element.removeAttribute('tabindex');
  });
  await page.keyboard.press('Tab');
  const measured = await page.locator('.screen').evaluate((root, args) => {
    const visible = (element: HTMLElement) => { const style = getComputedStyle(element); const rect = element.getBoundingClientRect(); return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0 && rect.width > 0 && rect.height > 0; };
    const label = (element: Element) => `${element.tagName.toLowerCase()}${element.className ? `.${String(element.className).trim().replace(/\s+/g, '.')}` : ''}:${(element.textContent ?? '').trim().slice(0, 60)}`;
    const textElements = new Set<HTMLElement>();
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) if (node.textContent?.trim() && node.parentElement instanceof HTMLElement && visible(node.parentElement)) textElements.add(node.parentElement);
    root.querySelectorAll<HTMLElement>('input, select, textarea').forEach(element => { if (visible(element)) textElements.add(element); });
    const fonts = [...textElements].map(element => ({ value: Number.parseFloat(getComputedStyle(element).fontSize), label: label(element) })).filter(item => Number.isFinite(item.value));
    const fontValues = fonts.map(item => item.value); const round = (value: number) => Math.round(value * 100) / 100;
    const controls = [...root.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea')].filter(visible);
    const missingAccessibleNames = controls.filter(element => { const labelled = element instanceof HTMLInputElement && [...(element.labels ?? [])].some(item => item.textContent?.trim()); return !labelled && !element.getAttribute('aria-label')?.trim() && !(element.textContent ?? '').trim() && !element.getAttribute('title')?.trim(); }).map(label);
    const semanticStatuses = [...root.querySelectorAll<HTMLElement>('.rarity-stars, .party-slot-gender, .party-status-art, .effect-result')].filter(visible);
    const missingNonColorStatusCues = semanticStatuses.filter(element => !element.getAttribute('aria-label')?.trim() && !(element.textContent ?? '').trim() && !element.getAttribute('title')?.trim()).map(label);
    const touchRect = (element: HTMLElement) => element.closest('label')?.getBoundingClientRect() ?? element.getBoundingClientRect();
    const undersizedTouchTargets = controls.filter(element => { const rect = touchRect(element); return rect.width < 43.99 || rect.height < 43.99; }).map(element => { const rect = touchRect(element); return `${label(element)} (${round(rect.width)}×${round(rect.height)})`; });
    const clippedText = [...textElements].filter(element => { const style = getComputedStyle(element); const clipped = element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1; const canClip = ['hidden', 'clip'].includes(style.overflowX) || ['hidden', 'clip'].includes(style.overflowY); const intentional = style.textOverflow === 'ellipsis' || element.classList.contains('rarity-star-fill'); return clipped && canClip && !intentional; }).map(label);
    type Color = { r: number; g: number; b: number; a: number };
    const parseColor = (value: string): Color | null => { const parts = value.match(/[\d.]+/g)?.map(Number) ?? []; if (parts.length < 3) return null; const srgb = value.startsWith('color(srgb'); return { r: srgb ? parts[0] * 255 : parts[0], g: srgb ? parts[1] * 255 : parts[1], b: srgb ? parts[2] * 255 : parts[2], a: parts[3] ?? 1 }; };
    const composite = (foreground: Color, background: Color): Color => { const a = foreground.a + background.a * (1 - foreground.a); return { r: (foreground.r * foreground.a + background.r * background.a * (1 - foreground.a)) / a, g: (foreground.g * foreground.a + background.g * background.a * (1 - foreground.a)) / a, b: (foreground.b * foreground.a + background.b * background.a * (1 - foreground.a)) / a, a }; };
    const backgroundFor = (element: HTMLElement): Color => { const layers: Array<Color | null> = []; for (let current: HTMLElement | null = element; current; current = current.parentElement) layers.push(parseColor(getComputedStyle(current).backgroundColor)); return layers.reverse().reduce<Color>((background, layer) => layer ? composite(layer, background) : background, { r: 255, g: 255, b: 255, a: 1 }); };
    const luminance = ({ r, g, b }: { r: number; g: number; b: number }) => [r, g, b].map(channel => { const normalized = channel / 255; return normalized <= .03928 ? normalized / 12.92 : ((normalized + .055) / 1.055) ** 2.4; }).reduce((sum, channel, index) => sum + channel * [.2126, .7152, .0722][index], 0);
    const contrastFailures = [...textElements].flatMap(element => { const style = getComputedStyle(element); if (element.closest('[aria-hidden="true"], .game-time-contrast-plate, .rarity-stars')) return []; const rawForeground = parseColor(style.color); if (!rawForeground) return []; const background = backgroundFor(element); const foreground = rawForeground.a < 1 ? composite(rawForeground, background) : rawForeground; const l1 = luminance(foreground); const l2 = luminance(background); const ratio = (Math.max(l1, l2) + .05) / (Math.min(l1, l2) + .05); const size = Number.parseFloat(style.fontSize); const bold = Number.parseInt(style.fontWeight, 10) >= 700; const required = size >= 24 || (bold && size >= 18.66) ? 3 : 4.5; return ratio + .05 < required ? [`${label(element)} (${round(ratio)}:1)`] : []; });
    const scrollOwners = [...root.querySelectorAll<HTMLElement>('*')].filter(element => { const overflow = getComputedStyle(element).overflowY; return visible(element) && (overflow === 'auto' || overflow === 'scroll') && element.scrollHeight > element.clientHeight + 1; }).map(element => element.className ? `.${String(element.className).trim().replace(/\s+/g, '.')}` : element.tagName.toLowerCase());
    const styleOf = (selector: string) => { const screenRoot = root as HTMLElement; const element = screenRoot.matches(selector) ? screenRoot : screenRoot.querySelector<HTMLElement>(selector); if (!element || !visible(element)) return null; const style = getComputedStyle(element); return { color: style.color, backgroundColor: style.backgroundColor, backgroundImage: style.backgroundImage, borderTopColor: style.borderTopColor, borderBottomColor: style.borderBottomColor, boxShadow: style.boxShadow, outlineColor: style.outlineColor, outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth, fontSize: style.fontSize, minHeight: style.minHeight }; };
    const focusTarget = document.activeElement instanceof HTMLElement && root.contains(document.activeElement) ? document.activeElement : null; const focusStyle = focusTarget ? getComputedStyle(focusTarget) : null;
    return {
      text: { count: fontValues.length, minimumPx: round(Math.min(...fontValues)), maximumPx: round(Math.max(...fontValues)), averagePx: round(fontValues.reduce((sum, value) => sum + value, 0) / fontValues.length), smallest: fonts.sort((left, right) => left.value - right.value).slice(0, 6).map(item => `${round(item.value)}px ${item.label}`) },
      computedStyles: { page: styleOf('.screen'), header: styleOf('.app-header'), separator: styleOf('.app-header'), panel: styleOf('.paper-panel, .progress-panel, .party-analysis-section, .specimen-card, .damage-forecast, .area-guide-drawer'), menu: styleOf('.segmented, .trainer-progress-tabs, .browse-tools'), card: styleOf('.party-slot:not(.empty), .trainer-card-shell, .challenge-card, .specimen-card, .attack-card'), text: styleOf('h1, h2, strong, p, small'), control: styleOf('button'), icon: styleOf('.header-action, .map-control'), focus: styleOf(':focus-visible'), semantic: styleOf('.party-hp-fill, .party-exp-fill, .rarity-stars, .type-chip, .effect-result'), pattern: styleOf('.trainer-card-content, .trainer-progress-content, .party-analysis-content, .specimens-content, .battle-content, .area-guide-drawer') },
      checks: { bodyOverflow: document.documentElement.scrollWidth > innerWidth + 1 || document.documentElement.scrollHeight > innerHeight + 1, missingAccessibleNames, missingNonColorStatusCues, undersizedTouchTargets, contrastFailures, clippedText, diagnosticLeaks: [] as string[], activeScrollOwners: scrollOwners, expectedScrollOwnerVisible: args.scrollOwner == null || Boolean(root.querySelector(args.scrollOwner)), focusVisible: Boolean(focusTarget && focusTarget.matches(':focus-visible') && (focusStyle?.outlineStyle !== 'none' || focusStyle?.boxShadow !== 'none')) },
    };
  }, { scrollOwner: route.scrollOwner });
  measured.checks.diagnosticLeaks = await page.locator('.screen').evaluate((root, source) => {
    const pattern = new RegExp(source, 'i');
    const copy = new Set((root.textContent ?? '').split(/\n+/).map(item => item.trim()).filter(Boolean));
    root.querySelectorAll<HTMLElement>('*').forEach(element => ['aria-label', 'title', 'alt', 'placeholder'].forEach(attribute => { const value = element.getAttribute(attribute)?.trim(); if (value) copy.add(value); }));
    return [...copy].filter(value => pattern.test(value));
  }, diagnosticPattern.source);
  return { routeId: route.id, routeFamily: route.family, state: route.state, themeId, fontScale, pattern: route.pattern, expectedScrollOwner: route.scrollOwner, ...measured };
}

function json(route: Route, body: unknown) { return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) }); }
function fontScaleName(value: number) { return `${Math.round(value * 100)}pct`; }
