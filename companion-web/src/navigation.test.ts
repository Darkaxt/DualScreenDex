import { describe, expect, it } from 'vitest';
import { popRoute, pushRoute, type UiRoute } from './navigation';

describe('client navigation stack', () => {
  it('restores a Party member beneath its Ability page', () => {
    const party: UiRoute = { kind: 'PARTY_MEMBER', slot: 0, catalogHash: 'sha' };
    const routes = pushRoute(pushRoute([], party), { kind: 'ABILITY', id: 65 });

    expect(popRoute(routes).at(-1)).toEqual(party);
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
