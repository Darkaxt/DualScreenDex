import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import type { Catalog, State } from '../models';
import { AbilityDetail } from './AbilityDetail';

afterEach(cleanup);

describe('ability detail', () => {
  it('shows the validated ROM description and species that can have it', () => {
    const ability = { id: 66, name: 'Blaze', description: 'Ups Fire moves in a pinch.' };
    const catalog = {
      hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
      species: [{ id: 6, dex: 6, name: 'Charizard', typeIds: [], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [ability], evolutions: [], hasSprite: false }],
    } satisfies Catalog;
    const state = {
      version: 1, screen: 'DETAIL', priorScreen: 'POKEDEX', settingsReturnScreen: 'DETAIL', selectedSpeciesId: 6, filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
      settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
      speciesState: { 6: { seen: true, caught: true, team: false, ballId: null } }, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
      activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
    } satisfies State;

    render(<AbilityDetail catalog={catalog} state={state} abilityId={66} onBack={() => undefined} />);

    expect(screen.getByText('Ups Fire moves in a pinch.')).toBeTruthy();
    expect(screen.getByText('Charizard')).toBeTruthy();
    expect(screen.getByText('ROM ABILITY #66')).toBeTruthy();
  });
});
