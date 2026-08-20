import { expect, test } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const artifactDir = process.env.DUALDEX_PARTY_ARTIFACT_DIR ?? 'D:/Temp/dualdex-party-artwork-rc17';
const portrait = readFileSync(join(process.cwd(), '..', 'app', 'src', 'main', 'assets', 'icon-lowest.png'));

const types = [
  { id: 13, name: 'ELECTRIC', foreground: '#2b2300', background: '#f5d642', border: '#9c851c' },
  { id: 2, name: 'FLYING', foreground: '#17253d', background: '#a9c7f0', border: '#5b79a4' },
  { id: 99, name: 'COSMIC-LIGHT', foreground: '#fff', background: '#4256a6', border: '#101c55' },
];

const species = Array.from({ length: 5 }, (_, index) => ({
  id: 25 + index, dex: 25 + index, name: ['PIKACHU', 'FAINTED PARTNER', 'UNIDENTIFIED', 'A VERY LONG PARTNER NAME', 'HEALTHY PARTNER'][index],
  typeIds: index === 0 ? [13, 2] : index === 3 ? [99] : [13], stats: null, description: null, height: null, weight: null,
  learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: index === 0 || index === 4,
}));

const baseMember = {
  occupied: true, nickname: null, level: 18, isEgg: false, gender: 'FEMALE', nature: 'Adamant', abilityId: null, abilityName: null,
  heldItemId: null, heldItemName: null, hasHeldItem: null, currentHp: 31, maximumHp: 45, status: null, experienceProgress: .5,
  stats: { HP: 45, ATTACK: 28, DEFENSE: 22, SPEED: 38, 'SP. ATK': 30, 'SP. DEF': 26 }, moves: [],
};

const party = [
  { ...baseMember, slot: 0, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', nickname: 'SPARK', typeIds: [13, 2], abilityId: 9, abilityName: 'Static', hasHeldItem: true, status: 'PAR' },
  { ...baseMember, slot: 1, speciesId: 26, speciesName: 'FAINTED PARTNER', spriteUrl: null, typeIds: [13], currentHp: 0, hasHeldItem: false },
  { ...baseMember, slot: 2, speciesId: null, speciesName: null, spriteUrl: '/api/sprites/species/27.png', typeIds: [], currentHp: null, maximumHp: null, nature: null, hasHeldItem: null },
  { ...baseMember, slot: 3, speciesId: 28, speciesName: 'A VERY LONG PARTNER NAME', spriteUrl: null, typeIds: [99], currentHp: null, maximumHp: null, status: 'CUSTOM', stats: {}, hasHeldItem: null },
  { ...baseMember, slot: 4, speciesId: 29, speciesName: 'HEALTHY PARTNER', spriteUrl: '/api/sprites/species/29.png', typeIds: [13], hasHeldItem: false },
  { slot: 5, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null, isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, hasHeldItem: null, currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [] },
];

const catalog = {
  hash: 'party-browser-control', crc32: 'CONTROL', family: 'EMERALD', platform: 'GBA', rulesets: [], species, moves: [], types, areas: [], balls: [], capabilities: {},
};

const initialState = {
  version: 1, screen: 'PARTY', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, selectedPartySlot: 0,
  filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'Party artwork control', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  trainer: { name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 98765, playTimeHours: 12, playTimeMinutes: 34, dexSeen: 42, dexCaught: 7, stars: 2, avatarUrl: null, badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index < 2, imageUrl: null })) },
  party,
};

