import type { ComponentChildren } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { catalogMediaUrl } from '../media';
import type {
  AreaGuideAreaView,
  AreaGuideEncounterSpeciesView,
  AreaGuideExitView,
  AreaGuidePointView,
} from '../models';

interface AreaGuideDrawerProps {
  area: AreaGuideAreaView;
  catalogHash?: string;
  onClose: () => void;
  onSelectPoint?: (key: string) => void;
  onSelectArea?: (baseAreaId: number) => void;
  selectablePointKeys?: ReadonlySet<string>;
}

interface EncounterRow {
  key: string;
  groupName: string | null;
  windows: string[];
  species: AreaGuideEncounterSpeciesView;
}

const VIRTUAL_ROW_HEIGHT = 56;
const VIRTUAL_VIEWPORT_ROWS = 5;
const VIRTUAL_OVERSCAN_ROWS = 2;

export function AreaGuideDrawer({
  area,
  catalogHash,
  onClose,
  onSelectPoint,
  onSelectArea,
  selectablePointKeys = new Set<string>(),
}: AreaGuideDrawerProps) {
  const drawerRef = useRef<HTMLElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [contentScrollTop, setContentScrollTop] = useState(0);
  const [contentViewportHeight, setContentViewportHeight] = useState(VIRTUAL_ROW_HEIGHT * VIRTUAL_VIEWPORT_ROWS);
  const renderStartedAt = useRef(typeof performance === 'undefined' ? 0 : performance.now());
  const encounterRows = area.encounters.flatMap((group, groupIndex) => group.species.map(species => ({
    key: `${groupIndex}/${species.speciesId}`,
    groupName: group.name,
    windows: group.windows,
    species,
  })));
  const projectedExits = projectAreaGuideExits(area.overview.exits);

  useEffect(() => {
    const content = contentRef.current;
    if (!content) return;
    content.scrollTop = 0;
    setContentScrollTop(0);
    setContentViewportHeight(content.clientHeight || VIRTUAL_ROW_HEIGHT * VIRTUAL_VIEWPORT_ROWS);
  }, [area.baseAreaId]);

  useEffect(() => {
    if (import.meta.env.MODE === 'test') return;
    const renderMillis = typeof performance === 'undefined'
      ? 0
      : Math.max(0, performance.now() - renderStartedAt.current);
    const retainedItems = drawerRef.current?.querySelectorAll(
      '.area-guide-windowed-item, .area-guide-exits > button, .area-guide-text-row',
    ).length ?? 0;
    console.debug(JSON.stringify({ event: 'area-guide-render', renderMillis, retainedItems }));
  }, [
    area.baseAreaId,
    encounterRows.length,
    area.placesAndServices.length,
    area.trainersAndPeople.length,
    area.items.length,
    area.objectives.length,
  ]);

  return <aside
    ref={drawerRef}
    class="area-guide-drawer"
    role="complementary"
    aria-label="Area guide"
    data-area-base-id={area.baseAreaId}
    onPointerDown={event => event.stopPropagation()}
    onWheel={event => event.stopPropagation()}
  >
    <header>
      <strong>AREA GUIDE</strong>
      <button aria-label="Close area guide" onClick={onClose}>×</button>
    </header>
    <div
      ref={contentRef}
      class="area-guide-content"
      onScroll={event => {
        setContentScrollTop(event.currentTarget.scrollTop);
        setContentViewportHeight(event.currentTarget.clientHeight || VIRTUAL_ROW_HEIGHT * VIRTUAL_VIEWPORT_ROWS);
      }}
    >
      <GuideSection title="OVERVIEW">
        <div class="area-guide-summary">
          <span><strong>{area.overview.knownPointCount}</strong> known points</span>
          {area.overview.totalPointCount != null && <span><strong>{area.overview.totalPointCount}</strong> total points</span>}
          <span><strong>{area.overview.collectedItemCount}</strong> collected items</span>
        </div>
        {projectedExits.length > 0 && <div class="area-guide-exits">
          <small>CONNECTED AREAS</small>
          {projectedExits.map(exit => <button
            key={exit.key}
            aria-label={exit.ariaLabel}
            onClick={() => onSelectArea?.(exit.baseAreaId)}
          >{exit.label}<span aria-hidden="true">›</span></button>)}
        </div>}
      </GuideSection>

      {encounterRows.length > 0 && <GuideSection title="ENCOUNTERS">
        <WindowedList
          ariaLabel="Wild Pokémon"
          items={encounterRows}
          itemKey={row => row.key}
          renderItem={row => <EncounterSpeciesRow row={row} catalogHash={catalogHash} />}
          scrollOwnerRef={contentRef}
          scrollTop={contentScrollTop}
          viewportHeight={contentViewportHeight}
        />
      </GuideSection>}

      {area.placesAndServices.length > 0 && <GuideSection title="PLACES & SERVICES">
        <PointList
          points={area.placesAndServices}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
          scrollOwnerRef={contentRef}
          scrollTop={contentScrollTop}
          viewportHeight={contentViewportHeight}
        />
      </GuideSection>}

      {area.trainersAndPeople.length > 0 && <GuideSection title="TRAINERS & PEOPLE">
        <PointList
          points={area.trainersAndPeople}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
          scrollOwnerRef={contentRef}
          scrollTop={contentScrollTop}
          viewportHeight={contentViewportHeight}
        />
      </GuideSection>}

      {area.items.length > 0 && <GuideSection title="ITEMS">
        <PointList
          points={area.items}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
          scrollOwnerRef={contentRef}
          scrollTop={contentScrollTop}
          viewportHeight={contentViewportHeight}
        />
      </GuideSection>}

      {area.objectives.length > 0 && <GuideSection title="OBJECTIVES">
        <div class="area-guide-static-list">
          {area.objectives.map(objective => <div key={objective.key} class="area-guide-text-row">{objective.title}</div>)}
        </div>
      </GuideSection>}
    </div>
  </aside>;
}

