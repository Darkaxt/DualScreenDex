import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { maskIdentityName } from '../components';
import { PokedexDetail } from './PokedexDetail';

afterEach(cleanup);
beforeEach(() => {
  HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as typeof HTMLCanvasElement.prototype.getContext;
});

describe('Pokédex evolution navigation', () => {
  it('offers specimens only when decoded owned instances exist', () => {
    const openSpecimens = vi.fn();
    const props = { catalog, send: vi.fn(), tab: 'MORE' as const, setTab: vi.fn(), openMove: vi.fn(), openAbility: vi.fn(), openSpecimens };
    const rendered = render(<PokedexDetail {...props} state={{
      ...state,
      speciesState: { 5: { ...state.speciesState[5], specimenCount: 2 } },
    }} />);

    fireEvent.click(screen.getByRole('button', { name: 'VIEW SPECIMENS' }));
    expect(openSpecimens).toHaveBeenCalledWith(5);

    rendered.rerender(<PokedexDetail {...props} state={{
      ...state,
      speciesState: { 5: { ...state.speciesState[5], caught: true, specimenCount: 0 } },
    }} />);
    expect(screen.queryByRole('button', { name: 'VIEW SPECIMENS' })).toBeNull();
  });

  it('uses MORE space for the selected species ability instead of forcing a sparse detail page', () => {
    const openAbility = vi.fn();
    render(<PokedexDetail
      catalog={{ ...catalog, species: catalog.species.map(species => species.id === 5 ? {
        ...species,
        abilities: [{
          id: 66,
          name: 'Blaze',
          description: 'Powers up Fire-type moves in a pinch.',
          mechanics: [{ kind: 'MULTIPLIER', label: 'Power', value: '×1.5', numerator: 3, denominator: 2 }],
        }],
      } : species) }}
      state={state}
      send={vi.fn()}
      tab="MORE"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={openAbility}
    />);

    expect(screen.getByText('Powers up Fire-type moves in a pinch.')).toBeTruthy();
    expect(screen.getByText('×1.5')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Blaze/i })).toBeNull();
    expect(openAbility).not.toHaveBeenCalled();
  });

  it('uses catalog height for a scaled Pokémon and human comparison on ENTRY', () => {
    const avatarUrl = '/api/trainer-assets/trainer%2Favatar%2Ffemale.png';
    render(<PokedexDetail
      catalog={{ ...catalog, species: catalog.species.map(species => species.id === 5 ? { ...species, height: 17 } : species) }}
      state={{ ...state, trainer: null, trainerAvatarUrl: avatarUrl }}
      send={vi.fn()}
      tab="ENTRY"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
    />);

    const chart = screen.getByRole('img', { name: 'Height comparison for Charmeleon: 1.7 m beside a 1.7 m person' });
    expect(chart.querySelectorAll('.height-ruler-line').length).toBeGreaterThanOrEqual(5);
    expect(chart.querySelector('.height-person img')?.getAttribute('src')).toBe(`${avatarUrl}?catalog=${catalog.hash}`);
    expect(chart.querySelector('.height-pokemon img')?.getAttribute('src')).toBe(`/api/sprites/species/5.png?catalog=${catalog.hash}`);
  });

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

    expect(document.querySelector('.app-header .header-title h1')?.textContent).toBe('POKÉDEX');
    expect(screen.getAllByText('Charmeleon')).toHaveLength(1);
    expect(screen.getByText('No Pokédex entry is available for this Pokémon.')).toBeTruthy();
    expect(screen.queryByText(/resolved from this ROM/i)).toBeNull();
  });

  it('masks every name character while preserving spacing', () => {
    expect(maskIdentityName('Mr Mime')).toBe('?? ????');
    expect(maskIdentityName('Farfetch’d')).toBe('??????????');
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
    expect(screen.getByAltText('Unidentified evolution sprite').classList.contains('identity-silhouette')).toBe(true);
    expect(screen.queryByRole('button', { name: /Charizard Level 36/i })).toBeNull();

    rendered.rerender(<PokedexDetail {...props} state={organic({ seen: true, caught: false, team: false, ballId: null })} />);
    expect(screen.getByText('Charizard')).toBeTruthy();
    expect(screen.getByAltText('Charizard evolution sprite').classList.contains('identity-seen')).toBe(true);

    rendered.rerender(<PokedexDetail {...props} state={organic({ seen: true, caught: true, team: false, ballId: null })} />);
    const captured = screen.getByAltText('Charizard evolution sprite');
    expect(captured.classList.contains('identity-seen')).toBe(false);
    expect(captured.classList.contains('identity-silhouette')).toBe(false);
  });

  it('uses unknown silhouette seen grayscale and captured full color for the Pokédex detail avatar', () => {
    const props = { catalog, send: vi.fn(), tab: 'ENTRY' as const, setTab: vi.fn(), openMove: vi.fn(), openAbility: vi.fn() };
    const unknownState: State = {
      ...state,
      settings: { ...state.settings, knowledgeMode: 'ORGANIC' },
      speciesState: {},
    };
    const rendered = render(<PokedexDetail {...props} state={unknownState} />);

    expect(screen.getByAltText('Unidentified Pokémon').classList.contains('identity-silhouette')).toBe(true);

    const seenState: State = {
      ...unknownState,
      speciesState: { 5: { seen: true, caught: false, team: false, ballId: null } },
    };
    rendered.rerender(<PokedexDetail {...props} state={seenState} />);

    expect(screen.getByAltText('Charmeleon sprite').classList.contains('identity-seen')).toBe(true);

    rendered.rerender(<PokedexDetail {...props} state={{
      ...seenState,
      speciesState: { 5: { seen: true, caught: true, team: false, ballId: null } },
    }} />);
    const captured = screen.getByAltText('Charmeleon sprite');
    expect(captured.classList.contains('identity-seen')).toBe(false);
    expect(captured.classList.contains('identity-silhouette')).toBe(false);
    const caught = screen.getByLabelText('Caught');
    expect(caught.closest('.pokedex-avatar')).toBeTruthy();
    expect(caught.closest('.caught-avatar-badge')).toBeTruthy();
    expect(caught.closest('.identity-line')).toBeNull();
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

  it('routes unresolved level-up moves directly to the Move List setting', () => {
    const openMoveListSettings = vi.fn();
    render(<PokedexDetail
      catalog={{
        ...catalog,
        rulesets: [
          { id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true },
          { id: 'modern', label: 'Modern', sourceOffset: 1, confidence: 0.9, primary: false },
        ],
      }}
      state={state}
      send={vi.fn()}
      tab="MOVES"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openMoveListSettings={openMoveListSettings}
    />);

    fireEvent.click(screen.getByRole('button', { name: 'CHOOSE MOVE LIST' }));

    expect(openMoveListSettings).toHaveBeenCalledOnce();
  });

  it('offers Atlas recovery when maps exist but the habitat cannot be placed', () => {
    const openAtlas = vi.fn();
    render(<PokedexDetail
      catalog={{
        ...catalog,
        areas: catalog.areas.map(area => ({ ...area, baseAreaId: 0x99 })),
      }}
      state={state}
      send={vi.fn()}
      tab="AREA"
      setTab={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openAtlas={openAtlas}
    />);

    expect(screen.getByText('NO HABITAT MAP')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'OPEN ATLAS' }));
    expect(openAtlas).toHaveBeenCalledOnce();
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

    expect(screen.getByText('NO HABITAT MAP')).toBeTruthy();
    expect(screen.getByText('No habitat map is available for this game.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'OPEN ATLAS' })).toBeNull();
    expect(screen.queryByText(/ROM|normalized world map/i)).toBeNull();
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
  hasSprite: true,
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