test('party artwork remains dynamic and privacy-safe at 4:3', async ({ page }) => {
  let state = { ...initialState };
  const actions: Record<string, unknown>[] = [];
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog, state }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) }));
  await page.route('**/api/sprites/species/**', route => route.fulfill({ contentType: 'image/png', body: portrait }));
  await page.route('**/api/actions', async route => {
    const action = route.request().postDataJSON() as Record<string, unknown>;
    actions.push(action);
    if (action.type === 'BACK') state = { ...state, version: state.version + 1, screen: 'POKEDEX' };
    if (action.type === 'OPEN_TRAINER') state = { ...state, version: state.version + 1, screen: 'TRAINER' };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) });
  });

  await page.goto('/');
  await expect(page.locator('.party-grid .party-slot')).toHaveCount(6);
  await expect(page.locator('.party-grid')).toHaveAttribute('data-layout', '2x3');
  await expect(page.locator('.party-grid')).toHaveCSS('grid-template-columns', /.+ .+/);
  await expect(page.locator('.party-grid')).toHaveCSS('grid-template-rows', /.+ .+ .+/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.party-slot').first().locator('.party-exp-track')).toHaveAttribute('aria-label', 'Experience 50%');
  const expHeight = await page.locator('.party-slot').first().locator('.party-exp-track').evaluate(element => element.getBoundingClientRect().height);
  const hpHeight = await page.locator('.party-slot').first().locator('.party-hp-track').evaluate(element => element.getBoundingClientRect().height);
  expect(expHeight).toBeLessThanOrEqual(hpHeight / 2 + 1);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'party-roster-4x3.png') });

  await page.getByRole('button', { name: 'Party slot 1: SPARK' }).click();
  await expect(page.getByRole('dialog', { name: 'SPARK details' })).toBeVisible();
  await expect(page.locator('.party-detail')).toHaveAttribute('data-condition', 'statused');
  await expect(page.getByRole('img', { name: 'Paralyzed' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Held item present' })).toBeVisible();
  await expect(page.locator('.party-detail .party-type-art')).toHaveCount(2);
  await expect(page.locator('.party-detail .party-sprite img')).toHaveJSProperty('complete', true);

  await page.screenshot({ path: join(artifactDir, 'party-selected-4x3.png') });

  await page.getByRole('button', { name: 'Close SPARK details' }).click();
  await page.getByRole('button', { name: 'Party slot 2: FAINTED PARTNER' }).click();
  await expect(page.locator('.party-detail')).toHaveAttribute('data-condition', 'fainted');
  await expect(page.getByText('FAINTED', { exact: true })).toBeVisible();
  await expect(page.locator('.party-detail [data-artwork="missing"]')).toBeVisible();
  await page.screenshot({ path: join(artifactDir, 'party-fainted-4x3.png') });

  await page.getByRole('button', { name: 'Close FAINTED PARTNER details' }).click();
  await page.getByRole('button', { name: 'Party slot 3: Unknown partner' }).click();
  await expect(page.locator('.party-detail [data-artwork="silhouette"]')).toBeVisible();
  await expect(page.locator('.party-detail img.identity-silhouette')).toBeVisible();
  await expect(page.locator('.party-detail .party-types')).toHaveCount(0);
  await expect(page.getByText('Held item unavailable')).toBeVisible();
  await page.screenshot({ path: join(artifactDir, 'party-silhouette-4x3.png') });

  await page.getByRole('button', { name: 'Close party member details' }).click();
  await page.getByRole('button', { name: 'Party slot 4: A VERY LONG PARTNER NAME' }).click();
  await expect(page.locator('.party-detail')).toHaveAttribute('data-condition', 'partial');
  await expect(page.getByRole('img', { name: 'CUSTOM status' })).toBeVisible();
  await expect(page.locator('.party-type-art abbr')).toHaveText('CO');
  await expect(page.getByText('COSMIC-LIGHT')).toBeVisible();
  await page.screenshot({ path: join(artifactDir, 'party-partial-4x3.png') });

  await page.getByRole('button', { name: 'Close A VERY LONG PARTNER NAME details' }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page.locator('.pokedex-screen')).toBeVisible();
  expect(actions.some(action => action.type === 'BACK')).toBe(true);

  await page.getByRole('button', { name: 'Trainer Card' }).click();
  await expect(page.locator('.trainer-card-shell')).toBeVisible();
  await expect(page.locator('.trainer-card-shell .trainer-badge')).toHaveCount(8);
  await expect(page.locator('.trainer-card-shell')).toContainText('₽98,765');
  await page.screenshot({ path: join(artifactDir, 'trainer-card-4x3.png') });
});
