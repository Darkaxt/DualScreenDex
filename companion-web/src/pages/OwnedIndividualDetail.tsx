import type { Catalog, OwnedIndividualView, PartyMemberView, TypeInfo } from '../models';
import { DexIcon, TypeChip, uniqueTypeIds } from '../components';
import { natureDetailFor } from '../natureDetails';
import { RarityStars } from './BattlePage';

export type IndividualDetailModel = PartyMemberView | OwnedIndividualView;

export function OwnedIndividualDetail({ individual, catalog, locationLabel, openMove, openAbility, openNature, openSpecies }: {
  individual: IndividualDetailModel;
  catalog: Catalog;
  locationLabel: string;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openNature?: (natureId: number) => void;
  openSpecies?: (speciesId: number) => void;
}) {
  const moves = Array.from({ length: 4 }, (_, slot) => individual.moves.find(move => move.slot === slot) ?? { slot, moveId: null, name: null, currentPp: null, maximumPp: null });
  const types = uniqueTypeIds(individual.typeIds).map(typeId => catalog.types.find(type => type.id === typeId)).filter((type): type is TypeInfo => type != null);
  const knownNature = natureDetailFor(catalog.natures, individual.natureId);
  const natureLabel = knownNature?.name ?? individual.nature ?? (knownNature ? `Nature #${knownNature.id}` : null);
  const speciesLabel = individual.speciesName ?? (individual.speciesId != null ? `Pokémon #${individual.speciesId}` : null);
  const abilityLabel = individual.abilityName ?? (individual.abilityId != null ? `Ability #${individual.abilityId}` : null);
  const ivs = 'ivs' in individual ? individual.ivs : [];
  const dvs = 'dvs' in individual ? individual.dvs : [];
  return <article class="owned-individual-detail party-detail paper-panel" data-condition={individualCondition(individual)}>
    <header>
      <OwnedIndividualSprite individual={individual} large />
      <div><p class="eyebrow">{locationLabel}</p><h2>{individual.nickname || speciesLabel || 'UNKNOWN PARTNER'}</h2>
        <div class="party-detail-meta">
          {individual.rarity && <RarityStars rarity={individual.rarity} />}
          {individual.level != null && <strong>Lv {individual.level}</strong>}
          {individual.currentHp != null && individual.maximumHp != null && <strong>{individual.currentHp} / {individual.maximumHp}</strong>}
          {individual.status && <IndividualStatusArtwork status={individual.status} />}
          {individualCondition(individual) === 'fainted' && <b class="party-fainted-mark">FAINTED</b>}
        </div>
        {types.length > 0 && <div class="party-types" aria-label="Types">{types.map(type => <IndividualTypeArtwork key={type.id} type={type} />)}</div>}
      </div>
      {individual.speciesId != null && openSpecies && <button type="button" class="party-dex-link" aria-label={`Open ${speciesLabel ?? 'partner'} in Pokédex`} onClick={() => openSpecies(individual.speciesId!)}><DexIcon /></button>}
    </header>
    <div class="party-summary-grid">
      <span><small>NATURE</small>{knownNature && openNature ? <button type="button" onClick={() => openNature(knownNature.id)}>{natureLabel}</button> : <strong>{natureLabel ?? '—'}</strong>}</span>
      <span><small>ABILITY</small>{individual.abilityId != null ? <button type="button" onClick={() => openAbility(individual.abilityId!)}>{abilityLabel}</button> : <strong>—</strong>}</span>
      <span><small>HELD ITEM</small><HeldItemArtwork individual={individual} /></span>
      <span><small>EXP TO NEXT</small><strong>{individual.experienceProgress == null ? '—' : `${Math.round(individual.experienceProgress * 100)}%`}</strong></span>
    </div>
    <div class="party-exp" aria-label="Experience progress"><i style={{ width: `${Math.round((individual.experienceProgress ?? 0) * 100)}%` }} /></div>
    <div class="party-stat-grid">{Object.entries(individual.stats).map(([name, value]) => <span key={name}><small>{name}</small><strong>{value}</strong></span>)}{Object.keys(individual.stats).length === 0 && <span><small>STATS</small><strong>—</strong></span>}</div>
    {(ivs.length > 0 || dvs.length > 0) && <div class="individual-innate-grid">
      {ivs.length > 0 && <span><small>IVs</small><strong>{ivs.join(' / ')}</strong></span>}
      {dvs.length > 0 && <span><small>DVs</small><strong>{dvs.join(' / ')}</strong></span>}
    </div>}
    <section class="party-moves"><p class="eyebrow">MOVES</p>{moves.map(move => <div class="party-move-row" key={move.slot}>
      {move.moveId != null ? <button type="button" onClick={() => openMove(move.moveId!)}>{move.name ?? `Move #${move.moveId}`}</button> : <strong>—</strong>}
      <span>PP {move.currentPp == null ? '—' : move.maximumPp == null ? move.currentPp : `${move.currentPp}/${move.maximumPp}`}</span>
    </div>)}</section>
  </article>;
}

