import type { Catalog, Move, Rarity as RarityModel, State } from '../models';
import { DexIcon, Header, Segmented, Sprite, StatusMarks, TypeChip, uniqueTypeIds } from '../components';
import { gameplayCopy } from '../gameplayCopy';

export function BattlePage({ catalog, state, send, openMove, openSpecies }: { catalog: Catalog; state: State; send: (type: string, values?: Record<string, string | number | boolean | null>) => void; openMove: (moveId: number) => void; openSpecies: (speciesId: number) => void }) {
  const battle = state.battle;
  if (!battle) return null;
  const opponent = battle.opponents[battle.targetIndex];
  const species = catalog.species.find(item => item.id === opponent.speciesId)!;
  const status = state.speciesState[species.id];
  const selectedMove = catalog.moves.find(move => move.id === battle.selectedMoveId);
  const tabs = ['ENTRY', state.settings.attackEnabled ? 'ATTACK' : null, state.settings.rarityEnabled ? 'RARITY' : null, state.settings.movesEnabled ? 'MOVES' : null].filter(Boolean) as string[];
  const hidden = state.settings.knowledgeMode === 'HIDDEN';
  const manualTargets = battle.opponents.length > 1 && battle.targetMode === 'MANUAL_TARGET_FALLBACK';
  const title = battle.encounterKind === 'WILD'
    ? 'WILD ENCOUNTER'
    : battle.encounterKind === 'TRAINER' ? 'TRAINER BATTLE' : 'ENCOUNTER';
  return <section class={`screen battle-screen ${manualTargets ? 'battle-double' : 'battle-single'}`}>
    <Header title={title} onSettings={() => send('SCREEN', { screen: 'SETTINGS' })} />
    {manualTargets && <div class="target-switch">{battle.opponents.map((target, index) => {
      const targetSpecies = catalog.species.find(item => item.id === target.speciesId);
      return <button key={`${target.speciesId}-${index}`} class={index === battle.targetIndex ? 'active' : ''} onClick={() => send('TARGET', { index })}>{targetSpecies?.name}<span>LV {target.level}</span></button>;
    })}</div>}
    <div class="battle-identity">
      <Sprite speciesId={species.id} name={species.name} available={species.hasSprite} large />
      <div class="battle-identity-copy"><small>TARGET · LV {opponent.level}{battle.opponents.length > 1 && battle.targetMode === 'AUTOMATIC' && <span class="automatic-target">AUTOMATIC TARGET</span>}</small><div class="battle-name-row"><h1>{species.name}</h1><RarityStars rarity={opponent.rarity} /><button class="battle-dex-link" aria-label={`Open ${species.name} in Pokédex`} onClick={() => openSpecies(species.id)}><DexIcon /></button></div><div class="identity-line"><StatusMarks state={status} catalog={catalog} mode={state.settings.knowledgeMode} />{uniqueTypeIds(opponent.typeIds?.length ? opponent.typeIds : species.typeIds).map(id => <TypeChip key={id} type={catalog.types.find(type => type.id === id)} />)}</div></div>
    </div>
    {!hidden && <Segmented values={tabs} active={state.battleTab} onSelect={tab => send('TAB', { tab })} label="Battle information" />}
    <div class="battle-content" data-scroll-region>
      {(hidden || state.battleTab === 'ENTRY') && <Entry catalog={catalog} species={species} unlocked={state.settings.knowledgeMode === 'DISCOVERED' || status?.caught} />}
      {!hidden && state.battleTab === 'ATTACK' && <Attack catalog={catalog} move={selectedMove} state={state} openMove={openMove} />}
      {!hidden && state.battleTab === 'RARITY' && <Rarity rarity={opponent.rarity} />}
      {!hidden && state.battleTab === 'MOVES' && <Moves catalog={catalog} moves={opponent.moves} showFrequency={!status?.caught} openMove={openMove} />}
    </div>
  </section>;
}

function Entry({ catalog, species, unlocked }: { catalog: Catalog; species: Catalog['species'][number]; unlocked?: boolean }) {
  return <div class="paper-panel"><p class="eyebrow">TARGET ENTRY</p>{unlocked ? <p class="entry-copy">{species.description || gameplayCopy.pokedexUnavailable}</p> : <div class="withheld"><strong>{gameplayCopy.dataUnavailable}</strong><p>{gameplayCopy.catchForEntry}</p></div>}</div>;
}

