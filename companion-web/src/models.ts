export type KnowledgeMode = 'DISCOVERED' | 'ORGANIC' | 'HIDDEN';
export type Screen = 'POKEDEX' | 'DETAIL' | 'BATTLE' | 'TRAINER' | 'PARTY' | 'SETTINGS' | 'SETUP';

export type PresentationMessageCode =
  | 'MOVE_INITIAL'
  | 'MOVE_LEVEL'
  | 'RULESET_DEFAULT'
  | 'RULESET_BASE'
  | 'RULESET_EXPANDED'
  | 'RULESET_OTHER'
  | 'EVOLUTION_LEVEL'
  | 'EVOLUTION_TRADE'
  | 'EVOLUTION_TRADE_WITH_ITEM'
  | 'EVOLUTION_USE_ITEM'
  | 'EVOLUTION_HIGH_FRIENDSHIP'
  | 'EVOLUTION_UNKNOWN'
  | 'SPECIMEN_PARTY_SLOT'
  | 'SPECIMEN_BOX_SLOT'
  | 'ABILITY_MECHANIC_BEHAVIOR'
  | 'ABILITY_MECHANIC_ACTIVATION_THRESHOLD'
  | 'ABILITY_MECHANIC_MULTIPLIER'
  | 'ABILITY_MECHANIC_STAT_STAGE'
  | 'ABILITY_MECHANIC_STATUS_CURE'
  | 'ABILITY_MECHANIC_TYPE_CHANGE'
  | 'ABILITY_MECHANIC_AI_RATING'
  | 'ABILITY_MECHANIC_FLAG'
  | 'ABILITY_MECHANIC_ATTACK'
  | 'ABILITY_MECHANIC_MOVE_POWER'
  | 'ABILITY_MECHANIC_INCOMING_DAMAGE'
  | 'ABILITY_MECHANIC_OPPONENT_ATTACK'
  | 'ABILITY_MECHANIC_NONVOLATILE_STATUS'
  | 'ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED'
  | 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SWAPPED'
  | 'ABILITY_MECHANIC_FLAG_CANNOT_BE_TRACED'
  | 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SUPPRESSED'
  | 'ABILITY_MECHANIC_FLAG_CANNOT_BE_OVERWRITTEN'
  | 'ABILITY_MECHANIC_FLAG_BREAKABLE'
  | 'ABILITY_MECHANIC_FLAG_FAILS_ON_IMPOSTER'
  | 'ABILITY_VALUE_HP_THRESHOLD'
  | 'ABILITY_VALUE_ATTACK_MULTIPLIER'
  | 'ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER'
  | 'ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER'
  | 'ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER'
  | 'ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER'
  | 'ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER'
  | 'ABILITY_VALUE_STAT_STAGE'
  | 'ABILITY_VALUE_STATUS_CURE_CHANCE'
  | 'ABILITY_VALUE_NORMAL_TO_FAIRY'
  | 'ABILITY_VALUE_AI_RATING'
  | 'ABILITY_VALUE_ENABLED'
  | 'ABILITY_CONDITION_MOVE_SPLIT'
  | 'ABILITY_CONDITION_ATTACKER_STATUS_NON_ZERO'
  | 'ABILITY_CONDITION_SWITCH_IN'
  | 'ABILITY_CONDITION_MOVE_POWER_NON_ZERO'
  | 'ABILITY_CONDITION_ATTACKING_MOVE_TYPE'
  | 'DAMAGE_CONDITION_STAB'
  | 'DAMAGE_CONDITION_STATUS'
  | 'DAMAGE_CONDITION_CRITICAL'
  | 'DAMAGE_CONDITION_WEATHER'
  | 'DAMAGE_CONDITION_ABILITY'
  | 'DAMAGE_CONDITION_ITEM'
  | 'DAMAGE_CONDITION_FIELD'
  | 'DAMAGE_CONDITION_MULTI_HIT'
  | 'DAMAGE_CONDITION_FIXED_DAMAGE'
  | 'DAMAGE_RANGE_BOUNDED'
  | 'GUIDE_LOAD_FAILED'
  | 'CATALOG_LOADING_FIRST_PREPARATION'
  | 'CATALOG_LOADING_VERSION_REFRESH'
  | 'CATALOG_LOADING_CACHE_RECOVERY'
  | 'RETROARCH_GAME_OPEN_FAILED'
  | 'API_SERVER_BUSY'
  | 'API_METHOD_NOT_ALLOWED'
  | 'API_REQUEST_TIMEOUT'
  | 'API_GUIDE_LOAD_FAILED'
  | 'API_INVALID_REQUEST'
  | 'API_INTERNAL_ERROR'
  | 'API_NOT_FOUND'
  | 'API_MAP_UNAVAILABLE'
  | 'PROGRESS_METRIC_PLAY_TIME'
  | 'PROGRESS_METRIC_BADGES'
  | 'PROGRESS_METRIC_DEX_SEEN'
  | 'PROGRESS_METRIC_DEX_CAUGHT'
  | 'PROGRESS_METRIC_MONEY'
  | 'PROGRESS_METRIC_BATTLES'
  | 'PROGRESS_METRIC_WILD_ENCOUNTERS'
  | 'PROGRESS_METRIC_TRAINER_BATTLES'
  | 'PROGRESS_METRIC_CAPTURES'
  | 'PROGRESS_METRIC_EVOLUTIONS'
  | 'PROGRESS_METRIC_AREAS_VISITED'
  | 'PROGRESS_METRIC_POINTS_DISCOVERED'
  | 'PROGRESS_METRIC_PARTY_CHANGES'
  | 'PROGRESS_METRIC_SAVES_OBSERVED'
  | 'PROGRESS_METRIC_CHALLENGES_COMPLETED'
  | 'TIMELINE_BATTLES'
  | 'TIMELINE_WILD_ENCOUNTERS'
  | 'TIMELINE_TRAINER_BATTLES'
  | 'TIMELINE_CAPTURES'
  | 'TIMELINE_EVOLUTIONS'
  | 'TIMELINE_AREAS_VISITED'
  | 'TIMELINE_POINTS_DISCOVERED'
  | 'TIMELINE_PARTY_CHANGES'
  | 'TIMELINE_SAVES_OBSERVED'
  | 'TIMELINE_CHALLENGES_COMPLETED'
  | 'CHALLENGE_COLLECTION_FIRST_PARTNER_TITLE'
  | 'CHALLENGE_COLLECTION_FIRST_PARTNER_DESCRIPTION'
  | 'CHALLENGE_COLLECTION_GROWING_ROSTER_TITLE'
  | 'CHALLENGE_COLLECTION_GROWING_ROSTER_DESCRIPTION'
  | 'CHALLENGE_PARTY_NEW_FORM_TITLE'
  | 'CHALLENGE_PARTY_NEW_FORM_DESCRIPTION'
  | 'CHALLENGE_EXPLORATION_OPEN_ROAD_TITLE'
  | 'CHALLENGE_EXPLORATION_OPEN_ROAD_DESCRIPTION'
  | 'CHALLENGE_EXPLORATION_CURIOUS_EYE_TITLE'
  | 'CHALLENGE_EXPLORATION_CURIOUS_EYE_DESCRIPTION'
  | 'CHALLENGE_BATTLE_SEASONED_TITLE'
  | 'CHALLENGE_BATTLE_SEASONED_DESCRIPTION'
  | 'CHALLENGE_PROGRESS_FIRST_BADGE_TITLE'
  | 'CHALLENGE_PROGRESS_FIRST_BADGE_DESCRIPTION'
  | 'CHALLENGE_PROGRESS_ALL_BADGES_TITLE'
  | 'CHALLENGE_PROGRESS_ALL_BADGES_DESCRIPTION'
  | 'CHALLENGE_COLLECTION_REGIONAL_RECORD_TITLE'
  | 'CHALLENGE_COLLECTION_REGIONAL_RECORD_DESCRIPTION'
  | 'CHALLENGE_EXPLORATION_AREA_ITEMS_TITLE'
  | 'CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION'
  | 'CHALLENGE_BATTLE_LEADER_NO_ITEMS_TITLE'
  | 'CHALLENGE_BATTLE_LEADER_NO_ITEMS_DESCRIPTION'
  | 'CHALLENGE_SPECIAL_MINIGAME_TITLE'
  | 'CHALLENGE_SPECIAL_MINIGAME_DESCRIPTION';

