import type { PresentationMessage } from './models';
import { formatUiNumber, msg } from './i18n';

export function renderPresentationMessage(message: PresentationMessage): string {
  switch (message.code) {
    case 'MOVE_INITIAL': return 'Initial';
    case 'MOVE_LEVEL': return `Lv ${argument(message.level)}`;
    case 'RULESET_DEFAULT': return 'Default';
    case 'RULESET_BASE': return 'Base';
    case 'RULESET_EXPANDED': return `Expanded ${argument(message.index)}`;
    case 'RULESET_OTHER': return 'Other';
    case 'EVOLUTION_LEVEL': return `Level ${argument(message.level)}`;
    case 'EVOLUTION_TRADE': return 'Trade';
    case 'EVOLUTION_TRADE_WITH_ITEM': return `Trade with item ${argument(message.itemId)}`;
    case 'EVOLUTION_USE_ITEM': return `Use item ${argument(message.itemId)}`;
    case 'EVOLUTION_HIGH_FRIENDSHIP': return 'High friendship';
    case 'EVOLUTION_UNKNOWN': return `Method ${argument(message.methodId)} · parameter ${argument(message.parameter)}`;
    case 'SPECIMEN_PARTY_SLOT': return `Party · Slot ${argument(message.slotNumber)}`;
    case 'SPECIMEN_BOX_SLOT': return `Box ${argument(message.boxNumber)} · Slot ${argument(message.slotNumber)}`;
    case 'ABILITY_MECHANIC_BEHAVIOR': return 'Effect';
    case 'ABILITY_MECHANIC_ACTIVATION_THRESHOLD': return 'Activation threshold';
    case 'ABILITY_MECHANIC_MULTIPLIER': return 'Multiplier';
    case 'ABILITY_MECHANIC_STAT_STAGE': return 'Stat stage';
    case 'ABILITY_MECHANIC_STATUS_CURE': return 'Status cure';
    case 'ABILITY_MECHANIC_TYPE_CHANGE': return 'Type change';
    case 'ABILITY_MECHANIC_AI_RATING': return 'AI rating';
    case 'ABILITY_MECHANIC_FLAG': return 'Attribute';
    case 'ABILITY_MECHANIC_ATTACK': return 'Attack';
    case 'ABILITY_MECHANIC_MOVE_POWER': return 'Move power';
    case 'ABILITY_MECHANIC_INCOMING_DAMAGE': return 'Incoming damage';
    case 'ABILITY_MECHANIC_OPPONENT_ATTACK': return "Opponents' Attack";
    case 'ABILITY_MECHANIC_NONVOLATILE_STATUS': return 'Nonvolatile status';
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED': return 'Cannot be copied';
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SWAPPED': return 'Cannot be swapped';
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_TRACED': return 'Cannot be traced';
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_SUPPRESSED': return 'Cannot be suppressed';
    case 'ABILITY_MECHANIC_FLAG_CANNOT_BE_OVERWRITTEN': return 'Cannot be overwritten';
    case 'ABILITY_MECHANIC_FLAG_BREAKABLE': return 'Breakable';
    case 'ABILITY_MECHANIC_FLAG_FAILS_ON_IMPOSTER': return 'Fails on Imposter';
    case 'ABILITY_VALUE_HP_THRESHOLD': return `HP ≤ ${fraction(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_ATTACK_MULTIPLIER': return `Attack ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_GRASS_MOVE_POWER_MULTIPLIER': return `Grass move power ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER': return `Fire move power ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_WATER_MOVE_POWER_MULTIPLIER': return `Water move power ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_BUG_MOVE_POWER_MULTIPLIER': return `Bug move power ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_INCOMING_DAMAGE_MULTIPLIER': return `Incoming damage ×${multiplier(message.numerator, message.denominator)}`;
    case 'ABILITY_VALUE_STAT_STAGE': return `${signed(message.numerator)} ${message.numerator === 1 || message.numerator === -1 ? 'stage' : 'stages'}`;
    case 'ABILITY_VALUE_STATUS_CURE_CHANCE': return `${fraction(message.numerator, message.denominator)} chance to cure`;
    case 'ABILITY_VALUE_NORMAL_TO_FAIRY': return 'Normal → Fairy';
    case 'ABILITY_VALUE_AI_RATING': return argument(message.conditionValue);
    case 'ABILITY_VALUE_ENABLED': return 'Yes';
    case 'ABILITY_CONDITION_MOVE_SPLIT': return moveSplit(message.conditionValue);
    case 'ABILITY_CONDITION_ATTACKER_STATUS_NON_ZERO': return 'While affected by status';
    case 'ABILITY_CONDITION_SWITCH_IN': return 'On switch-in';
    case 'ABILITY_CONDITION_MOVE_POWER_NON_ZERO': return 'Damaging moves';
    case 'ABILITY_CONDITION_ATTACKING_MOVE_TYPE': return `Type ${argument(message.conditionValue)} moves`;
    case 'DAMAGE_CONDITION_STAB': return 'Same-type attack bonus';
    case 'DAMAGE_CONDITION_STATUS': return 'Status';
    case 'DAMAGE_CONDITION_CRITICAL': return 'Critical hit';
    case 'DAMAGE_CONDITION_WEATHER': return 'Weather';
    case 'DAMAGE_CONDITION_ABILITY': return 'Ability';
    case 'DAMAGE_CONDITION_ITEM': return 'Held item';
    case 'DAMAGE_CONDITION_FIELD': return 'Field condition';
    case 'DAMAGE_CONDITION_MULTI_HIT': return 'Multiple hits';
    case 'DAMAGE_CONDITION_FIXED_DAMAGE': return 'Fixed damage';
    case 'DAMAGE_RANGE_BOUNDED': return 'The exact result depends on unresolved battle conditions.';
    case 'GUIDE_LOAD_FAILED': return 'This game guide could not be opened. You can try again.';
    case 'CATALOG_LOADING_FIRST_PREPARATION': return 'Preparing your game guide for the first time.';
    case 'CATALOG_LOADING_VERSION_REFRESH': return 'Saved guide data needs to be refreshed for this version.';
    case 'CATALOG_LOADING_CACHE_RECOVERY': return 'Saved guide data could not be reopened, so it is being prepared again.';
    case 'RETROARCH_GAME_OPEN_FAILED': return 'This game guide could not be opened. You can try again.';
    case 'API_SERVER_BUSY': return 'The server is busy. Try again.';
    case 'API_METHOD_NOT_ALLOWED': return 'The request method is not allowed.';
    case 'API_REQUEST_TIMEOUT': return 'The request timed out.';
    case 'API_GUIDE_LOAD_FAILED': return 'This game guide could not be opened. You can try again.';
    case 'API_INVALID_REQUEST': return 'The request was invalid.';
    case 'API_INTERNAL_ERROR': return 'The server could not complete the request.';
    case 'API_NOT_FOUND': return 'The requested resource was not found.';
    case 'API_MAP_UNAVAILABLE': return 'The map is temporarily unavailable. Try again.';
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
  return value == null ? '—' : String(value);
}

function moveSplit(value: number | null | undefined): string {
  if (value === 0) return 'Physical moves';
  if (value === 1) return 'Special moves';
  return `Move split ${argument(value)}`;
}

function timelineChange(label: string, count: number | null | undefined): string {
  return msg('timelineChange', label, count == null ? '—' : formatUiNumber(count));
}

function subject(value: string | null | undefined): string {
  return value ?? '—';
}

function fraction(numerator: number | null | undefined, denominator: number | null | undefined): string {
  if (numerator == null || denominator == null) return '—';
  return denominator === 1 ? String(numerator) : `${numerator}/${denominator}`;
}

function multiplier(numerator: number | null | undefined, denominator: number | null | undefined): string {
  if (numerator == null || denominator == null || denominator === 0) return '—';
  return String(numerator / denominator);
}

function signed(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value < 0) return `−${Math.abs(value)}`;
  return `+${value}`;
}
