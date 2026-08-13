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
import { App, catalogRefreshMarker } from './App';

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

  it('shows an indeterminate loading state without a misleading percentage', async () => {
    vi.mocked(bootstrap).mockResolvedValueOnce({
      ...fixture,
      state: {
        ...fixture.state,
        version: 2,
        loading: { active: true, phase: 'IDENTIFYING', completedUnits: 0, totalUnits: 5 },
      },
    });

    render(<App />);

    const loading = await screen.findByRole('status', { name: 'Loading IDENTIFYING' });
    expect(loading.textContent).toBe('Loading');
    expect(loading.textContent).not.toContain('%');
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
