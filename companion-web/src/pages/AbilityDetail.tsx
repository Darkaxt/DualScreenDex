import type { Catalog, State } from '../models';
import { Header } from '../components';
import { gameplayCopy } from '../gameplayCopy';

export function AbilityDetail({ catalog, state, abilityId, onBack }: { catalog: Catalog; state: State; abilityId: number; onBack: () => void }) {
  const ability = catalog.species.flatMap(species => species.abilities).find(item => item.id === abilityId);
  if (!ability) return null;
  const recruited = catalog.species.filter(species =>
    state.speciesState[species.id]?.caught && species.abilities.some(item => item.id === abilityId)
  );
  return <section class="screen ability-detail-screen">
    <Header title={ability.name} onBack={onBack} />
    <div class="ability-detail-content" data-scroll-region>
      <div class="paper-panel"><p class="eyebrow">EFFECT</p><p class="entry-copy">{ability.description || gameplayCopy.abilityUnavailable}</p></div>
      {ability.mechanics.length > 0 && <div class="paper-panel"><p class="eyebrow">BATTLE EFFECTS</p><div class="ability-mechanics">{ability.mechanics.map(mechanic => {
        const conditions = mechanic.conditions ?? [];
        return <div class="ability-mechanic" key={`${mechanic.kind}-${mechanic.label}-${conditions.map(condition => condition.kind).join('-')}`}><span>{conditions.length > 0 ? `${mechanic.label} · ${conditions.map(condition => condition.label).join(', ')}` : mechanic.label}</span><strong>{mechanic.value}</strong></div>;
      })}</div></div>}
      {recruited.length > 0 && <div class="paper-panel"><p class="eyebrow">KNOWN ON YOUR CAPTURES</p><div class="known-species">{recruited.map(species => <span key={species.id}>{species.name}</span>)}</div></div>}
    </div>
  </section>;
}
