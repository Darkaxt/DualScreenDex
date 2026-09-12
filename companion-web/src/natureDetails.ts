import { msg } from './i18n';
import type { NatureInfo, StatName } from './models';

export type NatureStat = keyof NatureInfo['statMultipliers'];
export const NATURE_STATS: NatureStat[] = ['ATTACK', 'DEFENSE', 'SPEED', 'SPECIAL_ATTACK', 'SPECIAL_DEFENSE'];

export function natureDetailFor(natures: NatureInfo[] | undefined, id: number | null | undefined): NatureInfo | null {
  if (id == null) return null;
  return natures?.find(nature => nature.id === id) ?? null;
}

export function statLabel(stat: StatName): string {
  const labels: Record<StatName, string> = {
    HP: msg('statHp'),
    ATTACK: msg('statAttack'),
    DEFENSE: msg('statDefense'),
    SPEED: msg('statSpeed'),
    SPECIAL_ATTACK: msg('statSpecialAttack'),
    SPECIAL_DEFENSE: msg('statSpecialDefense'),
  };
  return labels[stat];
}

export function natureStatLabel(stat: NatureStat | null): string | null {
  return stat == null ? null : statLabel(stat);
}

export function natureFlavorLabel(flavor: NatureInfo['likedFlavor']): string | null {
  if (flavor == null) return null;
  const labels: Record<NonNullable<NatureInfo['likedFlavor']>, string> = {
    SPICY: msg('flavorSpicy'),
    DRY: msg('flavorDry'),
    SWEET: msg('flavorSweet'),
    BITTER: msg('flavorBitter'),
    SOUR: msg('flavorSour'),
  };
  return labels[flavor];
}
