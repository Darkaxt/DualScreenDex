import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { MapPage } from './MapPage';

afterEach(cleanup);

beforeEach(() => {
  class TestResizeObserver {
    constructor(private readonly callback: ResizeObserverCallback) {}
    observe(target: Element) { this.callback([{ target, contentRect: target.getBoundingClientRect() } as ResizeObserverEntry], this as unknown as ResizeObserver); }
    disconnect() {}
    unobserve() {}
  }
  vi.stubGlobal('ResizeObserver', TestResizeObserver);
  HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as typeof HTMLCanvasElement.prototype.getContext;
});

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [],
  areas: [{ id: 0x11 * 10 + 1, baseAreaId: 0x11, name: 'Oldale grass', methodId: 1, speciesIds: [], windows: ['ANY'], slots: [] }],
  balls: [], capabilities: {},
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120,
    gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [
      { key: 'section-16', displayName: 'Route 101', baseAreaIds: [0x10], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
      { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [0x11], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
      { key: 'section-18', displayName: 'Petalburg City', baseAreaIds: [0x12], geometry: [{ x: 2, y: 8, width: 1, height: 1 }] },
    ],
  }],
};

const state: State = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'AREA', selectedAreaId: null, currentAreaBaseId: 0x10, currentAreaName: 'Route 101', battleTab: 'ENTRY',
  revealedAreaBaseIds: [0x10, 0x11],
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
};

describe('normalized world map presentation', () => {
  it('uses the approved semantic utility rail without a navigation toolbar', () => {
    const openAreaDex = vi.fn();
    const openSettings = vi.fn();
    const { container } = render(<MapPage catalog={catalog} state={state} onOpenAreaDex={openAreaDex} onOpenSettings={openSettings} />);

    expect(screen.getByText('CURRENT')).toBeTruthy();
    const rail = container.querySelector('.map-utility-rail')!;
    const buttons = [...rail.querySelectorAll(':scope > button')];
    expect(buttons.map(button => button.getAttribute('aria-label')).slice(0, 2)).toEqual([
      'Map settings and legend',
      'Open Area Pokédex',
    ]);
    expect(buttons[0].querySelector('svg')?.dataset.semanticIcon).toBe('map');
    expect(buttons[1].querySelector('svg')?.dataset.semanticIcon).toBe('pokedex');
    expect(container.querySelector('[data-map-navigation-row]')).toBeNull();

    fireEvent.click(buttons[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Open Settings' }));
    expect(openSettings).toHaveBeenCalledOnce();
    fireEvent.click(buttons[1]);
    expect(openAreaDex).toHaveBeenCalledWith('gen3-region-0', expect.objectContaining({ key: 'section-16' }));
  });

  it('keeps every persisted discovered location revealed and passes the chosen marker to Area Dex', () => {
    const openAreaDex = vi.fn();
    render(<MapPage catalog={catalog} state={state} onOpenAreaDex={openAreaDex} onOpenSettings={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Oldale Town' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Petalburg City' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Oldale Town' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open Area Pokédex' }));

    expect(openAreaDex).toHaveBeenCalledWith('gen3-region-0', expect.objectContaining({ key: 'section-17', baseAreaIds: [0x11] }));
  });

  it('does not expose or route an undiscovered location while fog is active', () => {
    render(<MapPage
      catalog={catalog}
      state={{ ...state, currentAreaBaseId: null, currentAreaName: null, revealedAreaBaseIds: [] }}
      onOpenAreaDex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(screen.getByText('Unknown location')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Route 101' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Open Area Pokédex' }).hasAttribute('disabled')).toBe(true);
  });

  it('restores the selected Area Dex location instead of replacing it with the physical current marker', () => {
    const { container } = render(<MapPage
      catalog={catalog}
      state={{ ...state, selectedAreaId: 0x11 * 10 + 1 }}
      onOpenAreaDex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(container.querySelector('.map-current-location strong')?.textContent).toBe('Oldale Town');
    expect(screen.getByText('MAP POINT')).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Interactive world map' }).dataset.selectedKey).toBe('section-17');
  });

  it('keeps zoom, recenter, fog, markers, and current-location controls functional', () => {
    const { container } = render(<MapPage catalog={catalog} state={state} onOpenAreaDex={vi.fn()} onOpenSettings={vi.fn()} />);
    const stage = screen.getByRole('region', { name: 'Interactive world map' });

    expect(stage.dataset.scale).toBe('1');
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(stage.dataset.scale)).toBeGreaterThan(1);
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    expect(stage.dataset.scale).toBe('1');
    expect(stage.dataset.panX).toBe('0');
    expect(stage.dataset.panY).toBe('0');

    const fog = screen.getByRole('button', { name: 'Toggle fog of war' });
    const markers = screen.getByRole('button', { name: 'Toggle map markers' });
    expect(fog.getAttribute('aria-pressed')).toBe('true');
    expect(markers.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(fog);
    fireEvent.click(markers);
    expect(fog.getAttribute('aria-pressed')).toBe('false');
    expect(markers.getAttribute('aria-pressed')).toBe('false');
    expect(container.querySelector('.map-marker')).toBeNull();
  });
});
