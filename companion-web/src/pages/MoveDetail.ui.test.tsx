import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import type { Catalog, State } from '../models';
import { MoveDetail } from './MoveDetail';

afterEach(cleanup);

describe('move detail header', () => {
  it('uses the move name without a redundant page-type subtitle', () => {
    render(<MoveDetail catalog={catalog} state={state} moveId={1} onBack={() => undefined} />);

    expect(screen.getByText('POUND')).toBeTruthy();
    expect(screen.queryByText('MOVE DETAIL')).toBeNull();
  });
});

const catalog = {
  hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], types: [], areas: [], balls: [], capabilities: {},
  moves: [{ id: 1, name: 'POUND', typeId: 0, power: 40, accuracy: 100, pp: 35, priority: 0, category: 'PHYSICAL', effectId: 0, description: 'Pounds the foe.' }],
} satisfies Catalog;

const state = {
  version: 1, screen: 'DETAIL', priorScreen: 'POKEDEX', settingsReturnScreen: 'DETAIL', selectedSpeciesId: null,
  filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY', speciesState: {}, observedMoves: {}, battle: null,
  catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
} satisfies State;
