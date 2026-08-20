import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const fixturePath = process.env.DUALDEX_MAP_FIXTURE_PNG;
if (!fixturePath) throw new Error('Set DUALDEX_MAP_FIXTURE_PNG to a sanitized normalized map PNG');
const artifactDir = process.env.DUALDEX_THEME_EVIDENCE_DIR;
if (!artifactDir) throw new Error('Set DUALDEX_THEME_EVIDENCE_DIR for browser evidence');
const raster = readFileSync(fixturePath);
const rasterSha256 = createHash('sha256').update(raster).digest('hex');

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
    moveAcquisitions: [], abilities: [{ id: 65, name: 'OVERGROW', description: 'Powers up Grass-type moves.', mechanics: [] }],
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
  trainer: { name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 98765, playTimeHours: 12, playTimeMinutes: 34, dexSeen: 2, dexCaught: 1, stars: 2, avatarUrl: null, badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index < 2, imageUrl: null })) },
  party: [{ slot: 0, occupied: true, speciesId: 1, speciesName: 'BULBASAUR', spriteUrl: null, typeIds: [12], nickname: 'BULBASAUR', level: 8, isEgg: false, gender: 'MALE', nature: 'Hardy', abilityId: 65, abilityName: 'OVERGROW', heldItemId: null, heldItemName: null, currentHp: 21, maximumHp: 25, status: null, experienceProgress: .5, stats: { HP: 25, ATTACK: 13, DEFENSE: 13, SPEED: 12, 'SP. ATK': 16, 'SP. DEF': 16 }, moves: [{ slot: 0, moveId: 22, name: 'VINE WHIP', currentPp: 24, maximumPp: 25 }] }],
  battle: null,
};

test('ROM-derived GAME theme remains stable across companion screens and fixed alternatives', async ({ page }) => {
  mkdirSync(artifactDir, { recursive: true });
  let serverState: Record<string, unknown> = { ...baseState };
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog, state: serverState }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) }));
  await page.route('**/api/actions', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) }));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: raster }));

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
  const show = async (name: string, state: Record<string, unknown>) => {
    serverState = { ...baseState, ...state, version: Number(serverState.version ?? 1) + 1 };
    await page.reload();
    await expect(page.locator('.production-device')).toBeVisible();
    await assertGameTheme();
    await page.screenshot({ path: join(artifactDir, `${name}.png`) });
  };

  await page.goto('/');
  await assertGameTheme();
  await page.screenshot({ path: join(artifactDir, 'pokedex.png') });
  await show('detail', { screen: 'DETAIL', selectedSpeciesId: 1 });
  await show('trainer', { screen: 'TRAINER' });
  await show('party', { screen: 'PARTY' });
  await show('battle', {
    screen: 'BATTLE', battle: { opponents: [{ speciesId: 2, level: 10, typeIds: [12], rarity: { relativeTier: 'ORDINARY', innateTier: 'STANDARD', baseStars: 2, areaAdjustment: 0, stars: 2 }, moves: [] }], targetIndex: 0, targetMode: 'AUTOMATIC', capabilities: {}, selectedMoveId: 22, effectiveness: 'NEUTRAL', effectivenessKnown: true },
  });
  await show('settings', { screen: 'SETTINGS' });
  await show('loading', { screen: 'POKEDEX', loading: { active: true, phase: 'EXTENDED', completedUnits: 4, totalUnits: 5 } });

  serverState = { ...baseState, version: 20 };
  await page.reload();
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.getByRole('region', { name: 'Interactive local map' })).toBeVisible();
  await assertGameTheme();
  await page.screenshot({ path: join(artifactDir, 'local-map.png') });
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
  const browserRasterHash = await page.evaluate(async () => {
    const payload = await fetch('/api/maps/world%2Fgen3-region-0.png').then(response => response.arrayBuffer());
    const digest = await crypto.subtle.digest('SHA-256', payload);
    return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
  });
  expect(browserRasterHash).toBe(rasterSha256);
  await page.screenshot({ path: join(artifactDir, 'atlas.png') });
  await page.getByRole('button', { name: 'Open Pokédex' }).click();
  await expect(page.locator('.pokedex-screen')).toBeVisible();

  serverState = { ...baseState, version: 21, screen: 'DETAIL', selectedSpeciesId: 1 };
  await page.reload();
  await page.getByRole('tab', { name: 'AREA' }).click();
  await expect(page.locator('.pokemon-area-panel')).toBeVisible();
  await assertGameTheme();
  await page.screenshot({ path: join(artifactDir, 'pokemon-area.png') });

  for (const fixed of ['DARK', 'LIGHT'] as const) {
    serverState = { ...baseState, version: fixed === 'DARK' ? 30 : 31, settings: { ...baseState.settings, theme: fixed } };
    await page.reload();
    const shell = page.locator('.production-device');
    await expect(shell).toHaveAttribute('data-theme', fixed.toLowerCase());
    await expect.poll(() => shell.evaluate(node => (node as HTMLElement).style.getPropertyValue('--theme-field'))).toBe('');
    await expect.poll(() => page.locator('.app-header').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(19, 69, 53)');
    await page.screenshot({ path: join(artifactDir, `${fixed.toLowerCase()}.png`) });
  }

  serverState = { ...baseState, version: 32, settings: { ...baseState.settings, theme: 'GAME', highContrast: true } };
  await page.reload();
  const highContrast = page.locator('.production-device');
  await expect(highContrast).toHaveAttribute('data-contrast', 'high');
  await expect.poll(() => highContrast.evaluate(node => (node as HTMLElement).style.getPropertyValue('--theme-field'))).toBe('');
  await expect.poll(() => page.locator('.app-header').evaluate(node => getComputedStyle(node).backgroundColor)).toBe('rgb(19, 69, 53)');
  await expect.poll(() => highContrast.evaluate(node => getComputedStyle(node).getPropertyValue('--paper').trim())).toBe('#fffde8');
  await page.screenshot({ path: join(artifactDir, 'high-contrast.png') });
});
