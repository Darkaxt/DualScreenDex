import type { State } from '../models';
import { Header, Segmented } from '../components';

export function SettingsPage({ state, send }: { state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const settings = state.settings;
  const update = (values: Record<string, string | number | boolean>) => send('SETTINGS', values);
  return <section class="screen settings-screen">
    <Header title="SETTINGS" kicker="PRESENTATION & KNOWLEDGE" onBack={() => send('SCREEN', { screen: state.settingsReturnScreen })} />
    <div class="settings-content" data-scroll-region>
      <section class="setting-group"><p class="eyebrow">INFORMATION POLICY</p><Segmented values={['DISCOVERED', 'ORGANIC', 'HIDDEN']} active={settings.knowledgeMode} onSelect={knowledgeMode => update({ knowledgeMode })} label="Information policy" /><p class="setting-note">Organic learns through play and unlocks a species after capture. Discovered exposes ROM facts immediately.</p></section>
      <section class="setting-group"><p class="eyebrow">BATTLE TABS</p><Toggle label="Selected attack" checked={settings.attackEnabled} onChange={attackEnabled => update({ attackEnabled })} /><Toggle label="Recruitment rarity" checked={settings.rarityEnabled} onChange={rarityEnabled => update({ rarityEnabled })} /><Toggle label="Observed moves" checked={settings.movesEnabled} onChange={movesEnabled => update({ movesEnabled })} /></section>
      <section class="setting-group"><p class="eyebrow">READABILITY</p><label class="range-setting"><span>FONT SCALE <b>{Math.round(settings.fontScale * 100)}%</b></span><input type="range" min="0.85" max="1.35" step="0.05" value={settings.fontScale} onInput={event => update({ fontScale: Number(event.currentTarget.value) })} /></label><Segmented values={['AUTO', 'COMFORTABLE', 'COMPACT']} active={settings.density} onSelect={density => update({ density })} label="Density" /><Toggle label="High contrast" checked={settings.highContrast} onChange={highContrast => update({ highContrast })} /></section>
      <section class="setting-group"><p class="eyebrow">BEHAVIOR</p><Toggle label="Open target automatically" checked={settings.autoOpenTarget} onChange={autoOpenTarget => update({ autoOpenTarget })} /></section>
    </div>
  </section>;
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label class="toggle-row"><span>{label}</span><input type="checkbox" checked={checked} onChange={event => onChange(event.currentTarget.checked)} /><i /></label>;
}
