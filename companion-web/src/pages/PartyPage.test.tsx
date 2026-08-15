import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { PartyPage } from './PartyPage';

afterEach(cleanup);

describe('Party', () => {
  it('renders six stable slots and presentation-safe details for the selected member', () => {
    const openMove = vi.fn();
    const openAbility = vi.fn();
    const state = partyState('ORGANIC');

    const { container } = render(<PartyPage catalog={catalog} state={state} onBack={vi.fn()} openMove={openMove} openAbility={openAbility} />);

    expect(container.querySelectorAll('.party-slot')).toHaveLength(6);
    expect(screen.getAllByText('SPARK').length).toBeGreaterThan(0);
    expect(screen.getByText('PIKACHU · Lv 18')).toBeTruthy();
    expect(screen.getByText('Lv 18')).toBeTruthy();
    expect(screen.getByText('31 / 45')).toBeTruthy();
    expect(screen.getByText('PAR')).toBeTruthy();
    expect(screen.getByText('Adamant')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Static' })).toBeTruthy();
    expect(screen.getByText('No held item data')).toBeTruthy();
    expect(container.querySelectorAll('.party-move-row')).toHaveLength(4);
    expect(screen.queryByText('999')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Thunderbolt' }));
    expect(openMove).toHaveBeenCalledWith(85);
    fireEvent.click(screen.getByRole('button', { name: 'Static' }));
    expect(openAbility).toHaveBeenCalledWith(9);
  });

  it('keeps the owned party visible in Organic and Hidden modes', () => {
    const props = { catalog, onBack: vi.fn(), openMove: vi.fn(), openAbility: vi.fn() };
    const rendered = render(<PartyPage {...props} state={partyState('ORGANIC')} />);
    expect(screen.getAllByText('SPARK').length).toBeGreaterThan(0);

    rendered.rerender(<PartyPage {...props} state={partyState('HIDDEN')} />);
    expect(screen.getAllByText('SPARK').length).toBeGreaterThan(0);
  });
});

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
};

function partyState(knowledgeMode: State['settings']['knowledgeMode']): State {
  return {
    version: 1, screen: 'PARTY', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, selectedPartySlot: 0,
    filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
    settings: { knowledgeMode, attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
    activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
    trainer: null,
    party: [
      {
        slot: 0, occupied: true, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13],
        nickname: 'SPARK', level: 18, isEgg: false, gender: 'FEMALE', nature: 'Adamant', abilityId: 9, abilityName: 'Static',
        heldItemId: null, heldItemName: null, currentHp: 31, maximumHp: 45, status: 'PAR', experienceProgress: .5,
        stats: { HP: 45, ATTACK: 28, DEFENSE: 22, SPEED: 38, 'SP. ATK': 30, 'SP. DEF': 26 },
        moves: [
          { slot: 0, moveId: 85, name: 'Thunderbolt', currentPp: 12, maximumPp: 15 },
          { slot: 1, moveId: null, name: null, currentPp: null, maximumPp: null },
          { slot: 2, moveId: null, name: null, currentPp: null, maximumPp: null },
          { slot: 3, moveId: null, name: null, currentPp: null, maximumPp: null },
        ],
      },
      ...Array.from({ length: 5 }, (_, index) => ({ slot: index + 1, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null, isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [] })),
    ],
  };
}
