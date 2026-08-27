export const themeControls = {
  'game-gen1': ['#4c4c4c', '#616161', '#6e6e6e', '#444444', '#fcfcfc', '#929292', '#fdfdfd', '#010101', '#030303', '#000000', '#030303', '#ffffff'],
  'game-gen2': ['#e6360a', '#e64d20', '#6767ea', '#3f3f91', '#edfcc5', '#899272', '#f7fde5', '#010101', '#030303', '#000000', '#03fb03', '#000000'],
  'game-gen3': ['#0245e6', '#205ae8', '#dcdc02', '#888801', '#fcfcfc', '#929292', '#fdfdfd', '#010101', '#030303', '#000000', '#356ffb', '#000000'],
  'game-modern-emerald': ['#0245e6', '#205ae8', '#dcdc02', '#888801', '#fcfcfc', '#929292', '#fdfdfd', '#010101', '#030303', '#000000', '#356ffb', '#000000'],
  'game-unbound': ['#e63d02', '#e85320', '#0e73dd', '#084789', '#fcfcfc', '#929292', '#fdfdfd', '#010101', '#030303', '#000000', '#fbd30b', '#000000'],
  'game-odyssey': ['#0253e6', '#2067e8', '#dcc002', '#887701', '#fcfcfc', '#929292', '#fdfdfd', '#010101', '#030303', '#000000', '#fb03fb', '#000000'],
} as const;

const emeraldTokens = Object.fromEntries([
  'field', 'fieldPattern', 'header', 'headerShadow', 'menu', 'menuShadow',
  'panel', 'border', 'text', 'textShadow', 'accent', 'accentText',
].map((key, index) => [key, themeControls['game-gen3'][index]]));

