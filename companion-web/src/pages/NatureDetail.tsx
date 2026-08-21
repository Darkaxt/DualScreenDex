import { Header } from '../components';
import { NATURE_STATS, natureDetailFor } from '../natureDetails';

export function NatureDetail({ natureName, onBack }: { natureName: string; onBack: () => void }) {
  const nature = natureDetailFor(natureName);
  if (!nature) return null;
  return <section class="screen ability-detail-screen nature-detail-screen">
    <Header title={nature.name.toUpperCase()} onBack={onBack} />
    <div class="ability-detail-content nature-detail-content" data-scroll-region>
      <div class="paper-panel nature-overview">
        <p class="eyebrow">STAT PROFILE</p>
        {nature.neutral
          ? <div class="nature-neutral"><strong>No stat changes</strong><span>All five affected stats keep their normal value.</span></div>
          : <div class="nature-shifts">
            <span class="nature-shift nature-raised"><small>RAISED</small><strong>{nature.raisedStat} ×1.1</strong></span>
            <span class="nature-shift nature-lowered"><small>LOWERED</small><strong>{nature.loweredStat} ×0.9</strong></span>
          </div>}
        <div class="nature-stat-row" aria-label="Nature stat multipliers">{NATURE_STATS.map(stat => {
          const direction = stat === nature.raisedStat ? 'raised' : stat === nature.loweredStat ? 'lowered' : 'neutral';
          return <span class={`nature-stat nature-stat-${direction}`} key={stat}><small>{stat}</small><strong>{direction === 'raised' ? '110%' : direction === 'lowered' ? '90%' : '100%'}</strong></span>;
        })}</div>
      </div>
      <div class="paper-panel nature-temperament">
        <p class="eyebrow">TEMPERAMENT</p>
        {nature.neutral
          ? <strong>No flavor preference</strong>
          : <div class="nature-flavors"><span><small>LIKES</small><strong>{nature.likedFlavor} flavors</strong></span><span><small>DISLIKES</small><strong>{nature.dislikedFlavor} flavors</strong></span></div>}
      </div>
    </div>
  </section>;
}
