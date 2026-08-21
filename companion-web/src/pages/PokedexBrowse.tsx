import { useMemo, useState } from 'preact/hooks';
import type { Catalog, EncounterWindow, State } from '../models';
import { Header, maskIdentityName, PokedexAvatar, speciesIdentityKnowledge, StatusMarks, TypeChip, uniqueTypeIds } from '../components';

export function PokedexBrowse({ catalog, state, send, onOpenMap }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void; onOpenMap?: () => void }) {
  const [search, setSearch] = useState('');
  const policy = state.settings.knowledgeMode;
  const activeFilter = policy === 'ORGANIC' && state.filter === 'SEEN' ? 'ALL' : state.filter;
  const filters = policy === 'ORGANIC'
    ? (['ALL', 'CAUGHT', 'TEAM', 'AREA'] as const)
    : (['ALL', 'CAUGHT', 'SEEN', 'TEAM', 'AREA'] as const);
  const capabilities = state.saveRam?.capabilities ?? {};
  const available = (name: string) => capabilities[name] === 'AVAILABLE' || capabilities[name] === 'PARTIAL';
  const filterEnabled = {
    ALL: true,
    CAUGHT: available('CAUGHT'),
    SEEN: available('SEEN'),
    TEAM: available('PARTY') && available('SPECIES'),
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
    if (activeFilter === 'SEEN' && !status?.seen) return false;
    if (activeFilter === 'TEAM' && !status?.team) return false;
    if (activeFilter === 'AREA' && !areaSpeciesIds.has(species.id)) return false;
    return true;
  }).sort((left, right) => {
    if (activeFilter !== 'AREA') return 0;
    const leftKnown = Boolean(state.speciesState[left.id]?.seen || state.speciesState[left.id]?.caught);
    const rightKnown = Boolean(state.speciesState[right.id]?.seen || state.speciesState[right.id]?.caught);
    return Number(rightKnown) - Number(leftKnown);
  }), [activeFilter, areaSpeciesIds, catalog.species, policy, search, state.speciesState]);

  return <section class="screen pokedex-screen">
    <Header
      title="POKÉDEX"
      gameTime={state.gameTime}
      onTrainer={state.trainer ? () => send('OPEN_TRAINER') : undefined}
      onParty={state.party?.some(member => member.occupied) ? () => send('OPEN_PARTY') : undefined}
      onSettings={() => send('SCREEN', { screen: 'SETTINGS' })}
      onMap={(catalog.worldMaps?.length ?? 0) > 0 ? onOpenMap : undefined}
    />
    <div class="browse-tools">
      <label class="search-box"><span>SEARCH</span><input value={search} onInput={event => setSearch(event.currentTarget.value)} placeholder="NAME OR NUMBER" /></label>
      <div class="filter-strip" aria-label="Pokédex filters">
        {filters.map(filter => <button key={filter} disabled={!filterEnabled[filter]} title={!filterEnabled[filter] ? `${filter} filter unavailable` : undefined} class={activeFilter === filter ? 'active' : ''} onClick={() => send('FILTER', { filter, areaId: null })}>{filter}</button>)}
      </div>
    </div>
    <div class="species-list" data-scroll-region>
      {visible.map(species => { const speciesState = state.speciesState[species.id]; const knowledge = speciesIdentityKnowledge(policy, speciesState); const hidden = knowledge === 'unknown'; const metadataUnlocked = policy === 'DISCOVERED' || Boolean(speciesState?.caught); const types = metadataUnlocked ? uniqueTypeIds(species.typeIds).map(id => catalog.types.find(type => type.id === id)).filter((type): type is Catalog['types'][number] => type != null) : []; return <button key={species.id} class={`species-row ${hidden ? 'identity-hidden' : ''}`} disabled={hidden} aria-label={hidden ? 'Unidentified encounter' : undefined} onClick={() => send('OPEN_SPECIES', { speciesId: species.id })}>
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
