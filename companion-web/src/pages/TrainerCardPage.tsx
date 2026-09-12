import type { State } from '../models';
import { Header } from '../components';
import { formatUiNumber, msg } from '../i18n';

export function TrainerCardPage({ state, onBack }: { state: State; onBack: () => void }) {
  return <section class="screen trainer-screen trainer-card-screen">
    <Header title={msg('trainerCard')} gameTime={state.gameTime} onBack={onBack} />
    <TrainerCardContent state={state} />
  </section>;
}

export function TrainerCardContent({ state }: { state: State }) {
  const trainer = state.trainer;
  return !trainer ? <div class="empty-state trainer-unavailable"><strong>{msg('trainerCardUnavailable')}</strong><p>{msg('trainerCardAvailableLater')}</p></div> :
      <div class="trainer-card-content" data-scroll-region>
        <article class="trainer-card-shell">
          <header class="trainer-card-strip"><strong>{msg('trainerCard')}</strong><span>{msg('trainerId', trainer.publicTrainerId == null ? '—' : formatUiNumber(trainer.publicTrainerId, { minimumIntegerDigits: 5, useGrouping: false }))}</span></header>
          <div class="trainer-card-body">
            <div class="trainer-card-copy">
              <div class="trainer-card-name"><small>{msg('name')}</small><h2>{trainer.name}</h2><span>{trainerGenderLabel(trainer.gender)}</span></div>
              <dl class="trainer-card-facts">
                <div><dt>{msg('money')}</dt><dd>{trainer.money == null ? '—' : `₽${formatUiNumber(trainer.money)}`}</dd></div>
                <div><dt>{msg('playTime')}</dt><dd>{trainer.playTimeHours == null || trainer.playTimeMinutes == null ? '—' : `${formatUiNumber(trainer.playTimeHours, { useGrouping: false })}:${formatUiNumber(trainer.playTimeMinutes, { minimumIntegerDigits: 2, useGrouping: false })}`}</dd></div>
                <div><dt>{msg('pokedexSeen')}</dt><dd>{trainer.dexSeen == null ? '—' : formatUiNumber(trainer.dexSeen)}</dd></div>
                <div><dt>{msg('pokedexCaught')}</dt><dd>{trainer.dexCaught == null ? '—' : formatUiNumber(trainer.dexCaught)}</dd></div>
                <div><dt>{msg('cardStars')}</dt><dd>{trainer.stars == null ? '—' : formatUiNumber(trainer.stars)}</dd></div>
              </dl>
            </div>
            <div class="trainer-avatar">
              {trainer.avatarUrl
                ? <img src={trainer.avatarUrl} alt={msg('trainerAvatar', trainer.name)} />
                : <span class="trainer-avatar-fallback" role="img" aria-label={msg('trainerAvatarUnavailable')}><i /></span>}
            </div>
          </div>
          <section class="trainer-card-badges"><p class="eyebrow">{msg('badges')}</p><div>
            {trainer.badges.map(badge => {
              const index = formatUiNumber(badge.index + 1);
              const label = badge.earned == null ? msg('badgeStatusUnknown', index) : badge.earned ? msg('badgeEarned', index) : msg('badgeNotEarned', index);
              return <span key={badge.index} class={`trainer-badge ${badge.earned === true ? 'earned' : ''}`} aria-label={label}>
                {badge.imageUrl ? <img src={badge.imageUrl} alt="" /> : <i />}
              </span>;
            })}
          </div></section>
        </article>
      </div>;
}

function trainerGenderLabel(gender: string | null): string {
  const normalized = gender?.trim().toUpperCase();
  if (normalized === 'F' || normalized === 'FEMALE') return msg('female');
  if (normalized === 'M' || normalized === 'MALE') return msg('male');
  return gender ?? '—';
}
