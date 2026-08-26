import type { Catalog, PartyAnalysis, PartyMemberView, State, TypeInfo } from '../models';
import { Header, maskIdentityName, TypeChip } from '../components';

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
    <Header title="PARTY ANALYSIS" onBack={onBack} />
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
  const levelSpan = summary.minimumLevel == null ? '—' : summary.minimumLevel === summary.maximumLevel ? `Lv ${summary.minimumLevel}` : `Lv ${summary.minimumLevel}–${summary.maximumLevel}`;
  return <section class="party-analysis-section party-analysis-summary" aria-labelledby="party-summary-title">
    <h2 id="party-summary-title">TEAM SUMMARY</h2>
    <div class="party-analysis-facts">
      <Fact value={`${summary.partySize} ${summary.partySize === 1 ? 'Pokémon' : 'Pokémon'}`} label="Current party" />
      <Fact value={levelSpan} label="Level span" />
      <Fact value={String(summary.faintedCount)} label="Fainted" />
      <Fact value={String(summary.statusCount)} label="Status conditions" />
    </div>
    {summary.moveDistribution && <div class="party-move-distribution" aria-label="Known move categories">
      <Distribution value={summary.moveDistribution.physical} label="Physical" />
      <Distribution value={summary.moveDistribution.special} label="Special" />
      <Distribution value={summary.moveDistribution.status} label="Status" />
      {summary.moveDistribution.unresolved > 0 && <Distribution value={summary.moveDistribution.unresolved} label="Unclassified" />}
    </div>}
  </section>;
}

function OffensiveCoverage({ catalog, analysis, members, openMember }: { catalog: Catalog; analysis: NonNullable<PartyAnalysis['offensiveCoverage']>; members: Map<number, PartyMemberView>; openMember: (slot: number) => void }) {
  return <section class="party-analysis-section party-analysis-offense" aria-labelledby="party-offense-title">
    <h2 id="party-offense-title">OFFENSIVE COVERAGE</h2>
    <p class="party-analysis-context">{analysis.contributingMoveCount} known damaging {analysis.contributingMoveCount === 1 ? 'move' : 'moves'}</p>
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
    <h2 id="party-defense-title">DEFENSIVE PROFILE</h2>
    {analysis.repeatedWeaknesses.length > 0 && <div class="party-repeated-weaknesses" aria-label="Repeated weaknesses">
      {analysis.repeatedWeaknesses.map(item => <span key={item.attackingTypeId}><b>Repeated weakness</b><TypeChip type={typeFor(catalog, item.attackingTypeId)} /><small>{item.memberCount} {item.memberCount === 1 ? 'member' : 'members'}</small></span>)}
    </div>}
    <div class="party-defense-grid">
      {analysis.members.map(item => {
        const member = members.get(item.slot);
        return <article key={item.slot} class="party-defense-member" data-available={item.availableForImmediateBattle ? 'true' : 'false'}>
          <button type="button" class="party-analysis-member" aria-label={`Open ${memberName(member)} details`} onClick={() => openMember(item.slot)}>
            <MemberPortrait member={member} />
            <span><strong>{memberName(member)}</strong>{!item.availableForImmediateBattle && <small>Not battle-ready</small>}</span>
          </button>
          <TypeGroup label="Weak to" typeIds={item.weaknessTypeIds} catalog={catalog} />
          <TypeGroup label="Resists" typeIds={item.resistanceTypeIds} catalog={catalog} />
          <TypeGroup label="Immune" typeIds={item.immunityTypeIds} catalog={catalog} />
          {item.abilityModifiers.map(modifier => {
            const ability = catalog.species.find(species => species.id === item.speciesId)?.abilities.find(candidate => candidate.id === modifier.abilityId);
            return <button type="button" class="party-ability-modifier" key={`${modifier.abilityId}-${modifier.attackingTypeId}`} aria-label={`Open ${ability?.name ?? 'ability'} ability`} onClick={() => openAbility(modifier.abilityId)}>
              <strong>{ability?.name ?? 'Ability'}</strong><TypeChip type={typeFor(catalog, modifier.attackingTypeId)} /><small>{formatFraction(modifier.numerator, modifier.denominator)}</small>
            </button>;
          })}
        </article>;
      })}
    </div>
  </section>;
}