function GuideSection({ title, children }: { title: string; children: ComponentChildren }) {
  return <section class="area-guide-section">
    <h2>{title}</h2>
    {children}
  </section>;
}

function EncounterSpeciesRow({ row, catalogHash }: { row: EncounterRow; catalogHash?: string }) {
  const { species } = row;
  const spriteUrl = `/api/sprites/species/${species.speciesId}.png`;
  const level = species.minimumLevel === species.maximumLevel
    ? `Lv. ${species.minimumLevel}`
    : `Lv. ${species.minimumLevel}–${species.maximumLevel}`;
  const detail = species.ratePercent == null ? level : `${level} · ${species.ratePercent}%`;
  const context = [row.groupName, windowLabel(row.windows)].filter(Boolean).join(' · ');
  return <div class="area-guide-encounter-row">
    {species.hasSprite
      ? <img src={catalogHash ? catalogMediaUrl(spriteUrl, catalogHash) : spriteUrl} alt="" loading="lazy" decoding="async" />
      : <span class="area-guide-sprite-unavailable" aria-hidden="true" />}
    <span>
      <strong>{species.name}</strong>
      {context && <small>{context}</small>}
    </span>
    <b>{detail}</b>
  </div>;
}

function PointList({
  points,
  selectablePointKeys,
  onSelectPoint,
  scrollOwnerRef,
  scrollTop,
  viewportHeight,
}: {
  points: AreaGuidePointView[];
  selectablePointKeys: ReadonlySet<string>;
  onSelectPoint?: (key: string) => void;
  scrollOwnerRef: { current: HTMLDivElement | null };
  scrollTop: number;
  viewportHeight: number;
}) {
  return <WindowedList
    ariaLabel="Area points"
    items={points}
    itemKey={point => point.key}
    renderItem={point => {
      const label = pointLabel(point);
      const detail = point.state === 'COLLECTED' ? 'Collected' : point.state === 'SILHOUETTE' ? 'Not identified' : null;
      const contents = <><span class={`area-guide-point-symbol is-${point.category.toLowerCase()}`} aria-hidden="true" />
        <span><strong>{label}</strong>{detail && <small>{detail}</small>}</span></>;
      return selectablePointKeys.has(point.key) && onSelectPoint
        ? <button class="area-guide-point-row" aria-label={`Show ${label} on map`} onClick={() => onSelectPoint(point.key)}>{contents}<i aria-hidden="true">⌖</i></button>
        : <div class="area-guide-point-row">{contents}</div>;
    }}
    scrollOwnerRef={scrollOwnerRef}
    scrollTop={scrollTop}
    viewportHeight={viewportHeight}
  />;
}

