import { useEffect, useState } from 'preact/hooks';
import { Header } from '../components';
import { diagnostics } from '../gateway';
import type { DiagnosticCapability, DiagnosticView } from '../models';

export function CapabilityReportPage({ romHash, refreshMarker, onBack }: { romHash: string; refreshMarker: string; onBack: () => void }) {
  const [view, setView] = useState<DiagnosticView | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const currentView = view && view.sha256.toLowerCase() === romHash.toLowerCase() ? view : null;

  useEffect(() => {
    let current = true;
    diagnostics().then(next => {
      if (!current) return;
      setView(next);
      setError(null);
      setExpanded(new Set());
    }).catch(failure => {
      if (current) setError(failure instanceof Error ? failure.message : String(failure));
    });
    return () => { current = false; };
  }, [romHash, refreshMarker, reloadKey]);

  const toggle = (index: number) => setExpanded(current => {
    const next = new Set(current);
    if (next.has(index)) next.delete(index); else next.add(index);
    return next;
  });

  const copy = async () => {
    if (!currentView) return;
    try {
      await navigator.clipboard.writeText(stableReport(currentView));
      setCopyStatus('REPORT COPIED');
    } catch (failure) {
      setCopyStatus(failure instanceof Error ? failure.message : 'COPY FAILED');
    }
  };

  return <section class="screen capability-screen">
    <Header title="COMPATIBILITY REPORT" kicker="LOADED ROM · READ ONLY" onBack={onBack} />
    <div class="capability-content" data-scroll-region>
      {currentView && <>
        <section class="capability-identity">
          <p class="eyebrow">ACTIVE GAME</p>
          <strong>{currentView.romName ?? 'Unnamed ROM'}</strong>
          <span>{pretty(currentView.family)} · {currentView.platform}</span>
          <span>CRC32 {currentView.crc32 ? currentView.crc32.toUpperCase() : 'N/F'} · SHA-256 {currentView.sha256 ? currentView.sha256.slice(0, 12).toUpperCase() : 'N/F'}</span>
          <span>{rulesetLabel(currentView)}{currentView.rulesetAssumed ? ' · ASSUMED' : ''}</span>
        </section>
        <CompatibilitySummary view={currentView} />
        <section class="capability-list" aria-label="ROM capabilities">
          {Array.isArray(currentView.capabilities) && currentView.capabilities.map((raw, index) => {
            const capability = normalizeCapability(raw);
            const status = displayStatus(capability);
            const coverage = coverageText(capability);
            const open = expanded.has(index);
            return <article class={`capability-card capability-${status.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`} key={`${capability.capability}-${index}`}>
              <button type="button" aria-expanded={open} onClick={() => toggle(index)} aria-label={`${pretty(capability.capability)} ${status}${coverage ? ` ${coverage}` : ''}`}>
                <span><strong>{pretty(capability.capability)}</strong>{coverage && <small>{coverage}</small>}</span>
                <b>{status}</b><i aria-hidden="true" />
              </button>
              {open && <CapabilityDetails capability={capability} status={status} />}
            </article>;
          })}
        </section>
        <section class="capability-actions">
          <button type="button" onClick={() => void copy()}>COPY REPORT</button>
          <a href="dualdex://compatibility/export">EXPORT REPORT</a>
          {copyStatus && <span role="status">{copyStatus}</span>}
        </section>
      </>}
      {!currentView && !error && <p class="capability-loading" role="status">LOADING CAPABILITIES</p>}
      {error && <section class="paper-panel capability-error"><p role="alert">{error}</p><button type="button" onClick={() => setReloadKey(value => value + 1)}>RETRY</button></section>}
    </div>
  </section>;
}

