import { Header } from '../components';
import type { RetroArchState, State } from '../models';

const disconnected: RetroArchState = {
  storageGrant: 'MISSING',
  configGrant: 'MISSING',
  romGrant: 'MISSING',
  configState: 'NOT_CONFIGURED',
  restartRequired: false,
  connection: 'DISCONNECTED',
  systemId: null,
  gameBasename: null,
  contentCrc32: null,
  resolution: 'NO_CONTENT',
  activeSource: null,
  savefileDirectory: null,
  indexedRoms: 0,
  message: null,
};

export function SetupPage({ state, send }: { state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const retroArch = state.retroArch ?? disconnected;
  const returnScreen = state.catalogReady ? state.priorScreen : 'POKEDEX';
  return <section class="screen setup-screen">
    <Header title="RETROARCH" onBack={() => send('SCREEN', { screen: returnScreen })} />
    <div class="setup-content" data-scroll-region>
      <div class="setup-intro">
        <p class="eyebrow">RETROARCH CONNECTION</p>
        <p>Connect DualDex to a running game in RetroArch.</p>
      </div>

      <SetupStep number="1" title="SHARED STORAGE" status={retroArch.storageGrant}>
        <p>All Files Access automatically finds supported games and their save files, even when they use separate folders.</p>
        <p class="warning-note">Android/data and Android/obb remain protected. Keep games and saves in public shared storage or use the folder fallback.</p>
        {retroArch.storageGrant === 'MISSING' && <a class="setup-action setup-action-primary" href="dualdex://grant/files">GRANT ALL FILES ACCESS</a>}
        <small>{retroArch.indexedRoms} games found.</small>
        {retroArch.romGrant !== 'INDEXING' && <a class="setup-action" href="dualdex://games/rescan">RESCAN GAMES</a>}
        {retroArch.romGrant === 'INDEXING' && <p class="setup-message" role="status">Finding your games…</p>}
        {retroArch.romGrant === 'FAILED' && <p class="warning-note">{retroArch.indexedRoms > 0
          ? 'Rescan failed. The previous game index remains active; try the rescan again or select a folder.'
          : 'Games could not be indexed. Select the game folder below or try again.'}</p>}
        {retroArch.storageGrant === 'MISSING' && <p class="warning-note">Save files in separate folders cannot be found until storage access is granted.</p>}
        <div class="setup-manual-path">
          <strong>FOLDER FALLBACK</strong>
          <p>Use these only when All Files Access is unavailable.</p>
          <a class="setup-action" href="dualdex://grant/retroarch">SELECT RETROARCH FOLDER</a>
          <a class="setup-action" href="dualdex://grant/roms">SELECT GAME FOLDER</a>
        </div>
      </SetupStep>

      <SetupStep number="2" title="RETROARCH CONFIG" status={retroArch.configState}>
        <p>Fully close RetroArch before setup. DualDex enables Network Commands and a 10-second SaveRAM autosave interval in the public retroarch.cfg, then verifies the exact edit without changing unrelated settings.</p>
        <small>The command interface is not considered active until DualDex verifies it after a full RetroArch restart.</small>
        {retroArch.configState !== 'VERIFIED' && <div class="setup-manual-path">
          <strong>MANUAL RETROARCH PATH</strong>
          <p>Settings → Network → Network Commands: enable Network Commands and keep port 55355.</p>
          <p>Settings → Saving → SaveRAM Autosave Interval: set 10 seconds.</p>
          <p>Settings → Directory → Save Files: select a public RetroArch/saves folder DualDex can read.</p>
          <p>Main Menu → Configuration File → Save Current Configuration, then fully restart RetroArch.</p>
        </div>}
      </SetupStep>

      <SetupStep number="3" title="LIVE SESSION" status={retroArch.connection}>
        <div class="setup-facts">
          <span><small>GAME</small><strong>{retroArch.gameBasename ?? 'No game open'}</strong></span>
          <span><small>COMPANION</small><strong>{retroArch.connection === 'CONNECTED' ? 'Ready' : 'Waiting for a game'}</strong></span>
        </div>
        <a class="setup-action setup-action-primary" href="dualdex://open/retroarch">OPEN RETROARCH</a>
        {retroArch.resolution === 'FAILED' && <>
          <p class="warning-note" role="alert">{retroArch.message ?? 'This game guide could not be opened. You can try again.'}</p>
          <a class="setup-action setup-action-primary" href="dualdex://guide/retry">RETRY OPENING GAME GUIDE</a>
        </>}
      </SetupStep>

      {retroArch.restartRequired && <p class="setup-message" role="status">Fully restart RetroArch, then return here.</p>}
      <p class="setup-fallback">Manual game loading remains available whenever RetroArch is not connected.</p>
    </div>
  </section>;
}

function SetupStep({ number, title, status, children }: { number: string; title: string; status: string; children: preact.ComponentChildren }) {
  return <section class="setup-step">
    <header><b>{number}</b><strong>{title}</strong><span data-state={status}>{setupStatusLabel(status)}</span></header>
    {children}
  </section>;
}

function setupStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    GRANTED: 'Ready', VERIFIED: 'Ready', CONNECTED: 'Connected',
    MISSING: 'Needs access', NOT_CONFIGURED: 'Needs setup', RESTART_REQUIRED: 'Restart needed',
    DISCONNECTED: 'Not connected', CONNECTING: 'Connecting…',
  };
  return labels[status] ?? 'Needs attention';
}