function Development({ catalog, state, analysis, members, openMember, openMove, openSpecies }: { catalog: Catalog; state: State; analysis: PartyAnalysis['development']; members: Map<number, PartyMemberView>; openMember: (slot: number) => void; openMove: (moveId: number) => void; openSpecies: (speciesId: number) => void }) {
  return <section class="party-analysis-section party-analysis-development" aria-labelledby="party-development-title">
    <h2 id="party-development-title">DEVELOPMENT</h2>
    <div class="party-development-grid">
      {analysis.evolutionOpportunities.map(item => {
        const target = catalog.species.find(species => species.id === item.targetSpeciesId);
        const targetKnown = state.settings.knowledgeMode !== 'ORGANIC' || Boolean(state.speciesState[item.targetSpeciesId]?.seen || state.speciesState[item.targetSpeciesId]?.caught);
        const targetName = targetKnown ? target?.name ?? 'Evolution' : maskIdentityName(target?.name ?? 'Evolution');
        return <article class="party-development-card" key={`evolution-${item.slot}-${item.targetSpeciesId}`}>
          <button type="button" class="party-analysis-member compact" aria-label={`Open ${memberName(members.get(item.slot))} details`} onClick={() => openMember(item.slot)}><MemberPortrait member={members.get(item.slot)} /><span><small>Evolution</small><strong>{memberName(members.get(item.slot))}</strong></span></button>
          <span class="party-development-arrow" aria-hidden="true">→</span>
          {targetKnown ? <button type="button" class="party-development-target" aria-label={`Open ${targetName} in Pokédex`} onClick={() => openSpecies(item.targetSpeciesId)}><strong>{targetName}</strong><small>{evolutionLabel(item)}</small></button>
            : <span class="party-development-target"><strong>{targetName}</strong><small>{evolutionLabel(item)}</small></span>}
        </article>;
      })}
      {analysis.nearbyMoves.map(item => {
        const move = catalog.moves.find(candidate => candidate.id === item.moveId);
        return <article class="party-development-card" key={`move-${item.slot}-${item.moveId}-${item.level}`}>
          <button type="button" class="party-analysis-member compact" aria-label={`Open ${memberName(members.get(item.slot))} details`} onClick={() => openMember(item.slot)}><MemberPortrait member={members.get(item.slot)} /><span><small>In {item.levelsAway} {item.levelsAway === 1 ? 'level' : 'levels'}</small><strong>{memberName(members.get(item.slot))}</strong></span></button>
          <button type="button" class="party-development-target" aria-label={`Open ${move?.name ?? 'move'} move`} onClick={() => openMove(item.moveId)}><strong>{move?.name ?? 'Move'}</strong><small>Lv {item.level}</small></button>
        </article>;
      })}
      {analysis.moveRoleGaps.length > 0 && <article class="party-development-card party-role-gaps"><strong>Current move roles</strong>{analysis.moveRoleGaps.map(gap => <span key={gap}>No damaging {gap.toLowerCase()} move</span>)}</article>}
      {analysis.evolutionOpportunities.length === 0 && analysis.nearbyMoves.length === 0 && analysis.moveRoleGaps.length === 0 && <p class="party-analysis-empty">No immediate changes in the current level range.</p>}
    </div>
  </section>;
}

function Fact({ value, label }: { value: string; label: string }) { return <span><strong>{value}</strong><small>{label}</small></span>; }
function Distribution({ value, label }: { value: number; label: string }) { return <span><b>{value}</b><small>{label}</small></span>; }
function typeFor(catalog: Catalog, id: number): TypeInfo | undefined { return catalog.types.find(type => type.id === id); }
function memberName(member?: PartyMemberView): string { return member?.nickname || member?.speciesName || 'Party member'; }
function outcomeMark(outcome: NonNullable<PartyAnalysis['offensiveCoverage']>['types'][number]['outcome']): string { return outcome === 'SUPER_EFFECTIVE' ? '↑' : outcome === 'NEUTRAL_ONLY' ? '•' : '—'; }
function outcomeLabel(outcome: NonNullable<PartyAnalysis['offensiveCoverage']>['types'][number]['outcome']): string { return outcome === 'SUPER_EFFECTIVE' ? 'Super effective' : outcome === 'NEUTRAL_ONLY' ? 'Neutral only' : 'No effective move'; }
function formatMultiplier(percent: number): string { return `×${Number.isInteger(percent / 100) ? percent / 100 : (percent / 100).toFixed(2).replace(/0+$/, '')}`; }
function formatFraction(numerator: number, denominator: number): string { return denominator > 0 ? formatMultiplier(Math.round(numerator * 100 / denominator)) : ''; }
function evolutionLabel(item: PartyAnalysis['development']['evolutionOpportunities'][number]): string { return item.availableNow === true ? 'Available now' : item.availableNow === false ? `Level ${item.parameter}` : 'Evolution path'; }

function MemberPortrait({ member }: { member?: PartyMemberView }) {
  return <span class="party-analysis-portrait">{member?.spriteUrl ? <img src={member.spriteUrl} alt="" /> : <i aria-hidden="true">?</i>}</span>;
}

function MemberLinks({ slots, members, openMember }: { slots: number[]; members: Map<number, PartyMemberView>; openMember: (slot: number) => void }) {
  return <span class="party-analysis-member-links">{slots.map(slot => <button type="button" key={slot} aria-label={`Open ${memberName(members.get(slot))} details`} onClick={() => openMember(slot)}><MemberPortrait member={members.get(slot)} /></button>)}</span>;
}

function TypeGroup({ label, typeIds, catalog }: { label: string; typeIds: number[]; catalog: Catalog }) {
  if (typeIds.length === 0) return null;
  return <div class="party-defense-types"><small>{label}</small><span>{typeIds.map(typeId => <TypeChip key={typeId} type={typeFor(catalog, typeId)} />)}</span></div>;
}
