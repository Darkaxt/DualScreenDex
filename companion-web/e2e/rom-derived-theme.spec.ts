import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const fixturePath = process.env.DUALDEX_MAP_FIXTURE_PNG;
if (!fixturePath) throw new Error('Set DUALDEX_MAP_FIXTURE_PNG to a sanitized normalized map PNG');
const artifactDir = process.env.DUALDEX_THEME_EVIDENCE_DIR;
if (!artifactDir) throw new Error('Set DUALDEX_THEME_EVIDENCE_DIR for browser evidence');
const raster = readFileSync(fixturePath);
const rasterSha256 = createHash('sha256').update(raster).digest('hex');
const trainerFixturePath = process.env.DUALDEX_TRAINER_FIXTURE_PNG;
if (!trainerFixturePath) throw new Error('Set DUALDEX_TRAINER_FIXTURE_PNG to a trainer sprite PNG');
const trainerRaster = readFileSync(trainerFixturePath);

const theme = {
  method: 'MULTI_ASSET_QUANTIZATION',
  assetClasses: ['TRAINER', 'WORLD_MAP', 'LOCAL_MAP', 'SPECIES'],
  contrastCorrected: true,
  tokens: {
    field: '#0245e6', fieldPattern: '#205ae8', header: '#dcdc02', headerShadow: '#888801',
    menu: '#fcfcfc', menuShadow: '#929292', panel: '#fdfdfd', border: '#010101',
    text: '#030303', textShadow: '#000000', accent: '#356ffb', accentText: '#000000',
  },
};

const catalog = {
  hash: 'emerald-theme-browser-control', crc32: '1F1C08FB', family: 'EMERALD', platform: 'GBA', theme,
  rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
  species: [{
    id: 1, dex: 1, name: 'BULBASAUR', typeIds: [12], stats: { HP: 45, ATTACK: 49, DEFENSE: 49, SPEED: 45, 'SP. ATK': 65, 'SP. DEF': 65 },
    description: 'A strange seed was planted on its back at birth.', height: 7, weight: 69,
    learnset: [{ level: 7, moveId: 22 }], learnsets: { default: [{ level: 7, moveId: 22 }] },
    normalizedLearnsets: { default: [{ moveId: 22, initial: false, levels: [7], label: 'Lv 7' }] },
    moveAcquisitions: [], abilities: [{
      id: 65,
      name: 'OVERGROW',
      description: 'Powers up Grass-type moves.',
      mechanics: [
        { kind: 'ACTIVATION_THRESHOLD', label: 'Activation', value: 'HP ≤ 1/3', numerator: 1, denominator: 3 },
        { kind: 'MULTIPLIER', label: 'Power', value: 'Grass move power ×1.5', numerator: 3, denominator: 2 },
      ],
    }],
    evolutions: [{ targetSpeciesId: 2, targetName: 'IVYSAUR', methodId: 4, parameter: 16, condition: 'Level 16' }], hasSprite: false,
  }, {
    id: 2, dex: 2, name: 'IVYSAUR', typeIds: [12], stats: null, description: null, height: null, weight: null,
    learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false,
  }],
  moves: [{ id: 22, name: 'VINE WHIP', typeId: 12, category: 'PHYSICAL', power: 45, accuracy: 100, pp: 25, priority: 0, effectId: 0, description: 'Strikes with slender vines.' }],
  types: [{ id: 12, name: 'GRASS', foreground: '#ffffffff', background: '#78c850ff', border: '#4e8234ff' }],
  areas: [{ id: 161, baseAreaId: 16, name: 'Route 101 grass', methodId: 1, speciesIds: [1], windows: ['DAY'], slots: [{ speciesId: 1, minimumLevel: 2, maximumLevel: 3, weight: 50 }] }],
  balls: [], capabilities: {},
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [{ key: 'section-16', displayName: 'Route 101', baseAreaIds: [16], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] }],
  }],
  localMaps: [{ key: 'local/16', displayName: 'Route 101', baseAreaId: 16, pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/local%2F16.png' }],
};

const baseState = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  selectedPartySlot: 0, filter: 'ALL', selectedAreaId: null, selectedAreaIds: [], currentAreaIds: [161], currentAreaBaseId: 16,
  currentAreaName: 'Route 101', currentAreaSpeciesIds: [1], revealedAreaBaseIds: [16], observedAreaBaseIdsBySpecies: { 1: [16] },
  battleTab: 'ENTRY', settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'GAME', displayTarget: 'AUTO' },
  speciesState: { 1: { seen: true, caught: true, team: true, ballId: null }, 2: { seen: true, caught: false, team: false, ballId: null } },
  observedMoves: {}, catalogReady: true, catalogName: 'Pokemon Emerald.gba', error: null, activeRulesetId: 'default', rulesetAssumed: false,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  trainer: { name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 98765, playTimeHours: 12, playTimeMinutes: 34, dexSeen: 2, dexCaught: 1, stars: 2, avatarUrl: '/api/trainer-assets/trainer%2Favatar%2Ffemale.png', badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index < 2, imageUrl: null })) },
  party: [{ slot: 0, occupied: true, speciesId: 1, speciesName: 'BULBASAUR', spriteUrl: null, typeIds: [12], nickname: 'BULBASAUR', level: 8, isEgg: false, gender: 'MALE', nature: 'Hardy', abilityId: 65, abilityName: 'OVERGROW', heldItemId: null, heldItemName: null, currentHp: 21, maximumHp: 25, status: null, experienceProgress: .5, stats: { HP: 25, ATTACK: 13, DEFENSE: 13, SPEED: 12, 'SP. ATK': 16, 'SP. DEF': 16 }, moves: [{ slot: 0, moveId: 22, name: 'VINE WHIP', currentPp: 24, maximumPp: 25 }] }],
  battle: null,
};

