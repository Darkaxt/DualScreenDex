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

  it('preserves the target-switch row for a double encounter', () => {
    const { catalog, state } = fixture(2);
    const { container } = render(<BattlePage catalog={catalog} state={state} send={vi.fn()} openMove={vi.fn()} openSpecies={vi.fn()} />);

    expect(container.querySelector('.battle-screen')?.classList.contains('battle-double')).toBe(true);
    expect(container.querySelectorAll('.target-switch button')).toHaveLength(2);
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
  const opponents = catalog.species.slice(0, opponentCount).map(species => ({ speciesId: species.id, level: 34, rarity: 'Ordinary Good', moves: [] }));
  const state = {
    version: 1, screen: 'BATTLE', priorScreen: 'POKEDEX', settingsReturnScreen: 'BATTLE', selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null, battleTab: 'ATTACK',
    settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: { 1: { seen: true, caught: false, team: false, ballId: null }, 2: { seen: true, caught: false, team: false, ballId: null } }, observedMoves: {},
    battle: { opponents, targetIndex: 0, selectedMoveId: 1, effectiveness: 'NEUTRAL', effectivenessKnown: true },
    catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
    loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  } satisfies State;
  return { catalog, state };
}
