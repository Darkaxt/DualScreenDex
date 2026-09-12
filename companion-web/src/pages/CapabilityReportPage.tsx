import { useEffect, useState } from 'preact/hooks';
import { boundedRequest } from '../boundedRequest';
import { Header } from '../components';
import { diagnostics } from '../gateway';
import type { DiagnosticCapability, DiagnosticView } from '../models';
import { formatUiNumber, msg, pluralCategory } from '../i18n';
import { renderPresentationMessage } from '../presentationMessages';

const CAPABILITY_REQUEST_TIMEOUT_MILLIS = 8_000;

export function CapabilityReportPage({ romHash, refreshMarker, onBack, load = diagnostics, requestTimeoutMillis = CAPABILITY_REQUEST_TIMEOUT_MILLIS }: {
  romHash: string;
  refreshMarker: string;
  onBack: () => void;
  load?: () => Promise<DiagnosticView>;
  requestTimeoutMillis?: number;
}) {
  const [view, setView] = useState<DiagnosticView | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const currentView = view && view.sha256.toLowerCase() === romHash.toLowerCase() ? view : null;

  useEffect(() => {
    let current = true;
    setView(null);
    setError(null);
    void boundedRequest(
      load(),
      requestTimeoutMillis,
      msg('compatibilityRequestTimeout'),
    ).then(next => {
      if (!current) return;
      setView(next);
      setExpanded(new Set());
    }).catch(failure => {
      if (current) setError(failure instanceof Error ? failure.message : String(failure));
    });
    return () => { current = false; };
  }, [romHash, refreshMarker, reloadKey, load, requestTimeoutMillis]);

  const toggle = (index: number) => setExpanded(current => {
    const next = new Set(current);
    if (next.has(index)) next.delete(index); else next.add(index);
    return next;
  });

  const copy = async () => {
    if (!currentView) return;
    try {
      await navigator.clipboard.writeText(stableReport(currentView));
      setCopyStatus(msg('reportCopied'));
    } catch (failure) {
      setCopyStatus(failure instanceof Error ? failure.message : msg('copyFailed'));
    }
  };

  return <section class="screen capability-screen">
    <Header title={msg('compatibilityReport')} kicker={msg('loadedRomReadOnly')} onBack={onBack} />
    <div class="capability-content" data-scroll-region>
      {currentView && <>
        <section class="capability-identity">
          <p class="eyebrow">{msg('activeGame')}</p>
          <strong>{currentView.romName ?? msg('unnamedRom')}</strong>
          <span>{pretty(currentView.family)} · {currentView.platform}</span>
          <span>CRC32 {currentView.crc32 ? currentView.crc32.toUpperCase() : 'N/F'} · SHA-256 {currentView.sha256 ? currentView.sha256.slice(0, 12).toUpperCase() : 'N/F'}</span>
          <span>{rulesetLabel(currentView)}{currentView.rulesetAssumed ? ` · ${msg('assumed')}` : ''}</span>
        </section>
        <CompatibilitySummary view={currentView} />
        <section class="capability-list" aria-label={msg('romCapabilities')}>
          {Array.isArray(currentView.capabilities) && currentView.capabilities.map((raw, index) => {
            const capability = normalizeCapability(raw);
            const status = displayStatus(capability);
            const statusText = displayStatusLabel(status);
            const coverage = coverageText(capability);
            const open = expanded.has(index);
            return <article class={`capability-card capability-${status.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`} key={`${capability.capability}-${index}`}>
              <button type="button" aria-expanded={open} onClick={() => toggle(index)} aria-label={`${pretty(capability.capability)} ${statusText}${coverage ? ` ${coverage}` : ''}`}>
                <span><strong>{pretty(capability.capability)}</strong>{coverage && <small>{coverage}</small>}</span>
                <b>{statusText}</b><i aria-hidden="true" />
              </button>
              {open && <CapabilityDetails capability={capability} status={status} />}
            </article>;
          })}
        </section>
        <section class="capability-actions">
          <button type="button" onClick={() => void copy()}>{msg('copyReport')}</button>
          <a href="dualdex://compatibility/export">{msg('exportReport')}</a>
          {copyStatus && <span role="status">{copyStatus}</span>}
        </section>
      </>}
      {!currentView && !error && <p class="capability-loading" role="status">{msg('loadingCapabilities')}</p>}
      {error && <section class="paper-panel capability-error" role="alert"><strong>{msg('reportUnavailable')}</strong><p>{error} {msg('activeGameRemainsSelected')}</p><button type="button" onClick={() => setReloadKey(value => value + 1)}>{msg('retry')}</button></section>}
    </div>
  </section>;
}

