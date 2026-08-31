import type { JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import type { Catalog, EncounterWindow, State } from '../models';
import { Header, maskIdentityName, PokedexAvatar, speciesIdentityKnowledge, StatusMarks, TypeChip, uniqueTypeIds } from '../components';

const DEFAULT_SPECIES_ROW_HEIGHT = 94;
const COMPACT_SPECIES_ROW_HEIGHT = 68;
const MAX_MOUNTED_SPECIES = 60;
const OVERSCAN_ROWS = 3;

export function PokedexBrowse({ catalog, state, send, onOpenMap }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void; onOpenMap?: () => void }) {
  const [search, setSearch] = useState('');
  const rowHeight = pokedexRowHeight(state.settings.density, state.settings.fontScale);
  const previousRowHeightRef = useRef(rowHeight);
  const listRef = useRef<HTMLDivElement>(null);
  const [viewport, setViewport] = useState(() => ({
    scrollTop: 0,
    height: typeof window === 'undefined' ? rowHeight * 8 : window.innerHeight,
    width: typeof window === 'undefined' ? 1024 : window.innerWidth,
  }));
  const policy = state.settings.knowledgeMode;
  const activeFilter = policy === 'ORGANIC' && state.filter === 'SEEN' ? 'ALL' : state.filter;
  const filters = policy === 'ORGANIC'
    ? (['ALL', 'CAUGHT', 'TEAM', 'AREA'] as const)
    : (['ALL', 'CAUGHT', 'SEEN', 'TEAM', 'AREA'] as const);
  const capabilities = state.saveRam?.capabilities ?? {};
  const available = (name: string) => capabilities[name] === 'AVAILABLE' || capabilities[name] === 'PARTIAL';
  const projectedAvailability = useMemo(() => {
    let caught = false;
    let seen = false;
    let team = false;
    for (const species of catalog.species) {
      const status = state.speciesState[species.id];
      caught ||= Boolean(status?.caught);
      seen ||= Boolean(status?.seen || status?.caught);
      team ||= Boolean(status?.team);
    }
    return { caught, seen, team };
  }, [catalog.species, state.speciesState]);
  const filterEnabled = {
    ALL: true,
    CAUGHT: available('CAUGHT') || projectedAvailability.caught,
    SEEN: available('SEEN') || projectedAvailability.seen,
    TEAM: (available('PARTY') && available('SPECIES')) || projectedAvailability.team,
    AREA: (state.currentAreaIds?.length ?? 0) > 0,
  } as const;
  const areaSpeciesIds = useMemo(() => new Set(catalog.areas
    .filter(area => (state.currentAreaIds ?? []).includes(area.id))
    .flatMap(area => area.speciesIds)
    .filter(id => id > 0)), [catalog.areas, state.currentAreaIds]);
  const visible = useMemo(() => catalog.species.filter(species => {
    const status = state.speciesState[species.id];
    const identityKnown = status?.seen || status?.caught;
    const organicArea = policy === 'ORGANIC' && activeFilter === 'AREA';
    if (policy === 'ORGANIC' && !organicArea && !identityKnown) return false;
    if (policy === 'HIDDEN' && !status?.caught) return false;
    if (search && (organicArea && !identityKnown || (!species.name.toLowerCase().includes(search.toLowerCase()) && !String(species.dex).includes(search)))) return false;
    if (activeFilter === 'CAUGHT' && !status?.caught) return false;
    if (activeFilter === 'SEEN' && !status?.seen && !status?.caught) return false;
    if (activeFilter === 'TEAM' && !status?.team) return false;
    if (activeFilter === 'AREA' && !areaSpeciesIds.has(species.id)) return false;
    return true;
  }).sort((left, right) => {
    if (activeFilter !== 'AREA') return 0;
    const leftKnown = Boolean(state.speciesState[left.id]?.seen || state.speciesState[left.id]?.caught);
    const rightKnown = Boolean(state.speciesState[right.id]?.seen || state.speciesState[right.id]?.caught);
    return Number(rightKnown) - Number(leftKnown);
  }), [activeFilter, areaSpeciesIds, catalog.species, policy, search, state.speciesState]);
  const columnCount = pokedexColumnCount(viewport.width);
  const virtualWindow = useMemo(
    () => pokedexVirtualWindow(visible.length, columnCount, viewport.scrollTop, viewport.height, rowHeight),
    [columnCount, rowHeight, viewport.height, viewport.scrollTop, visible.length],
  );
  const mounted = visible.slice(virtualWindow.startIndex, virtualWindow.endIndex);

  useEffect(() => {
    const updateViewport = () => {
      const list = listRef.current;
      setViewport(current => ({
        scrollTop: list?.scrollTop ?? current.scrollTop,
        height: list?.clientHeight || window.innerHeight,
        width: window.innerWidth,
      }));
    };
    updateViewport();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updateViewport);
    if (listRef.current) observer?.observe(listRef.current);
    window.addEventListener('resize', updateViewport);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', updateViewport);
    };
  }, []);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = 0;
    setViewport(current => ({ ...current, scrollTop: 0 }));
  }, [activeFilter, catalog.hash, search]);

  useEffect(() => {
    const previousRowHeight = previousRowHeightRef.current;
    if (previousRowHeight === rowHeight) return;
    previousRowHeightRef.current = rowHeight;
    const list = listRef.current;
    const viewportHeight = list?.clientHeight || viewport.height;
    const maximumScrollTop = Math.max(0, Math.ceil(visible.length / columnCount) * rowHeight - viewportHeight);
    const scrollTop = rebasePokedexScrollTop(
      list?.scrollTop ?? viewport.scrollTop,
      previousRowHeight,
      rowHeight,
      maximumScrollTop,
    );
    if (list) list.scrollTop = scrollTop;
    setViewport(current => ({ ...current, scrollTop }));
  }, [columnCount, rowHeight, viewport.height, viewport.scrollTop, visible.length]);

  return <section class="screen pokedex-screen">
    <Header
      title="POKÉDEX"
      gameTime={state.gameTime}
      onTrainer={state.trainerCardUnlocked === true ? () => send('OPEN_TRAINER') : undefined}
      onParty={state.party?.some(member => member.occupied) ? () => send('OPEN_PARTY') : undefined}
      onSettings={() => send('SCREEN', { screen: 'SETTINGS' })}
      onMap={(catalog.worldMaps?.length ?? 0) > 0 ? onOpenMap : undefined}
    />
    <div class="browse-tools">
      <div class="filter-strip" aria-label="Pokédex filters">
        {filters.map(filter => <button key={filter} disabled={!filterEnabled[filter]} aria-pressed={activeFilter === filter} title={!filterEnabled[filter] ? `${filter} filter unavailable` : undefined} class={activeFilter === filter ? 'active' : ''} onClick={() => send('FILTER', { filter, areaId: null })}>{filter}</button>)}
      </div>
    </div>
    <div
      ref={listRef}
      class="species-list"
      data-scroll-region
      onScroll={event => setViewport(current => ({ ...current, scrollTop: event.currentTarget.scrollTop }))}
    >
      <div
        class="species-window"
        style={{
          '--species-row-height': `${rowHeight}px`,
          paddingTop: virtualWindow.paddingTop,
          paddingBottom: virtualWindow.paddingBottom,
        } as JSX.CSSProperties}
      >
      {mounted.map(species => { const speciesState = state.speciesState[species.id]; const knowledge = speciesIdentityKnowledge(policy, speciesState); const hidden = knowledge === 'unknown'; const metadataUnlocked = policy === 'DISCOVERED' || Boolean(speciesState?.caught); const types = metadataUnlocked ? uniqueTypeIds(species.typeIds).map(id => catalog.types.find(type => type.id === id)).filter((type): type is Catalog['types'][number] => type != null) : []; return <button key={species.id} class={`species-row ${hidden ? 'identity-hidden' : ''}`} disabled={hidden} aria-label={hidden ? 'Unidentified encounter' : undefined} onClick={() => send('OPEN_SPECIES', { speciesId: species.id })}>
        <PokedexAvatar speciesId={species.id} name={species.name} available={species.hasSprite} knowledge={knowledge} state={state.speciesState[species.id]} catalog={catalog} />
        <span class="species-row-identity"><span class="species-number">{hidden ? '#???' : `#${String(species.dex).padStart(3, '0')}`}</span><strong>{hidden ? maskIdentityName(species.name) : species.name}</strong></span>
        {types.length > 0 && <span class="species-row-types" aria-label="Types">{types.map(type => <TypeChip key={type.id} type={type} />)}</span>}
        <span class="species-row-meta">
          {activeFilter === 'AREA' && <EncounterWindowMark windows={encounterWindows(catalog, state.currentAreaIds ?? [], species.id)} />}
          <StatusMarks state={state.speciesState[species.id]} catalog={catalog} mode={policy} />
        </span>
      </button>; })}
      {visible.length === 0 && <div class="empty-state"><strong>NO POKÉMON FOUND</strong><p>Pokémon you discover will appear here.</p></div>}
      </div>
    </div>
    {activeFilter !== 'TEAM' && <div class="pokedex-search-dock">
      <div class="pokedex-search-row">
        <label class="search-box"><span>SEARCH</span><input aria-label="Search Pokémon" value={search} onInput={event => setSearch(event.currentTarget.value)} placeholder="NAME OR NUMBER" /></label>
        <output
          class="pokedex-result-count"
          aria-label={`${activeFilter} list: ${visible.length} Pokémon`}
        >
          {visible.length}
        </output>
      </div>
    </div>}
  </section>;
}

