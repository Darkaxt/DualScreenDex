import { expect, test, type Browser, type Locator, type Page, type Route } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { baseState, battle, catalog, specimens } from './passive-insights-ui-fixture';

interface RouteCase {
  label: string;
  state: Record<string, unknown> & { settings: typeof baseState.settings };
  activeCatalog?: typeof catalog;
  open?: (page: Page) => Promise<void>;
}

const artifactDir = process.env.DUALDEX_UI_FIX_ARTIFACT_DIR ?? 'D:/Temp/dualdex-ui-space-regressions';
const thorViewport = { width: 538, height: 445 };
const placeholder = readFileSync(join(process.cwd(), '..', 'app', 'src', 'main', 'assets', 'icon-lowest.png'));
const longMapCatalog = {
  ...catalog,
  localMaps: catalog.localMaps.map(map => ({
    ...map,
    displayName: 'A VERY LONG MODERN EMERALD LOCATION NAME',
  })),
};
const longestAvailableMapCatalog = {
  ...catalog,
  localMaps: catalog.localMaps.map(map => ({
    ...map,
    displayName: 'Ever Grande City',
  })),
};
const longNearbyMemberName = 'SPARK THE UNFORGETTABLE PARTNER';
const longNearbyMoveName = 'SUPERCHARGED THUNDERBOLT BARRAGE';
const longNearbyCatalog = {
  ...catalog,
  moves: catalog.moves.map(move => move.id === 85 ? { ...move, name: longNearbyMoveName } : move),
};
const longNearbyState = {
  ...baseState,
  screen: 'PARTY',
  party: baseState.party.map(member => member.slot === 0 ? { ...member, nickname: longNearbyMemberName } : member),
};
const denseMapState = {
  ...baseState,
  revealedAreaBaseIds: [16, 17],
  localMapPois: [
    { key: 'route-gate', localMapKey: 'local/16', baseAreaId: 16, tileX: 3, tileY: 3, category: 'PLACE', state: 'IDENTIFIED', displayName: 'Route gate', service: null, itemId: null, itemName: null, destinationBaseAreaId: 17 },
    { key: 'pokemon-center', localMapKey: 'local/16', baseAreaId: 16, tileX: 12, tileY: 4, category: 'SERVICE', state: 'IDENTIFIED', displayName: 'Pokémon Center', service: 'POKEMON_CENTER', itemId: null, itemName: null, destinationBaseAreaId: null },
    { key: 'potion', localMapKey: 'local/16', baseAreaId: 16, tileX: 20, tileY: 8, category: 'AVAILABLE_ITEM', state: 'IDENTIFIED', displayName: null, service: null, itemId: 13, itemName: 'Potion', destinationBaseAreaId: null },
  ],
};
const connectedMapCatalog = {
  ...catalog,
  localMaps: [
    ...catalog.localMaps,
    { key: 'local/17', displayName: 'Oldale Town', baseAreaId: 17, pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/local-17.png', dynamicLighting: false },
  ],
  mapScenes: [{
    key: 'scene/16', pixelWidth: 224, pixelHeight: 240, gridWidth: 28, gridHeight: 30,
    placements: [
      { localMapKey: 'local/16', baseAreaId: 16, gridX: 0, gridY: 0, pixelX: 0, pixelY: 0, pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/local.png', dynamicLighting: false },
      { localMapKey: 'local/17', baseAreaId: 17, gridX: 0, gridY: 15, pixelX: 0, pixelY: 120, pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/local-17.png', dynamicLighting: false },
    ],
  }],
};

test('Pokédex identity, two-row tabs, and compact Area state share the viewport', async ({ page }) => {
  const state = { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 };
  await installHarness(page, { ...catalog, worldMaps: [] }, state);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const identity = page.locator('.identity-card');
  const content = page.locator('.detail-content');
  const tabs = page.getByRole('tablist', { name: 'Pokédex detail' }).getByRole('tab');
  await expect(tabs).toHaveCount(5);

  const identityBounds = await identity.boundingBox();
  const contentBounds = await content.boundingBox();
  expect(identityBounds).not.toBeNull();
  expect(contentBounds).not.toBeNull();
  expect(contentBounds!.y).toBeCloseTo(identityBounds!.y + identityBounds!.height, 0);
  expect(contentBounds!.y).toBeLessThan(200);

  const tabRows = new Set<number>();
  for (const tab of await tabs.all()) {
    const bounds = await tab.boundingBox();
    expect(bounds).not.toBeNull();
    tabRows.add(Math.round(bounds!.y));
  }
  expect(tabRows.size).toBe(2);
  await expect(page.locator('.detail-scroll')).toHaveCSS('display', 'contents');
  await expect(content).toHaveCSS('overflow-y', 'auto');

  await page.getByRole('tab', { name: 'AREA' }).click();
  const empty = page.locator('.pokemon-area-empty');
  await expect(empty).toBeVisible();
  const emptyBounds = await empty.boundingBox();
  expect(emptyBounds).not.toBeNull();
  expect(emptyBounds!.height).toBeLessThan(130);
  expect(emptyBounds!.y).toBeGreaterThanOrEqual(contentBounds!.y);
  expect(emptyBounds!.y + emptyBounds!.height).toBeLessThanOrEqual(768);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'pokedex-area-empty.png') });
});

