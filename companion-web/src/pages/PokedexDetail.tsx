import type { Catalog, State } from '../models';
import { Header, Segmented, Sprite, StatusMarks, TypeChip, uniqueTypeIds } from '../components';

type DetailTab = 'ENTRY' | 'STATS' | 'MOVES' | 'MORE';

export function PokedexDetail({
  catalog,
  state,
  send,
  tab,
  setTab,
  openMove,
  openAbility
}: {
  catalog: Catalog;
  state: State;
  send: (type: string, values?: Record<string, string | number | boolean | null>) => void;
  tab: DetailTab;
  setTab: (tab: DetailTab) => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
}) {
  const species = catalog.species.find(item => item.id === state.selectedSpeciesId) ?? catalog.species[0];
  if (!species) return null;
  const status = state.speciesState[species.id];
  const unlocked = state.settings.knowledgeMode !== 'ORGANIC' || status?.caught;
  const observedMoves = state.observedMoves[species.id] ?? [];
  const observedOnly = !unlocked && tab === 'MOVES';
  const rulesetId = state.activeRulesetId ?? catalog.rulesets.find(item => item.primary)?.id ?? 'default';
  const moves = species.normalizedLearnsets[rulesetId] ?? [];
  const locations = catalog.areas.flatMap(area => {
    const slots = area.slots.filter(slot => slot.speciesId === species.id);
    return slots.length ? [{ area, slots }] : [];
  });
  return <section class="screen detail-screen">
    <Header title={species.name} kicker={`#${String(species.dex).padStart(3, '0')}`} onBack={() => send('BACK')} />
    <div class="identity-card">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} large />
      <div class="identity-copy"><small>ROM INDEX {species.id}</small><h1>{species.name}</h1><div class="identity-line"><StatusMarks state={status} catalog={catalog} />{uniqueTypeIds(species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    <Segmented values={['ENTRY', 'STATS', 'MOVES', 'MORE']} active={tab} onSelect={value => setTab(value as DetailTab)} label="Pokédex detail" />
    <div class="detail-content" data-scroll-region>
      {!unlocked && !observedOnly && <div class="paper-panel withheld"><strong>KNOWLEDGE WITHHELD</strong><p>Organic mode unlocks the complete ROM entry after this species is recruited. Moves witnessed in battle remain available in the Moves tab.</p></div>}
      {unlocked && tab === 'ENTRY' && <div class="paper-panel"><p class="eyebrow">POKÉDEX ENTRY</p><p class="entry-copy">{species.description || 'No Pokédex description was resolved from this ROM.'}</p><div class="fact-grid"><span><small>HEIGHT</small><strong>{formatHeight(species.height, catalog.platform)}</strong></span><span><small>WEIGHT</small><strong>{formatWeight(species.weight, catalog.platform)}</strong></span></div></div>}
      {unlocked && tab === 'STATS' && <div class="paper-panel">
        <div class="section-heading"><div><p class="eyebrow">BASE STATS</p><p>Species values before level, IVs, EVs, nature, and battle modifiers.</p></div><strong>BST {baseStatSummary(species.stats)}</strong></div>
        <div class="stat-list">{Object.entries(species.stats ?? {}).map(([name, value]) => <div key={name}><span>{name}</span><i><b style={{ width: `${Math.min(100, value / 2.55)}%` }} /></i><strong>{value}</strong></div>)}</div>
        {locations.length > 0 && <p class="range-note">Wild encounters in this ROM: <strong>{wildLevelRange(locations.flatMap(item => item.slots))}</strong></p>}
      </div>}
      {unlocked && tab === 'MOVES' && <div class="paper-panel move-sections">
        <div class="section-heading"><div><p class="eyebrow">LEVEL-UP MOVES</p><p>{catalog.rulesets.find(item => item.id === rulesetId)?.label ?? 'Default'} ruleset</p></div></div>
        <div class="move-table">{moves.map(item => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={item.moveId} onClick={() => openMove(item.moveId)}><span>{item.label}</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div>
        {species.moveAcquisitions.length > 0 && <><p class="eyebrow acquisition-heading">OTHER METHODS</p><div class="move-table">{species.moveAcquisitions.map((item, index) => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={`${item.method}-${item.moveId}-${index}`} onClick={() => openMove(item.moveId)}><span>{item.method}{item.sourceId ? ` ${item.sourceId}` : ''}</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div></>}
      </div>}
      {observedOnly && <div class="paper-panel move-sections">
        <div class="section-heading"><div><p class="eyebrow">OBSERVED MOVES</p><p>Only attacks this species has used against you.</p></div></div>
        {observedMoves.length > 0 ? <div class="move-table">{observedMoves.map(item => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={item.moveId} onClick={() => openMove(item.moveId)}><span>OBSERVED · {item.encounters}×</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div> : <div class="empty-state"><strong>NO MOVES OBSERVED</strong><p>This list grows when the species uses an attack against you.</p></div>}
      </div>}
      {unlocked && tab === 'MORE' && <div class="paper-panel more-sections">
        {species.abilities.length > 0 && <section><p class="eyebrow">ABILITIES</p>{species.abilities.map(ability => <button class="data-row data-link" key={ability.id} onClick={() => openAbility(ability.id)}><strong>{ability.name}</strong><span>#{ability.id}</span></button>)}</section>}
        {species.evolutions.length > 0 && <section><p class="eyebrow">EVOLUTIONS</p>{species.evolutions.map((evolution, index) => {
          const targetAvailable = catalog.species.some(candidate => candidate.id === evolution.targetSpeciesId);
          return targetAvailable
            ? <button class="data-row data-link" key={`${evolution.targetSpeciesId}-${index}`} onClick={() => {
              setTab('ENTRY');
              send('OPEN_SPECIES', { speciesId: evolution.targetSpeciesId });
            }}><strong>{evolution.targetName}</strong><span>{evolution.condition}</span></button>
            : <div class="data-row" key={`${evolution.targetSpeciesId}-${index}`}><strong>{evolution.targetName}</strong><span>{evolution.condition}</span></div>;
        })}</section>}
        {locations.length > 0 && <section><p class="eyebrow">LOCATIONS</p>{locations.map(({ area, slots }) => <div class="data-row location-row" key={area.id}><strong>{area.name}</strong><span>{wildLevelRange(slots)}{slots.some(slot => slot.weight != null) ? ` · ${Math.max(...slots.map(slot => slot.weight ?? 0))}%` : ''}</span></div>)}</section>}
        {species.abilities.length === 0 && species.evolutions.length === 0 && locations.length === 0 && <div class="empty-state">No additional compatible ROM records were found for this species.</div>}
      </div>}
    </div>
  </section>;
}

export function baseStatSummary(stats: Record<string, number> | null): number {
  return Object.values(stats ?? {}).reduce((total, value) => total + value, 0);
}

export function wildLevelRange(slots: { minimumLevel: number; maximumLevel: number }[]): string {
  if (slots.length === 0) return 'N/F';
  const minimum = Math.min(...slots.map(slot => slot.minimumLevel));
  const maximum = Math.max(...slots.map(slot => slot.maximumLevel));
  return minimum === maximum ? `Lv ${minimum}` : `Lv ${minimum}–${maximum}`;
}

export function formatHeight(value: number | null, platform: string): string {
  if (value == null) return 'N/F';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} m`;
  if (platform === 'GBC') return `${value & 0xff}' ${(value >>> 8) & 0xff}"`;
  return String(value);
}

export function formatWeight(value: number | null, platform: string): string {
  if (value == null) return 'N/F';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} kg`;
  if (platform === 'GBC') return `${(value / 10).toFixed(1)} lb`;
  return String(value);
}
