import { expect, test, type Page, type Route } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { baseState, battle, catalog, specimens } from './passive-insights-ui-fixture';

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
  await page.getByRole('button', { name: 'COMPACT' }).click();
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
