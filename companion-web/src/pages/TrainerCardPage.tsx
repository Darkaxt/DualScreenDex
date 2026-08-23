import type { State } from '../models';
import { Header } from '../components';

export function TrainerCardPage({ state, onBack }: { state: State; onBack: () => void }) {
  const trainer = state.trainer;
  return <section class="screen trainer-screen">
    <Header title="TRAINER CARD" onBack={onBack} />
    {!trainer ? <div class="empty-state trainer-unavailable"><strong>TRAINER CARD UNAVAILABLE</strong><p>Your Trainer Card will appear here when it can be read from the game.</p></div> :
      <div class="trainer-card-content" data-scroll-region>
        <article class="trainer-card-shell">
          <header class="trainer-card-strip"><strong>TRAINER CARD</strong><span>ID {trainer.publicTrainerId == null ? '—' : String(trainer.publicTrainerId).padStart(5, '0')}</span></header>
          <div class="trainer-card-body">
            <div class="trainer-card-copy">
              <div class="trainer-card-name"><small>NAME</small><h1>{trainer.name}</h1><span>{trainer.gender}</span></div>
              <dl class="trainer-card-facts">
                <div><dt>MONEY</dt><dd>{trainer.money == null ? '—' : `₽${trainer.money.toLocaleString('en-US')}`}</dd></div>
                <div><dt>PLAY TIME</dt><dd>{trainer.playTimeHours == null || trainer.playTimeMinutes == null ? '—' : `${trainer.playTimeHours}:${String(trainer.playTimeMinutes).padStart(2, '0')}`}</dd></div>
                <div><dt>POKÉDEX SEEN</dt><dd>{trainer.dexSeen ?? '—'}</dd></div>
                <div><dt>POKÉDEX CAUGHT</dt><dd>{trainer.dexCaught ?? '—'}</dd></div>
                <div><dt>CARD STARS</dt><dd>{trainer.stars ?? '—'}</dd></div>
              </dl>
            </div>
            <div class="trainer-avatar">
              {trainer.avatarUrl
                ? <img src={trainer.avatarUrl} alt={`${trainer.name} avatar`} />
                : <span class="trainer-avatar-fallback" role="img" aria-label="Trainer avatar unavailable"><i /></span>}
            </div>
          </div>
          <section class="trainer-card-badges"><p class="eyebrow">BADGES</p><div>
            {trainer.badges.map(badge => <span key={badge.index} class={`trainer-badge ${badge.earned === true ? 'earned' : ''}`} aria-label={`Badge ${badge.index + 1}${badge.earned == null ? ' status unknown' : badge.earned ? ' earned' : ' not earned'}`}>
              {badge.imageUrl ? <img src={badge.imageUrl} alt="" /> : <i />}
            </span>)}
          </div></section>
        </article>
      </div>}
  </section>;
}
