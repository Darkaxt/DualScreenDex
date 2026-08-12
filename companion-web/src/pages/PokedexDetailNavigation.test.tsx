import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { maskEvolutionName, PokedexDetail } from './PokedexDetail';

afterEach(cleanup);

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

    expect(screen.getByText('Pokédex data not available.')).toBeTruthy();
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
  hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], areas: [], balls: [], capabilities: {},
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
