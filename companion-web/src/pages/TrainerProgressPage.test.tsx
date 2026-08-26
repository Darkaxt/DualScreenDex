import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { State } from '../models';
import { TrainerPage } from './TrainerPage';

afterEach(cleanup);

describe('Trainer progress', () => {
  it('shares the Trainer license and remembers normal destination and section choices', () => {
    const send = vi.fn();
    render(<TrainerPage state={trainerState()} send={send} onBack={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Card' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Progress' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Metrics' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Challenges' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Timeline' })).toBeTruthy();
    expect(screen.getByText('GAME TOTALS')).toBeTruthy();
    expect(screen.getByText('TRACKED JOURNEY')).toBeTruthy();
    expect(screen.getByText('3,000')).toBeTruthy();
    expect(screen.getByText('2')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Challenges' }));
    expect(send).toHaveBeenCalledWith('PROGRESS_SECTION', { value: 'CHALLENGES' });
    fireEvent.click(screen.getByRole('button', { name: 'Card' }));
    expect(send).toHaveBeenCalledWith('TRAINER_DESTINATION', { value: 'CARD' });
  });

  it('shows player-facing challenge and timeline details without internals', () => {
    const state = trainerState();
    state.trainerProgress!.selectedSection = 'CHALLENGES';
    const { rerender, container } = render(<TrainerPage state={state} send={vi.fn()} onBack={vi.fn()} />);

    expect(screen.getByText('A New Partner')).toBeTruthy();
    expect(screen.getByText('Catch your first Pokémon on this journey.')).toBeTruthy();
    expect(screen.getByText('1 / 1')).toBeTruthy();

    state.trainerProgress!.selectedSection = 'TIMELINE';
    rerender(<TrainerPage state={{ ...state }} send={vi.fn()} onBack={vi.fn()} />);
    expect(screen.getByText('Captures +1')).toBeTruthy();
    expect(container.textContent).not.toMatch(/parser|address|offset|capability|fingerprint/i);
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
      name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 3000, playTimeHours: 2, playTimeMinutes: 15,
      dexSeen: 12, dexCaught: 4, stars: 1, avatarUrl: null,
      badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index < 2, imageUrl: null })),
    },
    trainerProgress: {
      selectedDestination: 'PROGRESS', selectedSection: 'METRICS',
      gameTotals: [
        { key: 'money', label: 'Money', value: 3000 },
        { key: 'seen', label: 'Pokédex seen', value: 12 },
      ],
      trackedJourney: [{ key: 'captures', label: 'Captures', value: 2 }],
      challenges: [{ key: 'first', title: 'A New Partner', description: 'Catch your first Pokémon on this journey.', category: 'COLLECTION', progress: 1, target: 1, complete: true }],
      timeline: [{ recordedAtEpochMs: 1000, changes: ['Captures +1'], milestone: true }],
    },
    party: [],
  };
}
