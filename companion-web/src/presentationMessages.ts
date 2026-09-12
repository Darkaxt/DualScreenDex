import type { PresentationMessage } from './models';
import { formatUiNumber, msg, pluralCategory } from './i18n';

export function renderPresentationMessage(message: PresentationMessage): string {
  switch (message.code) {
    case 'MOVE_INITIAL': return msg('moveInitial');
    case 'MOVE_LEVEL': return msg('levelShort', argument(message.level));
    case 'RULESET_DEFAULT': return msg('rulesetDefault');
    case 'RULESET_BASE': return msg('rulesetBase');
    case 'RULESET_EXPANDED': return msg('rulesetExpanded', argument(message.index));
    case 'RULESET_OTHER': return msg('rulesetOther');
    case 'EVOLUTION_LEVEL': return msg('evolutionLevel', argument(message.level));
    case 'EVOLUTION_TRADE': return msg('evolutionTrade');
    case 'EVOLUTION_TRADE_WITH_ITEM': return msg('evolutionTradeWithItem', argument(message.itemId));
    case 'EVOLUTION_USE_ITEM': return msg('evolutionUseItem', argument(message.itemId));
    case 'EVOLUTION_HIGH_FRIENDSHIP': return msg('evolutionHighFriendship');
    case 'EVOLUTION_UNKNOWN': return msg('evolutionUnknown', argument(message.methodId), argument(message.parameter));
    case 'SPECIMEN_PARTY_SLOT': return msg('partySlotLocation', argument(message.slotNumber));
    case 'SPECIMEN_BOX_SLOT': return msg('boxSlotLocation', argument(message.boxNumber), argument(message.slotNumber));
    case 'ABILITY_MECHANIC_BEHAVIOR': return msg('abilityEffect');
    case 'ABILITY_MECHANIC_ACTIVATION_THRESHOLD': return msg('activationThreshold');
    case 'ABILITY_MECHANIC_MULTIPLIER': return msg('multiplier');
    case 'ABILITY_MECHANIC_STAT_STAGE': return msg('statStage');
    case 'ABILITY_MECHANIC_STATUS_CURE': return msg('statusCure');
    case 'ABILITY_MECHANIC_TYPE_CHANGE': return msg('typeChange');
    case 'ABILITY_MECHANIC_AI_RATING': return msg('aiRating');
    case 'ABILITY_MECHANIC_FLAG': return msg('attribute');
    case 'ABILITY_MECHANIC_ATTACK': return msg('attack');
    case 'ABILITY_MECHANIC_MOVE_POWER': return msg('movePower');
    case 'ABILITY_MECHANIC_INCOMING_DAMAGE': return msg('incomingDamage');
    case 'ABILITY_MECHANIC_OPPONENT_ATTACK': return msg('opponentAttack');
    case 'ABILITY_MECHANIC_NONVOLATILE_STATUS': return msg('nonvolatileStatus');
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED': return msg('cannotBeCopied');
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SWAPPED': return msg('cannotBeSwapped');
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_TRACED': return msg('cannotBeTraced');
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SUPPRESSED': return msg('cannotBeSuppressed');
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_OVERWRITTEN': return msg('cannotBeOverwritten');
    case 'ABILITY_MECHANIC_FLAG_BREAKABLE': return msg('breakable');
    case 'ABILITY_MECHANIC_FLAG_FAILS_ON_IMPOSTER': return msg('failsOnImposter');
    case 'ABILITY_VALUE_HP_THRESHOLD': return msg('hpThreshold', fraction(message.numerator, message.denominator));
    case 'ABILITY_VALUE_ATTACK_MULTIPLIER': return msg('attackMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER': return msg('grassMovePowerMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER': return msg('fireMovePowerMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER': return msg('waterMovePowerMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER': return msg('bugMovePowerMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER': return msg('incomingDamageMultiplier', multiplier(message.numerator, message.denominator));
    case 'ABILITY_VALUE_STAT_STAGE': return msg('abilityStatStages', signed(message.numerator), pluralCategory(message.numerator ?? 0));
    case 'ABILITY_VALUE_STATUS_CURE_CHANCE': return msg('statusCureChance', fraction(message.numerator, message.denominator));
    case 'ABILITY_VALUE_NORMAL_TO_FAIRY': return msg('normalToFairy');
    case 'ABILITY_VALUE_AI_RATING': return argument(message.conditionValue);
    case 'ABILITY_VALUE_ENABLED': return msg('yes');
    case 'ABILITY_CONDITION_MOVE_SPLIT': return moveSplit(message.conditionValue);
    case 'ABILITY_CONDITION_ATTACKER_STATUS_NON_ZERO': return msg('whileAffectedByStatus');
    case 'ABILITY_CONDITION_SWITCH_IN': return msg('onSwitchIn');
    case 'ABILITY_CONDITION_MOVE_POWER_NON_ZERO': return msg('damagingMoves');
    case 'ABILITY_CONDITION_ATTACKING_MOVE_TYPE': return msg('typeMoves', argument(message.conditionValue));
    case 'DAMAGE_CONDITION_STAB': return msg('damageSameTypeAttackBonus');
    case 'DAMAGE_CONDITION_STATUS': return msg('damageStatus');
    case 'DAMAGE_CONDITION_CRITICAL': return msg('damageCriticalHit');
    case 'DAMAGE_CONDITION_WEATHER': return msg('damageWeather');
    case 'DAMAGE_CONDITION_ABILITY': return msg('damageAbility');
    case 'DAMAGE_CONDITION_ITEM': return msg('damageHeldItem');
    case 'DAMAGE_CONDITION_FIELD': return msg('damageFieldCondition');
    case 'DAMAGE_CONDITION_MULTI_HIT': return msg('damageMultipleHits');
    case 'DAMAGE_CONDITION_FIXED_DAMAGE': return msg('damageFixedDamage');
    case 'DAMAGE_RANGE_BOUNDED': return msg('damageRangeBounded');
    case 'GUIDE_LOAD_FAILED': return msg('guideLoadFailed');
    case 'CATALOG_LOADING_FIRST_PREPARATION': return msg('catalogFirstPreparation');
    case 'CATALOG_LOADING_VERSION_REFRESH': return msg('catalogVersionRefresh');
    case 'CATALOG_LOADING_CACHE_RECOVERY': return msg('catalogCacheRecovery');
    case 'RETROARCH_GAME_OPEN_FAILED': return msg('guideLoadFailed');
    case 'API_SERVER_BUSY': return msg('serverBusy');
    case 'API_METHOD_NOT_ALLOWED': return msg('requestMethodNotAllowed');
    case 'API_REQUEST_TIMEOUT': return msg('requestTimedOut');
    case 'API_GUIDE_LOAD_FAILED': return msg('guideLoadFailed');
    case 'API_INVALID_REQUEST': return msg('invalidRequest');
    case 'API_INTERNAL_ERROR': return msg('serverRequestFailed');
    case 'API_NOT_FOUND': return msg('requestedResourceNotFound');
    case 'API_MAP_UNAVAILABLE': return msg('mapTemporarilyUnavailable');
    case 'PROGRESS_METRIC_PLAY_TIME': return msg('playTime');
    case 'PROGRESS_METRIC_BADGES': return msg('badges');
    case 'PROGRESS_METRIC_DEX_SEEN': return msg('pokedexSeen');
    case 'PROGRESS_METRIC_DEX_CAUGHT': return msg('pokedexCaught');
    case 'PROGRESS_METRIC_MONEY': return msg('money');
    case 'PROGRESS_METRIC_BATTLES': return msg('battles');
    case 'PROGRESS_METRIC_WILD_ENCOUNTERS': return msg('wildEncounters');
    case 'PROGRESS_METRIC_TRAINER_BATTLES': return msg('trainerBattles');
    case 'PROGRESS_METRIC_CAPTURES': return msg('captures');
    case 'PROGRESS_METRIC_EVOLUTIONS': return msg('evolutions');
    case 'PROGRESS_METRIC_AREAS_VISITED': return msg('areasVisited');
    case 'PROGRESS_METRIC_POINTS_DISCOVERED': return msg('pointsDiscovered');
    case 'PROGRESS_METRIC_PARTY_CHANGES': return msg('partyChanges');
    case 'PROGRESS_METRIC_SAVES_OBSERVED': return msg('savesObserved');
    case 'PROGRESS_METRIC_CHALLENGES_COMPLETED': return msg('challengesCompleted');
    case 'TIMELINE_BATTLES': return timelineChange(msg('battles'), message.count);
    case 'TIMELINE_WILD_ENCOUNTERS': return timelineChange(msg('wildEncounters'), message.count);
    case 'TIMELINE_TRAINER_BATTLES': return timelineChange(msg('trainerBattles'), message.count);
    case 'TIMELINE_CAPTURES': return timelineChange(msg('captures'), message.count);
    case 'TIMELINE_EVOLUTIONS': return timelineChange(msg('evolutions'), message.count);
    case 'TIMELINE_AREAS_VISITED': return timelineChange(msg('areasVisited'), message.count);
    case 'TIMELINE_POINTS_DISCOVERED': return timelineChange(msg('pointsDiscovered'), message.count);
    case 'TIMELINE_PARTY_CHANGES': return timelineChange(msg('partyChanges'), message.count);
    case 'TIMELINE_SAVES_OBSERVED': return timelineChange(msg('savesObserved'), message.count);
    case 'TIMELINE_CHALLENGES_COMPLETED': return timelineChange(msg('challengesCompleted'), message.count);
    case 'CHALLENGE_COLLECTION_FIRST_PARTNER_TITLE': return msg('firstPartnerTitle');
    case 'CHALLENGE_COLLECTION_FIRST_PARTNER_DESCRIPTION': return msg('firstPartnerDescription');
    case 'CHALLENGE_COLLECTION_GROWING_ROSTER_TITLE': return msg('growingRosterTitle');
    case 'CHALLENGE_COLLECTION_GROWING_ROSTER_DESCRIPTION': return msg('growingRosterDescription');
    case 'CHALLENGE_PARTY_NEW_FORM_TITLE': return msg('newFormTitle');
    case 'CHALLENGE_PARTY_NEW_FORM_DESCRIPTION': return msg('newFormDescription');
    case 'CHALLENGE_EXPLORATION_OPEN_ROAD_TITLE': return msg('openRoadTitle');
    case 'CHALLENGE_EXPLORATION_OPEN_ROAD_DESCRIPTION': return msg('openRoadDescription');
    case 'CHALLENGE_EXPLORATION_CURIOUS_EYE_TITLE': return msg('curiousEyeTitle');
    case 'CHALLENGE_EXPLORATION_CURIOUS_EYE_DESCRIPTION': return msg('curiousEyeDescription');
    case 'CHALLENGE_BATTLE_SEASONED_TITLE': return msg('seasonedBattlerTitle');
    case 'CHALLENGE_BATTLE_SEASONED_DESCRIPTION': return msg('seasonedBattlerDescription');
    case 'CHALLENGE_PROGRESS_FIRST_BADGE_TITLE': return msg('firstBadgeTitle');
    case 'CHALLENGE_PROGRESS_FIRST_BADGE_DESCRIPTION': return msg('firstBadgeDescription');
    case 'CHALLENGE_PROGRESS_ALL_BADGES_TITLE': return msg('badgeCollectionTitle');
    case 'CHALLENGE_PROGRESS_ALL_BADGES_DESCRIPTION': return msg('badgeCollectionDescription');
    case 'CHALLENGE_COLLECTION_REGIONAL_RECORD_TITLE': return msg('regionalRecordTitle');
    case 'CHALLENGE_COLLECTION_REGIONAL_RECORD_DESCRIPTION': return msg('regionalRecordDescription');
    case 'CHALLENGE_EXPLORATION_AREA_ITEMS_TITLE': return msg('localCollectorTitle');
    case 'CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION': return msg('localCollectorDescription', subject(message.subject));
    case 'CHALLENGE_BATTLE_LEADER_NO_ITEMS_TITLE': return msg('preparedVictoryTitle');
    case 'CHALLENGE_BATTLE_LEADER_NO_ITEMS_DESCRIPTION': return msg('preparedVictoryDescription', subject(message.subject));
    case 'CHALLENGE_SPECIAL_MINIGAME_TITLE': return subject(message.subject);
    case 'CHALLENGE_SPECIAL_MINIGAME_DESCRIPTION': return msg('minigameDescription', subject(message.subject));
  }
}

export function renderPresentationMessages(messages: PresentationMessage[], separator = ' · '): string {
  return messages.map(renderPresentationMessage).join(separator);
}

function argument(value: number | null | undefined): string {
  return value == null ? '—' : formatUiNumber(value);
}

function moveSplit(value: number | null | undefined): string {
  if (value === 0) return msg('physicalMoves');
  if (value === 1) return msg('specialMoves');
  return msg('moveSplit', argument(value));
}

function timelineChange(label: string, count: number | null | undefined): string {
  return msg('timelineChange', label, count == null ? '—' : formatUiNumber(count));
}

function subject(value: string | null | undefined): string {
  return value ?? '—';
}

function fraction(numerator: number | null | undefined, denominator: number | null | undefined): string {
  if (numerator == null || denominator == null) return '—';
  return denominator === 1
    ? formatUiNumber(numerator)
    : `${formatUiNumber(numerator)}/${formatUiNumber(denominator)}`;
}

function multiplier(numerator: number | null | undefined, denominator: number | null | undefined): string {
  if (numerator == null || denominator == null || denominator === 0) return '—';
  return formatUiNumber(numerator / denominator);
}

function signed(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value < 0) return `−${formatUiNumber(Math.abs(value))}`;
  return `+${formatUiNumber(value)}`;
}