export interface PresentationMessage {
  code: PresentationMessageCode;
  level?: number | null;
  itemId?: number | null;
  methodId?: number | null;
  parameter?: number | null;
  slotNumber?: number | null;
  boxNumber?: number | null;
  index?: number | null;
  numerator?: number | null;
  denominator?: number | null;
  conditionValue?: number | null;
  count?: number | null;
  subject?: string | null;
}

export type StatName = 'HP' | 'ATTACK' | 'DEFENSE' | 'SPEED' | 'SPECIAL_ATTACK' | 'SPECIAL_DEFENSE';

export interface Species {
  id: number;
  dex: number;
  name: string;
  typeIds: number[];
  stats: Partial<Record<StatName, number>> | null;
  description: string | null;
  height: number | null;
  weight: number | null;
  learnset: { level: number; moveId: number }[];
  learnsets: Record<string, { level: number; moveId: number }[]>;
  normalizedLearnsets: Record<string, { moveId: number; initial: boolean; levels: number[]; labels: PresentationMessage[] }[]>;
  moveAcquisitions: { moveId: number; method: 'EGG' | 'MACHINE' | 'TUTOR'; sourceId: number | null }[];
  abilities: {
    id: number;
    name: string;
    description: string | null;
    mechanics: {
      kind: string;
      label: PresentationMessage;
      value: PresentationMessage;
      numerator: number;
      denominator: number;
      conditions?: { kind: string; value: number; label: PresentationMessage }[];
    }[];
  }[];
  evolutions: { targetSpeciesId: number; targetName: string; methodId: number; parameter: number; condition: PresentationMessage }[];
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

export interface TypeMatchupView {
  attackingTypeId: number;
  defendingTypeId: number;
  multiplierPercent: number;
}

export interface NatureInfo {
  id: number;
  name: string | null;
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
  rulesets: { id: string; label: PresentationMessage; sourceOffset: number; confidence: number; primary: boolean }[];
  species: Species[];
  moves: Move[];
  types: TypeInfo[];
  typeMatchups?: TypeMatchupView[];
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

export type LocalMapPoiCategory = 'PLACE' | 'SERVICE' | 'AVAILABLE_ITEM' | 'COLLECTED_ITEM' | 'UNKNOWN';
export type LocalMapPoiState = 'SILHOUETTE' | 'IDENTIFIED' | 'COLLECTED';

export interface LocalMapPoiView {
  key: string;
  localMapKey: string;
  baseAreaId: number;
  tileX: number;
  tileY: number;
  category: LocalMapPoiCategory;
  state: LocalMapPoiState;
  displayName: string | null;
  service: string | null;
  itemId: number | null;
  itemName: string | null;
  destinationBaseAreaId: number | null;
}

export interface LocalMapPoiPreferences {
  showPlaces: boolean;
  showServices: boolean;
  showAvailableItems: boolean;
  showCollectedItems: boolean;
  showUnknownPois: boolean;
  iconZoomThresholdPercent: number;
  labelZoomThresholdPercent: number;
}

export interface AreaGuideView {
  trackedAreaBaseId: number | null;
  areas: AreaGuideAreaView[];
}

export interface AreaGuideAreaView {
  baseAreaId: number;
  name: string;
  overview: AreaGuideOverviewView;
  encounters: AreaGuideEncounterGroupView[];
  placesAndServices: AreaGuidePointView[];
  trainersAndPeople: AreaGuidePointView[];
  items: AreaGuidePointView[];
  objectives: AreaGuideObjectiveView[];
}

export interface AreaGuideOverviewView {
  knownPointCount: number;
  totalPointCount: number | null;
  collectedItemCount: number;
  exits: AreaGuideExitView[];
}

export interface AreaGuideExitView {
  baseAreaId: number;
  name: string;
  count?: number;
}

export interface AreaGuideEncounterGroupView {
  name: string | null;
  windows: string[];
  species: AreaGuideEncounterSpeciesView[];
}

export interface AreaGuideEncounterSpeciesView {
  speciesId: number;
  name: string;
  minimumLevel: number;
  maximumLevel: number;
  ratePercent: number | null;
  hasSprite: boolean;
}

export interface AreaGuidePointView {
  key: string;
  localMapKey: string;
  baseAreaId: number;
  tileX: number;
  tileY: number;
  category: LocalMapPoiCategory;
  state: LocalMapPoiState;
  label: string | null;
  service: string | null;
  itemId: number | null;
  destinationBaseAreaId: number | null;
}

export interface AreaGuideObjectiveView {
  key: string;
  title: PresentationMessage;
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
  displayName: string | null;
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
  interfaceLanguage?: 'AUTO' | 'EN' | 'FR' | 'DE' | 'IT' | 'ES';
  overlayScale?: number;
  battlePollingIntervalMs?: number;
  mapFollowSmoothingPercent?: number;
  highVisibilityMapPlayer?: boolean;
}

export interface SpeciesState {
  seen: boolean;
  caught: boolean;
  team: boolean;
  ballId: number | null;
  preferredLevel?: number | null;
  innateTier?: string | null;
  specimenCount?: number;
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
  presentationMessage?: PresentationMessage | null;
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
  localMapPois?: LocalMapPoiView[];
  localMapPoiPreferences?: LocalMapPoiPreferences;
  areaGuide?: AreaGuideView | null;
  areaGuideAvailability?: {
    status: 'AVAILABLE' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
    stage?: string | null;
    failureClass?: string | null;
  };
  gameTime?: GameTime | null;
  currentAreaSpeciesIds?: number[];
  revealedAreaBaseIds?: number[];
  observedAreaBaseIdsBySpecies?: Record<number, number[]>;
  battleTab: 'ENTRY' | 'ATTACK' | 'RARITY' | 'MOVES';
  settings: Settings;
  speciesState: Record<number, SpeciesState>;
  observedMoves: Record<number, { moveId: number; frequency: number }[]>;
  trainerCardUnlocked?: boolean;
  trainer?: TrainerView | null;
  trainerProgress?: TrainerProgressView | null;
  trainerAvatarUrl?: string | null;
  trainerMapSpriteUrl?: string | null;
  trainerMapSpriteWidth?: number | null;
  trainerMapSpriteHeight?: number | null;
  party?: PartyMemberView[];
  partyAnalysis?: PartyAnalysis | null;
  battle: null | {
    opponents: { speciesId: number; level: number; typeIds: number[]; rarity: Rarity; moves: { moveId: number; frequency: number }[] }[];
    targetIndex: number;
    targetMode: 'AUTOMATIC' | 'MANUAL_TARGET_FALLBACK';
    capabilities: Record<string, string>;
    selectedMoveId: number | null;
    encounterKind: 'WILD' | 'TRAINER' | 'UNKNOWN';
    effectiveness: string | null;
    effectivenessKnown: boolean;
    damageForecast?: DamageForecast | null;
  };
  catalogReady: boolean;
  catalogName: string | null;
  catalogHash?: string | null;
  activeLanguage?: ActiveLanguageBinding | null;
  mapperAvailable?: boolean;
  error: PresentationMessage | null;
  activeRulesetId: string | null;
  rulesetAssumed: boolean;
  loading: { active: boolean; phase: string; completedUnits: number; totalUnits: number; message?: PresentationMessage | null };
  retroArch?: RetroArchState;
  saveRam?: SaveRamState;
  gameAccessReady?: boolean;
}

export type MapLighting = 'MORNING' | 'DAY' | 'NIGHT' | 'DARK';

export interface GameTime {
  hours: number | null;
  minutes: number | null;
  phase?: MapLighting | null;
  phaseProgress?: number | null;
}

export interface DamageForecast {
  confidence: 'EXACT' | 'BOUNDED';
  minimumHp: number;
  maximumHp: number;
  minimumTargetPercent: number;
  maximumTargetPercent: number;
  minimumHitsToKnockOut: number;
  maximumHitsToKnockOut: number;
  accuracyPercent: number;
  effectivenessPercent: number;
  conditions: PresentationMessage[];
  uncertainty: PresentationMessage | null;
}

export interface TrainerView {
  name: string;
  gender: 'MALE' | 'FEMALE';
  publicTrainerId: number | null;
  money: number | null;
  playTimeHours: number | null;
  playTimeMinutes: number | null;
  dexSeen: number | null;
  dexCaught: number | null;
  stars: number | null;
  avatarUrl: string | null;
  badges: { index: number; earned: boolean | null; imageUrl: string | null }[];
}

export interface TrainerProgressView {
  selectedDestination: 'CARD' | 'PROGRESS';
  selectedSection: 'METRICS' | 'CHALLENGES' | 'TIMELINE';
  gameTotals: ProgressMetricView[];
  trackedJourney: ProgressMetricView[];
  challengeSummary: ChallengeSummaryView;
  challenges: ChallengeView[];
  timeline: TimelineEntryView[];
}

export interface ProgressMetricView {
  key: string;
  label: PresentationMessage;
  value: number | null;
}

export interface ChallengeSummaryView {
  completed: number;
  applicable: number;
  completionPercent: number | null;
}

export interface ChallengeView {
  key: string;
  title: PresentationMessage;
  description: PresentationMessage;
  category: 'PROGRESS' | 'COLLECTION' | 'EXPLORATION' | 'BATTLE' | 'PARTY' | 'SPECIAL';
  progress: number | null;
  target: number | null;
  completionPercent: number | null;
  complete: boolean;
}

export interface TimelineEntryView {
  recordedAtEpochMs: number;
  changes: PresentationMessage[];
  milestone: boolean;
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
  rarity?: Rarity | null;
  stats: Partial<Record<StatName, number>>;
  moves: { slot: number; moveId: number | null; name: string | null; currentPp: number | null; maximumPp: number | null }[];
}

export interface OwnedIndividualLocationView {
  kind: 'PARTY' | 'BOX';
  label: PresentationMessage;
  boxNumber: number | null;
  slotNumber: number;
}

export interface OwnedIndividualView {
  key: string;
  location: OwnedIndividualLocationView;
  speciesId: number;
  formId: number | null;
  speciesName: string | null;
  spriteUrl: string | null;
  typeIds: number[];
  nickname: string | null;
  level: number | null;
  isEgg: boolean;
  gender: 'MALE' | 'FEMALE' | 'GENDERLESS' | null;
  natureId: number | null;
  nature: string | null;
  abilityId: number | null;
  abilityName: string | null;
  heldItemId: number | null;
  heldItemName?: string | null;
  hasHeldItem: boolean | null;
  currentHp: number | null;
  maximumHp: number | null;
  status: string | null;
  experienceProgress: number | null;
  rarity: Rarity | null;
  stats: Partial<Record<StatName, number>>;
  moves: { slot: number; moveId: number | null; name: string | null; currentPp: number | null; maximumPp: number | null }[];
  ivs: number[];
  dvs: number[];
}

export interface SpecimenCollectionView {
  version: number;
  speciesId: number;
  speciesName: string;
  specimens: OwnedIndividualView[];
}

export interface PartyAnalysis {
  teamSummary: {
    partySize: number;
    minimumLevel: number | null;
    maximumLevel: number | null;
    faintedCount: number;
    statusCount: number;
    moveDistribution: { physical: number; special: number; status: number; unresolved: number } | null;
  };
  offensiveCoverage: null | {
    contributingMoveCount: number;
    types: {
      defendingTypeId: number;
      outcome: 'SUPER_EFFECTIVE' | 'NEUTRAL_ONLY' | 'NO_EFFECTIVE_KNOWN_OPTION';
      bestMultiplierPercent: number | null;
      attackingTypeIds: number[];
      memberSlots: number[];
    }[];
  };
  defensiveProfile: null | {
    members: {
      slot: number;
      speciesId: number;
      typeIds: number[];
      availableForImmediateBattle: boolean;
      weaknessTypeIds: number[];
      resistanceTypeIds: number[];
      immunityTypeIds: number[];
      abilityModifiers: { abilityId: number; attackingTypeId: number; numerator: number; denominator: number }[];
    }[];
    unavailableMemberSlots: number[];
    repeatedWeaknesses: { attackingTypeId: number; memberCount: number }[];
  };
  development: {
    evolutionOpportunities: { slot: number; speciesId: number; targetSpeciesId: number; methodId: number; parameter: number; availableNow: boolean | null }[];
    nearbyMoves: { slot: number; speciesId: number; moveId: number; level: number; levelsAway: number }[];
    moveRoleGaps: ('PHYSICAL' | 'SPECIAL')[];
  };
}

export interface LocalizedCapability {
  status: string;
  confidence: number;
  coveredRecords: number;
  expectedRecords: number;
  incompleteRecords: number;
  reviewStatus: string;
  validatorReviewRecommended: boolean;
}

export interface ActiveLanguageBinding {
  romSha256: string;
  contextEpoch: number | null;
  stateVersion: number | null;
  language: string;
  authority: 'ROM_DEFAULT' | 'LIVE_RAM';
  projectionVersion: number;
}

export interface LocalizedEntityText {
  name?: string | null;
  description?: string | null;
}

export interface CatalogLanguageOverlay {
  binding: ActiveLanguageBinding;
  species: Record<number, LocalizedEntityText>;
  moves: Record<number, LocalizedEntityText>;
  abilities: Record<number, LocalizedEntityText>;
  types: Record<number, LocalizedEntityText>;
  natures: Record<number, LocalizedEntityText>;
  items: Record<number, LocalizedEntityText>;
  areas: Record<number, LocalizedEntityText>;
  localMaps: Record<string, LocalizedEntityText>;
  worldRegions: Record<string, LocalizedEntityText>;
  worldLocations: { regionKey: string; locationKey: string; name?: string | null }[];
}

export interface LanguageProjection {
  language: string;
  status: string;
  codecId: string;
  codecVersion: number;
  overlayVersion: number | null;
  localizedCapabilities: Record<string, LocalizedCapability>;
}

export interface LanguageBootstrap {
  manifestStatus: string;
  defaultLanguage: string | null;
  activeLanguage: string | null;
  authority: 'ROM_DEFAULT' | 'LIVE_RAM';
  activeOverlayVersion: number | null;
  projections: LanguageProjection[];
  binding?: ActiveLanguageBinding | null;
}

export interface Bootstrap {
  catalog: Catalog | null;
  state: State;
  language?: LanguageBootstrap | null;
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
  coveredRecords?: number | null;
  expectedRecords?: number | null;
  incompleteRecords?: number | null;
  reviewStatus?: string | null;
  reasons: string[];
}

export interface DiagnosticEnvironment {
  appVersion: string | null;
  catalogSchemaVersion: number;
  parserSchemaVersion: number;
}

export interface DiagnosticRuntime {
  retroArchConnection: string;
  contentResolution: string;
  gameAccessReady: boolean;
  saveRamStatus: string;
  saveAutosaveStatus: string;
  saveCapabilities: Record<string, string>;
  catalogLoadingActive: boolean;
  catalogLoadingPhase: string;
  catalogLoadingCompletedUnits: number;
  catalogLoadingTotalUnits: number;
}

export interface DiagnosticMap {
  presentation: string;
  currentAreaBaseId: number | null;
  currentAreaName: string | null;
  localMapKey: string | null;
  sceneKey: string | null;
  atlasRegionKey: string | null;
  playerPositionStatus: string;
  playerX: number | null;
  playerY: number | null;
  lighting: string;
  totalPois: number;
  visiblePois: number;
  collectedPois: number;
  localMapStatus: string;
  worldMapStatus: string;
  fallbackReason: string | null;
}

export interface DiagnosticCache {
  entries: number;
  encodedBytes: number;
  hits: number;
  renders: number;
  evictions: number;
}

export interface DiagnosticPrivacy {
  containsRomBytes: boolean;
  containsMemoryBytes: boolean;
  containsSaveData: boolean;
  containsPrivatePaths: boolean;
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
  reportSchemaVersion?: number;
  environment?: DiagnosticEnvironment | null;
  runtime?: DiagnosticRuntime | null;
  map?: DiagnosticMap | null;
  cache?: DiagnosticCache | null;
  privacy?: DiagnosticPrivacy;
}
