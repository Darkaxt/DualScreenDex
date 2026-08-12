import { useEffect, useState } from 'preact/hooks';
import { Header } from '../components';
import { mapperAction, mapperExport, mapperState, type MapperState } from '../mapperGateway';

const labels = ['OVERWORLD', 'BATTLE_START', 'MOVE_SELECTED', 'MOVE_EXECUTED', 'TARGET_CHANGED', 'OPPONENT_SWITCHED', 'BATTLE_END'];

export function MemoryMapperPage({ onBack }: { onBack: () => void }) {
  const [state, setState] = useState<MapperState | null>(null);
  const [customLabel, setCustomLabel] = useState('');
  const [error, setError] = useState<string | null>(null);

  const refresh = () => mapperState().then(setState).catch(failure => setError(failure.message));
  const act = (type: string, values: Record<string, string | boolean | null> = {}) => mapperAction(type, values).then(value => { setState(value); setError(null); }).catch(failure => setError(failure.message));
  useEffect(() => {
    void refresh();
    const interval = window.setInterval(refresh, 500);
    return () => window.clearInterval(interval);
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
    } catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
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
