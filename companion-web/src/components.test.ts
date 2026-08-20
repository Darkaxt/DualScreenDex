import { describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/preact';
import { h } from 'preact';
import { afterEach } from 'vitest';
import { EyeStatus, speciesIdentityKnowledge, StatusMarks, uniqueTypeIds } from './components';
import type { Catalog, SpeciesState } from './models';

afterEach(cleanup);

describe('type presentation', () => {
  it('shows a monotype only once when the ROM repeats both type slots', () => {
    expect(uniqueTypeIds([1, 1])).toEqual([1]);
    expect(uniqueTypeIds([1, 2])).toEqual([1, 2]);
  });
});

describe('species visibility icon', () => {
  it('renders a conventional accessible eye and eye-off SVG', () => {
    const { rerender } = render(h(EyeStatus, { seen: true }));
    expect(screen.getByLabelText('Seen').tagName.toLowerCase()).toBe('svg');
    rerender(h(EyeStatus, { seen: false }));
    expect(screen.getByLabelText('Not seen').querySelector('line')).not.toBeNull();
  });
});

describe('species identity knowledge', () => {
  const seen: SpeciesState = { seen: true, caught: false, team: false, ballId: null };
  const captured: SpeciesState = { seen: false, caught: true, team: false, ballId: null };

  it('derives unknown, seen, and captured presentation in Organic mode', () => {
    expect(speciesIdentityKnowledge('ORGANIC', undefined)).toBe('unknown');
    expect(speciesIdentityKnowledge('ORGANIC', seen)).toBe('seen');
    expect(speciesIdentityKnowledge('ORGANIC', captured)).toBe('captured');
  });

  it('presents validated identities fully outside Organic mode', () => {
    expect(speciesIdentityKnowledge('DISCOVERED', undefined)).toBe('captured');
    expect(speciesIdentityKnowledge('HIDDEN', seen)).toBe('captured');
  });
});

describe('capture marker', () => {
  it('uses the ROM ball artwork when the caught individual has a parsed ball sprite', () => {
    const catalog = {
      balls: [{ id: 4, name: 'Poké Ball', generic: false, hasSprite: true }]
    } as Catalog;

    render(h(StatusMarks, {
      catalog,
      mode: 'DISCOVERED',
      state: { seen: true, caught: true, team: false, ballId: 4 }
    }));

    const marker = screen.getByAltText('Caught') as HTMLImageElement;
    expect(marker.src).toContain('/api/sprites/balls/4.png');
  });

  it('shows only affirmative capture in Organic mode', () => {
    const catalog = { balls: [] } as unknown as Catalog;
    const { container, rerender } = render(h(StatusMarks, {
      catalog,
      mode: 'ORGANIC',
      state: { seen: true, caught: false, team: false, ballId: null }
    }));

    expect(container.querySelector('.eye-icon')).toBeNull();
    expect(screen.queryByLabelText('Not caught')).toBeNull();
    expect(container.querySelector('.status-marks')).toBeNull();

    rerender(h(StatusMarks, {
      catalog,
      mode: 'ORGANIC',
      state: { seen: true, caught: true, team: false, ballId: null }
    }));
    expect(container.querySelector('.eye-icon')).toBeNull();
    expect(screen.getByLabelText('Caught')).toBeTruthy();
  });

  it('retains explicit seen and uncaught marks outside Organic mode', () => {
    const { container } = render(h(StatusMarks, {
      catalog: { balls: [] } as unknown as Catalog,
      mode: 'DISCOVERED',
      state: { seen: false, caught: false, team: false, ballId: null }
    }));

    expect(container.querySelector('[aria-label="Not seen"]')).toBeTruthy();
    expect(container.querySelector('[aria-label="Not caught"]')).toBeTruthy();
  });
});
