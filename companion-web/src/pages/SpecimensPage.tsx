import { useEffect, useRef, useState } from 'preact/hooks';
import { boundedRequest } from '../boundedRequest';
import type { Catalog, PresentationMessage, SpecimenCollectionView, State } from '../models';
import { Dialog, Header } from '../components';
import { formatUiNumber, msg } from '../i18n';
import { specimens as loadSpecimens } from '../gateway';
import { renderPresentationMessage } from '../presentationMessages';
import { RarityStars } from './BattlePage';
import { OwnedIndividualDetail, OwnedIndividualSprite } from './OwnedIndividualDetail';

const SPECIMEN_REQUEST_TIMEOUT_MILLIS = 8_000;

export function SpecimensPage({ catalog, speciesId, stateVersion, gameTime, detailKey, onBack, onOpenDetail, onCloseDetail, openMove, openAbility, openNature, openSpecies, initialScrollTop = 0, onScrollTopChange, load = loadSpecimens, requestTimeoutMillis = SPECIMEN_REQUEST_TIMEOUT_MILLIS }: {
  catalog: Catalog;
  speciesId: number;
  stateVersion: number;
  gameTime: State['gameTime'];
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
  requestTimeoutMillis?: number;
}) {
  const [collection, setCollection] = useState<SpecimenCollectionView | null>(null);
  const [error, setError] = useState<'timeout' | 'failed' | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const contentRef = useRef<HTMLDivElement>(null);
  const collectionRef = useRef<SpecimenCollectionView | null>(null);
  const collectionIdentityRef = useRef('');
  const triggerRefs = useRef(new Map<string, HTMLButtonElement>());
  const lastTriggerRef = useRef<HTMLButtonElement | null>(null);
  collectionRef.current = collection;

  useEffect(() => {
    let current = true;
    let animationFrame: number | null = null;
    const identity = `${catalog.hash}:${speciesId}`;
    const preserveCollection = collectionIdentityRef.current === identity && collectionRef.current != null;
    setError(null);
    if (!preserveCollection) setCollection(null);
    void boundedRequest(
      load(speciesId),
      requestTimeoutMillis,
      'SPECIMEN_REQUEST_TIMEOUT',
    ).then(value => {
      if (!current) return;
      collectionIdentityRef.current = identity;
      setCollection(value);
      if (!preserveCollection) {
        animationFrame = requestAnimationFrame(() => {
          if (contentRef.current) contentRef.current.scrollTop = initialScrollTop;
        });
      }
    }).catch(failure => {
      if (!current) return;
      setError(failure instanceof Error && failure.message === 'SPECIMEN_REQUEST_TIMEOUT' ? 'timeout' : 'failed');
      if (!preserveCollection) setCollection(null);
    });
    return () => {
      current = false;
      if (animationFrame != null) cancelAnimationFrame(animationFrame);
    };
  }, [catalog.hash, speciesId, stateVersion, load, reloadKey, requestTimeoutMillis]);

  const active = detailKey == null ? null : collection?.specimens.find(specimen => specimen.key === detailKey) ?? null;
  useEffect(() => {
    if (collection && detailKey != null && !active) onCloseDetail();
  }, [collection, detailKey, active, onCloseDetail]);

  const openDetail = (key: string) => {
    lastTriggerRef.current = triggerRefs.current.get(key) ?? null;
    onOpenDetail(key);
  };

  return <section class="screen specimens-screen">
    <Header title={msg('specimens')} kicker={collection?.speciesName ?? catalog.species.find(species => species.id === speciesId)?.name} gameTime={gameTime} onBack={onBack} />
    <div ref={contentRef} class="specimens-content" data-scroll-region onScroll={event => onScrollTopChange?.(event.currentTarget.scrollTop)}>
      {!collection && !error && <div class="specimens-loading" role="status"><span />{msg('preparingPokemon')}</div>}
      {error && <div class="empty-state specimens-error" role="alert">
        <strong>{msg('specimensUnavailable')}</strong>
        <p>{error === 'timeout' ? msg('specimenRequestTimeout') : msg('specimenRequestFailed')} {msg('selectionUnchanged')}</p>
        <button type="button" class="primary-button" onClick={() => setReloadKey(value => value + 1)}>{msg('retry')}</button>
      </div>}
      {collection?.specimens.length === 0 && <div class="empty-state"><strong>{msg('noSpecimensAvailable')}</strong><p>{msg('noIndividualRecord')}</p></div>}
      {collection && collection.specimens.length > 0 && <div class="specimens-grid" aria-label={msg('specimenCollection', collection.speciesName)}>
        {collection.specimens.map(specimen => {
          const specimenName = specimen.nickname || specimen.speciesName || msg('unknownPartner');
          return <button
            type="button"
            key={specimen.key}
            ref={element => {
              if (element) triggerRefs.current.set(specimen.key, element);
              else triggerRefs.current.delete(specimen.key);
            }}
            class="specimen-card"
            aria-label={msg('openDetails', specimenName)}
            aria-pressed={active?.key === specimen.key}
            onClick={() => openDetail(specimen.key)}
          >
            <OwnedIndividualSprite individual={specimen} />
            <span class="specimen-card-copy">
              <strong>{specimenName}</strong>
              {specimen.nickname && specimen.nickname !== specimen.speciesName && <small>{specimen.speciesName}</small>}
              <span>{specimenLocationLabel(specimen.location.label)}</span>
            </span>
            <span class="specimen-card-meta">
              {specimen.level != null && <b>{msg('levelShort', formatUiNumber(specimen.level))}</b>}
              {specimen.rarity && <RarityStars rarity={specimen.rarity} />}
            </span>
          </button>;
        })}
      </div>}
    </div>
    {active && <Dialog
      key={active.key}
      label={msg('details', active.nickname || active.speciesName || msg('partyMember'))}
      closeLabel={msg('closeDetails', active.nickname || active.speciesName || msg('partyMember'))}
      onClose={onCloseDetail}
      restoreFocus={lastTriggerRef.current}
    >
      <OwnedIndividualDetail individual={active} catalog={catalog} locationLabel={specimenLocationLabel(active.location.label)} openMove={openMove} openAbility={openAbility} openNature={openNature} openSpecies={openSpecies} />
    </Dialog>}
  </section>;
}

function specimenLocationLabel(label: PresentationMessage): string {
  if (label.code === 'SPECIMEN_PARTY_SLOT') {
    return msg('partySlotLocation', formatMessageNumber(label.slotNumber));
  }
  if (label.code === 'SPECIMEN_BOX_SLOT') {
    return msg(
      'boxSlotLocation',
      formatMessageNumber(label.boxNumber),
      formatMessageNumber(label.slotNumber),
    );
  }
  return renderPresentationMessage(label);
}

function formatMessageNumber(value: number | null | undefined): string {
  return value == null ? '—' : formatUiNumber(value);
}
