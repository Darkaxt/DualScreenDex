import type { JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { DexIcon, FilterIcon, MapIcon, SettingsIcon } from '../components';
import { GameClockIndicator } from '../GameClockIndicator';
import { AcceleratedMapFollower, anchoredZoom, centerMapPoint, containFit, focusMapRect, GestureTracker, maximumScaleForMarker, MAX_MAP_SCALE, shouldGlideCamera, type MapViewport } from '../mapEngine';
import type { Catalog, LocalMapPoiPreferences, LocalMapPoiView, State, WorldMapLocation, WorldMapRegion } from '../models';

interface MapPageProps {
  catalog: Catalog;
  state: State;
  onOpenPokedex: () => void;
  onOpenSettings: () => void;
  onUpdatePoiPreferences?: (values: Partial<LocalMapPoiPreferences>) => void;
}

type MapMode = 'LOCAL' | 'ATLAS';

interface LocalPoiMarker {
  poi: LocalMapPoiView;
  x: number;
  y: number;
}

type PoiLabelPlacement = 'below' | 'above';

const HOME_VIEWPORT: MapViewport = { scale: 1, panX: 0, panY: 0 };
const DEFAULT_TRAINER_MARKER_WIDTH_PIXELS = 16;
const DEFAULT_TRAINER_MARKER_HEIGHT_PIXELS = 32;
const DEFAULT_POI_PREFERENCES: LocalMapPoiPreferences = {
  showPlaces: true,
  showServices: true,
  showAvailableItems: true,
  showCollectedItems: true,
  showUnknownPois: true,
  iconZoomThresholdPercent: 0,
  labelZoomThresholdPercent: 0,
};

export function MapPage({ catalog, state, onOpenPokedex, onOpenSettings, onUpdatePoiPreferences }: MapPageProps) {
  const maps = catalog.worldMaps ?? [];
  const localMap = (catalog.localMaps ?? []).find(map => map.baseAreaId === state.currentAreaBaseId);
  const localScene = (catalog.mapScenes ?? []).find(scene =>
    scene.placements.some(placement => placement.baseAreaId === state.currentAreaBaseId));
  const activePlacement = localScene?.placements.find(placement => placement.baseAreaId === state.currentAreaBaseId);
  const selectedArea = catalog.areas.find(area => area.id === state.selectedAreaId);
  const selectedAreaBaseId = state.filter === 'AREA'
    ? selectedArea?.baseAreaId ?? (selectedArea ? Math.floor(selectedArea.id / 10) : undefined)
    : undefined;
  const focusedAreaBaseId = selectedAreaBaseId ?? state.currentAreaBaseId ?? undefined;
  const focusedRegion = maps.find(item => item.locations.some(location => location.baseAreaIds.includes(focusedAreaBaseId ?? -1)));
  const [regionKey, setRegionKey] = useState(() => focusedRegion?.key ?? maps[0]?.key ?? '');
  const region = maps.find(item => item.key === regionKey) ?? focusedRegion ?? maps[0];
  const currentLocation = region?.locations.find(location => location.baseAreaIds.includes(state.currentAreaBaseId ?? -1));
  const focusedLocation = region?.locations.find(location => location.baseAreaIds.includes(focusedAreaBaseId ?? -1));
  const [selectedKey, setSelectedKey] = useState(() => focusedLocation?.key ?? '');
  const activeMode: MapMode = localScene || localMap ? 'LOCAL' : 'ATLAS';
  const activeMap = localScene ?? localMap ?? region;
  const playerMapSpriteUrl = state.trainerMapSpriteUrl;
  const playerMapSpriteWidth = state.trainerMapSpriteWidth ?? DEFAULT_TRAINER_MARKER_WIDTH_PIXELS;
  const playerMapSpriteHeight = state.trainerMapSpriteHeight ?? DEFAULT_TRAINER_MARKER_HEIGHT_PIXELS;
  const [viewport, setViewportState] = useState<MapViewport>(HOME_VIEWPORT);
  const [fit, setFit] = useState({ width: activeMap?.pixelWidth ?? 1, height: activeMap?.pixelHeight ?? 1, scale: 1 });
  const fogVisible = activeMode === 'ATLAS' && state.settings.knowledgeMode !== 'DISCOVERED';
  const [legendOpen, setLegendOpen] = useState(false);
  const [poiFiltersOpen, setPoiFiltersOpen] = useState(false);
  const [selectedPoiKey, setSelectedPoiKey] = useState<string | null>(null);
  const [followingPlayer, setFollowingPlayer] = useState(false);
  const stageRef = useRef<HTMLElement>(null);
  const fogRef = useRef<HTMLCanvasElement>(null);
  const gestureRef = useRef(new GestureTracker(HOME_VIEWPORT));
  const maximumScaleRef = useRef(MAX_MAP_SCALE);
  const minimumScaleRef = useRef(1);
  const initializedSceneKeyRef = useRef<string | null>(null);
  const allowMarkerSelectionRef = useRef(true);
  const pressedMarkerRef = useRef(new Map<number, string>());
  const lastFollowedPositionRef = useRef<{ mapKey: string; x: number; y: number } | null>(null);
  const cameraFollowerRef = useRef(new AcceleratedMapFollower(
    { x: HOME_VIEWPORT.panX, y: HOME_VIEWPORT.panY },
    state.settings.mapFollowSmoothingPercent ?? 25,
  ));
  const animationFrameRef = useRef<number | null>(null);
  const animationTimestampRef = useRef<number | null>(null);
  const revealedBaseIds = useMemo(() => new Set([
    ...(state.revealedAreaBaseIds ?? []),
    ...(currentLocation?.baseAreaIds ?? []),
    ...(state.currentAreaBaseId == null ? [] : [state.currentAreaBaseId]),
  ]), [state.revealedAreaBaseIds, currentLocation?.key, state.currentAreaBaseId]);
  const revealedLocations = useMemo(
    () => region?.locations.filter(location => location.baseAreaIds.some(baseAreaId => revealedBaseIds.has(baseAreaId))) ?? [],
    [region?.key, revealedBaseIds],
  );
  const visibleScenePlacements = useMemo(
    () => localScene?.placements.filter(placement =>
      state.settings.knowledgeMode === 'DISCOVERED' || revealedBaseIds.has(placement.baseAreaId)) ?? [],
    [localScene?.key, localScene?.placements, revealedBaseIds, state.settings.knowledgeMode],
  );
  const hiddenScenePlacements = useMemo(
    () => localScene?.placements.filter(placement => !visibleScenePlacements.includes(placement)) ?? [],
    [localScene?.key, localScene?.placements, visibleScenePlacements],
  );
  const localMapNames = useMemo(
    () => new Map((catalog.localMaps ?? []).map(map => [map.key, map.displayName])),
    [catalog.localMaps],
  );
  const poiPreferences = state.localMapPoiPreferences ?? DEFAULT_POI_PREFERENCES;
  const selectedCandidate = region?.locations.find(location => location.key === selectedKey);
  const selectedLocation = fogVisible
    ? revealedLocations.find(location => location.key === selectedCandidate?.key) ?? currentLocation
    : selectedCandidate ?? currentLocation;
  const playerPosition = activeMode === 'LOCAL' && state.currentMapPosition &&
    state.currentMapPosition.x >= 0 && state.currentMapPosition.x < (activePlacement?.gridWidth ?? localMap?.gridWidth ?? 0) &&
    state.currentMapPosition.y >= 0 && state.currentMapPosition.y < (activePlacement?.gridHeight ?? localMap?.gridHeight ?? 0)
    ? {
      mapX: state.currentMapPosition.x,
      mapY: state.currentMapPosition.y,
      sceneX: (activePlacement?.gridX ?? 0) + state.currentMapPosition.x,
      sceneY: (activePlacement?.gridY ?? 0) + state.currentMapPosition.y,
    }
    : undefined;

  useEffect(() => {
    if (focusedRegion && focusedRegion.key !== regionKey) setRegionKey(focusedRegion.key);
  }, [focusedRegion?.key]);

  useEffect(() => {
    if (!region) return;
    const nextFocused = region.locations.find(location => location.baseAreaIds.includes(focusedAreaBaseId ?? -1));
    setSelectedKey(nextFocused?.key ?? '');
    if (activeMode === 'ATLAS') setViewport(HOME_VIEWPORT);
  }, [region?.key, focusedAreaBaseId]);

  useEffect(() => {
    if (!activeMap || !stageRef.current) return;
    const stage = stageRef.current;
    const measure = () => {
      const bounds = stage.getBoundingClientRect();
      const availableWidth = bounds.width || activeMap.pixelWidth;
      const availableHeight = bounds.height || activeMap.pixelHeight;
      const focused = localScene && activePlacement
        ? focusMapRect(
          localScene.pixelWidth,
          localScene.pixelHeight,
          {
            x: activePlacement.pixelX,
            y: activePlacement.pixelY,
            width: activePlacement.pixelWidth,
            height: activePlacement.pixelHeight,
          },
          availableWidth,
          availableHeight,
        )
        : null;
      const nextFit = focused?.fit ?? containFit(activeMap.pixelWidth, activeMap.pixelHeight, availableWidth, availableHeight);
      setFit(nextFit);
      gestureRef.current.setCenter(availableWidth / 2, availableHeight / 2);
      const genericMaximum = focused?.maximumScale ?? MAX_MAP_SCALE;
      const avatarMaximum = playerMapSpriteUrl
        ? maximumScaleForMarker(
          nextFit.scale,
          activeMap.pixelWidth / activeMap.gridWidth,
          playerMapSpriteWidth,
        )
        : genericMaximum;
      maximumScaleRef.current = Math.max(
        focused?.viewport.scale ?? 1,
        Math.min(genericMaximum, avatarMaximum),
      );
      minimumScaleRef.current = focused?.viewport.scale ?? 1;
      const clamped = gestureRef.current.setMaximumScale(maximumScaleRef.current);
      if (focused && initializedSceneKeyRef.current !== localScene?.key) {
        initializedSceneKeyRef.current = localScene!.key;
        setViewport(focused.viewport);
      } else {
        if (!focused) initializedSceneKeyRef.current = null;
        setViewportState(clamped);
      }
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(stage);
    return () => observer.disconnect();
  }, [
    activeMap?.key,
    activeMap?.pixelWidth,
    activeMap?.pixelHeight,
    activePlacement?.localMapKey,
    activePlacement?.pixelX,
    activePlacement?.pixelY,
    activePlacement?.pixelWidth,
    activePlacement?.pixelHeight,
    activeMap?.gridWidth,
    playerMapSpriteUrl,
    playerMapSpriteWidth,
  ]);

  useEffect(() => {
    if (!region || !fogVisible || !fogRef.current) return;
    paintFog(fogRef.current, region, revealedLocations);
  }, [region?.key, revealedLocations, fogVisible]);

  useEffect(() => {
    cameraFollowerRef.current.setSmoothingPercent(state.settings.mapFollowSmoothingPercent ?? 25);
  }, [state.settings.mapFollowSmoothingPercent]);

  useEffect(() => () => cancelCameraAnimation(), []);

  useEffect(() => {
    if (!followingPlayer || activeMode !== 'LOCAL' || !activeMap || !playerPosition) return;
    const previous = lastFollowedPositionRef.current;
    const canGlide = previous?.mapKey === activeMap.key && shouldGlideCamera(
        { x: previous.x, y: previous.y },
        { x: playerPosition.sceneX, y: playerPosition.sceneY },
        activeMap.gridWidth,
        activeMap.gridHeight,
      );
    lastFollowedPositionRef.current = {
      mapKey: activeMap.key,
      x: playerPosition.sceneX,
      y: playerPosition.sceneY,
    };
    const target = centerMapPoint(
      gestureRef.current.viewport,
      { x: 0, y: 0, width: activeMap.gridWidth, height: activeMap.gridHeight },
      fit,
      { x: playerPosition.sceneX + 0.5, y: playerPosition.sceneY + 0.5 },
    );
    if (!canGlide || prefersReducedMotion() || (state.settings.mapFollowSmoothingPercent ?? 25) === 0) {
      cancelCameraAnimation();
      cameraFollowerRef.current.reset({ x: target.panX, y: target.panY });
      setViewport(target);
      return;
    }
    cameraFollowerRef.current.setTarget({ x: target.panX, y: target.panY });
    startCameraAnimation();
  }, [
    followingPlayer,
    activeMode,
    activeMap?.key,
    playerPosition?.sceneX,
    playerPosition?.sceneY,
    fit.width,
    fit.height,
    state.settings.mapFollowSmoothingPercent,
  ]);

  const markerLocations = useMemo(() => {
    if (activeMode !== 'ATLAS' || !region) return [];
    return fogVisible ? revealedLocations : region.locations;
  }, [activeMode, region?.key, revealedLocations, fogVisible]);

  if (!activeMap) return null;

  function setViewport(next: MapViewport) {
    gestureRef.current.setViewport(next);
    setViewportState(next);
  }

  function cancelCameraAnimation() {
    if (animationFrameRef.current != null) window.cancelAnimationFrame(animationFrameRef.current);
    animationFrameRef.current = null;
    animationTimestampRef.current = null;
  }

  function startCameraAnimation() {
    if (animationFrameRef.current != null) return;
    animationFrameRef.current = window.requestAnimationFrame(animateCamera);
  }

  function animateCamera(timestamp: number) {
    animationFrameRef.current = null;
    const previousTimestamp = animationTimestampRef.current ?? timestamp;
    animationTimestampRef.current = timestamp;
    const position = cameraFollowerRef.current.step(timestamp - previousTimestamp);
    const current = gestureRef.current.viewport;
    setViewport({ ...current, panX: position.x, panY: position.y });
    if (cameraFollowerRef.current.settled) {
      animationTimestampRef.current = null;
    } else {
      animationFrameRef.current = window.requestAnimationFrame(animateCamera);
    }
  }

  function stopFollowingPlayer() {
    cancelCameraAnimation();
    setFollowingPlayer(false);
    lastFollowedPositionRef.current = null;
  }

  function zoom(multiplier: number, anchor?: { x: number; y: number }) {
    const bounds = stageRef.current?.getBoundingClientRect();
    const center = { x: (bounds?.width ?? activeMap!.pixelWidth) / 2, y: (bounds?.height ?? activeMap!.pixelHeight) / 2 };
    setViewport(anchoredZoom(viewport, viewport.scale * multiplier, anchor ?? center, center, maximumScaleRef.current));
  }

  function recenter() {
    if (activeMode === 'LOCAL' && playerPosition) {
      setFollowingPlayer(true);
      cancelCameraAnimation();
      lastFollowedPositionRef.current = {
        mapKey: activeMap!.key,
        x: playerPosition.sceneX,
        y: playerPosition.sceneY,
      };
      const centered = centerMapPoint(
        viewport,
        { x: 0, y: 0, width: activeMap!.gridWidth, height: activeMap!.gridHeight },
        fit,
        { x: playerPosition.sceneX + 0.5, y: playerPosition.sceneY + 0.5 },
      );
      cameraFollowerRef.current.reset({ x: centered.panX, y: centered.panY });
      setViewport(centered);
      return;
    }
    stopFollowingPlayer();
    if (localScene && activePlacement) {
      setViewport(centerMapPoint(
        viewport,
        { x: 0, y: 0, width: localScene.pixelWidth, height: localScene.pixelHeight },
        fit,
        {
          x: activePlacement.pixelX + activePlacement.pixelWidth / 2,
          y: activePlacement.pixelY + activePlacement.pixelHeight / 2,
        },
      ));
      return;
    }
    setViewport(HOME_VIEWPORT);
  }

  function pointerPoint(event: JSX.TargetedPointerEvent<HTMLElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
  }

  function onPointerDown(event: JSX.TargetedPointerEvent<HTMLElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    if ((event.target as Element).closest('.map-control, .map-legend-panel')) return;
    const point = pointerPoint(event);
    const markerKey = (event.target as Element).closest<HTMLElement>('.map-marker')?.dataset.markerKey;
    if (markerKey) pressedMarkerRef.current.set(event.pointerId, markerKey);
    else pressedMarkerRef.current.delete(event.pointerId);
    gestureRef.current.down(event.pointerId, point.x, point.y);
    allowMarkerSelectionRef.current = false;
    event.currentTarget.classList.add('is-manipulating');
    if (event.currentTarget.setPointerCapture) event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function onPointerMove(event: JSX.TargetedPointerEvent<HTMLElement>) {
    if (!gestureRef.current.pointers.has(event.pointerId)) return;
    const point = pointerPoint(event);
    const before = gestureRef.current.viewport;
    const next = gestureRef.current.move(event.pointerId, point.x, point.y);
    if (next.scale !== before.scale || next.panX !== before.panX || next.panY !== before.panY) {
      stopFollowingPlayer();
    }
    setViewportState(next);
    event.preventDefault();
  }

  function finishPointer(event: JSX.TargetedPointerEvent<HTMLElement>, cancelled: boolean) {
    const markerKey = pressedMarkerRef.current.get(event.pointerId);
    pressedMarkerRef.current.delete(event.pointerId);
    const result = cancelled ? gestureRef.current.cancel(event.pointerId) : gestureRef.current.up(event.pointerId);
    setViewportState(result.viewport);
    allowMarkerSelectionRef.current = result.select;
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    if (gestureRef.current.activeCount === 0) event.currentTarget.classList.remove('is-manipulating');
    if (result.select && markerKey) setSelectedKey(markerKey);
    event.preventDefault();
  }

  function onWheel(event: JSX.TargetedWheelEvent<HTMLElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    stopFollowingPlayer();
    zoom(event.deltaY < 0 ? 1.18 : 1 / 1.18, { x: event.clientX - bounds.left, y: event.clientY - bounds.top });
    event.preventDefault();
  }

  const renderedWidth = fit.width * viewport.scale;
  const renderedHeight = fit.height * viewport.scale;
  const playerMapSpriteScale = Math.max(1, renderedWidth / activeMap.pixelWidth);
  const transform = `translate(calc(-50% + ${viewport.panX}px), calc(-50% + ${viewport.panY}px))`;
  const selectedIsCurrent = selectedLocation != null && currentLocation != null && selectedLocation.key === currentLocation.key;
  const displayName = activeMode === 'LOCAL'
    ? localMap?.displayName ?? state.currentAreaName ?? 'LOCAL MAP'
    : region?.displayName ?? 'WORLD MAP';
  const localLightingQuery = state.gameTime?.hours != null && state.gameTime.minutes != null
    ? `hour=${state.gameTime.hours}&minute=${state.gameTime.minutes}`
    : `lighting=${state.gameTime?.phase ?? 'DAY'}`;
  const localImageUrl = localMap ? mapImageUrl(localMap.imageUrl, localMap.dynamicLighting, localLightingQuery) : undefined;
  const activeImageUrl = activeMode === 'LOCAL' ? localImageUrl : region?.imageUrl;
  const poiZoomPercent = normalizedPoiZoom(viewport.scale, minimumScaleRef.current, maximumScaleRef.current);
  const atOrAboveStartingLocalZoom = viewport.scale + 0.0001 >= minimumScaleRef.current;
  const poiIconsVisible = activeMode === 'LOCAL' && atOrAboveStartingLocalZoom &&
    poiZoomPercent >= poiPreferences.iconZoomThresholdPercent;
  const poiLabelsVisible = poiIconsVisible && poiZoomPercent >= poiPreferences.labelZoomThresholdPercent;
  const localPoiMarkers = (activeMode === 'LOCAL'
    ? (state.localMapPois ?? []).flatMap(poi => {
      if (!poiCategoryEnabled(poi, poiPreferences)) return [];
      if (localScene) {
        const placement = visibleScenePlacements.find(candidate => candidate.localMapKey === poi.localMapKey);
        if (!placement) return [];
        return [{
          poi,
          x: (placement.gridX + poi.tileX + 0.5) / localScene.gridWidth * 100,
          y: (placement.gridY + poi.tileY + 0.5) / localScene.gridHeight * 100,
        }];
      }
      if (!localMap || poi.localMapKey !== localMap.key) return [];
      return [{
        poi,
        x: (poi.tileX + 0.5) / localMap.gridWidth * 100,
        y: (poi.tileY + 0.5) / localMap.gridHeight * 100,
      }];
    })
    : []).filter(marker => poiWithinViewport(
      marker.x,
      marker.y,
      renderedWidth,
      renderedHeight,
      viewport,
      stageRef.current?.clientWidth ?? 0,
      stageRef.current?.clientHeight ?? 0,
    )) as LocalPoiMarker[];
  const stageBounds = stageRef.current?.getBoundingClientRect();
  const visiblePoiLabelPlacements = poiLabelsVisible
    ? declutterPoiLabels(
      localPoiMarkers,
      renderedWidth,
      renderedHeight,
      viewport,
      stageBounds?.width ?? 0,
      stageBounds?.height ?? 0,
      selectedPoiKey,
    )
    : new Map<string, PoiLabelPlacement>();
  const selectedPoi = localPoiMarkers.find(marker => marker.poi.key === selectedPoiKey)?.poi;

  return <section class="screen map-screen">
    <header class="map-page-header">
      <div class="map-current-location">
        <strong>{activeMode === 'LOCAL' ? displayName : selectedLocation?.displayName ?? state.currentAreaName ?? 'Atlas'}</strong>
        <span>{activeMode === 'LOCAL' || selectedIsCurrent ? 'CURRENT' : selectedLocation ? 'MAP POINT' : 'ATLAS'}</span>
      </div>
      {state.gameTime && <GameClockIndicator clock={state.gameTime} />}
      <div class="header-actions map-header-actions">
        <button class="header-action map-dex-action" aria-label="Open Pokédex" onClick={onOpenPokedex}><DexIcon /></button>
        <button class="header-action settings-action" aria-label="Settings" onClick={onOpenSettings}><SettingsIcon /></button>
      </div>
    </header>
    <main
      ref={stageRef}
      class="map-stage"
      role="region"
      aria-label={activeMode === 'LOCAL' ? 'Interactive local map' : 'Interactive world map'}
      data-map-mode={activeMode}
      data-scale={viewport.scale}
      data-pan-x={viewport.panX}
      data-pan-y={viewport.panY}
      data-effective-raster-scale={renderedWidth / activeMap.pixelWidth}
      data-poi-zoom-percent={poiZoomPercent}
      data-selected-key={activeMode === 'ATLAS' ? selectedLocation?.key : localScene?.key ?? localMap?.key}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={event => finishPointer(event, false)}
      onPointerCancel={event => finishPointer(event, true)}
      onWheel={onWheel}
    >
      <div class="map-plane map-framed-plane" style={{ width: renderedWidth, height: renderedHeight, transform }}>
        {localScene
          ? visibleScenePlacements.map(placement => <img
            key={placement.localMapKey}
            class="map-scene-tile"
            data-local-map-key={placement.localMapKey}
            src={mapImageUrl(placement.imageUrl, placement.dynamicLighting, localLightingQuery)}
            alt=""
            aria-hidden="true"
            draggable={false}
            style={{
              left: `${placement.pixelX / localScene.pixelWidth * 100}%`,
              top: `${placement.pixelY / localScene.pixelHeight * 100}%`,
              width: `${placement.pixelWidth / localScene.pixelWidth * 100}%`,
              height: `${placement.pixelHeight / localScene.pixelHeight * 100}%`,
            }}
          />)
          : <img src={activeImageUrl} alt={`${displayName} ${activeMode === 'LOCAL' ? 'local' : 'region'} map`} draggable={false} />}
        {localScene && hiddenScenePlacements.map(placement => <span
            key={`fog/${placement.localMapKey}`}
            class="map-scene-placement-fog"
            data-local-map-key={placement.localMapKey}
            aria-hidden="true"
            style={{
              left: `${placement.pixelX / localScene.pixelWidth * 100}%`,
              top: `${placement.pixelY / localScene.pixelHeight * 100}%`,
              width: `${placement.pixelWidth / localScene.pixelWidth * 100}%`,
              height: `${placement.pixelHeight / localScene.pixelHeight * 100}%`,
            }}
          />)}
        {localScene && visibleScenePlacements.map(placement => {
          const name = localMapNames.get(placement.localMapKey);
          if (!name || placement.localMapKey === activePlacement?.localMapKey) return null;
          return <span
            key={`poi/${placement.localMapKey}`}
            class="map-local-poi-label"
            aria-label={`Map location: ${name}`}
            style={{
              left: `${(placement.pixelX + placement.pixelWidth / 2) / localScene.pixelWidth * 100}%`,
              top: `${placement.pixelY / localScene.pixelHeight * 100}%`,
            }}
          >{name}</span>;
        })}
        {fogVisible && region && <canvas ref={fogRef} class="map-fog" width={region.pixelWidth} height={region.pixelHeight} aria-hidden="true" />}
        {markerLocations.map(location => {
          const position = markerPosition(location, region!);
          return <button
            key={location.key}
            class={`map-marker atlas-location-marker ${location.key === currentLocation?.key ? 'is-current' : ''} ${location.key === selectedLocation?.key ? 'is-selected' : ''}`}
            data-marker-key={location.key}
            style={{ left: `${position.x}%`, top: `${position.y}%` }}
            aria-label={location.key === currentLocation?.key ? `Current location: ${location.displayName}` : location.displayName}
            onClick={event => {
              if (event.detail !== 0 && !allowMarkerSelectionRef.current) {
                event.preventDefault();
                return;
              }
              setSelectedKey(location.key);
            }}
          ><span /></button>;
        })}
        {playerPosition && <span
          class={`map-marker map-player-marker is-current ${playerMapSpriteUrl ? 'has-sprite' : 'is-fallback'}`}
          style={{
            left: `${((playerPosition.sceneX + 0.5) / activeMap.gridWidth) * 100}%`,
            top: `${((playerPosition.sceneY + 0.5) / activeMap.gridHeight) * 100}%`,
            ...(playerMapSpriteUrl ? {
              width: `${playerMapSpriteWidth * playerMapSpriteScale}px`,
              height: `${playerMapSpriteHeight * playerMapSpriteScale}px`,
            } : {}),
          }}
          aria-label={`Player position ${playerPosition.mapX}, ${playerPosition.mapY}`}
        >{playerMapSpriteUrl
            ? <img src={playerMapSpriteUrl} alt={state.trainer?.name ?? 'Player'} draggable={false} />
            : <span class="map-player-dot" />}</span>}
        {poiIconsVisible && localPoiMarkers.map(({ poi, x, y }) => <button
          key={poi.key}
          class={`map-poi-marker map-poi-${poi.category.toLowerCase().replaceAll('_', '-')} ${poi.state === 'SILHOUETTE' ? 'is-silhouette' : ''} ${poi.key === selectedPoiKey ? 'is-selected' : ''}`}
          data-poi-key={poi.key}
          style={{ left: `${x}%`, top: `${y}%` }}
          aria-label={poiAriaLabel(poi)}
          onClick={() => setSelectedPoiKey(current => current === poi.key ? null : poi.key)}
        >
          <span class="map-poi-symbol" aria-hidden="true">{poiSymbol(poi)}</span>
          {visiblePoiLabelPlacements.has(poi.key) && <span class={`map-poi-label is-${visiblePoiLabelPlacements.get(poi.key)}`}>{poiDisplayLabel(poi)}</span>}
        </button>)}
      </div>

      <nav class="map-utility-rail" aria-label="Map utilities">
        {activeMode === 'ATLAS' && maps.length > 1 && <button class="map-control" aria-label="Choose map region" aria-expanded={legendOpen} onClick={() => setLegendOpen(value => !value)}><MapIcon /></button>}
        {activeMode === 'LOCAL' && <button class="map-control map-poi-filter-control" aria-label="Map POI filters" aria-expanded={poiFiltersOpen} onClick={() => setPoiFiltersOpen(value => !value)}><FilterIcon /></button>}
        {legendOpen && activeMode === 'ATLAS' && maps.length > 1 && <div class="map-legend-panel">
          <small>{activeMode}</small>
          <strong>{displayName}</strong>
          <div class="map-region-options">{maps.map(item => <button key={item.key} aria-pressed={item.key === region?.key} onClick={() => setRegionKey(item.key)}>{item.displayName ?? item.key}</button>)}</div>
        </div>}
        {poiFiltersOpen && activeMode === 'LOCAL' && <div class="map-legend-panel map-poi-filter-panel">
          <strong>Map details</strong>
          <PoiToggle label="Places" checked={poiPreferences.showPlaces} onChange={checked => onUpdatePoiPreferences?.({ showPlaces: checked })} />
          <PoiToggle label="Services" checked={poiPreferences.showServices} onChange={checked => onUpdatePoiPreferences?.({ showServices: checked })} />
          <PoiToggle label="Available items" checked={poiPreferences.showAvailableItems} onChange={checked => onUpdatePoiPreferences?.({ showAvailableItems: checked })} />
          <PoiToggle label="Collected items" checked={poiPreferences.showCollectedItems} onChange={checked => onUpdatePoiPreferences?.({ showCollectedItems: checked })} />
          <PoiToggle label="Unknown POIs" checked={poiPreferences.showUnknownPois} onChange={checked => onUpdatePoiPreferences?.({ showUnknownPois: checked })} />
        </div>}
      </nav>

      {selectedPoi && <aside class="map-poi-card" aria-label="Map point details">
        <button aria-label="Close map point details" onClick={() => setSelectedPoiKey(null)}>×</button>
        <small>{poiCategoryLabel(selectedPoi)}</small>
        <strong>{poiLabel(selectedPoi)}</strong>
        {selectedPoi.state === 'COLLECTED' && <span>Collected</span>}
      </aside>}

      <nav class="map-zoom-rail" aria-label="Map view controls">
        <button class="map-control" aria-label="Zoom in" onClick={() => zoom(1.25)}>+</button>
        <button class="map-control" aria-label="Zoom out" onClick={() => zoom(0.8)}>−</button>
        <button class="map-control recenter-control" aria-label="Recenter map" onClick={recenter}><span /></button>
      </nav>
    </main>
  </section>;
}

function PoiToggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return <label class="map-poi-toggle"><input type="checkbox" checked={checked} onChange={event => onChange(event.currentTarget.checked)} /><span>{label}</span></label>;
}

function normalizedPoiZoom(scale: number, minimum: number, maximum: number) {
  if (!Number.isFinite(scale) || !Number.isFinite(minimum) || !Number.isFinite(maximum) || maximum <= minimum) return 0;
  return Math.round(Math.min(100, Math.max(0, (scale - minimum) / (maximum - minimum) * 100)));
}

function prefersReducedMotion() {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function poiWithinViewport(
  xPercent: number,
  yPercent: number,
  renderedWidth: number,
  renderedHeight: number,
  viewport: MapViewport,
  stageWidth: number,
  stageHeight: number,
) {
  if (stageWidth <= 0 || stageHeight <= 0) return true;
  const x = stageWidth / 2 + viewport.panX + (xPercent / 100 - 0.5) * renderedWidth;
  const y = stageHeight / 2 + viewport.panY + (yPercent / 100 - 0.5) * renderedHeight;
  const margin = 48;
  return x >= -margin && x <= stageWidth + margin && y >= -margin && y <= stageHeight + margin;
}

function declutterPoiLabels(
  markers: LocalPoiMarker[],
  renderedWidth: number,
  renderedHeight: number,
  viewport: MapViewport,
  stageWidth: number,
  stageHeight: number,
  selectedKey: string | null,
) {
  const labeled = markers.filter(marker => poiDisplayLabel(marker.poi));
  if (stageWidth <= 0 || stageHeight <= 0) return new Map(labeled.map(marker => [marker.poi.key, 'below' as const]));
  const occupied: Array<{ left: number; top: number; right: number; bottom: number; centerX: number; centerY: number }> = [];
  const visible = new Map<string, PoiLabelPlacement>();
  const ordered = [...labeled].sort((left, right) =>
    Number(right.poi.key === selectedKey) - Number(left.poi.key === selectedKey) || left.poi.key.localeCompare(right.poi.key));
  for (const marker of ordered) {
    const label = poiDisplayLabel(marker.poi)!;
    const centerX = stageWidth / 2 + viewport.panX + (marker.x / 100 - 0.5) * renderedWidth;
    const centerY = stageHeight / 2 + viewport.panY + (marker.y / 100 - 0.5) * renderedHeight;
    const width = Math.min(210, Math.max(48, label.length * 9 + 10));
    if (occupied.some(other => Math.abs(centerX - other.centerX) < 1 && Math.abs(centerY - other.centerY) < 1) && marker.poi.key !== selectedKey) continue;
    const candidates = [
      { placement: 'below' as const, left: centerX - width / 2, top: centerY + 16, right: centerX + width / 2, bottom: centerY + 40, centerX, centerY },
      { placement: 'above' as const, left: centerX - width / 2, top: centerY - 40, right: centerX + width / 2, bottom: centerY - 16, centerX, centerY },
    ];
    const candidate = candidates.find(bounds => !occupied.some(other =>
      bounds.left < other.right + 4 && bounds.right + 4 > other.left &&
      bounds.top < other.bottom + 4 && bounds.bottom + 4 > other.top));
    if (!candidate && marker.poi.key !== selectedKey) continue;
    const accepted = candidate ?? candidates[0];
    occupied.push(accepted);
    visible.set(marker.poi.key, accepted.placement);
  }
  return visible;
}

function poiCategoryEnabled(poi: LocalMapPoiView, preferences: LocalMapPoiPreferences) {
  switch (poi.category) {
    case 'PLACE': return preferences.showPlaces;
    case 'SERVICE': return preferences.showServices;
    case 'AVAILABLE_ITEM': return preferences.showAvailableItems;
    case 'COLLECTED_ITEM': return preferences.showCollectedItems;
    case 'UNKNOWN': return preferences.showUnknownPois;
  }
}

function poiLabel(poi: LocalMapPoiView) {
  return poiDisplayLabel(poi) ?? (poi.category === 'AVAILABLE_ITEM' || poi.category === 'COLLECTED_ITEM'
    ? 'Unidentified item'
    : poi.category === 'SERVICE'
      ? 'Unidentified service'
      : poi.category === 'PLACE'
        ? 'Unidentified entrance'
        : 'Unidentified point');
}

function poiDisplayLabel(poi: LocalMapPoiView) {
  return poi.itemName ?? poi.displayName;
}

function poiCategoryLabel(poi: LocalMapPoiView) {
  return poi.category === 'COLLECTED_ITEM' ? 'Collected item' : poi.category.replaceAll('_', ' ').toLowerCase();
}

function poiAriaLabel(poi: LocalMapPoiView) {
  return `${poiCategoryLabel(poi)}: ${poiLabel(poi)}`;
}

function poiSymbol(poi: LocalMapPoiView) {
  switch (poi.category) {
    case 'PLACE': return '⌂';
    case 'SERVICE': return '+';
    case 'AVAILABLE_ITEM':
    case 'COLLECTED_ITEM': return '◒';
    case 'UNKNOWN': return '?';
  }
}

function mapImageUrl(imageUrl: string, dynamicLighting: boolean, lightingQuery: string) {
  return dynamicLighting ? `${imageUrl}?${lightingQuery}` : imageUrl;
}

function markerPosition(location: WorldMapLocation, region: WorldMapRegion) {
  const cells = location.geometry;
  if (cells.length === 0) return { x: 50, y: 50 };
  const left = Math.min(...cells.map(cell => cell.x));
  const top = Math.min(...cells.map(cell => cell.y));
  const right = Math.max(...cells.map(cell => cell.x + cell.width));
  const bottom = Math.max(...cells.map(cell => cell.y + cell.height));
  return { x: ((left + right) / 2 / region.gridWidth) * 100, y: ((top + bottom) / 2 / region.gridHeight) * 100 };
}

export function paintFog(canvas: HTMLCanvasElement, region: WorldMapRegion, revealedLocations: WorldMapLocation[]) {
  const context = canvas.getContext('2d');
  if (!context) return;
  context.clearRect(0, 0, region.pixelWidth, region.pixelHeight);
  context.fillStyle = '#000';
  context.fillRect(0, 0, region.pixelWidth, region.pixelHeight);
  if (revealedLocations.length > 0) {
    context.save();
    context.globalCompositeOperation = 'destination-out';
    for (const location of revealedLocations) for (const cell of location.geometry) {
      const centerX = ((cell.x + cell.width / 2) / region.gridWidth) * region.pixelWidth;
      const centerY = ((cell.y + cell.height / 2) / region.gridHeight) * region.pixelHeight;
      const radius = Math.max(14, Math.max(cell.width / region.gridWidth * region.pixelWidth, cell.height / region.gridHeight * region.pixelHeight) * 1.5);
      const aperture = context.createRadialGradient(centerX, centerY, radius * 0.35, centerX, centerY, radius);
      aperture.addColorStop(0, 'rgba(0, 0, 0, 1)');
      aperture.addColorStop(1, 'rgba(0, 0, 0, 0)');
      context.fillStyle = aperture;
      context.fillRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }
    context.restore();
  }
  context.globalCompositeOperation = 'source-over';
  context.fillStyle = '#000';
  context.fillRect(0, 0, region.pixelWidth, 1);
  context.fillRect(0, region.pixelHeight - 1, region.pixelWidth, 1);
  context.fillRect(0, 0, 1, region.pixelHeight);
  context.fillRect(region.pixelWidth - 1, 0, 1, region.pixelHeight);
}
