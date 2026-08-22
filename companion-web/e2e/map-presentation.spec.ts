import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const fixturePath = process.env.DUALDEX_MAP_FIXTURE_PNG;
if (!fixturePath) throw new Error('Set DUALDEX_MAP_FIXTURE_PNG to a sanitized normalized 224x120 Emerald PNG');
const localFixturePath = process.env.DUALDEX_LOCAL_MAP_FIXTURE_PNG;
if (!localFixturePath) throw new Error('Set DUALDEX_LOCAL_MAP_FIXTURE_PNG to the parsed 320x320 Modern Emerald Littleroot PNG');
const raster = readFileSync(fixturePath);
const rasterSha256 = createHash('sha256').update(raster).digest('hex');
const localRaster = readFileSync(localFixturePath);
const localRasterSha256 = createHash('sha256').update(localRaster).digest('hex');
const artifactDir = join(process.cwd(), '..', 'output', 'map-presentation');

const state = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'AREA', selectedAreaId: null, selectedAreaIds: [] as number[], currentAreaBaseId: 16, currentAreaName: 'Route 101', battleTab: 'ENTRY',
  currentAreaIds: [161], currentAreaSpeciesIds: [5], revealedAreaBaseIds: [16, 17], observedAreaBaseIdsBySpecies: { 5: [17] },
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: { 5: { seen: true, caught: true, team: false, ballId: null }, 6: { seen: false, caught: false, team: false, ballId: null } }, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'Emerald control', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
};

