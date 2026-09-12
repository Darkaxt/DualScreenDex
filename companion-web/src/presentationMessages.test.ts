import { describe, expect, it } from 'vitest';
import { renderPresentationMessage, renderPresentationMessages } from './presentationMessages';

describe('presentation messages', () => {
  it('renders typed arguments without parsing backend prose', () => {
    expect(renderPresentationMessages([
      { code: 'MOVE_INITIAL' },
      { code: 'MOVE_LEVEL', level: 7 },
    ])).toBe('Initial · Lv 7');
    expect(renderPresentationMessage({ code: 'EVOLUTION_UNKNOWN', methodId: 99, parameter: 4 }))
      .toBe('Method 99 · parameter 4');
    expect(renderPresentationMessage({ code: 'SPECIMEN_BOX_SLOT', boxNumber: 2, slotNumber: 5 }))
      .toBe('Box 2 · Slot 5');
  });

  it('renders every closed damage condition', () => {
    expect([
      'DAMAGE_CONDITION_STAB',
      'DAMAGE_CONDITION_STATUS',
      'DAMAGE_CONDITION_CRITICAL',
      'DAMAGE_CONDITION_WEATHER',
      'DAMAGE_CONDITION_ABILITY',
      'DAMAGE_CONDITION_ITEM',
      'DAMAGE_CONDITION_FIELD',
      'DAMAGE_CONDITION_MULTI_HIT',
      'DAMAGE_CONDITION_FIXED_DAMAGE',
    ].map(code => renderPresentationMessage({ code } as Parameters<typeof renderPresentationMessage>[0])))
      .toEqual([
        'Same-type attack bonus',
        'Status',
        'Critical hit',
        'Weather',
        'Ability',
        'Held item',
        'Field condition',
        'Multiple hits',
        'Fixed damage',
      ]);
  });

  it('renders ability condition values semantically', () => {
    expect(renderPresentationMessage({ code: 'ABILITY_CONDITION_MOVE_SPLIT', conditionValue: 0 }))
      .toBe('Physical moves');
    expect(renderPresentationMessage({ code: 'ABILITY_CONDITION_MOVE_SPLIT', conditionValue: 1 }))
      .toBe('Special moves');
    expect(renderPresentationMessage({ code: 'ABILITY_CONDITION_ATTACKING_MOVE_TYPE', conditionValue: 4 }))
      .toBe('Type 4 moves');
  });

  it('renders ability mechanics from typed arguments', () => {
    expect(renderPresentationMessage({ code: 'ABILITY_VALUE_HP_THRESHOLD', numerator: 1, denominator: 3 }))
      .toBe('HP ≤ 1/3');
    expect(renderPresentationMessage({ code: 'ABILITY_VALUE_FIRE_MOVE_POWER_MULTIPLIER', numerator: 3, denominator: 2 }))
      .toBe('Fire move power ×1.5');
    expect(renderPresentationMessage({ code: 'ABILITY_VALUE_STAT_STAGE', numerator: -1, denominator: 1 }))
      .toBe('−1 stage');
    expect(renderPresentationMessage({ code: 'ABILITY_MECHANIC_FLAG_CANNOT_BE_COPIED' }))
      .toBe('Cannot be copied');
    expect(renderPresentationMessage({ code: 'ABILITY_VALUE_AI_RATING', conditionValue: -2 }))
      .toBe('-2');
  });

  it('renders operational failures without backend prose', () => {
    expect(renderPresentationMessage({ code: 'GUIDE_LOAD_FAILED' }))
      .toBe('This game guide could not be opened. You can try again.');
    expect(renderPresentationMessage({ code: 'CATALOG_LOADING_VERSION_REFRESH' }))
      .toBe('Saved guide data needs to be refreshed for this version.');
    expect(renderPresentationMessage({ code: 'API_INVALID_REQUEST' }))
      .toBe('The request was invalid.');
  });

  it('renders progress and challenge arguments without backend prose', () => {
    expect(renderPresentationMessage({ code: 'PROGRESS_METRIC_DEX_SEEN' })).toBe('Pokédex seen');
    expect(renderPresentationMessage({ code: 'TIMELINE_CAPTURES', count: 2 })).toBe('Captures +2');
    expect(renderPresentationMessage({
      code: 'CHALLENGE_EXPLORATION_AREA_ITEMS_DESCRIPTION',
      subject: 'Viridian Forest',
    })).toBe('Collect every resolved item in Viridian Forest.');
    expect(renderPresentationMessage({ code: 'CHALLENGE_SPECIAL_MINIGAME_TITLE', subject: 'bug catching' }))
      .toBe('bug catching');
  });
});
