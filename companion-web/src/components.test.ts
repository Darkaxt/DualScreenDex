import { describe, expect, it } from 'vitest';
import { uniqueTypeIds } from './components';

describe('type presentation', () => {
  it('shows a monotype only once when the ROM repeats both type slots', () => {
    expect(uniqueTypeIds([1, 1])).toEqual([1]);
    expect(uniqueTypeIds([1, 2])).toEqual([1, 2]);
  });
});
