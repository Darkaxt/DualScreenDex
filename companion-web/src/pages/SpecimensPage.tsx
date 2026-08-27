import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalog, SpecimenCollectionView } from '../models';
import { Header } from '../components';
import { specimens as loadSpecimens } from '../gateway';
import { RarityStars } from './BattlePage';
import { OwnedIndividualDetail, OwnedIndividualSprite } from './OwnedIndividualDetail';

export function SpecimensPage({ catalog, speciesId, stateVersion, detailKey, onBack, onOpenDetail, onCloseDetail, openMove, openAbility, openNature, openSpecies, initialScrollTop = 0, onScrollTopChange, load = loadSpecimens }: {
  catalog: Catalog;
  speciesId: number;
  stateVersion: number;
  detailKey: string | null;
  onBack: () => void;
  onOpenDetail: (key: string) => void;
  onCloseDetail: () => void;
  openMove: (moveId: number) => void;
  openAbility: (abilityId: number) => void;
  openNature: (natureId: number) => void;
  openSpecies: (speciesId: number) => void;
  initialScrollTop?: number;
  onScrollTopChange?: (scrollTop: number) => void;
  load?: (speciesId: number) => Promise<SpecimenCollectionView>;
}) {
  const [collection, setCollection] = useState<SpecimenCollectionView | null>(null);
  const [failed, setFailed] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let current = true;
    setFailed(false);
    void load(speciesId).then(value => {
      if (!current) return;
      setCollection(value);
      requestAnimationFrame(() => {
        if (contentRef.current) contentRef.current.scrollTop = initialScrollTop;
      });
    }).catch(() => {
      if (!current) return;
      setFailed(true);
      setCollection(null);
    });
    return () => { current = false; };
  }, [catalog.hash, speciesId, stateVersion, load]);

  const active = detailKey == null ? null : collection?.specimens.find(specimen => specimen.key === detailKey) ?? null;
  useEffect(() => {
    if (collection && detailKey != null && !active) onCloseDetail();
  }, [collection, detailKey, active, onCloseDetail]);

  return <section class="screen specimens-screen">
    <Header title="SPECIMENS" kicker={collection?.speciesName ?? catalog.species.find(species => species.id === speciesId)?.name} onBack={onBack} />
    <div ref={contentRef} class="specimens-content" data-scroll-region onScroll={event => onScrollTopChange?.(event.currentTarget.scrollTop)}>
      {!collection && !failed && <div class="specimens-loading" role="status"><span />Preparing your Pokémon…</div>}
      {failed && <div class="empty-state"><strong>SPECIMENS UNAVAILABLE</strong><p>Your owned Pokémon could not be refreshed.</p></div>}
      {collection?.specimens.length === 0 && <div class="empty-state"><strong>NO SPECIMENS AVAILABLE</strong><p>No individual Pokémon record is available for this entry.</p></div>}
      {collection && collection.specimens.length > 0 && <div class="specimens-grid" aria-label={`${collection.speciesName} specimens`}>
        {collection.specimens.map(specimen => <button
          type="button"
          key={specimen.key}
          class="specimen-card"
          aria-label={`Open ${specimen.nickname || specimen.speciesName} details`}
          onClick={() => onOpenDetail(specimen.key)}
        >
          <OwnedIndividualSprite individual={specimen} />
          <span class="specimen-card-copy">
            <strong>{specimen.nickname || specimen.speciesName}</strong>
            {specimen.nickname && specimen.nickname !== specimen.speciesName && <small>{specimen.speciesName}</small>}
            <span>{specimen.location.label}</span>
          </span>
          <span class="specimen-card-meta">
            {specimen.level != null && <b>Lv {specimen.level}</b>}
            {specimen.rarity && <RarityStars rarity={specimen.rarity} />}
          </span>
        </button>)}
      </div>}
      {active && <div class="party-detail-layer">
        <div class="party-detail-backdrop" onClick={onCloseDetail} />
        <div class="party-detail-window" role="dialog" aria-modal="true" aria-label={`${active.nickname || active.speciesName} details`}>
          <button type="button" class="party-detail-close" aria-label={`Close ${active.nickname || active.speciesName} details`} onClick={onCloseDetail} autoFocus>×</button>
          <OwnedIndividualDetail individual={active} catalog={catalog} locationLabel={active.location.label} openMove={openMove} openAbility={openAbility} openNature={openNature} openSpecies={openSpecies} />
        </div>
      </div>}
    </div>
  </section>;
}
