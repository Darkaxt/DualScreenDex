import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalog, State } from '../models';
import { Header, SegmentedChoice } from '../components';

export const SETTINGS_CATEGORIES = [
  { id: 'GENERAL', label: 'General', description: 'Game and saved preferences' },
  { id: 'CONNECTION', label: 'Connection', description: 'RetroArch and save data' },
  { id: 'DISPLAY', label: 'Display', description: 'Theme, mode, and companion screen' },
  { id: 'INFORMATION', label: 'Information', description: 'Guide, map, move, and battle data' },
  { id: 'ACCESSIBILITY', label: 'Accessibility', description: 'Text, density, and contrast' },
  { id: 'BEHAVIOR', label: 'Behavior', description: 'Automatic companion actions' },
  { id: 'ADVANCED', label: 'Advanced', description: 'Diagnostics and maintenance' },
] as const;

export type SettingsCategory = typeof SETTINGS_CATEGORIES[number]['id'];
export type SettingsControl = 'MOVE_LIST';

interface SettingsPageProps {
  catalog: Catalog | null;
  state: State;
  send: (type: string, values?: Record<string, string | number | boolean | null>) => void;
  onUpload: (file: File) => void;
  onOpenCapabilities?: () => void;
  mapperAvailable?: boolean;
  onOpenMapper?: () => void;
  initialCategory?: SettingsCategory;
  initialControl?: SettingsControl;
  category?: SettingsCategory | null;
  onCategoryChange?: (category: SettingsCategory | null) => void;
  onBack?: () => void;
}

