import { createContext, type ComponentChildren } from 'preact';
import { useContext, useEffect, useRef } from 'preact/hooks';
import { GameClockIndicator } from './GameClockIndicator';
import { catalogMediaUrl } from './media';
import type { Catalog, GameTime, KnowledgeMode, SpeciesState, TypeInfo } from './models';

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

export function Sprite({ speciesId, name, available, catalogHash, large = false, knowledge = 'captured' }: { speciesId: number; name: string; available: boolean; catalogHash: string; large?: boolean; knowledge?: SpeciesIdentityKnowledge }) {
  return (
    <div class={`sprite-frame ${large ? 'sprite-large' : ''}`}>
      {available ? <img loading="lazy" decoding="async" class={identitySpriteClass(knowledge)} src={catalogMediaUrl(`/api/sprites/species/${speciesId}.png`, catalogHash)} alt={knowledge === 'unknown' ? 'Unidentified Pokémon' : `${name} sprite`} /> : <span class="sprite-missing" aria-label="Sprite unavailable" />}
    </div>
  );
}

export function PokedexAvatar({ speciesId, name, available, state, catalog, large = false, knowledge = 'captured' }: { speciesId: number; name: string; available: boolean; state?: SpeciesState; catalog: Catalog; large?: boolean; knowledge?: SpeciesIdentityKnowledge }) {
  return <span class={`pokedex-avatar ${large ? 'pokedex-avatar-large' : ''}`}>
    <Sprite speciesId={speciesId} name={name} available={available} catalogHash={catalog.hash} large={large} knowledge={knowledge} />
    <CaughtBadge state={state} catalog={catalog} />
  </span>;
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

export function StatusMarks({ state, catalog, mode }: { state?: SpeciesState; catalog: Catalog; mode: KnowledgeMode }) {
  const seen = state?.seen ?? false;
  void catalog;
  if (mode === 'ORGANIC') return null;
  return <span class="status-marks"><EyeStatus seen={seen} /></span>;
}

export function CaughtBadge({ state, catalog }: { state?: SpeciesState; catalog: Catalog }) {
  const caught = state?.caught ?? false;
  if (!caught) return null;
  const ball = state?.ballId != null && catalog.balls.some(item => item.id === state.ballId && item.hasSprite);
  return <span class="caught-avatar-badge">{ball
    ? <img loading="lazy" decoding="async" class="ball-art" src={catalogMediaUrl(`/api/sprites/balls/${state!.ballId}.png`, catalog.hash)} alt="Caught" />
    : <span class="ball-mark ball-caught" aria-label="Caught"><i /></span>}
  </span>;
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

export function AreaGuideIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="area-guide">
    <path d="M4 5.5h7.5c1.4 0 2.5.8 2.5 2.1 0-1.3 1.1-2.1 2.5-2.1H24v17h-7.5c-1.4 0-2.5.7-2.5 1.8 0-1.1-1.1-1.8-2.5-1.8H4Z" />
    <path d="M14 7.6v16.7M7.5 10h4M7.5 13h4M16.5 10h4M16.5 13h4M16.5 16h3" />
  </svg>;
}

export function FilterIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true" data-semantic-icon="filter">
    <path d="M3 5h18l-7 8v5.5l-4 2V13Z" />
  </svg>;
}

export function DexIcon() {
  return <svg class="dex-icon" viewBox="0 0 28 28" shape-rendering="geometricPrecision" aria-hidden="true" data-semantic-icon="pokedex">
    <path class="dex-shell" d="M4 3.5h15.5v3H24v18H4z" />
    <path class="dex-screen" d="M7 11h10.5v7H7z" />
    <path class="dex-hinge" d="M19.5 6.5H24M19.5 9.5H24M19.5 21.5H24" />
    <circle class="dex-lens" cx="8.5" cy="7.3" r="1.7" />
    <path class="dex-detail" d="M9 13.5h6.5M9 16h4M7 21.5h4M14 21.5h4" />
  </svg>;
}

export function SettingsIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true" data-semantic-icon="settings">
    <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.64 5.64l1.42 1.42M16.94 16.94l1.42 1.42M18.36 5.64l-1.42 1.42M7.06 16.94l-1.42 1.42" />
    <circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1.5" />
  </svg>;
}

export function TrainerCardIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="trainer-card">
    <rect x="3" y="5" width="22" height="18" rx="2" />
    <circle cx="10" cy="12" r="3" />
    <path d="M6 19c.8-2.8 2.2-4 4-4s3.2 1.2 4 4M17 10h5M17 14h5M17 18h3" />
  </svg>;
}