export function pokedexRowHeight(
  density: State['settings']['density'],
  fontScale = 1,
) {
  if (!Number.isFinite(fontScale) || fontScale <= 0) throw new RangeError('Pokédex font scale must be positive');
  const baseHeight = density === 'COMPACT'
    ? COMPACT_SPECIES_ROW_HEIGHT
    : DEFAULT_SPECIES_ROW_HEIGHT;
  return Math.max(baseHeight, Math.ceil(baseHeight * fontScale));
}

export function rebasePokedexScrollTop(
  scrollTop: number,
  previousRowHeight: number,
  nextRowHeight: number,
  maximumScrollTop: number,
) {
  if (previousRowHeight <= 0 || nextRowHeight <= 0) throw new RangeError('Pokédex row heights must be positive');
  const rebased = Math.max(0, scrollTop) / previousRowHeight * nextRowHeight;
  return Math.min(Math.max(0, maximumScrollTop), Math.round(rebased));
}

export function pokedexColumnCount(viewportWidth: number) {
  return viewportWidth <= 560 ? 1 : viewportWidth <= 820 ? 2 : 3;
}

export function pokedexVirtualWindow(
  itemCount: number,
  columnCount: number,
  scrollTop: number,
  viewportHeight: number,
  rowHeight = DEFAULT_SPECIES_ROW_HEIGHT,
) {
  if (rowHeight <= 0) throw new RangeError('Pokédex row height must be positive');
  const columns = Math.max(1, columnCount);
  const totalRows = Math.ceil(itemCount / columns);
  const maximumRows = Math.max(1, Math.floor(MAX_MOUNTED_SPECIES / columns));
  const visibleRows = Math.max(1, Math.ceil(Math.max(0, viewportHeight) / rowHeight));
  const windowRows = Math.min(maximumRows, visibleRows + OVERSCAN_ROWS * 2, totalRows);
  const firstVisibleRow = Math.floor(Math.max(0, scrollTop) / rowHeight);
  const startRow = Math.min(
    Math.max(0, firstVisibleRow - OVERSCAN_ROWS),
    Math.max(0, totalRows - windowRows),
  );
  const endRow = Math.min(totalRows, startRow + windowRows);
  return {
    startIndex: startRow * columns,
    endIndex: Math.min(itemCount, endRow * columns),
    paddingTop: startRow * rowHeight,
    paddingBottom: (totalRows - endRow) * rowHeight,
  };
}

export function encounterWindows(catalog: Catalog, areaIds: number[], speciesId: number): Set<EncounterWindow> {
  const windows = new Set<EncounterWindow>();
  for (const area of catalog.areas) {
    if (!areaIds.includes(area.id) || !area.speciesIds.includes(speciesId)) continue;
    for (const window of area.windows ?? ['ANY']) windows.add(window);
  }
  return windows;
}

function EncounterWindowMark({ windows }: { windows: Set<EncounterWindow> }) {
  if (windows.has('ANY')) return null;
  const day = windows.has('MORNING') || windows.has('DAY');
  const night = windows.has('NIGHT');
  if (day === night) return null;

  return day
    ? <svg data-testid="encounter-window-icon" class="encounter-window-icon day" role="img" aria-label="Day encounter" viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M19.1 4.9 17 7M7 17l-2.1 2.1" />
    </svg>
    : <svg data-testid="encounter-window-icon" class="encounter-window-icon night" role="img" aria-label="Night encounter" viewBox="0 0 24 24">
      <path d="M20 15.4A8.3 8.3 0 0 1 8.6 4a8.4 8.4 0 1 0 11.4 11.4Z" />
    </svg>;
}
