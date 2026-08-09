import type { Catalog, State } from '../models';
import { Header } from '../components';

export function AbilityDetail({ catalog, state, abilityId, onBack }: { catalog: Catalog; state: State; abilityId: number; onBack: () => void }) {
  const ability = catalog.species.flatMap(species => species.abilities).find(item => item.id === abilityId);
  if (!ability) return null;
  const recruited = catalog.species.filter(species =>
    state.speciesState[species.id]?.caught && species.abilities.some(item => item.id === abilityId)
  );
  return <section class="screen ability-detail-screen">
    <Header title={ability.name} kicker="ABILITY DETAIL" onBack={onBack} />
    <div class="ability-detail-content" data-scroll-region>
      <div class="ability-hero"><strong>{ability.name}</strong><span>ROM ABILITY #{ability.id}</span></div>
      <div class="paper-panel"><p class="eyebrow">EFFECT</p><p class="entry-copy">{ability.description || 'No compatible ability description was resolved from this ROM.'}</p></div>
      {recruited.length > 0 && <div class="paper-panel"><p class="eyebrow">KNOWN ON YOUR CAPTURES</p><div class="known-species">{recruited.map(species => <span key={species.id}>{species.name}</span>)}</div></div>}
    </div>
  </section>;
}
