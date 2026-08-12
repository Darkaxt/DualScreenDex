import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { PokedexBrowse } from './PokedexBrowse';

afterEach(cleanup);

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
  species: [
    { id: 1, dex: 1, name: 'Bulbasaur', typeIds: [], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
    { id: 4, dex: 4, name: 'Charmander', typeIds: [], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
  ],
};

const state: State = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null,
  battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {
    1: { seen: true, caught: false, team: false, ballId: null },
    4: { seen: false, caught: false, team: false, ballId: null },
  },
  observedMoves: {},
  battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
};

describe('Pokédex knowledge modes', () => {
  it('hides the redundant Seen filter only in Organic mode', () => {
    const { rerender } = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.queryByRole('button', { name: 'SEEN' })).toBeNull();

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    }} send={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'SEEN' })).toBeTruthy();

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'HIDDEN' },
    }} send={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'SEEN' })).toBeTruthy();
  });

  it('treats a persisted Seen filter as All after entering Organic mode', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      filter: 'SEEN',
      speciesState: {
        ...state.speciesState,
        4: { seen: false, caught: true, team: false, ballId: null },
      },
    }} send={vi.fn()} />);

    expect(screen.getByText('Charmander')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'ALL' }).classList.contains('active')).toBe(true);
  });

  it('keeps unseen species out of the Organic Pokédex', () => {
    render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('allows full ROM navigation in Discovered mode', () => {
    render(<PokedexBrowse catalog={catalog} state={{ ...state, settings: { ...state.settings, knowledgeMode: 'DISCOVERED' } }} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.getByText('Charmander')).toBeTruthy();
  });

  it('lists only recruited species in Hidden mode', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'HIDDEN' },
      speciesState: { ...state.speciesState, 1: { ...state.speciesState[1], caught: true } }
    }} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('disables save-backed filters when their capabilities are unavailable', () => {
    render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'TEAM' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'AREA' }).hasAttribute('disabled')).toBe(true);
  });

  it('gates Team and Area independently', () => {
    const { rerender } = render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      saveRam: {
        status: 'MATCHED', sourceName: 'fixture.srm', sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'VERIFIED', capabilities: { PARTY: 'AVAILABLE', SPECIES: 'AVAILABLE' }, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'TEAM' }) as HTMLButtonElement).disabled).toBe(false);
    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(true);

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      currentAreaIds: [1],
      saveRam: {
        status: 'MATCHED', sourceName: 'fixture.srm', sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'VERIFIED', capabilities: { CURRENT_AREA: 'AVAILABLE' }, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'TEAM' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('enables Area from resolved live memory without SaveRAM capability or an active battle', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      currentAreaIds: [1],
      saveRam: {
        status: 'NOT_FOUND', sourceName: null, sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'UNKNOWN', capabilities: {}, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows ROM-derived day and night markers only while the Area filter is active', () => {
    const windowCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 10, baseAreaId: 1, name: 'Route 1', methodId: 1, speciesIds: [1], windows: ['DAY'], slots: [] },
        { id: 11, baseAreaId: 1, name: 'Route 1', methodId: 2, speciesIds: [4], windows: ['NIGHT'], slots: [] },
      ],
    };
    const areaState: State = {
      ...state,
      filter: 'AREA',
      currentAreaIds: [10, 11],
      currentAreaSpeciesIds: [1, 4],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    };

    const { rerender } = render(<PokedexBrowse catalog={windowCatalog} state={areaState} send={vi.fn()} />);

    expect(screen.getByLabelText('Day encounter')).toBeTruthy();
    expect(screen.getByLabelText('Night encounter')).toBeTruthy();

    rerender(<PokedexBrowse catalog={windowCatalog} state={{ ...areaState, filter: 'ALL' }} send={vi.fn()} />);
    expect(screen.queryByLabelText('Day encounter')).toBeNull();
    expect(screen.queryByLabelText('Night encounter')).toBeNull();
  });

  it('shows no marker when a species is available both day and night or without a time restriction', () => {
    const windowCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 20, baseAreaId: 2, name: 'Route 2', methodId: 1, speciesIds: [1], windows: ['DAY', 'NIGHT'], slots: [] },
        { id: 21, baseAreaId: 2, name: 'Route 2 water', methodId: 2, speciesIds: [4], windows: ['ANY'], slots: [] },
      ],
    };
    render(<PokedexBrowse catalog={windowCatalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaIds: [20, 21],
      currentAreaSpeciesIds: [1, 4],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    }} send={vi.fn()} />);

    expect(screen.queryByTestId('encounter-window-icon')).toBeNull();
  });

  it('shows the parsed roster in Discovered mode regardless of local observation or caught state', () => {
    const areaCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 10, baseAreaId: 1, name: 'Route 1', methodId: 1, speciesIds: [1, 4], windows: ['ANY'], slots: [] },
      ],
    };
    const areaState: State = {
      ...state,
      filter: 'AREA',
      currentAreaIds: [10],
      currentAreaSpeciesIds: [1],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
      speciesState: {
        1: { seen: true, caught: false, team: false, ballId: null },
        4: { seen: true, caught: false, team: false, ballId: null },
      },
    };

    const { rerender } = render(<PokedexBrowse catalog={areaCatalog} state={areaState} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.getByText('Charmander')).toBeTruthy();

    rerender(<PokedexBrowse catalog={areaCatalog} state={{
      ...areaState,
      speciesState: { ...areaState.speciesState, 4: { ...areaState.speciesState[4], caught: true } },
    }} send={vi.fn()} />);
    expect(screen.getByText('Charmander')).toBeTruthy();
  });

  it('masks an unseen parsed encounter in Organic Area without leaking it to search', () => {
    const areaCatalog: Catalog = { ...catalog, areas: [{ id: 10, baseAreaId: 1, name: 'Route 1', methodId: 1, speciesIds: [1, 4], windows: ['NIGHT'], slots: [] }] };
    const organic: State = { ...state, filter: 'AREA', activeAreaIds: [10], activeAreaBaseId: 1, activeAreaName: 'Route 1', activeAreaSpeciesIds: [1], settings: { ...state.settings, knowledgeMode: 'ORGANIC' }, speciesState: { 1: { seen: true, caught: false, team: false, ballId: null }, 4: { seen: false, caught: false, team: false, ballId: null } } };
    const { container } = render(<PokedexBrowse catalog={areaCatalog} state={organic} send={vi.fn()} />);

    expect(screen.getByText('??????????')).toBeTruthy();
    expect(screen.getByText('#???')).toBeTruthy();
    expect((screen.getByLabelText('Unidentified encounter') as HTMLButtonElement).disabled).toBe(true);
    expect(container.querySelector('.identity-hidden')).toBeTruthy();
    expect(screen.getAllByLabelText('Night encounter')).toHaveLength(2);

    fireEvent.input(screen.getByPlaceholderText('NAME OR NUMBER'), { target: { value: 'Charmander' } });
    expect(screen.queryByText('??????????')).toBeNull();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('reveals a globally caught roster member but never inserts a caught gift outside the parsed roster', () => {
    const areaCatalog: Catalog = { ...catalog, areas: [{ id: 10, baseAreaId: 1, name: 'Route 1', methodId: 1, speciesIds: [1], windows: ['ANY'], slots: [{ speciesId: 1, minimumLevel: 2, maximumLevel: 3, weight: 100 }] }] };
    render(<PokedexBrowse catalog={areaCatalog} state={{
      ...state,
      filter: 'AREA',
      activeAreaIds: [10],
      activeAreaRoster: [{ speciesId: 1, methodIds: [1], windows: ['ANY'], identityKnown: true }],
      speciesState: {
        1: { seen: false, caught: true, team: false, ballId: null },
        4: { seen: false, caught: true, team: false, ballId: null },
      },
    }} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('offers a state-preserving back action after Map or Pokemon Area handoff', () => {
    const send = vi.fn();
    render(<PokedexBrowse catalog={catalog} state={{ ...state, priorScreen: 'DETAIL' }} send={send} />);

    fireEvent.click(screen.getByLabelText('Back'));

    expect(send).toHaveBeenCalledWith('BACK');
  });

  it('labels the live Area context with the ROM-derived location and Current marker', () => {
    render(<PokedexBrowse catalog={{
      ...catalog,
      areas: [
        { id: 10, baseAreaId: 1, name: 'Route 101', methodId: 1, speciesIds: [1], windows: ['ANY'], slots: [] },
      ],
    }} state={{
      ...state,
      filter: 'AREA',
      activeAreaIds: [10],
      activeAreaBaseId: 1,
      activeAreaName: 'Route 101',
      activeAreaSpeciesIds: [1],
      activeAreaIsCurrent: true,
    }} send={vi.fn()} />);

    expect(screen.getByLabelText('Area filter location')).toBeTruthy();
    expect(screen.getByText('Route 101')).toBeTruthy();
    expect(screen.getByText('CURRENT')).toBeTruthy();
  });

  it('labels a selected non-current Area without a Current marker or map-art dependency', () => {
    render(<PokedexBrowse catalog={{
      ...catalog,
      areas: [
        { id: 21, baseAreaId: 2, name: 'Oldale Town', methodId: 1, speciesIds: [4], windows: ['ANY'], slots: [] },
      ],
    }} state={{
      ...state,
      filter: 'AREA',
      selectedAreaId: 21,
      currentAreaBaseId: 1,
      currentAreaName: 'Route 101',
      activeAreaIds: [21],
      activeAreaBaseId: 2,
      activeAreaName: 'Oldale Town',
      activeAreaSpeciesIds: [4],
      activeAreaIsCurrent: false,
      speciesState: {
        ...state.speciesState,
        4: { seen: true, caught: false, team: false, ballId: null },
      },
    }} send={vi.fn()} />);

    expect(screen.getByText('Oldale Town')).toBeTruthy();
    expect(screen.queryByText('CURRENT')).toBeNull();
    expect(screen.getByText('Charmander')).toBeTruthy();
    expect(screen.queryByText('Bulbasaur')).toBeNull();
  });

  it('does not substitute the current name when a selected Area name is unavailable', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaBaseId: 1,
      currentAreaName: 'Route 101',
      activeAreaIds: [21],
      activeAreaBaseId: 2,
      activeAreaName: null,
      activeAreaSpeciesIds: [],
      activeAreaIsCurrent: false,
    }} send={vi.fn()} />);

    expect(screen.queryByLabelText('Area filter location')).toBeNull();
    expect(screen.queryByText('Route 101')).toBeNull();
  });
});
