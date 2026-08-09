import { useEffect, useMemo, useState } from 'preact/hooks';
import { action, bootstrap, events, uploadRom } from './gateway';
import type { Bootstrap, Catalog, State } from './models';
import { PokedexBrowse } from './pages/PokedexBrowse';
import { PokedexDetail } from './pages/PokedexDetail';
import { BattlePage } from './pages/BattlePage';
import { SettingsPage } from './pages/SettingsPage';
import { MoveDetail } from './pages/MoveDetail';
import { AbilityDetail } from './pages/AbilityDetail';
import { SimulatorPanel } from './dev/SimulatorPanel';

const emptyState: State = {
  version: 0,
  screen: 'POKEDEX',
  priorScreen: 'POKEDEX',
  settingsReturnScreen: 'POKEDEX',
  selectedSpeciesId: null,
  filter: 'ALL',
  selectedAreaId: null,
  battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: false, catalogName: null, error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'IDLE', completedUnits: 0, totalUnits: 0 }
};

export function App() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [state, setState] = useState<State>(emptyState);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [moveDetailId, setMoveDetailId] = useState<number | null>(null);
  const [abilityDetailId, setAbilityDetailId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<'ENTRY' | 'STATS' | 'MOVES' | 'MORE'>('ENTRY');

  useEffect(() => {
    bootstrap().then(applyBootstrap).catch(failure => setError(failure.message)).finally(() => setBusy(false));
    return events(incoming => {
      setState(current => incoming.version >= current.version ? incoming : current);
      if (incoming.loading.completedUnits > 0) {
        bootstrap().then(applyBootstrap).catch(failure => setError(failure.message));
      }
    });
  }, []);

  const applyBootstrap = (value: Bootstrap) => {
    setCatalog(value.catalog);
    setState(current => value.state.version >= current.version ? value.state : current);
    setError(null);
  };

  const send = async (type: string, values: Record<string, string | number | boolean | null> = {}) => {
    try {
      setState(await action(type, values));
      setError(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : String(failure));
    }
  };

  const onUpload = async (file: File) => {
    setBusy(true);
    try { applyBootstrap(await uploadRom(file)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : String(failure)); }
    finally { setBusy(false); }
  };

  const screen = useMemo(() => {
    if (!catalog) return <Welcome busy={busy || state.loading.active} error={error} onUpload={onUpload} />;
    if (moveDetailId != null) return <MoveDetail catalog={catalog} state={state} moveId={moveDetailId} onBack={() => setMoveDetailId(null)} />;
    if (abilityDetailId != null) return <AbilityDetail catalog={catalog} state={state} abilityId={abilityDetailId} onBack={() => setAbilityDetailId(null)} />;
    switch (state.screen) {
      case 'DETAIL': return <PokedexDetail catalog={catalog} state={state} send={send} tab={detailTab} setTab={setDetailTab} openMove={setMoveDetailId} openAbility={setAbilityDetailId} />;
      case 'BATTLE': return state.battle ? <BattlePage catalog={catalog} state={state} send={send} openMove={setMoveDetailId} openSpecies={speciesId => {
        setDetailTab('ENTRY');
        void send('OPEN_SPECIES', { speciesId });
      }} /> : <PokedexBrowse catalog={catalog} state={state} send={send} />;
      case 'SETTINGS': return <SettingsPage catalog={catalog} state={state} send={send} />;
      default: return <PokedexBrowse catalog={catalog} state={state} send={send} />;
    }
  }, [catalog, state, busy, error, moveDetailId, abilityDetailId, detailTab]);

  return <main class="lab-shell">
    <SimulatorPanel catalog={catalog} state={state} onUpload={onUpload} send={send} />
    <div class="device-shell" style={{ '--font-scale': state.settings.fontScale }} data-density={state.settings.density.toLowerCase()} data-contrast={state.settings.highContrast ? 'high' : 'normal'}>
      <div class="device-sensor" />
      <div class="device-screen">{screen}{state.loading.active && <div class="loading-indicator" role="status" aria-label={`Loading ${state.loading.phase}`}><span>Loading</span><i /></div>}{error && catalog && <div class="error-toast" role="alert">{error}</div>}</div>
    </div>
  </main>;
}

function Welcome({ busy, error, onUpload }: { busy: boolean; error: string | null; onUpload: (file: File) => void }) {
  return <section class="screen welcome-screen"><div class="welcome-mark"><span /><i /></div><p class="eyebrow">PASSIVE RETROARCH COMPANION</p><h1>DUALDEX</h1><p>Load a Game Boy, Game Boy Color, or Game Boy Advance Pokémon ROM. Its own Pokédex, moves, types, areas and artwork become the companion.</p><label class="welcome-upload"><span>{busy ? 'CHECKING SERVER' : 'LOAD ROM OR ZIP'}</span><input disabled={busy} type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label>{error && <div class="welcome-error">{error}</div>}<small>ROM bytes and extracted assets stay local.</small></section>;
}
