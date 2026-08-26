import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bootstrap } from './models';

const fixture: Bootstrap = {
  catalog: {
    hash: 'sha', crc32: 'C3A9F204', family: 'EMERALD', platform: 'GBA', rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
    species: [], moves: [], types: [],
    areas: [{ id: 0x11 * 10 + 1, baseAreaId: 0x11, name: 'Oldale grass', methodId: 1, speciesIds: [], windows: ['ANY'], slots: [] }],
    balls: [], capabilities: {},
    theme: {
      method: 'MULTI_ASSET_QUANTIZATION', assetClasses: ['WORLD_MAP', 'SPECIES'], contrastCorrected: true,
      tokens: {
        field: '#123456', fieldPattern: '#234567', header: '#345678', headerShadow: '#102030',
        menu: '#e4d6a8', menuShadow: '#75694b', panel: '#fff7db', border: '#4d4032',
        text: '#1c201d', textShadow: '#ffffff', accent: '#9d302a', accentText: '#ffffff',
      },
    },
    worldMaps: [{
      key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
      imageUrl: '/api/maps/world%2Fgen3-region-0.png',
      locations: [
        { key: 'section-16', displayName: 'Route 101', baseAreaIds: [16], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
        { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [17], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
      ],
    }]
  },
  state: {
    version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX',
    selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null, currentAreaBaseId: 16, revealedAreaBaseIds: [16, 17], battleTab: 'ENTRY',
    gameTime: { hours: 16, minutes: 48 },
    settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: {}, observedMoves: {}, battle: null, catalogReady: true,
    trainerCardUnlocked: true,
    trainer: {
      name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 98765, playTimeHours: 12, playTimeMinutes: 34,
      dexSeen: 42, dexCaught: 7, stars: 2, avatarUrl: null,
      badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index === 0, imageUrl: null })),
    },
    party: [{ slot: 0, occupied: true, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: null, typeIds: [], nickname: 'SPARK', level: 18, isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [] }],
    catalogName: 'Pokemon Modern Emerald.gba', error: null, activeRulesetId: null,
    rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 }
  }
};

vi.mock('./gateway', () => ({
  bootstrap: vi.fn(async () => fixture),
  action: vi.fn(async (type: string, values: Record<string, unknown> = {}) => ({ ...fixture.state, screen: type === 'SCREEN' ? values.screen : fixture.state.screen })),
  events: vi.fn(() => () => undefined),
  uploadRom: vi.fn(async () => fixture),
  diagnostics: vi.fn(async () => ({
    romName: fixture.state.catalogName, sha256: fixture.catalog!.hash, crc32: fixture.catalog!.crc32,
    family: fixture.catalog!.family, platform: fixture.catalog!.platform, activeRulesetId: 'default', rulesetAssumed: true,
    rulesets: fixture.catalog!.rulesets, capabilities: [], parserDiagnostics: [], species: null, move: null,
  }))
}));

import { action, bootstrap, events } from './gateway';
import { App, catalogRefreshMarker, loadingModuleLabel, loadingOriginClass } from './App';

afterEach(cleanup);

