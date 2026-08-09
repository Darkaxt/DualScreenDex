import { useState } from 'preact/hooks';
import type { Catalog, State } from '../models';
import { Header, Segmented, Sprite, StatusMarks, TypeChip, uniqueTypeIds } from '../components';

export function PokedexDetail({ catalog, state, send }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const [tab, setTab] = useState('ENTRY');
  const species = catalog.species.find(item => item.id === state.selectedSpeciesId) ?? catalog.species[0];
  if (!species) return null;
  const status = state.speciesState[species.id];
  const unlocked = state.settings.knowledgeMode !== 'ORGANIC' || status?.caught;
  const moves = species.learnset.map(item => ({ ...item, move: catalog.moves.find(move => move.id === item.moveId) })).filter(item => item.move);
  return <section class="screen detail-screen">
    <Header title={species.name} kicker={`#${String(species.dex).padStart(3, '0')}`} onBack={() => send('BACK')} />
    <div class="identity-card">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} large />
      <div class="identity-copy"><small>ROM INDEX {species.id}</small><h1>{species.name}</h1><div class="identity-line"><StatusMarks state={status} catalog={catalog} />{uniqueTypeIds(species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    <Segmented values={['ENTRY', 'STATS', 'MOVES', 'MORE']} active={tab} onSelect={setTab} label="Pokédex detail" />
    <div class="detail-content paper-panel" data-scroll-region>
      {!unlocked && <div class="withheld"><strong>KNOWLEDGE WITHHELD</strong><p>Organic mode unlocks the complete ROM entry after this species is recruited.</p></div>}
      {unlocked && tab === 'ENTRY' && <><p class="eyebrow">POKÉDEX ENTRY</p><p class="entry-copy">{species.description || 'No Pokédex description was resolved from this ROM.'}</p><div class="fact-grid"><span><small>HEIGHT</small><strong>{formatHeight(species.height, catalog.platform)}</strong></span><span><small>WEIGHT</small><strong>{formatWeight(species.weight, catalog.platform)}</strong></span></div></>}
      {unlocked && tab === 'STATS' && <div class="stat-list">{Object.entries(species.stats ?? {}).map(([name, value]) => <div key={name}><span>{name}</span><i><b style={{ width: `${Math.min(100, value / 2.55)}%` }} /></i><strong>{value}</strong></div>)}</div>}
      {unlocked && tab === 'MOVES' && <div class="move-table">{moves.map((item, index) => <div key={`${item.level}-${item.move!.id}-${index}`}><span>LV {item.level}</span><strong>{item.move!.name}</strong><TypeChip type={catalog.types.find(type => type.id === item.move!.typeId)} /></div>)}</div>}
      {unlocked && tab === 'MORE' && <><p class="eyebrow">ROM DATA</p><p>This species has {moves.length} decoded level-up entries. Evolution and ability drill-downs are catalog-ready and remain a later presentation pass.</p></>}
    </div>
  </section>;
}

export function formatHeight(value: number | null, platform: string): string {
  if (value == null) return 'N/F';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} m`;
  if (platform === 'GBC') return `${value & 0xff}' ${(value >>> 8) & 0xff}\"`;
  return String(value);
}

export function formatWeight(value: number | null, platform: string): string {
  if (value == null) return 'N/F';
  if (platform === 'GBA') return `${(value / 10).toFixed(1)} kg`;
  if (platform === 'GBC') return `${(value / 10).toFixed(1)} lb`;
  return String(value);
}
