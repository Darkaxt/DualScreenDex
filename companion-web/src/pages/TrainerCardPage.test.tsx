import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { State } from '../models';
import { TrainerCardPage } from './TrainerCardPage';

afterEach(cleanup);

describe('Trainer Card', () => {
  it('uses a title-only header without redundant live or read-only diagnostics', () => {
    const { container } = render(<TrainerCardPage state={trainerState()} onBack={vi.fn()} />);

    expect(screen.getAllByText('TRAINER CARD').length).toBeGreaterThan(0);
    expect(container.querySelector('.header-title small')).toBeNull();
    expect(screen.queryByText(/LIVE|READ ONLY/)).toBeNull();
  });

  it('composes every published trainer field inside one cohesive card shell', () => {
    const { container } = render(<TrainerCardPage state={trainerState()} onBack={vi.fn()} />);

    const card = container.querySelector('.trainer-card-shell');
    expect(card).toBeTruthy();
    expect(card?.textContent).toContain('MAY');
    expect(card?.textContent).toContain('ID 12345');
    expect(card?.textContent).toContain('₽98,765');
    expect(card?.textContent).toContain('12:34');
    expect(card?.textContent).toContain('42');
    expect(card?.textContent).toContain('7');
    expect(card?.textContent).toContain('2');
    expect(card?.querySelector('.trainer-avatar')).toBeTruthy();
    expect(card?.querySelectorAll('.trainer-badge')).toHaveLength(8);
    expect(container.querySelector('.trainer-identity')).toBeNull();
    expect(container.querySelector('.trainer-facts')).toBeNull();
    expect(container.querySelector('.trainer-badges.paper-panel')).toBeNull();
  });

  it('renders normalized identity, progress, earned badges, and the existing back action', () => {
    const back = vi.fn();
    const state = trainerState();

    const { container } = render(<TrainerCardPage state={state} onBack={back} />);

    expect(screen.getByRole('heading', { name: 'MAY' })).toBeTruthy();
    expect(screen.getByText('ID 12345')).toBeTruthy();
    expect(screen.getByText('₽98,765')).toBeTruthy();
    expect(screen.getByText('12:34')).toBeTruthy();
    expect(screen.getByText('42')).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
    expect(container.querySelectorAll('.trainer-badge')).toHaveLength(8);
    expect(container.querySelectorAll('.trainer-badge.earned')).toHaveLength(2);
    expect(container.querySelector('.trainer-avatar img')?.getAttribute('src')).toContain('/api/trainer-assets/');
    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(back).toHaveBeenCalledOnce();
  });

  it('renders independent unavailable fields and avatar assets without breaking the card', () => {
    const state = trainerState();
    state.trainer = { ...state.trainer!, stars: null, avatarUrl: null, badges: state.trainer!.badges.map(badge => ({ ...badge, imageUrl: null })) };

    const { container } = render(<TrainerCardPage state={state} onBack={vi.fn()} />);

    expect(screen.getByText('—')).toBeTruthy();
    expect(container.querySelector('.trainer-avatar-fallback')).toBeTruthy();
    expect(container.querySelectorAll('.trainer-badge')).toHaveLength(8);
  });

  it('renders unread card facts as neutral unknowns without diagnostics', () => {
    const state = trainerState();
    state.trainer = {
      ...state.trainer!,
      publicTrainerId: null,
      money: null,
      playTimeHours: null,
      playTimeMinutes: null,
      dexSeen: null,
      dexCaught: null,
      stars: null,
      badges: state.trainer!.badges.map(badge => ({ ...badge, earned: null })),
    };

    const { container } = render(<TrainerCardPage state={state} onBack={vi.fn()} />);

    expect(container.querySelector('.trainer-card-shell')).toBeTruthy();
    expect(screen.getByText('ID —')).toBeTruthy();
    expect(screen.getAllByText('—')).toHaveLength(5);
    expect(container.querySelectorAll('.trainer-badge.earned')).toHaveLength(0);
    expect(container.querySelectorAll('[aria-label$="status unknown"]')).toHaveLength(8);
    expect(container.textContent).not.toMatch(/parser|capability|unavailable/i);
  });
});

function trainerState(): State {
  return {
    version: 1, screen: 'TRAINER', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
    filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
    settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
    trainerCardUnlocked: true,
    activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
    trainer: {
      name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 98765, playTimeHours: 12, playTimeMinutes: 34,
      dexSeen: 42, dexCaught: 7, stars: 2, avatarUrl: '/api/trainer-assets/trainer%2Favatar%2Ffemale.png',
      badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index === 0 || index === 2, imageUrl: `/api/trainer-assets/trainer%2Fbadge%2F${index + 1}.png` })),
    },
    party: [],
  };
}
