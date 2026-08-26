import type { State, TrainerProgressView } from '../models';
import { Header } from '../components';
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
    <Header title="TRAINER" onBack={onBack} />
    <nav class="trainer-destination-tabs" aria-label="Trainer pages">
      <button type="button" class={destination === 'CARD' ? 'active' : ''} aria-pressed={destination === 'CARD'} onClick={() => send('TRAINER_DESTINATION', { value: 'CARD' })}>Card</button>
      {progress && <button type="button" class={destination === 'PROGRESS' ? 'active' : ''} aria-pressed={destination === 'PROGRESS'} onClick={() => send('TRAINER_DESTINATION', { value: 'PROGRESS' })}>Progress</button>}
    </nav>
    {destination === 'PROGRESS' && progress
      ? <TrainerProgressPage progress={progress} onSelectSection={(value: TrainerProgressView['selectedSection']) => send('PROGRESS_SECTION', { value })} />
      : <TrainerCardContent state={state} />}
  </section>;
}
