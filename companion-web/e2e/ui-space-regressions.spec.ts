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
  await page.route('**/api/bootstrap', route => json(route, { catalog: activeCatalog, state: activeState }));
  await page.route('**/api/state?*', route => json(route, activeState));
  await page.route('**/api/sprites/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/maps/**', route => route.fulfill({ contentType: 'image/png', body: placeholder }));
  await page.route('**/api/actions', route => json(route, activeState));
}

async function json(route: Route, body: unknown) {
  await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
}
