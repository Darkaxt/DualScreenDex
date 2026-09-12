import type { Catalog, OwnedIndividualView, PartyMemberView, StatName, TypeInfo } from '../models';
import { DexIcon, TypeChip, uniqueTypeIds } from '../components';
import { formatUiNumber, msg } from '../i18n';
import { natureDetailFor, statLabel } from '../natureDetails';
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
  const natureLabel = knownNature?.name ?? individual.nature ?? (knownNature ? msg('natureNumber', formatUiNumber(knownNature.id)) : null);
  const speciesLabel = individual.speciesName ?? (individual.speciesId != null ? msg('pokemonNumber', formatUiNumber(individual.speciesId)) : null);
  const abilityLabel = individual.abilityName ?? (individual.abilityId != null ? msg('abilityNumber', formatUiNumber(individual.abilityId)) : null);
  const ivs = 'ivs' in individual ? individual.ivs : [];
  const dvs = 'dvs' in individual ? individual.dvs : [];
  const experiencePercent = Math.round((individual.experienceProgress ?? 0) * 100);
  return <article class="owned-individual-detail party-detail paper-panel" data-condition={individualCondition(individual)}>
    <header>
      <OwnedIndividualSprite individual={individual} large />
      <div><p class="eyebrow">{locationLabel}</p><h2>{individual.nickname || speciesLabel || msg('unknownPartner')}</h2>
        <div class="party-detail-meta">
          {individual.rarity && <RarityStars rarity={individual.rarity} />}
          {individual.level != null && <strong>{msg('levelShort', formatUiNumber(individual.level))}</strong>}
          {individual.currentHp != null && individual.maximumHp != null && <strong>{formatUiNumber(individual.currentHp)} / {formatUiNumber(individual.maximumHp)}</strong>}
          {individual.status && <IndividualStatusArtwork status={individual.status} />}
          {individualCondition(individual) === 'fainted' && <b class="party-fainted-mark">{msg('fainted')}</b>}
        </div>
        {types.length > 0 && <div class="party-types" aria-label={msg('types')}>{types.map(type => <IndividualTypeArtwork key={type.id} type={type} />)}</div>}
      </div>
      {individual.speciesId != null && openSpecies && <button type="button" class="party-dex-link" aria-label={msg('openPokemonInPokedex', speciesLabel ?? msg('unknownPartnerLabel'))} onClick={() => openSpecies(individual.speciesId!)}><DexIcon /></button>}
    </header>
    <div class="party-summary-grid">
      <span><small>{msg('nature')}</small>{knownNature && openNature ? <button type="button" onClick={() => openNature(knownNature.id)}>{natureLabel}</button> : <strong>{natureLabel ?? '—'}</strong>}</span>
      <span><small>{msg('ability')}</small>{individual.abilityId != null ? <button type="button" onClick={() => openAbility(individual.abilityId!)}>{abilityLabel}</button> : <strong>—</strong>}</span>
      <span><small>{msg('heldItem')}</small><HeldItemArtwork individual={individual} /></span>
      <span><small>{msg('experienceToNext')}</small><strong>{individual.experienceProgress == null ? '—' : `${formatUiNumber(experiencePercent)}%`}</strong></span>
    </div>
    <div class="party-exp" aria-label={msg('experienceProgress')}><i style={{ width: `${experiencePercent}%` }} /></div>
    <div class="party-stat-grid">{Object.entries(individual.stats).map(([name, value]) => <span key={name}><small>{statLabel(name as StatName)}</small><strong>{formatUiNumber(value)}</strong></span>)}{Object.keys(individual.stats).length === 0 && <span><small>{msg('stats')}</small><strong>—</strong></span>}</div>
    {(ivs.length > 0 || dvs.length > 0) && <div class="individual-innate-grid">
      {ivs.length > 0 && <span><small>IVs</small><strong>{ivs.map(value => formatUiNumber(value)).join(' / ')}</strong></span>}
      {dvs.length > 0 && <span><small>DVs</small><strong>{dvs.map(value => formatUiNumber(value)).join(' / ')}</strong></span>}
    </div>}
    <section class="party-moves"><p class="eyebrow">{msg('moves')}</p>{moves.map(move => <div class="party-move-row" key={move.slot}>
      {move.moveId != null ? <button type="button" onClick={() => openMove(move.moveId!)}>{move.name ?? msg('moveNumber', formatUiNumber(move.moveId))}</button> : <strong>—</strong>}
      <span>PP {move.currentPp == null ? '—' : move.maximumPp == null ? formatUiNumber(move.currentPp) : `${formatUiNumber(move.currentPp)}/${formatUiNumber(move.maximumPp)}`}</span>
    </div>)}</section>
  </article>;
}

export function OwnedIndividualSprite({ individual, large = false }: { individual: IndividualDetailModel; large?: boolean }) {
  const identified = individual.speciesId != null;
  const speciesLabel = individual.speciesName ?? (individual.speciesId != null ? msg('pokemonNumber', formatUiNumber(individual.speciesId)) : null);
  const occupied = 'occupied' in individual ? individual.occupied : true;
  return <span class={`party-sprite ${large ? 'large' : ''}`} data-artwork={individual.spriteUrl ? identified ? 'portrait' : 'silhouette' : identified ? 'missing' : occupied ? 'silhouette' : 'empty'}>{individual.spriteUrl
    ? <img class={identified ? '' : 'identity-silhouette'} src={individual.spriteUrl} alt={identified ? msg('pokemonSprite', speciesLabel!) : msg('unidentifiedPokemon')} />
    : !occupied ? <i class="party-empty-mark" aria-label={msg('emptyPartySlot')} />
      : identified ? <i class="party-art-missing" role="img" aria-label={msg('partyArtworkUnavailable')} />
        : <i class="party-silhouette" role="img" aria-label={msg('unidentifiedPokemon')}><span /><b /></i>}</span>;
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
  const labels: Record<string, string> = {
    SLP: msg('asleep'),
    PSN: msg('poisoned'),
    BRN: msg('burned'),
    FRZ: msg('frozen'),
    PAR: msg('paralyzed'),
    TOX: msg('badlyPoisoned'),
    AILMENT: msg('statusCondition'),
  };
  const label = labels[key] ?? msg('statusLabel', status);
  return <span class={`party-status-art status-${key}`} role="img" aria-label={label}><i aria-hidden="true">{status}</i></span>;
}

function HeldItemArtwork({ individual }: { individual: IndividualDetailModel }) {
  const hasHeldItem = individual.hasHeldItem ?? (individual.heldItemName ? true : null);
  if (hasHeldItem == null) return <strong class="party-item unavailable">{msg('heldItemUnavailable')}</strong>;
  if (!hasHeldItem) return <strong class="party-item none">{msg('none')}</strong>;
  return <strong class="party-item held"><svg viewBox="0 0 24 24" role="img" aria-label={msg('heldItemPresent')}><path d="M7 8V6a5 5 0 0 1 10 0v2h3v13H4V8Z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>{individual.heldItemName ?? msg('heldItemGeneric')}</strong>;
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
