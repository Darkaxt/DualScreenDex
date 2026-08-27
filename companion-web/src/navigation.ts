import type { Catalog, Screen } from './models';

export type UiRoute =
  | { kind: 'MAP'; originScreen: Screen }
  | { kind: 'MAPPER' }
  | { kind: 'CAPABILITIES' }
  | { kind: 'PARTY_ANALYSIS'; catalogHash: string }
  | { kind: 'PARTY_MEMBER'; slot: number; catalogHash: string }
  | { kind: 'SPECIMENS'; speciesId: number; catalogHash: string }
  | { kind: 'SPECIMEN'; speciesId: number; specimenKey: string; catalogHash: string }
  | { kind: 'SPECIES'; id: number }
  | { kind: 'MOVE'; id: number }
  | { kind: 'ABILITY'; id: number }
  | { kind: 'NATURE'; id: number };

const MAX_CLIENT_ROUTES = 16;
const ROUTE_HASH_PREFIX = '#dualdex=';
const ROUTE_HASH_VERSION = 1;
const MAX_ROUTE_HASH_LENGTH = 8192;
const MAX_SPECIMEN_KEY_LENGTH = 128;
const SCREENS = new Set<Screen>(['POKEDEX', 'DETAIL', 'BATTLE', 'TRAINER', 'PARTY', 'SETTINGS', 'SETUP']);

interface RouteHashPayload {
  version: number;
  catalogHash: string;
  routes: UiRoute[];
}

export function encodeRouteHash(routes: UiRoute[], catalogHash: string): string {
  const payload: RouteHashPayload = {
    version: ROUTE_HASH_VERSION,
    catalogHash,
    routes,
  };
  return `${ROUTE_HASH_PREFIX}${encodeURIComponent(JSON.stringify(payload))}`;
}

export function decodeRouteHash(hash: string, catalog: Catalog): UiRoute[] {
  if (!hash.startsWith(ROUTE_HASH_PREFIX) || hash.length > MAX_ROUTE_HASH_LENGTH) return [];
  try {
    const payload: unknown = JSON.parse(decodeURIComponent(hash.slice(ROUTE_HASH_PREFIX.length)));
    if (!isRecord(payload) ||
      payload.version !== ROUTE_HASH_VERSION ||
      payload.catalogHash !== catalog.hash ||
      !Array.isArray(payload.routes) ||
      payload.routes.length > MAX_CLIENT_ROUTES
    ) return [];

    const routes = payload.routes.map(route => validRoute(route, catalog));
    return routes.every((route): route is UiRoute => route != null) ? routes : [];
  } catch {
    return [];
  }
}

function validRoute(value: unknown, catalog: Catalog): UiRoute | null {
  if (!isRecord(value) || typeof value.kind !== 'string') return null;
  switch (value.kind) {
    case 'MAP':
      return typeof value.originScreen === 'string' && SCREENS.has(value.originScreen as Screen)
        ? { kind: 'MAP', originScreen: value.originScreen as Screen }
        : null;
    case 'MAPPER':
      return { kind: 'MAPPER' };
    case 'CAPABILITIES':
      return { kind: 'CAPABILITIES' };
    case 'PARTY_ANALYSIS':
      return validCatalogHash(value.catalogHash, catalog)
        ? { kind: 'PARTY_ANALYSIS', catalogHash: value.catalogHash }
        : null;
    case 'PARTY_MEMBER':
      return validCatalogHash(value.catalogHash, catalog) && validInteger(value.slot) && value.slot >= 0 && value.slot <= 5
        ? { kind: 'PARTY_MEMBER', slot: value.slot, catalogHash: value.catalogHash }
        : null;
    case 'SPECIMENS':
      return validCatalogHash(value.catalogHash, catalog) && hasSpecies(catalog, value.speciesId)
        ? { kind: 'SPECIMENS', speciesId: value.speciesId as number, catalogHash: value.catalogHash }
        : null;
    case 'SPECIMEN':
      return validCatalogHash(value.catalogHash, catalog) &&
        hasSpecies(catalog, value.speciesId) &&
        typeof value.specimenKey === 'string' &&
        value.specimenKey.length > 0 &&
        value.specimenKey.length <= MAX_SPECIMEN_KEY_LENGTH
        ? {
            kind: 'SPECIMEN',
            speciesId: value.speciesId as number,
            specimenKey: value.specimenKey,
            catalogHash: value.catalogHash,
          }
        : null;
    case 'SPECIES':
      return hasSpecies(catalog, value.id) ? { kind: 'SPECIES', id: value.id as number } : null;
    case 'MOVE':
      return validInteger(value.id) && catalog.moves.some(move => move.id === value.id)
        ? { kind: 'MOVE', id: value.id }
        : null;
    case 'ABILITY':
      return validInteger(value.id) && catalog.species.some(species => species.abilities.some(ability => ability.id === value.id))
        ? { kind: 'ABILITY', id: value.id }
        : null;
    case 'NATURE':
      return validInteger(value.id) && catalog.natures?.some(nature => nature.id === value.id) === true
        ? { kind: 'NATURE', id: value.id }
        : null;
    default:
      return null;
  }
}

function validCatalogHash(value: unknown, catalog: Catalog): value is string {
  return typeof value === 'string' && value === catalog.hash;
}

function hasSpecies(catalog: Catalog, value: unknown): boolean {
  return validInteger(value) && catalog.species.some(species => species.id === value);
}

function validInteger(value: unknown): value is number {
  return Number.isSafeInteger(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value != null && !Array.isArray(value);
}

export function pushRoute(routes: UiRoute[], route: UiRoute): UiRoute[] {
  if (sameRoute(routes.at(-1), route)) return routes;
  return [...routes, route].slice(-MAX_CLIENT_ROUTES);
}

export function popRoute(routes: UiRoute[]): UiRoute[] {
  return routes.length === 0 ? routes : routes.slice(0, -1);
}

export function sameRoute(left: UiRoute | undefined, right: UiRoute | undefined): boolean {
  if (!left || !right || left.kind !== right.kind) return false;
  switch (left.kind) {
    case 'MAP': return right.kind === 'MAP' && left.originScreen === right.originScreen;
    case 'PARTY_ANALYSIS': return right.kind === 'PARTY_ANALYSIS' && left.catalogHash === right.catalogHash;
    case 'PARTY_MEMBER': return right.kind === 'PARTY_MEMBER' && left.slot === right.slot && left.catalogHash === right.catalogHash;
    case 'SPECIMENS': return right.kind === 'SPECIMENS' && left.speciesId === right.speciesId && left.catalogHash === right.catalogHash;
    case 'SPECIMEN': return right.kind === 'SPECIMEN' && left.speciesId === right.speciesId && left.specimenKey === right.specimenKey && left.catalogHash === right.catalogHash;
    case 'MOVE':
    case 'ABILITY':
    case 'NATURE':
    case 'SPECIES': return right.kind === left.kind && left.id === right.id;
    case 'MAPPER':
    case 'CAPABILITIES': return true;
  }
}
