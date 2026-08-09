import { useState } from 'preact/hooks';
import type { Catalog, State } from '../models';

export function SimulatorPanel({ catalog, state, onUpload, send }: { catalog: Catalog | null; state: State; onUpload: (file: File) => void; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const [seed, setSeed] = useState(151);
  const [count, setCount] = useState(1);
  const [minimumLevel, setMinimum] = useState(22);
  const [maximumLevel, setMaximum] = useState(42);
  const [captured, setCaptured] = useState(false);
  const [areaId, setArea] = useState<number | null>(null);
  return <aside class="simulator-panel">
    <div><small>DUALDEX LAB</small><h2>Encounter feed</h2><p>Only the live-memory source is simulated. Every name, sprite, type, move and matchup comes from the loaded ROM.</p></div>
    <label class="file-picker"><span>{catalog ? 'CHANGE ROM / ZIP' : 'LOAD ROM / ZIP'}</span><input type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label>
    <div class="sim-grid"><label>SEED<input type="number" value={seed} onInput={event => setSeed(Number(event.currentTarget.value))} /></label><label>OPPONENTS<select value={count} onChange={event => setCount(Number(event.currentTarget.value))}><option value="1">Single</option><option value="2">Double</option></select></label><label>MIN LEVEL<input type="number" min="1" max="100" value={minimumLevel} onInput={event => setMinimum(Number(event.currentTarget.value))} /></label><label>MAX LEVEL<input type="number" min="1" max="100" value={maximumLevel} onInput={event => setMaximum(Number(event.currentTarget.value))} /></label></div>
    <label class="sim-check"><input type="checkbox" checked={captured} onChange={event => setCaptured(event.currentTarget.checked)} /><i /><span>Already recruited</span></label>
    <label class="sim-area">AREA<select value={areaId ?? ''} onChange={event => setArea(event.currentTarget.value ? Number(event.currentTarget.value) : null)}><option value="">Any parsed area</option>{catalog?.areas.map(area => <option key={area.id} value={area.id}>{area.name}</option>)}</select></label>
    <button class="generate-button" disabled={!catalog} onClick={() => send('GENERATE', { seed, count, minimumLevel, maximumLevel, captured, areaId })}>GENERATE ENCOUNTER</button>
    {state.battle && <button class="end-button" onClick={() => send('END_BATTLE')}>END BATTLE</button>}
    <footer><span class={catalog ? 'ready-dot' : ''} />{catalog ? `${catalog.species.length} species · ${catalog.moves.length} moves` : 'Waiting for a ROM'}</footer>
  </aside>;
}
