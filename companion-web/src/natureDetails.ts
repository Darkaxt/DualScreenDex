import type { NatureInfo } from './models';

export type NatureStat = keyof NatureInfo['statMultipliers'];
export const NATURE_STATS: NatureStat[] = ['ATTACK', 'DEFENSE', 'SPEED', 'SPECIAL_ATTACK', 'SPECIAL_DEFENSE'];

export function natureDetailFor(natures: NatureInfo[] | undefined, id: number | null | undefined): NatureInfo | null {
  if (id == null) return null;
  return natures?.find(nature => nature.id === id) ?? null;
}

export function natureStatLabel(stat: NatureStat | null): string | null {
  if (stat == null) return null;
  return stat === 'SPECIAL_ATTACK' ? 'SP. ATK' : stat === 'SPECIAL_DEFENSE' ? 'SP. DEF' : stat;
}

export function natureFlavorLabel(flavor: NatureInfo['likedFlavor']): string | null {
  if (flavor == null) return null;
  return flavor[0] + flavor.slice(1).toLocaleLowerCase('en-US');
}
