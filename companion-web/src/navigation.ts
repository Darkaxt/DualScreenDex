import type { Screen } from './models';

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
