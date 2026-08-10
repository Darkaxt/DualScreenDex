import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { SimulatorPanel } from './SimulatorPanel';

afterEach(cleanup);

describe('simulator ROM identity', () => {
  it('keeps the loaded file name, family and checksum visible in the left panel', () => {
    const catalog = {
      hash: 'sha', crc32: 'C3A9F204', family: 'EMERALD', platform: 'GBA',
      rulesets: [], species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
    } satisfies Catalog;
    const state = {
      version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
      filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY', speciesState: {}, observedMoves: {}, battle: null, catalogReady: true,
      catalogName: 'Pokemon - Modern Emerald Version v3.5 (USA, Europe).zip', error: null,
      activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
      settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    } satisfies State;

    render(<SimulatorPanel catalog={catalog} state={state} onUpload={vi.fn()} send={vi.fn()} />);

    expect(screen.getByText('LOADED ROM')).toBeTruthy();
    expect(screen.getByText(state.catalogName)).toBeTruthy();
    expect(screen.getByText('EMERALD · CRC32 C3A9F204')).toBeTruthy();
  });

  it('owns the attack-reference control while a simulated battle is active', () => {
    const catalog = {
      hash: 'sha', crc32: 'C3A9F204', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], types: [], areas: [], balls: [], capabilities: {},
      moves: [{ id: 1, name: 'Pound', typeId: 1, category: 'PHYSICAL', power: 40, accuracy: 100, pp: 35, priority: 0, effectId: 0, description: null }],
    } satisfies Catalog;
    const state = {
      version: 1, screen: 'BATTLE', priorScreen: 'POKEDEX', settingsReturnScreen: 'BATTLE', selectedSpeciesId: null,
      filter: 'ALL', selectedAreaId: null, battleTab: 'ATTACK', speciesState: {}, observedMoves: {},
      battle: { opponents: [], targetIndex: 0, selectedMoveId: 1, effectiveness: null, effectivenessKnown: false }, catalogReady: true,
      catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
      loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
      settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    } satisfies State;

    render(<SimulatorPanel catalog={catalog} state={state} onUpload={vi.fn()} send={vi.fn()} />);

    expect(screen.getByText('ATTACK REFERENCE')).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Pound' })).toBeTruthy();
  });
});
