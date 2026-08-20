import type { GameTime } from './models';

export function GameClockIndicator({ clock }: { clock: GameTime }) {
  const phase = clock.phase;
  const progress = clock.phaseProgress;
  const hasNumericTime = typeof clock.hours === 'number' && typeof clock.minutes === 'number';
  const hasOrbit = (phase === 'DAY' || phase === 'NIGHT') && typeof progress === 'number' && Number.isFinite(progress);
  const boundedProgress = hasOrbit ? Math.min(1, Math.max(0, progress)) : 0;
  const arcHeight = 4 + 7 * (1 - 4 * Math.pow(boundedProgress - 0.5, 2));
  const time = hasNumericTime ? `${padClock(clock.hours!)}:${padClock(clock.minutes!)}` : null;
  const phaseLabel = phase ? phase.charAt(0) + phase.slice(1).toLowerCase() : '--:--';

  return <span class="header-game-clock">
    {time
      ? <time class="header-game-time" dateTime={time}>{time}</time>
      : <span class="header-game-time">{phaseLabel}</span>}
    {hasOrbit && <span class="game-time-orbit" aria-hidden="true">
      <svg class="game-time-orbit-track" viewBox="0 0 48 14" preserveAspectRatio="none">
        <path d="M1 13C8 2 40 2 47 13" />
      </svg>
      <span
        class={`game-time-celestial ${phase === 'DAY' ? 'is-day' : 'is-night'}`}
        style={{ left: `${boundedProgress * 100}%`, bottom: `${arcHeight}px` }}
      >
        {phase === 'DAY' ? <SunIcon /> : <MoonIcon />}
      </span>
    </span>}
  </span>;
}

function SunIcon() {
  return <svg viewBox="0 0 16 16" data-semantic-icon="sun">
    <circle cx="8" cy="8" r="3.1" />
    <path d="M8 1v2M8 13v2M1 8h2M13 8h2M3.05 3.05l1.4 1.4M11.55 11.55l1.4 1.4M12.95 3.05l-1.4 1.4M4.45 11.55l-1.4 1.4" />
  </svg>;
}

function MoonIcon() {
  return <svg viewBox="0 0 16 16" data-semantic-icon="moon">
    <path d="M11.9 11.65A6 6 0 0 1 6.1 2.1 6.1 6.1 0 1 0 11.9 11.65Z" />
  </svg>;
}

function padClock(value: number): string {
  return String(value).padStart(2, '0');
}
