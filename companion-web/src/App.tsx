import type { ComponentType } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { action, bootstrap, events, uploadRom } from './gateway';
import type { Bootstrap, Catalog, State } from './models';
import { PokedexBrowse } from './pages/PokedexBrowse';
import { PokedexDetail } from './pages/PokedexDetail';
import { BattlePage } from './pages/BattlePage';
import { SettingsPage } from './pages/SettingsPage';
import { MoveDetail } from './pages/MoveDetail';
import { AbilityDetail } from './pages/AbilityDetail';
import { SetupPage } from './pages/SetupPage';
import { MemoryMapperPage } from './pages/MemoryMapperPage';

export interface DevelopmentToolsProps {
  catalog: Catalog | null;
  state: State;
  onUpload: (file: File) => void;
  send: (type: string, values?: Record<string, string | number | boolean | null>) => void;
}

const emptyState: State = {
  version: 0,
  screen: 'POKEDEX',
  priorScreen: 'POKEDEX',
  settingsReturnScreen: 'POKEDEX',
  selectedSpeciesId: null,
  filter: 'ALL',
  selectedAreaId: null,
  battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'GAME', displayTarget: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: false, catalogName: null, error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'IDLE', completedUnits: 0, totalUnits: 0 },
  retroArch: { storageGrant: 'MISSING', configGrant: 'MISSING', romGrant: 'MISSING', configState: 'NOT_CONFIGURED', restartRequired: false, connection: 'DISCONNECTED', systemId: null, gameBasename: null, contentCrc32: null, resolution: 'NO_CONTENT', activeSource: null, savefileDirectory: null, indexedRoms: 0, message: null }
};

export function App({ DevelopmentTools }: { DevelopmentTools?: ComponentType<DevelopmentToolsProps> } = {}) {
  const showDevelopmentTools = DevelopmentTools != null;
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [state, setState] = useState<State>(emptyState);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [moveDetailId, setMoveDetailId] = useState<number | null>(null);
  const [abilityDetailId, setAbilityDetailId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<'ENTRY' | 'STATS' | 'MOVES' | 'MORE'>('ENTRY');
  const [mapperOpen, setMapperOpen] = useState(false);
  const lastCatalogRefresh = useRef('');
  const loadingPercent = loadingPercentage(state.loading);

  useEffect(() => {
    bootstrap().then(applyBootstrap).catch(failure => setError(failure.message)).finally(() => setBusy(false));
    return events(incoming => {
      setState(current => incoming.version >= current.version ? incoming : current);
      const marker = catalogRefreshMarker(incoming);
      if (marker && marker !== lastCatalogRefresh.current) {
        lastCatalogRefresh.current = marker;
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
    if (mapperOpen) return <MemoryMapperPage onBack={() => setMapperOpen(false)} />;
    if (state.screen === 'SETUP') return <SetupPage state={state} send={send} />;
    if (!catalog) return <Welcome busy={busy || state.loading.active} error={error} onUpload={onUpload} openSetup={() => void send('SCREEN', { screen: 'SETUP' })} />;
    if (moveDetailId != null) return <MoveDetail catalog={catalog} state={state} moveId={moveDetailId} onBack={() => setMoveDetailId(null)} />;
    if (abilityDetailId != null) return <AbilityDetail catalog={catalog} state={state} abilityId={abilityDetailId} onBack={() => setAbilityDetailId(null)} />;
    switch (state.screen) {
      case 'DETAIL': return <PokedexDetail catalog={catalog} state={state} send={send} tab={detailTab} setTab={setDetailTab} openMove={setMoveDetailId} openAbility={setAbilityDetailId} />;
      case 'BATTLE': return state.battle ? <BattlePage catalog={catalog} state={state} send={send} openMove={setMoveDetailId} openSpecies={speciesId => {
        setDetailTab('ENTRY');
        void send('OPEN_SPECIES', { speciesId });
      }} /> : <PokedexBrowse catalog={catalog} state={state} send={send} />;
      case 'SETTINGS': return <SettingsPage catalog={catalog} state={state} send={send} onUpload={onUpload} onOpenMapper={() => setMapperOpen(true)} />;
      default: return <PokedexBrowse catalog={catalog} state={state} send={send} />;
    }
  }, [catalog, state, busy, error, moveDetailId, abilityDetailId, detailTab, mapperOpen]);

  return <main class={showDevelopmentTools ? 'lab-shell' : 'production-shell'}>
    {DevelopmentTools && <DevelopmentTools catalog={catalog} state={state} onUpload={onUpload} send={send} />}
    <div class={showDevelopmentTools ? 'device-shell' : 'production-device'} style={{ '--font-scale': state.settings.fontScale }} data-density={state.settings.density.toLowerCase()} data-contrast={state.settings.highContrast ? 'high' : 'normal'} data-theme={(state.settings.theme ?? 'GAME').toLowerCase()}>
      {showDevelopmentTools && <div class="device-sensor" />}
      <div class="device-screen">
        {catalog && <div class="rom-status" title={state.catalogName ?? undefined}><strong>{state.catalogName ?? 'Unnamed ROM'}</strong><span>{catalog.family.replaceAll('_', ' ')} · CRC32 {catalog.crc32 || 'N/F'}</span></div>}
        <div class={catalog ? 'screen-host with-rom-status' : 'screen-host'}>{screen}</div>
        {state.loading.active && <div class="loading-indicator" role="status" aria-label={`Loading ${state.loading.phase}${loadingPercent == null ? '' : ` (${loadingPercent}%)`}`}><span>Loading</span><i />{loadingPercent != null && <b> ({loadingPercent}%)</b>}</div>}{error && catalog && <div class="error-toast" role="alert">{error}</div>}
      </div>
    </div>
  </main>;
}

export function loadingPercentage(loading: State['loading']): number | null {
  if (loading.totalUnits <= 0) return null;
  return Math.round(Math.min(1, Math.max(0, loading.completedUnits / loading.totalUnits)) * 100);
}

export function catalogRefreshMarker(state: Pick<State, 'catalogName' | 'loading'>): string {
  if (state.loading.completedUnits <= 0) return '';
  return `${state.catalogName ?? ''}:${state.loading.phase}:${state.loading.completedUnits}:${state.loading.totalUnits}`;
}

function Welcome({ busy, error, onUpload, openSetup }: { busy: boolean; error: string | null; onUpload: (file: File) => void; openSetup: () => void }) {
  return <section class="screen welcome-screen"><div class="welcome-mark"><span /><i /></div><p class="eyebrow">PASSIVE RETROARCH COMPANION</p><h1>DUALDEX</h1><p>Load a Game Boy, Game Boy Color, or Game Boy Advance Pokémon ROM. Its own Pokédex, moves, types, areas and artwork become the companion.</p><div class="welcome-actions"><label class="welcome-upload"><span>{busy ? 'CHECKING SERVER' : 'LOAD ROM OR ZIP'}</span><input disabled={busy} type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label><button type="button" onClick={openSetup}>CONNECT RETROARCH</button></div>{error && <div class="welcome-error">{error}</div>}<small>ROM bytes and extracted assets stay local.</small></section>;
}
