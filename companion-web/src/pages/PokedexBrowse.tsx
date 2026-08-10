import { useMemo, useState } from 'preact/hooks';
import type { Catalog, State } from '../models';
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
    AREA: available('CURRENT_AREA') && (state.currentAreaIds?.length ?? 0) > 0,
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
        {(['ALL', 'CAUGHT', 'SEEN', 'TEAM', 'AREA'] as const).map(filter => <button key={filter} disabled={!filterEnabled[filter]} title={!filterEnabled[filter] ? `${filter} is unavailable from the current SaveRAM` : undefined} class={state.filter === filter ? 'active' : ''} onClick={() => send('FILTER', { filter, areaId: null })}>{filter}</button>)}
      </div>
    </div>
    <div class="species-list" data-scroll-region>
      {visible.map(species => <button key={species.id} class="species-row" onClick={() => send('OPEN_SPECIES', { speciesId: species.id })}>
        <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} />
        <span class="species-number">#{String(species.dex).padStart(3, '0')}</span>
        <strong>{species.name}</strong>
        <StatusMarks state={state.speciesState[species.id]} catalog={catalog} />
      </button>)}
      {visible.length === 0 && <div class="empty-state"><strong>NO DISCOVERIES YET</strong><p>Change the information policy or select a different filter.</p></div>}
    </div>
  </section>;
}
