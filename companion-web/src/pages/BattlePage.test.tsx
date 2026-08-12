import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { BattlePage } from './BattlePage';

afterEach(cleanup);

describe('battle layout', () => {
  it('opens the targeted species in the full Pokédex from the identity header', () => {
    const { catalog, state } = fixture(1);
    const send = vi.fn();
    const openSpecies = vi.fn();
    render(<BattlePage catalog={catalog} state={state} send={send} openMove={vi.fn()} openSpecies={openSpecies} />);

    screen.getByRole('button', { name: 'Open Hitmonlee in Pokédex' }).click();

    expect(openSpecies).toHaveBeenCalledWith(1);
  });

  it('uses the compact grid for a single opponent and keeps simulator inputs out of the device UI', () => {
    const { catalog, state } = fixture(1);
    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(container.querySelector('.battle-screen')?.classList.contains('battle-single')).toBe(true);
    expect(screen.queryByText('ATTACK REFERENCE')).toBeNull();
  });

  it('offers both targets when a double encounter needs manual cursor fallback', () => {
    const { catalog, state } = fixture(2);
    state.battle!.targetMode = 'MANUAL_TARGET_FALLBACK';
    const send = vi.fn();
    const { container } = render(<BattlePage catalog={catalog} state={state} send={send} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(container.querySelector('.battle-screen')?.classList.contains('battle-double')).toBe(true);
    expect(container.querySelectorAll('.target-switch button')).toHaveLength(2);
    container.querySelectorAll('.target-switch button')[1].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(send).toHaveBeenCalledWith('TARGET', { index: 1 });
  });

  it('follows an automatic double target without showing manual target controls', () => {
    const { catalog, state } = fixture(2);
    state.battle = { ...state.battle!, targetIndex: 1, targetMode: 'AUTOMATIC' };
    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Hitmonchan' })).toBeTruthy();
    expect(screen.getByText('AUTOMATIC TARGET')).toBeTruthy();
    expect(container.querySelector('.target-switch')).toBeNull();
  });

  it('shows frequency only while the targeted species is not captured', () => {
    const { catalog, state } = fixture(1);
    const battle = {
      ...state.battle!,
      opponents: [{ ...state.battle!.opponents[0], moves: [{ moveId: 1, frequency: 3 }] }],
    };
    const { rerender } = render(<BattlePage
      catalog={catalog}
      state={{ ...state, battleTab: 'MOVES', battle }}
      send={vi.fn()}
      openMove={vi.fn()}
      openSpecies={vi.fn()}
    />);

    expect(screen.getByText('FREQUENCY · 3×')).toBeTruthy();

    rerender(<BattlePage
      catalog={catalog}
      state={{ ...state, battleTab: 'MOVES', battle, speciesState: { 1: { seen: true, caught: true, team: false, ballId: null } } }}
      send={vi.fn()}
      openMove={vi.fn()}
      openSpecies={vi.fn()}
    />);

    expect(screen.getByText('Pound')).toBeTruthy();
    expect(screen.queryByText(/FREQUENCY|encounter/i)).toBeNull();
  });

  it('does not expose a simulator resolve button when Organic effectiveness is still unknown', () => {
    const { catalog, state } = fixture(1);
    state.settings.knowledgeMode = 'ORGANIC';
    state.battle = { ...state.battle!, effectiveness: null, effectivenessKnown: false };

    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(container.querySelector('.effect-result strong')?.textContent).toBe('—');
    expect(screen.queryByText('UNKNOWN')).toBeNull();
    expect(screen.queryByRole('button', { name: 'RESOLVE ATTACK' })).toBeNull();
  });

  it('renders five stars between the name and Pokédex shortcut with half-star fill', () => {
    const { catalog, state } = fixture(1);
    state.battle!.opponents[0].rarity = {
      relativeTier: 'WEAK', innateTier: 'STANDARD', baseStars: 1, areaAdjustment: -0.5, stars: 0.5,
    };

    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);
    const identityChildren = container.querySelector('.battle-name-row')?.children;

    expect(identityChildren?.[0].tagName).toBe('H1');
    expect(identityChildren?.[1].classList.contains('rarity-stars')).toBe(true);
    expect(identityChildren?.[2].classList.contains('battle-dex-link')).toBe(true);
    expect(container.querySelectorAll('.rarity-star')).toHaveLength(5);
    expect(container.querySelectorAll('.rarity-star-fill[style="width: 50%;"]')).toHaveLength(1);
    expect(screen.getByLabelText('0.5 of 5 stars; STANDARD innate quality; WEAK for this encounter table')).toBeTruthy();
  });

  it('combines both tiers in the rarity title', () => {
    const { catalog, state } = fixture(1);
    state.battleTab = 'RARITY';
    state.battle!.opponents[0].rarity = {
      relativeTier: 'WEAK', innateTier: 'STANDARD', baseStars: 1, areaAdjustment: -0.5, stars: 0.5,
    };

    render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('WEAK STANDARD')).toBeTruthy();
    expect(screen.queryByText(/CURRENT PARTY/)).toBeNull();
  });

  it('labels unavailable area evidence without changing innate stars', () => {
    const { catalog, state } = fixture(1);
    state.battleTab = 'RARITY';
    state.battle!.opponents[0].rarity = {
      relativeTier: null, innateTier: 'TRAINED', baseStars: 2, areaAdjustment: null, stars: 2,
      areaOutcome: 'AREA_NOT_IN_CATALOG', currentAreaBaseId: 0x0202, matchingAreaCount: 0, candidateAreaCount: 0,
    };

    render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('TRAINED')).toBeTruthy();
    expect(screen.queryByText(/UNKNOWN TRAINED/)).toBeNull();
    expect(screen.getByLabelText('2 of 5 stars; TRAINED innate quality; area comparison unavailable')).toBeTruthy();
    expect(screen.getByText('Area comparison not available.')).toBeTruthy();
    expect(screen.queryByText(/0x0202|SaveRAM|encounter catalog/i)).toBeNull();
  });

  it('uses the same generic fallback for a missing Battle Target Entry', () => {
    const { catalog, state } = fixture(1);
    catalog.species[0].description = null;
    state.battleTab = 'ENTRY';

    render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('Pokédex data not available.')).toBeTruthy();
  });

  it('uses a concise fallback when a unique matching encounter table supplies rarity', () => {
    const { catalog, state } = fixture(1);
    state.battleTab = 'RARITY';
    state.battle!.opponents[0].rarity = {
      relativeTier: 'ORDINARY', innateTier: 'TRAINED', baseStars: 2, areaAdjustment: 0, stars: 2,
      areaOutcome: 'APPLIED_UNIQUE_ENCOUNTER', currentAreaBaseId: 0x0202, matchingAreaCount: 0, candidateAreaCount: 1,
    };

    render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('ORDINARY TRAINED')).toBeTruthy();
    expect(screen.getByText('Compared with matching wild encounters. First word: level. Second: innate quality.')).toBeTruthy();
    expect(screen.queryByText(/SaveRAM|0x0202/)).toBeNull();
  });

  it('uses the resolved road or city name in the rarity explanation', () => {
    const { catalog, state } = fixture(1);
    state.battleTab = 'RARITY';
    state.battle!.opponents[0].rarity = {
      relativeTier: 'ORDINARY', innateTier: 'TRAINED', baseStars: 2, areaAdjustment: 0, stars: 2,
      currentAreaName: 'Route 101',
    };

    render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('Compared with Route 101 wild encounters. First word: level. Second: innate quality.')).toBeTruthy();
  });

  it('reports unavailable rarity without inventing stars when innate data is missing', () => {
    const { catalog, state } = fixture(1);
    state.battleTab = 'RARITY';
    state.battle!.opponents[0].rarity = {
      relativeTier: 'COMPETENT', innateTier: null, baseStars: null, areaAdjustment: 0.5, stars: null,
    };

    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('RARITY UNAVAILABLE')).toBeTruthy();
    expect(container.querySelector('.rarity-stars')).toBeNull();
  });
});

