import { expect, test, type Page } from '@playwright/test';

test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

const state = {
  version: 1,
  screen: 'POKEDEX',
  priorScreen: 'POKEDEX',
  settingsReturnScreen: 'POKEDEX',
  selectedSpeciesId: null,
  selectedPartySlot: 0,
  filter: 'ALL',
  selectedAreaId: null,
  selectedAreaIds: [],
  currentAreaIds: [],
  currentAreaBaseId: null,
  currentAreaName: null,
  currentAreaSpeciesIds: [],
  revealedAreaBaseIds: [],
  observedAreaBaseIdsBySpecies: {},
  battleTab: 'ENTRY',
  settings: {
    knowledgeMode: 'DISCOVERED',
    attackEnabled: true,
    rarityEnabled: true,
    movesEnabled: true,
    fontScale: 1,
    density: 'AUTO',
    highContrast: false,
    autoOpenTarget: true,
    ruleset: 'AUTO',
    theme: 'LIGHT',
    displayTarget: 'AUTO',
  },
  speciesState: { 25: { seen: true, caught: true, team: false, ballId: null } },
  observedMoves: {},
  catalogReady: true,
  catalogName: 'public-fixture.gba',
  error: null,
  activeRulesetId: null,
  rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 1, totalUnits: 1 },
  party: [],
  battle: null,
};

function catalog(hash: string, speciesName: string) {
  return {
    hash,
    crc32: '1234ABCD',
    family: 'EMERALD',
    platform: 'GBA',
    rulesets: [],
    moves: [],
    balls: [],
    capabilities: {},
    types: [{ id: 13, name: 'ELECTRIC', foreground: '#111111', background: '#f8d030', border: '#a89020' }],
    areas: [],
    localMaps: [],
    natures: [],
    species: [{
      id: 25,
      dex: 25,
      name: speciesName,
      typeIds: [13],
      stats: null,
      description: 'Public browser fixture',
      height: 4,
      weight: 60,
      learnset: [],
      learnsets: {},
      normalizedLearnsets: {},
      moveAcquisitions: [],
      abilities: [],
      evolutions: [],
      hasSprite: true,
    }],
    worldMaps: [{
      key: 'public-map',
      displayName: 'Public Map',
      pixelWidth: 224,
      pixelHeight: 120,
      gridWidth: 28,
      gridHeight: 15,
      imageUrl: '/api/maps/public-map.png',
      locations: [],
    }],
  };
}

const catalogA = catalog('public-catalog-a', 'PIKACHU');
const catalogB = catalog('public-catalog-b', 'EEVEE');

const mapSvg = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="224" height="120">
  <rect width="224" height="120" fill="#78a858" />
</svg>`);

function spriteSvg(color: string): Buffer {
  return Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8">
  <rect width="8" height="8" fill="${color}" />
</svg>`);
}

async function renderedPixel(page: Page, alt: string): Promise<number[]> {
  return page.getByAltText(alt).evaluate(async element => {
    const image = element as HTMLImageElement;
    if (!image.complete) {
      await new Promise<void>((resolve, reject) => {
        image.addEventListener('load', () => resolve(), { once: true });
        image.addEventListener('error', () => reject(new Error('sprite failed to load')), { once: true });
      });
    }
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('canvas context unavailable');
    context.drawImage(image, 0, 0, 1, 1);
    return Array.from(context.getImageData(0, 0, 1, 1).data.slice(0, 3));
  });
}

test('touch navigation survives reload and preserves browser back behavior', async ({ page }) => {
  await page.route('**/api/bootstrap', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ catalog: catalogA, state }),
  }));
  await page.route('**/api/state**', route => route.fulfill({ status: 204 }));
  await page.route('**/api/maps/**', route => route.fulfill({
    contentType: 'image/svg+xml',
    body: mapSvg,
  }));
  await page.route('**/api/sprites/**', route => route.fulfill({
    contentType: 'image/svg+xml',
    body: spriteSvg('#ff0000'),
  }));

  await page.goto('/');
  const mapButton = page.getByRole('button', { name: 'Open Map' });
  await expect(mapButton).toBeVisible();
  await mapButton.tap();

  const map = page.getByRole('region', { name: 'Interactive world map' });
  await expect(map).toBeVisible();
  const routeHash = new URL(page.url()).hash;
  expect(routeHash).toMatch(/^#dualdex=/);

  await page.reload();
  await expect(map).toBeVisible();
  expect(new URL(page.url()).hash).toBe(routeHash);

  await page.goBack();
  await expect(page.getByText('POKÉDEX', { exact: true })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('');
});

test('connection recovery refreshes catalog-owned media without clearing browser data', async ({ page }) => {
  let bootstrapRequests = 0;
  let stateRequests = 0;
  let activeCatalog = catalogA;
  let spriteRequests = 0;

  await page.route('**/api/bootstrap', route => {
    bootstrapRequests += 1;
    activeCatalog = bootstrapRequests === 1 ? catalogA : catalogB;
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ catalog: activeCatalog, state }),
    });
  });
  await page.route('**/api/state**', route => {
    stateRequests += 1;
    return stateRequests === 1
      ? route.abort('connectionfailed')
      : route.fulfill({ status: 204 });
  });
  await page.route('**/api/sprites/**', route => {
    spriteRequests += 1;
    const color = activeCatalog.hash === catalogA.hash ? '#ff0000' : '#0000ff';
    return route.fulfill({
      contentType: 'image/svg+xml',
      headers: { 'Cache-Control': 'no-cache' },
      body: spriteSvg(color),
    });
  });

  await page.goto('/');
  await expect(page.getByAltText('PIKACHU sprite')).toBeVisible();
  expect(await renderedPixel(page, 'PIKACHU sprite')).toEqual([255, 0, 0]);
  await expect(page.getByText('Reconnecting to the companion…')).toBeVisible();

  await expect(page.getByAltText('EEVEE sprite')).toBeVisible();
  await expect.poll(() => spriteRequests).toBeGreaterThanOrEqual(2);
  expect(await renderedPixel(page, 'EEVEE sprite')).toEqual([0, 0, 255]);
  await expect(page.getByText('Reconnecting to the companion…')).toHaveCount(0);
});