function WindowedList<T>({
  ariaLabel,
  items,
  itemKey,
  renderItem,
  scrollOwnerRef,
  scrollTop,
  viewportHeight,
}: {
  ariaLabel: string;
  items: T[];
  itemKey: (item: T) => string;
  renderItem: (item: T) => ComponentChildren;
  scrollOwnerRef: { current: HTMLDivElement | null };
  scrollTop: number;
  viewportHeight: number;
}) {
  const listRef = useRef<HTMLDivElement>(null);
  const windowed = items.length > VIRTUAL_VIEWPORT_ROWS + VIRTUAL_OVERSCAN_ROWS;
  const listBounds = listRef.current?.getBoundingClientRect();
  const ownerBounds = scrollOwnerRef.current?.getBoundingClientRect();
  const hasLayoutBounds = Boolean(
    listBounds && ownerBounds
      && (listBounds.height > 0 || ownerBounds.height > 0 || listBounds.top !== ownerBounds.top),
  );
  const listTop = hasLayoutBounds && listBounds && ownerBounds
    ? listBounds.top - ownerBounds.top + scrollTop
    : 0;
  const relativeScrollTop = Math.max(0, scrollTop - listTop);
  const viewportRows = Math.max(VIRTUAL_VIEWPORT_ROWS, Math.ceil(viewportHeight / VIRTUAL_ROW_HEIGHT));
  const start = windowed
    ? Math.max(0, Math.min(
      Math.max(0, items.length - viewportRows),
      Math.floor(relativeScrollTop / VIRTUAL_ROW_HEIGHT) - VIRTUAL_OVERSCAN_ROWS,
    ))
    : 0;
  const end = windowed
    ? Math.min(items.length, start + viewportRows + VIRTUAL_OVERSCAN_ROWS * 2)
    : items.length;
  const visible = items.slice(start, end);

  return <div
    ref={listRef}
    class={`area-guide-windowed-list ${windowed ? 'is-windowed' : ''}`}
    role="list"
    aria-label={ariaLabel}
    data-total-items={items.length}
  >
    <div class="area-guide-windowed-spacer" style={{ height: windowed ? items.length * VIRTUAL_ROW_HEIGHT : 'auto' }}>
      <div class="area-guide-windowed-content" style={{ transform: windowed ? `translateY(${start * VIRTUAL_ROW_HEIGHT}px)` : undefined }}>
        {visible.map(item => <div key={itemKey(item)} role="listitem" class="area-guide-windowed-item">{renderItem(item)}</div>)}
      </div>
    </div>
  </div>;
}

interface ProjectedAreaGuideExit {
  key: string;
  baseAreaId: number;
  label: string;
  ariaLabel: string;
}

export function projectAreaGuideExits(
  exits: readonly AreaGuideExitView[],
): ProjectedAreaGuideExit[] {
  const grouped = new Map<number, { baseAreaId: number; name: string; count: number }>();
  exits.forEach(exit => {
    const count = Number.isFinite(exit.count) && (exit.count ?? 0) > 0
      ? Math.floor(exit.count!)
      : 1;
    const name = exit.name.trim() || 'Unidentified area';
    const existing = grouped.get(exit.baseAreaId);
    if (existing) existing.count += count;
    else grouped.set(exit.baseAreaId, { baseAreaId: exit.baseAreaId, name, count });
  });

  const nameTotals = new Map<string, number>();
  grouped.forEach(exit => {
    const nameKey = exit.name.toLocaleLowerCase();
    nameTotals.set(nameKey, (nameTotals.get(nameKey) ?? 0) + 1);
  });
  const nameOrdinals = new Map<string, number>();

  return [...grouped.values()].map(exit => {
    const nameKey = exit.name.toLocaleLowerCase();
    const sameNameTotal = nameTotals.get(nameKey) ?? 1;
    const ordinal = (nameOrdinals.get(nameKey) ?? 0) + 1;
    nameOrdinals.set(nameKey, ordinal);
    if (exit.count > 1 && sameNameTotal > 1) {
      return {
        key: `exit/${exit.baseAreaId}`,
        baseAreaId: exit.baseAreaId,
        label: `${exit.name} · ${exit.count} EXITS · DESTINATION ${ordinal} OF ${sameNameTotal}`,
        ariaLabel: `Open ${exit.name} guide, ${exit.count} exits, destination ${ordinal} of ${sameNameTotal}`,
      };
    }
    if (exit.count > 1) {
      return {
        key: `exit/${exit.baseAreaId}`,
        baseAreaId: exit.baseAreaId,
        label: `${exit.name} · ${exit.count} EXITS`,
        ariaLabel: `Open ${exit.name} guide, ${exit.count} exits`,
      };
    }
    if (sameNameTotal > 1) {
      return {
        key: `exit/${exit.baseAreaId}`,
        baseAreaId: exit.baseAreaId,
        label: `${exit.name} · EXIT ${ordinal} OF ${sameNameTotal}`,
        ariaLabel: `Open ${exit.name} guide, exit ${ordinal} of ${sameNameTotal}`,
      };
    }
    return {
      key: `exit/${exit.baseAreaId}`,
      baseAreaId: exit.baseAreaId,
      label: exit.name,
      ariaLabel: `Open ${exit.name} guide`,
    };
  });
}

function pointLabel(point: AreaGuidePointView) {
  const label = point.label?.trim();
  if (label && label.toLocaleLowerCase() !== 'place') return label;
  if (point.category === 'AVAILABLE_ITEM' || point.category === 'COLLECTED_ITEM') return 'Unidentified item';
  if (point.category === 'SERVICE') return 'Unidentified service';
  if (point.category === 'PLACE') return 'Unidentified entrance';
  return 'Unidentified point';
}

function windowLabel(windows: string[]) {
  const labels: string[] = windows.flatMap(window => {
    if (window === 'DAY') return ['Day'];
    if (window === 'NIGHT') return ['Night'];
    if (window === 'MORNING') return ['Morning'];
    if (window === 'EVENING') return ['Evening'];
    return [];
  });
  return [...new Set(labels)].join(' / ');
}