export function SettingsPage({
  catalog,
  state,
  send,
  onUpload,
  onOpenCapabilities = () => undefined,
  mapperAvailable = false,
  onOpenMapper = () => undefined,
  initialCategory,
  initialControl,
  category: controlledCategory,
  onCategoryChange,
  onBack,
}: SettingsPageProps) {
  const [localCategory, setLocalCategory] = useState<SettingsCategory | null>(initialCategory ?? null);
  const contentRef = useRef<HTMLDivElement>(null);
  const viewHeadingRef = useRef<HTMLHeadingElement>(null);
  const previousCategoryRef = useRef<SettingsCategory | null | undefined>(undefined);
  const categoryButtonRefs = useRef<Partial<Record<SettingsCategory, HTMLButtonElement>>>({});
  const moveListRef = useRef<HTMLSelectElement>(null);
  const focusedControlRef = useRef<SettingsControl | null>(null);
  const category = controlledCategory === undefined ? localCategory : controlledCategory;
  const settings = state.settings;
  const poiPreferences = state.localMapPoiPreferences ?? {
    showPlaces: true,
    showServices: true,
    showAvailableItems: true,
    showCollectedItems: true,
    showUnknownPois: true,
    iconZoomThresholdPercent: 0,
    labelZoomThresholdPercent: 0,
  };
  const update = (values: Record<string, string | number | boolean>) => send('SETTINGS', values);
  const updatePoi = (values: Record<string, string | number | boolean>) => send('MAP_POI_SETTINGS', values);
  const activeCategory = SETTINGS_CATEGORIES.find(candidate => candidate.id === category);

  const setCategory = (next: SettingsCategory | null) => {
    if (controlledCategory === undefined) setLocalCategory(next);
    onCategoryChange?.(next);
  };

  useEffect(() => {
    if (controlledCategory !== undefined || initialCategory === undefined) return;
    setLocalCategory(initialCategory);
    focusedControlRef.current = null;
  }, [controlledCategory, initialCategory, initialControl]);

  useEffect(() => {
    const previousCategory = previousCategoryRef.current;
    previousCategoryRef.current = category;
    if (previousCategory === undefined || previousCategory === category) return;
    if (contentRef.current) contentRef.current.scrollTop = 0;
    if (category == null && previousCategory != null) {
      categoryButtonRefs.current[previousCategory]?.focus();
      return;
    }
    if (category !== 'INFORMATION' || initialControl !== 'MOVE_LIST') {
      viewHeadingRef.current?.focus();
    }
  }, [category, initialControl]);

  useEffect(() => {
    if (category !== 'INFORMATION' || initialControl !== 'MOVE_LIST' || focusedControlRef.current === initialControl) return;
    moveListRef.current?.focus();
    focusedControlRef.current = initialControl;
  }, [category, initialControl]);

  const handleBack = () => {
    if (category) {
      setCategory(null);
      return;
    }
    if (onBack) onBack();
    else send('SCREEN', { screen: state.settingsReturnScreen });
  };

  return <section class="screen settings-screen">
    <Header title={activeCategory?.label.toUpperCase() ?? 'SETTINGS'} gameTime={state.gameTime} focusHeading={initialControl == null} onBack={handleBack} currentDestination="SETTINGS" />
    <div ref={contentRef} class="settings-content" data-scroll-region>
      <h2 ref={viewHeadingRef} class="settings-view-heading" tabIndex={-1}>{activeCategory?.label ?? 'Settings categories'}</h2>
      {category == null && <nav class="settings-category-list" aria-label="Settings categories">
        {SETTINGS_CATEGORIES.map(item => <button
          type="button"
          class="settings-category-row"
          key={item.id}
          ref={element => {
            if (element) categoryButtonRefs.current[item.id] = element;
            else delete categoryButtonRefs.current[item.id];
          }}
          aria-labelledby={`settings-category-${item.id.toLowerCase()}`}
          aria-describedby={`settings-category-${item.id.toLowerCase()}-description`}
          onClick={() => setCategory(item.id)}
        >
          <span><strong id={`settings-category-${item.id.toLowerCase()}`}>{item.label}</strong><small id={`settings-category-${item.id.toLowerCase()}-description`}>{item.description}</small></span>
          <i aria-hidden="true" />
        </button>)}
      </nav>}

      {category === 'GENERAL' && <>
        <section class="setting-group rom-setting"><p class="eyebrow">GAME</p><p class="setting-note rom-setting-name">Choose another game without changing your display preferences.</p><label class="settings-upload routine-action"><span>CHANGE ROM OR ZIP</span><input aria-label="Change ROM or ZIP" type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label></section>
        <section class="setting-group"><p class="eyebrow">PREFERENCES</p><p class="setting-note">{catalog ? 'These choices are saved for the current game.' : 'No game is open. These choices become your defaults.'}</p></section>
      </>}

      {category === 'CONNECTION' && <>
        <section class="setting-group retroarch-setting"><div><p class="eyebrow">RETROARCH</p><p class="setting-note">{retroArchConnectionLabel(state.retroArch?.connection)}</p></div><button type="button" class="primary-action" onClick={() => send('SCREEN', { screen: 'SETUP' })}>RETROARCH SETUP</button></section>
        <section class="setting-group save-setting"><div><p class="eyebrow">SAVE DATA</p><p class="setting-note"><strong>{saveDataLabel(state.saveRam?.status)}</strong></p></div>{state.saveRam?.candidates?.length ? <div class="save-candidates">{state.saveRam.candidates.map((candidate, index) => <button type="button" key={candidate.id} onClick={() => send('SELECT_SAVE', { documentId: candidate.id })}><strong>SAVE {index + 1}</strong><small>{saveFileName(candidate.path)}</small></button>)}</div> : null}</section>
      </>}

      {category === 'DISPLAY' && <>
        <section class="setting-group overlay-setting"><div><p class="eyebrow">DISPLAY MODE</p><p class="setting-note">Overlay keeps a draggable ROM-styled Poké Ball above RetroArch and toggles a resizable 4:3 panel. Drag its lower-right grip to fit unused screen space.</p></div><div class="display-mode" aria-label="Display mode"><a href="dualdex://overlay/dock" data-active={(settings.displayMode ?? 'DOCKED') === 'DOCKED'} aria-current={(settings.displayMode ?? 'DOCKED') === 'DOCKED' ? 'page' : undefined} onClick={() => update({ displayMode: 'DOCKED' })}>DOCKED</a><a href="dualdex://overlay/show" data-active={settings.displayMode === 'OVERLAY'} aria-current={settings.displayMode === 'OVERLAY' ? 'page' : undefined} onClick={() => update({ displayMode: 'OVERLAY' })}>OVERLAY</a></div></section>
        <section class="setting-group"><p class="eyebrow">PRESENTATION</p><SegmentedChoice values={['GAME', 'DARK', 'LIGHT']} active={settings.theme ?? 'GAME'} onSelect={theme => update({ theme })} label="Theme" /><p class="setting-note">Game follows the current game's colors. Dark and Light provide fixed alternatives.</p></section>
        <section class="setting-group"><p class="eyebrow">COMPANION DISPLAY</p><SegmentedChoice values={['AUTO', 'HANDHELD', 'EXTERNAL']} active={settings.displayTarget ?? 'AUTO'} onSelect={displayTarget => update({ displayTarget })} label="Companion display" /><p class="setting-note">Auto keeps the launcher-selected screen. Handheld and External choose a specific Android display.</p><p class="setting-note">The screen choice and overlay size apply to every game.</p></section>
      </>}

      {category === 'INFORMATION' && <>
        <section class="setting-group"><p class="eyebrow">INFORMATION POLICY</p><SegmentedChoice values={['DISCOVERED', 'ORGANIC', 'HIDDEN']} active={settings.knowledgeMode} onSelect={knowledgeMode => update({ knowledgeMode })} label="Information policy" /><p class="setting-note">Organic learns through play and unlocks a Pokémon after capture. Discovered shows the complete game guide immediately.</p></section>
        <section class="setting-group map-detail-setting"><p class="eyebrow">LOCAL MAP DETAILS</p><label class="range-setting"><span>ICONS <b>{poiPreferences.iconZoomThresholdPercent}%</b></span><input aria-label="Map detail icons" type="range" min="0" max="100" step="5" value={poiPreferences.iconZoomThresholdPercent} onInput={event => updatePoi({ iconZoomThresholdPercent: Number(event.currentTarget.value) })} /></label><label class="range-setting"><span>LABELS <b>{poiPreferences.labelZoomThresholdPercent}%</b></span><input aria-label="Map detail labels" type="range" min={poiPreferences.iconZoomThresholdPercent} max="100" step="5" value={poiPreferences.labelZoomThresholdPercent} onInput={event => updatePoi({ labelZoomThresholdPercent: Number(event.currentTarget.value) })} /></label><label class="range-setting"><span>FOLLOW SMOOTHING <b>{settings.mapFollowSmoothingPercent ?? 25}%</b></span><input aria-label="Map follow smoothing" type="range" min="0" max="100" step="5" value={settings.mapFollowSmoothingPercent ?? 25} onInput={event => update({ mapFollowSmoothingPercent: Number(event.currentTarget.value) })} /></label><p class="setting-note">At 0%, permitted map details are visible at the starting Local view. Higher values reveal them only as you zoom in.</p><p class="setting-note">Higher smoothing starts softly and accelerates while the map catches up. This choice applies to every game.</p></section>
        {catalog && catalog.rulesets.length > 0 && <section class="setting-group"><p class="eyebrow">LEVEL-UP MOVES</p><label class="ruleset-setting"><span>MOVE LIST</span><select ref={moveListRef} aria-label="Move list" value={settings.ruleset} onChange={event => update({ ruleset: event.currentTarget.value })}><option value="AUTO">Auto</option>{catalog.rulesets.map(ruleset => <option key={ruleset.id} value={ruleset.id}>{ruleset.label}</option>)}</select></label><p class="setting-note">Auto chooses the matching level-up list. Choose a list only when Auto cannot decide.</p></section>}
        <section class="setting-group"><p class="eyebrow">BATTLE TABS</p><Toggle label="Selected attack" checked={settings.attackEnabled} onChange={attackEnabled => update({ attackEnabled })} /><Toggle label="Recruitment rarity" checked={settings.rarityEnabled} onChange={rarityEnabled => update({ rarityEnabled })} /><Toggle label="Observed moves" checked={settings.movesEnabled} onChange={movesEnabled => update({ movesEnabled })} /></section>
      </>}

      {category === 'ACCESSIBILITY' && <section class="setting-group"><p class="eyebrow">READABILITY</p><label class="range-setting"><span>FONT SCALE <b>{Math.round(settings.fontScale * 100)}%</b></span><input aria-label="Font scale" type="range" min="0.85" max="1.35" step="0.05" value={settings.fontScale} onInput={event => update({ fontScale: Number(event.currentTarget.value) })} /></label><SegmentedChoice values={['AUTO', 'COMFORTABLE', 'COMPACT']} active={settings.density} onSelect={density => update({ density })} label="Density" /><Toggle label="High contrast" checked={settings.highContrast} onChange={highContrast => update({ highContrast })} /></section>}

      {category === 'BEHAVIOR' && <section class="setting-group"><p class="eyebrow">BEHAVIOR</p><Toggle label="Open target automatically" checked={settings.autoOpenTarget} onChange={autoOpenTarget => update({ autoOpenTarget })} /></section>}

      {category === 'ADVANCED' && <section class="setting-group mapper-setting"><p class="eyebrow">DEBUG</p>{catalog && <p class="setting-note debug-rom-identity">{state.catalogName ?? 'Unnamed game'} · {catalog.family.replaceAll('_', ' ')} · CRC32 {catalog.crc32 || 'N/F'}</p>}<p class="setting-note debug-save-state">Save {state.saveRam?.status ?? 'UNAVAILABLE'} · autosave {state.saveRam?.autosaveStatus ?? 'UNVERIFIED'}{state.saveRam?.sourceName ? ` · ${state.saveRam.sourceName}` : ''}</p>{state.saveRam?.refreshedAtEpochMs ? <p class="setting-note">Refreshed {formatTime(state.saveRam.refreshedAtEpochMs)} · file modified {formatTime(state.saveRam.sourceLastModifiedEpochMs)}</p> : null}{state.saveRam?.message && <p class="setting-note">{state.saveRam.message}</p>}{state.saveRam?.candidates?.map(candidate => <p class="setting-note" key={candidate.id}>{candidate.path}</p>)}<p class="setting-note">RetroArch {state.retroArch?.connection ?? 'DISCONNECTED'}{state.retroArch?.activeSource ? ` · ${state.retroArch.activeSource}` : ''}</p><label class="range-setting"><span>BATTLE DISCOVERY POLLING <b>{settings.battlePollingIntervalMs ?? 5} ms</b></span><input aria-label="Battle discovery polling" type="range" min="1" max="20" step="1" value={settings.battlePollingIntervalMs ?? 5} onInput={event => update({ battlePollingIntervalMs: Number(event.currentTarget.value) })} /></label><div class="debug-actions"><button type="button" class="diagnostic-action" disabled={!catalog} onClick={onOpenCapabilities}>{catalog ? 'COMPATIBILITY REPORT' : 'NO GAME LOADED'}</button>{mapperAvailable && <button type="button" class="diagnostic-action" onClick={onOpenMapper}>CAPTURE MEMORY REPORT</button>}<a class="debug-action diagnostic-action" href="dualdex://performance/export">EXPORT PERFORMANCE LOG</a><button type="button" class="danger-action" onClick={() => send('CLEAR_INACTIVE_CATALOGS')}>REMOVE UNUSED GAME DATA</button></div><p class="setting-note">Removing unused data keeps the open game and never resets seen, caught, team, or move knowledge.</p><p class="setting-note warning-note">Compatibility reports are read-only and exclude ROM, save, and memory bytes plus private paths. Optional memory capture remains separate and is used only to report unsupported battle layouts.</p></section>}
    </div>
  </section>;
}

function formatTime(epochMs: number | null | undefined): string {
  if (!epochMs) return 'unknown';
  return new Date(epochMs).toLocaleString();
}

function retroArchConnectionLabel(connection: string | null | undefined): string {
  if (connection === 'CONNECTED') return 'Connected';
  if (connection === 'CONNECTING') return 'Connecting…';
  return 'Not connected';
}

function saveDataLabel(status: string | null | undefined): string {
  if (status === 'MATCHED') return 'Game progress connected';
  if (status === 'LOCATING') return 'Looking for game progress…';
  if (status === 'AMBIGUOUS') return 'Choose the matching save';
  if (status === 'STALE') return 'Game progress needs refreshing';
  return 'No game progress connected';
}

function saveFileName(path: string): string {
  return path.split(/[\\/]/).filter(Boolean).at(-1) ?? 'Save file';
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label class="toggle-row"><span>{label}</span><input aria-label={label} type="checkbox" checked={checked} onChange={event => onChange(event.currentTarget.checked)} /><i aria-hidden="true" /></label>;
}