test('Pokédex detail fits and shares compact scrolling at the Thor viewport', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  const state = { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 };
  await installHarness(page, catalog, state);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const screen = page.locator('.detail-screen');
  const header = page.locator('.detail-screen > .app-header');
  const scroller = page.locator('.detail-scroll');
  const identity = page.locator('.identity-card');
  const content = page.locator('.detail-content');
  const tabs = page.getByRole('tablist', { name: 'Pokédex detail' }).getByRole('tab');

  for (const locator of [screen, scroller, identity, content]) {
    const horizontalFit = await locator.evaluate(element => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
    }));
    expect(horizontalFit.scrollWidth).toBeLessThanOrEqual(horizontalFit.clientWidth + 1);
  }

  const screenBounds = await screen.boundingBox();
  expect(screenBounds).not.toBeNull();
  for (const tab of await tabs.all()) {
    const bounds = await tab.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x).toBeGreaterThanOrEqual(screenBounds!.x);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(screenBounds!.x + screenBounds!.width + 1);
  }

  await expect(scroller).toHaveCSS('overflow-y', 'auto');
  await expect(content).toHaveCSS('overflow-y', 'visible');
  const scrollExtent = await scroller.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(scrollExtent.scrollHeight).toBeGreaterThan(scrollExtent.clientHeight);

  const before = {
    header: (await header.boundingBox())!,
    identity: (await identity.boundingBox())!,
    content: (await content.boundingBox())!,
  };
  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'pokedex-detail-thor.png') });
  await scroller.evaluate(element => { element.scrollTop = 100; });
  await expect.poll(() => scroller.evaluate(element => element.scrollTop)).toBeGreaterThan(0);
  const after = {
    header: (await header.boundingBox())!,
    identity: (await identity.boundingBox())!,
    content: (await content.boundingBox())!,
  };
  expect(after.header.y).toBeCloseTo(before.header.y, 0);
  expect(after.identity.y).toBeLessThan(before.identity.y);
  expect(after.content.y).toBeLessThan(before.content.y);
});

test('Pokédex density uses exact shared row geometry at the Thor viewport', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  const species = Array.from({ length: 80 }, (_, index) => ({
    ...catalog.species[0],
    id: index + 1,
    dex: index + 1,
    name: `SPECIES ${index + 1}`,
  }));
  const speciesState = Object.fromEntries(species.map(item => [item.id, {
    seen: true,
    caught: true,
    team: false,
    ballId: null,
  }]));
  await installHarness(page, { ...catalog, species }, {
    ...baseState,
    settings: { ...baseState.settings, knowledgeMode: 'DISCOVERED', density: 'AUTO' },
    speciesState,
  });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const firstRow = page.locator('.species-row').first();
  expect((await firstRow.boundingBox())?.height).toBe(94);
  expect(await page.locator('.species-list').evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }))).toMatchObject({ scrollHeight: 80 * 94 });

  await page.getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Accessibility' }).click();
  await page.getByRole('button', { name: 'COMPACT' }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Back' }).click();

  await expect(page.locator('.pokedex-screen')).toBeVisible();
  expect((await firstRow.boundingBox())?.height).toBe(76);
  expect(await page.locator('.species-list').evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }))).toMatchObject({ scrollHeight: 80 * 76 });

  await page.getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Accessibility' }).click();
  await page.getByRole('slider', { name: 'Font scale' }).fill('1.35');
  await page.getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Back' }).click();

  await expect(page.locator('.pokedex-screen')).toBeVisible();
  const enlargedRows = page.locator('.species-row');
  const firstEnlargedRow = await enlargedRows.nth(0).boundingBox();
  const secondEnlargedRow = await enlargedRows.nth(1).boundingBox();
  expect(firstEnlargedRow?.height).toBe(103);
  expect(firstEnlargedRow!.y + firstEnlargedRow!.height).toBeLessThanOrEqual(secondEnlargedRow!.y);
  const enlargedContentExtent = await enlargedRows.nth(0).evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(enlargedContentExtent.scrollHeight).toBe(enlargedContentExtent.clientHeight);
  expect(await page.locator('.species-list').evaluate(element => element.scrollHeight)).toBe(80 * 103);
});

