import { describe, expect, it } from 'vitest';
import { baseStatSummary, formatHeight, formatWeight, wildLevelRange } from './PokedexDetail';

describe('ROM Pokédex measurements', () => {
  it('renders Gen III decimetres and hectograms as metric values', () => {
    expect(formatHeight(17, 'GBA')).toBe('1.7 m');
    expect(formatWeight(905, 'GBA')).toBe('90.5 kg');
  });

  it('renders packed Gen II feet and inches with tenths of pounds', () => {
    expect(formatHeight((7 << 8) | 5, 'GBC')).toBe(`5' 7\"`);
    expect(formatWeight(1990, 'GBC')).toBe('199.0 lb');
  });
});

describe('species stat context', () => {
  it('labels base-stat totals and folds ROM encounter slots into a range', () => {
    expect(baseStatSummary({ HP: 45, ATTACK: 49, DEFENSE: 49, SPEED: 45, 'SP. ATK': 65, 'SP. DEF': 65 })).toBe(318);
    expect(wildLevelRange([{ minimumLevel: 3, maximumLevel: 5 }, { minimumLevel: 7, maximumLevel: 9 }])).toBe('Lv 3–9');
  });
});
