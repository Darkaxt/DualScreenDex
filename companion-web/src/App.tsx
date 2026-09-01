import type { ComponentType, JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { action, bootstrap, events, uploadRom, type ConnectionStatus } from './gateway';
import type { Bootstrap, Catalog, State } from './models';
import { deriveSemanticTheme, semanticThemeCssVariables } from './themeContrast';
import { decodeRouteHash, encodeRouteHash, popRoute, pushRoute, type UiRoute } from './navigation';
import { RouteHeadingFocusContext } from './components';
import { PokedexBrowse } from './pages/PokedexBrowse';
import { PokedexDetail } from './pages/PokedexDetail';
import { BattlePage } from './pages/BattlePage';
import { SettingsPage, type SettingsCategory } from './pages/SettingsPage';
import { MoveDetail } from './pages/MoveDetail';
import { AbilityDetail } from './pages/AbilityDetail';
import { NatureDetail } from './pages/NatureDetail';
import { SetupPage } from './pages/SetupPage';
import { MemoryMapperPage } from './pages/MemoryMapperPage';
import { CapabilityReportPage } from './pages/CapabilityReportPage';
import { MapPage } from './pages/MapPage';
import { TrainerPage } from './pages/TrainerPage';
import { PartyPage } from './pages/PartyPage';
import { PartyAnalysisPage } from './pages/PartyAnalysisPage';
import { SpecimensPage } from './pages/SpecimensPage';

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
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO', theme: 'GAME', displayTarget: 'AUTO', mapFollowSmoothingPercent: 25, highVisibilityMapPlayer: false },
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
  const [errorRetry, setErrorRetry] = useState<(() => void) | null>(null);
  const [dismissedError, setDismissedError] = useState<string | null>(null);
  const [pendingFocusReturn, setPendingFocusReturn] = useState<FocusReturn | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('CONNECTED');
  const [busy, setBusy] = useState(true);
  const [routes, setRoutes] = useState<UiRoute[]>([]);
  const [settingsNavigation, setSettingsNavigation] = useState<{
    ownerKey: string;
    category: SettingsCategory | null;
  }>({ ownerKey: '', category: null });
  const [detailTab, setDetailTab] = useState<'ENTRY' | 'STATS' | 'MOVES' | 'AREA' | 'MORE'>('ENTRY');
  const [partySelection, setPartySelection] = useState<{ catalogHash: string; slot: number | null }>({ catalogHash: '', slot: null });
  const [partyScroll, setPartyScroll] = useState<{ catalogHash: string; top: number }>({ catalogHash: '', top: 0 });
  const [specimenScroll, setSpecimenScroll] = useState<{ key: string; top: number }>({ key: '', top: 0 });
  const lastCatalogRefresh = useRef('');
  const bootstrapRequestRef = useRef(0);
  const battleWasForegroundRef = useRef(false);
  const activeRoute = routes.at(-1);
  const routesRef = useRef(routes);
  const catalogRef = useRef(catalog);
  const routeCatalogInitializedRef = useRef(false);
  const routeHistoryIndexRef = useRef(0);
  const screenRef = useRef(state.screen);
  const stateVersionRef = useRef(state.version);
  const stateRef = useRef(state);
  const settingsNavigationRef = useRef<{
    ownerKey: string | null;
    category: SettingsCategory | null;
  }>({ ownerKey: null, category: null });
  const routeFocusReturnsRef = useRef(new Map<number, FocusReturn>());
  const screenFocusReturnsRef = useRef(new Map<string, FocusReturn>());
  const focusRestoreTimerRef = useRef<number | null>(null);
  const routeTriggerRef = useRef<HTMLElement | null>(null);
  routesRef.current = routes;
  catalogRef.current = catalog;
  screenRef.current = state.screen;
  stateVersionRef.current = state.version;
  stateRef.current = state;
  const settingsOwnerKey = activeRoute?.kind === 'SETTINGS'
    ? `route:${activeRoute.catalogHash}:${activeRoute.category}:${activeRoute.control}`
    : activeRoute == null && catalog && state.screen === 'SETTINGS'
      ? `screen:${catalog.hash}`
      : null;
  const defaultSettingsCategory = activeRoute?.kind === 'SETTINGS'
    ? activeRoute.category
    : null;
  const displayedSettingsCategory = settingsOwnerKey != null && settingsNavigation.ownerKey === settingsOwnerKey
    ? settingsNavigation.category
    : defaultSettingsCategory;
  settingsNavigationRef.current = {
    ownerKey: settingsOwnerKey,
    category: displayedSettingsCategory,
  };

  useEffect(() => {
    if (!pendingFocusReturn) return;
    const focusReturn = pendingFocusReturn;
    setPendingFocusReturn(null);
    if (focusRestoreTimerRef.current != null) window.clearTimeout(focusRestoreTimerRef.current);
    focusRestoreTimerRef.current = window.setTimeout(() => {
      focusRestoreTimerRef.current = window.setTimeout(() => {
        focusRestoreTimerRef.current = null;
        if (viewFocusKey(screenRef.current, routesRef.current) === focusReturn.viewKey) {
          findFocusReturnTarget(focusReturn)?.focus();
        }
      }, 0);
    }, 0);
  }, [pendingFocusReturn, routes, state.screen]);

  useEffect(() => () => {
    if (focusRestoreTimerRef.current != null) window.clearTimeout(focusRestoreTimerRef.current);
  }, []);

  const reportFailure = (failure: unknown, message: string, retry?: () => void) => {
    console.error(failure);
    setError(message);
    setErrorRetry(() => retry ?? null);
  };

  function setDisplayedSettingsCategory(category: SettingsCategory | null) {
    const ownerKey = settingsNavigationRef.current.ownerKey;
    if (ownerKey) setSettingsNavigation({ ownerKey, category });
  }

  function setClientRoutes(next: UiRoute[]) {
    routesRef.current = next;
    setRoutes(next);
  }

  function routeUrl(next: UiRoute[], catalogHash: string): string {
    if (next.length > 0) return encodeRouteHash(next, catalogHash);
    const hash = window.location.hash.startsWith('#dualdex=') ? '' : window.location.hash;
    return `${window.location.pathname}${window.location.search}${hash}`;
  }

  function currentRouteHistoryIndex(): number {
    const value: unknown = window.history.state?.dualdexRouteIndex;
    return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : 0;
  }

  function replaceRoutes(next: UiRoute[], catalogHash = catalogRef.current?.hash, routeIndex = 0) {
    setClientRoutes(next);
    routeHistoryIndexRef.current = routeIndex;
    if (catalogHash) {
      window.history.replaceState({ dualdexRouteIndex: routeIndex }, '', routeUrl(next, catalogHash));
    }
  }

  function pushClientRoute(route: UiRoute) {
    const activeCatalog = catalogRef.current;
    if (!activeCatalog) return;
    const next = pushRoute(routesRef.current, route);
    if (next === routesRef.current) return;
    const routeIndex = routeHistoryIndexRef.current + 1;
    const focusReturn = activeFocusReturn(viewFocusKey(screenRef.current, routesRef.current), routeTriggerRef.current);
    routeTriggerRef.current = null;
    if (focusReturn) routeFocusReturnsRef.current.set(next.length, focusReturn);
    routeHistoryIndexRef.current = routeIndex;
    window.history.pushState(
      { dualdexRouteIndex: routeIndex },
      '',
      routeUrl(next, activeCatalog.hash),
    );
    setClientRoutes(next);
  }

  function closeClientRoute() {
    const depth = routesRef.current.length;
    if (depth === 0) return;
    setPendingFocusReturn(routeFocusReturnsRef.current.get(depth) ?? null);
    routeFocusReturnsRef.current.delete(depth);
    if (routeHistoryIndexRef.current > 0) {
      routeHistoryIndexRef.current -= 1;
      setClientRoutes(popRoute(routesRef.current));
      window.history.back();
      return;
    }
    replaceRoutes(popRoute(routesRef.current));
  }

  useEffect(() => {
    const handlePopState = () => {
      const activeCatalog = catalogRef.current;
      if (!activeCatalog) return;
      const routeIndex = currentRouteHistoryIndex();
      const restored = decodeRouteHash(window.location.hash, activeCatalog, {
        mapperAvailable: stateRef.current.mapperAvailable === true,
        worldMapsAvailable: (activeCatalog.worldMaps?.length ?? 0) > 0,
      });
      if (restored.length < routesRef.current.length) {
        const depth = routesRef.current.length;
        setPendingFocusReturn(routeFocusReturnsRef.current.get(depth) ?? null);
        routeFocusReturnsRef.current.delete(depth);
      }
      routeHistoryIndexRef.current = routeIndex;
      setClientRoutes(restored);
      if (restored.length === 0 && window.location.hash.startsWith('#dualdex=')) {
        window.history.replaceState({ dualdexRouteIndex: routeIndex }, '', routeUrl([], activeCatalog.hash));
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  function applyBootstrap(value: Bootstrap, resetStateVersion = false) {
    const previousCatalogHash = catalogRef.current?.hash ?? null;
    const nextCatalogHash = value.catalog?.hash ?? null;
    const catalogChanged = previousCatalogHash !== nextCatalogHash;
    catalogRef.current = value.catalog;
    setCatalog(value.catalog);
    if (!routeCatalogInitializedRef.current || catalogChanged) {
      routeCatalogInitializedRef.current = true;
      if (value.catalog) {
        const restored = decodeRouteHash(window.location.hash, value.catalog, {
          mapperAvailable: value.state.mapperAvailable === true,
          worldMapsAvailable: (value.catalog.worldMaps?.length ?? 0) > 0,
        });
        replaceRoutes(
          restored,
          value.catalog.hash,
          restored.length > 0 ? currentRouteHistoryIndex() : 0,
        );
      } else {
        setClientRoutes([]);
        routeHistoryIndexRef.current = 0;
        if (window.location.hash.startsWith('#dualdex=')) {
          window.history.replaceState({ dualdexRouteIndex: 0 }, '', routeUrl([], ''));
        }
      }
    }
    setState(current => {
      const next = (resetStateVersion || catalogChanged || value.state.version >= current.version) ? value.state : current;
      stateRef.current = next;
      return next;
    });
    const refreshMarker = catalogRefreshMarker(value.state);
    if (refreshMarker) lastCatalogRefresh.current = refreshMarker;
    setError(null);
    setErrorRetry(null);
  }

  function requestLatestBootstrap(
    request: () => Promise<Bootstrap>,
    resetStateVersion = false,
  ): { id: number; promise: Promise<boolean> } {
    const id = ++bootstrapRequestRef.current;
    return {
      id,
      promise: request().then(value => {
        if (id !== bootstrapRequestRef.current) return false;
        applyBootstrap(value, resetStateVersion);
        return true;
      }),
    };
  }

  function retryBootstrap(message: string, resetStateVersion = false, showBusy = false) {
    if (showBusy) setBusy(true);
    const retry = requestLatestBootstrap(bootstrap, resetStateVersion);
    void retry.promise.then(
      () => { if (showBusy && retry.id === bootstrapRequestRef.current) setBusy(false); },
      failure => {
        if (retry.id !== bootstrapRequestRef.current) return;
        reportFailure(failure, message, () => retryBootstrap(message, resetStateVersion, showBusy));
        if (showBusy) setBusy(false);
      },
    );
  }

  useEffect(() => {
    const initial = requestLatestBootstrap(bootstrap);
    void initial.promise.then(
      committed => { if (committed) setBusy(false); },
      failure => {
        if (initial.id !== bootstrapRequestRef.current) return;
        reportFailure(
          failure,
          'The companion could not start. Please try again.',
          () => retryBootstrap('The companion could not start. Please try again.', false, true),
        );
        setBusy(false);
      },
    );
    return events(() => stateVersionRef.current, incoming => {
      setState(current => {
        const next = incoming.version > current.version ? incoming : current;
        stateRef.current = next;
        return next;
      });
      const marker = catalogRefreshMarker(incoming);
      if (marker && marker !== lastCatalogRefresh.current) {
        lastCatalogRefresh.current = marker;
        const refresh = requestLatestBootstrap(bootstrap);
        void refresh.promise.catch(failure => {
          if (refresh.id === bootstrapRequestRef.current) {
            reportFailure(
              failure,
              'Your game guide could not be refreshed. Please try again.',
              () => retryBootstrap('Your game guide could not be refreshed. Please try again.'),
            );
          }
        });
      }
    }, setConnectionStatus, () => {
      const reconnect = requestLatestBootstrap(bootstrap, true);
      return reconnect.promise.then(
        () => undefined,
        failure => {
          if (reconnect.id !== bootstrapRequestRef.current) return;
          reportFailure(
            failure,
            'The companion could not reconnect. It will keep trying.',
            () => retryBootstrap('The companion could not reconnect. It will keep trying.', true),
          );
          throw failure;
        },
      );
    });
  }, []);

  const send = async (
    type: string,
    values: Record<string, string | number | boolean | null> = {},
    focusOverride?: FocusReturn | null,
  ) => {
    if (type === 'SCREEN' && values.screen === 'SETTINGS' && catalogRef.current) {
      setSettingsNavigation({
        ownerKey: `screen:${catalogRef.current.hash}`,
        category: null,
      });
    }
    const sourceScreen = screenRef.current;
    const sourceFocus = focusOverride ?? activeFocusReturn(
      viewFocusKey(sourceScreen, routesRef.current),
      routeTriggerRef.current,
    );
    routeTriggerRef.current = null;
    try {
      const next = await action(type, values);
      if (next.screen !== sourceScreen) {
        const focusReturn = screenFocusReturnsRef.current.get(sourceScreen);
        if (focusReturn?.viewKey === viewFocusKey(next.screen, routesRef.current)) {
          setPendingFocusReturn(focusReturn);
          screenFocusReturnsRef.current.delete(sourceScreen);
        } else if (sourceFocus) {
          screenFocusReturnsRef.current.set(next.screen, sourceFocus);
        }
      }
      stateRef.current = next;
      screenRef.current = next.screen;
      setState(next);
      setError(null);
      setErrorRetry(null);
    } catch (failure) {
      reportFailure(
        failure,
        'That action could not be completed. Please try again.',
        () => { void send(type, values, sourceFocus); },
      );
    }
  };

  const onUpload = async (file: File) => {
    setBusy(true);
    const request = requestLatestBootstrap(() => uploadRom(file));
    try {
      await request.promise;
    } catch (failure) {
      if (request.id === bootstrapRequestRef.current) {
        reportFailure(
          failure,
          'This game could not be opened. Try another file or retry.',
          () => { void onUpload(file); },
        );
      }
    } finally {
      if (request.id === bootstrapRequestRef.current) setBusy(false);
    }
  };

  const loadingLabel = loadingModuleLabel(state.loading.phase);
  const waitingForGame = shouldWaitForGameAccess(state);
  const rawDisplayedError = error
    ?? state.error
    ?? (state.retroArch?.resolution === 'FAILED' ? state.retroArch.message : null);
  const displayedError = rawDisplayedError === dismissedError ? null : rawDisplayedError;
  const displayedErrorRetry = errorRetry ?? (rawDisplayedError == null
    ? null
    : () => retryBootstrap('The companion could not refresh. Please try again.'));
  useEffect(() => {
    if (rawDisplayedError == null) setDismissedError(null);
  }, [rawDisplayedError]);
  const connectionMessage = connectionStatus === 'RECONNECTING'
    ? 'Reconnecting to the companion…'
    : connectionStatus === 'FAILED'
      ? 'The companion is unavailable. Retrying automatically…'
      : null;

  useEffect(() => {
    const handleCompanionBack = (event: Event) => {
      const backEvent = event as Event & { dualdexHandled?: boolean };
      event.preventDefault();
      queueMicrotask(() => {
        if (backEvent.dualdexHandled) return;
        const currentRoutes = routesRef.current;
        const currentRoute = currentRoutes.at(-1);
        const currentScreen = screenRef.current;
        const currentSettingsNavigation = settingsNavigationRef.current;
        if (currentSettingsNavigation.ownerKey && currentSettingsNavigation.category) {
          setSettingsNavigation({
            ownerKey: currentSettingsNavigation.ownerKey,
            category: null,
          });
        } else if (currentRoutes.length > 0) {
          if (currentRoute?.kind === 'PARTY_MEMBER' && currentScreen !== 'PARTY') void send('BACK');
          else closeClientRoute();
        }
        else if (currentScreen !== 'POKEDEX') void send('BACK');
      });
    };
    window.addEventListener('dualdexback', handleCompanionBack);
    return () => window.removeEventListener('dualdexback', handleCompanionBack);
  }, []);

  useEffect(() => {
    const battleForeground = state.screen === 'BATTLE' && state.battle != null;
    if (battleForeground && !battleWasForegroundRef.current) replaceRoutes([]);
    battleWasForegroundRef.current = battleForeground;
  }, [state.screen, state.battle != null]);

  useEffect(() => {
    if (activeRoute?.kind === 'MAP' && state.screen !== activeRoute.originScreen) {
      replaceRoutes(popRoute(routesRef.current));
    }
  }, [activeRoute, state.screen]);

  function openMap() {
    pushClientRoute({ kind: 'MAP', originScreen: state.screen });
  }

  function openRoute(route: UiRoute) {
    pushClientRoute(route);
  }

  function openMoveListSettings() {
    const activeCatalog = catalogRef.current;
    if (!activeCatalog) return;
    const route = {
      kind: 'SETTINGS',
      category: 'INFORMATION',
      control: 'MOVE_LIST',
      catalogHash: activeCatalog.hash,
    } satisfies UiRoute;
    setSettingsNavigation({
      ownerKey: `route:${route.catalogHash}:${route.category}:${route.control}`,
      category: route.category,
    });
    pushClientRoute(route);
  }

  function closeRoute() {
    closeClientRoute();
  }

  const screen = useMemo(() => {
    if (catalog && waitingForGame && state.screen !== 'SETUP') return <GameAccessWaiting />;
    if (activeRoute?.kind === 'MAPPER' && state.mapperAvailable === true) return <MemoryMapperPage onBack={closeRoute} />;
    if (activeRoute?.kind === 'CAPABILITIES' && catalog) return <CapabilityReportPage romHash={catalog.hash} refreshMarker={catalogRefreshMarker(state)} onBack={closeRoute} />;
    if (state.screen === 'SETUP') return <SetupPage state={state} send={send} />;
    if (!catalog) return <Welcome
      busy={busy}
      loading={state.loading}
      loadingLabel={state.loading.active ? loadingLabel : 'Preparing your companion'}
      error={displayedError}
      showGuideRetry={state.retroArch?.resolution === 'FAILED'}
      onUpload={onUpload}
      openSetup={() => void send('SCREEN', { screen: 'SETUP' })}
    />;
    if (activeRoute?.kind === 'MAP' && (catalog.worldMaps?.length ?? 0) > 0) return <MapPage
      catalog={catalog}
      state={state}
      onOpenPokedex={() => {
        replaceRoutes([]);
        void send('SCREEN', { screen: 'POKEDEX' });
      }}
      onOpenSettings={() => {
        replaceRoutes([]);
        void send('SCREEN', { screen: 'SETTINGS' });
      }}
      onUpdatePoiPreferences={values => void send('MAP_POI_SETTINGS', values)}
    />;
    if (activeRoute?.kind === 'SETTINGS' && activeRoute.catalogHash === catalog.hash) return <SettingsPage
      catalog={catalog}
      state={state}
      send={send}
      onUpload={onUpload}
      initialControl={activeRoute.control}
      category={displayedSettingsCategory}
      onCategoryChange={setDisplayedSettingsCategory}
      onBack={closeRoute}
      onOpenCapabilities={() => openRoute({ kind: 'CAPABILITIES' })}
      mapperAvailable={state.mapperAvailable === true}
      onOpenMapper={() => openRoute({ kind: 'MAPPER' })}
    />;
    if (activeRoute?.kind === 'MOVE') return <MoveDetail catalog={catalog} state={state} moveId={activeRoute.id} onBack={closeRoute} />;
    if (activeRoute?.kind === 'ABILITY') return <AbilityDetail catalog={catalog} state={state} abilityId={activeRoute.id} onBack={closeRoute} />;
    if (activeRoute?.kind === 'NATURE') {
      const nature = catalog.natures?.find(candidate => candidate.id === activeRoute.id);
      if (nature) return <NatureDetail nature={nature} gameTime={state.gameTime} onBack={closeRoute} />;
    }
    if (activeRoute?.kind === 'SPECIMENS' || activeRoute?.kind === 'SPECIMEN') {
      const speciesId = activeRoute.speciesId;
      const catalogHash = activeRoute.catalogHash;
      if (catalogHash === catalog.hash) {
        const scrollKey = `${catalogHash}:${speciesId}`;
        return <SpecimensPage
          catalog={catalog}
          speciesId={speciesId}
          stateVersion={state.version}
          gameTime={state.gameTime}
          detailKey={activeRoute.kind === 'SPECIMEN' ? activeRoute.specimenKey : null}
          initialScrollTop={specimenScroll.key === scrollKey ? specimenScroll.top : 0}
          onScrollTopChange={top => setSpecimenScroll({ key: scrollKey, top })}
          onBack={closeRoute}
          onOpenDetail={specimenKey => openRoute({ kind: 'SPECIMEN', speciesId, specimenKey, catalogHash })}
          onCloseDetail={closeRoute}
          openMove={id => openRoute({ kind: 'MOVE', id })}
          openAbility={id => openRoute({ kind: 'ABILITY', id })}
          openNature={id => openRoute({ kind: 'NATURE', id })}
          openSpecies={id => {
            setDetailTab('ENTRY');
            openRoute({ kind: 'SPECIES', id });
          }}
        />;
      }
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
      openSpecimens={speciesId => openRoute({ kind: 'SPECIMENS', speciesId, catalogHash: catalog.hash })}
      openMoveListSettings={openMoveListSettings}
      openAtlas={(catalog.worldMaps?.length ?? 0) > 0 ? openMap : undefined}
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
      case 'DETAIL': return <PokedexDetail catalog={catalog} state={state} send={send} tab={detailTab} setTab={setDetailTab} openMove={id => openRoute({ kind: 'MOVE', id })} openAbility={id => openRoute({ kind: 'ABILITY', id })} openSpecimens={speciesId => openRoute({ kind: 'SPECIMENS', speciesId, catalogHash: catalog.hash })} openMoveListSettings={openMoveListSettings} openAtlas={(catalog.worldMaps?.length ?? 0) > 0 ? openMap : undefined} />;
      case 'BATTLE': return state.battle ? <BattlePage catalog={catalog} state={state} send={send} openMove={id => openRoute({ kind: 'MOVE', id })} openSpecies={speciesId => {
        setDetailTab('ENTRY');
        void send('OPEN_SPECIES', { speciesId });
      }} /> : <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={openMap} />;
      case 'TRAINER': return <TrainerPage state={state} send={(type, values) => void send(type, values)} onBack={() => void send('BACK')} />;
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
      case 'SETTINGS': return <SettingsPage catalog={catalog} state={state} send={send} onUpload={onUpload} category={displayedSettingsCategory} onCategoryChange={setDisplayedSettingsCategory} onOpenCapabilities={() => openRoute({ kind: 'CAPABILITIES' })} mapperAvailable={state.mapperAvailable === true} onOpenMapper={() => openRoute({ kind: 'MAPPER' })} />;
      default: return <PokedexBrowse catalog={catalog} state={state} send={send} onOpenMap={openMap} />;
    }
  }, [catalog, state, busy, error, detailTab, routes, partySelection, partyScroll, specimenScroll, settingsNavigation, waitingForGame]);
  return <main class={showDevelopmentTools ? 'lab-shell' : 'production-shell'}>
    {DevelopmentTools && <DevelopmentTools catalog={catalog} state={state} onUpload={onUpload} send={send} />}
    <div class={showDevelopmentTools ? 'device-shell' : 'production-device'} style={applicationThemeStyle(catalog, state.settings)} data-density={state.settings.density.toLowerCase()} data-contrast={state.settings.highContrast ? 'high' : 'normal'} data-theme={(state.settings.theme ?? 'GAME').toLowerCase()}>
      {showDevelopmentTools && <div class="device-sensor" />}
      <div class="device-screen">
        <div class="screen-host" onClickCapture={event => {
          const target = event.target;
          if (!(target instanceof Element)) return;
          const control = target.closest<HTMLElement>('button, a, input, select, textarea, [tabindex]');
          routeTriggerRef.current = control;
          queueMicrotask(() => {
            if (routeTriggerRef.current === control) routeTriggerRef.current = null;
          });
        }}><RouteHeadingFocusContext.Provider value={pendingFocusReturn == null}>{screen}</RouteHeadingFocusContext.Provider></div>
        <div class="global-feedback" aria-label="Application status">
          {connectionMessage && <div
            class={`connection-toast is-${connectionStatus.toLowerCase()}`}
            role={connectionStatus === 'FAILED' ? 'alert' : 'status'}
          >{connectionMessage}</div>}
          {catalog && state.loading.active && <div class={`loading-indicator ${loadingOriginClass(state.loading)}`} role="status" aria-label={loadingLabel}><span>{loadingLabel}</span><i /></div>}
          {displayedError && catalog && <div class="error-toast" role="alert"><span>{displayedError}</span><div class="error-toast-actions"><button type="button" onClick={() => displayedErrorRetry?.()}>RETRY</button><button type="button" onClick={() => setDismissedError(displayedError)}>DISMISS</button></div></div>}
        </div>
      </div>
    </div>
  </main>;
}

interface FocusReturn {
  selector: string;
  tagName: string;
  ariaLabel: string | null;
  text: string;
  viewKey: string;
}

function viewFocusKey(screen: string, routes: UiRoute[]): string {
  return `${screen}:${JSON.stringify(routes)}`;
}

function activeFocusReturn(viewKey: string, preferred?: HTMLElement | null): FocusReturn | null {
  const root = document.querySelector('.screen-host');
  const active = preferred && root?.contains(preferred) ? preferred : document.activeElement;
  if (!(root instanceof HTMLElement) || !(active instanceof HTMLElement) || !root.contains(active)) return null;
  const parts: string[] = [];
  let element: HTMLElement | null = active;
  while (element && element !== root) {
    const parent: HTMLElement | null = element.parentElement;
    if (!parent) return null;
    const index = Array.from(parent.children).indexOf(element) + 1;
    parts.unshift(`${element.tagName.toLowerCase()}:nth-child(${index})`);
    element = parent;
  }
  return {
    selector: `.screen-host > ${parts.join(' > ')}`,
    tagName: active.tagName.toLowerCase(),
    ariaLabel: active.getAttribute('aria-label'),
    text: active.textContent?.trim() ?? '',
    viewKey,
  };
}

function findFocusReturnTarget(focusReturn: FocusReturn): HTMLElement | null {
  const structuralTarget = document.querySelector<HTMLElement>(focusReturn.selector);
  if (structuralTarget && matchesFocusReturn(structuralTarget, focusReturn)) return structuralTarget;
  const root = document.querySelector('.screen-host');
  if (!(root instanceof HTMLElement)) return null;
  const candidates = Array.from(root.querySelectorAll<HTMLElement>(focusReturn.tagName))
    .filter(candidate => matchesFocusReturn(candidate, focusReturn));
  return candidates.length === 1 ? candidates[0] : null;
}

function matchesFocusReturn(candidate: HTMLElement, focusReturn: FocusReturn): boolean {
  if (candidate.tagName.toLowerCase() !== focusReturn.tagName) return false;
  if (focusReturn.ariaLabel != null) return candidate.getAttribute('aria-label') === focusReturn.ariaLabel;
  return candidate.textContent?.trim() === focusReturn.text;
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
  }, semanticThemeCssVariables(deriveSemanticTheme(tokens)));
  return style;
}

export function catalogRefreshMarker(state: Pick<State, 'catalogName' | 'catalogHash' | 'loading'>): string {
  if (state.loading.completedUnits <= 0 || !state.catalogHash) return '';
  return `${state.catalogHash}:${state.catalogName ?? ''}:${state.loading.phase}:${state.loading.completedUnits}:${state.loading.totalUnits}`;
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

function Welcome({ busy, loading, loadingLabel, error, showGuideRetry, onUpload, openSetup }: { busy: boolean; loading: State['loading']; loadingLabel: string; error: string | null; showGuideRetry: boolean; onUpload: (file: File) => void; openSetup: () => void }) {
  const active = busy || loading.active;
  return <section class="screen welcome-screen"><div class="welcome-mark"><span /><i /></div><h1>DUALDEX</h1>{active
    ? <WelcomeLoadingProgress label={loadingLabel} loading={loading} />
    : <><p>Choose a Pokémon game to begin.</p><div class="welcome-actions"><label class="welcome-upload"><span>LOAD ROM OR ZIP</span><input type="file" accept=".gb,.gbc,.gba,.zip" onChange={event => { const file = event.currentTarget.files?.[0]; if (file) onUpload(file); }} /></label><button type="button" onClick={openSetup}>CONNECT RETROARCH</button></div></>}{error && <div class="welcome-error" role="alert">{error}</div>}{showGuideRetry && <a class="setup-action setup-action-primary" href="dualdex://guide/retry">RETRY OPENING GAME GUIDE</a>}</section>;
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
