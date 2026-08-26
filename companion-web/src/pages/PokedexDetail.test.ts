import { describe, expect, it } from 'vitest';
import { baseStatSummary, formatHeight, formatWeight, heightChartMaximum, heightInMeters, opaquePixelBounds, projectedStatRange, wildLevelRange } from './PokedexDetail';

describe('ROM Pokédex measurements', () => {
  it('renders Gen III decimetres and hectograms as metric values', () => {
    expect(formatHeight(17, 'GBA')).toBe('1.7 m');
    expect(formatWeight(905, 'GBA')).toBe('90.5 kg');
  });

  it('renders packed Gen II feet and inches with tenths of pounds', () => {
    expect(formatHeight((7 << 8) | 5, 'GBC')).toBe(`5' 7\"`);
    expect(formatWeight(1990, 'GBC')).toBe('199.0 lb');
  });

  it('normalizes source height units onto a shared metric comparison scale', () => {
    expect(heightInMeters(7, 'GBA')).toBe(.7);
    expect(heightInMeters((7 << 8) | 5, 'GBC')).toBeCloseTo(1.7018, 4);
    expect(heightChartMaximum(.7)).toBe(2.125);
    expect(heightChartMaximum(2.6)).toBe(3.25);
  });

  it('measures the visible sprite instead of its transparent image canvas', () => {
    const rgba = new Uint8ClampedArray(4 * 6 * 4);
    for (let y = 2; y <= 4; y += 1) {
      for (let x = 1; x <= 2; x += 1) rgba[(y * 4 + x) * 4 + 3] = 255;
    }

    expect(opaquePixelBounds(rgba, 4, 6)).toEqual({ left: 1, top: 2, width: 2, height: 3 });
    expect(opaquePixelBounds(new Uint8ClampedArray(4 * 6 * 4), 4, 6)).toBeNull();
  });
});

describe('species stat context', () => {
  it('labels base-stat totals and folds ROM encounter slots into a range', () => {
    expect(baseStatSummary({ HP: 45, ATTACK: 49, DEFENSE: 49, SPEED: 45, 'SP. ATK': 65, 'SP. DEF': 65 })).toBe(318);
    expect(wildLevelRange([{ minimumLevel: 3, maximumLevel: 5 }, { minimumLevel: 7, maximumLevel: 9 }])).toBe('Lv 3–9');
  });

  it('projects the Generation III zero-to-perfect IV impact at level 50', () => {
    expect(projectedStatRange(45, 'HP', 'GBA')).toEqual({ low: 105, typical: 112, high: 120 });
    expect(projectedStatRange(49, 'ATTACK', 'GBA')).toEqual({ low: 54, typical: 61, high: 69 });
  });

  it('projects the Generation I and II DV impact at level 50', () => {
    expect(projectedStatRange(45, 'HP', 'GBC')).toEqual({ low: 105, typical: 112, high: 120 });
    expect(projectedStatRange(49, 'ATTACK', 'GB')).toEqual({ low: 54, typical: 61, high: 69 });
  });
});
