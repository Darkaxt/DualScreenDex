export type KnowledgeMode = 'DISCOVERED' | 'ORGANIC' | 'HIDDEN';
export type Screen = 'POKEDEX' | 'DETAIL' | 'BATTLE' | 'SETTINGS' | 'SETUP';

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
  learnsets: Record<string, { level: number; moveId: number }[]>;
  normalizedLearnsets: Record<string, { moveId: number; initial: boolean; levels: number[]; label: string }[]>;
  moveAcquisitions: { moveId: number; method: 'EGG' | 'MACHINE' | 'TUTOR'; sourceId: number | null }[];
  abilities: {
    id: number;
    name: string;
    description: string | null;
    mechanics: { kind: string; label: string; value: string; numerator: number; denominator: number }[];
  }[];
  evolutions: { targetSpeciesId: number; targetName: string; methodId: number; parameter: number; condition: string }[];
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
  effectId: number | null;
  description: string | null;
}

export interface TypeInfo {
  id: number;
  name: string;
  foreground: string | null;
  background: string | null;
  border: string | null;
}

export type EncounterWindow = 'ANY' | 'MORNING' | 'DAY' | 'NIGHT';

export interface Catalog {
  hash: string;
  crc32: string;
  family: string;
  platform: string;
  rulesets: { id: string; label: string; sourceOffset: number; confidence: number; primary: boolean }[];
  species: Species[];
  moves: Move[];
  types: TypeInfo[];
  areas: { id: number; name: string; methodId: number; speciesIds: number[]; windows: EncounterWindow[]; slots: { speciesId: number; minimumLevel: number; maximumLevel: number; weight: number | null }[] }[];
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
  ruleset: string;
  displayMode?: 'DOCKED' | 'OVERLAY';
  theme?: 'GAME' | 'DARK' | 'LIGHT';
  displayTarget?: 'AUTO' | 'HANDHELD' | 'EXTERNAL';
  overlayScale?: number;
  battlePollingIntervalMs?: number;
}

export interface SpeciesState {
  seen: boolean;
  caught: boolean;
  team: boolean;
  ballId: number | null;
  preferredLevel?: number | null;
  innateTier?: string | null;
}

export interface SaveRamState {
  status: 'UNAVAILABLE' | 'LOCATING' | 'MATCHED' | 'AMBIGUOUS' | 'STALE' | string;
  sourceName: string | null;
  sourceLastModifiedEpochMs: number | null;
  refreshedAtEpochMs: number | null;
  autosaveStatus: 'VERIFIED' | 'DISABLED' | 'UNVERIFIED' | string;
  capabilities: Record<string, string>;
  candidates: { id: string; path: string; lastModifiedEpochMs: number }[];
  message: string | null;
}

export interface RetroArchState {
  storageGrant: string;
  configGrant: string;
  romGrant: string;
  configState: string;
  restartRequired: boolean;
  connection: string;
  systemId: string | null;
  gameBasename: string | null;
  contentCrc32: string | null;
  resolution: string;
  activeSource: string | null;
  savefileDirectory: string | null;
  indexedRoms: number;
  message: string | null;
}

export interface Rarity {
  relativeTier: 'WEAK' | 'ORDINARY' | 'COMPETENT' | 'STRONG' | 'MAJOR' | null;
  innateTier: 'FODDER' | 'STANDARD' | 'TRAINED' | 'VETERAN' | 'ELITE' | 'ACE' | null;
  baseStars: number | null;
  areaAdjustment: number | null;
  stars: number | null;
  areaOutcome?: 'AREA_UNAVAILABLE' | 'AREA_NOT_IN_CATALOG' | 'SPECIES_LEVEL_NOT_IN_AREA' | 'INVALID_WEIGHTS' | 'AMBIGUOUS_TIER' | 'APPLIED' | 'APPLIED_UNIQUE_ENCOUNTER';
  currentAreaBaseId?: number | null;
  matchingAreaCount?: number;
  candidateAreaCount?: number;
}

export interface State {
  version: number;
  screen: Screen;
  priorScreen: Screen;
  settingsReturnScreen: Screen;
  selectedSpeciesId: number | null;
  filter: 'ALL' | 'CAUGHT' | 'SEEN' | 'TEAM' | 'AREA';
  selectedAreaId: number | null;
  currentAreaIds?: number[];
  currentAreaBaseId?: number | null;
  battleTab: 'ENTRY' | 'ATTACK' | 'RARITY' | 'MOVES';
  settings: Settings;
  speciesState: Record<number, SpeciesState>;
  observedMoves: Record<number, { moveId: number; frequency: number }[]>;
  battle: null | {
    opponents: { speciesId: number; level: number; typeIds: number[]; rarity: Rarity; moves: { moveId: number; frequency: number }[] }[];
    targetIndex: number;
    targetMode: 'AUTOMATIC' | 'MANUAL_TARGET_FALLBACK';
    capabilities: Record<string, string>;
    selectedMoveId: number | null;
    effectiveness: string | null;
    effectivenessKnown: boolean;
  };
  catalogReady: boolean;
  catalogName: string | null;
  error: string | null;
  activeRulesetId: string | null;
  rulesetAssumed: boolean;
  loading: { active: boolean; phase: string; completedUnits: number; totalUnits: number };
  retroArch?: RetroArchState;
  saveRam?: SaveRamState;
}

export interface Bootstrap {
  catalog: Catalog | null;
  state: State;
}

export interface DiagnosticCapability {
  capability: string;
  status: string;
  confidence: number;
  offset: number | null;
  count: number | null;
  recordSize: number | null;
  elementSize?: number | null;
  validRecords?: number | null;
  totalRecords?: number | null;
  reviewStatus?: string | null;
  reasons: string[];
}

export interface DiagnosticView {
  romName: string | null;
  sha256: string;
  crc32: string;
  family: string;
  platform: string;
  activeRulesetId: string | null;
  rulesetAssumed: boolean;
  rulesets: Catalog['rulesets'];
  capabilities: DiagnosticCapability[];
  parserDiagnostics: string[];
  species: Species | null;
  move: Move | null;
}
