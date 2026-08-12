import { describe, expect, it } from 'vitest';
import { formatMoveMetric, speciesKnowsMove } from './MoveDetail';

describe('move metadata', () => {
  it('renders zero battle sentinels as not applicable', () => {
    expect(formatMoveMetric(0)).toBe('—');
    expect(formatMoveMetric(null)).toBe('—');
    expect(formatMoveMetric(95, '%')).toBe('95%');
  });

  it('uses only the active level-up table while keeping other acquisition methods independent', () => {
    const species = {
      normalizedLearnsets: {
        original: [{ moveId: 10 }],
        modern: [{ moveId: 20 }],
      },
      moveAcquisitions: [{ moveId: 30 }],
    };

    expect(speciesKnowsMove(species, null, 10)).toBe(false);
    expect(speciesKnowsMove(species, 'original', 10)).toBe(true);
    expect(speciesKnowsMove(species, 'modern', 10)).toBe(false);
    expect(speciesKnowsMove(species, null, 30)).toBe(true);
  });
});
