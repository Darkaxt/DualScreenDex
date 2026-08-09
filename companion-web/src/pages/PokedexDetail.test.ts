import { describe, expect, it } from 'vitest';
import { formatHeight, formatWeight } from './PokedexDetail';

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
