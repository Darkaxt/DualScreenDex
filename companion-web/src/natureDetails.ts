export type NatureStat = 'ATTACK' | 'DEFENSE' | 'SPEED' | 'SP. ATK' | 'SP. DEF';

export interface NatureDetailProfile {
  id: number;
  name: string;
  raisedStat: NatureStat | null;
  loweredStat: NatureStat | null;
  likedFlavor: string | null;
  dislikedFlavor: string | null;
  neutral: boolean;
}

export const NATURE_STATS: NatureStat[] = ['ATTACK', 'DEFENSE', 'SPEED', 'SP. ATK', 'SP. DEF'];
const FLAVORS = ['Spicy', 'Sour', 'Sweet', 'Dry', 'Bitter'];
const NAMES = [
  'Hardy', 'Lonely', 'Brave', 'Adamant', 'Naughty',
  'Bold', 'Docile', 'Relaxed', 'Impish', 'Lax',
  'Timid', 'Hasty', 'Serious', 'Jolly', 'Naive',
  'Modest', 'Mild', 'Quiet', 'Bashful', 'Rash',
  'Calm', 'Gentle', 'Sassy', 'Careful', 'Quirky',
] as const;

export const NATURE_DETAILS: NatureDetailProfile[] = NAMES.map((name, id) => {
  const raisedIndex = Math.floor(id / NATURE_STATS.length);
  const loweredIndex = id % NATURE_STATS.length;
  const neutral = raisedIndex === loweredIndex;
  return {
    id,
    name,
    raisedStat: neutral ? null : NATURE_STATS[raisedIndex],
    loweredStat: neutral ? null : NATURE_STATS[loweredIndex],
    likedFlavor: neutral ? null : FLAVORS[raisedIndex],
    dislikedFlavor: neutral ? null : FLAVORS[loweredIndex],
    neutral,
  };
});

export function natureDetailFor(name: string | null | undefined): NatureDetailProfile | null {
  if (!name) return null;
  const normalized = name.trim().toLocaleLowerCase('en-US');
  return NATURE_DETAILS.find(nature => nature.name.toLocaleLowerCase('en-US') === normalized) ?? null;
}
