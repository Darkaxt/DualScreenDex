import type { Catalog, State } from '../models';
import { Header, TypeChip } from '../components';
import { gameplayCopy } from '../gameplayCopy';
import { msg } from '../i18n';

export function MoveDetail({ catalog, state, moveId, onBack }: { catalog: Catalog; state: State; moveId: number; onBack: () => void }) {
  const move = catalog.moves.find(item => item.id === moveId);
  if (!move) return null;
  const recruited = catalog.species.filter(species =>
    state.speciesState[species.id]?.caught && speciesKnowsMove(species, state.activeRulesetId, moveId),
  );
  return <section class="screen move-detail-screen">
    <Header title={move.name} gameTime={state.gameTime} onBack={onBack} />
    <div class="move-detail-content" data-scroll-region>
      <div class="move-hero">
        <TypeChip type={catalog.types.find(type => type.id === move.typeId)} />
        <strong>{moveCategoryLabel(move.category)}</strong>
      </div>
      <div class="paper-panel">
        <p class="eyebrow">{msg('battleData')}</p>
        <div class="move-detail-grid">
          <span><small>{msg('power')}</small><strong>{formatMoveMetric(move.power)}</strong></span>
          <span><small>{msg('precision')}</small><strong>{formatMoveMetric(move.accuracy, '%')}</strong></span>
          <span><small>PP</small><strong>{formatMoveMetric(move.pp)}</strong></span>
          <span><small>{msg('priority')}</small><strong>{move.priority == null ? '—' : move.priority > 0 ? `+${move.priority}` : String(move.priority)}</strong></span>
        </div>
      </div>
      <div class="paper-panel"><p class="eyebrow">{msg('effect')}</p><p class="entry-copy">{move.description || gameplayCopy.moveEffectUnavailable}</p></div>
      {recruited.length > 0 && <div class="paper-panel"><p class="eyebrow">{msg('knownByCaptures')}</p><div class="known-species">{recruited.map(species => <span key={species.id}>{species.name}</span>)}</div></div>}
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

export function moveCategoryLabel(category: string | null): string {
  if (category === 'PHYSICAL') return msg('categoryPhysical');
  if (category === 'SPECIAL') return msg('categorySpecial');
  if (category === 'STATUS') return msg('categoryStatus');
  return '—';
}

export function formatMoveMetric(value: number | null, suffix = ''): string {
  return value == null || value === 0 ? '—' : `${value}${suffix}`;
}
