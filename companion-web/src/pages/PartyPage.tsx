import { useEffect, useMemo, useState } from 'preact/hooks';
import type { Catalog, PartyMemberView, State } from '../models';
import { Header, TypeChip } from '../components';

interface PartyPageProps {
  catalog: Catalog;
  state: State;
  onBack: () => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openSpecies?: (speciesId: number) => void;
  selectedSlot?: number | null;
  onSelectSlot?: (slot: number) => void;
}

export function PartyPage({ catalog, state, onBack, openMove, openAbility, openSpecies, selectedSlot, onSelectSlot }: PartyPageProps) {
  const members = useMemo(() => normalizeParty(state.party), [state.party]);
  const firstOccupied = members.find(member => member.occupied)?.slot ?? null;
  const [localSlot, setLocalSlot] = useState<number | null>(selectedSlot ?? state.selectedPartySlot ?? firstOccupied);
  const requestedSlot = selectedSlot ?? localSlot;
  const activeSlot = members.some(member => member.slot === requestedSlot && member.occupied) ? requestedSlot : firstOccupied;
  const active = activeSlot == null ? null : members[activeSlot];
  const occupancy = members.map(member => member.occupied ? '1' : '0').join('');

  useEffect(() => {
    if (selectedSlot == null && activeSlot !== localSlot) setLocalSlot(activeSlot);
  }, [catalog.hash, occupancy, activeSlot, localSlot, selectedSlot]);

  const select = (slot: number) => {
    if (!members[slot]?.occupied) return;
    setLocalSlot(slot);
    onSelectSlot?.(slot);
  };

  return <section class="screen party-screen">
    <Header title="PARTY" kicker="LIVE · OWNED POKÉMON" onBack={onBack} />
    <div class="party-content" data-scroll-region>
      <div class="party-grid" aria-label="Party slots">
        {members.map(member => <button
          type="button"
          key={member.slot}
          class={`party-slot ${member.slot === activeSlot ? 'active' : ''} ${member.occupied ? '' : 'empty'}`}
          disabled={!member.occupied}
          aria-label={member.occupied ? `Party slot ${member.slot + 1}: ${member.nickname || member.speciesName || 'Unknown partner'}` : `Party slot ${member.slot + 1}: Empty`}
          onClick={() => select(member.slot)}
        >
          <PartySprite member={member} />
          <span><strong>{member.occupied ? member.nickname || member.speciesName || 'UNKNOWN PARTNER' : 'EMPTY'}</strong>
            <small>{member.occupied ? `${member.speciesName ?? 'Species unavailable'}${member.level != null ? ` · Lv ${member.level}` : ''}` : 'OPEN SLOT'}</small>
            {member.occupied && <i>{hpLabel(member)}{member.status ? ` · ${member.status}` : ''}</i>}
          </span>
        </button>)}
      </div>
      {active ? <PartyDetail member={active} catalog={catalog} openMove={openMove} openAbility={openAbility} openSpecies={openSpecies} /> :
        <div class="empty-state party-empty"><strong>NO PARTY DATA</strong><p>The party will appear when a supported live or SaveRAM snapshot is available.</p></div>}
    </div>
  </section>;
}

function PartyDetail({ member, catalog, openMove, openAbility, openSpecies }: { member: PartyMemberView; catalog: Catalog; openMove: (moveId: number) => void; openAbility: (abilityId: number) => void; openSpecies?: (speciesId: number) => void }) {
  const moves = Array.from({ length: 4 }, (_, slot) => member.moves.find(move => move.slot === slot) ?? { slot, moveId: null, name: null, currentPp: null, maximumPp: null });
  return <article class="party-detail paper-panel">
    <header>
      <PartySprite member={member} large />
      <div><p class="eyebrow">SLOT {member.slot + 1}</p><h1>{member.nickname || member.speciesName || 'UNKNOWN PARTNER'}</h1>
        <div class="party-detail-meta">
          {member.level != null && <strong>Lv {member.level}</strong>}
          {member.currentHp != null && member.maximumHp != null && <strong>{member.currentHp} / {member.maximumHp}</strong>}
          {member.status && <b>{member.status}</b>}
        </div>
        <div>{member.typeIds.map(typeId => <TypeChip key={typeId} type={catalog.types.find(type => type.id === typeId)} />)}</div>
      </div>
      {member.speciesId != null && openSpecies && <button type="button" class="party-dex-link" aria-label={`Open ${member.speciesName ?? 'partner'} in Pokédex`} onClick={() => openSpecies(member.speciesId!)}>DEX</button>}
    </header>
    <div class="party-summary-grid">
      <span><small>NATURE</small><strong>{member.nature ?? '—'}</strong></span>
      <span><small>ABILITY</small>{member.abilityId != null && member.abilityName ? <button type="button" onClick={() => openAbility(member.abilityId!)}>{member.abilityName}</button> : <strong>—</strong>}</span>
      <span><small>HELD ITEM</small><strong>{member.heldItemName ?? 'No held item data'}</strong></span>
      <span><small>EXP TO NEXT</small><strong>{member.experienceProgress == null ? '—' : `${Math.round(member.experienceProgress * 100)}%`}</strong></span>
    </div>
    <div class="party-exp" aria-label="Experience progress"><i style={{ width: `${Math.round((member.experienceProgress ?? 0) * 100)}%` }} /></div>
    <div class="party-stat-grid">{Object.entries(member.stats).map(([name, value]) => <span key={name}><small>{name}</small><strong>{value}</strong></span>)}{Object.keys(member.stats).length === 0 && <span><small>STATS</small><strong>—</strong></span>}</div>
    <section class="party-moves"><p class="eyebrow">MOVES</p>{moves.map(move => <div class="party-move-row" key={move.slot}>
      {move.moveId != null && move.name ? <button type="button" onClick={() => openMove(move.moveId!)}>{move.name}</button> : <strong>—</strong>}
      <span>PP {move.currentPp == null ? '—' : move.maximumPp == null ? move.currentPp : `${move.currentPp}/${move.maximumPp}`}</span>
    </div>)}</section>
  </article>;
}

function PartySprite({ member, large = false }: { member: PartyMemberView; large?: boolean }) {
  return <span class={`party-sprite ${large ? 'large' : ''}`}>{member.spriteUrl
    ? <img src={member.spriteUrl} alt={member.speciesName ? `${member.speciesName} sprite` : 'Party Pokémon'} />
    : <i class="party-silhouette" aria-label={member.occupied ? 'Party sprite unavailable' : 'Empty party slot'} />}</span>;
}

function hpLabel(member: PartyMemberView): string {
  if (member.currentHp == null || member.maximumHp == null) return 'HP —';
  return `${member.currentHp} / ${member.maximumHp}`;
}

function normalizeParty(party: PartyMemberView[] | undefined): PartyMemberView[] {
  const bySlot = new Map((party ?? []).filter(member => member.slot >= 0 && member.slot < 6).map(member => [member.slot, member]));
  return Array.from({ length: 6 }, (_, slot) => bySlot.get(slot) ?? {
    slot, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null,
    isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null,
    currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [],
  });
}
