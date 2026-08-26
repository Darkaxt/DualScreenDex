import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, PartyAnalysis, PartyMemberView, State } from '../models';
import { PartyAnalysisPage } from './PartyAnalysisPage';

afterEach(cleanup);

describe('Party Analysis', () => {
  it('renders the four factual sections in order and links through typed destinations', () => {
    const openMember = vi.fn();
    const openMove = vi.fn();
    const openAbility = vi.fn();
    const openSpecies = vi.fn();
    const { container } = render(<PartyAnalysisPage
      catalog={catalog}
      state={state}
      analysis={analysis}
      onBack={vi.fn()}
      openMember={openMember}
      openMove={openMove}
      openAbility={openAbility}
      openSpecies={openSpecies}
    />);

    const sections = Array.from(container.querySelectorAll('.party-analysis-section > h2')).map(node => node.textContent);
    expect(sections).toEqual(['TEAM SUMMARY', 'OFFENSIVE COVERAGE', 'DEFENSIVE PROFILE', 'DEVELOPMENT']);
    expect(screen.getByText('1 Pokémon')).toBeTruthy();
    expect(screen.getByText('Lv 18')).toBeTruthy();
    expect(screen.getByText('Super effective')).toBeTruthy();
    expect(screen.getByText('No effective move')).toBeTruthy();
    expect(screen.getByText('Repeated weakness')).toBeTruthy();
    expect(screen.getByText('Available now')).toBeTruthy();

    fireEvent.click(screen.getAllByRole('button', { name: 'Open SPARK details' })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Open Static ability' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open Thunderbolt move' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open RAICHU in Pokédex' }));
    expect(openMember).toHaveBeenCalledWith(0);
    expect(openAbility).toHaveBeenCalledWith(9);
    expect(openMove).toHaveBeenCalledWith(85);
    expect(openSpecies).toHaveBeenCalledWith(26);
  });

  it('omits unknown-dependent calculations without inventing neutral values or diagnostic copy', () => {
    const { container } = render(<PartyAnalysisPage
      catalog={catalog}
      state={state}
      analysis={{ ...analysis, offensiveCoverage: null, defensiveProfile: null }}
      onBack={vi.fn()}
      openMember={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openSpecies={vi.fn()}
    />);

    expect(container.querySelector('.party-analysis-offense')).toBeNull();
    expect(container.querySelector('.party-analysis-defense')).toBeNull();
    expect(document.body.textContent).not.toMatch(/unsupported|capability|parser|fallback|neutral by default/i);
    expect(Array.from(container.querySelectorAll('.party-analysis-section > h2')).map(node => node.textContent)).toEqual(['TEAM SUMMARY', 'DEVELOPMENT']);
  });

  it('uses accessible non-color-only outcome labels and no subjective recommendations', () => {
    render(<PartyAnalysisPage catalog={catalog} state={state} analysis={analysis} onBack={vi.fn()} openMember={vi.fn()} openMove={vi.fn()} openAbility={vi.fn()} openSpecies={vi.fn()} />);

    expect(screen.getByText('Super effective')).toBeTruthy();
    expect(screen.getByText('Neutral only')).toBeTruthy();
    expect(screen.getByText('No effective move')).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/bad team|replace|recommended|grade/i);
  });
});

const catalog: Catalog = {
  hash: 'sha', crc32: '0', family: 'EMERALD', platform: 'GBA', rulesets: [], areas: [], balls: [], capabilities: {},
  species: [
    { id: 25, dex: 25, name: 'PIKACHU', typeIds: [13], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [{ id: 9, name: 'Static', description: null, mechanics: [] }], evolutions: [], hasSprite: true },
    { id: 26, dex: 26, name: 'RAICHU', typeIds: [13], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: true },
  ],
  moves: [{ id: 85, name: 'Thunderbolt', typeId: 13, category: 'SPECIAL', power: 90, accuracy: 100, pp: 15, priority: 0, effectId: null, description: null }],
  types: [
    { id: 2, name: 'FIGHTING', foreground: '#fff', background: '#984', border: '#632' },
    { id: 4, name: 'GROUND', foreground: '#222', background: '#dbb', border: '#876' },
    { id: 6, name: 'ROCK', foreground: '#222', background: '#ba7', border: '#764' },
    { id: 13, name: 'ELECTRIC', foreground: '#222', background: '#ed4', border: '#a83' },
  ],
};

const party: PartyMemberView[] = [{
  slot: 0, occupied: true, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13], nickname: 'SPARK', level: 18,
  isEgg: false, gender: 'FEMALE', nature: 'Adamant', abilityId: 9, abilityName: 'Static', heldItemId: null, heldItemName: null,
  currentHp: 31, maximumHp: 45, status: null, experienceProgress: .5, stats: {}, moves: [{ slot: 0, moveId: 85, name: 'Thunderbolt', currentPp: 12, maximumPp: 15 }],
}];

const state: State = {
  version: 1, screen: 'PARTY', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null,
  battleTab: 'ENTRY', settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: { 26: { seen: true, caught: false, team: false, ballId: null } }, observedMoves: {}, party, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 1, totalUnits: 1 },
};

const analysis: PartyAnalysis = {
  teamSummary: { partySize: 1, minimumLevel: 18, maximumLevel: 18, faintedCount: 0, statusCount: 0, moveDistribution: { physical: 0, special: 1, status: 0, unresolved: 0 } },
  offensiveCoverage: { contributingMoveCount: 1, types: [
    { defendingTypeId: 2, outcome: 'SUPER_EFFECTIVE', bestMultiplierPercent: 200, attackingTypeIds: [13], memberSlots: [0] },
    { defendingTypeId: 6, outcome: 'NEUTRAL_ONLY', bestMultiplierPercent: 100, attackingTypeIds: [13], memberSlots: [0] },
    { defendingTypeId: 4, outcome: 'NO_EFFECTIVE_KNOWN_OPTION', bestMultiplierPercent: 0, attackingTypeIds: [13], memberSlots: [0] },
  ] },
  defensiveProfile: {
    members: [{ slot: 0, speciesId: 25, typeIds: [13], availableForImmediateBattle: true, weaknessTypeIds: [4], resistanceTypeIds: [13], immunityTypeIds: [], abilityModifiers: [{ abilityId: 9, attackingTypeId: 13, numerator: 1, denominator: 2 }] }],
    unavailableMemberSlots: [], repeatedWeaknesses: [{ attackingTypeId: 4, memberCount: 1 }],
  },
  development: {
    evolutionOpportunities: [{ slot: 0, speciesId: 25, targetSpeciesId: 26, methodId: 4, parameter: 16, availableNow: true }],
    nearbyMoves: [{ slot: 0, speciesId: 25, moveId: 85, level: 20, levelsAway: 2 }],
    moveRoleGaps: ['PHYSICAL'],
  },
};
