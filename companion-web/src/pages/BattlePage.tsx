import type { Catalog, Move, PresentationMessage, Rarity as RarityModel, State } from '../models';
import { DexIcon, Header, Sprite, StatusMarks, tabPanelAttributes, Tabs, TypeChip, uniqueTypeIds } from '../components';
import { gameplayCopy } from '../gameplayCopy';
import { formatUiNumber, msg, pluralCategory } from '../i18n';
import { renderPresentationMessage } from '../presentationMessages';
import { moveCategoryLabel } from './MoveDetail';

export function BattlePage({ catalog, state, send, openMove, openSpecies }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void; openMove: (moveId: number) => void; openSpecies: (speciesId: number) => void }) {
  const battle = state.battle;
  if (!battle) return null;
  const opponent = battle.opponents[battle.targetIndex];
  const species = catalog.species.find(item => item.id === opponent.speciesId)!;
  const status = state.speciesState[species.id];
  const selectedMove = catalog.moves.find(move => move.id === battle.selectedMoveId);
  const tabs = ['ENTRY', 'ATTACK', 'RARITY', 'MOVES'];
  const disabledTabs = [
    !state.settings.attackEnabled ? 'ATTACK' : null,
    !state.settings.rarityEnabled ? 'RARITY' : null,
    !state.settings.movesEnabled ? 'MOVES' : null,
  ].filter(Boolean) as string[];
  const hidden = state.settings.knowledgeMode === 'HIDDEN';
  const displayTab = hidden || disabledTabs.includes(state.battleTab) ? 'ENTRY' : state.battleTab;
  const manualTargets = battle.opponents.length > 1 && battle.targetMode === 'MANUAL_TARGET_FALLBACK';
  const title = battle.encounterKind === 'WILD'
    ? msg('wildEncounter')
    : battle.encounterKind === 'TRAINER' ? msg('trainerBattle') : msg('encounter');
  const tabLabels = {
    ENTRY: msg('entry'),
    ATTACK: msg('attack'),
    RARITY: msg('rarity'),
    MOVES: msg('moves'),
  };
  return <section class={`screen battle-screen ${manualTargets ? 'battle-double' : 'battle-single'}`}>
    <Header title={title} gameTime={state.gameTime} onSettings={() => send('SCREEN', { screen: 'SETTINGS' })} />
    {manualTargets && <div class="target-switch">{battle.opponents.map((target, index) => {
      const targetSpecies = catalog.species.find(item => item.id === target.speciesId);
      return <button key={`${target.speciesId}-${index}`} aria-pressed={index === battle.targetIndex} class={index === battle.targetIndex ? 'active' : ''} onClick={() => send('TARGET', { index })}>{targetSpecies?.name}<span>{msg('levelShort', formatUiNumber(target.level))}</span></button>;
    })}</div>}
    <div class="battle-identity">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} catalogHash={catalog.hash} large />
      <div class="battle-identity-copy"><small>{msg('target')} · {msg('levelShort', formatUiNumber(opponent.level))}{battle.opponents.length > 1 && battle.targetMode === 'AUTOMATIC' && <span class="automatic-target">{msg('automaticTarget')}</span>}</small><div class="battle-name-row"><h2>{species.name}</h2><RarityStars rarity={opponent.rarity} /><button class="battle-dex-link" aria-label={msg('openPokemonInPokedex', species.name)} onClick={() => openSpecies(species.id)}><DexIcon /></button></div><div class="identity-line"><StatusMarks state={status} catalog={catalog} mode={state.settings.knowledgeMode} />{uniqueTypeIds(opponent.typeIds?.length ? opponent.typeIds : species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    {!hidden && <Tabs values={tabs} active={displayTab} disabledValues={disabledTabs} labels={tabLabels} panelPrefix="battle-information" onSelect={tab => send('TAB', { tab })} label={msg('battleInformation')} />}
    <div class="battle-content" data-scroll-region {...(!hidden ? tabPanelAttributes('battle-information', displayTab) : {})}>
      {(hidden || displayTab === 'ENTRY') && <Entry catalog={catalog} species={species} unlocked={state.settings.knowledgeMode === 'DISCOVERED' || status?.caught} />}
      {!hidden && displayTab === 'ATTACK' && <Attack catalog={catalog} move={selectedMove} state={state} openMove={openMove} />}
      {!hidden && displayTab === 'RARITY' && <Rarity rarity={opponent.rarity} />}
      {!hidden && displayTab === 'MOVES' && <Moves catalog={catalog} moves={opponent.moves} showFrequency={!status?.caught} openMove={openMove} />}
    </div>
  </section>;
}

function Entry({ catalog, species, unlocked }: { catalog: Catalog; species: Catalog['species'][number]; unlocked?: boolean }) {
  return <div class="paper-panel"><p class="eyebrow">{msg('targetEntry')}</p>{unlocked ? <p class="entry-copy">{species.description || gameplayCopy.pokedexUnavailable}</p> : <div class="withheld"><strong>{gameplayCopy.dataUnavailable}</strong><p>{gameplayCopy.catchForEntry}</p></div>}</div>;
}

function Attack({ catalog, move, state, openMove }: { catalog: Catalog; move?: Move; state: State; openMove: (moveId: number) => void }) {
  if (!move) return <div class="empty-state"><strong>{gameplayCopy.noMoveSelected}</strong><p>{gameplayCopy.selectMove}</p></div>;
  const effectCode = state.battle?.effectivenessKnown ? state.battle.effectiveness : null;
  const effect = effectivenessLabel(effectCode);
  const effectClass = effectCode == null ? 'effect-unavailable' : `effect-${effectCode.toLowerCase().replaceAll('_', '-')}`;
  return <div class="attack-card">
    <div class="attack-heading"><div><small>{msg('selectedAttack')}</small><button class="move-link" onClick={() => openMove(move.id)}>{move.name}</button></div><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></div>
    <div class="move-metadata"><span><small>{msg('power')}</small><strong>{move.power ? formatUiNumber(move.power) : '—'}</strong></span><span><small>{msg('precision')}</small><strong>{move.accuracy ? `${formatUiNumber(move.accuracy)}%` : '—'}</strong></span><span><small>PP</small><strong>{move.pp ? formatUiNumber(move.pp) : '—'}</strong></span><span><small>{msg('moveClass')}</small><strong>{moveCategoryLabel(move.category)}</strong></span></div>
    {state.battle?.damageForecast && <DamageForecastPanel forecast={state.battle.damageForecast} />}
    <div class={`effect-result ${effectClass}`}><small>{msg('effectiveness')}</small><strong>{effect}</strong></div>
  </div>;
}

function effectivenessLabel(effect: string | null | undefined): string {
  if (!effect) return '—';
  if (effect === 'SUPER_EFFECTIVE') return msg('superEffective');
  if (effect === 'NOT_VERY_EFFECTIVE') return msg('notVeryEffective');
  if (effect === 'IMMUNE' || effect === 'NO_EFFECT') return msg('noEffect');
  if (effect === 'NEUTRAL') return msg('neutral');
  return effect.replaceAll('_', ' ');
}

function DamageForecastPanel({ forecast }: { forecast: NonNullable<NonNullable<State['battle']>['damageForecast']> }) {
  return <section class="damage-forecast" aria-label={msg('damageForecast')}>
    <div class="damage-forecast-grid">
      <span><small>{msg('damage')}</small><strong>{formatIntegerRange(forecast.minimumHp, forecast.maximumHp)} {msg('statHp')}</strong></span>
      <span><small>{msg('ofTargetHp')}</small><strong>{formatDecimalRange(forecast.minimumTargetPercent, forecast.maximumTargetPercent)}%</strong></span>
      <span><small>{msg('toKnockOut')}</small><strong>{msg('hitCount', formatIntegerRange(forecast.minimumHitsToKnockOut, forecast.maximumHitsToKnockOut), pluralCategory(forecast.maximumHitsToKnockOut))}</strong></span>
      <span><small>{msg('hitChance')}</small><strong>{formatUiNumber(forecast.accuracyPercent)}%</strong></span>
    </div>
    {forecast.conditions.length > 0 && <div class="damage-conditions">{forecast.conditions.map(condition => <span key={condition.code}>{renderDamageMessage(condition)}</span>)}</div>}
    {forecast.confidence === 'BOUNDED' && forecast.uncertainty && <p class="damage-uncertainty">{renderDamageMessage(forecast.uncertainty)}</p>}
  </section>;
}

function renderDamageMessage(message: PresentationMessage): string {
  const labels: Partial<Record<PresentationMessage['code'], string>> = {
    DAMAGE_CONDITION_STAB: msg('sameTypeAttackBonus'),
    DAMAGE_CONDITION_STATUS: msg('statusCondition'),
    DAMAGE_CONDITION_CRITICAL: msg('criticalHit'),
    DAMAGE_CONDITION_WEATHER: msg('weather'),
    DAMAGE_CONDITION_ABILITY: msg('ability'),
    DAMAGE_CONDITION_ITEM: msg('heldItem'),
    DAMAGE_CONDITION_FIELD: msg('fieldCondition'),
    DAMAGE_CONDITION_MULTI_HIT: msg('multipleHits'),
    DAMAGE_CONDITION_FIXED_DAMAGE: msg('fixedDamage'),
    DAMAGE_RANGE_BOUNDED: msg('unresolvedBattleConditions'),
  };
  return labels[message.code] ?? renderPresentationMessage(message);
}

function formatIntegerRange(minimum: number, maximum: number): string {
  return minimum === maximum ? formatUiNumber(minimum) : `${formatUiNumber(minimum)}–${formatUiNumber(maximum)}`;
}

function formatDecimalRange(minimum: number, maximum: number): string {
  const format = (value: number) => formatUiNumber(value, { maximumFractionDigits: 1 });
  return minimum === maximum ? format(minimum) : `${format(minimum)}–${format(maximum)}`;
}

export function RarityStars({ rarity }: { rarity: RarityModel }) {
  if (rarity.stars == null || rarity.innateTier == null) return null;
  const rating = formatStars(rarity.stars);
  const title = rarityTitle(rarity);
  return <div class="rarity-stars" role="img" aria-label={msg('rarityStars', rating, title)}>
    {[0, 1, 2, 3, 4].map(index => {
      const fill = Math.max(0, Math.min(1, rarity.stars! - index));
      return <span class="rarity-star" aria-hidden="true" key={index}>
        <span class="rarity-star-outline">☆</span>
        <span class="rarity-star-fill" style={{ width: `${fill * 100}%` }}>★</span>
      </span>;
    })}
  </div>;
}

function Rarity({ rarity }: { rarity: RarityModel }) {
  const title = rarity.innateTier == null
    ? msg('noRecruitmentReading')
    : rarityTitle(rarity);
  const band = rarityBand(rarity.stars);
  return <div class="rarity-card" data-rarity-band={band}>
    {rarity.stars != null && <RarityStars rarity={rarity} />}
    <small>{msg('recruitmentImpression')}</small>
    <strong>{title}</strong>
    {rarity.stars != null && <p>{rarityAssessment(rarity.stars)}</p>}
  </div>;
}

function rarityBand(stars: number | null): 'unavailable' | 'low' | 'medium' | 'high' | 'exceptional' {
  if (stars == null) return 'unavailable';
  if (stars <= 2) return 'low';
  if (stars <= 3) return 'medium';
  if (stars <= 4) return 'high';
  return 'exceptional';
}

export function rarityAssessment(stars: number): string {
  if (stars <= 1) return msg('rarityAssessmentOne');
  if (stars <= 2) return msg('rarityAssessmentTwo');
  if (stars <= 3) return msg('rarityAssessmentThree');
  if (stars <= 4) return msg('rarityAssessmentFour');
  return msg('rarityAssessmentFive');
}

function formatStars(stars: number): string {
  return formatUiNumber(stars, { maximumFractionDigits: 1 });
}

function rarityTitle(rarity: RarityModel): string {
  return [rarity.relativeTier, rarity.innateTier]
    .filter((tier): tier is NonNullable<typeof tier> => tier != null)
    .map(rarityTierLabel)
    .join(' ');
}

function rarityTierLabel(tier: NonNullable<RarityModel['relativeTier'] | RarityModel['innateTier']>): string {
  const labels = {
    WEAK: msg('rarityWeak'), ORDINARY: msg('rarityOrdinary'), COMPETENT: msg('rarityCompetent'), STRONG: msg('rarityStrong'), MAJOR: msg('rarityMajor'),
    FODDER: msg('rarityFodder'), STANDARD: msg('rarityStandard'), TRAINED: msg('rarityTrained'), VETERAN: msg('rarityVeteran'), ELITE: msg('rarityElite'), ACE: msg('rarityAce'),
  };
  return labels[tier];
}

function Moves({ catalog, moves, showFrequency, openMove }: { catalog: Catalog; moves: { moveId: number; frequency: number }[]; showFrequency: boolean; openMove: (moveId: number) => void }) {
  if (moves.length === 0) return <div class="empty-state"><strong>{gameplayCopy.noMovesRecorded}</strong><p>{gameplayCopy.movesWillAppear}</p></div>;
  return <div class="observed-list">{moves.map(item => {
    const move = catalog.moves.find(candidate => candidate.id === item.moveId);
    const moveName = move?.name ?? msg('moveNumber', formatUiNumber(item.moveId));
    return <button key={item.moveId} onClick={() => openMove(item.moveId)}><TypeChip type={catalog.types.find(type => type.id === move?.typeId)} /><strong>{moveName}</strong>{showFrequency && <span>{msg('frequency', formatUiNumber(item.frequency))}</span>}<small>{move?.power ? msg('movePowerPrecision', formatUiNumber(move.power), move.accuracy == null ? '—' : `${formatUiNumber(move.accuracy)}%`) : moveCategoryLabel(move?.category ?? null)}</small></button>;
  })}</div>;
}
