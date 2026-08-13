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
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120,
    gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [
      { key: 'section-16', displayName: 'Route 101', baseAreaIds: [0x10], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
      { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [0x11], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
    ],
  }],
};

const state: State = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'AREA', selectedAreaId: null, currentAreaBaseId: 0x10, currentAreaName: 'Route 101', battleTab: 'ENTRY',
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
    expect(openAreaDex).toHaveBeenCalledOnce();
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