function CompatibilitySummary({ view }: { view: DiagnosticView }) {
  if (!view.runtime && !view.map && !view.cache && !view.environment) return null;
  return <section class="compatibility-summary" aria-label="Current compatibility">
    {view.runtime && <article>
      <p class="eyebrow">RUNTIME</p>
      <strong>{pretty(view.runtime.retroArchConnection)} · {view.runtime.gameAccessReady ? 'GAME ACCESS READY' : 'WAITING FOR GAME ACCESS'}</strong>
      <span>{pretty(view.runtime.contentResolution)} · SAVE {pretty(view.runtime.saveRamStatus)}</span>
    </article>}
    {view.map && <article>
      <p class="eyebrow">CURRENT MAP</p>
      <strong>{pretty(view.map.presentation)}</strong>
      <span>{view.map.currentAreaName ?? 'AREA UNAVAILABLE'} · PLAYER {pretty(view.map.playerPositionStatus)}</span>
      <span>{pretty(view.map.lighting)} · POIS {view.map.visiblePois}/{view.map.totalPois}</span>
      {view.map.fallbackReason && <span>FALLBACK {pretty(view.map.fallbackReason)}</span>}
    </article>}
    {view.cache && <article>
      <p class="eyebrow">MAP CACHE</p>
      <strong>{view.cache.entries} RASTERS · {view.cache.renders} RENDERS</strong>
      <span>{view.cache.hits} hits · {view.cache.evictions} evictions · {view.cache.encodedBytes} bytes</span>
    </article>}
    {view.environment && <article>
      <p class="eyebrow">REPORT CONTRACT</p>
      <strong>{view.environment.appVersion ?? 'APP VERSION UNAVAILABLE'}</strong>
      <span>Catalog {view.environment.catalogSchemaVersion} · Parser {view.environment.parserSchemaVersion} · Report {view.reportSchemaVersion ?? 1}</span>
    </article>}
  </section>;
}

function CapabilityDetails({ capability, status }: { capability: DiagnosticCapability; status: string }) {
  const absent = status === 'N/A' ? 'N/A' : 'N/F';
  const value = (entry: string | number | null | undefined, suffix = '') => entry == null || entry === '' ? absent : `${entry}${suffix}`;
  return <div class="capability-details">
    <dl>
      <div><dt>CONFIDENCE</dt><dd>{Number.isFinite(capability.confidence) ? `${(capability.confidence * 100).toFixed(1)}%` : absent}</dd></div>
      <div><dt>ROM OFFSET</dt><dd>{capability.offset == null ? absent : `0x${capability.offset.toString(16).toUpperCase()}`}</dd></div>
      <div><dt>VALID / TOTAL</dt><dd>{capability.validRecords == null || capability.totalRecords == null ? absent : `${capability.validRecords} / ${capability.totalRecords}`}</dd></div>
      <div><dt>COVERED / EXPECTED</dt><dd>{capability.coveredRecords == null || capability.expectedRecords == null ? absent : `${capability.coveredRecords} / ${capability.expectedRecords}`}</dd></div>
      <div><dt>INCOMPLETE</dt><dd>{value(capability.incompleteRecords)}</dd></div>
      <div><dt>COUNT</dt><dd>{value(capability.count)}</dd></div>
      <div><dt>RECORD SIZE</dt><dd>{value(capability.recordSize, ' bytes')}</dd></div>
      <div><dt>ELEMENT SIZE</dt><dd class="capability-element-size">{value(capability.elementSize, ' bytes')}</dd></div>
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

function coverageText(capability: DiagnosticCapability): string | null {
  const covered = capability.coveredRecords ?? capability.validRecords;
  const expected = capability.expectedRecords ?? capability.totalRecords;
  if (covered == null || expected == null || expected <= 0) return null;
  return `${covered} / ${expected} records · ${(covered / expected * 100).toFixed(1)}%`;
}

function rulesetLabel(view: DiagnosticView): string {
  if (!view.activeRulesetId) return 'RULESET N/F';
  return view.rulesets.find(item => item.id === view.activeRulesetId)?.label ?? view.activeRulesetId;
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
