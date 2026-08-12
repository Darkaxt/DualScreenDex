import { useMemo, useState } from 'preact/hooks';
import type { Catalog, EncounterWindow, State } from '../models';
import { Header, Sprite, StatusMarks } from '../components';

export function PokedexBrowse({ catalog, state, send }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const [search, setSearch] = useState('');
  const policy = state.settings.knowledgeMode;
  const capabilities = state.saveRam?.capabilities ?? {};
  const available = (name: string) => capabilities[name] === 'AVAILABLE' || capabilities[name] === 'PARTIAL';
  const filterEnabled = {
    ALL: true,
    CAUGHT: available('CAUGHT'),
    SEEN: available('SEEN'),
    TEAM: available('PARTY') && available('SPECIES'),
    AREA: (state.currentAreaIds?.length ?? 0) > 0,
  } as const;
  const visible = useMemo(() => catalog.species.filter(species => {
    const status = state.speciesState[species.id];
    if (policy === 'ORGANIC' && !status?.seen && !status?.caught) return false;
    if (policy === 'HIDDEN' && !status?.caught) return false;
    if (search && !species.name.toLowerCase().includes(search.toLowerCase()) && !String(species.dex).includes(search)) return false;
    if (state.filter === 'CAUGHT' && !status?.caught) return false;
    if (state.filter === 'SEEN' && !status?.seen) return false;
    if (state.filter === 'TEAM' && !status?.team) return false;
    if (state.filter === 'AREA') {
      const inCurrentArea = catalog.areas.some(item => state.currentAreaIds?.includes(item.id) && item.speciesIds.includes(species.id));
      if (!inCurrentArea) return false;
    }
    return true;
  }), [catalog, policy, search, state.filter, state.currentAreaIds, state.speciesState]);

  return <section class="screen pokedex-screen">
    <Header title="POKÉDEX" kicker={`${catalog.family.replaceAll('_', ' ')} · ${policy}`} onSettings={() => send('SCREEN', { screen: 'SETTINGS' })} />
    <div class="browse-tools">
      <label class="search-box"><span>SEARCH</span><input value={search} onInput={event => setSearch(event.currentTarget.value)} placeholder="NAME OR NUMBER" /></label>
      <div class="filter-strip" aria-label="Pokédex filters">
        {(['ALL', 'CAUGHT', 'SEEN', 'TEAM', 'AREA'] as const).map(filter => <button key={filter} disabled={!filterEnabled[filter]} title={!filterEnabled[filter] ? `${filter} filter unavailable` : undefined} class={state.filter === filter ? 'active' : ''} onClick={() => send('FILTER', { filter, areaId: null })}>{filter}</button>)}
      </div>
    </div>
    <div class="species-list" data-scroll-region>
      {visible.map(species => <button key={species.id} class="species-row" onClick={() => send('OPEN_SPECIES', { speciesId: species.id })}>
        <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} />
        <span class="species-number">#{String(species.dex).padStart(3, '0')}</span>
        <strong>{species.name}</strong>
        <span class="species-row-meta">
          {state.filter === 'AREA' && <EncounterWindowMark windows={encounterWindows(catalog, state.currentAreaIds ?? [], species.id)} />}
          <StatusMarks state={state.speciesState[species.id]} catalog={catalog} />
        </span>
      </button>)}
      {visible.length === 0 && <div class="empty-state"><strong>NO POKÉMON FOUND</strong><p>Pokémon you discover will appear here.</p></div>}
    </div>
  </section>;
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
