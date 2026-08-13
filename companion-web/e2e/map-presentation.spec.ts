import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const fixturePath = process.env.DUALDEX_MAP_FIXTURE_PNG;
if (!fixturePath) throw new Error('Set DUALDEX_MAP_FIXTURE_PNG to a sanitized normalized 224x120 Emerald PNG');
const raster = readFileSync(fixturePath);
const rasterSha256 = createHash('sha256').update(raster).digest('hex');
const artifactDir = join(process.cwd(), '..', 'output', 'map-presentation');

const state = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'AREA', selectedAreaId: null, currentAreaBaseId: 16, currentAreaName: 'Route 101', battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'Emerald control', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
};

const catalog = {
  hash: 'sanitized-browser-control', crc32: 'CONTROL', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [
      { key: 'section-16', displayName: 'Route 101', baseAreaIds: [16], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
      { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [17], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
    ],
  }],
};

test('real 4:3 map presentation, gestures, fog, and no-map fallback', async ({ page, context }) => {
  let serveMaps = true;
  await page.route('**/api/bootstrap', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ catalog: { ...catalog, worldMaps: serveMaps ? catalog.worldMaps : [] }, state }) }));
  await page.route('**/api/state', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) }));
  await page.route('**/api/actions', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(state) }));
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
  await page.getByRole('button', { name: 'Toggle fog of war' }).click();
  await expect(page.locator('.map-fog')).toHaveCount(0);
  await page.screenshot({ path: join(artifactDir, 'fog-off.png') });
  await page.getByRole('button', { name: 'Oldale Town' }).click();
  await expect(stage).toHaveAttribute('data-selected-key', 'section-17');
  await page.getByRole('button', { name: 'Toggle fog of war' }).click();

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

  serveMaps = false;
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
    onePointerPan: { x: 82, y: 43 },
    pinchOut: { scale: pinchOutScale, anchored: true },
    pinchIn: { scale: pinchInScale, anchored: true },
    pointerCancelCleanup: true,
    pinchSelectionSuppressed: true,
    noMapFallback: true,
  }, null, 2)}\n`);
});
