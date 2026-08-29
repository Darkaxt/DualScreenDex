import { useEffect, useRef, useState } from 'preact/hooks';
import { Header } from '../components';
import { mapperAction, mapperExport, mapperState, type MapperState } from '../mapperGateway';

const labels = ['OVERWORLD', 'BATTLE_START', 'MOVE_SELECTED', 'MOVE_EXECUTED', 'TARGET_CHANGED', 'OPPONENT_SWITCHED', 'BATTLE_END'];
const POLL_INTERVAL_MILLIS = 500;
const MAX_RETRY_MILLIS = 8_000;
const POLL_TIMEOUT_MILLIS = 10_000;

function safeErrorMessage(failure: unknown): string {
  return failure instanceof Error && failure.message.length > 0 && failure.message.length <= 256
    ? failure.message
    : 'Memory mapper request failed.';
}

export function MemoryMapperPage({ onBack }: { onBack: () => void }) {
  const [state, setState] = useState<MapperState | null>(null);
  const [customLabel, setCustomLabel] = useState('');
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const pollControllerRef = useRef<AbortController | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const pollGenerationRef = useRef(0);
  const activeActionCountRef = useRef(0);
  const schedulePollRef = useRef<(delay: number) => void>(() => undefined);

  const act = (type: string, values: Record<string, string | boolean | null> = {}) => {
    const generation = ++pollGenerationRef.current;
    activeActionCountRef.current += 1;
    if (pollTimerRef.current != null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    pollControllerRef.current?.abort();
    return mapperAction(type, values)
      .then(value => {
        if (!mountedRef.current || generation !== pollGenerationRef.current) return;
        setState(value);
        setError(null);
      })
      .catch(failure => {
        if (mountedRef.current && generation === pollGenerationRef.current) setError(safeErrorMessage(failure));
      })
      .finally(() => {
        activeActionCountRef.current -= 1;
        if (mountedRef.current && generation === pollGenerationRef.current && activeActionCountRef.current === 0) {
          schedulePollRef.current(POLL_INTERVAL_MILLIS);
        }
      });
  };
  useEffect(() => {
    let stopped = false;
    let failures = 0;
    let poll: () => Promise<void>;
    mountedRef.current = true;

    const schedule = (delay: number) => {
      if (stopped) return;
      if (pollTimerRef.current != null) window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = window.setTimeout(() => {
        pollTimerRef.current = null;
        void poll();
      }, delay);
    };
    schedulePollRef.current = schedule;
    poll = async () => {
      if (stopped || activeActionCountRef.current > 0) return;
      const generation = pollGenerationRef.current;
      const controller = new AbortController();
      pollControllerRef.current = controller;
      const timeout = window.setTimeout(() => controller.abort(), POLL_TIMEOUT_MILLIS);
      try {
        const value = await mapperState(controller.signal);
        if (stopped || generation !== pollGenerationRef.current || activeActionCountRef.current > 0) return;
        failures = 0;
        setState(value);
        setError(null);
        schedule(POLL_INTERVAL_MILLIS);
      } catch (failure) {
        if (stopped || generation !== pollGenerationRef.current || activeActionCountRef.current > 0) return;
        failures += 1;
        setError(safeErrorMessage(failure));
        schedule(Math.min(MAX_RETRY_MILLIS, POLL_INTERVAL_MILLIS * 2 ** failures));
      } finally {
        window.clearTimeout(timeout);
        if (pollControllerRef.current === controller) pollControllerRef.current = null;
      }
    };

    void poll();
    return () => {
      stopped = true;
      mountedRef.current = false;
      schedulePollRef.current = () => undefined;
      if (pollTimerRef.current != null) {
        window.clearTimeout(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      pollControllerRef.current?.abort();
      pollControllerRef.current = null;
    };
  }, []);

  const download = async () => {
    try {
      if (/Android/i.test(navigator.userAgent)) {
        window.location.href = 'dualdex://mapper/export';
        return;
      }
      const blob = await mapperExport();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = 'dualdex-memory-session.json'; anchor.click();
      URL.revokeObjectURL(url);
      setError(null);
    } catch (failure) { setError(safeErrorMessage(failure)); }
  };

  const enable = () => {
    if (window.confirm('Enable read-only memory capture for this app session? Exported sessions include raw emulator memory.')) {
      void act('ENABLE', { privacyAcknowledged: true });
    }
  };

  const progress = state?.totalBytes ? Math.round(state.completedBytes / state.totalBytes * 100) : 0;
  return <section class="screen mapper-screen">
    <Header title="MEMORY MAPPER" kicker="READ-ONLY DEBUG LAB" onBack={onBack} />
    <div class="mapper-content" data-scroll-region>
      <section class="mapper-warning"><strong>ISSUE REPORT MEMORY CAPTURE</strong><p>This optional tool records read-only RetroArch memory for diagnosing unsupported battle layouts. Normal battle detection remains independent, and capture starts disabled after every app launch.</p></section>
      {!state?.enabled ? <section class="paper-panel mapper-enable"><button class="primary-button" type="button" onClick={enable}>ENABLE FOR THIS SESSION</button></section> : <>
        <section class="mapper-identity"><span><small>CORE</small><strong>{state.coreIdentity ?? '—'}</strong></span><span><small>CONTENT</small><strong>{state.contentIdentity ?? '—'}</strong></span><button type="button" onClick={() => void act('DISABLE')}>DISABLE</button></section>
        <section class="paper-panel mapper-capture"><p class="eyebrow">LABEL A SNAPSHOT</p><div class="mapper-labels">{labels.map(label => <button type="button" disabled={state.captureLabel != null} onClick={() => void act('CAPTURE', { label })}>{label.replaceAll('_', ' ')}</button>)}</div><div class="mapper-custom"><input aria-label="Custom mapper label" value={customLabel} placeholder="CUSTOM LABEL" onInput={event => setCustomLabel(event.currentTarget.value)} /><button type="button" disabled={!customLabel.trim() || state.captureLabel != null} onClick={() => void act('CAPTURE', { label: 'CUSTOM', customLabel })}>CAPTURE</button></div>{state.captureLabel && <p class="mapper-progress" role="status">READING {state.captureLabel.replaceAll('_', ' ')} · {progress}%</p>}</section>
      </>}
      <section class="paper-panel mapper-history"><div class="section-heading"><p class="eyebrow">SESSION SNAPSHOTS</p><strong>{state?.snapshots.length ?? 0}</strong></div>{state?.latestDiff && <p class="mapper-diff">Latest diff: <strong>{state.latestDiff.changedBytes}</strong> bytes in {state.latestDiff.ranges} ranges{state.latestDiff.omittedRanges ? ` · ${state.latestDiff.omittedRanges} omitted` : ''}</p>}{state?.snapshots.map(snapshot => <div class="mapper-snapshot"><strong>{snapshot.customLabel ?? snapshot.label.replaceAll('_', ' ')}</strong><span>{snapshot.bytes.toLocaleString()} bytes</span></div>)}</section>
      <section class="paper-panel mapper-export"><button type="button" disabled={!state?.privacyAcknowledged || !state?.snapshots.length} onClick={() => void download()}>EXPORT RAW SESSION</button><button type="button" class="danger-button" onClick={() => void act('CLEAR_SESSIONS')}>CLEAR MAPPER SESSIONS</button></section>
      {error || state?.error ? <p class="mapper-error" role="alert">{error ?? state?.error}</p> : null}
    </div>
  </section>;
}
