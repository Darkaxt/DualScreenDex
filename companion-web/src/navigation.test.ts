import { describe, expect, it } from 'vitest';
import { decodeRouteHash, encodeRouteHash, popRoute, pushRoute, type UiRoute } from './navigation';
import type { Catalog } from './models';

const catalog = {
  hash: 'sha',
  species: [{ id: 1, abilities: [{ id: 65 }] }],
  moves: [{ id: 2 }],
  natures: [{ id: 3 }],
} as Catalog;

function routeHash(payload: unknown) {
  return `#dualdex=${encodeURIComponent(JSON.stringify(payload))}`;
}

describe('client navigation stack', () => {
  it('round trips a catalog-bound nested route stack', () => {
    const routes: UiRoute[] = [
      { kind: 'PARTY_MEMBER', slot: 0, catalogHash: 'sha' },
      { kind: 'ABILITY', id: 65 },
    ];

    expect(decodeRouteHash(encodeRouteHash(routes, catalog.hash), catalog)).toEqual(routes);
  });

  it('rejects malformed, stale, and unavailable routes', () => {
    const stale = encodeRouteHash([{ kind: 'SPECIES', id: 1 }], 'old-sha');
    const unavailable = encodeRouteHash([{ kind: 'SPECIES', id: 999 }], catalog.hash);

    expect(decodeRouteHash('#dualdex=%7Bbroken', catalog)).toEqual([]);
    expect(decodeRouteHash(stale, catalog)).toEqual([]);
    expect(decodeRouteHash(unavailable, catalog)).toEqual([]);
  });

  it('keeps a maximum valid stack within the transferred marker bound', () => {
    const routes = Array.from({ length: 16 }, (_, index) => ({
      kind: 'SPECIMEN' as const,
      speciesId: 1,
      specimenKey: `${index}-${'x'.repeat(124)}`,
      catalogHash: catalog.hash,
    }));
    const hash = encodeRouteHash(routes, catalog.hash);

    expect(hash.length).toBeLessThanOrEqual(8192);
    expect(decodeRouteHash(hash, catalog)).toEqual(routes);
  });

  it('round trips fixed-size Gen I and II fallback specimen keys at the maximum history stack', () => {
    const fallbackKey = `fallback:${'a'.repeat(64)}`;
    const routes = Array.from({ length: 16 }, () => ({
      kind: 'SPECIMEN' as const,
      speciesId: 1,
      specimenKey: fallbackKey,
      catalogHash: catalog.hash,
    }));
    const hash = encodeRouteHash(routes, catalog.hash);

    expect(fallbackKey).toHaveLength(73);
    expect(hash.length).toBeLessThanOrEqual(8192);
    expect(decodeRouteHash(hash, catalog)).toEqual(routes);
  });

  it('discards a restored mapper route unless the bootstrap declares mapper support', () => {
    const mapper = encodeRouteHash([{ kind: 'MAPPER' }], catalog.hash);

    expect(decodeRouteHash(mapper, catalog)).toEqual([]);
    expect(decodeRouteHash(mapper, catalog, { mapperAvailable: true })).toEqual([{ kind: 'MAPPER' }]);
  });

  it('discards a restored map route unless the bootstrap declares world-map support', () => {
    const map: UiRoute[] = [{ kind: 'MAP', originScreen: 'POKEDEX' }];
    const hash = encodeRouteHash(map, catalog.hash);

    expect(decodeRouteHash(hash, catalog)).toEqual([]);
    expect(decodeRouteHash(hash, catalog, { worldMapsAvailable: true })).toEqual(map);
  });

  it('round trips only the catalog-bound Move List Settings target', () => {
    const settingsRoute: Extract<UiRoute, { kind: 'SETTINGS' }> = {
      kind: 'SETTINGS',
      category: 'INFORMATION',
      control: 'MOVE_LIST',
      catalogHash: catalog.hash,
    };
    const settings: UiRoute[] = [settingsRoute];

    expect(decodeRouteHash(encodeRouteHash(settings, catalog.hash), catalog)).toEqual(settings);
    expect(decodeRouteHash(encodeRouteHash([{
      ...settingsRoute,
      catalogHash: 'old-sha',
    }], catalog.hash), catalog)).toEqual([]);
    expect(decodeRouteHash(routeHash({
      version: 1,
      catalogHash: catalog.hash,
      routes: [{ ...settingsRoute, category: 'DISPLAY' }],
    }), catalog)).toEqual([]);
  });

  it('rejects invalid entity references and party slots', () => {
    const invalidRoutes: UiRoute[][] = [
      [{ kind: 'MOVE', id: 999 }],
      [{ kind: 'ABILITY', id: 999 }],
      [{ kind: 'NATURE', id: 999 }],
      [{ kind: 'PARTY_MEMBER', slot: 6, catalogHash: catalog.hash }],
      [{ kind: 'SPECIMENS', speciesId: 999, catalogHash: catalog.hash }],
      [{ kind: 'SPECIMEN', speciesId: 1, specimenKey: '', catalogHash: catalog.hash }],
    ];

    for (const routes of invalidRoutes) {
      expect(decodeRouteHash(encodeRouteHash(routes, catalog.hash), catalog)).toEqual([]);
    }
  });

  it('rejects unknown route kinds, unsupported versions, and oversized stacks', () => {
    const base = { version: 1, catalogHash: catalog.hash };
    const tooManyRoutes = Array.from({ length: 17 }, () => ({ kind: 'SPECIES', id: 1 }));

    expect(decodeRouteHash(routeHash({ ...base, routes: [{ kind: 'UNKNOWN' }] }), catalog)).toEqual([]);
    expect(decodeRouteHash(routeHash({ ...base, version: 2, routes: [] }), catalog)).toEqual([]);
    expect(decodeRouteHash(routeHash({ ...base, routes: tooManyRoutes }), catalog)).toEqual([]);
  });

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

  it('restores the same specimen list beneath an individual and its linked details', () => {
    const list: UiRoute = { kind: 'SPECIMENS', speciesId: 25, catalogHash: 'sha' };
    const individual: UiRoute = { kind: 'SPECIMEN', speciesId: 25, specimenKey: 'individual:1', catalogHash: 'sha' };
    const routes = [list, individual, { kind: 'NATURE', id: 3 } satisfies UiRoute];

    const individualRestored = popRoute(routes);
    expect(individualRestored.at(-1)).toEqual(individual);
    expect(popRoute(individualRestored).at(-1)).toEqual(list);
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
