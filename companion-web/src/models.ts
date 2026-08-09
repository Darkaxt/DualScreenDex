export type KnowledgeMode = 'DISCOVERED' | 'ORGANIC' | 'HIDDEN';
export type Screen = 'POKEDEX' | 'DETAIL' | 'BATTLE' | 'SETTINGS';

export interface Species {
  id: number;
  dex: number;
  name: string;
  typeIds: number[];
  stats: Record<string, number> | null;
  description: string | null;
  height: number | null;
  weight: number | null;
  learnset: { level: number; moveId: number }[];
  hasSprite: boolean;
}

export interface Move {
  id: number;
  name: string;
  typeId: number | null;
  category: string | null;
  power: number | null;
  accuracy: number | null;
  pp: number | null;
  priority: number | null;
}

export interface TypeInfo {
  id: number;
  name: string;
  foreground: string | null;
  background: string | null;
  border: string | null;
}

export interface Catalog {
  hash: string;
  family: string;
  platform: string;
  species: Species[];
  moves: Move[];
  types: TypeInfo[];
  areas: { id: number; name: string; speciesIds: number[] }[];
  balls: { id: number; name: string; generic: boolean; hasSprite: boolean }[];
  capabilities: Record<string, string>;
}

export interface Settings {
  knowledgeMode: KnowledgeMode;
  attackEnabled: boolean;
  rarityEnabled: boolean;
  movesEnabled: boolean;
  fontScale: number;
  density: 'AUTO' | 'COMFORTABLE' | 'COMPACT';
  highContrast: boolean;
  autoOpenTarget: boolean;
}

export interface SpeciesState {
  seen: boolean;
  caught: boolean;
  team: boolean;
  ballId: number | null;
}

export interface State {
  version: number;
  screen: Screen;
  priorScreen: Screen;
  settingsReturnScreen: Screen;
  selectedSpeciesId: number | null;
  filter: 'ALL' | 'CAUGHT' | 'SEEN' | 'TEAM' | 'AREA';
  selectedAreaId: number | null;
  battleTab: 'ENTRY' | 'ATTACK' | 'RARITY' | 'MOVES';
  settings: Settings;
  speciesState: Record<number, SpeciesState>;
  battle: null | {
    opponents: { speciesId: number; level: number; rarity: string; moves: { moveId: number; encounters: number; lastSeen: number }[] }[];
    targetIndex: number;
    selectedMoveId: number | null;
    effectiveness: string | null;
    effectivenessKnown: boolean;
  };
  catalogReady: boolean;
  catalogName: string | null;
  error: string | null;
}

export interface Bootstrap {
  catalog: Catalog | null;
  state: State;
}
