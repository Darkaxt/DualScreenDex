import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/preact';
import { h } from 'preact';
import { EyeStatus, StatusMarks, uniqueTypeIds } from './components';
import type { Catalog } from './models';

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

describe('capture marker', () => {
  it('uses the ROM ball artwork when the caught individual has a parsed ball sprite', () => {
    const catalog = {
      balls: [{ id: 4, name: 'Poké Ball', generic: false, hasSprite: true }]
    } as Catalog;

    render(h(StatusMarks, {
      catalog,
      state: { seen: true, caught: true, team: false, ballId: 4 }
    }));

    const marker = screen.getByAltText('Caught') as HTMLImageElement;
    expect(marker.src).toContain('/api/sprites/balls/4.png');
  });
});