test('shared tabs and route headings keep one keyboard focus path at the Thor viewport', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  await installHarness(page, catalog, { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const heading = page.getByRole('heading', { level: 1, name: 'POKÉDEX' });
  await expect(page.locator('.detail-screen h1')).toHaveCount(1);
  await expect(heading).toBeFocused();

  const entry = page.getByRole('tab', { name: 'ENTRY' });
  const stats = page.getByRole('tab', { name: 'STATS' });
  const more = page.getByRole('tab', { name: 'MORE' });
  await entry.focus();
  await page.keyboard.press('ArrowRight');
  await expect(stats).toBeFocused();
  await expect(stats).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-labelledby', await stats.getAttribute('id') ?? '');
  await page.keyboard.press('ArrowDown');
  await expect(more).toBeFocused();
  await expect(more).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('Home');
  await expect(entry).toBeFocused();
});

test('scrolled specimen dialogs remain viewport-bound and restore first, middle, and last triggers', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  const longSpecimens = {
    ...specimens,
    specimens: Array.from({ length: 9 }, (_, index) => ({
      ...specimens.specimens[index % specimens.specimens.length],
      key: `individual:${index}`,
      nickname: `SPECIMEN ${index}`,
    })),
  };
  await page.route('**/api/specimens?*', route => json(route, longSpecimens));
  await installHarness(page, catalog, {
    ...baseState,
    screen: 'DETAIL',
    selectedSpeciesId: 25,
    speciesState: {
      ...baseState.speciesState,
      25: { ...baseState.speciesState[25], specimenCount: 9 },
    },
  });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.getByRole('tab', { name: 'MORE' }).click();
  await page.getByRole('button', { name: 'VIEW SPECIMENS' }).click();

  const scroller = page.locator('.specimens-content');
  await expect(page.getByRole('button', { name: 'Open SPECIMEN 8 details' })).toBeVisible();
  for (const index of [0, 4, 8]) {
    const trigger = page.getByRole('button', { name: `Open SPECIMEN ${index} details` });
    await trigger.scrollIntoViewIfNeeded();
    const scrollTop = await scroller.evaluate(element => element.scrollTop);
    await trigger.click();

    const dialog = page.getByRole('dialog', { name: `SPECIMEN ${index} details` });
    const close = page.getByRole('button', { name: `Close SPECIMEN ${index} details` });
    await expect(dialog).toBeVisible();
    await expect(close).toBeFocused();
    expect(await dialog.evaluate(element => element.parentElement?.parentElement?.classList.contains('specimens-screen'))).toBe(true);
    const [hostBounds, dialogBounds, closeBounds] = await Promise.all([
      page.locator('.screen-host').boundingBox(),
      dialog.boundingBox(),
      close.boundingBox(),
    ]);
    expect(hostBounds).not.toBeNull();
    expect(dialogBounds).not.toBeNull();
    expect(closeBounds).not.toBeNull();
    expect(dialogBounds!.x).toBeGreaterThanOrEqual(hostBounds!.x);
    expect(dialogBounds!.y).toBeGreaterThanOrEqual(hostBounds!.y);
    expect(dialogBounds!.x + dialogBounds!.width).toBeLessThanOrEqual(hostBounds!.x + hostBounds!.width + 1);
    expect(dialogBounds!.y + dialogBounds!.height).toBeLessThanOrEqual(hostBounds!.y + hostBounds!.height + 1);
    expect(closeBounds!.width).toBeGreaterThanOrEqual(44);
    expect(closeBounds!.height).toBeGreaterThanOrEqual(44);

    await close.click();
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    expect(await scroller.evaluate(element => element.scrollTop)).toBe(scrollTop);
  }

  mkdirSync(artifactDir, { recursive: true });
  await page.getByRole('button', { name: 'Open SPECIMEN 8 details' }).scrollIntoViewIfNeeded();
  await page.getByRole('button', { name: 'Open SPECIMEN 8 details' }).click();
  await page.screenshot({ path: join(artifactDir, 'specimens-scrolled-dialog-thor.png') });
});

test('global loading and error feedback use a non-obscuring reserved row', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  await installHarness(page, catalog, {
    ...baseState,
    error: 'The latest update could not be applied.',
    loading: { active: true, phase: 'FAMILY_AND_TABLES', completedUnits: 2, totalUnits: 5 },
  });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const host = page.locator('.screen-host');
  const feedback = page.locator('.global-feedback');
  const loading = page.locator('.loading-indicator');
  const error = page.locator('.error-toast');
  await expect(loading).toBeVisible();
  await expect(error).toBeVisible();
  await expect(feedback).toHaveCSS('pointer-events', 'none');
  const [hostBounds, feedbackBounds] = await Promise.all([host.boundingBox(), feedback.boundingBox()]);
  expect(hostBounds).not.toBeNull();
  expect(feedbackBounds).not.toBeNull();
  expect(feedbackBounds!.y).toBeGreaterThanOrEqual(hostBounds!.y + hostBounds!.height - 1);

  const dismiss = page.getByRole('button', { name: 'DISMISS' });
  await expect(dismiss).toHaveCSS('pointer-events', 'auto');
  await dismiss.click();
  await expect(error).toHaveCount(0);
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.getByRole('region', { name: 'Interactive local map' })).toBeVisible();

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'global-feedback-reserved-row-thor.png') });
});

