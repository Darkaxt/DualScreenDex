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
    <Header title="RETROARCH" kicker="PASSIVE CONNECTION" onBack={() => send('SCREEN', { screen: returnScreen })} />
    <div class="setup-content" data-scroll-region>
      <div class="setup-intro">
        <p class="eyebrow">RETROARCH CONNECTION</p>
        <p>DualDex watches RetroArch directly. Cocoon and process-ID access are not required.</p>
      </div>

      <SetupStep number="1" title="SHARED STORAGE" status={retroArch.storageGrant}>
        <p>All Files Access automatically finds GB, GBC, GBA, ZIP, and RetroArch SaveRAM folders even when every console uses a separate directory.</p>
        <a class="setup-action setup-action-primary" href="dualdex://grant/files">GRANT ALL FILES ACCESS</a>
        <small>{retroArch.indexedRoms} ROM sources indexed. ROM and save data remain local.</small>
        {retroArch.storageGrant === 'MISSING' && <p class="warning-note">SaveRAM cannot be discovered across separate folders until storage access is granted.</p>}
        <div class="setup-manual-path">
          <strong>FOLDER FALLBACK</strong>
          <p>Use these only when All Files Access is unavailable.</p>
          <a class="setup-action" href="dualdex://grant/retroarch">SELECT RETROARCH FOLDER</a>
          <a class="setup-action" href="dualdex://grant/roms">SELECT ROM FOLDER</a>
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
          <span><small>CONTENT</small><strong>{retroArch.gameBasename ?? 'None'}</strong></span>
          <span><small>MATCH</small><strong>{retroArch.activeSource ?? retroArch.resolution.replaceAll('_', ' ')}</strong></span>
        </div>
        {retroArch.savefileDirectory && <p class="setup-directory"><small>EFFECTIVE SAVE DIRECTORY</small><strong>{retroArch.savefileDirectory}</strong></p>}
        <a class="setup-action setup-action-primary" href="dualdex://open/retroarch">OPEN RETROARCH</a>
      </SetupStep>

      {retroArch.message && <p class="setup-message" role="status">{retroArch.message}</p>}
      <p class="setup-fallback">Manual ROM loading remains available whenever RetroArch is disconnected, inaccessible, or ambiguous.</p>
    </div>
  </section>;
}

function SetupStep({ number, title, status, children }: { number: string; title: string; status: string; children: preact.ComponentChildren }) {
  return <section class="setup-step">
    <header><b>{number}</b><strong>{title}</strong><span data-state={status}>{status.replaceAll('_', ' ')}</span></header>
    {children}
  </section>;
}
