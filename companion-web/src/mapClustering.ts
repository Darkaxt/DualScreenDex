export interface PositionedMapTarget {
  key: string;
  x: number;
  y: number;
}

export interface MapTargetCluster<T extends PositionedMapTarget> {
  key: string;
  x: number;
  y: number;
  members: T[];
}

export function clusterMapTargets<T extends PositionedMapTarget>(
  targets: readonly T[],
  targetSize: number,
): MapTargetCluster<T>[] {
  if (!Number.isFinite(targetSize) || targetSize <= 0) {
    throw new TypeError('map target size must be finite and positive');
  }

  const keys = new Set<string>();
  for (const target of targets) {
    if (!target.key || keys.has(target.key)) throw new TypeError('map target keys must be non-empty and unique');
    if (!Number.isFinite(target.x) || !Number.isFinite(target.y)) {
      throw new TypeError('map target coordinates must be finite');
    }
    keys.add(target.key);
  }

  const parents = targets.map((_, index) => index);
  const buckets = new Map<string, number[]>();
  const root = (index: number): number => {
    let current = index;
    while (parents[current] !== current) current = parents[current];
    while (parents[index] !== index) {
      const next = parents[index];
      parents[index] = current;
      index = next;
    }
    return current;
  };
  const union = (first: number, second: number) => {
    const firstRoot = root(first);
    const secondRoot = root(second);
    if (firstRoot === secondRoot) return;
    if (firstRoot < secondRoot) parents[secondRoot] = firstRoot;
    else parents[firstRoot] = secondRoot;
  };

  targets.forEach((target, index) => {
    const cellX = Math.floor(target.x / targetSize);
    const cellY = Math.floor(target.y / targetSize);
    for (let deltaY = -1; deltaY <= 1; deltaY += 1) {
      for (let deltaX = -1; deltaX <= 1; deltaX += 1) {
        const neighbors = buckets.get(`${cellX + deltaX}/${cellY + deltaY}`) ?? [];
        for (const neighborIndex of neighbors) {
          const neighbor = targets[neighborIndex];
          if (
            Math.abs(target.x - neighbor.x) < targetSize
            && Math.abs(target.y - neighbor.y) < targetSize
          ) {
            union(index, neighborIndex);
          }
        }
      }
    }
    const bucketKey = `${cellX}/${cellY}`;
    const bucket = buckets.get(bucketKey);
    if (bucket) bucket.push(index);
    else buckets.set(bucketKey, [index]);
  });

  const grouped = new Map<number, T[]>();
  targets.forEach((target, index) => {
    const groupRoot = root(index);
    const members = grouped.get(groupRoot);
    if (members) members.push(target);
    else grouped.set(groupRoot, [target]);
  });

  return [...grouped.values()].map(members => ({
    key: members.length === 1 ? members[0].key : `cluster/${members[0].key}/${members.length}`,
    x: members[0].x,
    y: members[0].y,
    members,
  }));
}
