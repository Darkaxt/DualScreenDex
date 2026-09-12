import { expect, test, type Page, type Route } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { baseState, battle, catalog } from './passive-insights-ui-fixture';

type LocaleCase = {
  setting: 'EN' | 'FR' | 'DE' | 'IT' | 'ES' | 'PSEUDO';
  language: string;
  settingsLabel: string;
};

type RouteCase = {
  label: string;
  state: Record<string, unknown>;
  open?: (page: Page) => Promise<void>;
};

const thorViewport = { width: 538, height: 445 };
const placeholder = readFileSync(join(process.cwd(), '..', 'app', 'src', 'main', 'assets', 'icon-lowest.png'));
const locales: LocaleCase[] = [
  { setting: 'EN', language: 'en', settingsLabel: 'SETTINGS' },
  { setting: 'FR', language: 'fr', settingsLabel: 'PARAMÈTRES' },
  { setting: 'DE', language: 'de', settingsLabel: 'EINSTELLUNGEN' },
  { setting: 'IT', language: 'it', settingsLabel: 'IMPOSTAZIONI' },
  { setting: 'ES', language: 'es', settingsLabel: 'AJUSTES' },
  { setting: 'PSEUDO', language: 'en', settingsLabel: '⟦SEeTTIiNGS⟧' },
];
const routes: RouteCase[] = [
  { label: 'Pokédex Browse', state: baseState },
  { label: 'Pokédex Detail', state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 } },
  { label: 'Party', state: { ...baseState, screen: 'PARTY' } },
  { label: 'Trainer', state: { ...baseState, screen: 'TRAINER' } },
  { label: 'Battle', state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' } },
  { label: 'Settings', state: { ...baseState, screen: 'SETTINGS' } },
  { label: 'Setup', state: { ...baseState, screen: 'SETUP' } },
  {
    label: 'Map',
    state: baseState,
    open: async page => {
      await page.locator('.map-action').click();
      await expect(page.locator('.map-screen')).toBeVisible();
    },
  },
  {
    label: 'Area Guide',
    state: baseState,
    open: async page => {
      await page.locator('.map-action').click();
      await page.locator('.map-area-guide-control').click();
      await expect(page.locator('.area-guide-drawer')).toBeVisible();
    },
  },
  {
    label: 'Party Analysis',
    state: { ...baseState, screen: 'PARTY' },
    open: async page => {
      await page.locator('.analysis-action').click();
      await expect(page.locator('.party-analysis-screen')).toBeVisible();
    },
  },
];

for (const locale of locales) {
  for (const fontScale of [0.85, 1, 1.35]) {
    test(`${locale.setting} fits every primary route at ${Math.round(fontScale * 100)}% text`, async ({ page }) => {
      test.setTimeout(90_000);
      await page.setViewportSize(thorViewport);
      const harness = await installHarness(page, locale, fontScale);

      for (const route of routes) {
        harness.state = withSettings(route.state, locale, fontScale);
        await page.goto(`/?localization=${locale.setting}-${fontScale}-${encodeURIComponent(route.label)}`);
        await expect(page.locator('.screen:not(.welcome-screen)')).toBeVisible();
        await route.open?.(page);
        if (locale.setting === 'PSEUDO') await applyPseudoExpansion(page);

        expect(await page.locator('html').getAttribute('lang'), route.label).toBe(locale.language);
        await expectThorContainment(page, `${locale.setting} ${route.label} at ${fontScale}`);
        if (route.label === 'Pokédex Browse') await expectVirtualRowsStaySynchronized(page);
      }

      harness.state = withSettings({ ...baseState, screen: 'SETTINGS' }, locale, fontScale);
      await page.goto(`/?localization=${locale.setting}-${fontScale}-focus`);
      const category = page.locator('.settings-category-row').first();
      await category.focus();
      await category.click();
      await page.locator('.back-action').click();
      await expect(category).toBeFocused();
      if (locale.setting === 'PSEUDO') await applyPseudoExpansion(page);
      await expect(page.locator('body')).toContainText(locale.settingsLabel);
    });
  }
}

