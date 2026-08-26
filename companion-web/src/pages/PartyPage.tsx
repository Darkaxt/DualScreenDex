import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import type { Catalog, PartyMemberView, State, TypeInfo } from '../models';
import { DexIcon, Header, TypeChip, uniqueTypeIds } from '../components';
import { natureDetailFor } from '../natureDetails';
import { RarityStars } from './BattlePage';

interface PartyPageProps {
  catalog: Catalog;
  state: State;
  onBack: () => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openNature?: (natureId: number) => void;
  openSpecies?: (speciesId: number) => void;
  selectedSlot?: number | null;
  onSelectSlot?: (slot: number) => void;
  detailSlot?: number | null;
  onOpenDetails?: (slot: number) => void;
  onCloseDetails?: () => void;
  onOpenAnalysis?: () => void;
  initialScrollTop?: number;
  onScrollTopChange?: (scrollTop: number) => void;
}

export function PartyPage({ catalog, state, onBack, openMove, openAbility, openNature, openSpecies, selectedSlot, onSelectSlot, detailSlot: controlledDetailSlot, onOpenDetails, onCloseDetails, onOpenAnalysis, initialScrollTop = 0, onScrollTopChange }: PartyPageProps) {
  const members = useMemo(() => normalizeParty(state.party), [state.party]);
  const contentRef = useRef<HTMLDivElement>(null);
  const [fallbackDetailSlot, setFallbackDetailSlot] = useState<number | null>(null);
  const controlled = controlledDetailSlot !== undefined;
  const detailSlot = controlled ? controlledDetailSlot : fallbackDetailSlot;
  const highlightedSlot = detailSlot ?? (selectedSlot != null && members[selectedSlot]?.occupied ? selectedSlot : null);
  const active = detailSlot == null || !members[detailSlot]?.occupied ? null : members[detailSlot];
  const occupancy = members.map(member => member.occupied ? '1' : '0').join('');

  useEffect(() => {
    if (detailSlot != null && occupancy[detailSlot] !== '1') {
      if (controlled) onCloseDetails?.();
      else setFallbackDetailSlot(null);
    }
  }, [catalog.hash, detailSlot, occupancy]);

  useEffect(() => {
    if (detailSlot == null) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDetails();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [detailSlot, controlled, onCloseDetails]);

  useEffect(() => {
    if (detailSlot == null) return;
    const closeOnCompanionBack = (event: Event) => {
      if (controlled) return;
      (event as Event & { dualdexHandled?: boolean }).dualdexHandled = true;
      event.preventDefault();
      setFallbackDetailSlot(null);
    };
    window.addEventListener('dualdexback', closeOnCompanionBack);
    return () => window.removeEventListener('dualdexback', closeOnCompanionBack);
  }, [detailSlot, controlled]);

  useEffect(() => {
    if (contentRef.current) contentRef.current.scrollTop = initialScrollTop;
  }, [catalog.hash]);

  const select = (slot: number) => {
    if (!members[slot]?.occupied) return;
    if (controlled) onOpenDetails?.(slot);
    else setFallbackDetailSlot(slot);
    onSelectSlot?.(slot);
  };

  const closeDetails = () => {
    if (controlled) onCloseDetails?.();
    else setFallbackDetailSlot(null);
  };

  return <section class="screen party-screen">
    <Header title="PARTY" onBack={onBack} onAnalysis={onOpenAnalysis} />
    <div ref={contentRef} class="party-content" data-scroll-region onScroll={event => onScrollTopChange?.(event.currentTarget.scrollTop)}>
      <div class="party-grid" data-layout="2x3" aria-label="Party slots">
        {members.map(member => {
          const accessibleName = member.nickname || member.speciesName || 'Unknown partner';
          const displayName = member.nickname || member.speciesName || 'UNKNOWN PARTNER';
          const nicknameDiffers = Boolean(member.nickname && member.speciesName && member.nickname !== member.speciesName);
          const gender = partyGenderMark(member.gender);
          return <button
            type="button"
            key={member.slot}
            class={`party-slot ${member.slot === highlightedSlot ? 'active' : ''} ${member.occupied ? memberCondition(member) : 'empty'}`}
            disabled={!member.occupied}
            aria-label={member.occupied ? `Party slot ${member.slot + 1}: ${accessibleName}` : `Party slot ${member.slot + 1}: Empty`}
            onClick={() => select(member.slot)}
          >
            <PartySprite member={member} />
            {member.occupied && <span class="party-slot-copy">
              <span class="party-slot-heading">
                <strong>{displayName}</strong>
                {gender && <i class="party-slot-gender" aria-label={member.gender ?? undefined}>{gender}</i>}
                {member.level != null && <small class="party-slot-level">Lv {member.level}</small>}
                {member.rarity && <RarityStars rarity={member.rarity} />}
              </span>
              {nicknameDiffers && <span class="party-slot-species">{member.speciesName}</span>}
              <span class="party-slot-bars">
                {partyExperiencePercent(member) != null && <span class="party-exp-track" aria-label={`Experience ${partyExperiencePercent(member)}%`}><b class="party-exp-fill" style={{ width: `${partyExperiencePercent(member)}%` }} /></span>}
                <span class="party-hp-line"><b>HP</b>{partyHpPercent(member) != null && <span class="party-hp-track" aria-label={`HP ${partyHpValue(member)}`}><b class="party-hp-fill" style={{ width: `${partyHpPercent(member)}%` }} /></span>}</span>
                <span class="party-hp-value"><i>{partyHpValue(member)}</i>{member.status && <em class={`party-status-dot status-${statusKey(member.status)}`}>{member.status}</em>}</span>
              </span>
            </span>}
          </button>;
        })}
      </div>
      {!members.some(member => member.occupied) && <div class="empty-state party-empty"><strong>YOUR PARTY IS EMPTY</strong><p>Your Pokémon will appear here when they join the party.</p></div>}
      {active && <div class="party-detail-layer">
        <div class="party-detail-backdrop" onClick={closeDetails} />
        <div class="party-detail-window" role="dialog" aria-modal="true" aria-label={`${active.nickname || active.speciesName || 'Party member'} details`}>
          <button type="button" class="party-detail-close" aria-label={`Close ${active.nickname || active.speciesName || 'party member'} details`} onClick={closeDetails} autoFocus>×</button>
          <PartyDetail member={active} catalog={catalog} openMove={openMove} openAbility={openAbility} openNature={openNature} openSpecies={openSpecies} />
        </div>
      </div>}
    </div>
  </section>;
}

function PartyDetail({ member, catalog, openMove, openAbility, openNature, openSpecies }: { member: PartyMemberView; catalog: Catalog; openMove: (moveId: number) => void; openAbility: (abilityId: number) => void; openNature?: (natureId: number) => void; openSpecies?: (speciesId: number) => void }) {
  const moves = Array.from({ length: 4 }, (_, slot) => member.moves.find(move => move.slot === slot) ?? { slot, moveId: null, name: null, currentPp: null, maximumPp: null });
  const types = uniqueTypeIds(member.typeIds).map(typeId => catalog.types.find(type => type.id === typeId)).filter((type): type is TypeInfo => type != null);
  const knownNature = natureDetailFor(catalog.natures, member.natureId);
  return <article class="party-detail paper-panel" data-condition={memberCondition(member)}>
    <header>
      <PartySprite member={member} large />
      <div><p class="eyebrow">SLOT {member.slot + 1}</p><h1>{member.nickname || member.speciesName || 'UNKNOWN PARTNER'}</h1>
        <div class="party-detail-meta">
          {member.rarity && <RarityStars rarity={member.rarity} />}
          {member.level != null && <strong>Lv {member.level}</strong>}
          {member.currentHp != null && member.maximumHp != null && <strong>{member.currentHp} / {member.maximumHp}</strong>}
          {member.status && <PartyStatusArtwork status={member.status} />}
          {memberCondition(member) === 'fainted' && <b class="party-fainted-mark">FAINTED</b>}
        </div>
        {types.length > 0 && <div class="party-types" aria-label="Types">{types.map(type => <PartyTypeArtwork key={type.id} type={type} />)}</div>}
      </div>
      {member.speciesId != null && openSpecies && <button type="button" class="party-dex-link" aria-label={`Open ${member.speciesName ?? 'partner'} in Pokédex`} onClick={() => openSpecies(member.speciesId!)}><DexIcon /></button>}
    </header>
    <div class="party-summary-grid">
      <span><small>NATURE</small>{knownNature && openNature ? <button type="button" onClick={() => openNature(knownNature.id)}>{knownNature.name}</button> : <strong>{member.nature ?? '—'}</strong>}</span>
      <span><small>ABILITY</small>{member.abilityId != null && member.abilityName ? <button type="button" onClick={() => openAbility(member.abilityId!)}>{member.abilityName}</button> : <strong>—</strong>}</span>
      <span><small>HELD ITEM</small><HeldItemArtwork member={member} /></span>
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
  const identified = member.speciesId != null && member.speciesName != null;
  return <span class={`party-sprite ${large ? 'large' : ''}`} data-artwork={member.spriteUrl ? identified ? 'portrait' : 'silhouette' : identified ? 'missing' : member.occupied ? 'silhouette' : 'empty'}>{member.spriteUrl
    ? <img class={identified ? '' : 'identity-silhouette'} src={member.spriteUrl} alt={identified ? `${member.speciesName} sprite` : 'Unidentified Pokémon'} />
    : !member.occupied ? <i class="party-empty-mark" aria-label="Empty party slot" />
      : identified ? <i class="party-art-missing" role="img" aria-label="Party artwork unavailable" />
        : <i class="party-silhouette" role="img" aria-label="Unidentified Pokémon"><span /><b /></i>}</span>;
}

function PartyTypeArtwork({ type }: { type: TypeInfo }) {
  const style = {
    '--type-fg': type.foreground ?? '#10251e',
    '--type-bg': type.background ?? '#d9e0c9',
    '--type-border': type.border ?? '#6d796d',
  } as Record<string, string>;
  const monogram = Array.from(type.name.trim()).filter(character => /[\p{L}\p{N}]/u.test(character)).slice(0, 2).join('').toUpperCase() || '??';
  return <span class="party-type-art" style={style}><abbr title={type.name} aria-hidden="true">{monogram}</abbr><TypeChip type={type} /></span>;
}

function PartyStatusArtwork({ status }: { status: string }) {
  const key = statusKey(status);
  const label = STATUS_LABELS[key] ?? `${status} status`;
  return <span class={`party-status-art status-${key}`} role="img" aria-label={label}><i aria-hidden="true">{status}</i></span>;
}

function HeldItemArtwork({ member }: { member: PartyMemberView }) {
  const hasHeldItem = member.hasHeldItem ?? (member.heldItemName ? true : null);
  if (hasHeldItem == null) return <strong class="party-item unavailable">Held item unavailable</strong>;
  if (!hasHeldItem) return <strong class="party-item none">None</strong>;
  return <strong class="party-item held"><svg viewBox="0 0 24 24" role="img" aria-label="Held item present"><path d="M7 8V6a5 5 0 0 1 10 0v2h3v13H4V8Z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>{member.heldItemName ?? 'Held item'}</strong>;
}

function hpLabel(member: PartyMemberView): string {
  return `HP ${partyHpValue(member)}`;
}

function partyHpValue(member: PartyMemberView): string {
  if (member.currentHp == null || member.maximumHp == null) return '—';
  return `${member.currentHp} / ${member.maximumHp}`;
}

function partyHpPercent(member: PartyMemberView): number | null {
  if (member.currentHp == null || member.maximumHp == null || member.maximumHp <= 0) return null;
  return Math.round(Math.max(0, Math.min(1, member.currentHp / member.maximumHp)) * 100);
}

function partyExperiencePercent(member: PartyMemberView): number | null {
  if (member.experienceProgress == null || !Number.isFinite(member.experienceProgress)) return null;
  return Math.round(Math.max(0, Math.min(1, member.experienceProgress)) * 100);
}

function memberCondition(member: PartyMemberView): 'healthy' | 'statused' | 'fainted' | 'partial' {
  if (member.currentHp === 0) return 'fainted';
  if (!member.spriteUrl || member.currentHp == null || member.maximumHp == null || member.typeIds.length === 0) return 'partial';
  if (member.status) return 'statused';
  return 'healthy';
}

function statusKey(status: string): string {
  return status.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/^-|-$/g, '') || 'UNKNOWN';
}

function partyGenderMark(gender: string | null): string | null {
  if (!gender) return null;
  const normalized = gender.trim().toUpperCase();
  if (normalized === 'F' || normalized === 'FEMALE') return '♀';
  if (normalized === 'M' || normalized === 'MALE') return '♂';
  return gender;
}

const STATUS_LABELS: Record<string, string> = {
  SLP: 'Asleep', PSN: 'Poisoned', BRN: 'Burned', FRZ: 'Frozen', PAR: 'Paralyzed', TOX: 'Badly poisoned', AILMENT: 'Status condition',
};

function normalizeParty(party: PartyMemberView[] | undefined): PartyMemberView[] {
  const bySlot = new Map((party ?? []).filter(member => member.slot >= 0 && member.slot < 6).map(member => [member.slot, member]));
  return Array.from({ length: 6 }, (_, slot) => bySlot.get(slot) ?? {
    slot, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null,
    isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, hasHeldItem: null,
    currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [],
    rarity: null,
  });
}
