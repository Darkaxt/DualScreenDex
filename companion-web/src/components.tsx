import type { Catalog, SpeciesState, TypeInfo } from './models';

export function Sprite({ speciesId, name, available, large = false }: { speciesId: number; name: string; available: boolean; large?: boolean }) {
  return (
    <div class={`sprite-frame ${large ? 'sprite-large' : ''}`}>
      {available ? <img src={`/api/sprites/species/${speciesId}.png`} alt={`${name} sprite`} /> : <span class="sprite-missing" aria-label="Sprite unavailable" />}
    </div>
  );
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

export function Header({ title, kicker, onBack, onSettings }: { title: string; kicker?: string; onBack?: () => void; onSettings?: () => void }) {
  return (
    <header class="app-header">
      {onBack ? <button class="header-action back-action" onClick={onBack} aria-label="Back"><span /></button> : <span class="header-spacer" />}
      <div class="header-title"><strong>{title}</strong>{kicker && <small>{kicker}</small>}</div>
      {onSettings ? <button class="header-action settings-action" onClick={onSettings} aria-label="Settings">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.64 5.64l1.42 1.42M16.94 16.94l1.42 1.42M18.36 5.64l-1.42 1.42M7.06 16.94l-1.42 1.42" />
          <circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1.5" />
        </svg>
      </button> : <span class="header-spacer" />}
    </header>
  );
}

export function Segmented({ values, active, onSelect, label }: { values: string[]; active: string; onSelect: (value: string) => void; label: string }) {
  return <div class="segmented" role="tablist" aria-label={label}>{values.map(value => (
    <button key={value} role="tab" aria-selected={active === value} class={active === value ? 'active' : ''} onClick={() => onSelect(value)}>{value}</button>
  ))}</div>;
}
