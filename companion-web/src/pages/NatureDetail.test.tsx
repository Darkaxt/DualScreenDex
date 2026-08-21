import { cleanup, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it } from 'vitest';
import { NatureDetail } from './NatureDetail';
import type { NatureInfo } from '../models';

afterEach(cleanup);

describe('Nature detail', () => {
  it('explains a stat-changing nature without diagnostic copy', () => {
    render(<NatureDetail nature={changing} onBack={() => undefined} />);

    expect(screen.getByText('RESOLUTE')).toBeTruthy();
    expect(screen.getByText('ATTACK ×1.12')).toBeTruthy();
    expect(screen.getByText('SP. ATK ×0.88')).toBeTruthy();
    expect(screen.getByText('Spicy flavors')).toBeTruthy();
    expect(screen.getByText('Dry flavors')).toBeTruthy();
    expect(screen.queryByText(/ROM|parser|table offset|calculation|canonical|Gen III/i)).toBeNull();
  });

  it('explains a neutral nature without inventing a boost', () => {
    render(<NatureDetail nature={neutral} onBack={() => undefined} />);

    expect(screen.getByText('No stat changes')).toBeTruthy();
    expect(screen.getByText('No flavor preference')).toBeTruthy();
    expect(screen.queryByText(/×1\.1|×0\.9/)).toBeNull();
  });
});

const changing: NatureInfo = {
  id: 7, name: 'Resolute',
  statMultipliers: { ATTACK: 112, DEFENSE: 100, SPEED: 100, SPECIAL_ATTACK: 88, SPECIAL_DEFENSE: 100 },
  raisedStat: 'ATTACK', loweredStat: 'SPECIAL_ATTACK', positivePercent: 112, negativePercent: 88,
  likedFlavor: 'SPICY', dislikedFlavor: 'DRY',
};
const neutral: NatureInfo = {
  id: 8, name: 'Even',
  statMultipliers: { ATTACK: 100, DEFENSE: 100, SPEED: 100, SPECIAL_ATTACK: 100, SPECIAL_DEFENSE: 100 },
  raisedStat: null, loweredStat: null, positivePercent: 112, negativePercent: 88,
  likedFlavor: null, dislikedFlavor: null,
};
