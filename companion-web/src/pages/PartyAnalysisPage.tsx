import type { Catalog, PartyAnalysis, PartyMemberView, State, TypeInfo } from '../models';
import { Header, maskIdentityName, TypeChip } from '../components';
import { formatUiNumber, interfaceLocale, msg, pluralCategory } from '../i18n';

interface PartyAnalysisPageProps {
  catalog: Catalog;
  state: State;
  analysis: PartyAnalysis;
  onBack: () => void;
  openMember: (slot: number) => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openSpecies: (speciesId: number) => void;
}

export function PartyAnalysisPage({ catalog, state, analysis, onBack, openMember, openMove, openAbility, openSpecies }: PartyAnalysisPageProps) {
  const members = new Map((state.party ?? []).filter(member => member.occupied).map(member => [member.slot, member]));
  return <section class="screen party-analysis-screen">
    <Header title={msg('partyAnalysis')} gameTime={state.gameTime} onBack={onBack} />
    <div class="party-analysis-content" data-scroll-region>
      <TeamSummary analysis={analysis} />
      {analysis.offensiveCoverage && <OffensiveCoverage catalog={catalog} analysis={analysis.offensiveCoverage} members={members} openMember={openMember} />}
      {analysis.defensiveProfile && <DefensiveProfile catalog={catalog} analysis={analysis.defensiveProfile} members={members} openMember={openMember} openAbility={openAbility} />}
      <Development catalog={catalog} state={state} analysis={analysis.development} members={members} openMember={openMember} openMove={openMove} openSpecies={openSpecies} />
    </div>
  </section>;
}

function TeamSummary({ analysis }: { analysis: PartyAnalysis }) {
  const summary = analysis.teamSummary;
  const levelSpan = summary.minimumLevel == null
    ? '—'
    : summary.minimumLevel === summary.maximumLevel
      ? msg('levelShort', formatUiNumber(summary.minimumLevel))
      : `${msg('levelShort', formatUiNumber(summary.minimumLevel))}–${formatUiNumber(summary.maximumLevel!)}`;
  return <section class="party-analysis-section party-analysis-summary" aria-labelledby="party-summary-title">
    <h2 id="party-summary-title">{msg('teamSummary')}</h2>
    <div class="party-analysis-facts">
      <Fact value={`${formatUiNumber(summary.partySize)} Pokémon`} label={msg('currentParty')} />
      <Fact value={levelSpan} label={msg('levelSpan')} />
      <Fact value={formatUiNumber(summary.faintedCount)} label={msg('fainted')} />
      <Fact value={formatUiNumber(summary.statusCount)} label={msg('statusConditions')} />
    </div>
    {summary.moveDistribution && <div class="party-move-distribution" aria-label={msg('knownMoveCategories')}>
      <Distribution value={summary.moveDistribution.physical} label={msg('categoryPhysical')} />
      <Distribution value={summary.moveDistribution.special} label={msg('categorySpecial')} />
      <Distribution value={summary.moveDistribution.status} label={msg('categoryStatus')} />
      {summary.moveDistribution.unresolved > 0 && <Distribution value={summary.moveDistribution.unresolved} label={msg('unclassified')} />}
    </div>}
  </section>;
}

function OffensiveCoverage({ catalog, analysis, members, openMember }: { catalog: Catalog; analysis: NonNullable<PartyAnalysis['offensiveCoverage']>; members: Map<number, PartyMemberView>; openMember: (slot: number) => void }) {
  return <section class="party-analysis-section party-analysis-offense" aria-labelledby="party-offense-title">
    <h2 id="party-offense-title">{msg('offensiveCoverage')}</h2>
    <p class="party-analysis-context">{msg('knownDamagingMoves', formatUiNumber(analysis.contributingMoveCount), pluralCategory(analysis.contributingMoveCount))}</p>
    <div class="party-coverage-matrix">
      {analysis.types.map(row => <article key={row.defendingTypeId} class="party-coverage-row" data-outcome={row.outcome}>
        <TypeChip type={typeFor(catalog, row.defendingTypeId)} />
        <span class="party-outcome-mark" aria-hidden="true">{outcomeMark(row.outcome)}</span>
        <strong>{outcomeLabel(row.outcome)}</strong>
        {row.bestMultiplierPercent != null && <small>{formatMultiplier(row.bestMultiplierPercent)}</small>}
        <MemberLinks slots={row.memberSlots} members={members} openMember={openMember} />
      </article>)}
    </div>
  </section>;
}