describe('production application shell', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.body.replaceChildren();
    class TestResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe(target: Element) { this.callback([{ target, contentRect: target.getBoundingClientRect() } as ResizeObserverEntry], this as unknown as ResizeObserver); }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as typeof HTMLCanvasElement.prototype.getContext;
  });

  it('does not replace application state with an equal-version heartbeat', async () => {
    let publishState!: (state: typeof fixture.state) => void;
    vi.mocked(events).mockImplementationOnce((_currentVersion, onState) => {
      publishState = onState as (state: typeof fixture.state) => void;
      return () => undefined;
    });
    render(<App />);
    await waitFor(() => expect(document.querySelector('.production-device')?.getAttribute('data-theme')).toBe('game'));
    await waitFor(() => expect(publishState).toBeTypeOf('function'));

    publishState({
      ...fixture.state,
      settings: { ...fixture.state.settings, theme: 'DARK' },
    });

    await Promise.resolve();
    expect(document.querySelector('.production-device')?.getAttribute('data-theme')).toBe('game');
  });

  it('keeps ROM diagnostics out of the production shell', async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByText('POKÉDEX')).toBeTruthy());
    expect(screen.queryByText('Pokemon Modern Emerald.gba')).toBeNull();
    expect(screen.queryByText(/CRC32 C3A9F204/)).toBeNull();
    expect(document.querySelector('.rom-status')).toBeNull();
    expect(document.querySelector('.screen-host')?.classList.contains('with-rom-status')).toBe(false);
    expect(screen.queryByText('Encounter feed')).toBeNull();
    expect(screen.queryByText('GENERATE ENCOUNTER')).toBeNull();
    expect(screen.queryByText(/Generate an encounter/i)).toBeNull();
  });

  it('applies the complete ROM palette only to GAME and leaves fixed themes authoritative', async () => {
    const first = render(<App />);
    await waitFor(() => expect(screen.getByText('POKÉDEX')).toBeTruthy());
    const gameShell = document.querySelector('.production-device') as HTMLElement;
    expect(gameShell.dataset.theme).toBe('game');
    expect(gameShell.style.getPropertyValue('--theme-field')).toBe('#123456');
    expect(gameShell.style.getPropertyValue('--theme-accent-text')).toBe('#ffffff');
    first.unmount();

    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: { ...fixture.state, settings: { ...fixture.state.settings, theme: 'DARK' } },
    });
    render(<App />);
    await waitFor(() => expect(screen.getByText('POKÉDEX')).toBeTruthy());
    const darkShell = document.querySelector('.production-device') as HTMLElement;
    expect(darkShell.dataset.theme).toBe('dark');
    expect(darkShell.style.getPropertyValue('--theme-field')).toBe('');
  });

  it('uses a concise welcome prompt without implementation commentary', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: null,
      state: { ...fixture.state, catalogReady: false },
    });

    render(<App />);

    expect(await screen.findByText('Choose a Pokémon game to begin.')).toBeTruthy();
    expect(screen.queryByText('PASSIVE RETROARCH COMPANION')).toBeNull();
    expect(screen.queryByText(/Game Boy Advance Pokémon ROM/)).toBeNull();
    expect(screen.queryByText(/extracted assets stay local/)).toBeNull();
  });

  it('keeps Trainer and Party shortcuts inside the existing application header', async () => {
    render(<App />);

    const trainer = await screen.findByRole('button', { name: 'Trainer Card' });
    const party = screen.getByRole('button', { name: 'Party' });
    expect(trainer.closest('.app-header')).toBeTruthy();
    expect(party.closest('.app-header')).toBeTruthy();
    expect(document.querySelector('[data-player-navigation-row]')).toBeNull();

    fireEvent.click(trainer);
    expect(action).toHaveBeenCalledWith('OPEN_TRAINER', {});
  });

  it('drives the Trainer Card shortcut only from the first Pokemon license', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: { ...fixture.state, trainerCardUnlocked: false },
    });
    const locked = render(<App />);

    await screen.findByText('POKÉDEX');
    expect(screen.queryByRole('button', { name: 'Trainer Card' })).toBeNull();
    locked.unmount();

    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: { ...fixture.state, trainerCardUnlocked: true, trainer: null },
    });
    render(<App />);

    expect(await screen.findByRole('button', { name: 'Trainer Card' })).toBeTruthy();
  });

  it('returns from a Party ability to the same Pokemon detail before the Party grid', async () => {
    const ability = { id: 65, name: 'Overgrow', description: 'Powers up Grass-type moves when HP is low.', mechanics: [] };
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: {
        ...fixture.catalog!,
        species: [{
          id: 25, dex: 25, name: 'PIKACHU', typeIds: [], stats: null, description: null, height: null, weight: null,
          learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [ability], evolutions: [], hasSprite: false,
        }],
      },
      state: {
        ...fixture.state,
        party: [{ ...fixture.state.party![0], abilityId: 65, abilityName: 'Overgrow' }],
      },
    });
    vi.mocked(action).mockResolvedValueOnce({ ...fixture.state, screen: 'PARTY', party: [{ ...fixture.state.party![0], abilityId: 65, abilityName: 'Overgrow' }] });
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Party' }));
    fireEvent.click(await screen.findByRole('button', { name: /Party slot 1: SPARK/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Overgrow' }));
    expect(screen.getByText('Powers up Grass-type moves when HP is low.')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByRole('dialog', { name: 'SPARK details' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Close SPARK details' }));
    expect(screen.queryByRole('dialog', { name: 'SPARK details' })).toBeNull();
    expect(screen.getByRole('button', { name: /Party slot 1: SPARK/i })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Party slot 1: SPARK/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Overgrow' }));
    window.dispatchEvent(new Event('dualdexback', { cancelable: true }));
    await waitFor(() => expect(screen.getByRole('dialog', { name: 'SPARK details' })).toBeTruthy());
  });

  it('replaces setup actions with real catalog loading progress', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: null,
      state: {
        ...fixture.state,
        version: 2,
        catalogReady: false,
        loading: { active: true, phase: 'ROM_IDENTITY', completedUnits: 0, totalUnits: 11 },
      },
    });

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Checking the game' });
    expect(loading.classList.contains('loading-origin-parse')).toBe(true);
    expect(loading.textContent).toBe('Checking the game');
    expect(loading.textContent).not.toContain('%');
    const progress = screen.getByRole('progressbar', { name: 'Checking the game' });
    expect(progress.getAttribute('aria-valuemin')).toBe('0');
    expect(progress.getAttribute('aria-valuemax')).toBe('11');
    expect(progress.getAttribute('aria-valuenow')).toBe('0');
    expect(screen.queryByText('LOAD ROM OR ZIP')).toBeNull();
    expect(screen.queryByRole('button', { name: 'CONNECT RETROARCH' })).toBeNull();
    expect(screen.queryByText('Choose a Pokémon game to begin.')).toBeNull();
  });

  it('marks a persisted catalog reopen in yellow without changing progress semantics', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: null,
      state: {
        ...fixture.state,
        version: 2,
        catalogReady: false,
        loading: { active: true, phase: 'CACHE_REOPEN', completedUnits: 0, totalUnits: 1 },
      },
    });

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Opening your game guide' });
    expect(loading.classList.contains('loading-origin-cache')).toBe(true);
    expect(loading.classList.contains('loading-origin-parse')).toBe(false);
    const progress = screen.getByRole('progressbar', { name: 'Opening your game guide' });
    expect(progress.getAttribute('aria-valuemax')).toBe('1');
    expect(progress.getAttribute('aria-valuenow')).toBe('0');
  });

  it('replaces completed catalog progress with a waiting spinner until live game access is ready', async () => {
    let publishState!: (state: typeof fixture.state & { gameAccessReady: boolean }) => void;
    vi.mocked(events).mockImplementationOnce((_currentVersion, onState) => {
      publishState = onState as typeof publishState;
      return () => undefined;
    });
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: {
        ...fixture.state,
        version: 2,
        gameAccessReady: false,
        retroArch: {
          storageGrant: 'GRANTED', configGrant: 'GRANTED', romGrant: 'GRANTED', configState: 'VERIFIED',
          restartRequired: false, connection: 'PLAYING', systemId: 'Nintendo - Game Boy Advance',
          gameBasename: 'Modern Emerald', contentCrc32: fixture.catalog!.crc32, resolution: 'ACTIVE',
          activeSource: 'Modern Emerald.gba', savefileDirectory: null, indexedRoms: 1, message: null,
        },
        saveRam: {
          status: 'MATCHED', sourceName: 'Modern Emerald.srm', sourceLastModifiedEpochMs: 1,
          refreshedAtEpochMs: 1, autosaveStatus: 'UNVERIFIED', capabilities: {}, candidates: [], message: null,
        },
      },
    });

    render(<App />);

    const waiting = await screen.findByRole('status', { name: 'Waiting for in-game access' });
    expect(waiting.querySelector('.welcome-waiting-spinner')).toBeTruthy();
    expect(screen.queryByRole('progressbar')).toBeNull();
    expect(screen.queryByText('POKÉDEX')).toBeNull();
    expect(await screen.findByText('Waiting for the game to finish initializing.')).toBeTruthy();

    publishState({
      ...fixture.state,
      version: 3,
      gameAccessReady: true,
      retroArch: {
        storageGrant: 'GRANTED', configGrant: 'GRANTED', romGrant: 'GRANTED', configState: 'VERIFIED',
        restartRequired: false, connection: 'PLAYING', systemId: 'Nintendo - Game Boy Advance',
        gameBasename: 'Modern Emerald', contentCrc32: fixture.catalog!.crc32, resolution: 'ACTIVE',
        activeSource: 'Modern Emerald.gba', savefileDirectory: null, indexedRoms: 1, message: null,
      },
    });

    await waitFor(() => expect(screen.getByText('POKÉDEX')).toBeTruthy());
    expect(screen.queryByRole('status', { name: 'Waiting for in-game access' })).toBeNull();
  });

  it('does not gate a manually opened catalog on RetroArch game initialization', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: { ...fixture.state, gameAccessReady: false },
    });

    render(<App />);

    expect(await screen.findByText('POKÉDEX')).toBeTruthy();
    expect(screen.queryByRole('status', { name: 'Waiting for in-game access' })).toBeNull();
  });

  it('explains an automatic saved-guide refresh without exposing parser diagnostics', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: null,
      state: {
        ...fixture.state,
        version: 2,
        catalogReady: false,
        loading: {
          active: true,
          phase: 'ROM_IDENTITY',
          completedUnits: 0,
          totalUnits: 11,
          message: 'Saved guide data needs to be refreshed for this version.',
        },
      },
    });

    render(<App />);

    expect(await screen.findByText('Saved guide data needs to be refreshed for this version.')).toBeTruthy();
    expect(document.querySelector('.welcome-loading-note')).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/schema|sha-?256|parser/i);
  });

  it('shows indeterminate companion progress while bootstrap is pending', async () => {
    let resolveBootstrap!: (value: Bootstrap) => void;
    vi.mocked(bootstrap).mockImplementationOnce(() => new Promise(resolve => { resolveBootstrap = resolve; }));

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Preparing your companion' });
    expect(screen.getByRole('progressbar', { name: 'Preparing your companion' }).hasAttribute('aria-valuenow')).toBe(false);
    expect(screen.queryByText('LOAD ROM OR ZIP')).toBeNull();
    expect(screen.queryByRole('button', { name: 'CONNECT RETROARCH' })).toBeNull();
    expect(screen.queryByText('Choose a Pokémon game to begin.')).toBeNull();

    resolveBootstrap(fixture);
  });

  it('uses concise names for every parser module and humanizes future phases', () => {
    expect(loadingModuleLabel('ROM_IDENTITY')).toBe('Checking the game');
    expect(loadingModuleLabel('FAMILY_AND_TABLES')).toBe('Finding game data');
    expect(loadingModuleLabel('CORE_RECORDS')).toBe('Reading Pokémon and moves');
    expect(loadingModuleLabel('SPECIES_MEDIA')).toBe('Preparing artwork and entries');
    expect(loadingModuleLabel('EVOLUTIONS_AND_LEARNSETS')).toBe('Reading evolutions and learnsets');
    expect(loadingModuleLabel('ENCOUNTERS')).toBe('Finding wild encounters');
    expect(loadingModuleLabel('MOVE_DATA')).toBe('Reading move details');
    expect(loadingModuleLabel('ABILITY_DATA')).toBe('Reading ability details');
    expect(loadingModuleLabel('MAPS')).toBe('Preparing maps');
    expect(loadingModuleLabel('TRAINER_AND_THEME')).toBe('Preparing your Trainer Card');
    expect(loadingModuleLabel('CATALOG_STORAGE')).toBe('Saving your game guide');
    expect(loadingModuleLabel('CACHE_REOPEN')).toBe('Opening your game guide');
    expect(loadingModuleLabel('FUTURE_PHASE')).toBe('Preparing your companion');
    expect(loadingOriginClass({ active: true, phase: 'ROM_IDENTITY', completedUnits: 0, totalUnits: 11 })).toBe('loading-origin-parse');
    expect(loadingOriginClass({ active: true, phase: 'CACHE_REOPEN', completedUnits: 0, totalUnits: 1 })).toBe('loading-origin-cache');
    expect(loadingOriginClass({ active: false, phase: 'COMPLETE', completedUnits: 11, totalUnits: 11 })).toBe('');
  });

  it('keeps technical failures out of normal screens', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.mocked(bootstrap).mockRejectedValueOnce(new Error('Parser table layout at ROM 0x1234 failed CRC32 DEADBEEF'));

    render(<App />);

    expect(await screen.findByText('The companion could not start. Please try again.')).toBeTruthy();
    expect(screen.queryByText(/Parser table layout|CRC32 DEADBEEF|0x1234/)).toBeNull();
    expect(consoleError).toHaveBeenCalledOnce();
    consoleError.mockRestore();
  });

  it('consumes companion Back locally and never leaves the root Pokédex', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Open Map' }));
    expect(screen.getByRole('region', { name: 'Interactive world map' })).toBeTruthy();
    vi.mocked(action).mockClear();

    const closeMap = new Event('dualdexback', { cancelable: true });
    window.dispatchEvent(closeMap);
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Interactive world map' })).toBeNull());
    expect(closeMap.defaultPrevented).toBe(true);
    expect(action).not.toHaveBeenCalledWith('BACK', {});

    const rootBack = new Event('dualdexback', { cancelable: true });
    window.dispatchEvent(rootBack);
    await Promise.resolve();
    expect(rootBack.defaultPrevented).toBe(true);
    expect(action).not.toHaveBeenCalledWith('BACK', {});
    expect(screen.getByText('POKÉDEX')).toBeTruthy();
  });

  it('lets an auto-opened battle replace an already open map', async () => {
    let publishState: ((state: Bootstrap['state']) => void) | undefined;
    vi.mocked(events).mockImplementationOnce((_currentVersion, listener) => {
      publishState = listener;
      return () => undefined;
    });
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: {
        ...fixture.catalog!,
        species: [{
          id: 1, dex: 1, name: 'BULBASAUR', typeIds: [], stats: null, description: null,
          height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {},
          moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false,
        }],
      },
    });
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Open Map' }));
    expect(screen.getByRole('region', { name: 'Interactive world map' })).toBeTruthy();

    publishState?.({
      ...fixture.state,
      version: 2,
      screen: 'BATTLE',
      loading: { active: false, phase: 'COMPLETE', completedUnits: 0, totalUnits: 0 },
      battle: {
        opponents: [{
          speciesId: 1,
          level: 5,
          typeIds: [],
          rarity: { relativeTier: null, innateTier: null, baseStars: null, areaAdjustment: null, stars: null },
          moves: [],
        }],
        targetIndex: 0,
        targetMode: 'AUTOMATIC',
        encounterKind: 'WILD',
        capabilities: {},
        selectedMoveId: null,
        effectiveness: null,
        effectivenessKnown: false,
      },
    });

    await waitFor(() => expect(screen.getByText('WILD ENCOUNTER')).toBeTruthy());
    expect(screen.queryByRole('region', { name: 'Interactive world map' })).toBeNull();
  });

  it('opens the capability report from Settings without opening memory capture', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
    fireEvent.click(await screen.findByRole('button', { name: 'COMPATIBILITY REPORT' }));

    expect(await screen.findByText('LOADED ROM · READ ONLY')).toBeTruthy();
    expect(screen.queryByText('ISSUE REPORT MEMORY CAPTURE')).toBeNull();
  });

  it('opens the normalized Map locally and returns to the retained Pokédex view', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Open Map' }));
    expect(screen.getByRole('region', { name: 'Interactive world map' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Open Pokédex' }));

    expect(action).toHaveBeenCalledWith('SCREEN', { screen: 'POKEDEX' });
    expect(screen.queryByRole('region', { name: 'Interactive world map' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Open Map' })).toBeTruthy();
  });

  it('shows the live game clock centered in the root Pokédex header', async () => {
    render(<App />);

    const clock = await screen.findByText('16:48');
    expect(clock.tagName).toBe('TIME');
    expect(clock.closest('.app-header')).toBeTruthy();
    expect(document.querySelector('.app-header-root .header-title strong')?.textContent).toBe('POKÉDEX');
  });
});

describe('catalog refresh marker', () => {
  it('remains stable across ordinary state versions after one catalog phase', () => {
    const loading = { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 };
    expect(catalogRefreshMarker({ catalogName: 'game.gba', loading })).toBe(
      catalogRefreshMarker({ catalogName: 'game.gba', loading }),
    );
  });
});
