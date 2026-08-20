import type { State } from '../models';
import { Header } from '../components';

export function TrainerCardPage({ state, onBack }: { state: State; onBack: () => void }) {
  const trainer = state.trainer;
  return <section class="screen trainer-screen">
    <Header title="TRAINER CARD" kicker="LIVE · READ ONLY" onBack={onBack} />
    {!trainer ? <div class="empty-state trainer-unavailable"><strong>TRAINER DATA UNAVAILABLE</strong><p>This ROM or session has not published a safe Trainer record.</p></div> :
      <div class="trainer-card-content" data-scroll-region>
        <section class="trainer-identity">
          <div class="trainer-avatar">
            {trainer.avatarUrl
              ? <img src={trainer.avatarUrl} alt={`${trainer.name} avatar`} />
              : <span class="trainer-avatar-fallback" role="img" aria-label="Trainer avatar unavailable"><i /></span>}
          </div>
          <div><p class="eyebrow">TRAINER</p><h1>{trainer.name}</h1><strong>ID {String(trainer.publicTrainerId).padStart(5, '0')}</strong><small>{trainer.gender}</small></div>
        </section>
        <section class="trainer-facts paper-panel">
          <div><small>MONEY</small><strong>₽{trainer.money.toLocaleString('en-US')}</strong></div>
          <div><small>PLAY TIME</small><strong>{trainer.playTimeHours}:{String(trainer.playTimeMinutes).padStart(2, '0')}</strong></div>
          <div><small>SEEN</small><strong>{trainer.dexSeen}</strong></div>
          <div><small>CAUGHT</small><strong>{trainer.dexCaught}</strong></div>
          <div><small>CARD STARS</small><strong>{trainer.stars ?? '—'}</strong></div>
        </section>
        <section class="trainer-badges paper-panel"><p class="eyebrow">BADGES</p><div>
          {trainer.badges.map(badge => <span key={badge.index} class={`trainer-badge ${badge.earned ? 'earned' : ''}`} aria-label={`Badge ${badge.index + 1}${badge.earned ? ' earned' : ' not earned'}`}>
            {badge.imageUrl ? <img src={badge.imageUrl} alt="" /> : <i />}
          </span>)}
        </div></section>
      </div>}
  </section>;
}