function DefensiveProfile({ catalog, analysis, members, openMember, openAbility }: { catalog: Catalog; analysis: NonNullable<PartyAnalysis['defensiveProfile']>; members: Map<number, PartyMemberView>; openMember: (slot: number) => void; openAbility: (abilityId: number) => void }) {
  return <section class="party-analysis-section party-analysis-defense" aria-labelledby="party-defense-title">
    <h2 id="party-defense-title">{msg('defensiveProfile')}</h2>
    {analysis.repeatedWeaknesses.length > 0 && <div class="party-repeated-weaknesses" aria-label={msg('repeatedWeaknesses')}>
      {analysis.repeatedWeaknesses.map(item => <span key={item.attackingTypeId}><b>{msg('repeatedWeakness')}</b><TypeChip type={typeFor(catalog, item.attackingTypeId)} /><small>{msg('memberCount', formatUiNumber(item.memberCount), pluralCategory(item.memberCount))}</small></span>)}
    </div>}
    <div class="party-defense-grid">
      {analysis.members.map(item => {
        const member = members.get(item.slot);
        return <article key={item.slot} class="party-defense-member" data-available={item.availableForImmediateBattle ? 'true' : 'false'}>
          <button type="button" class="party-analysis-member" aria-label={msg('openDetails', memberName(member))} onClick={() => openMember(item.slot)}>
            <MemberPortrait member={member} />
            <span><strong>{memberName(member)}</strong>{!item.availableForImmediateBattle && <small>{msg('notBattleReady')}</small>}</span>
          </button>
          <TypeGroup label={msg('weakTo')} typeIds={item.weaknessTypeIds} catalog={catalog} />
          <TypeGroup label={msg('resists')} typeIds={item.resistanceTypeIds} catalog={catalog} />
          <TypeGroup label={msg('immune')} typeIds={item.immunityTypeIds} catalog={catalog} />
          {item.abilityModifiers.map(modifier => {
            const ability = catalog.species.find(species => species.id === item.speciesId)?.abilities.find(candidate => candidate.id === modifier.abilityId);
            const abilityName = ability?.name ?? msg('ability');
            return <button type="button" class="party-ability-modifier" key={`${modifier.abilityId}-${modifier.attackingTypeId}`} aria-label={msg('openAbilityDetails', abilityName)} onClick={() => openAbility(modifier.abilityId)}>
              <strong>{abilityName}</strong><TypeChip type={typeFor(catalog, modifier.attackingTypeId)} /><small>{formatFraction(modifier.numerator, modifier.denominator)}</small>
            </button>;
          })}
        </article>;
      })}
    </div>
  </section>;
}

function Development({ catalog, state, analysis, members, openMember, openMove, openSpecies }: { catalog: Catalog; state: State; analysis: PartyAnalysis['development']; members: Map<number, PartyMemberView>; openMember: (slot: number) => void; openMove: (moveId: number) => void; openSpecies: (speciesId: number) => void }) {
  return <section class="party-analysis-section party-analysis-development" aria-labelledby="party-development-title">
    <h2 id="party-development-title">{msg('development')}</h2>
    <div class="party-development-grid">
      {analysis.evolutionOpportunities.map(item => {
        const target = catalog.species.find(species => species.id === item.targetSpeciesId);
        const targetKnown = state.settings.knowledgeMode !== 'ORGANIC' || Boolean(state.speciesState[item.targetSpeciesId]?.seen || state.speciesState[item.targetSpeciesId]?.caught);
        const targetName = targetKnown ? target?.name ?? msg('evolution') : maskIdentityName(target?.name ?? msg('evolution'));
        const sourceName = memberName(members.get(item.slot));
        return <article class="party-development-card" key={`evolution-${item.slot}-${item.targetSpeciesId}`}>
          <button type="button" class="party-analysis-member compact" aria-label={msg('openDetails', sourceName)} onClick={() => openMember(item.slot)}><MemberPortrait member={members.get(item.slot)} /><span><small>{msg('evolution')}</small><strong>{sourceName}</strong></span></button>
          <span class="party-development-arrow" aria-hidden="true">→</span>
          {targetKnown ? <button type="button" class="party-development-target" aria-label={msg('openPokemonInPokedex', targetName)} onClick={() => openSpecies(item.targetSpeciesId)}><strong>{targetName}</strong><small>{evolutionLabel(item)}</small></button>
            : <span class="party-development-target"><strong>{targetName}</strong><small>{evolutionLabel(item)}</small></span>}
        </article>;
      })}
      {analysis.nearbyMoves.map(item => {
        const move = catalog.moves.find(candidate => candidate.id === item.moveId);
        const sourceName = memberName(members.get(item.slot));
        const moveName = move?.name ?? msg('moveNumber', formatUiNumber(item.moveId));
        return <article class="party-development-card party-nearby-move-card" key={`move-${item.slot}-${item.moveId}-${item.level}`}>
          <button type="button" class="party-analysis-member compact" aria-label={msg('openDetails', sourceName)} onClick={() => openMember(item.slot)}><MemberPortrait member={members.get(item.slot)} /><span><small>{msg('levelsAway', formatUiNumber(item.levelsAway), pluralCategory(item.levelsAway))}</small><strong>{sourceName}</strong></span></button>
          <button type="button" class="party-development-target" aria-label={msg('openMoveDetails', moveName)} onClick={() => openMove(item.moveId)}><strong>{moveName}</strong><small>{msg('levelShort', formatUiNumber(item.level))}</small></button>
        </article>;
      })}
      {analysis.moveRoleGaps.length > 0 && <article class="party-development-card party-role-gaps"><strong>{msg('currentMoveRoles')}</strong>{analysis.moveRoleGaps.map(gap => <span key={gap}>{msg('noDamagingMove', moveRoleLabel(gap))}</span>)}</article>}
      {analysis.evolutionOpportunities.length === 0 && analysis.nearbyMoves.length === 0 && analysis.moveRoleGaps.length === 0 && <p class="party-analysis-empty">{msg('noImmediateChanges')}</p>}
    </div>
  </section>;
}

