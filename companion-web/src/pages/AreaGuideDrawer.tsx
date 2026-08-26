import type { ComponentChildren } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import type { AreaGuideAreaView, AreaGuideEncounterSpeciesView, AreaGuidePointView } from '../models';

interface AreaGuideDrawerProps {
  area: AreaGuideAreaView;
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
  onClose,
  onSelectPoint,
  onSelectArea,
  selectablePointKeys = new Set<string>(),
}: AreaGuideDrawerProps) {
  const drawerRef = useRef<HTMLElement>(null);
  const renderStartedAt = useRef(typeof performance === 'undefined' ? 0 : performance.now());
  const encounterRows = area.encounters.flatMap((group, groupIndex) => group.species.map(species => ({
    key: `${groupIndex}/${species.speciesId}`,
    groupName: group.name,
    windows: group.windows,
    species,
  })));

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
    <div class="area-guide-content">
      <GuideSection title="OVERVIEW">
        <div class="area-guide-summary">
          <span><strong>{area.overview.knownPointCount}</strong> known points</span>
          {area.overview.totalPointCount != null && <span><strong>{area.overview.totalPointCount}</strong> total points</span>}
          <span><strong>{area.overview.collectedItemCount}</strong> collected items</span>
        </div>
        {area.overview.exits.length > 0 && <div class="area-guide-exits">
          <small>CONNECTED AREAS</small>
          {area.overview.exits.map(exit => <button
            key={exit.baseAreaId}
            aria-label={`Open ${exit.name} guide`}
            onClick={() => onSelectArea?.(exit.baseAreaId)}
          >{exit.name}<span aria-hidden="true">›</span></button>)}
        </div>}
      </GuideSection>

      {encounterRows.length > 0 && <GuideSection title="ENCOUNTERS">
        <WindowedList
          ariaLabel="Wild Pokémon"
          items={encounterRows}
          itemKey={row => row.key}
          renderItem={row => <EncounterSpeciesRow row={row} />}
        />
      </GuideSection>}

      {area.placesAndServices.length > 0 && <GuideSection title="PLACES & SERVICES">
        <PointList
          points={area.placesAndServices}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
        />
      </GuideSection>}

      {area.trainersAndPeople.length > 0 && <GuideSection title="TRAINERS & PEOPLE">
        <PointList
          points={area.trainersAndPeople}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
        />
      </GuideSection>}

      {area.items.length > 0 && <GuideSection title="ITEMS">
        <PointList
          points={area.items}
          selectablePointKeys={selectablePointKeys}
          onSelectPoint={onSelectPoint}
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

function EncounterSpeciesRow({ row }: { row: EncounterRow }) {
  const { species } = row;
  const level = species.minimumLevel === species.maximumLevel
    ? `Lv. ${species.minimumLevel}`
    : `Lv. ${species.minimumLevel}–${species.maximumLevel}`;
  const detail = species.ratePercent == null ? level : `${level} · ${species.ratePercent}%`;
  const context = [row.groupName, windowLabel(row.windows)].filter(Boolean).join(' · ');
  return <div class="area-guide-encounter-row">
    <img src={`/api/sprites/species/${species.speciesId}.png`} alt="" loading="lazy" decoding="async" />
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
}: {
  points: AreaGuidePointView[];
  selectablePointKeys: ReadonlySet<string>;
  onSelectPoint?: (key: string) => void;
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
  />;
}

function WindowedList<T>({
  ariaLabel,
  items,
  itemKey,
  renderItem,
}: {
  ariaLabel: string;
  items: T[];
  itemKey: (item: T) => string;
  renderItem: (item: T) => ComponentChildren;
}) {
  const [scrollTop, setScrollTop] = useState(0);
  const virtual = items.length > VIRTUAL_VIEWPORT_ROWS + VIRTUAL_OVERSCAN_ROWS;
  const viewportRows = Math.min(VIRTUAL_VIEWPORT_ROWS, items.length);
  const start = virtual
    ? Math.max(0, Math.min(items.length - viewportRows, Math.floor(scrollTop / VIRTUAL_ROW_HEIGHT) - VIRTUAL_OVERSCAN_ROWS))
    : 0;
  const end = virtual
    ? Math.min(items.length, start + viewportRows + VIRTUAL_OVERSCAN_ROWS * 2)
    : items.length;
  const visible = items.slice(start, end);
  const viewportHeight = Math.max(VIRTUAL_ROW_HEIGHT, viewportRows * VIRTUAL_ROW_HEIGHT);

  return <div
    class={`area-guide-windowed-list ${virtual ? 'is-virtual' : ''}`}
    role="list"
    aria-label={ariaLabel}
    data-total-items={items.length}
    onScroll={event => setScrollTop(event.currentTarget.scrollTop)}
    style={{ height: virtual ? viewportHeight : 'auto', maxHeight: viewportHeight }}
  >
    <div class="area-guide-windowed-spacer" style={{ height: virtual ? items.length * VIRTUAL_ROW_HEIGHT : 'auto' }}>
      <div class="area-guide-windowed-content" style={{ transform: virtual ? `translateY(${start * VIRTUAL_ROW_HEIGHT}px)` : undefined }}>
        {visible.map(item => <div key={itemKey(item)} role="listitem" class="area-guide-windowed-item">{renderItem(item)}</div>)}
      </div>
    </div>
  </div>;
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
