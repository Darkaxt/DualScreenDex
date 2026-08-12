import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { PokemonAreaMap, WorldMapPage } from './WorldMapPage';

afterEach(cleanup);

const catalog: Catalog = {
  hash: 'map', crc32: '1', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
  worldMaps: [{ key: 'hoenn', displayName: 'HOENN', pixelWidth: 160, pixelHeight: 144, gridWidth: 20, gridHeight: 18, assetUrl: '/api/maps/world%2Fhoenn.png', locations: [
    { key: 'route-101', displayName: 'Route 101', baseAreaIds: [1], geometry: [{ x: 2, y: 3, width: 1, height: 1 }] },
    { key: 'oldale', displayName: 'Oldale Town', baseAreaIds: [2], geometry: [{ x: 4, y: 5, width: 1, height: 1 }] },
  ] }],
};
const state: State = {
  version: 1, screen: 'MAP', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, filter: 'AREA', selectedAreaId: null,
  currentAreaBaseId: 1, activeAreaBaseId: 2, activeAreaName: 'Oldale Town', activeAreaIsCurrent: false, visitedAreaBaseIds: [1, 2], observedAreaBaseIdsBySpecies: { 25: [1, 2] },
  battleTab: 'ENTRY', settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' }, speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'ROM', error: null, activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'IDLE', completedUnits: 5, totalUnits: 5 },
};

describe('Map First', () => {
  it('renders two-line place context, fixed controls, independent current and selected markers, and Area handoff', () => {
    const send = vi.fn();
    const { container } = render(<WorldMapPage catalog={catalog} state={state} send={send} />);
    expect(screen.getByText('HOENN')).toBeTruthy();
    expect(screen.getAllByText('Oldale Town')).toHaveLength(2);
    expect(screen.queryByText('CURRENT')).toBeNull();
    expect(screen.getByLabelText('Open Pokédex')).toBeTruthy();
    expect(screen.getByLabelText('Layers')).toBeTruthy();
    expect(screen.getByLabelText('Zoom in')).toBeTruthy();
    expect(screen.getByLabelText('Zoom out')).toBeTruthy();
    expect(screen.getByLabelText('Recenter map')).toBeTruthy();
    expect(container.querySelector('.map-location.current')).toBeTruthy();
    expect(container.querySelector('.map-location.selected')).toBeTruthy();
    expect((container.querySelector('.map-canvas') as HTMLElement).style.width).toBe('');
    expect((container.querySelector('.map-canvas') as HTMLElement).style.aspectRatio).toBe('160 / 144');
    fireEvent.click(screen.getByLabelText('Open Area Pokédex'));
    expect(send).toHaveBeenCalledWith('MAP_AREA', { locationKey: 'oldale' });
    expect(container.querySelector('.bottom-toolbar')).toBeNull();
    expect(container.querySelector('.place-card')).toBeNull();
  });

  it('embeds the ROM map in Pokemon Area with simultaneous observed locations and exact empty copy', () => {
    const { rerender } = render(<PokemonAreaMap catalog={catalog} state={state} speciesId={25} send={vi.fn()} />);
    expect(screen.getByLabelText('Observed at Route 101')).toBeTruthy();
    expect(screen.getByLabelText('Observed at Oldale Town')).toBeTruthy();
    rerender(<PokemonAreaMap catalog={catalog} state={{ ...state, observedAreaBaseIdsBySpecies: {} }} speciesId={25} send={vi.fn()} />);
    expect(screen.getByText('No known locations yet.')).toBeTruthy();
  });
});