export function OwnedIndividualSprite({ individual, large = false }: { individual: IndividualDetailModel; large?: boolean }) {
  const identified = individual.speciesId != null;
  const speciesLabel = individual.speciesName ?? (individual.speciesId != null ? `Pokémon #${individual.speciesId}` : null);
  const occupied = 'occupied' in individual ? individual.occupied : true;
  return <span class={`party-sprite ${large ? 'large' : ''}`} data-artwork={individual.spriteUrl ? identified ? 'portrait' : 'silhouette' : identified ? 'missing' : occupied ? 'silhouette' : 'empty'}>{individual.spriteUrl
    ? <img class={identified ? '' : 'identity-silhouette'} src={individual.spriteUrl} alt={identified ? `${speciesLabel} sprite` : 'Unidentified Pokémon'} />
    : !occupied ? <i class="party-empty-mark" aria-label="Empty party slot" />
      : identified ? <i class="party-art-missing" role="img" aria-label="Party artwork unavailable" />
        : <i class="party-silhouette" role="img" aria-label="Unidentified Pokémon"><span /><b /></i>}</span>;
}

function IndividualTypeArtwork({ type }: { type: TypeInfo }) {
  const style = {
    '--type-fg': type.foreground ?? '#10251e',
    '--type-bg': type.background ?? '#d9e0c9',
    '--type-border': type.border ?? '#6d796d',
  } as Record<string, string>;
  const monogram = Array.from(type.name.trim()).filter(character => /[\p{L}\p{N}]/u.test(character)).slice(0, 2).join('').toUpperCase() || '??';
  return <span class="party-type-art" style={style}><abbr title={type.name} aria-hidden="true">{monogram}</abbr><TypeChip type={type} /></span>;
}

function IndividualStatusArtwork({ status }: { status: string }) {
  const key = statusKey(status);
  const label = STATUS_LABELS[key] ?? `${status} status`;
  return <span class={`party-status-art status-${key}`} role="img" aria-label={label}><i aria-hidden="true">{status}</i></span>;
}

function HeldItemArtwork({ individual }: { individual: IndividualDetailModel }) {
  const hasHeldItem = individual.hasHeldItem ?? (individual.heldItemName ? true : null);
  if (hasHeldItem == null) return <strong class="party-item unavailable">Held item unavailable</strong>;
  if (!hasHeldItem) return <strong class="party-item none">None</strong>;
  return <strong class="party-item held"><svg viewBox="0 0 24 24" role="img" aria-label="Held item present"><path d="M7 8V6a5 5 0 0 1 10 0v2h3v13H4V8Z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>{individual.heldItemName ?? 'Held item'}</strong>;
}

export function individualCondition(individual: IndividualDetailModel): 'healthy' | 'statused' | 'fainted' | 'partial' {
  if (individual.currentHp === 0) return 'fainted';
  if (!individual.spriteUrl || individual.currentHp == null || individual.maximumHp == null || individual.typeIds.length === 0) return 'partial';
  if (individual.status) return 'statused';
  return 'healthy';
}

export function statusKey(status: string): string {
  return status.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/^-|-$/g, '') || 'UNKNOWN';
}

const STATUS_LABELS: Record<string, string> = {
  SLP: 'Asleep', PSN: 'Poisoned', BRN: 'Burned', FRZ: 'Frozen', PAR: 'Paralyzed', TOX: 'Badly poisoned', AILMENT: 'Status condition',
};
