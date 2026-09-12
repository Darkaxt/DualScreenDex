import type { Catalog, State } from '../models';
import { Header } from '../components';
import { gameplayCopy } from '../gameplayCopy';
import { msg } from '../i18n';
import { renderPresentationMessage } from '../presentationMessages';

export function AbilityDetail({ catalog, state, abilityId, onBack }: { catalog: Catalog; state: State; abilityId: number; onBack: () => void }) {
  const ability = catalog.species.flatMap(species => species.abilities).find(item => item.id === abilityId);
  if (!ability) return null;
  const recruited = catalog.species.filter(species =>
    state.speciesState[species.id]?.caught && species.abilities.some(item => item.id === abilityId)
  );
  const mechanics = userFacingAbilityMechanics(ability.mechanics);
  return <section class="screen ability-detail-screen">
    <Header title={ability.name} gameTime={state.gameTime} onBack={onBack} />
    <div class="ability-detail-content" data-scroll-region>
      <div class="paper-panel"><p class="eyebrow">{msg('effect')}</p><p class="entry-copy">{ability.description || gameplayCopy.abilityUnavailable}</p></div>
      {mechanics.length > 0 && <div class="paper-panel"><p class="eyebrow">{msg('battleEffects')}</p><AbilityMechanics mechanics={mechanics} /></div>}
      {recruited.length > 0 && <div class="paper-panel"><p class="eyebrow">{msg('knownOnCaptures')}</p><div class="known-species">{recruited.map(species => <span key={species.id}>{species.name}</span>)}</div></div>}
    </div>
  </section>;
}

export function AbilityMechanics({ mechanics }: { mechanics: Catalog['species'][number]['abilities'][number]['mechanics'] }) {
  return <div class="ability-mechanics">{userFacingAbilityMechanics(mechanics).map(mechanic => {
    const conditions = mechanic.conditions ?? [];
    const label = renderPresentationMessage(mechanic.label);
    return <div class="ability-mechanic" key={`${mechanic.kind}-${mechanic.label.code}-${conditions.map(condition => condition.kind).join('-')}`}><span>{conditions.length > 0 ? `${label} · ${conditions.map(condition => renderPresentationMessage(condition.label)).join(', ')}` : label}</span><strong>{renderPresentationMessage(mechanic.value)}</strong></div>;
  })}</div>;
}

export function userFacingAbilityMechanics(mechanics: Catalog['species'][number]['abilities'][number]['mechanics']) {
  return mechanics;
}
