import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalog, State } from '../models';
import { Header, identitySpriteClass, maskIdentityName, PokedexAvatar, Segmented, speciesIdentityKnowledge, StatusMarks, TypeChip, uniqueTypeIds } from '../components';
import { gameplayCopy } from '../gameplayCopy';
import { catalogMediaUrl } from '../media';
import { AbilityMechanics } from './AbilityDetail';
import { PokemonAreaMap } from './PokemonAreaMap';

type DetailTab = 'ENTRY' | 'STATS' | 'MOVES' | 'AREA' | 'MORE';

export function PokedexDetail({
  catalog,
  state,
  send,
  tab,
  setTab,
  openMove,
  openAbility: _openAbility,
  openSpecimens,
}: {
  catalog: Catalog;
  state: State;
  send: (type: string, values?: Record<string, string | number | boolean | null>) => void;
  tab: DetailTab;
  setTab: (tab: DetailTab) => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openSpecimens?: (speciesId: number) => void;
}) {
  const species = catalog.species.find(item => item.id === state.selectedSpeciesId) ?? catalog.species[0];
  if (!species) return null;
  const status = state.speciesState[species.id];
  const identityKnowledge = speciesIdentityKnowledge(state.settings.knowledgeMode, status);
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
    <Header title="POKÉDEX" kicker={`#${String(species.dex).padStart(3, '0')}`} onBack={() => send('BACK')} />
    <div class="identity-card">
      <PokedexAvatar speciesId={species.id} name={species.name} available={species.hasSprite} large knowledge={identityKnowledge} state={status} catalog={catalog} />
      <div class="identity-copy"><h1>{species.name}</h1><div class="identity-line"><StatusMarks state={status} catalog={catalog} mode={state.settings.knowledgeMode} />{uniqueTypeIds(species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
      <Segmented values={['ENTRY', 'STATS', 'MOVES', 'AREA', 'MORE']} active={displayTab} disabledValues={unlocked ? [] : ['STATS', 'MORE']} onSelect={value => setTab(value as DetailTab)} label="Pokédex detail" />
    </div>
    <div class="detail-content" data-scroll-region>
      {!unlocked && !observedOnly && displayTab !== 'AREA' && <div class="paper-panel withheld"><strong>{gameplayCopy.dataUnavailable}</strong><p>{gameplayCopy.catchForFullData}</p></div>}
      {unlocked && displayTab === 'ENTRY' && <>
        <div class="paper-panel"><p class="eyebrow">POKÉDEX ENTRY</p><p class="entry-copy">{species.description || gameplayCopy.pokedexUnavailable}</p><div class="fact-grid"><span><small>HEIGHT</small><strong>{formatHeight(species.height, catalog.platform)}</strong></span><span><small>WEIGHT</small><strong>{formatWeight(species.weight, catalog.platform)}</strong></span></div></div>
        <HeightComparison
          species={species}
          platform={catalog.platform}
          knowledge={identityKnowledge}
          catalogHash={catalog.hash}
          trainerAvatarUrl={catalogMediaUrl(state.trainerAvatarUrl ?? state.trainer?.avatarUrl, catalog.hash)}
        />
      </>}
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
        {locations.length > 0 && <p class="range-note">Wild encounter levels: <strong>{wildLevelRange(locations.flatMap(item => item.slots))}</strong></p>}
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
      {displayTab === 'AREA' && <PokemonAreaMap catalog={catalog} state={state} speciesId={species.id} send={send} />}
      {unlocked && displayTab === 'MORE' && <div class="paper-panel more-sections">
        {(status?.specimenCount ?? 0) > 0 && <section class="specimen-entry-section">
          <div><p class="eyebrow">YOUR POKÉMON</p><strong>{status?.specimenCount} {status?.specimenCount === 1 ? 'specimen' : 'specimens'}</strong></div>
          <button type="button" onClick={() => openSpecimens?.(species.id)}>VIEW SPECIMENS</button>
        </section>}
        {species.abilities.length > 0 && <section><p class="eyebrow">ABILITIES</p><div class="inline-abilities">{species.abilities.map(ability => <article class="inline-ability" key={ability.id}>
          <header><strong>{ability.name}</strong><span>#{ability.id}</span></header>
          <p>{ability.description || gameplayCopy.abilityUnavailable}</p>
          {ability.mechanics.length > 0 && <AbilityMechanics mechanics={ability.mechanics} />}
        </article>)}</div></section>}
        {species.evolutions.length > 0 && <section><p class="eyebrow">EVOLUTIONS</p>{species.evolutions.map((evolution, index) => {
          const target = catalog.species.find(candidate => candidate.id === evolution.targetSpeciesId);
          const targetStatus = state.speciesState[evolution.targetSpeciesId];
          const knowledge = speciesIdentityKnowledge(state.settings.knowledgeMode, targetStatus);
          const resolvedTargetName = target?.name ?? evolution.targetName;
          const targetName = knowledge === 'unknown' ? maskIdentityName(resolvedTargetName) : resolvedTargetName;
          const sprite = <span class="evolution-sprite-frame">{target?.hasSprite
            ? <img
                src={catalogMediaUrl(`/api/sprites/species/${evolution.targetSpeciesId}.png`, catalog.hash)}
                alt={knowledge === 'unknown' ? 'Unidentified evolution sprite' : `${targetName} evolution sprite`}
                aria-hidden="true"
                class={identitySpriteClass(knowledge)}
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
        {species.abilities.length === 0 && species.evolutions.length === 0 && locations.length === 0 && (status?.specimenCount ?? 0) === 0 && <div class="empty-state">{gameplayCopy.noAdditionalData}</div>}
      </div>}
    </div>
  </section>;
}

export function baseStatSummary(stats: Record<string, number> | null): number {
  return Object.values(stats ?? {}).reduce((total, value) => total + value, 0);
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
  if (slots.length === 0) return '—';
  const minimum = Math.min(...slots.map(slot => slot.minimumLevel));
  const maximum = Math.max(...slots.map(slot => slot.maximumLevel));
  return minimum === maximum ? `Lv ${minimum}` : `Lv ${minimum}–${maximum}`;
}

export function formatHeight(value: number | null, platform: string): string {
  if (value == null) return '—';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} m`;
  if (platform === 'GBC') return `${value & 0xff}' ${(value >>> 8) & 0xff}"`;
  return String(value);
}

export function formatWeight(value: number | null, platform: string): string {
  if (value == null) return '—';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} kg`;
  if (platform === 'GBC') return `${(value / 10).toFixed(1)} lb`;
  return String(value);
}

export function heightInMeters(value: number | null, platform: string): number | null {
  if (value == null) return null;
  if (platform === 'GBA') return value / 10;
  if (platform === 'GBC' || platform === 'GB') {
    const feet = value & 0xff;
    const inches = (value >>> 8) & 0xff;
    return feet * .3048 + inches * .0254;
  }
  return null;
}

export function heightChartMaximum(pokemonMeters: number): number {
  return Math.max(1.7, pokemonMeters) / .8;
}

export function opaquePixelBounds(
  rgba: Uint8ClampedArray,
  width: number,
  height: number,
): { left: number; top: number; width: number; height: number } | null {
  if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width <= 0 || height <= 0
      || rgba.length !== width * height * 4) {
    throw new TypeError('RGBA dimensions must match the pixel buffer');
  }
  let left = width;
  let top = height;
  let right = -1;
  let bottom = -1;
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      if (rgba[(y * width + x) * 4 + 3] === 0) continue;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x);
      bottom = Math.max(bottom, y);
    }
  }
  return right < left || bottom < top
    ? null
    : { left, top, width: right - left + 1, height: bottom - top + 1 };
}

function AlphaTrimmedHeightSprite({ src, className = '' }: { src: string; className?: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [trimmed, setTrimmed] = useState(false);
  useEffect(() => setTrimmed(false), [src]);
  const trim = (image: HTMLImageElement) => {
    const canvas = canvasRef.current;
    if (!canvas || image.naturalWidth <= 0 || image.naturalHeight <= 0) return;
    try {
      const staging = document.createElement('canvas');
      staging.width = image.naturalWidth;
      staging.height = image.naturalHeight;
      const stagingContext = staging.getContext('2d', { willReadFrequently: true });
      if (!stagingContext) return;
      stagingContext.imageSmoothingEnabled = false;
      stagingContext.drawImage(image, 0, 0);
      const bounds = opaquePixelBounds(
        stagingContext.getImageData(0, 0, staging.width, staging.height).data,
        staging.width,
        staging.height,
      );
      if (!bounds) return;
      canvas.width = bounds.width;
      canvas.height = bounds.height;
      const context = canvas.getContext('2d');
      if (!context) return;
      context.imageSmoothingEnabled = false;
      context.drawImage(
        image,
        bounds.left,
        bounds.top,
        bounds.width,
        bounds.height,
        0,
        0,
        bounds.width,
        bounds.height,
      );
      setTrimmed(true);
    } catch {
      setTrimmed(false);
    }
  };
  return <>
    <img
      class={`${className} height-sprite-fallback`.trim()}
      src={src}
      alt=""
      hidden={trimmed}
      onLoad={event => trim(event.currentTarget)}
    />
    <canvas
      ref={canvasRef}
      class={`${className} height-sprite-canvas`.trim()}
      data-alpha-trimmed={trimmed ? 'true' : 'false'}
      hidden={!trimmed}
    />
  </>;
}

function HeightComparison({ species, platform, knowledge, catalogHash, trainerAvatarUrl }: {
  species: Catalog['species'][number];
  platform: string;
  knowledge: ReturnType<typeof speciesIdentityKnowledge>;
  catalogHash: string;
  trainerAvatarUrl: string | null;
}) {
  const pokemonMeters = heightInMeters(species.height, platform);
  if (pokemonMeters == null || pokemonMeters <= 0) return null;
  const maximum = heightChartMaximum(pokemonMeters);
  const ticks = Array.from({ length: Math.floor(maximum * 2) + 1 }, (_, index) => Math.floor(maximum * 2) / 2 - index / 2);
  const style = {
    '--person-height': `${1.7 / maximum * 100}%`,
    '--pokemon-height': `${pokemonMeters / maximum * 100}%`,
  } as Record<string, string>;
  const metric = `${pokemonMeters.toFixed(1)} m`;
  return <section
    class="paper-panel height-comparison"
    role="img"
    aria-label={`Height comparison for ${species.name}: ${metric} beside a 1.7 m person`}
    style={style}
  >
    <div class="height-comparison-heading"><p class="eyebrow">HEIGHT COMPARISON</p><strong>{metric}</strong></div>
    <div class="height-ruler" aria-hidden="true">
      {ticks.map(tick => <span
        class={`height-ruler-line ${Number.isInteger(tick) ? 'major' : ''}`}
        key={tick}
        style={{ bottom: `${tick / maximum * 100}%` }}
      ><i>{tick.toFixed(tick % 1 === 0 ? 0 : 1)} m</i></span>)}
      <span class="height-figure height-person">
        {trainerAvatarUrl
          ? <AlphaTrimmedHeightSprite src={trainerAvatarUrl} />
          : <svg viewBox="0 0 64 170"><circle cx="32" cy="17" r="15" /><path d="M22 35h20l7 52-9 2-3-32v50l8 60H33l-5-48-5 48H11l8-60V57l-3 32-9-2 7-52h8Z" /></svg>}
      </span>
      <span class="height-figure height-pokemon">
        {species.hasSprite
          ? <AlphaTrimmedHeightSprite className={identitySpriteClass(knowledge)} src={catalogMediaUrl(`/api/sprites/species/${species.id}.png`, catalogHash)} />
          : <svg class="height-pokemon-silhouette" viewBox="0 0 120 100"><path d="M18 65c0-22 13-39 34-43l7-17 10 18c23 6 36 24 33 45l15 12-20 3c-8 10-20 15-36 15-25 0-43-12-43-33Zm12-31L10 20l24 3m61 15 18-13-8 23" /></svg>}
      </span>
    </div>
  </section>;
}
