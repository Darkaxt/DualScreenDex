import type { Catalog, KnowledgeMode, SpeciesState, TypeInfo } from './models';

export type SpeciesIdentityKnowledge = 'unknown' | 'seen' | 'captured';

export function speciesIdentityKnowledge(mode: KnowledgeMode, state?: SpeciesState): SpeciesIdentityKnowledge {
  if (mode !== 'ORGANIC') return 'captured';
  if (state?.caught) return 'captured';
  if (state?.seen) return 'seen';
  return 'unknown';
}

export function identitySpriteClass(knowledge: SpeciesIdentityKnowledge): string {
  return knowledge === 'unknown' ? 'identity-silhouette' : knowledge === 'seen' ? 'identity-seen' : '';
}

export function Sprite({ speciesId, name, available, large = false, knowledge = 'captured' }: { speciesId: number; name: string; available: boolean; large?: boolean; knowledge?: SpeciesIdentityKnowledge }) {
  return (
    <div class={`sprite-frame ${large ? 'sprite-large' : ''}`}>
      {available ? <img class={identitySpriteClass(knowledge)} src={`/api/sprites/species/${speciesId}.png`} alt={knowledge === 'unknown' ? 'Unidentified Pokémon' : `${name} sprite`} /> : <span class="sprite-missing" aria-label="Sprite unavailable" />}
    </div>
  );
}

export function maskIdentityName(name: string): string {
  return Array.from(name).map(character => /\s/u.test(character) ? character : '?').join('');
}

export function TypeChip({ type }: { type?: TypeInfo }) {
  if (!type) return null;
  const style = {
    '--type-fg': type.foreground ?? '#10251e',
    '--type-bg': type.background ?? '#d9e0c9',
    '--type-border': type.border ?? '#6d796d'
  } as Record<string, string>;
  return <span class="type-chip" style={style}>{type.name}</span>;
}

export function uniqueTypeIds(typeIds: number[]): number[] {
  return typeIds.filter((id, index) => typeIds.indexOf(id) === index);
}

export function StatusMarks({ state, catalog }: { state?: SpeciesState; catalog: Catalog }) {
  const caught = state?.caught ?? false;
  const seen = state?.seen ?? false;
  const ball = state?.ballId != null && catalog.balls.some(item => item.id === state.ballId && item.hasSprite);
  return (
    <span class="status-marks">
      <EyeStatus seen={seen} />
      {caught && ball ? (
        <img class="ball-art" src={`/api/sprites/balls/${state!.ballId}.png`} alt="Caught" />
      ) : (
        <span class={`ball-mark ${caught ? 'ball-caught' : ''}`} aria-label={caught ? 'Caught' : 'Not caught'}><i /></span>
      )}
    </span>
  );
}

export function EyeStatus({ seen }: { seen: boolean }) {
  return <svg class="eye-icon" viewBox="0 0 24 24" role="img" aria-label={seen ? 'Seen' : 'Not seen'}>
    <path d="M2.5 12s3.6-6 9.5-6 9.5 6 9.5 6-3.6 6-9.5 6-9.5-6-9.5-6Z" />
    <circle cx="12" cy="12" r="2.7" />
    {!seen && <line x1="4" y1="3.5" x2="20" y2="20.5" />}
  </svg>;
}

export function MapIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="map">
    <path d="m3 6 7-3 8 3 7-3v19l-7 3-8-3-7 3Z" />
    <path d="M10 3v19M18 6v19" />
  </svg>;
}

export function DexIcon() {
  return <svg viewBox="0 0 28 28" shape-rendering="crispEdges" aria-hidden="true" data-semantic-icon="pokedex">
    <path class="dex-shell" d="M3 3h17v3h4v19H3z" />
    <path class="dex-screen" d="M7 11h13v8H7z" />
    <path class="dex-hinge" d="M20 6h4M20 9h4M20 22h4" />
    <circle class="dex-lens" cx="9" cy="7" r="2" />
    <path class="dex-detail" d="M9 14h6v2H9zM7 22h4M14 22h6" />
  </svg>;
}

function SettingsIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.64 5.64l1.42 1.42M16.94 16.94l1.42 1.42M18.36 5.64l-1.42 1.42M7.06 16.94l-1.42 1.42" />
    <circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1.5" />
  </svg>;
}

function TrainerIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="trainer">
    <rect x="3" y="5" width="22" height="18" rx="2" />
    <circle cx="10" cy="12" r="3" />
    <path d="M6 19c.8-2.8 2.2-4 4-4s3.2 1.2 4 4M17 10h5M17 14h5M17 18h3" />
  </svg>;
}

function PartyIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="party">
    <circle cx="9" cy="10" r="5" /><circle cx="19" cy="17" r="5" />
    <path d="M4 10h10M9 5v10M14 17h10M19 12v10" />
  </svg>;
}

export function Header({ title, kicker, onBack, onSettings, onMap, onTrainer, onParty }: { title: string; kicker?: string; onBack?: () => void; onSettings?: () => void; onMap?: () => void; onTrainer?: () => void; onParty?: () => void }) {
  const hasActions = Boolean(onTrainer || onParty || onMap || onSettings);
  return (
    <header class="app-header">
      {onBack ? <button class="header-action back-action" onClick={onBack} aria-label="Back"><span /></button> : <span class="header-spacer" />}
      <div class="header-title"><strong>{title}</strong>{kicker && <small>{kicker}</small>}</div>
      {hasActions ? <div class="header-actions">
        {onTrainer && <button class="header-action trainer-action" onClick={onTrainer} aria-label="Trainer Card"><TrainerIcon /></button>}
        {onParty && <button class="header-action party-action" onClick={onParty} aria-label="Party"><PartyIcon /></button>}
        {onMap && <button class="header-action map-action" onClick={onMap} aria-label="Open Map"><MapIcon /></button>}
        {onSettings && <button class="header-action settings-action" onClick={onSettings} aria-label="Settings"><SettingsIcon /></button>}
      </div> : <span class="header-spacer" />}
    </header>
  );
}

export function Segmented({ values, active, onSelect, label, disabledValues = [] }: { values: string[]; active: string; onSelect: (value: string) => void; label: string; disabledValues?: string[] }) {
  return <div class="segmented" role="tablist" aria-label={label}>{values.map(value => (
    <button key={value} role="tab" aria-selected={active === value} class={active === value ? 'active' : ''} disabled={disabledValues.includes(value)} onClick={() => onSelect(value)}>{value}</button>
  ))}</div>;
}