function CompatibilitySummary({ view }: { view: DiagnosticView }) {
  if (!view.runtime && !view.map && !view.cache && !view.environment) return null;
  return <section class="compatibility-summary" aria-label={msg('currentCompatibility')}>
    {view.runtime && <article>
      <p class="eyebrow">{msg('runtime')}</p>
      <strong>{pretty(view.runtime.retroArchConnection)} · {view.runtime.gameAccessReady ? msg('gameAccessReady') : msg('waitingGameAccess')}</strong>
      <span>{pretty(view.runtime.contentResolution)} · {msg('saveData')} {pretty(view.runtime.saveRamStatus)}</span>
    </article>}
    {view.map && <article>
      <p class="eyebrow">{msg('currentMap')}</p>
      <strong>{pretty(view.map.presentation)}</strong>
      <span>{view.map.currentAreaName ?? msg('areaUnavailable')} · {msg('player')} {pretty(view.map.playerPositionStatus)}</span>
      <span>{pretty(view.map.lighting)} · {msg('poisSummary', formatUiNumber(view.map.visiblePois), formatUiNumber(view.map.totalPois))}</span>
      {view.map.fallbackReason && <span>{msg('fallback')} {pretty(view.map.fallbackReason)}</span>}
    </article>}
    {view.cache && <article>
      <p class="eyebrow">{msg('mapCache')}</p>
      <strong>{msg('rasterCount', formatUiNumber(view.cache.entries), pluralCategory(view.cache.entries))} · {msg('renderCount', formatUiNumber(view.cache.renders), pluralCategory(view.cache.renders))}</strong>
      <span>{msg('cacheStats', formatUiNumber(view.cache.hits), formatUiNumber(view.cache.evictions), formatUiNumber(view.cache.encodedBytes))}</span>
    </article>}
    {view.environment && <article>
      <p class="eyebrow">{msg('reportContract')}</p>
      <strong>{view.environment.appVersion ?? msg('appVersionUnavailable')}</strong>
      <span>{msg('schemaVersions', formatUiNumber(view.environment.catalogSchemaVersion), formatUiNumber(view.environment.parserSchemaVersion), formatUiNumber(view.reportSchemaVersion ?? 1))}</span>
    </article>}
  </section>;
}

