import type { PresentationMessage } from './models';

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