function Fact({ value, label }: { value: string; label: string }) { return <span><strong>{value}</strong><small>{label}</small></span>; }
function Distribution({ value, label }: { value: number; label: string }) { return <span><b>{formatUiNumber(value)}</b><small>{label}</small></span>; }
function typeFor(catalog: Catalog, id: number): TypeInfo | undefined { return catalog.types.find(type => type.id === id); }
function memberName(member?: PartyMemberView): string { return member?.nickname || member?.speciesName || msg('partyMember'); }
function outcomeMark(outcome: NonNullable<PartyAnalysis['offensiveCoverage']>['types'][number]['outcome']): string { return outcome === 'SUPER_EFFECTIVE' ? '↑' : outcome === 'NEUTRAL_ONLY' ? '•' : '—'; }
function outcomeLabel(outcome: NonNullable<PartyAnalysis['offensiveCoverage']>['types'][number]['outcome']): string { return outcome === 'SUPER_EFFECTIVE' ? msg('superEffective') : outcome === 'NEUTRAL_ONLY' ? msg('neutralOnly') : msg('noEffectiveMove'); }
function formatMultiplier(percent: number): string {
  return `×${formatUiNumber(percent / 100, { maximumFractionDigits: 2 })}`;
}
function formatFraction(numerator: number, denominator: number): string { return denominator > 0 ? formatMultiplier(Math.round(numerator * 100 / denominator)) : ''; }
function evolutionLabel(item: PartyAnalysis['development']['evolutionOpportunities'][number]): string {
  if (item.availableNow === true) return msg('availableNow');
  if (item.availableNow === false) return msg('levelShort', item.parameter == null ? '—' : formatUiNumber(item.parameter));
  return msg('evolutionPath');
}
function moveRoleLabel(role: string): string {
  if (role === 'PHYSICAL') return msg('categoryPhysical').toLocaleLowerCase(interfaceLocale.value);
  if (role === 'SPECIAL') return msg('categorySpecial').toLocaleLowerCase(interfaceLocale.value);
  if (role === 'STATUS') return msg('categoryStatus').toLocaleLowerCase(interfaceLocale.value);
  return role.toLocaleLowerCase(interfaceLocale.value);
}

function MemberPortrait({ member }: { member?: PartyMemberView }) {
  return <span class="party-analysis-portrait">{member?.spriteUrl ? <img src={member.spriteUrl} alt="" /> : <i aria-hidden="true">?</i>}</span>;
}

function MemberLinks({ slots, members, openMember }: { slots: number[]; members: Map<number, PartyMemberView>; openMember: (slot: number) => void }) {
  return <span class="party-analysis-member-links">{slots.map(slot => <button type="button" key={slot} aria-label={msg('openDetails', memberName(members.get(slot)))} onClick={() => openMember(slot)}><MemberPortrait member={members.get(slot)} /></button>)}</span>;
}

function TypeGroup({ label, typeIds, catalog }: { label: string; typeIds: number[]; catalog: Catalog }) {
  if (typeIds.length === 0) return null;
  return <div class="party-defense-types"><small>{label}</small><span>{typeIds.map(typeId => <TypeChip key={typeId} type={typeFor(catalog, typeId)} />)}</span></div>;
}