test('Trainer Card and Progress remain touch-reachable at the Thor viewport', async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 445 });
  await installHarness(page, catalog, { ...baseState, screen: 'TRAINER' });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const screen = page.locator('.trainer-screen');
  const card = page.getByRole('button', { name: 'Card' });
  const progress = page.getByRole('button', { name: 'Progress' });
  const screenBounds = await screen.boundingBox();
  expect(screenBounds).not.toBeNull();

  for (const destination of [card, progress]) {
    const bounds = await destination.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.width).toBeGreaterThanOrEqual(44);
    expect(bounds!.height).toBeGreaterThanOrEqual(44);
    expect(bounds!.x).toBeGreaterThanOrEqual(screenBounds!.x);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(screenBounds!.x + screenBounds!.width + 1);
  }

  for (const selector of ['.trainer-screen', '.app-header', '.header-actions', '.trainer-destination-switcher']) {
    const fit = await page.locator(selector).evaluate(element => ({ clientWidth: element.clientWidth, scrollWidth: element.scrollWidth }));
    expect(fit.scrollWidth, selector).toBeLessThanOrEqual(fit.clientWidth + 1);
  }

  await progress.click();
  await expect(progress).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('GAME TOTALS')).toBeVisible();
  await card.click();
  await expect(card).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.trainer-card-shell')).toBeVisible();

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'trainer-destinations-thor.png') });
});

test('long map titles stop before the centered clock and header actions', async ({ page }) => {
  await setUpThorPage(page, longMapCatalog, baseState);
  await page.getByRole('button', { name: 'Open Map' }).click();

  const location = page.locator('.map-current-location');
  const title = location.getByRole('heading', { level: 1 });
  const clock = page.locator('.header-game-clock');
  const actions = page.locator('.map-header-actions');
  await expect(title).toHaveCSS('text-overflow', 'ellipsis');
  await expect(title).toHaveCSS('white-space', 'nowrap');
  await expectHeaderSpacing(location, clock, actions);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'map-long-title-thor.png') });
});

test('longest available map title stops before the centered clock and header actions', async ({ page }) => {
  await setUpThorPage(page, longestAvailableMapCatalog, baseState);
  await page.getByRole('button', { name: 'Open Map' }).click();

  const location = page.locator('.map-current-location');
  await expect(location.getByRole('heading', { level: 1 })).toHaveText('Ever Grande City');
  await expectHeaderSpacing(
    location,
    page.locator('.header-game-clock'),
    page.locator('.map-header-actions'),
  );
});

test('nearby move cards use two balanced columns at the Thor viewport', async ({ page }) => {
  await setUpThorPage(page, longNearbyCatalog, longNearbyState);
  await page.getByRole('button', { name: 'Party Analysis' }).click();

  const moveAction = page.getByRole('button', { name: `Open ${longNearbyMoveName} move` });
  const card = moveAction.locator('..');
  await card.scrollIntoViewIfNeeded();
  const columns = await card.evaluate(element =>
    getComputedStyle(element).gridTemplateColumns.split(' '),
  );
  expect(columns).toHaveLength(2);

  const memberAction = card.getByRole('button', { name: `Open ${longNearbyMemberName} details` });
  await expect(memberAction.locator('strong')).toHaveCSS('text-overflow', 'ellipsis');
  await expect(moveAction.locator('strong')).toHaveCSS('text-overflow', 'ellipsis');
  const [cardBounds, memberBounds, moveBounds] = await Promise.all([
    card.boundingBox(),
    memberAction.boundingBox(),
    moveAction.boundingBox(),
  ]);
  expect(cardBounds).not.toBeNull();
  expect(memberBounds).not.toBeNull();
  expect(moveBounds).not.toBeNull();
  expect(Math.abs(memberBounds!.width - moveBounds!.width)).toBeLessThanOrEqual(1);
  expect(moveBounds!.x + moveBounds!.width).toBeLessThanOrEqual(
    cardBounds!.x + cardBounds!.width - 7,
  );

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'party-nearby-move-thor.png') });
});

test('normal content headers keep the authoritative clock clear at 135% text', async ({ browser }) => {
  test.setTimeout(90_000);
  const routes: RouteCase[] = [
    { label: 'Pokédex Detail', state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 } },
    { label: 'Party', state: { ...baseState, screen: 'PARTY' } },
    {
      label: 'Party Analysis',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyAnalysis,
    },
    { label: 'Trainer', state: { ...baseState, screen: 'TRAINER' } },
    { label: 'Battle', state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' } },
    {
      label: 'Move Detail',
      state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' },
      open: openBattleMoveDetail,
    },
    {
      label: 'Ability Detail',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyAbilityDetail,
    },
    {
      label: 'Nature Detail',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyNatureDetail,
    },
    {
      label: 'Specimens',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openSpecimensScreen,
    },
    {
      label: 'Settings',
      state: { ...baseState, screen: 'SETTINGS' },
      open: openAccessibilitySettings,
    },
  ];

  for (const route of routes) {
    const page = await openRouteCase(browser, route, 1.35);
    const title = page.locator('.app-header .header-title');
    const clock = page.locator('.app-header .header-game-clock');
    await expect(clock, route.label).toHaveCount(1);
    const actions = page.locator('.app-header .header-actions');
    const visibleActions = await actions.count() > 0 ? actions : undefined;
    await expectHeaderSpacing(title, clock, visibleActions, route.label);
    await page.close();
  }
});

