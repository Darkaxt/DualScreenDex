import { expect, test, type Page, type Route } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { baseState, battle, catalog } from './passive-insights-ui-fixture';

const artifactDir = process.env.DUALDEX_UI_FIX_ARTIFACT_DIR ?? 'D:/Temp/dualdex-ui-space-regressions';
const placeholder = readFileSync(join(process.cwd(), '..', 'app', 'src', 'main', 'assets', 'icon-lowest.png'));

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
  await page.getByRole('tab', { name: 'COMPACT' }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Back' }).click();

  await expect(page.locator('.pokedex-screen')).toBeVisible();
  expect((await firstRow.boundingBox())?.height).toBe(68);
  expect(await page.locator('.species-list').evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }))).toMatchObject({ scrollHeight: 80 * 68 });

  await page.getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Accessibility' }).click();
  await page.getByRole('slider', { name: 'Font scale' }).fill('1.35');
  await page.getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Back' }).click();

  await expect(page.locator('.pokedex-screen')).toBeVisible();
  const enlargedRows = page.locator('.species-row');
  const firstEnlargedRow = await enlargedRows.nth(0).boundingBox();
  const secondEnlargedRow = await enlargedRows.nth(1).boundingBox();
  expect(firstEnlargedRow?.height).toBe(92);
  expect(firstEnlargedRow!.y + firstEnlargedRow!.height).toBeLessThanOrEqual(secondEnlargedRow!.y);
  const enlargedContentExtent = await enlargedRows.nth(0).evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(enlargedContentExtent.scrollHeight).toBe(enlargedContentExtent.clientHeight);
  expect(await page.locator('.species-list').evaluate(element => element.scrollHeight)).toBe(80 * 92);
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

test('Battle rarity fills only the available content region without scrolling', async ({ page }) => {
  const state = { ...baseState, screen: 'BATTLE', battle, battleTab: 'RARITY' };
  await installHarness(page, catalog, state);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const content = page.locator('.battle-content');
  const card = page.locator('.rarity-card');
  await expect(card).toBeVisible();
  const dimensions = await content.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(dimensions.scrollHeight).toBeLessThanOrEqual(dimensions.clientHeight + 1);

  const contentBounds = await content.boundingBox();
  const cardBounds = await card.boundingBox();
  expect(contentBounds).not.toBeNull();
  expect(cardBounds).not.toBeNull();
  expect(cardBounds!.y).toBeGreaterThan(contentBounds!.y);
  expect(cardBounds!.y + cardBounds!.height).toBeLessThan(contentBounds!.y + contentBounds!.height);

  mkdirSync(artifactDir, { recursive: true });
  await page.screenshot({ path: join(artifactDir, 'battle-rarity-responsive.png') });
});

async function installHarness(page: Page, activeCatalog: unknown, activeState: unknown) {
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

async function json(route: Route, body: unknown) {
  await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
}
