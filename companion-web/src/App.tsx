import type { ComponentType, JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { action, bootstrap, events, uploadRom } from './gateway';
import type { Bootstrap, Catalog, State } from './models';
import { PokedexBrowse } from './pages/PokedexBrowse';
import { PokedexDetail } from './pages/PokedexDetail';
import { BattlePage } from './pages/BattlePage';
import { SettingsPage } from './pages/SettingsPage';
import { MoveDetail } from './pages/MoveDetail';
import { AbilityDetail } from './pages/AbilityDetail';
import { NatureDetail } from './pages/NatureDetail';
import { SetupPage } from './pages/SetupPage';
import { MemoryMapperPage } from './pages/MemoryMapperPage';
import { CapabilityReportPage } from './pages/CapabilityReportPage';
import { MapPage } from './pages/MapPage';
import { TrainerCardPage } from './pages/TrainerCardPage';
import { PartyPage } from './pages/PartyPage';

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
  trainer: null, party: [],
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
  const [natureDetailId, setNatureDetailId] = useState<number | null>(null);
  const [detailTab, setDetailTab] = useState<'ENTRY' | 'STATS' | 'MOVES' | 'AREA' | 'MORE'>('ENTRY');
  const [mapperOpen, setMapperOpen] = useState(false);
  const [capabilityReportOpen, setCapabilityReportOpen] = useState(false);
  const [mapOpen, setMapOpen] = useState(false);
  const [partySelection, setPartySelection] = useState<{ catalogHash: string; slot: number | null }>({ catalogHash: '', slot: null });
  const lastCatalogRefresh = useRef('');

  const reportFailure = (failure: unknown, message: string) => {
    console.error(failure);
    setError(message);
  };

  useEffect(() => {
    bootstrap().then(applyBootstrap).catch(failure => reportFailure(failure, 'The companion could not start. Please try again.')).finally(() => setBusy(false));
    return events(incoming => {
      setState(current => incoming.version >= current.version ? incoming : current);
      const marker = catalogRefreshMarker(incoming);
      if (marker && marker !== lastCatalogRefresh.current) {
        lastCatalogRefresh.current = marker;
        bootstrap().then(applyBootstrap).catch(failure => reportFailure(failure, 'Your game guide could not be refreshed. Please try again.'));
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
      reportFailure(failure, 'That action could not be completed. Please try again.');
    }
  };

  const onUpload = async (file: File) => {
    setBusy(true);
    try { applyBootstrap(await uploadRom(file)); }
    catch (failure) { reportFailure(failure, 'This game could not be opened. Try another file or retry.'); }
    finally { setBusy(false); }
  };

  const loadingLabel = loadingModuleLabel(state.loading.phase);

  useEffect(() => {
    const handleCompanionBack = (event: Event) => {
      const backEvent = event as Event & { dualdexHandled?: boolean };
      event.preventDefault();
      queueMicrotask(() => {
        if (backEvent.dualdexHandled) return;
        if (mapperOpen) setMapperOpen(false);
        else if (capabilityReportOpen) setCapabilityReportOpen(false);
        else if (mapOpen) setMapOpen(false);
        else if (moveDetailId != null) setMoveDetailId(null);
        else if (abilityDetailId != null) setAbilityDetailId(null);
        else if (natureDetailId != null) setNatureDetailId(null);
        else if (state.screen !== 'POKEDEX') void send('BACK');
      });
    };
    window.addEventListener('dualdexback', handleCompanionBack);
    return () => window.removeEventListener('dualdexback', handleCompanionBack);
  }, [abilityDetailId, capabilityReportOpen, mapOpen, mapperOpen, moveDetailId, natureDetailId, state.screen]);

  const screen = useMemo(() => {
    if (mapperOpen) return <MemoryMapperPage onBack={() => setMapperOpen(false)} />;
    if (capabilityReportOpen && catalog) return <CapabilityReportPage romHash={catalog.hash} refreshMarker={catalogRefreshMarker(state)} onBack={() => setCapabilityReportOpen(false)} />;
    if (state.screen === 'SETUP') return <SetupPage state={state} send={send} />;
    if (!catalog) return <Welcome
      busy={busy}
      loading={state.loading}
      loadingLabel={state.loading.active ? loadingLabel : 'Preparing your companion'}
      error={error}
      onUpload={onUpload}
      openSetup={() => void send('SCREEN', { screen: 'SETUP' })}
    />;
    if (mapOpen && (catalog.worldMaps?.length ?? 0) > 0) return <MapPage
      catalog={catalog}
      state={state}
      onOpenPokedex={() => {
        setMapOpen(false);
        void send('SCREEN', { screen: 'POKEDEX' });
      }}
      onOpenSettings={() => {
        setMapOpen(false);
        void send('SCREEN', { screen: 'SETTINGS' });
      }}
    />;
    if (moveDetailId != null) return <MoveDetail catalog={catalog} state={state} moveId={moveDetailId} onBack={() => setMoveDetailId(null)} />;
    if (abilityDetailId != null) return <AbilityDetail catalog={catalog} state={state} abilityId={abilityDetailId} onBack={() => setAbilityDetailId(null)} />;
    if (natureDetailId != null) {
      const nature = catalog.natures?.find(candidate => candidate.id === natureDetailId);
      if (nature) return <NatureDetail nature={nature} onBack={() => setNatureDetailId(null)} />;
    }
    switch (state.screen) {
      case 'DETAIL': return <PokedexDetail catalog={catalog} state={state} send={send} tab={detailTab} setTab={setDetailTab} openMove={setMoveDetailId} openAbility={setAbilityDetailId} />;
      case 'BATTLE': return state.battle ? <BattlePage catalog={catalog} state={state} send={send} openMove={setMoveDetailId} openSpecies={speciesId => {
        setDetailTab('ENTRY');
        void send('OPEN_SPECIES', { speciesId });
      }} /> : <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={() => setMapOpen(true)} />;
      case 'TRAINER': return <TrainerCardPage state={state} onBack={() => void send('BACK')} />;
      case 'PARTY': {
        const occupied = new Set((state.party ?? []).filter(member => member.occupied).map(member => member.slot));
        const selectedSlot = partySelection.catalogHash === catalog.hash && partySelection.slot != null && occupied.has(partySelection.slot)
          ? partySelection.slot
          : state.selectedPartySlot != null && occupied.has(state.selectedPartySlot)
            ? state.selectedPartySlot
            : [...occupied][0] ?? null;
        return <PartyPage
          catalog={catalog}
          state={state}
          selectedSlot={selectedSlot}
          onSelectSlot={slot => setPartySelection({ catalogHash: catalog.hash, slot })}
          onBack={() => void send('BACK')}
          openMove={setMoveDetailId}
          openAbility={setAbilityDetailId}
          openNature={setNatureDetailId}
          openSpecies={speciesId => {
            setDetailTab('ENTRY');
            void send('OPEN_SPECIES', { speciesId });
          }}
        />;
      }
      case 'SETTINGS': return <SettingsPage catalog={catalog} state={state} send={send} onUpload={onUpload} onOpenCapabilities={() => setCapabilityReportOpen(true)} onOpenMapper={() => setMapperOpen(true)} />;
      default: return <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={() => setMapOpen(true)} />;
    }
  }, [catalog, state, busy, error, moveDetailId, abilityDetailId, natureDetailId, detailTab, mapperOpen, capabilityReportOpen, mapOpen, partySelection]);
  return <main class={showDevelopmentTools ? 'lab-shell' : 'production-shell'}>
    {DevelopmentTools && <DevelopmentTools catalog={catalog} state={state} onUpload={onUpload} send={send} />}
    <div class={showDevelopmentTools ? 'device-shell' : 'production-device'} style={applicationThemeStyle(catalog, state.settings)} data-density={state.settings.density.toLowerCase()} data-contrast={state.settings.highContrast ? 'high' : 'normal'} data-theme={(state.settings.theme ?? 'GAME').toLowerCase()}>
      {showDevelopmentTools && <div class="device-sensor" />}
      <div class="device-screen">
        <div class="screen-host">{screen}</div>
        {catalog && state.loading.active && <div class="loading-indicator" role="status" aria-label={loadingLabel}><span>{loadingLabel}</span><i /></div>}{error && catalog && <div class="error-toast" role="alert">{error}</div>}
      </div>
    </div>
  </main>;
}

export function applicationThemeStyle(catalog: Catalog | null, settings: State['settings']): JSX.CSSProperties {
  const style = { '--font-scale': settings.fontScale } as JSX.CSSProperties;
  if (!catalog?.theme || (settings.theme ?? 'GAME') !== 'GAME' || settings.highContrast) return style;
  const tokens = catalog.theme.tokens;
  Object.assign(style, {
    '--theme-field': tokens.field,
    '--theme-field-pattern': tokens.fieldPattern,
    '--theme-header': tokens.header,
    '--theme-header-shadow': tokens.headerShadow,
    '--theme-menu': tokens.menu,
    '--theme-menu-shadow': tokens.menuShadow,
    '--theme-panel': tokens.panel,
    '--theme-border': tokens.border,
    '--theme-text': tokens.text,
    '--theme-text-shadow': tokens.textShadow,
    '--theme-accent': tokens.accent,
    '--theme-accent-text': tokens.accentText,
  });
  return style;
}

export function catalogRefreshMarker(state: Pick<State, 'catalogName' | 'loading'>): string {
  if (state.loading.completedUnits <= 0) return '';
  return `${state.catalogName ?? ''}:${state.loading.phase}:${state.loading.completedUnits}:${state.loading.totalUnits}`;
}

export function loadingModuleLabel(phase: string): string {
  const labels: Record<string, string> = {
    ROM_IDENTITY: 'Checking the game',
    FAMILY_AND_TABLES: 'Finding game data',
    CORE_RECORDS: 'Reading Pokémon and moves',
    SPECIES_MEDIA: 'Preparing artwork and entries',
    EVOLUTIONS_AND_LEARNSETS: 'Reading evolutions and learnsets',
    ENCOUNTERS: 'Finding wild encounters',
    MOVE_DATA: 'Reading move details',
    ABILITY_DATA: 'Reading ability details',
    MAPS: 'Preparing maps',
    TRAINER_AND_THEME: 'Preparing your Trainer Card',
    CATALOG_STORAGE: 'Saving your game guide',
    CACHE_REOPEN: 'Opening your game guide',
  };
  return labels[phase] ?? 'Preparing your companion';
}

function Welcome({ busy, loading, loadingLabel, error, onUpload, openSetup }: { busy: boolean; loading: State['loading']; loadingLabel: string; error: string | null; onUpload: (file: File) => void; openSetup: () => void }) {
  const active = busy || loading.active;
  return <section class="screen welcome-screen"><div class="welcome-mark"><span /><i /></div><h1>DUALDEX</h1>{active
    ? <WelcomeLoadingProgress label={loadingLabel} loading={loading} />
    : <><p>Choose a Pokémon game to begin.</p><div class="welcome-actions"><label class="welcome-upload"><span>LOAD ROM OR ZIP</span><input type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label><button type="button" onClick={openSetup}>CONNECT RETROARCH</button></div></>}{error && <div class="welcome-error">{error}</div>}</section>;
}

function WelcomeLoadingProgress({ label, loading }: { label: string; loading: State['loading'] }) {
  const determinate = loading.active && loading.totalUnits > 0;
  const ratio = determinate ? Math.max(0, Math.min(1, loading.completedUnits / loading.totalUnits)) : 0;
  return <div class="welcome-loading" role="status" aria-label={label}>
    <strong>{label}</strong>
    <div
      class={`welcome-progress ${determinate ? '' : 'is-indeterminate'}`}
      role="progressbar"
      aria-label={label}
      aria-valuemin={determinate ? 0 : undefined}
      aria-valuemax={determinate ? loading.totalUnits : undefined}
      aria-valuenow={determinate ? loading.completedUnits : undefined}
    ><span style={determinate ? { width: `${ratio * 100}%` } : undefined} /></div>
  </div>;
}
