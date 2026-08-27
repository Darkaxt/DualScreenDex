import { describe, expect, it } from 'vitest';
import { catalogMediaUrl } from './media';

describe('catalogMediaUrl', () => {
  it('adds catalog identity without discarding an existing media variant', () => {
    expect(catalogMediaUrl('/api/maps/local.png?lighting=NIGHT', 'hash value')).toBe(
      '/api/maps/local.png?lighting=NIGHT&catalog=hash%20value',
    );
  });

  it('does not duplicate a server-provided catalog identity', () => {
    expect(catalogMediaUrl('/api/maps/local.png?catalog=active', 'active')).toBe(
      '/api/maps/local.png?catalog=active',
    );
  });
});
