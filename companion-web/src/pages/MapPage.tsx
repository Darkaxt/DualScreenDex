import type { JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { DexIcon, MapIcon, SettingsIcon } from '../components';
import { anchoredZoom, containFit, GestureTracker, type MapViewport } from '../mapEngine';
import type { Catalog, State, WorldMapLocation, WorldMapRegion } from '../models';

interface MapPageProps {
  catalog: Catalog;
  state: State;
  onOpenAreaDex: (regionKey: string, location: WorldMapLocation) => void;
  onOpenSettings: () => void;
}

type MapMode = 'LOCAL' | 'ATLAS';

const HOME_VIEWPORT: MapViewport = { scale: 1, panX: 0, panY: 0 };

export function MapPage({ catalog, state, onOpenAreaDex, onOpenSettings }: MapPageProps) {
  const maps = catalog.worldMaps ?? [];
  const localMap = (catalog.localMaps ?? []).find(map => map.baseAreaId === state.currentAreaBaseId);
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
  const [mode, setMode] = useState<MapMode>(() => localMap ? 'LOCAL' : 'ATLAS');
  const modeSelectedRef = useRef(false);
  const activeMode: MapMode = mode === 'LOCAL' && localMap ? 'LOCAL' : 'ATLAS';
  const activeMap = activeMode === 'LOCAL' ? localMap : region;
  const [viewport, setViewportState] = useState<MapViewport>(HOME_VIEWPORT);
  const [fit, setFit] = useState({ width: activeMap?.pixelWidth ?? 1, height: activeMap?.pixelHeight ?? 1, scale: 1 });
  const fogVisible = activeMode === 'ATLAS' && state.settings.knowledgeMode !== 'DISCOVERED';
  const [markersVisible, setMarkersVisible] = useState(true);
  const [legendOpen, setLegendOpen] = useState(false);
  const stageRef = useRef<HTMLElement>(null);
  const fogRef = useRef<HTMLCanvasElement>(null);
  const gestureRef = useRef(new GestureTracker(HOME_VIEWPORT));
  const allowMarkerSelectionRef = useRef(true);
  const pressedMarkerRef = useRef(new Map<number, string>());
  const revealedBaseIds = useMemo(() => new Set([
    ...(state.revealedAreaBaseIds ?? []),
    ...(currentLocation?.baseAreaIds ?? []),
  ]), [state.revealedAreaBaseIds, currentLocation?.key]);
  const revealedLocations = useMemo(
    () => region?.locations.filter(location => location.baseAreaIds.some(baseAreaId => revealedBaseIds.has(baseAreaId))) ?? [],
    [region?.key, revealedBaseIds],
  );
  const selectedCandidate = region?.locations.find(location => location.key === selectedKey);
  const selectedLocation = fogVisible
    ? revealedLocations.find(location => location.key === selectedCandidate?.key) ?? currentLocation ?? revealedLocations[0]
    : selectedCandidate ?? currentLocation ?? region?.locations[0];
  const selectedHasEncounterAreas = selectedLocation != null && catalog.areas.some(area =>
    selectedLocation.baseAreaIds.includes(area.baseAreaId ?? Math.floor(area.id / 10)),
  );
  const playerPosition = activeMode === 'LOCAL' && localMap && state.currentMapPosition &&
    state.currentMapPosition.x >= 0 && state.currentMapPosition.x < localMap.gridWidth &&
    state.currentMapPosition.y >= 0 && state.currentMapPosition.y < localMap.gridHeight
    ? state.currentMapPosition
    : undefined;

  useEffect(() => {
    if (localMap && !modeSelectedRef.current) setMode('LOCAL');
    else if (!localMap && mode === 'LOCAL') setMode('ATLAS');
  }, [localMap?.key]);

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
    setViewport(HOME_VIEWPORT);
  }, [activeMode, activeMap?.key]);

  useEffect(() => {
    if (!activeMap || !stageRef.current) return;
    const stage = stageRef.current;
    const measure = () => {
      const bounds = stage.getBoundingClientRect();
      const availableWidth = bounds.width || activeMap.pixelWidth;
      const availableHeight = bounds.height || activeMap.pixelHeight;
      setFit(containFit(activeMap.pixelWidth, activeMap.pixelHeight, availableWidth, availableHeight));
      gestureRef.current.setCenter(availableWidth / 2, availableHeight / 2);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(stage);
    return () => observer.disconnect();
  }, [activeMap?.key, activeMap?.pixelWidth, activeMap?.pixelHeight]);

  useEffect(() => {
    if (!region || !fogVisible || !fogRef.current) return;
    paintFog(fogRef.current, region, revealedLocations);
  }, [region?.key, revealedLocations, fogVisible]);

  const markerLocations = useMemo(() => {
    if (activeMode !== 'ATLAS' || !region || !markersVisible) return [];
    return fogVisible ? revealedLocations : region.locations;
  }, [activeMode, region?.key, revealedLocations, fogVisible, markersVisible]);

  if (!activeMap) return null;

  function setViewport(next: MapViewport) {
    gestureRef.current.setViewport(next);
    setViewportState(next);
  }

  function zoom(multiplier: number, anchor?: { x: number; y: number }) {
    const bounds = stageRef.current?.getBoundingClientRect();
    const center = { x: (bounds?.width ?? activeMap!.pixelWidth) / 2, y: (bounds?.height ?? activeMap!.pixelHeight) / 2 };
    setViewport(anchoredZoom(viewport, viewport.scale * multiplier, anchor ?? center, center));
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
    setViewportState(gestureRef.current.move(event.pointerId, point.x, point.y));
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
    zoom(event.deltaY < 0 ? 1.18 : 1 / 1.18, { x: event.clientX - bounds.left, y: event.clientY - bounds.top });
    event.preventDefault();
  }

  function switchMode() {
    modeSelectedRef.current = true;
    setMode(activeMode === 'LOCAL' ? 'ATLAS' : 'LOCAL');
  }

  const transform = `translate(calc(-50% + ${viewport.panX}px), calc(-50% + ${viewport.panY}px)) scale(${viewport.scale})`;
  const selectedIsCurrent = selectedLocation?.key === currentLocation?.key;
  const displayName = activeMode === 'LOCAL'
    ? localMap?.displayName ?? state.currentAreaName ?? 'LOCAL MAP'
    : region?.displayName ?? 'WORLD MAP';

  return <section class="screen map-screen">
    <header class="map-page-header">
      <div class="map-current-location">
        <strong>{activeMode === 'LOCAL' ? displayName : selectedLocation?.displayName ?? state.currentAreaName ?? 'Unknown location'}</strong>
        <span>{activeMode === 'LOCAL' || selectedIsCurrent ? 'CURRENT' : 'MAP POINT'}</span>
      </div>
      <div class="header-actions map-header-actions">
        <button class="header-action settings-action" aria-label="Settings" onClick={onOpenSettings}><SettingsIcon /></button>
        <button class="header-action map-dex-action" aria-label="Open Area Pokédex" disabled={!region || !selectedHasEncounterAreas} onClick={() => region && selectedLocation && selectedHasEncounterAreas && onOpenAreaDex(region.key, selectedLocation)}><DexIcon /></button>
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
      data-selected-key={activeMode === 'ATLAS' ? selectedLocation?.key : localMap?.key}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={event => finishPointer(event, false)}
      onPointerCancel={event => finishPointer(event, true)}
      onWheel={onWheel}
    >
      <div class="map-plane map-framed-plane" style={{ width: fit.width, height: fit.height, transform }}>
        <img src={activeMap.imageUrl} alt={`${displayName} ${activeMode === 'LOCAL' ? 'local' : 'region'} map`} draggable={false} />
        {fogVisible && region && <canvas ref={fogRef} class="map-fog" width={region.pixelWidth} height={region.pixelHeight} aria-hidden="true" />}
        {markerLocations.map(location => {
          const position = markerPosition(location, region!);
          return <button
            key={location.key}
            class={`map-marker ${location.key === currentLocation?.key ? 'is-current' : ''} ${location.key === selectedLocation?.key ? 'is-selected' : ''}`}
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
        {playerPosition && localMap && <span
          class="map-marker map-player-marker is-current"
          style={{
            left: `${((playerPosition.x + 0.5) / localMap.gridWidth) * 100}%`,
            top: `${((playerPosition.y + 0.5) / localMap.gridHeight) * 100}%`,
          }}
          aria-label={`Player position ${playerPosition.x}, ${playerPosition.y}`}
        ><span /></span>}
      </div>

      <nav class="map-utility-rail" aria-label="Map utilities">
        {activeMode === 'ATLAS' && maps.length > 1 && <button class="map-control" aria-label="Choose map region" aria-expanded={legendOpen} onClick={() => setLegendOpen(value => !value)}><MapIcon /></button>}
        {activeMode === 'ATLAS' && <button class="map-control marker-control" aria-label="Toggle map markers" aria-pressed={markersVisible} onClick={() => setMarkersVisible(value => !value)}><span class="pin-icon" /></button>}
        {legendOpen && activeMode === 'ATLAS' && maps.length > 1 && <div class="map-legend-panel">
          <small>{activeMode}</small>
          <strong>{displayName}</strong>
          <div class="map-region-options">{maps.map(item => <button key={item.key} aria-pressed={item.key === region?.key} onClick={() => setRegionKey(item.key)}>{item.displayName ?? item.key}</button>)}</div>
        </div>}
      </nav>

      {localMap && region && <button
        class="map-control map-mode-control"
        aria-label={activeMode === 'LOCAL' ? 'Show Atlas' : 'Show Local map'}
        onClick={switchMode}
      >{activeMode === 'LOCAL' ? 'A' : 'L'}</button>}

      <nav class="map-zoom-rail" aria-label="Map view controls">
        <button class="map-control" aria-label="Zoom in" onClick={() => zoom(1.25)}>+</button>
        <button class="map-control" aria-label="Zoom out" onClick={() => zoom(0.8)}>−</button>
        <button class="map-control recenter-control" aria-label="Recenter map" onClick={() => setViewport(HOME_VIEWPORT)}><span /></button>
      </nav>
    </main>
  </section>;
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
