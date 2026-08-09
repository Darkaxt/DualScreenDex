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
});
