import { describe, expect, it } from 'vitest';
import { natureDetailFor, natureFlavorLabel, natureStatLabel } from './natureDetails';
import type { NatureInfo } from './models';

describe('ROM-derived Nature details', () => {
  it('selects a noncanonical Nature by its ROM-native ID', () => {
    expect(natureDetailFor([nature], 7)).toEqual(nature);
  });

  it('fails closed for unknown IDs and presents typed ROM fields', () => {
    expect(natureDetailFor([nature], 3)).toBeNull();
    expect(natureDetailFor(undefined, 7)).toBeNull();
    expect(natureStatLabel('SPECIAL_ATTACK')).toBe('SP. ATK');
    expect(natureFlavorLabel('SPICY')).toBe('Spicy');
  });
});

const nature: NatureInfo = {
  id: 7,
  name: 'Resolute',
  statMultipliers: { ATTACK: 112, DEFENSE: 100, SPEED: 100, SPECIAL_ATTACK: 88, SPECIAL_DEFENSE: 100 },
  raisedStat: 'ATTACK', loweredStat: 'SPECIAL_ATTACK', positivePercent: 112, negativePercent: 88,
  likedFlavor: 'SPICY', dislikedFlavor: 'DRY',
};
