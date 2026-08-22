import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Header } from './components';

afterEach(cleanup);

describe('application header icons', () => {
  it('uses one detailed Poké Ball for Party instead of two cryptic circles', () => {
    const { container } = render(<Header title="POKÉDEX" onParty={vi.fn()} />);
    const button = screen.getByRole('button', { name: 'Party' });
    const icon = button.querySelector('[data-semantic-icon="party"]')!;

    expect(icon.querySelectorAll('.party-ball-body')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-upper')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-divider')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-button')).toHaveLength(1);
    expect(container.querySelectorAll('.party-ball-body')).toHaveLength(1);
  });
});