export function ProgressTrophyIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="trainer-progress">
    <path d="M9 4h10v4.5c0 4.2-2 7-5 7s-5-2.8-5-7Z" />
    <path d="M9 6H5v2.5c0 2.5 1.7 4.2 4.5 4.5M19 6h4v2.5c0 2.5-1.7 4.2-4.5 4.5M14 15.5V21M10 24h8M11 21h6" />
  </svg>;
}

function PartyIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="party">
    <circle class="party-ball-body" cx="14" cy="14" r="10.5" />
    <path class="party-ball-upper" d="M3.5 14a10.5 10.5 0 0 1 21 0Z" />
    <path class="party-ball-divider" d="M3.5 14h21" />
    <g class="party-ball-button">
      <circle class="party-ball-button-ring" cx="14" cy="14" r="3.6" />
      <circle class="party-ball-button-center" cx="14" cy="14" r="1.55" />
    </g>
  </svg>;
}

function AnalysisIcon() {
  return <svg viewBox="0 0 28 28" aria-hidden="true" data-semantic-icon="analysis">
    <path d="M4 23V12h5v11M11.5 23V5h5v18M19 23v-8h5v8M3 23.5h22" />
  </svg>;
}

export const RouteHeadingFocusContext = createContext(true);

export function Header({ title, kicker, gameTime, onBack, onSettings, onMap, onTrainer, onParty, onAnalysis, actions, focusKey, focusHeading = true }: {
  title: string;
  kicker?: string;
  gameTime?: GameTime | null;
  onBack?: () => void;
  onSettings?: () => void;
  onMap?: () => void;
  onTrainer?: () => void;
  onParty?: () => void;
  onAnalysis?: () => void;
  actions?: ComponentChildren;
  focusKey?: string | number;
  focusHeading?: boolean;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const mountedRef = useRef(false);
  const previousFocusKeyRef = useRef(focusKey);
  const allowRouteHeadingFocus = useContext(RouteHeadingFocusContext);
  const hasActions = Boolean(actions || onTrainer || onParty || onMap || onSettings || onAnalysis);

  useEffect(() => {
    const firstRender = !mountedRef.current;
    const focusKeyChanged = mountedRef.current && previousFocusKeyRef.current !== focusKey;
    if (allowRouteHeadingFocus && focusHeading && (firstRender || focusKeyChanged)) {
      headingRef.current?.focus();
    }
    mountedRef.current = true;
    previousFocusKeyRef.current = focusKey;
  }, [allowRouteHeadingFocus, focusHeading, focusKey]);

  return (
    <header class={`app-header ${onBack ? '' : 'app-header-root'}`}>
      {onBack ? <button class="header-action back-action" onClick={onBack} aria-label="Back"><span /></button> : <span class="header-spacer" />}
      <div class="header-title"><h1 ref={headingRef} tabIndex={-1}>{title}</h1>{kicker && <small>{kicker}</small>}</div>
      {gameTime && <GameClockIndicator clock={gameTime} />}
      {hasActions ? <div class="header-actions">
        {actions}
        {onAnalysis && <button class="header-action analysis-action" onClick={onAnalysis} aria-label="Party Analysis"><AnalysisIcon /></button>}
        {onTrainer && <button class="header-action trainer-action" onClick={onTrainer} aria-label="Trainer Card"><TrainerCardIcon /></button>}
        {onParty && <button class="header-action party-action" onClick={onParty} aria-label="Party"><PartyIcon /></button>}
        {onMap && <button class="header-action map-action" onClick={onMap} aria-label="Open Map"><MapIcon /></button>}
        {onSettings && <button class="header-action settings-action" onClick={onSettings} aria-label="Settings"><SettingsIcon /></button>}
      </div> : <span class="header-spacer" />}
    </header>
  );
}

interface ChoiceProps {
  values: string[];
  active: string;
  onSelect: (value: string) => void;
  label: string;
  disabledValues?: string[];
}

export function SegmentedChoice({ values, active, onSelect, label, disabledValues = [] }: ChoiceProps) {
  return <div class="segmented" role="group" aria-label={label}>{values.map(value => {
    const disabled = disabledValues.includes(value);
    return <button
      type="button"
      key={value}
      aria-pressed={active === value}
      aria-disabled={disabled || undefined}
      class={active === value ? 'active' : ''}
      onClick={() => { if (!disabled) onSelect(value); }}
    >{value}</button>;
  })}</div>;
}

export function Segmented(props: ChoiceProps) {
  return <SegmentedChoice {...props} />;
}

export function Tabs({ values, active, onSelect, label, disabledValues = [], columns = values.length, panelPrefix }: ChoiceProps & {
  columns?: number;
  panelPrefix: string;
}) {
  const buttonsRef = useRef<Array<HTMLButtonElement | null>>([]);
  const enabledIndexes = values.flatMap((value, index) => disabledValues.includes(value) ? [] : [index]);

  const activate = (index: number) => {
    if (!enabledIndexes.includes(index)) return;
    buttonsRef.current[index]?.focus();
    onSelect(values[index]);
  };
  const move = (current: number, delta: number) => {
    if (enabledIndexes.length === 0) return;
    let index = current;
    do index = (index + delta + values.length) % values.length;
    while (!enabledIndexes.includes(index) && index !== current);
    activate(index);
  };
  const onKeyDown = (event: KeyboardEvent, index: number) => {
    let handled = true;
    if (event.key === 'ArrowRight') move(index, 1);
    else if (event.key === 'ArrowLeft') move(index, -1);
    else if (event.key === 'ArrowDown') move(index, Math.max(1, columns));
    else if (event.key === 'ArrowUp') move(index, -Math.max(1, columns));
    else if (event.key === 'Home') activate(enabledIndexes[0]);
    else if (event.key === 'End') activate(enabledIndexes.at(-1)!);
    else handled = false;
    if (handled) event.preventDefault();
  };

  return <div class="segmented" role="tablist" aria-label={label}>{values.map((value, index) => {
    const id = tabId(panelPrefix, value);
    const disabled = disabledValues.includes(value);
    return <button
      type="button"
      key={value}
      id={`${id}-tab`}
      ref={element => { buttonsRef.current[index] = element; }}
      role="tab"
      tabIndex={active === value ? 0 : -1}
      aria-selected={active === value}
      aria-disabled={disabled || undefined}
      disabled={disabled}
      aria-controls={`${id}-panel`}
      class={active === value ? 'active' : ''}
      onClick={() => { if (!disabled) onSelect(value); }}
      onKeyDown={event => onKeyDown(event, index)}
    >{value}</button>;
  })}</div>;
}

export function tabPanelAttributes(panelPrefix: string, value: string) {
  const id = tabId(panelPrefix, value);
  return {
    id: `${id}-panel`,
    role: 'tabpanel' as const,
    'aria-labelledby': `${id}-tab`,
    tabIndex: 0,
  };
}

function tabId(prefix: string, value: string) {
  return `${prefix}-${value.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
}

export function Dialog({ label, closeLabel, onClose, restoreFocus, children }: {
  label: string;
  closeLabel: string;
  onClose: () => void;
  restoreFocus?: HTMLElement | null;
  children: ComponentChildren;
}) {
  const layerRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const layer = layerRef.current;
    const host = layer?.parentElement;
    if (!layer || !host) return;
    const background = Array.from(host.children).filter(element => element !== layer);
    const feedback = host.closest('.device-screen')?.querySelector(':scope > .global-feedback');
    if (feedback && !background.includes(feedback)) background.push(feedback);
    const prior = background.map(element => ({
      element,
      inert: element.getAttribute('inert'),
      ariaHidden: element.getAttribute('aria-hidden'),
    }));
    for (const element of background) {
      element.setAttribute('inert', '');
      element.setAttribute('aria-hidden', 'true');
    }
    closeRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = dialogFocusableElements(layer);
      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable.at(-1)!;
      if (event.shiftKey && (document.activeElement === first || !layer.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !layer.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      for (const { element, inert, ariaHidden } of prior) {
        if (inert == null) element.removeAttribute('inert'); else element.setAttribute('inert', inert);
        if (ariaHidden == null) element.removeAttribute('aria-hidden'); else element.setAttribute('aria-hidden', ariaHidden);
      }
      if (restoreFocus?.isConnected) restoreFocus.focus();
    };
  }, [restoreFocus]);

  return <div ref={layerRef} class="party-detail-layer">
    <div class="party-detail-backdrop" onClick={onClose} />
    <div class="party-detail-window" role="dialog" aria-modal="true" aria-label={label}>
      <button ref={closeRef} type="button" class="party-detail-close" aria-label={closeLabel} onClick={onClose}>×</button>
      {children}
    </div>
  </div>;
}

function dialogFocusableElements(host: HTMLElement) {
  return Array.from(host.querySelectorAll<HTMLElement>(
    'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )).filter(element => !element.hasAttribute('aria-hidden'));
}
