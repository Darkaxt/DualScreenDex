import { useMemo, useRef, useState } from 'preact/hooks';
import type { Catalog, State, WorldMapRegion } from '../models';

type Send = (type: string, values?: Record<string, string | number | boolean | null>) => void;

export function WorldMapPage({ catalog, state, send }: { catalog: Catalog; state: State; send: Send }) {
  const maps = catalog.worldMaps ?? [];
  const activeBase = state.activeAreaBaseId ?? state.currentAreaBaseId ?? null;
  const initialRegion = Math.max(0, maps.findIndex(region => region.locations.some(location => activeBase != null && location.baseAreaIds.includes(activeBase))));
  const [regionIndex, setRegionIndex] = useState(initialRegion);
  const region = maps[regionIndex] ?? maps[0];
  const activeLocation = region?.locations.find(location => activeBase != null && location.baseAreaIds.includes(activeBase));
  const [selectedKey, setSelectedKey] = useState<string | null>(activeLocation?.key ?? null);
  const selected = region?.locations.find(location => location.key === selectedKey) ?? activeLocation ?? null;
  const [scale, setScale] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
  const dragged = useRef(false);
  const [legendOpen, setLegendOpen] = useState(false);
  const policy = state.settings.knowledgeMode;
  const revealedBases = useMemo(() => new Set([
    ...(state.visitedAreaBaseIds ?? []),
    ...Object.values(state.observedAreaBaseIdsBySpecies ?? {}).flat(),
    ...(state.currentAreaBaseId == null ? [] : [state.currentAreaBaseId]),
  ]), [state.visitedAreaBaseIds, state.observedAreaBaseIdsBySpecies, state.currentAreaBaseId]);
  const revealed = (location: WorldMapRegion['locations'][number]) => policy !== 'ORGANIC' || location.baseAreaIds.some(id => revealedBases.has(id));
  const current = region?.locations.find(location => state.currentAreaBaseId != null && location.baseAreaIds.includes(state.currentAreaBaseId));

  if (!region) return <section class="screen map-unavailable"><button onClick={() => send('SCREEN', { screen: 'POKEDEX' })}>Back to Pokédex</button></section>;
  const title = region.displayName ?? catalog.family.replaceAll('_', ' ');
  const subtitle = selected?.displayName ?? activeLocation?.displayName ?? 'WORLD MAP';
  const activeIsCurrent = selected != null && current?.key === selected.key;
  const transform = `translate(${pan.x}px, ${pan.y}px) scale(${scale})`;
  const maskId = `fog-${region.key.replace(/[^a-z0-9_-]/gi, '-')}`;
  const revealId = `${maskId}-reveal`;
  const recenter = () => setPan({ x: 0, y: 0 });

  return <section class="screen world-map-screen">
    <header class="map-header">
      <div class="map-heading"><strong>{title}</strong><span>{subtitle}{activeIsCurrent && <b>CURRENT</b>}</span></div>
      <button class="map-global-dex" onClick={() => send('SCREEN', { screen: 'POKEDEX' })} aria-label="Open Pokédex" title="Open Pokédex">◉</button>
    </header>
    <div class="map-stage" onWheel={event => {
      event.preventDefault();
      setScale(value => clampScale(value + (event.deltaY < 0 ? .25 : -.25)));
    }} onPointerDown={event => {
      drag.current = { x: event.clientX, y: event.clientY, panX: pan.x, panY: pan.y };
      dragged.current = false;
      event.currentTarget.setPointerCapture(event.pointerId);
    }} onPointerMove={event => {
      if (!drag.current) return;
      if (Math.abs(event.clientX - drag.current.x) + Math.abs(event.clientY - drag.current.y) > 4) dragged.current = true;
      setPan({ x: drag.current.panX + event.clientX - drag.current.x, y: drag.current.panY + event.clientY - drag.current.y });
    }} onPointerUp={() => { drag.current = null; }}>
      <div class="map-canvas" style={{ width: region.pixelWidth, height: region.pixelHeight, transform }}>
        {policy === 'ORGANIC' ? <svg class="map-raster" viewBox={`0 0 ${region.pixelWidth} ${region.pixelHeight}`} role="img" aria-label={`${title} explored map`}>
          <defs><radialGradient id={revealId}><stop offset="0%" stop-color="white" /><stop offset="68%" stop-color="white" /><stop offset="100%" stop-color="black" /></radialGradient><mask id={maskId}><rect width="100%" height="100%" fill="black" />{region.locations.filter(revealed).flatMap(location => location.geometry.map((cell, index) => <ellipse key={`${location.key}-${index}`} cx={(cell.x + cell.width / 2) * region.pixelWidth / region.gridWidth} cy={(cell.y + cell.height / 2) * region.pixelHeight / region.gridHeight} rx={Math.max(18, cell.width * region.pixelWidth / region.gridWidth * 2.4)} ry={Math.max(18, cell.height * region.pixelHeight / region.gridHeight * 2.4)} fill={`url(#${revealId})`} />))}</mask></defs>
          <rect width="100%" height="100%" fill="black" />
          <image href={region.assetUrl} width={region.pixelWidth} height={region.pixelHeight} mask={`url(#${maskId})`} image-rendering="pixelated" />
        </svg> : <img class="map-raster" src={region.assetUrl} alt={`${title} map`} />}
        {region.locations.filter(revealed).map(location => {
          const cell = location.geometry[0];
          const isCurrent = current?.key === location.key;
          const isSelected = selected?.key === location.key;
          return <button key={location.key} class={`map-location ${isCurrent ? 'current' : ''} ${isSelected ? 'selected' : ''}`} style={{ left: `${(cell.x + cell.width / 2) / region.gridWidth * 100}%`, top: `${(cell.y + cell.height / 2) / region.gridHeight * 100}%` }} onClick={event => { event.stopPropagation(); if (!dragged.current) setSelectedKey(location.key); }} aria-label={`${location.displayName}${isCurrent ? ', current location' : ''}`}><i /><span>{location.displayName}</span></button>;
        })}
      </div>
      <div class="map-left-controls">
        <button aria-label="Layers" title="Layers" aria-expanded={legendOpen} onClick={() => setLegendOpen(value => !value)}>◇</button>
        {selected && revealed(selected) && <button class="map-area-dex" aria-label="Open Area Pokédex" title="Open Area Pokédex" onClick={() => send('MAP_AREA', { locationKey: selected.key })}>◉</button>}
        {maps.length > 1 && <button aria-label="Next region" title="Next region" onClick={() => { setRegionIndex(value => (value + 1) % maps.length); setSelectedKey(null); recenter(); }}>↔</button>}
        {legendOpen && <div class="map-legend" role="region" aria-label="Map legend"><span><i class="current" />Current</span><span><i class="selected" />Selected</span><span><i class="explored" />Explored</span></div>}
      </div>
      <div class="map-right-controls">
        <button aria-label="Zoom in" title="Zoom in" onClick={() => setScale(value => clampScale(value + .25))}>+</button>
        <button aria-label="Zoom out" title="Zoom out" onClick={() => setScale(value => clampScale(value - .25))}>−</button>
        <button aria-label="Recenter map" title="Recenter map" onClick={recenter}>⌖</button>
      </div>
    </div>
  </section>;
}