function Attack({ catalog, move, state, openMove }: { catalog: Catalog; move?: Move; state: State; openMove: (moveId: number) => void }) {
  if (!move) return <div class="empty-state"><strong>{gameplayCopy.noMoveSelected}</strong><p>{gameplayCopy.selectMove}</p></div>;
  const effect = state.battle?.effectivenessKnown ? state.battle.effectiveness!.replaceAll('_', ' ') : '—';
  const effectClass = effect === '—' ? 'effect-unavailable' : `effect-${effect.toLowerCase().replaceAll(' ', '-')}`;
  return <div class="attack-card">
    <div class="attack-heading"><div><small>SELECTED ATTACK</small><button class="move-link" onClick={() => openMove(move.id)}>{move.name}</button></div><TypeChip type={catalog.types.find(type => type.id === move.typeId)} /></div>
    <div class="move-metadata"><span><small>POWER</small><strong>{move.power ? move.power : '—'}</strong></span><span><small>PRECISION</small><strong>{move.accuracy ? `${move.accuracy}%` : '—'}</strong></span><span><small>PP</small><strong>{move.pp || '—'}</strong></span><span><small>CLASS</small><strong>{move.category ?? '—'}</strong></span></div>
    <div class={`effect-result ${effectClass}`}><small>EFFECTIVENESS</small><strong>{effect}</strong></div>
  </div>;
}

export function RarityStars({ rarity }: { rarity: RarityModel }) {
  if (rarity.stars == null || rarity.innateTier == null) return null;
  const rating = formatStars(rarity.stars);
  const title = [rarity.relativeTier, rarity.innateTier].filter(Boolean).join(' ');
  return <div class="rarity-stars" role="img" aria-label={`${rating} of 5 stars; ${title}`}>
    {[0, 1, 2, 3, 4].map(index => {
      const fill = Math.max(0, Math.min(1, rarity.stars! - index));
      return <span class="rarity-star" aria-hidden="true" key={index}>
        <span class="rarity-star-outline">☆</span>
        <span class="rarity-star-fill" style={{ width: `${fill * 100}%` }}>★</span>
      </span>;
    })}
  </div>;
}

function Rarity({ rarity }: { rarity: RarityModel }) {
  const title = rarity.innateTier == null
    ? 'NO RECRUITMENT READING'
    : [rarity.relativeTier, rarity.innateTier].filter(Boolean).join(' ');
  const band = rarityBand(rarity.stars);
  return <div class="rarity-card" data-rarity-band={band}>
    {rarity.stars != null && <RarityStars rarity={rarity} />}
    <small>RECRUITMENT IMPRESSION</small>
    <strong>{title}</strong>
    {rarity.stars != null && <p>{rarityAssessment(rarity.stars)}</p>}
  </div>;
}

function rarityBand(stars: number | null): 'unavailable' | 'low' | 'medium' | 'high' | 'exceptional' {
  if (stars == null) return 'unavailable';
  if (stars <= 2) return 'low';
  if (stars <= 3) return 'medium';
  if (stars <= 4) return 'high';
  return 'exceptional';
}

export function rarityAssessment(stars: number): string {
  if (stars <= 1) return 'Probably not worth catching. It seems quite weak and may only serve as a stepping stone.';
  if (stars <= 2) return 'A modest find. It could help for a while, but you may soon outgrow it.';
  if (stars <= 3) return 'A solid catch. It should be a dependable addition to your team.';
  if (stars <= 4) return 'An impressive catch. It looks strong enough to become a lasting team member.';
  return 'An exceptional catch. This one has the makings of a standout partner.';
}

function formatStars(stars: number): string {
  return Number.isInteger(stars) ? String(stars) : stars.toFixed(1);
}

function Moves({ catalog, moves, showFrequency, openMove }: { catalog: Catalog; moves: { moveId: number; frequency: number }[]; showFrequency: boolean; openMove: (moveId: number) => void }) {
  if (moves.length === 0) return <div class="empty-state"><strong>{gameplayCopy.noMovesRecorded}</strong><p>{gameplayCopy.movesWillAppear}</p></div>;
  return <div class="observed-list">{moves.map(item => {
    const move = catalog.moves.find(candidate => candidate.id === item.moveId);
    return <button key={item.moveId} onClick={() => openMove(item.moveId)}><TypeChip type={catalog.types.find(type => type.id === move?.typeId)} /><strong>{move?.name ?? `MOVE ${item.moveId}`}</strong>{showFrequency && <span>FREQUENCY · {item.frequency}×</span>}<small>{move?.power ? `${move.power} power · ${move.accuracy}% precision` : move?.category}</small></button>;
  })}</div>;
}
