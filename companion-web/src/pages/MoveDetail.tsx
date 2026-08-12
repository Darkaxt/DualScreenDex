import type { Catalog, State } from '../models';
import { Header, TypeChip } from '../components';
import { gameplayCopy } from '../gameplayCopy';

export function MoveDetail({ catalog, state, moveId, onBack }: { catalog: Catalog; state: State; moveId: number; onBack: () => void }) {
  const move = catalog.moves.find(item => item.id === moveId);
  if (!move) return null;
  const recruited = catalog.species.filter(species =>
    state.speciesState[species.id]?.caught && speciesKnowsMove(species, state.activeRulesetId, moveId),
  );
  return <section class="screen move-detail-screen">
    <Header title={move.name} kicker="MOVE DETAIL" onBack={onBack} />
    <div class="move-detail-content" data-scroll-region>
      <div class="move-hero">
        <TypeChip type={catalog.types.find(type => type.id === move.typeId)} />
        <strong>{move.category ?? '—'}</strong>
      </div>
      <div class="paper-panel">
        <p class="eyebrow">BATTLE DATA</p>
        <div class="move-detail-grid">
          <span><small>POWER</small><strong>{formatMoveMetric(move.power)}</strong></span>
          <span><small>PRECISION</small><strong>{formatMoveMetric(move.accuracy, '%')}</strong></span>
          <span><small>PP</small><strong>{formatMoveMetric(move.pp)}</strong></span>
          <span><small>PRIORITY</small><strong>{move.priority == null ? 'N/F' : move.priority > 0 ? `+${move.priority}` : String(move.priority)}</strong></span>
        </div>
      </div>
      <div class="paper-panel"><p class="eyebrow">EFFECT</p><p class="entry-copy">{move.description || gameplayCopy.moveEffectUnavailable}</p></div>
      {recruited.length > 0 && <div class="paper-panel"><p class="eyebrow">KNOWN BY YOUR CAPTURES</p><div class="known-species">{recruited.map(species => <span key={species.id}>{species.name}</span>)}</div></div>}
    </div>
  </section>;
}

export function speciesKnowsMove(
  species: {
    normalizedLearnsets: Record<string, { moveId: number }[]>;
    moveAcquisitions: { moveId: number }[];
  },
  activeRulesetId: string | null,
  moveId: number,
): boolean {
  const learnsByLevel = activeRulesetId != null &&
    (species.normalizedLearnsets[activeRulesetId] ?? []).some(entry => entry.moveId === moveId);
  return learnsByLevel || species.moveAcquisitions.some(entry => entry.moveId === moveId);
}

export function formatMoveMetric(value: number | null, suffix = ''): string {
  return value == null || value === 0 ? '—' : `${value}${suffix}`;
}
