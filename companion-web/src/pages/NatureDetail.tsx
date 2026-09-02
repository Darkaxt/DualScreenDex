import { Header } from '../components';
import type { NatureInfo, State } from '../models';
import { NATURE_STATS, natureFlavorLabel, natureStatLabel } from '../natureDetails';

export function NatureDetail({ nature, gameTime, onBack }: { nature: NatureInfo; gameTime: State['gameTime']; onBack: () => void }) {
  const neutral = nature.raisedStat == null && nature.loweredStat == null;
  return <section class="screen ability-detail-screen nature-detail-screen">
    <Header title={nature.name?.toUpperCase() ?? `#${nature.id}`} gameTime={gameTime} onBack={onBack} />
    <div class="ability-detail-content nature-detail-content" data-scroll-region>
      <div class="paper-panel nature-overview">
        <p class="eyebrow">STAT PROFILE</p>
        {neutral
          ? <div class="nature-neutral"><strong>No stat changes</strong><span>All five affected stats keep their normal value.</span></div>
          : <div class="nature-shifts">
            <span class="nature-shift nature-raised"><small>RAISED</small><strong>{natureStatLabel(nature.raisedStat)} ×{formatMultiplier(nature.positivePercent)}</strong></span>
            <span class="nature-shift nature-lowered"><small>LOWERED</small><strong>{natureStatLabel(nature.loweredStat)} ×{formatMultiplier(nature.negativePercent)}</strong></span>
          </div>}
        <div class="nature-stat-row" aria-label="Nature stat multipliers">{NATURE_STATS.map(stat => {
          const direction = stat === nature.raisedStat ? 'raised' : stat === nature.loweredStat ? 'lowered' : 'neutral';
          return <span class={`nature-stat nature-stat-${direction}`} key={stat}><small>{natureStatLabel(stat)}</small><strong>{nature.statMultipliers[stat]}%</strong></span>;
        })}</div>
      </div>
      <div class="paper-panel nature-temperament">
        <p class="eyebrow">TEMPERAMENT</p>
        {neutral
          ? <strong>No flavor preference</strong>
          : <div class="nature-flavors"><span><small>LIKES</small><strong>{natureFlavorLabel(nature.likedFlavor)} flavors</strong></span><span><small>DISLIKES</small><strong>{natureFlavorLabel(nature.dislikedFlavor)} flavors</strong></span></div>}
      </div>
    </div>
  </section>;
}

function formatMultiplier(percent: number): string {
  return Number.isInteger(percent / 100) ? String(percent / 100) : (percent / 100).toFixed(2).replace(/0$/, '');
}