test('supported text scales retain horizontal containment and reachable scrolling', async ({ browser }) => {
  test.setTimeout(150_000);
  const routes: RouteCase[] = [
    { label: 'Pokédex Browse', state: baseState },
    {
      label: 'Pokédex Detail More',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openMoreTab,
    },
    {
      label: 'Party Analysis',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyAnalysis,
    },
    {
      label: 'Trainer Progress',
      state: { ...baseState, screen: 'TRAINER' },
      open: openTrainerProgress,
    },
    {
      label: 'Battle Attack',
      state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' },
    },
    {
      label: 'Settings Accessibility',
      state: { ...baseState, screen: 'SETTINGS' },
      open: openAccessibilitySettings,
    },
    { label: 'Setup', state: { ...baseState, screen: 'SETUP' } },
    {
      label: 'Map long title',
      state: baseState,
      activeCatalog: longMapCatalog,
      open: openMapScreen,
    },
    {
      label: 'Area Guide',
      state: baseState,
      open: openAreaGuide,
    },
    {
      label: 'Organic observed habitat',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openObservedAreaTab,
    },
    {
      label: 'Specimen dialog',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openSpecimenDialog,
    },
  ];

  for (const scale of [0.85, 1, 1.35]) {
    for (const route of routes) {
      const page = await openRouteCase(browser, route, scale);
      const context = `${route.label} at ${Math.round(scale * 100)}%`;
      await expectHorizontalContainment(page, context);

      const scrollRegions = await activeScrollOwners(page);
      for (let index = 0; index < await scrollRegions.count(); index += 1) {
        const region = scrollRegions.nth(index);
        const maximum = await region.evaluate(element => Math.max(0, element.scrollHeight - element.clientHeight));
        if (maximum <= 1) continue;
        for (const scrollTop of [0, maximum / 2, maximum]) {
          await region.evaluate((element, top) => { element.scrollTop = top; }, scrollTop);
          await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => resolve())));
          await expectHorizontalContainment(
            page,
            `${context} scroll region ${index} at ${Math.round(scrollTop)}`,
          );
        }
        expect(await region.evaluate(element => element.scrollTop), `${context} scroll region ${index}`).toBeGreaterThanOrEqual(maximum - 1);
      }
      await page.close();
    }
  }
});

test('visible route text respects the Thor physical floor at normal scale', async ({ browser }) => {
  test.setTimeout(120_000);
  const routes: RouteCase[] = [
    { label: 'Pokédex Browse', state: baseState },
    {
      label: 'Pokédex Detail',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
    },
    {
      label: 'Pokédex Detail More',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openMoreTab,
    },
    {
      label: 'Organic Area without observed habitats',
      state: {
        ...baseState,
        screen: 'DETAIL',
        selectedSpeciesId: 25,
        observedAreaBaseIdsBySpecies: { 25: [] },
      },
      open: openAreaTab,
    },
    {
      label: 'Organic observed habitat',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openObservedAreaTab,
    },
    { label: 'Party', state: { ...baseState, screen: 'PARTY' } },
    {
      label: 'Party detail',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyDetail,
    },
    {
      label: 'Party Analysis',
      state: { ...baseState, screen: 'PARTY' },
      open: openPartyAnalysis,
    },
    { label: 'Trainer', state: { ...baseState, screen: 'TRAINER' } },
    {
      label: 'Trainer Progress',
      state: { ...baseState, screen: 'TRAINER' },
      open: openTrainerProgress,
    },
    {
      label: 'Battle Entry',
      state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ENTRY' },
    },
    {
      label: 'Battle Attack',
      state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' },
    },
    {
      label: 'Battle Rarity',
      state: { ...baseState, screen: 'BATTLE', battle, battleTab: 'RARITY' },
    },
    {
      label: 'Settings',
      state: { ...baseState, screen: 'SETTINGS' },
    },
    {
      label: 'Settings Accessibility',
      state: { ...baseState, screen: 'SETTINGS' },
      open: openAccessibilitySettings,
    },
    {
      label: 'Setup',
      state: { ...baseState, screen: 'SETUP' },
    },
    {
      label: 'Dense connected Local map',
      state: denseMapState,
      activeCatalog: connectedMapCatalog,
      open: openDenseMap,
    },
    {
      label: 'Area Guide',
      state: baseState,
      open: openAreaGuide,
    },
    {
      label: 'Connection recovery',
      state: baseState,
      open: openConnectionRecovery,
    },
    {
      label: 'Specimens',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openSpecimensScreen,
    },
    {
      label: 'Specimen dialog',
      state: { ...baseState, screen: 'DETAIL', selectedSpeciesId: 25 },
      open: openSpecimenDialog,
    },
  ];

  for (const route of routes) {
    const page = await openRouteCase(browser, route);
    const scrollOwners = await activeScrollOwners(page);
    const ownerCount = await scrollOwners.count();
    if (ownerCount === 0) {
      await expectRouteTextFloor(page, route.label);
    }
    for (let index = 0; index < ownerCount; index += 1) {
      const owner = scrollOwners.nth(index);
      const maximum = await owner.evaluate(element => Math.max(0, element.scrollHeight - element.clientHeight));
      const positions = maximum > 1 ? [0, maximum / 2, maximum] : [0];
      for (const scrollTop of positions) {
        await owner.evaluate((element, top) => { element.scrollTop = top; }, scrollTop);
        await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => resolve())));
        await expectRouteTextFloor(
          page,
          `${route.label} scroll owner ${index} at ${Math.round(scrollTop)}`,
        );
      }
    }
    await page.close();
  }
});

