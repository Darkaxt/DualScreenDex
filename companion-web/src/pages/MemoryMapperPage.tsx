import { useEffect, useRef, useState } from 'preact/hooks';
import { Header } from '../components';
import { formatUiNumber, msg } from '../i18n';
import { mapperAction, mapperExport, mapperState, type MapperState } from '../mapperGateway';

const labels = ['OVERWORLD', 'BATTLE_START', 'MOVE_SELECTED', 'MOVE_EXECUTED', 'TARGET_CHANGED', 'OPPONENT_SWITCHED', 'BATTLE_END'];
const POLL_INTERVAL_MILLIS = 500;
const MAX_RETRY_MILLIS = 8_000;
const POLL_TIMEOUT_MILLIS = 10_000;

function safeErrorMessage(failure: unknown): string {
  return failure instanceof Error && failure.message.length > 0 && failure.message.length <= 256
    ? failure.message
    : msg('mapperRequestFailed');
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
    if (window.confirm(msg('enableMemoryCaptureConfirm'))) {
      void act('ENABLE', { privacyAcknowledged: true });
    }
  };

  const progress = state?.totalBytes ? Math.round(state.completedBytes / state.totalBytes * 100) : 0;
  return <section class="screen mapper-screen">
    <Header title={msg('memoryMapper')} kicker={msg('readOnlyDebugLab')} onBack={onBack} />
    <div class="mapper-content" data-scroll-region>
      <section class="mapper-warning"><strong>{msg('issueReportMemoryCapture')}</strong><p>{msg('memoryCaptureDescription')}</p></section>
      {!state?.enabled ? <section class="paper-panel mapper-enable"><button class="primary-button" type="button" onClick={enable}>{msg('enableForSession')}</button></section> : <>
        <section class="mapper-identity"><span><small>{msg('core')}</small><strong>{state.coreIdentity ?? '—'}</strong></span><span><small>{msg('content')}</small><strong>{state.contentIdentity ?? '—'}</strong></span><button type="button" onClick={() => void act('DISABLE')}>{msg('disable')}</button></section>
        <section class="paper-panel mapper-capture"><p class="eyebrow">{msg('labelSnapshot')}</p><div class="mapper-labels">{labels.map(label => <button type="button" disabled={state.captureLabel != null} onClick={() => void act('CAPTURE', { label })}>{mapperLabel(label)}</button>)}</div><div class="mapper-custom"><input aria-label={msg('customMapperLabel')} value={customLabel} placeholder={msg('customLabel')} onInput={event => setCustomLabel(event.currentTarget.value)} /><button type="button" disabled={!customLabel.trim() || state.captureLabel != null} onClick={() => void act('CAPTURE', { label: 'CUSTOM', customLabel })}>{msg('capture')}</button></div>{state.captureLabel && <p class="mapper-progress" role="status">{msg('readingSnapshot', mapperLabel(state.captureLabel), formatUiNumber(progress))}</p>}</section>
      </>}
      <section class="paper-panel mapper-history"><div class="section-heading"><p class="eyebrow">{msg('sessionSnapshots')}</p><strong>{formatUiNumber(state?.snapshots.length ?? 0)}</strong></div>{state?.latestDiff && <p class="mapper-diff">{msg('latestDiff', formatUiNumber(state.latestDiff.changedBytes), formatUiNumber(state.latestDiff.ranges))}{state.latestDiff.omittedRanges ? ` · ${msg('omittedRanges', formatUiNumber(state.latestDiff.omittedRanges))}` : ''}</p>}{state?.snapshots.map(snapshot => <div class="mapper-snapshot"><strong>{snapshot.customLabel ?? mapperLabel(snapshot.label)}</strong><span>{msg('bytesValue', formatUiNumber(snapshot.bytes))}</span></div>)}</section>
      <section class="paper-panel mapper-export"><button type="button" disabled={!state?.privacyAcknowledged || !state?.snapshots.length} onClick={() => void download()}>{msg('exportRawSession')}</button><button type="button" class="danger-button" onClick={() => void act('CLEAR_SESSIONS')}>{msg('clearMapperSessions')}</button></section>
      {error || state?.error ? <p class="mapper-error" role="alert">{error ?? state?.error}</p> : null}
    </div>
  </section>;
}

function mapperLabel(label: string): string {
  const localized: Record<string, string> = {
    OVERWORLD: msg('mapperOverworld'),
    BATTLE_START: msg('mapperBattleStart'),
    MOVE_SELECTED: msg('mapperMoveSelected'),
    MOVE_EXECUTED: msg('mapperMoveExecuted'),
    TARGET_CHANGED: msg('mapperTargetChanged'),
    OPPONENT_SWITCHED: msg('mapperOpponentSwitched'),
    BATTLE_END: msg('mapperBattleEnd'),
  };
  return localized[label] ?? label.replaceAll('_', ' ');
}
