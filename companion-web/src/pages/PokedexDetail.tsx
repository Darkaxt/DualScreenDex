import type { Catalog, State } from '../models';
import { Header, Segmented, Sprite, StatusMarks, TypeChip, uniqueTypeIds } from '../components';
import { gameplayCopy } from '../gameplayCopy';

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
  const displayTab = !unlocked && (tab === 'STATS' || tab === 'MORE') ? 'ENTRY' : tab;
  const observedOnly = !unlocked && displayTab === 'MOVES';
  const activeRuleset = state.activeRulesetId == null
    ? null
    : catalog.rulesets.find(item => item.id === state.activeRulesetId) ?? null;
  const moves = activeRuleset == null ? [] : species.normalizedLearnsets[activeRuleset.id] ?? [];
  const statRanges = Object.entries(species.stats ?? {}).map(([name, value]) => ({ name, value, ...projectedStatRange(value, name, catalog.platform) }));
  const statScale = Math.max(1, ...statRanges.map(item => item.high));
  const locations = catalog.areas.flatMap(area => {
    const slots = area.slots.filter(slot => slot.speciesId === species.id);
    return slots.length ? [{ area, slots }] : [];
  });
  return <section class="screen detail-screen">
    <Header title={species.name} kicker={`#${String(species.dex).padStart(3, '0')}`} onBack={() => send('BACK')} />
    <div class="identity-card">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} large />
      <div class="identity-copy"><h1>{species.name}</h1><div class="identity-line"><StatusMarks state={status} catalog={catalog} />{uniqueTypeIds(species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    <Segmented values={['ENTRY', 'STATS', 'MOVES', 'MORE']} active={displayTab} disabledValues={unlocked ? [] : ['STATS', 'MORE']} onSelect={value => setTab(value as DetailTab)} label="Pokédex detail" />
    <div class="detail-content" data-scroll-region>
      {!unlocked && !observedOnly && <div class="paper-panel withheld"><strong>{gameplayCopy.dataUnavailable}</strong><p>{gameplayCopy.catchForFullData}</p></div>}
      {unlocked && displayTab === 'ENTRY' && <div class="paper-panel"><p class="eyebrow">POKÉDEX ENTRY</p><p class="entry-copy">{species.description || gameplayCopy.pokedexUnavailable}</p><div class="fact-grid"><span><small>HEIGHT</small><strong>{formatHeight(species.height, catalog.platform)}</strong></span><span><small>WEIGHT</small><strong>{formatWeight(species.weight, catalog.platform)}</strong></span></div></div>}
      {unlocked && displayTab === 'STATS' && <div class="paper-panel">
        <div class="section-heading"><div><p class="eyebrow">BASE STATS + INNATE RANGE</p><p>Lv 50 projection · no EV/stat experience · neutral nature where applicable.</p></div><strong>BST {baseStatSummary(species.stats)}</strong></div>
        {status?.innateTier && <p class="range-note">Preferred recruit: <strong>{status.innateTier}</strong>{status.preferredLevel ? ` · Lv ${status.preferredLevel}` : ''}</p>}
        <div class="stat-legend" aria-label="Stat projection legend"><span class="legend-low">LOW</span><span class="legend-typical">TYPICAL</span><span class="legend-high">HIGH</span></div>
        <div class="stat-list">{statRanges.map(item => <div key={item.name}>
          <span class="stat-label">{item.name}<small>BASE {item.value}</small></span>
          <i class="stat-impact" aria-label={`${item.name}: ${item.low} to ${item.high} at level 50`}>
            <b class="stat-typical" style={{ width: `${item.typical / statScale * 100}%` }} />
            <b class="stat-low" style={{ left: `${item.low / statScale * 100}%`, width: `${(item.typical - item.low) / statScale * 100}%` }} />
            <b class="stat-high" style={{ left: `${item.typical / statScale * 100}%`, width: `${(item.high - item.typical) / statScale * 100}%` }} />
          </i>
          <strong class="stat-range">{item.low}–{item.high}</strong>
        </div>)}</div>
        {locations.length > 0 && <p class="range-note">Wild encounters in this ROM: <strong>{wildLevelRange(locations.flatMap(item => item.slots))}</strong></p>}
      </div>}
      {unlocked && displayTab === 'MOVES' && <div class="paper-panel move-sections">
        <div class="section-heading"><div><p class="eyebrow">LEVEL-UP MOVES</p><p>{activeRuleset == null ? 'Move list not selected' : `${activeRuleset.label} list`}</p></div></div>
        {activeRuleset == null && catalog.rulesets.length > 1
          ? <div class="empty-state"><strong>{gameplayCopy.moveDataUnavailable}</strong><p>{gameplayCopy.chooseMoveList}</p></div>
          : <div class="move-table">{moves.map(item => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={item.moveId} onClick={() => openMove(item.moveId)}><span>{item.label}</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div>}
        {species.moveAcquisitions.length > 0 && <><p class="eyebrow acquisition-heading">OTHER METHODS</p><div class="move-table">{species.moveAcquisitions.map((item, index) => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={`${item.method}-${item.moveId}-${index}`} onClick={() => openMove(item.moveId)}><span>{item.method}{item.sourceId ? ` ${item.sourceId}` : ''}</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div></>}
      </div>}
      {observedOnly && <div class="paper-panel move-sections">
        <div class="section-heading"><div><p class="eyebrow">OBSERVED MOVES</p><p>Only attacks this species has used against you.</p></div></div>
        {observedMoves.length > 0 ? <div class="move-table">{observedMoves.map(item => {
          const move = catalog.moves.find(candidate => candidate.id === item.moveId);
          return move && <button key={item.moveId} onClick={() => openMove(item.moveId)}><span>FREQUENCY · {item.frequency}×</span><strong>{move.name}</strong><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></button>;
        })}</div> : <div class="empty-state"><strong>{gameplayCopy.noMovesRecorded}</strong><p>{gameplayCopy.movesWillAppear}</p></div>}
      </div>}
      {unlocked && displayTab === 'MORE' && <div class="paper-panel more-sections">
        {species.abilities.length > 0 && <section><p class="eyebrow">ABILITIES</p>{species.abilities.map(ability => <button class="data-row data-link" key={ability.id} onClick={() => openAbility(ability.id)}><strong>{ability.name}</strong><span>#{ability.id}</span></button>)}</section>}
        {species.evolutions.length > 0 && <section><p class="eyebrow">EVOLUTIONS</p>{species.evolutions.map((evolution, index) => {
          const target = catalog.species.find(candidate => candidate.id === evolution.targetSpeciesId);
          const targetStatus = state.speciesState[evolution.targetSpeciesId];
          const knowledge = state.settings.knowledgeMode !== 'ORGANIC' || targetStatus?.caught
            ? 'captured'
            : targetStatus?.seen ? 'seen' : 'unknown';
          const resolvedTargetName = target?.name ?? evolution.targetName;
          const targetName = knowledge === 'unknown' ? maskEvolutionName(resolvedTargetName) : resolvedTargetName;
          const sprite = <span class="evolution-sprite-frame">{target?.hasSprite
            ? <img
                src={`/api/sprites/species/${evolution.targetSpeciesId}.png`}
                alt={knowledge === 'unknown' ? 'Unidentified evolution sprite' : `${targetName} evolution sprite`}
                aria-hidden="true"
                class={knowledge === 'unknown' ? 'evolution-silhouette' : knowledge === 'seen' ? 'evolution-seen' : ''}
              />
            : <span class="evolution-sprite-missing" aria-label="Evolution sprite unavailable" />}</span>;
          const content = <>{sprite}<strong>{targetName}</strong><span>{evolution.condition}</span></>;
          return target && knowledge !== 'unknown'
            ? <button class="evolution-row evolution-link" key={`${evolution.targetSpeciesId}-${index}`} onClick={() => {
              setTab('ENTRY');
              send('OPEN_SPECIES', { speciesId: evolution.targetSpeciesId });
            }}>{content}</button>
            : <div class="evolution-row" key={`${evolution.targetSpeciesId}-${index}`}>{content}</div>;
        })}</section>}
        {locations.length > 0 && <section><p class="eyebrow">LOCATIONS</p>{locations.map(({ area, slots }) => <div class="data-row location-row" key={area.id}><strong>{area.name}</strong><span>{wildLevelRange(slots)}{slots.some(slot => slot.weight != null) ? ` · ${Math.max(...slots.map(slot => slot.weight ?? 0))}%` : ''}</span></div>)}</section>}
        {species.abilities.length === 0 && species.evolutions.length === 0 && locations.length === 0 && <div class="empty-state">{gameplayCopy.noAdditionalData}</div>}
      </div>}
    </div>
  </section>;
}

export function baseStatSummary(stats: Record<string, number> | null): number {
  return Object.values(stats ?? {}).reduce((total, value) => total + value, 0);
}

export function maskEvolutionName(name: string): string {
  return Array.from(name).map(character => /\s/u.test(character) ? character : '?').join('');
}

export function projectedStatRange(base: number, name: string, platform: string, level = 50): { low: number; typical: number; high: number } {
  const innateValues = platform === 'GBA' ? [0, 15, 31] : [0, 7, 15];
  const hp = name === 'HP';
  const calculate = (innate: number) => {
    const weightedBase = platform === 'GBA' ? 2 * base + innate : 2 * (base + innate);
    return Math.floor(weightedBase * level / 100) + (hp ? level + 10 : 5);
  };
  const [low, typical, high] = innateValues.map(calculate);
  return { low, typical, high };
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
