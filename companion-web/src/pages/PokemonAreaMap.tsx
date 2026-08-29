import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { DexIcon } from '../components';
import type { Catalog, State, WorldMapLocation, WorldMapRegion } from '../models';
import { paintFog } from './MapPage';

type Send = (type: string, values?: Record<string, string | number | boolean | null>) => void;

export function PokemonAreaMap({ catalog, state, speciesId, send }: { catalog: Catalog; state: State; speciesId: number; send: Send }) {
  const habitatBaseIds = useMemo(() => new Set(catalog.areas
    .filter(area => area.speciesIds.includes(speciesId))
    .map(area => area.baseAreaId ?? Math.floor(area.id / 10))), [catalog.areas, speciesId]);
  const organic = state.settings.knowledgeMode === 'ORGANIC';
  const visibleBaseIds = useMemo(
    () => organic ? new Set(state.observedAreaBaseIdsBySpecies?.[speciesId] ?? []) : habitatBaseIds,
    [habitatBaseIds, organic, speciesId, state.observedAreaBaseIdsBySpecies],
  );
  const presentedBaseIds = organic ? visibleBaseIds : habitatBaseIds;
  const mapRegions = useMemo(() => (catalog.worldMaps ?? []).filter(region =>
    region.locations.some(location => location.baseAreaIds.some(baseAreaId => presentedBaseIds.has(baseAreaId))),
  ), [catalog.worldMaps, presentedBaseIds]);
  const [regionKey, setRegionKey] = useState(() => mapRegions[0]?.key ?? '');
  const region = mapRegions.find(candidate => candidate.key === regionKey) ?? mapRegions[0];
  const visibleLocations = useMemo(() => region?.locations.filter(location =>
    location.baseAreaIds.some(baseAreaId => visibleBaseIds.has(baseAreaId)),
  ) ?? [], [region?.key, visibleBaseIds]);
  const [selectedKey, setSelectedKey] = useState(() => visibleLocations[0]?.key ?? '');
  const selected = visibleLocations.find(location => location.key === selectedKey) ?? visibleLocations[0];
  const fogRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (region && fogRef.current) paintFog(fogRef.current, region, visibleLocations);
  }, [region?.key, visibleLocations]);

  if (!region) return <div class="pokemon-area-empty"><strong>{organic && habitatBaseIds.size > 0 ? 'NO KNOWN LOCATIONS' : 'NO HABITAT MAP'}</strong><p>{organic && habitatBaseIds.size > 0 ? 'Discover this Pokémon in the wild to reveal its habitat.' : 'No habitat map is available for this game.'}</p></div>;

  return <section class="pokemon-area-panel" aria-label="Pokémon habitat atlas">
    <header>
      <div><small>HABITAT</small><strong>{region.displayName ?? 'REGION'}</strong></div>
      {mapRegions.length > 1 && <div class="pokemon-area-regions" aria-label="Habitat regions">{mapRegions.map(candidate =>
        <button key={candidate.key} aria-pressed={candidate.key === region.key} onClick={() => { setRegionKey(candidate.key); setSelectedKey(''); }}>{candidate.displayName ?? candidate.key}</button>
      )}</div>}
    </header>
    <div
      class="pokemon-area-canvas"
      style={{ aspectRatio: `${region.pixelWidth} / ${region.pixelHeight}`, maxWidth: `${330 * region.pixelWidth / region.pixelHeight}px` }}
      role="img"
      aria-label={`${region.displayName ?? 'Region'} ${catalog.species.find(species => species.id === speciesId)?.name ?? 'Pokémon'} habitat map`}
    >
      <img src={region.imageUrl} alt="" draggable={false} />
      <canvas ref={fogRef} width={region.pixelWidth} height={region.pixelHeight} aria-hidden="true" />
      {visibleLocations.map(location => {
        const position = habitatMarkerPosition(location, region);
        return <button
          key={location.key}
          class={selected?.key === location.key ? 'is-selected' : ''}
          style={{ left: `${position.x}%`, top: `${position.y}%` }}
          aria-label={`Observed at ${location.displayName}`}
          onClick={() => setSelectedKey(location.key)}
        ><span /></button>;
      })}
      {selected && <button class="pokemon-area-dex" aria-label="Open selected Area Pokédex" onClick={() => send('MAP_AREA', { regionKey: region.key, locationKey: selected.key })}><DexIcon /></button>}
    </div>
    {visibleLocations.length === 0 && <p class="pokemon-area-undiscovered">No organically observed habitat yet. Undiscovered locations stay masked.</p>}
  </section>;
}

function habitatMarkerPosition(location: WorldMapLocation, region: WorldMapRegion) {
  const cells = location.geometry;
  if (cells.length === 0) return { x: 50, y: 50 };
  const left = Math.min(...cells.map(cell => cell.x));
  const top = Math.min(...cells.map(cell => cell.y));
  const right = Math.max(...cells.map(cell => cell.x + cell.width));
  const bottom = Math.max(...cells.map(cell => cell.y + cell.height));
  return { x: ((left + right) / 2 / region.gridWidth) * 100, y: ((top + bottom) / 2 / region.gridHeight) * 100 };
}
