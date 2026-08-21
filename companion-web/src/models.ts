export type KnowledgeMode = 'DISCOVERED' | 'ORGANIC' | 'HIDDEN';
export type Screen = 'POKEDEX' | 'DETAIL' | 'BATTLE' | 'TRAINER' | 'PARTY' | 'SETTINGS' | 'SETUP';

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
    mechanics: {
      kind: string;
      label: string;
      value: string;
      numerator: number;
      denominator: number;
      conditions?: { kind: string; value: number; label: string }[];
    }[];
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

export interface NatureInfo {
  id: number;
  name: string;
  statMultipliers: Record<'ATTACK' | 'DEFENSE' | 'SPEED' | 'SPECIAL_ATTACK' | 'SPECIAL_DEFENSE', number>;
  raisedStat: 'ATTACK' | 'DEFENSE' | 'SPEED' | 'SPECIAL_ATTACK' | 'SPECIAL_DEFENSE' | null;
  loweredStat: 'ATTACK' | 'DEFENSE' | 'SPEED' | 'SPECIAL_ATTACK' | 'SPECIAL_DEFENSE' | null;
  positivePercent: number;
  negativePercent: number;
  likedFlavor: 'SPICY' | 'DRY' | 'SWEET' | 'BITTER' | 'SOUR' | null;
  dislikedFlavor: 'SPICY' | 'DRY' | 'SWEET' | 'BITTER' | 'SOUR' | null;
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
  areas: { id: number; baseAreaId?: number; name: string; methodId: number; speciesIds: number[]; windows: EncounterWindow[]; slots: { speciesId: number; minimumLevel: number; maximumLevel: number; weight: number | null }[] }[];
  balls: { id: number; name: string; generic: boolean; hasSprite: boolean }[];
  natures?: NatureInfo[];
  worldMaps?: WorldMapRegion[];
  localMaps?: LocalMapView[];
  mapScenes?: LocalMapSceneView[];
  theme?: CatalogTheme;
  capabilities: Record<string, string>;
}

export interface CatalogTheme {
  method: 'DIRECT_UI_PALETTE' | 'MULTI_ASSET_QUANTIZATION' | 'NEUTRAL_FALLBACK';
  assetClasses: ('INTERFACE' | 'TRAINER' | 'WORLD_MAP' | 'LOCAL_MAP' | 'SPECIES')[];
  contrastCorrected: boolean;
  tokens: {
    field: string;
    fieldPattern: string;
    header: string;
    headerShadow: string;
    menu: string;
    menuShadow: string;
    panel: string;
    border: string;
    text: string;
    textShadow: string;
    accent: string;
    accentText: string;
  };
}

export interface LocalMapView {
  key: string;
  displayName: string | null;
  baseAreaId: number;
  pixelWidth: number;
  pixelHeight: number;
  gridWidth: number;
  gridHeight: number;
  imageUrl: string;
  dynamicLighting: boolean;
}

export interface LocalMapSceneView {
  key: string;
  pixelWidth: number;
  pixelHeight: number;
  gridWidth: number;
  gridHeight: number;
  placements: LocalMapScenePlacementView[];
}

export interface LocalMapScenePlacementView {
  localMapKey: string;
  baseAreaId: number;
  gridX: number;
  gridY: number;
  pixelX: number;
  pixelY: number;
  pixelWidth: number;
  pixelHeight: number;
  gridWidth: number;
  gridHeight: number;
  imageUrl: string;
  dynamicLighting: boolean;
}

export interface WorldMapRegion {
  key: string;
  displayName: string | null;
  pixelWidth: number;
  pixelHeight: number;
  gridWidth: number;
  gridHeight: number;
  imageUrl: string;
  locations: WorldMapLocation[];
}

export interface WorldMapLocation {
  key: string;
  displayName: string;
  baseAreaIds: number[];
  geometry: { x: number; y: number; width: number; height: number }[];
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
  currentAreaName?: string | null;
  matchingAreaCount?: number;
  candidateAreaCount?: number;
}

export interface State {
  version: number;
  screen: Screen;
  priorScreen: Screen;
  settingsReturnScreen: Screen;
  selectedSpeciesId: number | null;
  selectedPartySlot?: number | null;
  filter: 'ALL' | 'CAUGHT' | 'SEEN' | 'TEAM' | 'AREA';
  selectedAreaId: number | null;
  selectedAreaIds?: number[];
  currentAreaIds?: number[];
  currentAreaBaseId?: number | null;
  currentAreaName?: string | null;
  currentMapPosition?: { x: number; y: number } | null;
  gameTime?: GameTime | null;
  currentAreaSpeciesIds?: number[];
  revealedAreaBaseIds?: number[];
  observedAreaBaseIdsBySpecies?: Record<number, number[]>;
  battleTab: 'ENTRY' | 'ATTACK' | 'RARITY' | 'MOVES';
  settings: Settings;
  speciesState: Record<number, SpeciesState>;
  observedMoves: Record<number, { moveId: number; frequency: number }[]>;
  trainer?: TrainerView | null;
  party?: PartyMemberView[];
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

export type MapLighting = 'MORNING' | 'DAY' | 'NIGHT' | 'DARK';

export interface GameTime {
  hours: number | null;
  minutes: number | null;
  phase?: MapLighting | null;
  phaseProgress?: number | null;
}

export interface TrainerView {
  name: string;
  gender: 'MALE' | 'FEMALE';
  publicTrainerId: number;
  money: number;
  playTimeHours: number;
  playTimeMinutes: number;
  dexSeen: number;
  dexCaught: number;
  stars: number | null;
  avatarUrl: string | null;
  badges: { index: number; earned: boolean; imageUrl: string | null }[];
}

export interface PartyMemberView {
  slot: number;
  occupied: boolean;
  speciesId: number | null;
  speciesName: string | null;
  spriteUrl: string | null;
  typeIds: number[];
  nickname: string | null;
  level: number | null;
  isEgg: boolean;
  gender: 'MALE' | 'FEMALE' | 'GENDERLESS' | null;
  natureId?: number | null;
  nature: string | null;
  abilityId: number | null;
  abilityName: string | null;
  heldItemId: number | null;
  heldItemName: string | null;
  hasHeldItem?: boolean | null;
  currentHp: number | null;
  maximumHp: number | null;
  status: string | null;
  experienceProgress: number | null;
  stats: Record<string, number>;
  moves: { slot: number; moveId: number | null; name: string | null; currentPp: number | null; maximumPp: number | null }[];
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
