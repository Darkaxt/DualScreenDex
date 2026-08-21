import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import { NatureDetail } from './NatureDetail';

afterEach(cleanup);

describe('Nature detail', () => {
  it('explains a stat-changing nature without diagnostic copy', () => {
    render(<NatureDetail natureName="Adamant" onBack={() => undefined} />);

    expect(screen.getByText('ADAMANT')).toBeTruthy();
    expect(screen.getByText('ATTACK ×1.1')).toBeTruthy();
    expect(screen.getByText('SP. ATK ×0.9')).toBeTruthy();
    expect(screen.getByText('Spicy flavors')).toBeTruthy();
    expect(screen.getByText('Dry flavors')).toBeTruthy();
    expect(screen.queryByText(/ROM|parser|table offset|calculation|canonical|Gen III/i)).toBeNull();
  });

  it('explains a neutral nature without inventing a boost', () => {
    render(<NatureDetail natureName="Hardy" onBack={() => undefined} />);

    expect(screen.getByText('No stat changes')).toBeTruthy();
    expect(screen.getByText('No flavor preference')).toBeTruthy();
    expect(screen.queryByText(/×1\.1|×0\.9/)).toBeNull();
  });
});
