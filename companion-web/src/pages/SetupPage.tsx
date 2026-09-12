import { Header } from '../components';
import { formatUiNumber, msg, pluralCategory } from '../i18n';
import type { RetroArchState, State } from '../models';
import { renderPresentationMessage } from '../presentationMessages';

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
    <Header title={msg('retroArch')} onBack={() => send('SCREEN', { screen: returnScreen })} />
    <div class="setup-content" data-scroll-region>
      <div class="setup-intro">
        <p class="eyebrow">{msg('retroArchConnection')}</p>
        <p>{msg('connectRetroArch')}</p>
      </div>

      <SetupStep number="1" title={msg('sharedStorage')} status={retroArch.storageGrant}>
        <p>{msg('sharedStorageDescription')}</p>
        <p class="warning-note">{msg('protectedStorageWarning')}</p>
        {retroArch.storageGrant === 'MISSING' && <a class="setup-action setup-action-primary" href="dualdex://grant/files">{msg('grantAllFiles')}</a>}
        <small>{msg('gamesFound', formatUiNumber(retroArch.indexedRoms), pluralCategory(retroArch.indexedRoms))}</small>
        {retroArch.romGrant !== 'INDEXING' && <a class="setup-action" href="dualdex://games/rescan">{msg('rescanGames')}</a>}
        {retroArch.romGrant === 'INDEXING' && <p class="setup-message" role="status">{msg('findingGames')}</p>}
        {retroArch.romGrant === 'FAILED' && <p class="warning-note">{msg(retroArch.indexedRoms > 0 ? 'rescanFailedWithIndex' : 'gamesIndexFailed')}</p>}
        {retroArch.storageGrant === 'MISSING' && <p class="warning-note">{msg('saveAccessRequired')}</p>}
        <div class="setup-manual-path">
          <strong>{msg('folderFallback')}</strong>
          <p>{msg('folderFallbackDescription')}</p>
          <a class="setup-action" href="dualdex://grant/retroarch">{msg('selectRetroArchFolder')}</a>
          <a class="setup-action" href="dualdex://grant/roms">{msg('selectGameFolder')}</a>
        </div>
      </SetupStep>

      <SetupStep number="2" title={msg('retroArchConfig')} status={retroArch.configState}>
        <p>{msg('configDescription')}</p>
        <small>{msg('configRestartNote')}</small>
        {retroArch.configState === 'FAILED' && <div class="setup-recovery" role="alert">
          <p>{msg('configVerificationFailed')}</p>
          <a class="setup-action setup-action-primary" href="dualdex://grant/retroarch">{msg('reselectRetroArchFolder')}</a>
        </div>}
        {retroArch.configState !== 'VERIFIED' && <div class="setup-manual-path">
          <strong>{msg('manualRetroArchPath')}</strong>
          <p>{msg('manualNetworkInstruction')}</p>
          <p>{msg('manualSaveInstruction')}</p>
          <p>{msg('manualDirectoryInstruction')}</p>
          <p>{msg('manualRestartInstruction')}</p>
        </div>}
      </SetupStep>

      <SetupStep number="3" title={msg('liveSession')} status={retroArch.connection}>
        <div class="setup-facts">
          <span><small>{msg('game')}</small><strong>{retroArch.gameBasename ?? msg('noGameOpen')}</strong></span>
          <span><small>{msg('companion')}</small><strong>{retroArch.connection === 'CONNECTED' ? msg('ready') : msg('waitingForGame')}</strong></span>
        </div>
        <a class="setup-action setup-action-primary" href="dualdex://open/retroarch">{msg('openRetroArch')}</a>
        {retroArch.resolution === 'FAILED' && <>
          <p class="warning-note" role="alert">{retroArch.presentationMessage
            ? renderPresentationMessage(retroArch.presentationMessage)
            : msg('guideOpenFailed')}</p>
          <a class="setup-action setup-action-primary" href="dualdex://guide/retry">{msg('retryOpeningGuide')}</a>
        </>}
      </SetupStep>

      {retroArch.restartRequired && <p class="setup-message" role="status">{msg('restartRetroArch')}</p>}
      <p class="setup-fallback">{msg('manualLoadingAvailable')}</p>
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
    GRANTED: msg('ready'), VERIFIED: msg('ready'), CONNECTED: msg('connected'),
    MISSING: msg('needsAccess'), NOT_CONFIGURED: msg('needsSetup'), RESTART_REQUIRED: msg('restartNeeded'),
    DISCONNECTED: msg('notConnected'), CONNECTING: msg('connecting'),
  };
  return labels[status] ?? msg('needsAttention');
}