function CapabilityDetails({ capability, status }: { capability: DiagnosticCapability; status: string }) {
  const absent = status === 'N/A' ? 'N/A' : 'N/F';
  const value = (entry: string | number | null | undefined, suffix = '') => entry == null || entry === '' ? absent : `${typeof entry === 'number' ? formatUiNumber(entry) : entry}${suffix}`;
  return <div class="capability-details">
    <dl>
      <div><dt>{msg('confidence')}</dt><dd>{Number.isFinite(capability.confidence) ? `${formatUiNumber(capability.confidence * 100, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%` : absent}</dd></div>
      <div><dt>{msg('romOffset')}</dt><dd>{capability.offset == null ? absent : `0x${capability.offset.toString(16).toUpperCase()}`}</dd></div>
      <div><dt>{msg('validTotal')}</dt><dd>{capability.validRecords == null || capability.totalRecords == null ? absent : `${formatUiNumber(capability.validRecords)} / ${formatUiNumber(capability.totalRecords)}`}</dd></div>
      <div><dt>{msg('coveredExpected')}</dt><dd>{capability.coveredRecords == null || capability.expectedRecords == null ? absent : `${formatUiNumber(capability.coveredRecords)} / ${formatUiNumber(capability.expectedRecords)}`}</dd></div>
      <div><dt>{msg('incomplete')}</dt><dd>{value(capability.incompleteRecords)}</dd></div>
      <div><dt>{msg('count')}</dt><dd>{value(capability.count)}</dd></div>
      <div><dt>{msg('recordSize')}</dt><dd>{capability.recordSize == null ? absent : msg('bytesValue', formatUiNumber(capability.recordSize))}</dd></div>
      <div><dt>{msg('elementSize')}</dt><dd class="capability-element-size">{capability.elementSize == null ? absent : msg('bytesValue', formatUiNumber(capability.elementSize))}</dd></div>
    </dl>
    {capability.reviewStatus && capability.reviewStatus !== 'NONE' && <strong class="capability-review">{pretty(capability.reviewStatus)}</strong>}
    {capability.reasons.length > 0 && <ul>{capability.reasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul>}
  </div>;
}

function normalizeCapability(value: DiagnosticCapability): DiagnosticCapability {
  const raw = value && typeof value === 'object' ? value : {} as DiagnosticCapability;
  return {
    capability: typeof raw.capability === 'string' ? raw.capability : 'UNRESOLVED_CAPABILITY',
    status: typeof raw.status === 'string' ? raw.status : 'NOT_FOUND',
    confidence: typeof raw.confidence === 'number' ? raw.confidence : Number.NaN,
    offset: typeof raw.offset === 'number' ? raw.offset : null,
    count: typeof raw.count === 'number' ? raw.count : null,
    recordSize: typeof raw.recordSize === 'number' ? raw.recordSize : null,
    elementSize: typeof raw.elementSize === 'number' ? raw.elementSize : null,
    validRecords: typeof raw.validRecords === 'number' ? raw.validRecords : null,
    totalRecords: typeof raw.totalRecords === 'number' ? raw.totalRecords : null,
    coveredRecords: typeof raw.coveredRecords === 'number' ? raw.coveredRecords : null,
    expectedRecords: typeof raw.expectedRecords === 'number' ? raw.expectedRecords : null,
    incompleteRecords: typeof raw.incompleteRecords === 'number' ? raw.incompleteRecords : null,
    reviewStatus: typeof raw.reviewStatus === 'string' ? raw.reviewStatus : null,
    reasons: Array.isArray(raw.reasons) ? raw.reasons.filter(reason => typeof reason === 'string') : [],
  };
}

function displayStatus(capability: DiagnosticCapability): string {
  if (capability.status === 'NOT_APPLICABLE' || capability.status === 'N/A') return 'N/A';
  if (capability.status === 'AMBIGUOUS') return 'AMBIGUOUS';
  if (capability.status === 'PARTIAL' || (capability.status === 'AVAILABLE' && capability.reviewStatus === 'MANUAL_REVIEW') || (
    capability.status === 'AVAILABLE' && capability.validRecords != null && capability.totalRecords != null && capability.validRecords < capability.totalRecords
  ) || (
    capability.status === 'AVAILABLE' && capability.coveredRecords != null && capability.expectedRecords != null && capability.coveredRecords < capability.expectedRecords
  )) return 'PARTIAL';
  if (capability.status === 'NOT_FOUND') return 'NOT FOUND';
  return pretty(capability.status || 'NOT_FOUND');
}

function displayStatusLabel(status: string): string {
  if (status === 'AVAILABLE') return msg('statusAvailable');
  if (status === 'AMBIGUOUS') return msg('statusAmbiguous');
  if (status === 'PARTIAL') return msg('statusPartial');
  if (status === 'NOT FOUND') return msg('statusNotFound');
  return pretty(status);
}

function coverageText(capability: DiagnosticCapability): string | null {
  const covered = capability.coveredRecords ?? capability.validRecords;
  const expected = capability.expectedRecords ?? capability.totalRecords;
  if (covered == null || expected == null || expected <= 0) return null;
  return msg(
    'recordsCoverage',
    formatUiNumber(covered),
    formatUiNumber(expected),
    formatUiNumber(covered / expected * 100, { minimumFractionDigits: 1, maximumFractionDigits: 1 }),
  );
}

function rulesetLabel(view: DiagnosticView): string {
  if (!view.activeRulesetId) return msg('rulesetUnavailable');
  return view.rulesets.find(item => item.id === view.activeRulesetId)?.label
    ? renderPresentationMessage(view.rulesets.find(item => item.id === view.activeRulesetId)!.label)
    : view.activeRulesetId;
}

function pretty(value: string): string {
  return value.replaceAll('_', ' ');
}

export function stableReport(view: DiagnosticView): string {
  const capabilities = view.capabilities.map(normalizeCapability).map(capability => ({
    ...capability,
    reasons: capability.reasons.map(sanitizeText),
  }));
  const map = view.map ? {
    presentation: view.map.presentation,
    playerPositionStatus: view.map.playerPositionStatus,
    lighting: view.map.lighting,
    totalPois: view.map.totalPois,
    visiblePois: view.map.visiblePois,
    collectedPois: view.map.collectedPois,
    localMapStatus: view.map.localMapStatus,
    worldMapStatus: view.map.worldMapStatus,
    fallbackReason: sanitizeNullable(view.map.fallbackReason),
  } : null;
  return JSON.stringify({
    reportSchemaVersion: 2,
    family: view.family,
    platform: view.platform,
    activeRulesetId: view.activeRulesetId,
    rulesetAssumed: view.rulesetAssumed,
    rulesets: view.rulesets,
    environment: view.environment ?? null,
    runtime: view.runtime ?? null,
    map,
    cache: view.cache ?? null,
    capabilities,
    parserDiagnostics: view.parserDiagnostics.map(sanitizeText),
    privacy: view.privacy ?? {
      containsRomBytes: false,
      containsMemoryBytes: false,
      containsSaveData: false,
      containsPrivatePaths: false,
    },
  }, null, 2);
}

function sanitizeNullable(value: string | null): string | null {
  return value == null ? null : sanitizeText(value);
}

function sanitizeText(value: string): string {
  return value.replace(/(?:[A-Za-z]:[\\/]|\\\\[^\\/\r\n]+[\\/]|\/(?:data|storage|sdcard|home|Users|private|var|tmp|mnt|media|Volumes)\/|(?:content|file):\/\/)[^\r\n]*/gi, '[path omitted]');
}
