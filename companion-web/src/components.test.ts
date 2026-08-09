import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/preact';
import { h } from 'preact';
import { EyeStatus, uniqueTypeIds } from './components';

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
