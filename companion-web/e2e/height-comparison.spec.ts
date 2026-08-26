import { expect, test } from '@playwright/test';

const trainerWithTransparentMargins = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
  <rect x="40" y="10" width="20" height="80" fill="#d04040" />
</svg>`);

const catalog = {
  hash: 'height-control', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], balls: [], capabilities: {},
  types: [{ id: 10, name: 'FIRE', foreground: '#111111', background: '#f08040', border: '#a04020' }],
  areas: [], worldMaps: [], localMaps: [], natures: [],
  species: [{
    id: 255, dex: 255, name: 'TORCHIC', typeIds: [10], stats: null, description: 'Entry', height: 4, weight: 25,
    learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false,
  }],
};

const state = {
  version: 1, screen: 'DETAIL', priorScreen: 'POKEDEX', settingsReturnScreen: 'DETAIL', selectedSpeciesId: 255,
  selectedPartySlot: 0, filter: 'ALL', selectedAreaId: null, selectedAreaIds: [], currentAreaIds: [], currentAreaBaseId: null,
  currentAreaName: null, currentAreaSpeciesIds: [], revealedAreaBaseIds: [], observedAreaBaseIdsBySpecies: {}, battleTab: 'ENTRY',
  settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'LIGHT', displayTarget: 'AUTO' },
  speciesState: { 255: { seen: true, caught: true, team: false, ballId: null } }, observedMoves: {}, catalogReady: true,
  catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  trainer: { name: 'PLAYER', gender: 'FEMALE', publicTrainerId: 1, money: 0, playTimeHours: 1, playTimeMinutes: 0, dexSeen: 1, dexCaught: 1, stars: 0, avatarUrl: '/api/trainer-assets/trainer.png', badges: [] },
  trainerAvatarUrl: '/api/trainer-assets/trainer.png', party: [], battle: null,
};

test('visible trainer pixels represent 1.7 m and occupy 80% of the ruler', async ({ page }) => {
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog, state }) }));
  await page.route('**/api/state**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) }));
  await page.route('**/api/actions', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) }));
  await page.route('**/api/trainer-assets/**', route => route.fulfill({ contentType: 'image/svg+xml', body: trainerWithTransparentMargins }));

  await page.goto('/');
  const chart = page.getByRole('img', { name: 'Height comparison for TORCHIC: 0.4 m beside a 1.7 m person' });
  await expect(chart).toBeVisible();
  const sprite = chart.locator('.height-person canvas[data-alpha-trimmed="true"]');
  await expect(sprite).toBeVisible();
  await expect.poll(async () => chart.locator('.height-ruler').evaluate(node => {
    const ruler = node.getBoundingClientRect();
    const trainer = node.querySelector('.height-person canvas[data-alpha-trimmed="true"]')!.getBoundingClientRect();
    return {
      heightShare: Math.round(trainer.height / ruler.height * 100),
      aspect: Math.round(trainer.width / trainer.height * 100),
      grounded: Math.round(trainer.bottom - ruler.bottom),
    };
  })).toEqual({ heightShare: 80, aspect: 25, grounded: 0 });
});