function withSettings(state: Record<string, unknown>, locale: LocaleCase, fontScale: number): Record<string, unknown> {
  return {
    ...state,
    settings: {
      ...baseState.settings,
      ...(state.settings as Record<string, unknown> | undefined),
      interfaceLanguage: locale.setting === 'PSEUDO' ? 'EN' : locale.setting,
      fontScale,
    },
  };
}

async function expectThorContainment(page: Page, context: string): Promise<void> {
  const result = await page.locator('.screen').evaluate(screen => {
    const screenBounds = screen.getBoundingClientRect();
    const clippedControls = Array.from(screen.querySelectorAll<HTMLElement>('button, a[href], input, select'))
      .filter(element => {
        const bounds = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        const visible = style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0 &&
          bounds.width > 0 && bounds.height > 0 && bounds.right > 0 && bounds.bottom > 0 &&
          bounds.left < innerWidth && bounds.top < innerHeight;
        return visible && (bounds.left < screenBounds.left - 1 || bounds.right > screenBounds.right + 1);
      })
      .map(element => `${element.tagName.toLowerCase()}.${element.className}`);
    return {
      screenClientWidth: screen.clientWidth,
      screenScrollWidth: screen.scrollWidth,
      bodyClientWidth: document.body.clientWidth,
      bodyScrollWidth: document.body.scrollWidth,
      bodyClientHeight: document.body.clientHeight,
      bodyScrollHeight: document.body.scrollHeight,
      clippedControls,
    };
  });

  expect(result.screenScrollWidth, context).toBeLessThanOrEqual(result.screenClientWidth + 1);
  expect(result.bodyScrollWidth, context).toBeLessThanOrEqual(result.bodyClientWidth + 1);
  expect(result.bodyScrollHeight, context).toBeLessThanOrEqual(result.bodyClientHeight + 1);
  expect(result.clippedControls, context).toEqual([]);
}

async function expectVirtualRowsStaySynchronized(page: Page): Promise<void> {
  const rows = page.locator('.species-row');
  if (await rows.count() < 2) return;
  const first = await rows.nth(0).boundingBox();
  const second = await rows.nth(1).boundingBox();
  expect(first).not.toBeNull();
  expect(second).not.toBeNull();
  expect(first!.y + first!.height).toBeLessThanOrEqual(second!.y + 2);
  expect(Math.abs(first!.height - second!.height)).toBeLessThanOrEqual(1);
}

async function applyPseudoExpansion(page: Page): Promise<void> {
  await page.locator('.screen').evaluate(root => {
    const expand = (value: string) => `⟦${value.replace(/[aeiouAEIOU]/g, vowel => `${vowel}${vowel.toLowerCase()}`)}⟧`;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes: Text[] = [];
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      if (node.textContent?.trim()) nodes.push(node as Text);
    }
    nodes.forEach(node => { node.textContent = expand(node.textContent ?? ''); });
    root.querySelectorAll<HTMLElement>('[aria-label]').forEach(element => {
      const label = element.getAttribute('aria-label');
      if (label) element.setAttribute('aria-label', expand(label));
    });
  });
}

async function installHarness(
  page: Page,
  locale: LocaleCase,
  fontScale: number,
): Promise<{ state: Record<string, unknown> }> {
  const harness = { state: withSettings(baseState, locale, fontScale) };
  await page.route('**/api/bootstrap', route => json(route, { catalog, state: harness.state }));
  await page.route('**/api/state?*', route => json(route, harness.state));
  await page.route('**/api/sprites/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/actions', route => {
    const action = route.request().postDataJSON() as Record<string, unknown>;
    if (action.type === 'SCREEN') {
      harness.state = { ...harness.state, version: Number(harness.state.version ?? 0) + 1, screen: action.screen };
    }
    return json(route, harness.state);
  });
  return harness;
}

async function json(route: Route, body: unknown): Promise<void> {
  await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
}
