import { Header } from '../components';
import { msg } from '../i18n';
import type { NatureInfo, State } from '../models';
import { NATURE_STATS, natureFlavorLabel, natureStatLabel } from '../natureDetails';

export function NatureDetail({ nature, gameTime, onBack }: { nature: NatureInfo; gameTime: State['gameTime']; onBack: () => void }) {
  const neutral = nature.raisedStat == null && nature.loweredStat == null;
  return <section class="screen ability-detail-screen nature-detail-screen">
    <Header title={nature.name?.toUpperCase() ?? `#${nature.id}`} gameTime={gameTime} onBack={onBack} />
    <div class="ability-detail-content nature-detail-content" data-scroll-region>
      <div class="paper-panel nature-overview">
        <p class="eyebrow">{msg('statProfile')}</p>
        {neutral
          ? <div class="nature-neutral"><strong>{msg('noStatChanges')}</strong><span>{msg('allStatsNormal')}</span></div>
          : <div class="nature-shifts">
            <span class="nature-shift nature-raised"><small>{msg('raised')}</small><strong>{natureStatLabel(nature.raisedStat)} ×{formatMultiplier(nature.positivePercent)}</strong></span>
            <span class="nature-shift nature-lowered"><small>{msg('lowered')}</small><strong>{natureStatLabel(nature.loweredStat)} ×{formatMultiplier(nature.negativePercent)}</strong></span>
          </div>}
        <div class="nature-stat-row" aria-label={msg('natureStatMultipliers')}>{NATURE_STATS.map(stat => {
          const direction = stat === nature.raisedStat ? 'raised' : stat === nature.loweredStat ? 'lowered' : 'neutral';
          return <span class={`nature-stat nature-stat-${direction}`} key={stat}><small>{natureStatLabel(stat)}</small><strong>{nature.statMultipliers[stat]}%</strong></span>;
        })}</div>
      </div>
      <div class="paper-panel nature-temperament">
        <p class="eyebrow">{msg('temperament')}</p>
        {neutral
          ? <strong>{msg('noFlavorPreference')}</strong>
          : <div class="nature-flavors"><span><small>{msg('likes')}</small><strong>{msg('flavors', natureFlavorLabel(nature.likedFlavor) ?? '')}</strong></span><span><small>{msg('dislikes')}</small><strong>{msg('flavors', natureFlavorLabel(nature.dislikedFlavor) ?? '')}</strong></span></div>}
      </div>
    </div>
  </section>;
}

function formatMultiplier(percent: number): string {
  return Number.isInteger(percent / 100) ? String(percent / 100) : (percent / 100).toFixed(2).replace(/0$/, '');
}