test('ROM-derived GAME theme remains stable across companion screens and fixed alternatives', async ({ page }) => {
  mkdirSync(artifactDir, { recursive: true });
  let serverState: Record<string, unknown> = { ...baseState };
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog, state: serverState }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) }));
  await page.route('**/api/actions', async route => {
    const request = route.request().postDataJSON() as Record<string, unknown>;
    if (request.type === 'TAB') serverState = { ...serverState, battleTab: request.tab };
    if (request.type === 'SCREEN') serverState = { ...serverState, screen: request.screen };
    if (request.type === 'BACK') serverState = { ...serverState, screen: serverState.priorScreen ?? 'POKEDEX' };
    serverState = { ...serverState, version: Number(serverState.version ?? 1) + 1 };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) });
  });
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: raster }));
  await page.route('**/api/trainer-assets/**', route => route.fulfill({ contentType: 'image/png', body: trainerRaster }));
  await page.route('**/api/diagnostics?*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({
    romName: baseState.catalogName, sha256: catalog.hash, crc32: catalog.crc32, family: catalog.family, platform: catalog.platform,
    activeRulesetId: 'default', rulesetAssumed: false, rulesets: catalog.rulesets,
    capabilities: [{ capability: 'SPECIES_CATALOG', status: 'AVAILABLE', confidence: 1, reasons: [], offset: 4096, validRecords: 2, totalRecords: 2, count: 2, recordSize: 28, elementSize: 1, reviewStatus: 'NONE' }],
    parserDiagnostics: [], species: null, move: null,
  }) }));
  const mapperState = { enabled: false, privacyAcknowledged: false, coreIdentity: null, contentIdentity: null, descriptors: [], captureLabel: null, completedBytes: 0, totalBytes: 0, snapshots: [], latestDiff: null, error: null };
  await page.route('**/api/mapper/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(mapperState) }));
  await page.route('**/api/mapper/actions', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(mapperState) }));

  const assertGameTheme = async () => {
    const shell = page.locator('.production-device');
    await expect(shell).toHaveAttribute('data-theme', 'game');
    for (const [token, value] of Object.entries(theme.tokens)) {
      const cssName = `--theme-${token.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`)}`;
      await expect.poll(() => shell.evaluate((node, name) => (node as HTMLElement).style.getPropertyValue(name), cssName)).toBe(value);
    }
    const themedScreen = page.locator('.screen:not(.map-screen):not(.welcome-screen):not(.battle-screen)').first();
    if (await themedScreen.isVisible()) {
      const backgroundImage = await themedScreen.evaluate(node => getComputedStyle(node).backgroundImage);
      expect(backgroundImage).toMatch(/(?:rgba\([^)]*,\s*0\.(?:1\d|2[0-5])\)|color\(srgb [^/]+\/ 0\.(?:1\d|2[0-5])\))/);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth && document.documentElement.scrollHeight <= window.innerHeight)).toBe(true);
  };
  const expectSurface = async (selector: string, expected: Partial<Record<'backgroundColor' | 'borderTopColor' | 'borderBottomColor' | 'color', string>>) => {
    const surface = page.locator(selector).first();
    await expect(surface).toBeVisible();
    await expect.poll(() => surface.evaluate((node, keys) => {
      const style = getComputedStyle(node);
      return Object.fromEntries(keys.map(key => [key, style[key]]));
    }, Object.keys(expected) as Array<keyof typeof expected>)).toEqual(expected);
  };
  const expectTypography = async (selector: string, expectedPx: number) => {
    const element = page.locator(selector).first();
    await expect(element).toBeVisible();
    await expect.poll(() => element.evaluate(node => Math.round(Number.parseFloat(getComputedStyle(node).fontSize) * 10) / 10)).toBe(expectedPx);
    await expect.poll(() => element.evaluate(node => node.scrollWidth <= node.clientWidth && node.scrollHeight <= node.clientHeight)).toBe(true);
  };
  const colors = {
    header: 'rgb(220, 220, 2)', menu: 'rgb(252, 252, 252)', panel: 'rgb(253, 253, 253)',
    border: 'rgb(1, 1, 1)', text: 'rgb(3, 3, 3)', accent: 'rgb(53, 111, 251)', accentText: 'rgb(0, 0, 0)',
  };
  const fontMetrics: Array<{ view: string; elements: number; minimumPx: number; maximumPx: number; averagePx: number }> = [];
  const capture = async (name: string) => {
    const metrics = await page.locator('.screen').first().evaluate(root => {
      const textElements = new Set<HTMLElement>();
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (!node.textContent?.trim() || !(node.parentElement instanceof HTMLElement)) continue;
        textElements.add(node.parentElement);
      }
      root.querySelectorAll<HTMLElement>('input, select, textarea').forEach(element => {
        const copy = element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement
          ? element.value || element.placeholder
          : element.textContent;
        if (copy?.trim()) textElements.add(element);
      });
      const values = [...textElements].flatMap(element => {
        const style = getComputedStyle(element);
        const bounds = element.getBoundingClientRect();
        if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0 || bounds.width === 0 || bounds.height === 0) return [];
        return [Number.parseFloat(style.fontSize)];
      }).filter(Number.isFinite);
      return {
        elements: values.length,
        minimumPx: Math.min(...values),
        maximumPx: Math.max(...values),
        averagePx: values.reduce((sum, value) => sum + value, 0) / values.length,
      };
    });
    const rounded = (value: number) => Math.round(value * 10) / 10;
    fontMetrics.push({
      view: name,
      elements: metrics.elements,
      minimumPx: rounded(metrics.minimumPx),
      maximumPx: rounded(metrics.maximumPx),
      averagePx: rounded(metrics.averagePx),
    });
    await page.screenshot({ path: join(artifactDir, `${name}.png`) });
  };
  const assertNoNormalDiagnostics = async () => {
    const text = await page.locator('.screen').evaluate(node => {
      const copy = node.cloneNode(true) as HTMLElement;
      copy.querySelectorAll('.mapper-setting').forEach(element => element.remove());
      return copy.innerText;
    });
    expect(text).not.toMatch(/Pokemon Emerald\.gba|1F1C08FB|CRC32|SHA-?256|\bEMERALD\b|ROM identity|game data layout|parser|capability|RESTART_REQUIRED|DISCONNECTED|NO_CONTENT|UNVERIFIED/i);
  };
  const show = async (name: string, state: Record<string, unknown>) => {
    serverState = { ...baseState, ...state, version: Number(serverState.version ?? 1) + 1 };
    await page.reload();
    await expect(page.locator('.production-device')).toBeVisible();
    await assertGameTheme();
    await assertNoNormalDiagnostics();
    await capture(name);
  };

  await page.goto('/');
  await assertGameTheme();
  await expectSurface('.browse-tools', { backgroundColor: colors.menu });
  await expectSurface('.filter-strip button:not(.active)', { backgroundColor: colors.panel, borderTopColor: colors.border });
  await expect.poll(() => page.locator('.filter-strip').evaluate(node => getComputedStyle(node).gridTemplateColumns.split(' ').length)).toBe(4);
  await expectSurface('.species-row', { backgroundColor: colors.panel, color: colors.text });
  await expectTypography('.search-box span', 12);
  await expectTypography('.species-number', 12);
  await expectTypography('.species-row-types .type-chip', 11.4);
  await expect.poll(() => page.locator('.species-list').evaluate(node => getComputedStyle(node).gridTemplateColumns.split(' ').length)).toBe(3);
  await expect.poll(() => page.locator('.species-row').first().evaluate(node => {
    const row = node.getBoundingClientRect();
    const portrait = node.querySelector('.pokedex-avatar')!.getBoundingClientRect();
    return { rowHeight: row.height, portraitWidth: portrait.width, portraitHeight: portrait.height };
  })).toEqual({ rowHeight: 94, portraitWidth: 76, portraitHeight: 76 });
  await expect(page.locator('.species-row-types .type-chip').first()).toHaveText('GRASS');
  await expect.poll(() => page.locator('.caught-avatar-badge').first().evaluate(node => {
    const badge = node.getBoundingClientRect();
    const mark = node.querySelector('.ball-mark')!.getBoundingClientRect();
    return { badgeWidth: badge.width, badgeHeight: badge.height, markWidth: mark.width, markHeight: mark.height };
  })).toEqual({ badgeWidth: 22, badgeHeight: 22, markWidth: 15, markHeight: 15 });
  await capture('pokedex');
  await show('detail-entry', { screen: 'DETAIL', selectedSpeciesId: 1 });
  await expectSurface('.identity-card', { backgroundColor: colors.menu, color: colors.text });
  await expectSurface('.paper-panel', { backgroundColor: colors.panel, borderTopColor: colors.border });
  await expect(page.getByRole('img', { name: 'Height comparison for BULBASAUR: 0.7 m beside a 1.7 m person' })).toBeVisible();
  await expect.poll(() => page.locator('.height-ruler').evaluate(node => {
    const ruler = node.getBoundingClientRect();
    const person = node.querySelector('.height-person')!.getBoundingClientRect();
    const pokemon = node.querySelector('.height-pokemon')!.getBoundingClientRect();
    const rounded = (value: number) => Math.round(value * 100) / 100;
    return {
      personShare: rounded(person.height / ruler.height),
      personWidthShare: rounded(person.width / ruler.width),
      pokemonShare: rounded(pokemon.height / ruler.height),
    };
  })).toEqual({ personShare: .79, personWidthShare: .2, pokemonShare: .33 });
  await page.getByRole('tab', { name: 'STATS' }).click();
  await expect(page.locator('.stat-list')).toBeVisible();
  await expectTypography('.stat-list > div', 15);
  await expectTypography('.stat-label small', 12);
  await expectTypography('.stat-range', 15);
  await expectTypography('.section-heading p:not(.eyebrow)', 13.5);
  await expectTypography('.range-note', 13.5);
  await capture('detail-stats');
  await page.getByRole('tab', { name: 'MOVES' }).click();
  await expect(page.getByRole('button', { name: /VINE WHIP/ })).toBeVisible();
  await capture('detail-moves');
  await page.getByRole('button', { name: /VINE WHIP/ }).click();
  await expect(page.locator('.move-detail-screen')).toBeVisible();
  await assertGameTheme();
  await expectSurface('.move-detail-content', { backgroundColor: 'rgba(0, 0, 0, 0)' });
  await expectSurface('.move-hero', { backgroundColor: colors.menu, borderBottomColor: colors.border, color: colors.text });
  await expectSurface('.move-hero > span', { color: colors.text });
  await expectTypography('.move-hero > strong', 15);
  await expectTypography('.move-hero > span', 12);
  await expectTypography('.move-detail-grid small', 12);
  await expectTypography('.move-detail-grid strong', 16.5);
  await expectTypography('.move-detail-content .entry-copy', 16.5);
  await assertNoNormalDiagnostics();
  await capture('move-detail');
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.detail-screen')).toBeVisible();
  await page.getByRole('tab', { name: 'MORE' }).click();
  await expect(page.locator('.inline-ability')).toContainText('OVERGROW');
  await expect(page.locator('.inline-ability')).toContainText('Powers up Grass-type moves.');
  await expect(page.locator('.inline-ability')).toContainText('HP ≤ 1/3');
  await expect(page.locator('.inline-ability')).toContainText('Grass move power ×1.5');
  await expect(page.getByRole('button', { name: /OVERGROW/ })).toHaveCount(0);
  await expectTypography('.inline-ability > header span', 12);
  await expectTypography('.inline-ability > p', 16.5);
  await expectTypography('.evolution-row > span:last-child', 13.5);
  await expectTypography('.data-row span', 13.5);
  await capture('detail-more');
  await show('trainer', { screen: 'TRAINER' });
  await expectSurface('.trainer-card-content', { backgroundColor: 'rgba(0, 0, 0, 0)' });
  await expectSurface('.trainer-card-shell', { backgroundColor: colors.panel, borderTopColor: colors.border });
  await expectSurface('.trainer-card-strip', { backgroundColor: colors.header, borderTopColor: colors.border });
  await expectTypography('.trainer-card-strip strong', 13.5);
  await expectTypography('.trainer-card-strip span', 12);
  await expectTypography('.trainer-card-facts dt', 12);
  await expectTypography('.trainer-card-facts dd', 15);
  await expect.poll(() => page.locator('.trainer-avatar').evaluate(node => {
    const frame = node.getBoundingClientRect();
    const art = node.querySelector('img')!.getBoundingClientRect();
    const rounded = (value: number) => Math.round(value * 100) / 100;
    return { artworkScale: rounded(art.height / frame.height), topInsetShare: rounded((art.top - frame.top) / frame.height) };
  })).toEqual({ artworkScale: 1.46, topInsetShare: .01 });
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.pokedex-screen')).toBeVisible();
  await show('party', { screen: 'PARTY' });
  await expectSurface('.party-content', { backgroundColor: 'rgba(0, 0, 0, 0)' });
  await expectSurface('.party-slot:not(.empty)', { borderTopColor: colors.accent, color: colors.text });
  await expect.poll(() => page.locator('.party-content').evaluate(content => {
    const contentBox = content.getBoundingClientRect();
    const grid = content.querySelector('.party-grid')!.getBoundingClientRect();
    const card = content.querySelector('.party-slot')!.getBoundingClientRect();
    const portrait = content.querySelector('.party-sprite')!.getBoundingClientRect();
    const headingNode = content.querySelector('.party-slot-heading')!;
    const heading = headingNode.getBoundingClientRect();
    const vitals = content.querySelector('.party-hp-value')!.getBoundingClientRect();
    const level = content.querySelector('.party-slot-level')!;
    const gender = content.querySelector('.party-slot-gender')!;
    const hpLabel = content.querySelector('.party-hp-line > b')!;
    const hpValue = content.querySelector('.party-hp-value')!;
    const hpTrack = content.querySelector('.party-hp-track')!.getBoundingClientRect();
    const expTrack = content.querySelector('.party-exp-track')!.getBoundingClientRect();
    const rounded = (value: number) => Math.round(value * 100) / 100;
    return {
      widthShare: rounded(grid.width / contentBox.width),
      heightShare: rounded(grid.height / contentBox.height),
      topInsetShare: rounded((grid.top - contentBox.top) / contentBox.height),
      bottomInsetShare: rounded((contentBox.bottom - grid.bottom) / contentBox.height),
      cardRatio: rounded(card.width / card.height),
      portraitShare: rounded(portrait.height / card.height),
      detailsTopInsetShare: rounded((heading.top - card.top) / card.height),
      detailsBottomInsetShare: rounded((card.bottom - vitals.bottom) / card.height),
      headingFontPx: Math.round(Number.parseFloat(getComputedStyle(headingNode.querySelector('strong')!).fontSize) * 10) / 10,
      levelFontPx: Math.round(Number.parseFloat(getComputedStyle(level).fontSize) * 10) / 10,
      genderFontPx: Math.round(Number.parseFloat(getComputedStyle(gender).fontSize) * 10) / 10,
      hpLabelFontPx: Math.round(Number.parseFloat(getComputedStyle(hpLabel).fontSize) * 10) / 10,
      hpValueFontPx: Math.round(Number.parseFloat(getComputedStyle(hpValue).fontSize) * 10) / 10,
      hpTrackHeight: rounded(hpTrack.height),
      expTrackHeight: rounded(expTrack.height),
    };
  })).toEqual({
    widthShare: .94,
    heightShare: .88,
    topInsetShare: .06,
    bottomInsetShare: .06,
    cardRatio: 2.38,
    portraitShare: .52,
    detailsTopInsetShare: .16,
    detailsBottomInsetShare: .15,
    headingFontPx: 30,
    levelFontPx: 21,
    genderFontPx: 22.5,
    hpLabelFontPx: 18.8,
    hpValueFontPx: 18,
    hpTrackHeight: 16,
    expTrackHeight: 8,
  });
  await page.getByRole('button', { name: /Party slot 1/ }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expectTypography('.party-summary-grid small', 12);
  await expectTypography('.party-stat-grid small', 12);
  await expectTypography('.party-move-row span', 12);
  await capture('party-detail');
  await page.getByRole('button', { name: 'OVERGROW' }).click();
  await expect(page.locator('.ability-detail-screen')).toBeVisible();
  await assertGameTheme();
  await expectSurface('.ability-detail-content', { backgroundColor: 'rgba(0, 0, 0, 0)' });
  await expect(page.locator('.ability-detail-content')).toContainText('HP ≤ 1/3');
  await expect(page.locator('.ability-detail-content')).toContainText('Grass move power ×1.5');
  await expectTypography('.ability-detail-content .eyebrow', 12);
  await expectTypography('.ability-detail-content .entry-copy', 16.5);
  await expectTypography('.ability-mechanic span', 12);
  await expectTypography('.ability-mechanic strong', 16.5);
  await assertNoNormalDiagnostics();
  await capture('ability-detail');
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.party-screen')).toBeVisible();
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.pokedex-screen')).toBeVisible();
  await show('battle-entry', {
    screen: 'BATTLE', battle: { opponents: [{ speciesId: 2, level: 10, typeIds: [12], rarity: { relativeTier: 'ORDINARY', innateTier: 'STANDARD', baseStars: 2, areaAdjustment: 0, stars: 2 }, moves: [] }], targetIndex: 0, targetMode: 'AUTOMATIC', capabilities: {}, selectedMoveId: 22, effectiveness: 'NEUTRAL', effectivenessKnown: true },
  });
  await expectSurface('.battle-identity', { backgroundColor: colors.menu, borderBottomColor: colors.border, color: colors.text });
  await expectSurface('.battle-content', { backgroundColor: 'rgba(0, 0, 0, 0)', color: colors.text });
  await expect.poll(() => page.locator('.battle-screen').evaluate(node => getComputedStyle(node).backgroundImage)).not.toBe('none');
  await expectSurface('.battle-screen .segmented button:not(.active)', { backgroundColor: colors.panel, borderTopColor: colors.border });
  await expectTypography('.battle-identity small', 12);
  await expectTypography('.battle-content .eyebrow', 12);
  await expect(page.locator('.battle-dex-link svg')).toHaveAttribute('data-semantic-icon', 'pokedex');
  await expectSurface('.battle-dex-link', { backgroundColor: 'rgba(0, 0, 0, 0)', borderTopColor: 'rgba(0, 0, 0, 0)', color: colors.text });
  for (const tab of ['ATTACK', 'RARITY', 'MOVES']) {
    await page.getByRole('tab', { name: tab }).click();
    await expect(page.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true');
    if (tab === 'RARITY') {
      await expect(page.locator('.rarity-card > .rarity-stars')).toBeVisible();
      await expect(page.locator('.rarity-card')).toHaveAttribute('data-rarity-band', 'low');
      await expect.poll(() => page.locator('.rarity-card').evaluate(node => getComputedStyle(node).backgroundImage)).not.toBe('none');
      await expectSurface('.rarity-card > small', { color: colors.text });
      await expectSurface('.rarity-card p', { color: colors.text });
      await expectTypography('.rarity-card > small', 14.3);
      await expectTypography('.rarity-card p', 15.8);
    }
    if (tab === 'ATTACK') {
      await expectTypography('.attack-heading small', 12);
      await expectTypography('.move-metadata small', 12);
      await expectTypography('.move-metadata strong', 15);
    }
    await assertNoNormalDiagnostics();
    await capture(`battle-${tab.toLowerCase()}`);
  }
  await show('settings', { screen: 'SETTINGS' });
  await expectSurface('.settings-content', { backgroundColor: colors.menu, color: colors.text });
  await expectSurface('.settings-content .segmented button:not(.active)', { backgroundColor: colors.panel, borderTopColor: colors.border });
  await expectTypography('.setting-note', 12.8);
  await expectTypography('.settings-upload', 12);
  await page.getByRole('button', { name: 'CAPABILITY REPORT' }).click();
  await expect(page.locator('.capability-screen')).toBeVisible();
  await assertGameTheme();
  await expectSurface('.capability-identity', { backgroundColor: colors.header, borderBottomColor: colors.border, color: colors.accentText });
  await expectSurface('.capability-identity > span', { color: colors.accentText });
  await expectSurface('.capability-actions button', { backgroundColor: colors.accent, borderTopColor: colors.border, color: colors.accentText });
  await capture('debug-capabilities');
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.settings-screen')).toBeVisible();
  await page.getByRole('button', { name: 'CAPTURE MEMORY REPORT' }).click();
  await expect(page.locator('.mapper-screen')).toBeVisible();
  await assertGameTheme();
  await expectSurface('.mapper-enable .primary-button', { backgroundColor: colors.accent, borderTopColor: colors.border, color: colors.accentText });
  await capture('debug-memory');
  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.settings-screen')).toBeVisible();
  await show('setup', { screen: 'SETUP' });
  await expectTypography('.setup-step > header strong', 12.9);
  await expectTypography('.setup-step p', 12.3);
  await expectTypography('.setup-step small', 11.3);
  await expectTypography('.setup-action', 12);
  await show('loading', { screen: 'POKEDEX', loading: { active: true, phase: 'ABILITY_DATA', completedUnits: 8, totalUnits: 12 } });
  await expectTypography('.loading-indicator', 11.3);

  serverState = { ...baseState, version: 20 };
  await page.reload();
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.getByRole('region', { name: 'Interactive local map' })).toBeVisible();
  await assertGameTheme();
  await capture('local-map');
  await page.getByRole('button', { name: 'Show Atlas' }).click();
  await expect(page.getByRole('region', { name: 'Interactive world map' })).toBeVisible();
  await expect(page.locator('.map-fog')).toHaveCount(1);
  const fogState = await page.locator('.map-fog').evaluate(canvas => {
    const target = canvas as HTMLCanvasElement;
    const context = target.getContext('2d')!;
    const pixels = context.getImageData(0, 0, target.width, target.height).data;
    const alphaAt = (x: number, y: number) => pixels[(y * target.width + x) * 4 + 3];
    const currentX = Math.round(3.5 / 28 * target.width);
    const currentY = Math.round(11.5 / 15 * target.height);
    return {
      outerEdgesOpaque: [alphaAt(0, 0), alphaAt(target.width - 1, 0), alphaAt(0, target.height - 1), alphaAt(target.width - 1, target.height - 1)].every(alpha => alpha === 255),
      currentAreaRevealed: alphaAt(currentX, currentY) < 255,
    };
  });
  expect(fogState).toEqual({ outerEdgesOpaque: true, currentAreaRevealed: true });
  await assertGameTheme();
  await expectSurface('.map-page-header', { backgroundColor: colors.header, color: colors.accentText });
  await expectTypography('.map-current-location strong', 15.8);
  await expectTypography('.map-current-location span', 11.3);
  await expect(page.locator('.map-dex-action svg')).toHaveAttribute('data-semantic-icon', 'pokedex');
  await expect.poll(() => page.locator('.map-dex-action .dex-shell').evaluate(node => ({
    fill: getComputedStyle(node).fill,
    stroke: getComputedStyle(node).stroke,
  }))).toEqual({ fill: 'none', stroke: colors.accentText });
  await expectSurface('.map-zoom-rail .map-control', { backgroundColor: colors.menu, borderTopColor: colors.border, color: colors.text });
  const browserRasterHash = await page.evaluate(async () => {
    const payload = await fetch('/api/maps/world%2Fgen3-region-0.png').then(response => response.arrayBuffer());
    const digest = await crypto.subtle.digest('SHA-256', payload);
    return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
  });
  expect(browserRasterHash).toBe(rasterSha256);
  await capture('atlas');
  await page.getByRole('button', { name: 'Open Pokédex' }).click();
  await expect(page.locator('.pokedex-screen')).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event('dualdexback', { cancelable: true })));
  await expect(page.locator('.pokedex-screen')).toBeVisible();

  serverState = { ...baseState, version: 21, screen: 'DETAIL', selectedSpeciesId: 1 };
  await page.reload();
  await page.getByRole('tab', { name: 'AREA' }).click();
  await expect(page.locator('.pokemon-area-panel')).toBeVisible();
  await assertGameTheme();
  await expectSurface('.pokemon-area-panel', { backgroundColor: colors.panel, borderTopColor: colors.border, color: colors.text });
  await expectSurface('.pokemon-area-panel > header', { backgroundColor: colors.header });
  await expectTypography('.pokemon-area-panel > header small', 11.3);
  await capture('pokemon-area');

  for (const fixed of ['DARK', 'LIGHT'] as const) {
    serverState = { ...baseState, version: fixed === 'DARK' ? 30 : 31, settings: { ...baseState.settings, theme: fixed } };
    await page.reload();
    const shell = page.locator('.production-device');
    await expect(shell).toHaveAttribute('data-theme', fixed.toLowerCase());
    await expect.poll(() => shell.evaluate(node => (node as HTMLElement).style.getPropertyValue('--theme-field'))).toBe('');
    await expect.poll(() => page.locator('.app-header').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(19, 69, 53)');
    await capture(fixed.toLowerCase());
  }

  serverState = { ...baseState, version: 32, settings: { ...baseState.settings, theme: 'GAME', highContrast: true } };
  await page.reload();
  const highContrast = page.locator('.production-device');
  await expect(highContrast).toHaveAttribute('data-contrast', 'high');
  await expect.poll(() => highContrast.evaluate(node => (node as HTMLElement).style.getPropertyValue('--theme-field'))).toBe('');
  await expect.poll(() => page.locator('.app-header').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(19, 69, 53)');
  await expect.poll(() => highContrast.evaluate(node => getComputedStyle(node).getPropertyValue('--paper').trim())).toBe('#fffde8');
  await capture('high-contrast');
  writeFileSync(join(artifactDir, 'font-size-matrix.json'), `${JSON.stringify(fontMetrics, null, 2)}\n`);
  writeFileSync(join(artifactDir, 'font-size-matrix.md'), [
    '# 1024×768 visible-text font-size matrix',
    '',
    'Average is unweighted across visible text-bearing elements in each captured layout.',
    '',
    '| Layout view | Text elements | Minimum (px) | Maximum (px) | Average (px) |',
    '|---|---:|---:|---:|---:|',
    ...fontMetrics.map(metric => `| ${metric.view} | ${metric.elements} | ${metric.minimumPx.toFixed(1)} | ${metric.maximumPx.toFixed(1)} | ${metric.averagePx.toFixed(1)} |`),
    '',
  ].join('\n'));
  for (const metric of fontMetrics) {
    expect(metric.minimumPx, `${metric.view} has sub-floor visible text`).toBeGreaterThanOrEqual(11.2);
    expect(metric.averagePx, `${metric.view} has an undersized overall text hierarchy`).toBeGreaterThanOrEqual(12);
  }
});
