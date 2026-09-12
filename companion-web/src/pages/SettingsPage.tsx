import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalog, State } from '../models';
import { Header, SegmentedChoice } from '../components';
import { formatUiDate, formatUiNumber, msg } from '../i18n';
import { renderPresentationMessage } from '../presentationMessages';

export const SETTINGS_CATEGORIES = [
  { id: 'GENERAL', label: 'General', description: 'Game and saved preferences' },
  { id: 'CONNECTION', label: 'Connection', description: 'RetroArch and save data' },
  { id: 'DISPLAY', label: 'Display', description: 'Theme, mode, and companion screen' },
  { id: 'INFORMATION', label: 'Information', description: 'Guide, map, move, and battle data' },
  { id: 'ACCESSIBILITY', label: 'Accessibility', description: 'Text, contrast, and map visibility' },
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
  const categories = localizedSettingsCategories();
  const activeCategory = categories.find(candidate => candidate.id === category);

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
    <Header title={activeCategory?.label.toUpperCase() ?? msg('settingsTitle')} gameTime={state.gameTime} focusHeading={initialControl == null} onBack={handleBack} currentDestination="SETTINGS" />
    <div ref={contentRef} class="settings-content" data-scroll-region>
      <h2 ref={viewHeadingRef} class="settings-view-heading" tabIndex={-1}>{activeCategory?.label ?? msg('settingsCategories')}</h2>
      {category == null && <nav class="settings-category-list" aria-label={msg('settingsCategories')}>
        {categories.map(item => <button
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
        <section class="setting-group rom-setting"><p class="eyebrow">{msg('game')}</p><p class="setting-note rom-setting-name">{msg('chooseAnotherGame')}</p><label class="settings-upload routine-action"><span>{msg('changeRomOrZip')}</span><input aria-label={msg('changeRomOrZip')} type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label></section>
        <section class="setting-group"><p class="eyebrow">{msg('interfaceLanguage')}</p><label class="ruleset-setting" for="interface-language"><span>{msg('interfaceLanguage')}</span><select id="interface-language" aria-label={msg('interfaceLanguage')} value={settings.interfaceLanguage ?? 'AUTO'} onChange={event => update({ interfaceLanguage: event.currentTarget.value })}><option value="AUTO">{msg('languageAuto')}</option><option value="EN">{msg('languageEnglish')}</option><option value="FR">{msg('languageFrench')}</option><option value="DE">{msg('languageGerman')}</option><option value="IT">{msg('languageItalian')}</option><option value="ES">{msg('languageSpanish')}</option></select></label><p class="setting-note">{msg('interfaceLanguageNote')}</p></section>
        <section class="setting-group"><p class="eyebrow">{msg('preferences')}</p><p class="setting-note">{catalog ? msg('preferencesCurrentGame') : msg('preferencesDefaults')}</p></section>
      </>}

      {category === 'CONNECTION' && <>
        <section class="setting-group retroarch-setting"><div><p class="eyebrow">{msg('retroArch')}</p><p class="setting-note">{retroArchConnectionLabel(state.retroArch?.connection)}</p></div><button type="button" class="primary-action" onClick={() => send('SCREEN', { screen: 'SETUP' })}>{msg('retroArchSetup')}</button></section>
        <section class="setting-group save-setting"><div><p class="eyebrow">{msg('saveData')}</p><p class="setting-note"><strong>{saveDataLabel(state.saveRam?.status)}</strong></p></div>{state.saveRam?.candidates?.length ? <div class="save-candidates">{state.saveRam.candidates.map((candidate, index) => <button type="button" key={candidate.id} onClick={() => send('SELECT_SAVE', { documentId: candidate.id })}><strong>{msg('saveNumber', formatUiNumber(index + 1))}</strong><small>{saveFileName(candidate.path)}</small></button>)}</div> : null}</section>
      </>}

      {category === 'DISPLAY' && <>
        <section class="setting-group overlay-setting"><div><p class="eyebrow">{msg('displayMode')}</p><p class="setting-note">{msg('overlayDescription')}</p></div><div class="display-mode" aria-label={msg('displayMode')}><a href="dualdex://overlay/dock" data-active={(settings.displayMode ?? 'DOCKED') === 'DOCKED'} aria-current={(settings.displayMode ?? 'DOCKED') === 'DOCKED' ? 'page' : undefined} onClick={() => update({ displayMode: 'DOCKED' })}>{msg('docked')}</a><a href="dualdex://overlay/show" data-active={settings.displayMode === 'OVERLAY'} aria-current={settings.displayMode === 'OVERLAY' ? 'page' : undefined} onClick={() => update({ displayMode: 'OVERLAY' })}>{msg('overlay')}</a></div></section>
        <section class="setting-group"><p class="eyebrow">{msg('presentation')}</p><SegmentedChoice values={['GAME', 'DARK', 'LIGHT']} active={settings.theme ?? 'GAME'} onSelect={theme => update({ theme })} label={msg('theme')} labels={{ GAME: msg('themeGame'), DARK: msg('themeDark'), LIGHT: msg('themeLight') }} /><p class="setting-note">{msg('themeDescription')}</p></section>
        <section class="setting-group"><p class="eyebrow">{msg('companionDisplay')}</p><SegmentedChoice values={['AUTO', 'HANDHELD', 'EXTERNAL']} active={settings.displayTarget ?? 'AUTO'} onSelect={displayTarget => update({ displayTarget })} label={msg('companionDisplay')} labels={{ AUTO: msg('displayAuto'), HANDHELD: msg('displayHandheld'), EXTERNAL: msg('displayExternal') }} /><p class="setting-note">{msg('displayTargetDescription')}</p><p class="setting-note">{msg('displayGlobalNote')}</p></section>
      </>}

      {category === 'INFORMATION' && <>
        <section class="setting-group"><p class="eyebrow">{msg('informationPolicy')}</p><SegmentedChoice values={['DISCOVERED', 'ORGANIC', 'HIDDEN']} active={settings.knowledgeMode} onSelect={knowledgeMode => update({ knowledgeMode })} label={msg('informationPolicy')} labels={{ DISCOVERED: msg('informationDiscovered'), ORGANIC: msg('informationOrganic'), HIDDEN: msg('informationHidden') }} /><p class="setting-note">{msg('informationPolicyDescription')}</p></section>
        <section class="setting-group map-detail-setting"><p class="eyebrow">{msg('localMapDetails')}</p><label class="range-setting"><span>{msg('icons')} <b>{formatUiNumber(poiPreferences.iconZoomThresholdPercent)}%</b></span><input aria-label={msg('mapDetailIcons')} type="range" min="0" max="100" step="5" value={poiPreferences.iconZoomThresholdPercent} onInput={event => updatePoi({ iconZoomThresholdPercent: Number(event.currentTarget.value) })} /></label><label class="range-setting"><span>{msg('labels')} <b>{formatUiNumber(poiPreferences.labelZoomThresholdPercent)}%</b></span><input aria-label={msg('mapDetailLabels')} type="range" min={poiPreferences.iconZoomThresholdPercent} max="100" step="5" value={poiPreferences.labelZoomThresholdPercent} onInput={event => updatePoi({ labelZoomThresholdPercent: Number(event.currentTarget.value) })} /></label><label class="range-setting"><span>{msg('followSmoothing')} <b>{formatUiNumber(settings.mapFollowSmoothingPercent ?? 25)}%</b></span><input aria-label={msg('mapFollowSmoothing')} type="range" min="0" max="100" step="5" value={settings.mapFollowSmoothingPercent ?? 25} onInput={event => update({ mapFollowSmoothingPercent: Number(event.currentTarget.value) })} /></label><p class="setting-note">{msg('mapDetailThresholdDescription')}</p><p class="setting-note">{msg('mapSmoothingDescription')}</p></section>
        {catalog && catalog.rulesets.length > 0 && <section class="setting-group"><p class="eyebrow">{msg('levelUpMoves')}</p><label class="ruleset-setting"><span>{msg('moveList')}</span><select ref={moveListRef} aria-label={msg('moveList')} value={settings.ruleset} onChange={event => update({ ruleset: event.currentTarget.value })}><option value="AUTO">{msg('languageAuto')}</option>{catalog.rulesets.map(ruleset => <option key={ruleset.id} value={ruleset.id}>{renderPresentationMessage(ruleset.label)}</option>)}</select></label><p class="setting-note">{msg('moveListAutoDescription')}</p></section>}
        <section class="setting-group"><p class="eyebrow">{msg('battleTabs')}</p><Toggle label={msg('selectedAttack')} checked={settings.attackEnabled} onChange={attackEnabled => update({ attackEnabled })} /><Toggle label={msg('recruitmentRarity')} checked={settings.rarityEnabled} onChange={rarityEnabled => update({ rarityEnabled })} /><Toggle label={msg('observedMoves')} checked={settings.movesEnabled} onChange={movesEnabled => update({ movesEnabled })} /></section>
      </>}

      {category === 'ACCESSIBILITY' && <>
        <section class="setting-group"><p class="eyebrow">{msg('readability')}</p><label class="range-setting"><span>{msg('fontScale')} <b>{formatUiNumber(Math.round(settings.fontScale * 100))}%</b></span><input aria-label={msg('fontScale')} type="range" min="0.85" max="1.35" step="0.05" value={settings.fontScale} onInput={event => update({ fontScale: Number(event.currentTarget.value) })} /></label><SegmentedChoice values={['AUTO', 'COMFORTABLE', 'COMPACT']} active={settings.density} onSelect={density => update({ density })} label={msg('density')} labels={{ AUTO: msg('densityAuto'), COMFORTABLE: msg('densityComfortable'), COMPACT: msg('densityCompact') }} /><Toggle label={msg('highContrast')} checked={settings.highContrast} onChange={highContrast => update({ highContrast })} /></section>
        <section class="setting-group"><p class="eyebrow">{msg('localMap')}</p><Toggle label={msg('highVisibilityMapPlayer')} checked={settings.highVisibilityMapPlayer ?? false} onChange={highVisibilityMapPlayer => update({ highVisibilityMapPlayer })} /><p class="setting-note">{msg('highVisibilityMapPlayerDescription')}</p></section>
      </>}

      {category === 'BEHAVIOR' && <section class="setting-group"><p class="eyebrow">{msg('settingsBehavior')}</p><Toggle label={msg('openTargetAutomatically')} checked={settings.autoOpenTarget} onChange={autoOpenTarget => update({ autoOpenTarget })} /></section>}

      {category === 'ADVANCED' && <section class="setting-group mapper-setting"><p class="eyebrow">{msg('debug')}</p>{catalog && <p class="setting-note debug-rom-identity">{state.catalogName ?? msg('unnamedGame')} · {catalog.family.replaceAll('_', ' ')} · CRC32 {catalog.crc32 || 'N/F'}</p>}<p class="setting-note debug-save-state">{msg('saveData')} {state.saveRam?.status ?? 'UNAVAILABLE'} · autosave {state.saveRam?.autosaveStatus ?? 'UNVERIFIED'}{state.saveRam?.sourceName ? ` · ${state.saveRam.sourceName}` : ''}</p>{state.saveRam?.refreshedAtEpochMs ? <p class="setting-note">{msg('refreshedAt', formatTime(state.saveRam.refreshedAtEpochMs))} · {msg('fileModifiedAt', formatTime(state.saveRam.sourceLastModifiedEpochMs))}</p> : null}{state.saveRam?.message && <p class="setting-note">{state.saveRam.message}</p>}{state.saveRam?.candidates?.map(candidate => <p class="setting-note" key={candidate.id}>{candidate.path}</p>)}<p class="setting-note">RetroArch {state.retroArch?.connection ?? 'DISCONNECTED'}{state.retroArch?.activeSource ? ` · ${state.retroArch.activeSource}` : ''}</p><label class="range-setting"><span>{msg('battleDiscoveryPolling')} <b>{formatUiNumber(settings.battlePollingIntervalMs ?? 5)} ms</b></span><input aria-label={msg('battleDiscoveryPolling')} type="range" min="1" max="20" step="1" value={settings.battlePollingIntervalMs ?? 5} onInput={event => update({ battlePollingIntervalMs: Number(event.currentTarget.value) })} /></label><div class="debug-actions"><button type="button" class="diagnostic-action" disabled={!catalog} onClick={onOpenCapabilities}>{catalog ? msg('compatibilityReport') : msg('noGameLoaded')}</button>{mapperAvailable && <button type="button" class="diagnostic-action" onClick={onOpenMapper}>{msg('captureMemoryReport')}</button>}<a class="debug-action diagnostic-action" href="dualdex://performance/export">{msg('exportPerformanceLog')}</a><button type="button" class="danger-action" onClick={() => send('CLEAR_INACTIVE_CATALOGS')}>{msg('removeUnusedGameData')}</button></div><p class="setting-note">{msg('removeUnusedGameDataDescription')}</p><p class="setting-note warning-note">{msg('compatibilityPrivacyDescription')}</p></section>}
    </div>
  </section>;
}

function localizedSettingsCategories(): ReadonlyArray<{ id: SettingsCategory; label: string; description: string }> {
  return [
    { id: 'GENERAL', label: msg('settingsGeneral'), description: msg('settingsGeneralDescription') },
    { id: 'CONNECTION', label: msg('settingsConnection'), description: msg('settingsConnectionDescription') },
    { id: 'DISPLAY', label: msg('settingsDisplay'), description: msg('settingsDisplayDescription') },
    { id: 'INFORMATION', label: msg('settingsInformation'), description: msg('settingsInformationDescription') },
    { id: 'ACCESSIBILITY', label: msg('settingsAccessibility'), description: msg('settingsAccessibilityDescription') },
    { id: 'BEHAVIOR', label: msg('settingsBehavior'), description: msg('settingsBehaviorDescription') },
    { id: 'ADVANCED', label: msg('settingsAdvanced'), description: msg('settingsAdvancedDescription') },
  ];
}

function formatTime(epochMs: number | null | undefined): string {
  if (!epochMs) return msg('unknown');
  return formatUiDate(epochMs, { dateStyle: 'short', timeStyle: 'short' });
}

function retroArchConnectionLabel(connection: string | null | undefined): string {
  if (connection === 'CONNECTED') return msg('connected');
  if (connection === 'CONNECTING') return msg('connecting');
  return msg('notConnected');
}

function saveDataLabel(status: string | null | undefined): string {
  if (status === 'MATCHED') return msg('progressConnected');
  if (status === 'LOCATING') return msg('locatingProgress');
  if (status === 'AMBIGUOUS') return msg('chooseMatchingSave');
  if (status === 'STALE') return msg('progressNeedsRefresh');
  return msg('noProgressConnected');
}

function saveFileName(path: string): string {
  return path.split(/[\\/]/).filter(Boolean).at(-1) ?? msg('saveFile');
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label class="toggle-row"><span>{label}</span><input aria-label={label} type="checkbox" checked={checked} onChange={event => onChange(event.currentTarget.checked)} /><i aria-hidden="true" /></label>;
}
