import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { PokedexDetail } from './PokedexDetail';

afterEach(cleanup);

describe('Organic Pokédex move knowledge', () => {
  it('shows only witnessed moves without their ROM acquisition levels before capture', () => {
    renderDetail(uncaughtState);

    expect(screen.getByRole('button', { name: /Scratch/ })).toBeTruthy();
    expect(screen.getByText('OBSERVED · 3×')).toBeTruthy();
    expect(screen.queryByText('Ember')).toBeNull();
    expect(screen.queryByText('Lv 7')).toBeNull();
  });

  it('unlocks the complete ROM learnset after capture', () => {
    renderDetail({
      ...uncaughtState,
      speciesState: { 4: { seen: true, caught: true, team: false, ballId: null } },
    });

    expect(screen.getByText('Scratch')).toBeTruthy();
    expect(screen.getByText('Ember')).toBeTruthy();
    expect(screen.getByText('Lv 7')).toBeTruthy();
  });
});

function renderDetail(state: State) {
  render(<PokedexDetail
    catalog={catalog}
    state={state}
    send={vi.fn()}
    tab="MOVES"
    setTab={vi.fn()}
    openMove={vi.fn()}
    openAbility={vi.fn()}
  />);
}

const catalog = {
  hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', areas: [], balls: [], capabilities: {},
  rulesets: [{ id: 'base', label: 'Base', sourceOffset: 1, confidence: 1, primary: true }],
  types: [{ id: 0, name: 'Normal', foreground: '#111', background: '#ddd', border: '#999' }, { id: 10, name: 'Fire', foreground: '#fff', background: '#f80', border: '#b40' }],
  moves: [
    { id: 10, name: 'Scratch', typeId: 0, category: 'PHYSICAL', power: 40, accuracy: 100, pp: 35, priority: 0, effectId: 0, description: 'Scratches the target.' },
    { id: 52, name: 'Ember', typeId: 10, category: 'SPECIAL', power: 40, accuracy: 100, pp: 25, priority: 0, effectId: 4, description: 'May burn the target.' },
  ],
  species: [{
    id: 4, dex: 4, name: 'Charmander', typeIds: [10], stats: null, description: 'Entry', height: null, weight: null,
    learnset: [{ level: 1, moveId: 10 }, { level: 7, moveId: 52 }],
    learnsets: { base: [{ level: 1, moveId: 10 }, { level: 7, moveId: 52 }] },
    normalizedLearnsets: { base: [{ moveId: 10, initial: true, levels: [], label: 'Initial' }, { moveId: 52, initial: false, levels: [7], label: 'Lv 7' }] },
    moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false,
  }],
} satisfies Catalog;

const uncaughtState = {
  version: 1, screen: 'DETAIL', priorScreen: 'BATTLE', settingsReturnScreen: 'DETAIL', selectedSpeciesId: 4,
  filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY', speciesState: { 4: { seen: true, caught: false, team: false, ballId: null } }, battle: null,
  observedMoves: { 4: [{ moveId: 10, encounters: 3, lastSeen: 8 }] },
  catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: 'base', rulesetAssumed: false,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
} satisfies State;
