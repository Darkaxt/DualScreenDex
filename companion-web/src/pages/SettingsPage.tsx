import type { Catalog, State } from '../models';
import { Header, Segmented } from '../components';

export function SettingsPage({ catalog, state, send, onUpload }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void; onUpload: (file: File) => void }) {
  const settings = state.settings;
  const update = (values: Record<string, string | number | boolean>) => send('SETTINGS', values);
  return <section class="screen settings-screen">
    <Header title="SETTINGS" kicker="PRESENTATION & KNOWLEDGE" onBack={() => send('SCREEN', { screen: state.settingsReturnScreen })} />
    <div class="settings-content" data-scroll-region>
      <section class="setting-group rom-setting"><p class="eyebrow">ACTIVE GAME</p><p class="setting-note rom-setting-name">{state.catalogName ?? 'Unnamed ROM'} · CRC32 {catalog.crc32 || 'N/F'}</p><label class="settings-upload"><span>CHANGE ROM OR ZIP</span><input aria-label="Change ROM or ZIP" type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label></section>
      <section class="setting-group retroarch-setting"><div><p class="eyebrow">RETROARCH</p><p class="setting-note">{state.retroArch?.activeSource ?? state.retroArch?.connection ?? 'DISCONNECTED'}</p></div><button type="button" onClick={() => send('SCREEN', { screen: 'SETUP' })}>RETROARCH SETUP</button></section>
      <section class="setting-group save-setting"><div><p class="eyebrow">SAVERAM</p><p class="setting-note"><strong>{state.saveRam?.status ?? 'UNAVAILABLE'}</strong>{state.saveRam?.sourceName ? ` · ${state.saveRam.sourceName}` : ''}</p>{state.saveRam?.refreshedAtEpochMs ? <p class="setting-note">Refreshed {formatTime(state.saveRam.refreshedAtEpochMs)} · file modified {formatTime(state.saveRam.sourceLastModifiedEpochMs)}</p> : null}{state.saveRam?.autosaveStatus !== 'VERIFIED' && <p class="setting-note warning-note">RetroArch autosave is {state.saveRam?.autosaveStatus?.toLowerCase() ?? 'unverified'}.</p>}{state.saveRam?.message && <p class="setting-note">{state.saveRam.message}</p>}</div>{state.saveRam?.candidates?.length ? <div class="save-candidates">{state.saveRam.candidates.map(candidate => <button type="button" key={candidate.id} onClick={() => send('SELECT_SAVE', { documentId: candidate.id })}><strong>{candidate.path}</strong><small>{formatTime(candidate.lastModifiedEpochMs)}</small></button>)}</div> : null}</section>
      <section class="setting-group overlay-setting"><div><p class="eyebrow">DISPLAY MODE</p><p class="setting-note">Overlay keeps a draggable ROM-styled Poké Ball above RetroArch and uses it to toggle a fixed 4:3 panel.</p></div><div class="display-mode" aria-label="Display mode"><a href="dualdex://overlay/dock" data-active={(settings.displayMode ?? 'DOCKED') === 'DOCKED'} onClick={() => update({ displayMode: 'DOCKED' })}>DOCKED</a><a href="dualdex://overlay/show" data-active={settings.displayMode === 'OVERLAY'} onClick={() => update({ displayMode: 'OVERLAY' })}>OVERLAY</a></div></section>
      <section class="setting-group"><p class="eyebrow">INFORMATION POLICY</p><Segmented values={['DISCOVERED', 'ORGANIC', 'HIDDEN']} active={settings.knowledgeMode} onSelect={knowledgeMode => update({ knowledgeMode })} label="Information policy" /><p class="setting-note">Organic learns through play and unlocks a species after capture. Discovered exposes ROM facts immediately.</p></section>
      {catalog.rulesets.length > 0 && <section class="setting-group"><p class="eyebrow">MOVESET RULESET</p><label class="ruleset-setting"><span>ACTIVE CATALOG</span><select value={settings.ruleset} onChange={event => update({ ruleset: event.currentTarget.value })}><option value="AUTO">Auto{state.activeRulesetId ? ` · ${catalog.rulesets.find(item => item.id === state.activeRulesetId)?.label ?? state.activeRulesetId}` : ''}</option>{catalog.rulesets.map(ruleset => <option key={ruleset.id} value={ruleset.id}>{ruleset.label}</option>)}</select></label><p class="setting-note">Auto uses the ROM default. Choose a catalog to preview a different supported ruleset.</p></section>}
      <section class="setting-group"><p class="eyebrow">BATTLE TABS</p><Toggle label="Selected attack" checked={settings.attackEnabled} onChange={attackEnabled => update({ attackEnabled })} /><Toggle label="Recruitment rarity" checked={settings.rarityEnabled} onChange={rarityEnabled => update({ rarityEnabled })} /><Toggle label="Observed moves" checked={settings.movesEnabled} onChange={movesEnabled => update({ movesEnabled })} /></section>
      <section class="setting-group"><p class="eyebrow">READABILITY</p><label class="range-setting"><span>FONT SCALE <b>{Math.round(settings.fontScale * 100)}%</b></span><input type="range" min="0.85" max="1.35" step="0.05" value={settings.fontScale} onInput={event => update({ fontScale: Number(event.currentTarget.value) })} /></label><Segmented values={['AUTO', 'COMFORTABLE', 'COMPACT']} active={settings.density} onSelect={density => update({ density })} label="Density" /><Toggle label="High contrast" checked={settings.highContrast} onChange={highContrast => update({ highContrast })} /></section>
      <section class="setting-group"><p class="eyebrow">BEHAVIOR</p><Toggle label="Open target automatically" checked={settings.autoOpenTarget} onChange={autoOpenTarget => update({ autoOpenTarget })} /></section>
    </div>
  </section>;
}

function formatTime(epochMs: number | null | undefined): string {
  if (!epochMs) return 'unknown';
  return new Date(epochMs).toLocaleString();
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label class="toggle-row"><span>{label}</span><input type="checkbox" checked={checked} onChange={event => onChange(event.currentTarget.checked)} /><i /></label>;
}
