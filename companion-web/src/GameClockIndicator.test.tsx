import { cleanup, render } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import { Header } from './components';
import { GameClockIndicator } from './GameClockIndicator';

afterEach(cleanup);

describe('game clock indicator', () => {
  it('keeps a valid numeric clock but withholds the orbit without phase evidence', () => {
    const { container } = render(<GameClockIndicator clock={{ hours: 12, minutes: 34 }} />);

    expect(container.querySelector('time')?.textContent).toBe('12:34');
    expect(container.querySelector('.game-time-orbit')).toBeNull();
    expect(container.querySelector('.game-time-celestial')).toBeNull();
  });

  it('renders a phase-only Gen II clock without inventing numeric time', () => {
    const { container } = render(<GameClockIndicator clock={{ hours: null, minutes: null, phase: 'MORNING' }} />);

    expect(container.querySelector('time')).toBeNull();
    expect(container.querySelector('.header-game-time')?.textContent).toBe('Morning');
    expect(container.querySelector('.game-time-orbit')).toBeNull();
  });

  it('renders only the sun at normalized day progress', () => {
    const { container } = render(<GameClockIndicator clock={{ hours: 16, minutes: 48, phase: 'DAY', phaseProgress: 0.75 }} />);

    expect(container.querySelectorAll('.game-time-celestial')).toHaveLength(1);
    expect(container.querySelector('[data-semantic-icon="sun"]')).toBeTruthy();
    expect(container.querySelector('[data-semantic-icon="moon"]')).toBeNull();
    expect(container.querySelector<HTMLElement>('.game-time-celestial')?.style.left).toBe('75%');
    expect(container.querySelector('.game-time-contrast-plate')).toBeTruthy();
  });

  it('renders only the moon at normalized night progress', () => {
    const { container } = render(<GameClockIndicator clock={{ hours: 23, minutes: 15, phase: 'NIGHT', phaseProgress: 0.25 }} />);

    expect(container.querySelectorAll('.game-time-celestial')).toHaveLength(1);
    expect(container.querySelector('[data-semantic-icon="moon"]')).toBeTruthy();
    expect(container.querySelector('[data-semantic-icon="sun"]')).toBeNull();
  });

  it('uses the shared renderer in application headers', () => {
    const { container } = render(<Header title="Pokédex" gameTime={{ hours: 6, minutes: 0, phase: 'DAY', phaseProgress: 0 }} />);

    expect(container.querySelectorAll('.header-game-clock')).toHaveLength(1);
    expect(container.querySelector('[data-semantic-icon="sun"]')).toBeTruthy();
  });
});