export const catalog = {
  hash: 'stage7-ui-control', crc32: 'STAGE7', family: 'EMERALD', platform: 'GBA',
  theme: { method: 'MULTI_ASSET_QUANTIZATION', assetClasses: ['TRAINER', 'WORLD_MAP', 'LOCAL_MAP', 'SPECIES'], contrastCorrected: true, tokens: emeraldTokens },
  rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
  species: [{
    id: 25, dex: 25, name: 'PIKACHU', typeIds: [13], stats: { HP: 35, ATTACK: 55, DEFENSE: 40, SPEED: 90, 'SP. ATK': 50, 'SP. DEF': 50 },
    description: 'It stores electricity in the electric sacs on its cheeks.', height: 4, weight: 60,
    learnset: [{ level: 18, moveId: 85 }], learnsets: { default: [{ level: 18, moveId: 85 }] },
    normalizedLearnsets: { default: [{ moveId: 85, initial: false, levels: [18], label: 'Lv 18' }] }, moveAcquisitions: [],
    abilities: [{ id: 9, name: 'STATIC', description: 'Contact may paralyze the attacker.', mechanics: [{ kind: 'CHANCE', label: 'Chance', value: '30%', numerator: 3, denominator: 10 }] }],
    evolutions: [{ targetSpeciesId: 26, targetName: 'RAICHU', methodId: 7, parameter: 83, condition: 'Use Thunder Stone' }], hasSprite: true,
  }, {
    id: 26, dex: 26, name: 'RAICHU', typeIds: [13], stats: null, description: null, height: 8, weight: 300,
    learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: true,
  }],
  moves: [{ id: 85, name: 'THUNDERBOLT', typeId: 13, category: 'SPECIAL', power: 90, accuracy: 100, pp: 15, priority: 0, effectId: 0, description: 'A strong electric blast crashes down on the target.' }],
  types: [
    { id: 2, name: 'FLYING', foreground: '#17253d', background: '#a9c7f0', border: '#5b79a4' },
    { id: 4, name: 'GROUND', foreground: '#241c00', background: '#e0c068', border: '#927522' },
    { id: 13, name: 'ELECTRIC', foreground: '#2b2300', background: '#f5d642', border: '#9c851c' },
  ],
  natures: [{ id: 3, name: 'Adamant', statMultipliers: { ATTACK: 110, DEFENSE: 100, SPEED: 100, SPECIAL_ATTACK: 90, SPECIAL_DEFENSE: 100 }, raisedStat: 'ATTACK', loweredStat: 'SPECIAL_ATTACK', positivePercent: 110, negativePercent: 90, likedFlavor: 'SPICY', dislikedFlavor: 'DRY' }],
  areas: [{ id: 161, baseAreaId: 16, name: 'Route 101 grass', methodId: 1, speciesIds: [25], windows: ['DAY'], slots: [{ speciesId: 25, minimumLevel: 2, maximumLevel: 4, weight: 50 }] }],
  balls: [], capabilities: {},
  worldMaps: [{ key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/world.png', locations: [{ key: 'section-16', displayName: 'Route 101', baseAreaIds: [16], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] }] }],
  localMaps: [{ key: 'local/16', displayName: 'Route 101', baseAreaId: 16, pixelWidth: 224, pixelHeight: 120, gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/local.png', dynamicLighting: false }],
};

const member = {
  slot: 0, occupied: true, speciesId: 25, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13], nickname: 'SPARK', level: 18,
  isEgg: false, gender: 'FEMALE', natureId: 3, nature: 'Adamant', abilityId: 9, abilityName: 'STATIC', heldItemId: null, heldItemName: null,
  hasHeldItem: false, currentHp: 31, maximumHp: 45, status: null, experienceProgress: .5,
  rarity: { relativeTier: 'COMPETENT', innateTier: 'VETERAN', baseStars: 3, areaAdjustment: .5, stars: 3.5 },
  stats: { HP: 45, ATTACK: 28, DEFENSE: 22, SPEED: 38, 'SP. ATK': 30, 'SP. DEF': 26 },
  moves: [{ slot: 0, moveId: 85, name: 'THUNDERBOLT', currentPp: 12, maximumPp: 15 }], ivs: [31, 30, 29, 28, 27, 26], dvs: [],
};

export const populatedArea = {
  baseAreaId: 16, name: 'Route 101',
  overview: { knownPointCount: 3, totalPointCount: null, collectedItemCount: 1, exits: [{ baseAreaId: 17, name: 'Oldale Town' }] },
  encounters: [{ name: 'Grass', windows: ['DAY', 'NIGHT'], species: [{ speciesId: 25, name: 'PIKACHU', minimumLevel: 2, maximumLevel: 4, ratePercent: 50 }] }],
  placesAndServices: [{ key: 'house', localMapKey: 'local/16', baseAreaId: 16, tileX: 5, tileY: 4, category: 'SERVICE', state: 'IDENTIFIED', label: 'Pokémon Center', service: 'HEALING', itemId: null, destinationBaseAreaId: 17 }],
  trainersAndPeople: [],
  items: [{ key: 'item', localMapKey: 'local/16', baseAreaId: 16, tileX: 9, tileY: 6, category: 'AVAILABLE_ITEM', state: 'SILHOUETTE', label: null, service: null, itemId: null, destinationBaseAreaId: null }],
  objectives: [{ key: 'explore', title: 'Explore Route 101' }],
};

export const emptyArea = {
  baseAreaId: 16, name: 'Route 101', overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
  encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
};

export const specimens = {
  version: 7, speciesId: 25, speciesName: 'PIKACHU', specimens: [{
    key: 'individual:1', location: { kind: 'PARTY', label: 'Party · Slot 1', boxNumber: null, slotNumber: 1 }, formId: null, ...member,
  }, {
    key: 'individual:2', location: { kind: 'BOX', label: 'Box 2 · Slot 2', boxNumber: 2, slotNumber: 2 }, formId: null,
    ...member, nickname: 'VOLT', level: 12, natureId: null, nature: null, abilityId: null, abilityName: null,
    currentHp: null, maximumHp: null, experienceProgress: .2, rarity: null, ivs: [12, 13, 14, 15, 16, 17], moves: [],
  }],
};

export const baseState = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, selectedPartySlot: 0,
  filter: 'ALL', selectedAreaId: null, selectedAreaIds: [], currentAreaIds: [161], currentAreaBaseId: 16, currentAreaName: 'Route 101',
  currentMapPosition: { x: 12, y: 8 }, currentAreaSpeciesIds: [25], revealedAreaBaseIds: [16], observedAreaBaseIdsBySpecies: { 25: [16] },
  localMapPois: [], localMapPoiPreferences: { showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true, iconZoomThresholdPercent: 0, labelZoomThresholdPercent: 0 },
  areaGuide: { trackedAreaBaseId: 16, areas: [populatedArea] }, areaGuideAvailability: { status: 'AVAILABLE' },
  gameTime: { hours: 12, minutes: 34, phase: 'DAY', phaseProgress: .5 },
  battleTab: 'ENTRY', settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'GAME', displayTarget: 'AUTO' },
  speciesState: { 25: { seen: true, caught: true, team: true, ballId: null, specimenCount: 2 }, 26: { seen: true, caught: false, team: false, ballId: null } }, observedMoves: {},
  trainerCardUnlocked: true, trainer: { name: 'MAY', gender: 'FEMALE', publicTrainerId: 12345, money: 3000, playTimeHours: 2, playTimeMinutes: 15, dexSeen: 2, dexCaught: 1, stars: 1, avatarUrl: null, badges: Array.from({ length: 8 }, (_, index) => ({ index, earned: index < 2, imageUrl: null })) },
  trainerProgress: {
    selectedDestination: 'CARD', selectedSection: 'METRICS',
    gameTotals: [{ key: 'money', label: 'Money', value: 3000 }, { key: 'seen', label: 'Pokédex seen', value: 2 }, { key: 'caught', label: 'Pokédex caught', value: 1 }],
    trackedJourney: [{ key: 'captures', label: 'Captures', value: 2 }, { key: 'areas', label: 'Areas visited', value: 3 }],
    challengeSummary: { completed: 1, applicable: 4, completionPercent: 25 },
    challenges: [{ key: 'roster', title: 'Growing Roster', description: 'Catch five different Pokémon.', category: 'COLLECTION', progress: 2, target: 5, completionPercent: 40, complete: false }, { key: 'partner', title: 'A New Partner', description: 'Catch your first Pokémon on this journey.', category: 'COLLECTION', progress: 1, target: 1, completionPercent: 100, complete: true }],
    timeline: [{ recordedAtEpochMs: 1720000000000, changes: ['Captures +1', 'Areas visited +1'], milestone: true }],
  },
  party: [member],
  partyAnalysis: {
    teamSummary: { partySize: 1, minimumLevel: 18, maximumLevel: 18, faintedCount: 0, statusCount: 0, moveDistribution: { physical: 0, special: 1, status: 0, unresolved: 0 } },
    offensiveCoverage: { contributingMoveCount: 1, types: [{ defendingTypeId: 2, outcome: 'SUPER_EFFECTIVE', bestMultiplierPercent: 200, attackingTypeIds: [13], memberSlots: [0] }, { defendingTypeId: 4, outcome: 'NO_EFFECTIVE_KNOWN_OPTION', bestMultiplierPercent: 0, attackingTypeIds: [13], memberSlots: [0] }] },
    defensiveProfile: { members: [{ slot: 0, speciesId: 25, typeIds: [13], availableForImmediateBattle: true, weaknessTypeIds: [4], resistanceTypeIds: [13], immunityTypeIds: [], abilityModifiers: [] }], unavailableMemberSlots: [], repeatedWeaknesses: [{ attackingTypeId: 4, memberCount: 1 }] },
    development: { evolutionOpportunities: [{ slot: 0, speciesId: 25, targetSpeciesId: 26, methodId: 7, parameter: 83, availableNow: false }], nearbyMoves: [{ slot: 0, speciesId: 25, moveId: 85, level: 20, levelsAway: 2 }], moveRoleGaps: ['PHYSICAL'] },
  },
  battle: null, catalogReady: true, catalogName: 'Stage 7 control', error: null, activeRulesetId: 'default', rulesetAssumed: false,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 }, gameAccessReady: true,
};