test('Battle Attack fits the exact Thor content region without scrolling', async ({ page }) => {
  const state = { ...baseState, screen: 'BATTLE', battle, battleTab: 'ATTACK' };
  await setUpThorPage(page, catalog, state);

  const content = page.locator('.battle-content');
  const card = page.locator('.attack-card');
  await expect(card).toBeVisible();
  const dimensions = await content.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(dimensions.clientHeight).toBe(203);
  expect(dimensions.scrollHeight).toBeLessThanOrEqual(dimensions.clientHeight + 1);

  const contentBounds = await content.boundingBox();
  const cardBounds = await card.boundingBox();
  expect(contentBounds).not.toBeNull();
  expect(cardBounds).not.toBeNull();
  expect(cardBounds!.y).toBeGreaterThanOrEqual(contentBounds!.y);
  expect(cardBounds!.y + cardBounds!.height).toBeLessThanOrEqual(contentBounds!.y + contentBounds!.height + 1);

  const moveActionBounds = await page.getByRole('button', { name: 'THUNDERBOLT' }).boundingBox();
  expect(moveActionBounds).not.toBeNull();
  expect(moveActionBounds!.width).toBeGreaterThanOrEqual(44);
  expect(moveActionBounds!.height).toBeGreaterThanOrEqual(44);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'battle-attack-thor.png') });
});

test('Battle rarity fills only the available content region without scrolling', async ({ page }) => {
  const state = { ...baseState, screen: 'BATTLE', battle, battleTab: 'RARITY' };
  await setUpThorPage(page, catalog, state);

  const content = page.locator('.battle-content');
  const card = page.locator('.rarity-card');
  await expect(card).toBeVisible();
  const dimensions = await content.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(dimensions.scrollHeight).toBeLessThanOrEqual(dimensions.clientHeight + 1);
  const cardDimensions = await card.evaluate(element => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth,
  }));
  expect(cardDimensions.scrollWidth).toBeLessThanOrEqual(cardDimensions.clientWidth + 1);

  const contentBounds = await content.boundingBox();
  const cardBounds = await card.boundingBox();
  expect(contentBounds).not.toBeNull();
  expect(cardBounds).not.toBeNull();
  expect(cardBounds!.y).toBeGreaterThan(contentBounds!.y);
  expect(cardBounds!.y + cardBounds!.height).toBeLessThan(contentBounds!.y + contentBounds!.height);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'battle-rarity-responsive.png') });
});

async function setUpThorPage(page: Page, activeCatalog: unknown, activeState: unknown): Promise<void> {
  await page.setViewportSize(thorViewport);
  await installHarness(page, activeCatalog, activeState);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
}

async function openRouteCase(browser: Browser, route: RouteCase, fontScale?: number): Promise<Page> {
  const page = await browser.newPage();
  const activeState = fontScale === undefined
    ? route.state
    : {
      ...route.state,
      settings: { ...route.state.settings, fontScale },
    };
  await setUpThorPage(page, route.activeCatalog ?? catalog, activeState);
  await expect(page.locator('.screen:not(.welcome-screen)')).toBeVisible();
  await route.open?.(page);
  return page;
}

async function expectHorizontalContainment(page: Page, context: string): Promise<void> {
  const containment = await page.locator('.screen').evaluate(screen => {
    const bounds = screen.getBoundingClientRect();
    const clippedControls = Array.from(screen.querySelectorAll<HTMLElement>('button, a[href], input, select'))
      .filter(element => !element.closest('.map-plane'))
      .flatMap(element => {
        const style = getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        const visible = style.display !== 'none' && style.visibility !== 'hidden' &&
          Number(style.opacity) > 0 && rect.width > 0 && rect.height > 0 &&
          rect.right > bounds.left && rect.left < bounds.right &&
          rect.bottom > bounds.top && rect.top < bounds.bottom;
        if (!visible || rect.left >= bounds.left - 1 && rect.right <= bounds.right + 1) return [];
        return [`${element.tagName.toLowerCase()}.${element.className}: ${rect.left}-${rect.right}`];
      });
    return {
      clientWidth: screen.clientWidth,
      scrollWidth: screen.scrollWidth,
      clippedControls,
    };
  });
  expect(containment.scrollWidth, context).toBeLessThanOrEqual(containment.clientWidth + 1);
  expect(containment.clippedControls, context).toEqual([]);
}

