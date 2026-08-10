import { cleanup, render, screen } from '@testing-library/preact';
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

  it('shows ROM-derived day and night markers only while the Area filter is active', () => {
    const windowCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 10, name: 'Route 1', methodId: 1, speciesIds: [1], windows: ['DAY'], slots: [] },
        { id: 11, name: 'Route 1', methodId: 2, speciesIds: [4], windows: ['NIGHT'], slots: [] },
      ],
    };
    const areaState: State = {
      ...state,
      filter: 'AREA',
      currentAreaIds: [10, 11],
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
        { id: 20, name: 'Route 2', methodId: 1, speciesIds: [1], windows: ['DAY', 'NIGHT'], slots: [] },
        { id: 21, name: 'Route 2 water', methodId: 2, speciesIds: [4], windows: ['ANY'], slots: [] },
      ],
    };
    render(<PokedexBrowse catalog={windowCatalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaIds: [20, 21],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    }} send={vi.fn()} />);

    expect(screen.queryByTestId('encounter-window-icon')).toBeNull();
  });
});