export const battle = {
  opponents: [{ speciesId: 26, level: 20, typeIds: [13], rarity: { relativeTier: 'COMPETENT', innateTier: 'VETERAN', baseStars: 3, areaAdjustment: .5, stars: 3.5 }, moves: [] }],
  targetIndex: 0, targetMode: 'AUTOMATIC', capabilities: {}, selectedMoveId: 85, encounterKind: 'WILD', effectiveness: 'SUPER EFFECTIVE', effectivenessKnown: true,
};

export function exactForecast() {
  return { confidence: 'EXACT', minimumHp: 35, maximumHp: 42, minimumTargetPercent: 43.75, maximumTargetPercent: 52.5, minimumHitsToKnockOut: 2, maximumHitsToKnockOut: 3, accuracyPercent: 100, effectivenessPercent: 200, conditions: ['Same-type bonus'], uncertainty: null };
}

export function boundedForecast() {
  return { confidence: 'BOUNDED', minimumHp: 28, maximumHp: 55, minimumTargetPercent: 35, maximumTargetPercent: 68.75, minimumHitsToKnockOut: 2, maximumHitsToKnockOut: 4, accuracyPercent: 100, effectivenessPercent: 100, conditions: ['Weather may change the result'], uncertainty: 'Weather could change before the move lands.' };
}
