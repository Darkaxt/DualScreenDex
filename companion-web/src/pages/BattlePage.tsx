import type { Catalog, Move, State } from '../models';
import { Header, Segmented, Sprite, StatusMarks, TypeChip, uniqueTypeIds } from '../components';

export function BattlePage({ catalog, state, send }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  const battle = state.battle;
  if (!battle) return null;
  const opponent = battle.opponents[battle.targetIndex];
  const species = catalog.species.find(item => item.id === opponent.speciesId)!;
  const status = state.speciesState[species.id];
  const selectedMove = catalog.moves.find(move => move.id === battle.selectedMoveId) ?? catalog.moves.find(move => (move.power ?? 0) > 0);
  const tabs = ['ENTRY', state.settings.attackEnabled ? 'ATTACK' : null, state.settings.rarityEnabled ? 'RARITY' : null, state.settings.movesEnabled ? 'MOVES' : null].filter(Boolean) as string[];
  const hidden = state.settings.knowledgeMode === 'HIDDEN';
  return <section class="screen battle-screen">
    <Header title="BATTLE" kicker={`${catalog.family.replaceAll('_', ' ')} · ${state.settings.knowledgeMode}`} onSettings={() => send('SCREEN', { screen: 'SETTINGS' })} />
    {battle.opponents.length > 1 && <div class="target-switch">{battle.opponents.map((target, index) => {
      const targetSpecies = catalog.species.find(item => item.id === target.speciesId);
      return <button key={`${target.speciesId}-${index}`} class={index === battle.targetIndex ? 'active' : ''} onClick={() => send('TARGET', { index })}>{targetSpecies?.name}<span>LV {target.level}</span></button>;
    })}</div>}
    <div class="battle-identity">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} large />
      <div><small>TARGET · LV {opponent.level}</small><h1>{species.name}</h1><div class="identity-line"><StatusMarks state={status} catalog={catalog} />{uniqueTypeIds(species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    {!hidden && <Segmented values={tabs} active={state.battleTab} onSelect={tab => send('TAB', { tab })} label="Battle information" />}
    <div class="battle-content" data-scroll-region>
      {(hidden || state.battleTab === 'ENTRY') && <Entry catalog={catalog} species={species} unlocked={state.settings.knowledgeMode === 'DISCOVERED' || status?.caught} />}
      {!hidden && state.battleTab === 'ATTACK' && <Attack catalog={catalog} move={selectedMove} state={state} send={send} />}
      {!hidden && state.battleTab === 'RARITY' && <Rarity label={opponent.rarity} />}
      {!hidden && state.battleTab === 'MOVES' && <Moves catalog={catalog} moves={opponent.moves} />}
    </div>
  </section>;
}

function Entry({ catalog, species, unlocked }: { catalog: Catalog; species: Catalog['species'][number]; unlocked?: boolean }) {
  return <div class="paper-panel"><p class="eyebrow">TARGET ENTRY</p>{unlocked ? <p class="entry-copy">{species.description || 'No Pokédex description was resolved from this ROM.'}</p> : <div class="withheld"><strong>OBSERVE OR RECRUIT</strong><p>The target is identified. Organic mode keeps its full Pokédex entry hidden until capture.</p></div>}</div>;
}

function Attack({ catalog, move, state, send }: { catalog: Catalog; move?: Move; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void }) {
  if (!move) return <div class="empty-state">No attack data is available.</div>;
  const effect = state.battle?.effectivenessKnown ? state.battle.effectiveness!.replaceAll('_', ' ') : 'UNKNOWN';
  return <div class="attack-card">
    <div class="attack-heading"><div><small>SELECTED ATTACK</small><h2>{move.name}</h2></div><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></div>
    <div class="move-metadata"><span><small>POWER</small><strong>{move.power || '—'}</strong></span><span><small>PRECISION</small><strong>{move.accuracy == null ? '—' : `${move.accuracy}%`}</strong></span><span><small>PP</small><strong>{move.pp ?? '—'}</strong></span><span><small>CLASS</small><strong>{move.category ?? '—'}</strong></span></div>
    <label class="move-select"><span>ATTACK REFERENCE</span><select value={move.id} onChange={event => send('MOVE', { moveId: Number(event.currentTarget.value) })}>{catalog.moves.filter(item => (item.power ?? 0) > 0).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
    <div class={`effect-result effect-${effect.toLowerCase().replaceAll(' ', '-')}`}><small>EFFECTIVENESS</small><strong>{effect}</strong></div>
    {!state.battle?.effectivenessKnown && state.settings.knowledgeMode === 'ORGANIC' && <button class="primary-button" onClick={() => send('RESOLVE_ATTACK')}>RESOLVE ATTACK</button>}
  </div>;
}

function Rarity({ label }: { label: string }) {
  const [prefix, tier] = label.split(' ');
  return <div class="rarity-card"><small>RECRUITMENT IMPRESSION</small><strong>{tier}</strong><span>{prefix} FOR YOUR CURRENT PARTY</span><p>Innate quality uses the complete IV or DV vector. Exact hidden values, EVs, and encounter rate are not exposed.</p></div>;
}

function Moves({ catalog, moves }: { catalog: Catalog; moves: { moveId: number; encounters: number; lastSeen: number }[] }) {
  if (moves.length === 0) return <div class="empty-state"><strong>NO MOVES OBSERVED</strong><p>History begins after this species uses an attack.</p></div>;
  return <div class="observed-list">{moves.map(item => {
    const move = catalog.moves.find(candidate => candidate.id === item.moveId);
    return <div key={item.moveId}><TypeChip type={catalog.types.find(type => type.id === move?.typeId)} /><strong>{move?.name ?? `MOVE ${item.moveId}`}</strong><span>{item.encounters} encounter{item.encounters === 1 ? '' : 's'}</span><small>{move?.power ? `${move.power} power · ${move.accuracy}% precision` : move?.category}</small></div>;
  })}</div>;
}