function fixture(opponentCount: number): { catalog: Catalog; state: State } {
  const catalog = {
    hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], areas: [], balls: [], capabilities: {},
    types: [{ id: 1, name: 'Fighting', background: '#c33', foreground: '#fff', border: '#811' }],
    moves: [{ id: 1, name: 'Pound', typeId: 1, category: 'PHYSICAL', power: 40, accuracy: 100, pp: 35, priority: 0, effectId: 0, description: null }],
    species: [
      { id: 1, dex: 1, name: 'Hitmonlee', typeIds: [1], stats: null, description: 'Entry', height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
      { id: 2, dex: 2, name: 'Hitmonchan', typeIds: [1], stats: null, description: 'Entry', height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
    ],
  } satisfies Catalog;
  const opponents: NonNullable<State['battle']>['opponents'] = catalog.species.slice(0, opponentCount).map(species => ({
    speciesId: species.id,
    level: 34,
    typeIds: species.typeIds,
    rarity: { relativeTier: 'ORDINARY', innateTier: 'VETERAN', baseStars: 3, areaAdjustment: 0, stars: 3 },
    moves: [],
  }));
  const state = {
    version: 1, screen: 'BATTLE', priorScreen: 'POKEDEX', settingsReturnScreen: 'BATTLE', selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null, battleTab: 'ATTACK',
    settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', battlePollingIntervalMs: 5 },
    speciesState: { 1: { seen: true, caught: false, team: false, ballId: null }, 2: { seen: true, caught: false, team: false, ballId: null } }, observedMoves: {},
    battle: { opponents, targetIndex: 0, targetMode: 'AUTOMATIC', capabilities: {}, selectedMoveId: 1, effectiveness: 'NEUTRAL', effectivenessKnown: true },
    catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
    loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  } satisfies State;
  return { catalog, state };
}