function clampScale(value: number): number { return Math.min(4, Math.max(1, value)); }

export function PokemonAreaMap({ catalog, state, speciesId, send }: { catalog: Catalog; state: State; speciesId: number; send: Send }) {
  const observedBases = new Set(state.observedAreaBaseIdsBySpecies?.[speciesId] ?? []);
  const maps = catalog.worldMaps ?? [];
  const region = maps.find(candidate => candidate.locations.some(location => location.baseAreaIds.some(id => observedBases.has(id)))) ?? maps[0];
  const highlighted = region?.locations.filter(location => location.baseAreaIds.some(id => observedBases.has(id))) ?? [];
  const [selectedKey, setSelectedKey] = useState<string | null>(highlighted[0]?.key ?? null);
  const selected = highlighted.find(location => location.key === selectedKey) ?? null;
  if (!region) return <div class="pokemon-area-map empty-state"><strong>MAP UNAVAILABLE</strong></div>;
  const maskId = `pokemon-area-${speciesId}-${region.key.replace(/[^a-z0-9_-]/gi, '-')}`;
  const revealId = `${maskId}-reveal`;
  return <div class="pokemon-area-map">
    <div class="pokemon-area-map-canvas" style={{ aspectRatio: `${region.pixelWidth} / ${region.pixelHeight}` }}>
      {state.settings.knowledgeMode === 'ORGANIC' ? <svg viewBox={`0 0 ${region.pixelWidth} ${region.pixelHeight}`} role="img" aria-label={`${region.displayName ?? catalog.family} observed locations map`}><defs><radialGradient id={revealId}><stop offset="0%" stop-color="white" /><stop offset="68%" stop-color="white" /><stop offset="100%" stop-color="black" /></radialGradient><mask id={maskId}><rect width="100%" height="100%" fill="black" />{highlighted.flatMap(location => location.geometry.map((cell, index) => <ellipse key={`${location.key}-${index}`} cx={(cell.x + cell.width / 2) * region.pixelWidth / region.gridWidth} cy={(cell.y + cell.height / 2) * region.pixelHeight / region.gridHeight} rx={Math.max(18, cell.width * region.pixelWidth / region.gridWidth * 2.4)} ry={Math.max(18, cell.height * region.pixelHeight / region.gridHeight * 2.4)} fill={`url(#${revealId})`} />))}</mask></defs><rect width="100%" height="100%" fill="black" /><image href={region.assetUrl} width={region.pixelWidth} height={region.pixelHeight} mask={`url(#${maskId})`} image-rendering="pixelated" /></svg> : <img src={region.assetUrl} alt={`${region.displayName ?? catalog.family} map`} />}
      {highlighted.map(location => { const cell = location.geometry[0]; return <button key={location.key} class={selected?.key === location.key ? 'selected' : ''} style={{ left: `${(cell.x + cell.width / 2) / region.gridWidth * 100}%`, top: `${(cell.y + cell.height / 2) / region.gridHeight * 100}%` }} onClick={() => setSelectedKey(location.key)} aria-label={`Observed at ${location.displayName}`}><i /></button>; })}
      {selected && <button class="pokemon-area-dex" aria-label="Open Area Pokédex" title="Open Area Pokédex" onClick={() => send('MAP_AREA', { locationKey: selected.key })}>◉</button>}
    </div>
    {highlighted.length === 0 && <p class="pokemon-area-empty">No known locations yet.</p>}
  </div>;
}
