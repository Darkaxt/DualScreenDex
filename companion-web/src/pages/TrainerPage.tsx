import type { State, TrainerProgressView } from '../models';
import { Header, ProgressTrophyIcon, TrainerCardIcon } from '../components';
import { TrainerCardContent } from './TrainerCardPage';
import { TrainerProgressPage } from './TrainerProgressPage';

export function TrainerPage({
  state,
  send,
  onBack,
}: {
  state: State;
  send: (type: string, values?: Record<string, string>) => void;
  onBack: () => void;
}) {
  const progress = state.trainerProgress;
  const destination = progress?.selectedDestination ?? 'CARD';
  return <section class="screen trainer-screen">
    <Header title="TRAINER" onBack={onBack} actions={<nav class="trainer-destination-switcher" aria-label="Trainer pages">
      <button type="button" class="header-action trainer-destination-action" aria-label="Card" aria-pressed={destination === 'CARD'} onClick={() => send('TRAINER_DESTINATION', { value: 'CARD' })}><TrainerCardIcon /></button>
      {progress && <button type="button" class="header-action trainer-destination-action" aria-label="Progress" aria-pressed={destination === 'PROGRESS'} onClick={() => send('TRAINER_DESTINATION', { value: 'PROGRESS' })}><ProgressTrophyIcon /></button>}
    </nav>} />
    {destination === 'PROGRESS' && progress
      ? <TrainerProgressPage progress={progress} onSelectSection={(value: TrainerProgressView['selectedSection']) => send('PROGRESS_SECTION', { value })} />
      : <TrainerCardContent state={state} />}
  </section>;
}
