import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { maskEvolutionName, PokedexDetail } from './PokedexDetail';

afterEach(cleanup);
beforeEach(() => {
  HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as typeof HTMLCanvasElement.prototype.getContext;
});

describe('Pokédex evolution navigation', () => {
  it('uses a generic Pokédex Entry fallback when compatible text is unavailable', () => {
    render(<PokedexDetail
      catalog={{ ...catalog, species: catalog.species.map(species => species.id === 5 ? { ...species, description: null } : species) }}
      state={state}
      send={vi.fn()}
      tab="ENTRY"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
    />);

    expect(screen.getByText('No compatible Pokédex entry is available for this species.')).toBeTruthy();
    expect(screen.queryByText(/resolved from this ROM/i)).toBeNull();
  });

  it('masks every name character while preserving spacing', () => {
    expect(maskEvolutionName('Mr Mime')).toBe('?? ????');
    expect(maskEvolutionName('Farfetch’d')).toBe('??????????');
  });

  it('opens the resolved target species on its entry tab', () => {
    const send = vi.fn();
    const setTab = vi.fn();

    render(<PokedexDetail
      catalog={catalog}
      state={state}
      send={send}
      tab="MORE"
      setTab={setTab}
      openMove={vi.fn()}
      openAbility={vi.fn()}
    />);

    fireEvent.click(screen.getByRole('button', { name: 'Charizard Level 36' }));

    expect(setTab).toHaveBeenCalledWith('ENTRY');
    expect(send).toHaveBeenCalledWith('OPEN_SPECIES', { speciesId: 6 });
  });

  it('reveals evolution artwork progressively in Organic mode', () => {
    const organic = (target: State['speciesState'][number] | undefined): State => ({
      ...state,
      settings: { ...state.settings, knowledgeMode: 'ORGANIC' },
      speciesState: target ? { 5: state.speciesState[5], 6: target } : { 5: state.speciesState[5] },
    });
    const props = { catalog, send: vi.fn(), tab: 'MORE' as const, setTab: vi.fn(), openMove: vi.fn(), openAbility: vi.fn() };
    const rendered = render(<PokedexDetail {...props} state={organic(undefined)} />);

    expect(screen.getByText('?????????')).toBeTruthy();
    expect(screen.queryByText('Charizard')).toBeNull();
    expect(screen.getByAltText('Unidentified evolution sprite').classList.contains('evolution-silhouette')).toBe(true);
    expect(screen.queryByRole('button', { name: /Charizard Level 36/i })).toBeNull();

    rendered.rerender(<PokedexDetail {...props} state={organic({ seen: true, caught: false, team: false, ballId: null })} />);
    expect(screen.getByText('Charizard')).toBeTruthy();
    expect(screen.getByAltText('Charizard evolution sprite').classList.contains('evolution-seen')).toBe(true);

    rendered.rerender(<PokedexDetail {...props} state={organic({ seen: true, caught: true, team: false, ballId: null })} />);
    const captured = screen.getByAltText('Charizard evolution sprite');
    expect(captured.classList.contains('evolution-seen')).toBe(false);
    expect(captured.classList.contains('evolution-silhouette')).toBe(false);
  });

  it('embeds the normalized world map on AREA and exposes only organically observed habitats', () => {
    const send = vi.fn();
    render(<PokedexDetail
      catalog={catalog}
      state={{
        ...state,
        settings: { ...state.settings, knowledgeMode: 'ORGANIC' },
        observedAreaBaseIdsBySpecies: { 5: [0x11] },
        revealedAreaBaseIds: [0x10, 0x11],
      }}
      send={send}
      tab="AREA"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
    />);

    expect(screen.getByRole('img', { name: 'Hoenn Charmeleon habitat map' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Observed at Oldale Town' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Observed at Petalburg City' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Kanto' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Observed at Oldale Town' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open selected Area Pokédex' }));
    expect(send).toHaveBeenCalledWith('MAP_AREA', { regionKey: 'hoenn', locationKey: 'oldale' });
  });

  it('keeps AREA safe when the catalog has no normalized map', () => {
    render(<PokedexDetail
      catalog={{ ...catalog, worldMaps: [] }}
      state={state}
      send={vi.fn()}
      tab="AREA"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
    />);

    expect(screen.getByText('MAP UNAVAILABLE')).toBeTruthy();
  });
});

const baseSpecies = {
  dex: 5,
  typeIds: [10],
  stats: null,
  description: 'Entry',
  height: null,
  weight: null,
  learnset: [],
  learnsets: {},
  normalizedLearnsets: {},
  moveAcquisitions: [],
  abilities: [],
  hasSprite: false,
};

const catalog = {
  hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], balls: [], capabilities: {},
  areas: [
    { id: 0x11 * 10 + 1, baseAreaId: 0x11, name: 'Oldale grass', methodId: 1, speciesIds: [5, 6], windows: ['ANY'], slots: [{ speciesId: 5, minimumLevel: 3, maximumLevel: 4, weight: 50 }] },
    { id: 0x12 * 10 + 1, baseAreaId: 0x12, name: 'Petalburg grass', methodId: 1, speciesIds: [5], windows: ['ANY'], slots: [{ speciesId: 5, minimumLevel: 4, maximumLevel: 5, weight: 50 }] },
    { id: 0x13 * 10 + 1, baseAreaId: 0x13, name: 'Kanto grass', methodId: 1, speciesIds: [5], windows: ['ANY'], slots: [{ speciesId: 5, minimumLevel: 5, maximumLevel: 6, weight: 50 }] },
  ],
  worldMaps: [{
    key: 'hoenn', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fhoenn.png',
    locations: [
      { key: 'oldale', displayName: 'Oldale Town', baseAreaIds: [0x11], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
      { key: 'petalburg', displayName: 'Petalburg City', baseAreaIds: [0x12], geometry: [{ x: 2, y: 8, width: 1, height: 1 }] },
    ],
  }, {
    key: 'kanto', displayName: 'Kanto', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15,
    imageUrl: '/api/maps/world%2Fkanto.png',
    locations: [{ key: 'pallet', displayName: 'Pallet Town', baseAreaIds: [0x13], geometry: [{ x: 3, y: 10, width: 1, height: 1 }] }],
  }],
  types: [{ id: 10, name: 'Fire', foreground: '#111', background: '#f80', border: '#b40' }],
  species: [
    { ...baseSpecies, id: 5, name: 'Charmeleon', evolutions: [{ targetSpeciesId: 6, targetName: 'Charizard', methodId: 1, parameter: 36, condition: 'Level 36' }] },
    { ...baseSpecies, id: 6, dex: 6, name: 'Charizard', evolutions: [], hasSprite: true },
  ],
} satisfies Catalog;

const state = {
  version: 1, screen: 'DETAIL', priorScreen: 'POKEDEX', settingsReturnScreen: 'DETAIL', selectedSpeciesId: 5,
  filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY', speciesState: { 5: { seen: true, caught: true, team: false, ballId: null } }, observedMoves: {}, battle: null,
  catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
} satisfies State;
