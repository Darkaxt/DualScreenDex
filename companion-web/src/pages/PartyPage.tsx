import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import type { Catalog, PartyMemberView, State } from '../models';
import { Header } from '../components';
import { RarityStars } from './BattlePage';
import { individualCondition, OwnedIndividualDetail, OwnedIndividualSprite, statusKey } from './OwnedIndividualDetail';

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
            class={`party-slot ${member.slot === highlightedSlot ? 'active' : ''} ${member.occupied ? individualCondition(member) : 'empty'}`}
            disabled={!member.occupied}
            aria-label={member.occupied ? `Party slot ${member.slot + 1}: ${accessibleName}` : `Party slot ${member.slot + 1}: Empty`}
            onClick={() => select(member.slot)}
          >
            <OwnedIndividualSprite individual={member} />
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
          <OwnedIndividualDetail individual={active} catalog={catalog} locationLabel={`Party · Slot ${active.slot + 1}`} openMove={openMove} openAbility={openAbility} openNature={openNature} openSpecies={openSpecies} />
        </div>
      </div>}
    </div>
  </section>;
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

function partyGenderMark(gender: string | null): string | null {
  if (!gender) return null;
  const normalized = gender.trim().toUpperCase();
  if (normalized === 'F' || normalized === 'FEMALE') return '♀';
  if (normalized === 'M' || normalized === 'MALE') return '♂';
  return gender;
}

function normalizeParty(party: PartyMemberView[] | undefined): PartyMemberView[] {
  const bySlot = new Map((party ?? []).filter(member => member.slot >= 0 && member.slot < 6).map(member => [member.slot, member]));
  return Array.from({ length: 6 }, (_, slot) => bySlot.get(slot) ?? {
    slot, occupied: false, speciesId: null, speciesName: null, spriteUrl: null, typeIds: [], nickname: null, level: null,
    isEgg: false, gender: null, nature: null, abilityId: null, abilityName: null, heldItemId: null, heldItemName: null, hasHeldItem: null,
    currentHp: null, maximumHp: null, status: null, experienceProgress: null, stats: {}, moves: [],
    rarity: null,
  });
}