async function activeScrollOwners(page: Page): Promise<Locator> {
  await page.locator('.screen').evaluate(root => {
    root.querySelectorAll('[data-e2e-scroll-owner]').forEach(element =>
      element.removeAttribute('data-e2e-scroll-owner'),
    );
    const owners = Array.from(root.querySelectorAll<HTMLElement>('*')).filter(element => {
      if (element.closest('[inert], [aria-hidden="true"]')) return false;
      const style = getComputedStyle(element);
      const bounds = element.getBoundingClientRect();
      return ['auto', 'scroll'].includes(style.overflowY) &&
        element.scrollHeight > element.clientHeight + 1 &&
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        Number(style.opacity) > 0 &&
        bounds.width > 0 &&
        bounds.height > 0 &&
        bounds.right > 0 &&
        bounds.bottom > 0 &&
        bounds.left < innerWidth &&
        bounds.top < innerHeight;
    });
    owners.forEach((element, index) =>
      element.setAttribute('data-e2e-scroll-owner', String(index)),
    );
  });
  return page.locator('[data-e2e-scroll-owner]');
}

async function expectHeaderSpacing(
  leading: Locator,
  clock: Locator,
  actions?: Locator,
  context?: string,
): Promise<void> {
  const [leadingBounds, clockBounds] = await Promise.all([
    leading.boundingBox(),
    clock.boundingBox(),
  ]);
  expect(leadingBounds, context).not.toBeNull();
  expect(clockBounds, context).not.toBeNull();
  expect(leadingBounds!.x + leadingBounds!.width, context).toBeLessThanOrEqual(clockBounds!.x - 8);

  if (actions) {
    const actionBounds = await actions.boundingBox();
    expect(actionBounds, context).not.toBeNull();
    expect(clockBounds!.x + clockBounds!.width, context).toBeLessThanOrEqual(actionBounds!.x - 8);
  }
}

async function openMoreTab(page: Page): Promise<void> {
  await page.getByRole('tab', { name: 'MORE' }).click();
}

async function openAreaTab(page: Page): Promise<void> {
  await page.getByRole('tab', { name: 'AREA' }).click();
  await expect(page.locator('.pokemon-area-empty')).toBeVisible();
  await expectVisibleTextFloor(page, '.pokemon-area-empty');
}

async function openObservedAreaTab(page: Page): Promise<void> {
  await page.getByRole('tab', { name: 'AREA' }).click();
  await expect(page.locator('.pokemon-area-panel')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Observed at Route 101' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open selected Area Pokédex' })).toBeVisible();
  await expectVisibleTextFloor(page, '.pokemon-area-panel > header');
}

async function openPartyDetail(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Party slot 1: SPARK' }).click();
  await expect(page.getByRole('dialog', { name: 'SPARK details' })).toBeVisible();
}

async function openPartyAnalysis(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Party Analysis' }).click();
  await expect(page.locator('.party-analysis-screen')).toBeVisible();
}

async function openBattleMoveDetail(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'THUNDERBOLT' }).click();
  await expect(page.locator('.move-detail-screen')).toBeVisible();
}

async function openPartyAbilityDetail(page: Page): Promise<void> {
  await openPartyDetail(page);
  await page.getByRole('button', { name: 'STATIC' }).click();
  await expect(page.locator('.ability-detail-screen')).toBeVisible();
}

async function openPartyNatureDetail(page: Page): Promise<void> {
  await openPartyDetail(page);
  await page.getByRole('button', { name: 'Adamant' }).click();
  await expect(page.locator('.nature-detail-screen')).toBeVisible();
}

async function openTrainerProgress(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Progress' }).click();
}

async function openAccessibilitySettings(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Accessibility' }).click();
}

async function openMapScreen(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Open Map' }).click();
  await expect(page.locator('.map-screen')).toBeVisible();
}

async function openDenseMap(page: Page): Promise<void> {
  await openMapScreen(page);
  await expectVisibleTextFloor(page, '.map-local-poi-label');
  await expectVisibleTextFloor(page, '.map-poi-label');
}

async function openConnectionRecovery(page: Page): Promise<void> {
  await page.unroute('**/api/state?*');
  await page.route('**/api/state?*', route => route.abort());
  await expect(page.locator('.connection-toast')).toBeVisible({ timeout: 5_000 });
  await expectVisibleTextFloor(page, '.connection-toast');
}

