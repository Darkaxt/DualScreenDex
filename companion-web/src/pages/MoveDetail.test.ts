import { describe, expect, it } from 'vitest';
import { formatMoveMetric } from './MoveDetail';

describe('move metadata', () => {
  it('renders zero battle sentinels as not applicable', () => {
    expect(formatMoveMetric(0)).toBe('—');
    expect(formatMoveMetric(null)).toBe('—');
    expect(formatMoveMetric(95, '%')).toBe('95%');
  });
});
