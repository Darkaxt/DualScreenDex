import type { ComponentType, JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { action, bootstrap, events, uploadRom } from './gateway';
import type { Bootstrap, Catalog, State } from './models';
import { popRoute, pushRoute, type UiRoute } from './navigation';
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
import { PartyAnalysisPage } from './pages/PartyAnalysisPage';

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
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'GAME', displayTarget: 'AUTO', mapFollowSmoothingPercent: 25 },
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
  const [routes, setRoutes] = useState<UiRoute[]>([]);
  const [detailTab, setDetailTab] = useState<'ENTRY' | 'STATS' | 'MOVES' | 'AREA' | 'MORE'>('ENTRY');
  const [partySelection, setPartySelection] = useState<{ catalogHash: string; slot: number | null }>({ catalogHash: '', slot: null });
  const [partyScroll, setPartyScroll] = useState<{ catalogHash: string; top: number }>({ catalogHash: '', top: 0 });
  const lastCatalogRefresh = useRef('');
  const battleWasForegroundRef = useRef(false);
  const activeRoute = routes.at(-1);
  const routesRef = useRef(routes);
  const screenRef = useRef(state.screen);
  const stateVersionRef = useRef(state.version);
  routesRef.current = routes;
  screenRef.current = state.screen;
  stateVersionRef.current = state.version;

  const reportFailure = (failure: unknown, message: string) => {
    console.error(failure);
    setError(message);
  };

  useEffect(() => {
    bootstrap().then(applyBootstrap).catch(failure => reportFailure(failure, 'The companion could not start. Please try again.')).finally(() => setBusy(false));
    return events(() => stateVersionRef.current, incoming => {
      setState(current => incoming.version > current.version ? incoming : current);
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
  const waitingForGame = shouldWaitForGameAccess(state);

  useEffect(() => {
    const handleCompanionBack = (event: Event) => {
      const backEvent = event as Event & { dualdexHandled?: boolean };
      event.preventDefault();
      queueMicrotask(() => {
        if (backEvent.dualdexHandled) return;
        const currentRoutes = routesRef.current;
        const currentRoute = currentRoutes.at(-1);
        const currentScreen = screenRef.current;
        if (currentRoutes.length > 0) {
          if (currentRoute?.kind === 'PARTY_MEMBER' && currentScreen !== 'PARTY') void send('BACK');
          else setRoutes(current => popRoute(current));
        }
        else if (currentScreen !== 'POKEDEX') void send('BACK');
      });
    };
    window.addEventListener('dualdexback', handleCompanionBack);
    return () => window.removeEventListener('dualdexback', handleCompanionBack);
  }, []);

  useEffect(() => {
    const battleForeground = state.screen === 'BATTLE' && state.battle != null;
    if (battleForeground && !battleWasForegroundRef.current) setRoutes([]);
    battleWasForegroundRef.current = battleForeground;
  }, [state.screen, state.battle != null]);

  useEffect(() => {
    if (activeRoute?.kind === 'MAP' && state.screen !== activeRoute.originScreen) {
      setRoutes(current => popRoute(current));
    }
  }, [activeRoute, state.screen]);

  function openMap() {
    setRoutes(current => pushRoute(current, { kind: 'MAP', originScreen: state.screen }));
  }

  function openRoute(route: UiRoute) {
    setRoutes(current => pushRoute(current, route));
  }

  function closeRoute() {
    setRoutes(current => popRoute(current));
  }

  const screen = useMemo(() => {
    if (catalog && waitingForGame && state.screen !== 'SETUP') return <GameAccessWaiting />;
    if (activeRoute?.kind === 'MAPPER') return <MemoryMapperPage onBack={closeRoute} />;
    if (activeRoute?.kind === 'CAPABILITIES' && catalog) return <CapabilityReportPage romHash={catalog.hash} refreshMarker={catalogRefreshMarker(state)} onBack={closeRoute} />;
    if (state.screen === 'SETUP') return <SetupPage state={state} send={send} />;
    if (!catalog) return <Welcome
      busy={busy}
      loading={state.loading}
      loadingLabel={state.loading.active ? loadingLabel : 'Preparing your companion'}
      error={error}
      onUpload={onUpload}
      openSetup={() => void send('SCREEN', { screen: 'SETUP' })}
    />;
    if (activeRoute?.kind === 'MAP' && (catalog.worldMaps?.length ?? 0) > 0) return <MapPage
      catalog={catalog}
      state={state}
      onOpenPokedex={() => {
        setRoutes([]);
        void send('SCREEN', { screen: 'POKEDEX' });
      }}
      onOpenSettings={() => {
        setRoutes([]);
        void send('SCREEN', { screen: 'SETTINGS' });
      }}
      onUpdatePoiPreferences={values => void send('MAP_POI_SETTINGS', values)}
    />;
    if (activeRoute?.kind === 'MOVE') return <MoveDetail catalog={catalog} state={state} moveId={activeRoute.id} onBack={closeRoute} />;
    if (activeRoute?.kind === 'ABILITY') return <AbilityDetail catalog={catalog} state={state} abilityId={activeRoute.id} onBack={closeRoute} />;
    if (activeRoute?.kind === 'NATURE') {
      const nature = catalog.natures?.find(candidate => candidate.id === activeRoute.id);
      if (nature) return <NatureDetail nature={nature} onBack={closeRoute} />;
    }
    if (activeRoute?.kind === 'SPECIES') return <PokedexDetail
      catalog={catalog}
      state={{ ...state, screen: 'DETAIL', selectedSpeciesId: activeRoute.id }}
      send={(type, values) => {
        if (type === 'BACK') closeRoute();
        else if (type === 'OPEN_SPECIES' && typeof values?.speciesId === 'number') openRoute({ kind: 'SPECIES', id: values.speciesId });
        else void send(type, values);
      }}
      tab={detailTab}
      setTab={setDetailTab}
      openMove={id => openRoute({ kind: 'MOVE', id })}
      openAbility={id => openRoute({ kind: 'ABILITY', id })}
    />;
    if (activeRoute?.kind === 'PARTY_ANALYSIS' && activeRoute.catalogHash === catalog.hash && state.partyAnalysis) return <PartyAnalysisPage
      catalog={catalog}
      state={state}
      analysis={state.partyAnalysis}
      onBack={closeRoute}
      openMember={slot => openRoute({ kind: 'PARTY_MEMBER', slot, catalogHash: catalog.hash })}
      openMove={id => openRoute({ kind: 'MOVE', id })}
      openAbility={id => openRoute({ kind: 'ABILITY', id })}
      openSpecies={id => {
        setDetailTab('ENTRY');
        openRoute({ kind: 'SPECIES', id });
      }}
    />;
    switch (state.screen) {
      case 'DETAIL': return <PokedexDetail catalog={catalog} state={state} send={send} tab={detailTab} setTab={setDetailTab} openMove={id => openRoute({ kind: 'MOVE', id })} openAbility={id => openRoute({ kind: 'ABILITY', id })} />;
      case 'BATTLE': return state.battle ? <BattlePage catalog={catalog} state={state} send={send} openMove={id => openRoute({ kind: 'MOVE', id })} openSpecies={speciesId => {
        setDetailTab('ENTRY');
        void send('OPEN_SPECIES', { speciesId });
      }} /> : <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={openMap} />;
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
          initialScrollTop={partyScroll.catalogHash === catalog.hash ? partyScroll.top : 0}
          onScrollTopChange={top => setPartyScroll({ catalogHash: catalog.hash, top })}
          detailSlot={activeRoute?.kind === 'PARTY_MEMBER' && activeRoute.catalogHash === catalog.hash ? activeRoute.slot : null}
          onOpenDetails={slot => openRoute({ kind: 'PARTY_MEMBER', slot, catalogHash: catalog.hash })}
          onCloseDetails={closeRoute}
          onOpenAnalysis={state.partyAnalysis ? () => openRoute({ kind: 'PARTY_ANALYSIS', catalogHash: catalog.hash }) : undefined}
          onBack={() => activeRoute?.kind === 'PARTY_MEMBER' ? closeRoute() : void send('BACK')}
          openMove={id => openRoute({ kind: 'MOVE', id })}
          openAbility={id => openRoute({ kind: 'ABILITY', id })}
          openNature={id => openRoute({ kind: 'NATURE', id })}
          openSpecies={speciesId => {
            setDetailTab('ENTRY');
            openRoute({ kind: 'SPECIES', id: speciesId });
          }}
        />;
      }
      case 'SETTINGS': return <SettingsPage catalog={catalog} state={state} send={send} onUpload={onUpload} onOpenCapabilities={() => openRoute({ kind: 'CAPABILITIES' })} onOpenMapper={() => openRoute({ kind: 'MAPPER' })} />;
      default: return <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={openMap} />;
    }
  }, [catalog, state, busy, error, detailTab, routes, partySelection, partyScroll, waitingForGame]);
  return <main class={showDevelopmentTools ? 'lab-shell' : 'production-shell'}>
    {DevelopmentTools && <DevelopmentTools catalog={catalog} state={state} onUpload={onUpload} send={send} />}
    <div class={showDevelopmentTools ? 'device-shell' : 'production-device'} style={applicationThemeStyle(catalog, state.settings)} data-density={state.settings.density.toLowerCase()} data-contrast={state.settings.highContrast ? 'high' : 'normal'} data-theme={(state.settings.theme ?? 'GAME').toLowerCase()}>
      {showDevelopmentTools && <div class="device-sensor" />}
      <div class="device-screen">
        <div class="screen-host">{screen}</div>
        {catalog && state.loading.active && <div class={`loading-indicator ${loadingOriginClass(state.loading)}`} role="status" aria-label={loadingLabel}><span>{loadingLabel}</span><i /></div>}{error && catalog && <div class="error-toast" role="alert">{error}</div>}
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

export function loadingOriginClass(loading: State['loading']): string {
  if (!loading.active) return '';
  return loading.phase === 'CACHE_REOPEN' ? 'loading-origin-cache' : 'loading-origin-parse';
}

export function shouldWaitForGameAccess(state: State): boolean {
  const connection = state.retroArch?.connection;
  const resolution = state.retroArch?.resolution;
  const liveSession = connection === 'PLAYING' || connection === 'PAUSED';
  const catalogSession = resolution === 'LOADING' || resolution === 'ACTIVE';
  return state.catalogReady && liveSession && catalogSession && state.gameAccessReady === false;
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
  return <div class={`welcome-loading ${loadingOriginClass(loading)}`} role="status" aria-label={label}>
    <strong>{label}</strong>
    <div
      class={`welcome-progress ${determinate ? '' : 'is-indeterminate'}`}
      role="progressbar"
      aria-label={label}
      aria-valuemin={determinate ? 0 : undefined}
      aria-valuemax={determinate ? loading.totalUnits : undefined}
      aria-valuenow={determinate ? loading.completedUnits : undefined}
    ><span style={determinate ? { width: `${ratio * 100}%` } : undefined} /></div>
    {loading.message && <p class="welcome-loading-note">{loading.message}</p>}
  </div>;
}

function GameAccessWaiting() {
  return <section class="screen welcome-screen game-access-waiting">
    <div class="welcome-mark"><span /><i /></div>
    <h1>DUALDEX</h1>
    <div class="welcome-game-waiting" role="status" aria-label="Waiting for in-game access">
      <span class="welcome-waiting-spinner" aria-hidden="true" />
      <strong>Waiting for in-game access</strong>
      <p>Waiting for the game to finish initializing.</p>
    </div>
  </section>;
}
