import { describe, expect, it } from 'vitest';
import { popRoute, pushRoute, type UiRoute } from './navigation';

describe('client navigation stack', () => {
  it('restores a Party member beneath its Ability page', () => {
    const party: UiRoute = { kind: 'PARTY_MEMBER', slot: 0, catalogHash: 'sha' };
    const routes = pushRoute(pushRoute([], party), { kind: 'ABILITY', id: 65 });

    expect(popRoute(routes).at(-1)).toEqual(party);
  });

  it('unwinds Analysis, member, and linked details one destination at a time', () => {
    const analysis: UiRoute = { kind: 'PARTY_ANALYSIS', catalogHash: 'sha' };
    const member: UiRoute = { kind: 'PARTY_MEMBER', slot: 0, catalogHash: 'sha' };
    const routes = [analysis, member, { kind: 'ABILITY', id: 65 } satisfies UiRoute];

    const memberRestored = popRoute(routes);
    expect(memberRestored.at(-1)).toEqual(member);
    expect(popRoute(memberRestored).at(-1)).toEqual(analysis);
    expect(pushRoute([analysis], analysis)).toEqual([analysis]);

    const species = pushRoute(memberRestored, { kind: 'SPECIES', id: 1 });
    expect(popRoute(species).at(-1)).toEqual(member);
  });

  it('does not add adjacent duplicate routes and remains bounded', () => {
    const map: UiRoute = { kind: 'MAP', originScreen: 'POKEDEX' };
    expect(pushRoute(pushRoute([], map), map)).toEqual([map]);

    const routes = Array.from({ length: 24 }, (_, id) => ({ kind: 'MOVE', id }) as const)
      .reduce<UiRoute[]>((stack, route) => pushRoute(stack, route), []);
    expect(routes).toHaveLength(16);
    expect(routes[0]).toEqual({ kind: 'MOVE', id: 8 });
  });
});
