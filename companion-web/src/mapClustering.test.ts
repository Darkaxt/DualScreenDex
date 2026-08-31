import { describe, expect, it } from 'vitest';
import { clusterMapTargets } from './mapClustering';

describe('map target clustering', () => {
  it('forms deterministic connected components without losing source order', () => {
    const points = [
      { key: 'first', x: 100, y: 100 },
      { key: 'second', x: 130, y: 100 },
      { key: 'third', x: 160, y: 100 },
      { key: 'separate', x: 240, y: 100 },
    ];

    const clusters = clusterMapTargets(points, 44);

    expect(clusters).toEqual([
      { key: 'cluster/first/3', x: 100, y: 100, members: points.slice(0, 3) },
      { key: 'separate', x: 240, y: 100, members: [points[3]] },
    ]);
  });

  it('splits members as zoom increases their screen-space separation', () => {
    const compact = [
      { key: 'a', x: 100, y: 100 },
      { key: 'b', x: 130, y: 100 },
      { key: 'c', x: 160, y: 100 },
    ];
    const zoomed = compact.map((point, index) => ({ ...point, x: 100 + index * 50 }));

    expect(clusterMapTargets(compact, 44)).toHaveLength(1);
    expect(clusterMapTargets(zoomed, 44).map(cluster => cluster.key)).toEqual(['a', 'b', 'c']);
  });

  it('rejects duplicate identities and non-finite coordinates', () => {
    expect(() => clusterMapTargets([
      { key: 'duplicate', x: 0, y: 0 },
      { key: 'duplicate', x: 100, y: 100 },
    ], 44)).toThrow(/unique/);
    expect(() => clusterMapTargets([{ key: 'bad', x: Number.NaN, y: 0 }], 44)).toThrow(/finite/);
  });
});
