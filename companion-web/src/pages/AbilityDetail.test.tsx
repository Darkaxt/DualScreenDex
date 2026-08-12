import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import type { Catalog, State } from '../models';
import { AbilityDetail } from './AbilityDetail';

afterEach(cleanup);

describe('ability detail', () => {
  it('shows the validated ROM description and species that can have it', () => {
    const ability = {
      id: 66,
      name: 'Blaze',
      description: 'Ups Fire moves in a pinch.',
      mechanics: [
        { kind: 'ACTIVATION_THRESHOLD', label: 'Activation', value: 'HP ≤ 1/3', numerator: 1, denominator: 3 },
        { kind: 'MULTIPLIER', label: 'Power', value: 'Fire move power ×1.5', numerator: 150, denominator: 100 },
      ],
    };
    const catalog = {
      hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
      species: [{ id: 6, dex: 6, name: 'Charizard', typeIds: [], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [ability], evolutions: [], hasSprite: false }],
    } satisfies Catalog;
    const state = {
      version: 1, screen: 'DETAIL', priorScreen: 'POKEDEX', settingsReturnScreen: 'DETAIL', selectedSpeciesId: 6, filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
      settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
      speciesState: { 6: { seen: true, caught: true, team: false, ballId: null } }, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
      activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
    } satisfies State;

    render(<AbilityDetail catalog={catalog} state={state} abilityId={66} onBack={() => undefined} />);

    expect(screen.getByText('Ups Fire moves in a pinch.')).toBeTruthy();
    expect(screen.getByText('Charizard')).toBeTruthy();
    expect(screen.queryByText(/ROM ABILITY|ROM-VALIDATED/i)).toBeNull();
    expect(screen.getByText('KNOWN VALUES')).toBeTruthy();
    expect(screen.getByText('HP ≤ 1/3')).toBeTruthy();
    expect(screen.getByText('Fire move power ×1.5')).toBeTruthy();
  });
});
