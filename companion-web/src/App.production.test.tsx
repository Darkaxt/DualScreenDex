import { fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bootstrap } from './models';

const fixture: Bootstrap = {
  catalog: {
    hash: 'sha', crc32: 'C3A9F204', family: 'EMERALD', platform: 'GBA', rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
    species: [], moves: [], types: [],
    areas: [{ id: 0x11 * 10 + 1, baseAreaId: 0x11, name: 'Oldale grass', methodId: 1, speciesIds: [], windows: ['ANY'], slots: [] }],
    balls: [], capabilities: {},
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
    settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: {}, observedMoves: {}, battle: null, catalogReady: true,
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

import { action, bootstrap } from './gateway';
import { App, catalogRefreshMarker, loadingModuleLabel } from './App';

describe('production application shell', () => {
  beforeEach(() => {
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

  it('keeps ROM identity visible without rendering simulator controls', async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByText('Pokemon Modern Emerald.gba')).toBeTruthy());
    expect(screen.getByText(/CRC32 C3A9F204/)).toBeTruthy();
    expect(screen.queryByText('Encounter feed')).toBeNull();
    expect(screen.queryByText('GENERATE ENCOUNTER')).toBeNull();
    expect(screen.queryByText(/Generate an encounter/i)).toBeNull();
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

  it('replaces setup actions with real catalog loading progress', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      catalog: null,
      state: {
        ...fixture.state,
        version: 2,
        catalogReady: false,
        loading: { active: true, phase: 'IDENTIFYING', completedUnits: 0, totalUnits: 5 },
      },
    });

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Loading ROM identity' });
    expect(loading.textContent).toBe('Loading ROM identity');
    expect(loading.textContent).not.toContain('%');
    const progress = screen.getByRole('progressbar', { name: 'Loading ROM identity' });
    expect(progress.getAttribute('aria-valuemin')).toBe('0');
    expect(progress.getAttribute('aria-valuemax')).toBe('5');
    expect(progress.getAttribute('aria-valuenow')).toBe('0');
    expect(screen.queryByText('LOAD ROM OR ZIP')).toBeNull();
    expect(screen.queryByRole('button', { name: 'CONNECT RETROARCH' })).toBeNull();
  });

  it('shows indeterminate companion progress while bootstrap is pending', async () => {
    let resolveBootstrap!: (value: Bootstrap) => void;
    vi.mocked(bootstrap).mockImplementationOnce(() => new Promise(resolve => { resolveBootstrap = resolve; }));

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Loading companion state' });
    expect(screen.getByRole('progressbar', { name: 'Loading companion state' }).hasAttribute('aria-valuenow')).toBe(false);
    expect(screen.queryByText('LOAD ROM OR ZIP')).toBeNull();
    expect(screen.queryByRole('button', { name: 'CONNECT RETROARCH' })).toBeNull();

    resolveBootstrap(fixture);
  });

  it('uses concise names for every parser module and humanizes future phases', () => {
    expect(loadingModuleLabel('IDENTIFYING')).toBe('ROM identity');
    expect(loadingModuleLabel('ESSENTIAL')).toBe('core catalog');
    expect(loadingModuleLabel('SPECIES_MEDIA')).toBe('sprites & entries');
    expect(loadingModuleLabel('RELATIONSHIPS')).toBe('evolutions & areas');
    expect(loadingModuleLabel('EXTENDED')).toBe('extended data');
    expect(loadingModuleLabel('CACHE_REOPEN')).toBe('saved catalog');
    expect(loadingModuleLabel('FUTURE_PHASE')).toBe('future phase');
  });

  it('opens the capability report from Settings without opening memory capture', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
    fireEvent.click(await screen.findByRole('button', { name: 'CAPABILITY REPORT' }));

    expect(await screen.findByText('LOADED ROM · READ ONLY')).toBeTruthy();
    expect(screen.queryByText('ISSUE REPORT MEMORY CAPTURE')).toBeNull();
  });

  it('opens the normalized Map locally and returns to Area Pokédex without a new server screen', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Open Map' }));
    expect(screen.getByRole('region', { name: 'Interactive world map' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Oldale Town' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open Area Pokédex' }));

    expect(action).toHaveBeenCalledWith('MAP_AREA', { regionKey: 'gen3-region-0', locationKey: 'section-17' });
    expect(screen.queryByRole('region', { name: 'Interactive world map' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Open Map' })).toBeTruthy();
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
