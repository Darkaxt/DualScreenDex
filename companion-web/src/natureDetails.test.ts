import { describe, expect, it } from 'vitest';
import { NATURE_DETAILS, natureDetailFor } from './natureDetails';

describe('canonical Nature details', () => {
  it('covers the complete 25-entry Gen III nature order without duplicate names', () => {
    expect(NATURE_DETAILS).toHaveLength(25);
    expect(new Set(NATURE_DETAILS.map(nature => nature.name)).size).toBe(25);
  });

  it('maps Adamant to Attack up, Special Attack down and the matching flavors', () => {
    expect(natureDetailFor('Adamant')).toMatchObject({
      id: 3,
      raisedStat: 'ATTACK',
      loweredStat: 'SP. ATK',
      likedFlavor: 'Spicy',
      dislikedFlavor: 'Dry',
      neutral: false,
    });
  });

  it('represents neutral natures and fails closed for unknown names', () => {
    expect(natureDetailFor('Hardy')).toMatchObject({ id: 0, neutral: true, raisedStat: null, loweredStat: null });
    expect(natureDetailFor('Quirky')).toMatchObject({ id: 24, neutral: true });
    expect(natureDetailFor('custom nature')).toBeNull();
  });
});
