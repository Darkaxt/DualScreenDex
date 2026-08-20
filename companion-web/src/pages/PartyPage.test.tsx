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
    expect(screen.getAllByText('31 / 45').length).toBeGreaterThan(0);
    expect(screen.getAllByText('PAR').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('img', { name: 'Paralyzed' }).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Held item present')).toBeTruthy();
    expect(container.querySelectorAll('.party-type-art')).toHaveLength(2);
    expect(container.querySelector('.party-detail[data-condition="statused"]')).toBeTruthy();
    expect(screen.getByText('Adamant')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Static' })).toBeTruthy();
    expect(screen.getByText('Held item')).toBeTruthy();
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

  it('distinguishes fainted no-art and unidentified silhouettes without leaking unavailable fields', () => {
    const state = partyState('ORGANIC');
    state.party = [
      { ...state.party![0], spriteUrl: null, currentHp: 0, status: null, heldItemId: null, heldItemName: null, hasHeldItem: false },
      { ...state.party![0], slot: 1, speciesId: null, speciesName: null, spriteUrl: '/api/sprites/species/26.png', nickname: null, typeIds: [], abilityId: null, abilityName: null, status: null, hasHeldItem: null },
    ];

    const { container } = render(<PartyPage catalog={catalog} state={state} onBack={vi.fn()} openMove={vi.fn()} openAbility={vi.fn()} />);

    expect(container.querySelector('.party-slot.fainted')).toBeTruthy();
    expect(screen.getAllByLabelText('Party artwork unavailable').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: 'Party slot 2: Unknown partner' }));
    expect(screen.getAllByAltText('Unidentified Pokémon').length).toBeGreaterThan(0);
    expect(container.querySelector('.party-detail img.identity-silhouette')).toBeTruthy();
    expect(container.querySelector('.party-detail .party-type-art')).toBeNull();
    expect(screen.queryByText(/999/)).toBeNull();
  });

  it('renders partial and engine-specific fields without inventing missing artwork', () => {
    const state = partyState('DISCOVERED');
    state.party = [{
      ...state.party![0], speciesName: 'A VERY LONG PARTNER NAME', nickname: null, spriteUrl: null, typeIds: [99], status: 'CUSTOM',
      heldItemId: null, heldItemName: null, hasHeldItem: null, currentHp: null, maximumHp: null, stats: {}, moves: [],
    }];
    const customCatalog = { ...catalog, types: [{ id: 99, name: 'COSMIC-LIGHT', foreground: '#fff', background: '#4256a6', border: '#101c55' }] };

    const { container } = render(<PartyPage catalog={customCatalog} state={state} onBack={vi.fn()} openMove={vi.fn()} openAbility={vi.fn()} />);

    expect(screen.getAllByText('COSMIC-LIGHT').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('img', { name: 'CUSTOM status' }).length).toBeGreaterThan(0);
    expect(screen.getByText('Held item unavailable')).toBeTruthy();
    expect(container.querySelector('.party-type-art abbr')?.textContent).toBe('CO');
    expect(container.querySelector('.party-detail[data-condition="partial"]')).toBeTruthy();
  });
});

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [
    { id: 13, name: 'ELECTRIC', foreground: '#2b2300', background: '#f5d642', border: '#9c851c' },
    { id: 2, name: 'FLYING', foreground: '#17253d', background: '#a9c7f0', border: '#5b79a4' },
  ], areas: [], balls: [], capabilities: {},
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
        slot: 0, occupied: true, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13, 2],
        nickname: 'SPARK', level: 18, isEgg: false, gender: 'FEMALE', nature: 'Adamant', abilityId: 9, abilityName: 'Static',
        heldItemId: null, heldItemName: null, hasHeldItem: true, currentHp: 31, maximumHp: 45, status: 'PAR', experienceProgress: .5,
        stats: { HP: 45, ATTACK: 28, DEFENSE: 22, SPEED: 38, 'SP. ATK': 30, 'SP. DEF': 26 },
        moves: [
          { slot: 0, moveId: 85, name: 'Thunderbolt', currentPp: 12, maximumPp: 15 },
          { slot: 1, moveId: null, name: null, currentPp: null, maximumPp: null },
          { slot: 2, moveId: null, name: null, currentPp: null, maximumPp: null },
          { slot: 3, moveId: null, name: null, currentPp: null, maximumPp: null },
        ],
      },
      ...Array.from({ length: 5 }, (_, index) => ({ slot: index + 1, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null, isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, hasHeldItem: null, currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [] })),
    ],
  };
}