async function expectVisibleTextFloor(page: Page, selector: string): Promise<void> {
  const measurements = await page.locator(selector).evaluateAll(elements => elements.map(element => {
    const bounds = element.getBoundingClientRect();
    return {
      fontSize: Number.parseFloat(getComputedStyle(element).fontSize),
      inViewport: bounds.right > 0 && bounds.bottom > 0 && bounds.left < innerWidth && bounds.top < innerHeight,
    };
  }));
  const visible = measurements.filter(measurement => measurement.inViewport);
  expect(visible, `${selector}: ${JSON.stringify(measurements)}`).not.toEqual([]);
  expect(Math.min(...visible.map(measurement => measurement.fontSize)), selector).toBeGreaterThanOrEqual(11.19);
}

async function openAreaGuide(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Open Map' }).click();
  await page.getByRole('button', { name: 'Area Guide' }).click();
  await expect(page.locator('.area-guide-drawer')).toBeVisible();
}

async function openSpecimens(page: Page): Promise<void> {
  await page.route('**/api/specimens?*', route => json(route, specimens));
  await page.getByRole('tab', { name: 'MORE' }).click();
  await page.getByRole('button', { name: 'VIEW SPECIMENS' }).click();
}

async function openSpecimensScreen(page: Page): Promise<void> {
  await openSpecimens(page);
  await expect(page.locator('.specimens-screen')).toBeVisible();
}

async function openSpecimenDialog(page: Page): Promise<void> {
  await openSpecimens(page);
  await page.getByRole('button', { name: 'Open SPARK details' }).click();
  await expect(page.getByRole('dialog', { name: 'SPARK details' })).toBeVisible();
}

async function expectRouteTextFloor(page: Page, context: string): Promise<void> {
  const metrics = await visibleTextMetrics(page);
  expect(metrics.underFloor, `${context}: ${metrics.underFloor.join(', ')}`).toEqual([]);
  expect(metrics.average, `${context}: ${JSON.stringify(metrics)}`).toBeGreaterThanOrEqual(12);
}

async function visibleTextMetrics(page: Page): Promise<{ average: number; underFloor: string[] }> {
  return page.locator('.screen').evaluate(root => {
    const isVisible = (element: HTMLElement, bounds: DOMRect) => {
      if (element.closest('[aria-hidden="true"]')) return false;
      for (let current: HTMLElement | null = element; current; current = current.parentElement) {
        const style = getComputedStyle(current);
        if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false;
        if (current === root) break;
      }
      return bounds.width > 0 &&
        bounds.height > 0 &&
        bounds.right > 0 &&
        bounds.bottom > 0 &&
        bounds.left < innerWidth &&
        bounds.top < innerHeight;
    };
    const label = (element: HTMLElement, text: string) => ({
      selector: `${element.tagName.toLowerCase()}${Array.from(element.classList).map(name => `.${name}`).join('')}`,
      text: text.slice(0, 36),
      size: Number.parseFloat(getComputedStyle(element).fontSize),
    });
    const entries: Array<{ selector: string; text: string; size: number }> = [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const text = node.textContent?.trim() ?? '';
      const element = node.parentElement;
      if (!text || !(element instanceof HTMLElement)) continue;
      const range = document.createRange();
      range.selectNodeContents(node);
      if (isVisible(element, range.getBoundingClientRect())) entries.push(label(element, text));
    }
    root.querySelectorAll<HTMLElement>('input, select, textarea').forEach(element => {
      const bounds = element.getBoundingClientRect();
      if (isVisible(element, bounds)) entries.push(label(element, element.getAttribute('aria-label') ?? element.tagName));
    });
    const sizes = entries.map(entry => entry.size).filter(Number.isFinite);
    return {
      average: sizes.reduce((sum, size) => sum + size, 0) / Math.max(1, sizes.length),
      underFloor: entries
        .filter(entry => Number.isFinite(entry.size) && entry.size < 11.19)
        .map(entry => `${entry.selector}=${entry.size}px (${entry.text})`),
    };
  });
}

async function installHarness(page: Page, activeCatalog: unknown, activeState: unknown): Promise<void> {
  let currentState = activeState as Record<string, unknown>;
  await page.route('**/api/bootstrap', route => json(route, { catalog: activeCatalog, state: currentState }));
  await page.route('**/api/state?*', route => json(route, currentState));
  await page.route('**/api/sprites/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/actions', route => {
    const action = route.request().postDataJSON() as Record<string, unknown>;
    if (action.type === 'TRAINER_DESTINATION') {
      currentState = {
        ...currentState,
        version: Number(currentState.version ?? 0) + 1,
        trainerProgress: {
          ...(currentState.trainerProgress as Record<string, unknown>),
          selectedDestination: action.value,
        },
      };
    } else if (action.type === 'SCREEN') {
      currentState = {
        ...currentState,
        version: Number(currentState.version ?? 0) + 1,
        screen: action.screen,
      };
    } else if (action.type === 'SETTINGS') {
      const { type: _type, ...values } = action;
      currentState = {
        ...currentState,
        version: Number(currentState.version ?? 0) + 1,
        settings: {
          ...(currentState.settings as Record<string, unknown>),
          ...values,
        },
      };
    }
    return json(route, currentState);
  });
}

async function json(route: Route, body: unknown): Promise<void> {
  await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
}