const catalog = {
  hash: 'sanitized-browser-control', crc32: 'CONTROL', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], types: [], balls: [], capabilities: {},
  species: [
    { id: 5, dex: 5, name: 'Charmeleon', typeIds: [], stats: null, description: 'Fixture', height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
    { id: 6, dex: 6, name: 'Charizard', typeIds: [], stats: null, description: 'Fixture', height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
  ],
  areas: [
    { id: 171, baseAreaId: 17, name: 'Oldale grass', methodId: 1, speciesIds: [5], windows: ['DAY'], slots: [{ speciesId: 5, minimumLevel: 3, maximumLevel: 4, weight: 50 }] },
    { id: 181, baseAreaId: 18, name: 'Oldale water', methodId: 2, speciesIds: [6], windows: ['NIGHT'], slots: [{ speciesId: 6, minimumLevel: 4, maximumLevel: 4, weight: 1 }] },
    { id: 191, baseAreaId: 19, name: 'Kanto grass', methodId: 1, speciesIds: [5], windows: ['DAY'], slots: [{ speciesId: 5, minimumLevel: 5, maximumLevel: 6, weight: 25 }] },
  ],
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [
      { key: 'section-16', displayName: 'Route 101', baseAreaIds: [16], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
      { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [17, 18], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
      { key: 'section-18', displayName: 'Petalburg City', baseAreaIds: [18], geometry: [{ x: 23, y: 3, width: 1, height: 1 }] },
    ],
  }, {
    key: 'gen3-region-1', displayName: 'Kanto', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fgen3-region-1.png',
    locations: [{ key: 'section-19', displayName: 'Pallet Town', baseAreaIds: [19], geometry: [{ x: 3, y: 10, width: 1, height: 1 }] }],
  }],
};

test('real 4:3 map presentation, gestures, fog, and no-map fallback', async ({ page, context }) => {
  let serveMaps = true;
  let serverState: Omit<typeof state, 'selectedAreaId' | 'selectedSpeciesId'> & { selectedAreaId: number | null; selectedSpeciesId: number | null } = { ...state };
  const actions: Record<string, unknown>[] = [];
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog: { ...catalog, worldMaps: serveMaps ? catalog.worldMaps : [] }, state: serverState }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) }));
  await page.route('**/api/actions', async route => {
    const action = route.request().postDataJSON() as Record<string, unknown>;
    actions.push(action);
    if (action.type === 'MAP_AREA') serverState = { ...serverState, version: serverState.version + 1, screen: 'POKEDEX', filter: 'AREA', selectedAreaId: 171, selectedAreaIds: [171, 181], currentAreaIds: [171, 181], currentAreaSpeciesIds: [5] };
    if (action.type === 'OPEN_SPECIES') serverState = { ...serverState, version: serverState.version + 1, screen: 'DETAIL', selectedSpeciesId: Number(action.speciesId) };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(serverState) });
  });
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: raster }));

  await page.goto('/');
  const mapEntry = page.getByRole('button', { name: 'Open Map' });
  await expect(mapEntry).toBeVisible();
  await expect(mapEntry.locator('svg')).toHaveAttribute('data-semantic-icon', 'map');
  await expect(page.locator('[data-map-navigation-row]')).toHaveCount(0);
  const entryBounds = await mapEntry.boundingBox();
  const headerBounds = await page.locator('.app-header').boundingBox();
  expect(entryBounds!.y).toBeGreaterThanOrEqual(headerBounds!.y);
  expect(entryBounds!.y + entryBounds!.height).toBeLessThanOrEqual(headerBounds!.y + headerBounds!.height);
  await expect(page.locator('.pokedex-screen > .species-list')).toHaveCount(1);

  await mapEntry.click();
  const stage = page.getByRole('region', { name: 'Interactive world map' });
  await expect(stage).toBeVisible();
  const controls = ['Zoom in', 'Zoom out', 'Recenter map', 'Map settings and legend', 'Open Area Pokédex'];
  for (const label of controls) await expect(page.getByRole('button', { name: label })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Toggle fog of war' })).toHaveCount(0);
  await expect(page.locator('.map-utility-rail > button').nth(0).locator('svg')).toHaveAttribute('data-semantic-icon', 'map');
  await expect(page.locator('.map-utility-rail > button').nth(1).locator('svg')).toHaveAttribute('data-semantic-icon', 'pokedex');

  const image = page.locator('.map-plane > img');
  await expect.poll(() => image.evaluate(element => ({ width: (element as HTMLImageElement).naturalWidth, height: (element as HTMLImageElement).naturalHeight }))).toEqual({ width: 224, height: 120 });
  const fit = await page.locator('.map-plane').boundingBox();
  expect(fit!.width / fit!.height).toBeCloseTo(224 / 120, 2);
  const stageBounds = await stage.boundingBox();
  const hostBounds = await page.locator('.screen-host').boundingBox();
  expect(stageBounds!.height / hostBounds!.height).toBeGreaterThan(0.88);

  const fogEdges = await page.locator('.map-fog').evaluate(canvas => {
    const target = canvas as HTMLCanvasElement;
    const pixels = target.getContext('2d')!.getImageData(0, 0, target.width, target.height).data;
    const black = (x: number, y: number) => {
      const offset = (y * target.width + x) * 4;
      return pixels[offset] === 0 && pixels[offset + 1] === 0 && pixels[offset + 2] === 0 && pixels[offset + 3] === 255;
    };
    return {
      top: Array.from({ length: target.width }, (_, x) => black(x, 0)).every(Boolean),
      right: Array.from({ length: target.height }, (_, y) => black(target.width - 1, y)).every(Boolean),
      bottom: Array.from({ length: target.width }, (_, x) => black(x, target.height - 1)).every(Boolean),
      left: Array.from({ length: target.height }, (_, y) => black(0, y)).every(Boolean),
    };
  });
  expect(fogEdges).toEqual({ top: true, right: true, bottom: true, left: true });
  const fogDiscovery = await page.locator('.map-fog').evaluate(canvas => {
    const target = canvas as HTMLCanvasElement;
    const context = target.getContext('2d')!;
    const alphaAtCell = (x: number, y: number) => context.getImageData(
      Math.round((x + .5) / 28 * target.width),
      Math.round((y + .5) / 15 * target.height),
      1,
      1,
    ).data[3];
    return { current: alphaAtCell(3, 11), visited: alphaAtCell(4, 9), undiscovered: alphaAtCell(23, 3) };
  });
  expect(fogDiscovery.current).toBeLessThan(255);
  expect(fogDiscovery.visited).toBeLessThan(255);
  expect(fogDiscovery.undiscovered).toBe(255);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'fit-fog.png') });

  await page.getByRole('button', { name: 'Zoom in' }).click();
  expect(Number(await stage.getAttribute('data-scale'))).toBeGreaterThan(1);
  await page.screenshot({ path: join(artifactDir, 'zoomed.png') });

  await page.getByRole('button', { name: 'Recenter map' }).click();
  const panBounds = await stage.boundingBox();
  await page.mouse.move(panBounds!.x + panBounds!.width / 2, panBounds!.y + panBounds!.height / 2);
  await page.mouse.down();
  await page.mouse.move(panBounds!.x + panBounds!.width / 2 + 82, panBounds!.y + panBounds!.height / 2 + 43);
  await page.mouse.up();
  expect(Number(await stage.getAttribute('data-pan-x'))).toBeGreaterThan(75);
  expect(Number(await stage.getAttribute('data-pan-y'))).toBeGreaterThan(35);
  await page.screenshot({ path: join(artifactDir, 'panned.png') });

  await page.getByRole('button', { name: 'Recenter map' }).click();
  serverState = { ...serverState, version: serverState.version + 1, settings: { ...serverState.settings, knowledgeMode: 'DISCOVERED' } };
  await page.reload();
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.locator('.map-fog')).toHaveCount(0);
  await page.screenshot({ path: join(artifactDir, 'fog-off.png') });
  await page.getByRole('button', { name: 'Oldale Town' }).click();
  await expect(stage).toHaveAttribute('data-selected-key', 'section-17');
  serverState = { ...serverState, version: serverState.version + 1, settings: { ...serverState.settings, knowledgeMode: 'ORGANIC' } };
  await page.reload();
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.locator('.map-fog')).toHaveCount(1);
  await page.getByRole('button', { name: 'Oldale Town' }).click();

  const currentMarkerBounds = await page.getByRole('button', { name: 'Current location: Route 101' }).boundingBox();
  const centerX = currentMarkerBounds!.x + currentMarkerBounds!.width / 2 + 60;
  const centerY = currentMarkerBounds!.y + currentMarkerBounds!.height / 2;
  const client = await context.newCDPSession(page);
  const mapPointAt = (x: number, y: number) => stage.evaluate((element, point) => {
    const bounds = element.getBoundingClientRect();
    const scale = Number((element as HTMLElement).dataset.scale);
    const panX = Number((element as HTMLElement).dataset.panX);
    const panY = Number((element as HTMLElement).dataset.panY);
    return { x: (point.x - bounds.left - bounds.width / 2 - panX) / scale, y: (point.y - bounds.top - bounds.height / 2 - panY) / scale };
  }, { x, y });
  const touch = (x: number, y: number, id: number) => ({ x, y, id, radiusX: 2, radiusY: 2, force: 1 });

  const pinchOutAnchor = await mapPointAt(centerX, centerY);
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [touch(centerX - 60, centerY, 1), touch(centerX + 60, centerY, 2)] });
  await client.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [touch(centerX - 80, centerY + 18, 1), touch(centerX + 140, centerY + 18, 2)] });
  const pinchOutScale = Number(await stage.getAttribute('data-scale'));
  const pinchOutAfter = await mapPointAt(centerX + 30, centerY + 18);
  expect(pinchOutScale).toBeGreaterThan(1.7);
  expect(pinchOutAfter.x).toBeCloseTo(pinchOutAnchor.x, 3);
  expect(pinchOutAfter.y).toBeCloseTo(pinchOutAnchor.y, 3);
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });

  const pinchInAnchor = await mapPointAt(centerX + 30, centerY + 18);
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [touch(centerX - 80, centerY + 18, 3), touch(centerX + 140, centerY + 18, 4)] });
  await client.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [touch(centerX - 30, centerY + 4, 3), touch(centerX + 80, centerY + 4, 4)] });
  const pinchInScale = Number(await stage.getAttribute('data-scale'));
  const pinchInAfter = await mapPointAt(centerX + 25, centerY + 4);
  expect(pinchInScale).toBeLessThan(pinchOutScale);
  expect(pinchInAfter.x).toBeCloseTo(pinchInAnchor.x, 3);
  expect(pinchInAfter.y).toBeCloseTo(pinchInAnchor.y, 3);
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await expect(stage).toHaveAttribute('data-selected-key', 'section-17');

  await stage.evaluate(element => element.addEventListener('pointercancel', () => { (element as HTMLElement).dataset.pointerCancelSeen = 'true'; }, { once: true }));
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [touch(centerX, centerY, 5)] });
  await expect(stage).toHaveClass(/is-manipulating/);
  await client.send('Input.dispatchTouchEvent', { type: 'touchCancel', touchPoints: [] });
  await expect(stage).toHaveAttribute('data-pointer-cancel-seen', 'true');
  await expect(stage).not.toHaveClass(/is-manipulating/);

  await page.getByRole('button', { name: 'Open Area Pokédex' }).click();
  await expect.poll(() => actions.at(-1)).toEqual({ type: 'MAP_AREA', regionKey: 'gen3-region-0', locationKey: 'section-17' });
  await expect(page.getByRole('button', { name: 'Charmeleon' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Unidentified encounter' })).toBeDisabled();
  await expect(page.getByLabel('Day encounter')).toBeVisible();
  await expect(page.getByLabel('Night encounter')).toBeVisible();
  expect(serverState.currentAreaIds).toEqual([171, 181]);
  await page.getByRole('button', { name: 'Charmeleon' }).click();
  await page.getByRole('tab', { name: 'AREA' }).click();
  await expect(page.getByRole('img', { name: 'Hoenn Charmeleon habitat map' })).toBeVisible();
  const pokemonMapBounds = await page.locator('.pokemon-area-canvas').boundingBox();
  expect(pokemonMapBounds!.width / pokemonMapBounds!.height).toBeCloseTo(224 / 120, 2);
  await expect(page.getByRole('button', { name: 'Observed at Oldale Town' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Observed at Petalburg City' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Kanto' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Open selected Area Pokédex' }).locator('svg')).toHaveAttribute('data-semantic-icon', 'pokedex');
  await page.screenshot({ path: join(artifactDir, 'pokemon-area.png') });
  await page.getByRole('button', { name: 'Open selected Area Pokédex' }).click();
  await expect.poll(() => actions.at(-1)).toEqual({ type: 'MAP_AREA', regionKey: 'gen3-region-0', locationKey: 'section-17' });

  serveMaps = false;
  serverState = { ...serverState, version: serverState.version + 1, screen: 'POKEDEX', selectedSpeciesId: null };
  await page.reload();
  await expect(page.getByRole('button', { name: 'Open Map' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Settings' })).toBeVisible();
  await expect(page.locator('.pokedex-screen > .browse-tools')).toHaveCount(1);
  await expect(page.locator('.pokedex-screen > .species-list')).toHaveCount(1);

  writeFileSync(join(artifactDir, 'browser-report.json'), `${JSON.stringify({
    raster: { sha256: rasterSha256, intrinsicWidth: 224, intrinsicHeight: 120 },
    viewport: { width: 1024, height: 768 },
    fit: { width: fit!.width, height: fit!.height, aspect: fit!.width / fit!.height },
    contentStageRatio: stageBounds!.height / hostBounds!.height,
    fogEdges,
    fogDiscovery,
    fogControlledByKnowledgeMode: true,
    onePointerPan: { x: 82, y: 43 },
    pinchOut: { scale: pinchOutScale, anchored: true },
    pinchIn: { scale: pinchInScale, anchored: true },
    pointerCancelCleanup: true,
    pinchSelectionSuppressed: true,
    selectedAreaDexLocationPreserved: true,
    multiBaseAreaIdsPreserved: serverState.currentAreaIds.length === 2,
    pokemonAreaOrganicMaskingAndReturn: true,
    pokemonAreaAspect: pokemonMapBounds!.width / pokemonMapBounds!.height,
    noMapFallback: true,
  }, null, 2)}\n`);
});

test('local POI controls, labels, and zoom visibility remain coherent at Thor geometry', async ({ page }) => {
  expect(localRasterSha256).toBe('e83e6007735aef644647fe6fea027132ca79423fe4b8eec9e339cfe61808222c');
  await page.setViewportSize({ width: 1024, height: 768 });
  const localState = {
    ...state,
    currentAreaBaseId: 9,
    currentAreaName: 'Littleroot Town',
    revealedAreaBaseIds: [9],
    currentMapPosition: { x: 12, y: 10 },
    localMapPois: [
      { key: 'house-player', localMapKey: 'local/0009', baseAreaId: 9, tileX: 7, tileY: 8, category: 'PLACE', state: 'IDENTIFIED', displayName: 'Your House', service: null, itemId: null, itemName: null, destinationBaseAreaId: 256 },
      { key: 'house-birch', localMapKey: 'local/0009', baseAreaId: 9, tileX: 12, tileY: 8, category: 'PLACE', state: 'IDENTIFIED', displayName: "Prof. Birch's House", service: null, itemId: null, itemName: null, destinationBaseAreaId: 258 },
    ],
    localMapPoiPreferences: {
      showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true,
      iconZoomThresholdPercent: 0, labelZoomThresholdPercent: 0,
    },
  };
  const localCatalog = {
    ...catalog,
    localMaps: [
      { key: 'local/0009', displayName: 'Littleroot Town', baseAreaId: 9, pixelWidth: 320, pixelHeight: 320, gridWidth: 20, gridHeight: 20, imageUrl: '/api/maps/local%2F0009%2Fmap.png', dynamicLighting: true },
    ],
    mapScenes: [],
  };
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog: localCatalog, state: localState }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(localState) }));
  await page.route('**/api/actions', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(localState) }));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: localRaster }));

  await page.goto('/');
  await page.getByRole('button', { name: 'Open Map' }).click();
  const stage = page.getByRole('region', { name: 'Interactive local map' });
  const localImage = page.locator('.map-plane > img');
  await expect.poll(() => localImage.evaluate(element => ({
    width: (element as HTMLImageElement).naturalWidth,
    height: (element as HTMLImageElement).naturalHeight,
  }))).toEqual({ width: 320, height: 320 });
  const filter = page.getByRole('button', { name: 'Map POI filters' });
  const zoomIn = page.getByRole('button', { name: 'Zoom in' });
  await expect(filter.locator('svg')).toHaveAttribute('data-semantic-icon', 'filter');
  const controlStyle = async (control: typeof filter) => control.evaluate(element => {
    const style = getComputedStyle(element);
    return { width: style.width, height: style.height, background: style.backgroundColor, border: style.borderTopColor, color: style.color };
  });
  expect(await controlStyle(filter)).toEqual(await controlStyle(zoomIn));
  await expect(page.locator('.map-poi-marker')).toHaveCount(2);
  await expect(page.locator('.map-poi-label')).toHaveCount(2);
  const labelSurface = await page.locator('.map-poi-label').first().evaluate(element => {
    const style = getComputedStyle(element);
    return {
      background: style.backgroundColor,
      backdropFilter: style.backdropFilter,
      textShadow: style.textShadow,
      padding: style.padding,
    };
  });
  expect(labelSurface.background).not.toBe('rgba(0, 0, 0, 0)');
  expect(labelSurface.backdropFilter).not.toBe('none');
  expect(labelSurface.textShadow).not.toBe('none');
  expect(labelSurface.padding).toBe('3px 5px');
  const labelBoxes = await page.locator('.map-poi-label').evaluateAll(labels => labels.map(label => {
    const box = label.getBoundingClientRect();
    return { left: box.left, right: box.right, top: box.top, bottom: box.bottom, clipped: label.scrollWidth > label.clientWidth };
  }));
  expect(labelBoxes.every(label => !label.clipped)).toBe(true);
  expect(labelBoxes[0].right <= labelBoxes[1].left || labelBoxes[1].right <= labelBoxes[0].left ||
    labelBoxes[0].bottom <= labelBoxes[1].top || labelBoxes[1].bottom <= labelBoxes[0].top).toBe(true);
  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'local-pois-starting-zoom.png') });

  const startingScale = Number(await stage.getAttribute('data-scale'));
  await page.getByRole('button', { name: 'Zoom in' }).click();
  expect(Number(await stage.getAttribute('data-scale'))).toBeGreaterThan(startingScale);
  await page.getByRole('button', { name: 'Zoom out' }).click();
  expect(Number(await stage.getAttribute('data-scale'))).toBe(startingScale);
  await expect(page.locator('.map-poi-marker')).toHaveCount(2);
  await expect(page.locator('.map-poi-label')).toHaveCount(2);
});
