import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { MapPage } from './MapPage';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

beforeEach(() => {
  class TestResizeObserver {
    constructor(private readonly callback: ResizeObserverCallback) {}
    observe(target: Element) { this.callback([{ target, contentRect: target.getBoundingClientRect() } as ResizeObserverEntry], this as unknown as ResizeObserver); }
    disconnect() {}
    unobserve() {}
  }
  vi.stubGlobal('ResizeObserver', TestResizeObserver);
  HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as typeof HTMLCanvasElement.prototype.getContext;
});

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], species: [], moves: [], types: [],
  areas: [{ id: 0x11 * 10 + 1, baseAreaId: 0x11, name: 'Oldale grass', methodId: 1, speciesIds: [], windows: ['ANY'], slots: [] }],
  balls: [], capabilities: {},
  worldMaps: [{
    key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120,
    gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/world%2Fgen3-region-0.png',
    locations: [
      { key: 'section-16', displayName: 'Route 101', baseAreaIds: [0x10], geometry: [{ x: 3, y: 11, width: 2, height: 1 }] },
      { key: 'section-17', displayName: 'Oldale Town', baseAreaIds: [0x11], geometry: [{ x: 4, y: 9, width: 1, height: 1 }] },
      { key: 'section-18', displayName: 'Petalburg City', baseAreaIds: [0x12], geometry: [{ x: 2, y: 8, width: 1, height: 1 }] },
    ],
  }],
};

const state: State = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'AREA', selectedAreaId: null, currentAreaBaseId: 0x10, currentAreaName: 'Route 101', battleTab: 'ENTRY',
  revealedAreaBaseIds: [0x10, 0x11],
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {}, observedMoves: {}, battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null,
  activeRulesetId: null, rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  gameTime: { hours: 16, minutes: 48, phase: 'DAY', phaseProgress: 0.72 },
};

describe('normalized world map presentation', () => {
  it('opens the Area Guide for the tracked or manually selected Atlas area without changing map state', () => {
    const guideState: State = {
      ...state,
      areaGuide: {
        trackedAreaBaseId: 0x10,
        areas: [
          {
            baseAreaId: 0x10, name: 'Route 101',
            overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
            encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
          },
          {
            baseAreaId: 0x11, name: 'Oldale Town',
            overview: { knownPointCount: 1, totalPointCount: null, collectedItemCount: 0, exits: [{ baseAreaId: 0x10, name: 'Route 101' }] },
            encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
          },
        ],
      },
    };
    render(<MapPage catalog={catalog} state={guideState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    const stage = screen.getByRole('region', { name: 'Interactive world map' });
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    const scale = stage.dataset.scale;
    const panX = stage.dataset.panX;
    const panY = stage.dataset.panY;

    const guideControl = screen.getByRole('button', { name: 'Area Guide' });
    expect(guideControl.classList.contains('map-control')).toBe(true);
    expect(guideControl.querySelector('svg')?.dataset.semanticIcon).toBe('area-guide');
    fireEvent.click(guideControl);
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('16');
    fireEvent.click(screen.getByRole('button', { name: 'Oldale Town' }));
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('17');
    fireEvent.click(screen.getByRole('button', { name: 'Close area guide' }));

    expect(stage.dataset.scale).toBe(scale);
    expect(stage.dataset.panX).toBe(panX);
    expect(stage.dataset.panY).toBe(panY);
    expect(screen.queryByRole('complementary', { name: 'Area guide' })).toBeNull();
  });

  it('follows live area changes until a manual guide selection is held, then resumes on recenter', () => {
    const guideState: State = {
      ...state,
      areaGuide: {
        trackedAreaBaseId: 0x10,
        areas: [
          {
            baseAreaId: 0x10, name: 'Route 101',
            overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
            encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
          },
          {
            baseAreaId: 0x11, name: 'Oldale Town',
            overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
            encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
          },
        ],
      },
    };
    const view = render(<MapPage catalog={catalog} state={guideState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Area Guide' }));
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('16');

    const movedState = {
      ...guideState,
      currentAreaBaseId: 0x11,
      currentAreaName: 'Oldale Town',
      areaGuide: { ...guideState.areaGuide!, trackedAreaBaseId: 0x11 },
    };
    view.rerender(<MapPage catalog={catalog} state={movedState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('17');

    fireEvent.click(screen.getByRole('button', { name: 'Route 101' }));
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('16');
    view.rerender(<MapPage catalog={catalog} state={movedState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('16');
    expect(document.querySelector('.map-current-location span')?.textContent).toBe('MAP POINT');

    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    expect(screen.getByRole('complementary', { name: 'Area guide' }).dataset.areaBaseId).toBe('17');
    expect(document.querySelector('.map-current-location span')?.textContent).toBe('CURRENT');
  });

  it('shows only location context on the left and Pokédex-style actions on the right', () => {
    const openAreaDex = vi.fn();
    const openSettings = vi.fn();
    const { container } = render(<MapPage catalog={catalog} state={state} onOpenPokedex={openAreaDex} onOpenSettings={openSettings} />);

    expect(screen.getByText('CURRENT')).toBeTruthy();
    expect(container.querySelector('.map-page-title')).toBeNull();
    expect(screen.queryByText('EMERALD')).toBeNull();
    expect(screen.queryByText('WORLD MAP')).toBeNull();
    expect(container.querySelector('.map-current-location strong')?.textContent).toBe('Route 101');
    expect(container.querySelectorAll('.map-marker.is-current')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Current location: Route 101' }).classList.contains('atlas-location-marker')).toBe(true);
    expect(screen.getByRole('button', { name: 'Oldale Town' }).classList.contains('atlas-location-marker')).toBe(true);

    const actions = container.querySelector('.map-header-actions')!;
    const buttons = [...actions.querySelectorAll(':scope > button')];
    expect(buttons.map(button => button.getAttribute('aria-label'))).toEqual([
      'Open Pokédex',
      'Settings',
    ]);
    expect(buttons[0].querySelector('svg')?.dataset.semanticIcon).toBe('pokedex');
    expect(buttons[1].querySelector('svg')?.dataset.semanticIcon).toBe('settings');
    expect(container.querySelector('[data-map-navigation-row]')).toBeNull();
    expect(container.querySelector('.map-plane')?.classList.contains('map-framed-plane')).toBe(true);
    expect(container.querySelectorAll('.header-game-clock')).toHaveLength(1);
    expect(container.querySelector('[data-semantic-icon="sun"]')).toBeTruthy();

    fireEvent.click(buttons[1]);
    expect(openSettings).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Oldale Town' }));
    fireEvent.click(buttons[0]);
    expect(openAreaDex).toHaveBeenCalledOnce();
  });

  it('keeps every persisted discovered location revealed and returns to the retained Pokédex view', () => {
    const openAreaDex = vi.fn();
    render(<MapPage catalog={catalog} state={state} onOpenPokedex={openAreaDex} onOpenSettings={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Oldale Town' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Petalburg City' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Oldale Town' }));
    fireEvent.click(screen.getByRole('button', { name: 'Open Pokédex' }));

    expect(openAreaDex).toHaveBeenCalledOnce();
  });

  it('does not expose or route an undiscovered location while fog is active', () => {
    render(<MapPage
      catalog={catalog}
      state={{ ...state, currentAreaBaseId: null, currentAreaName: null, revealedAreaBaseIds: [] }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(screen.getByText('Atlas')).toBeTruthy();
    expect(screen.getByText('ATLAS')).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Interactive world map' }).dataset.selectedKey).toBeUndefined();
    expect(document.querySelectorAll('.map-marker.is-current')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: 'Route 101' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Open Pokédex' }).hasAttribute('disabled')).toBe(false);
  });

  it('restores the selected Area Dex location instead of replacing it with the physical current marker', () => {
    const { container } = render(<MapPage
      catalog={catalog}
      state={{ ...state, selectedAreaId: 0x11 * 10 + 1 }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(container.querySelector('.map-current-location strong')?.textContent).toBe('Oldale Town');
    expect(screen.getByText('MAP POINT')).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Interactive world map' }).dataset.selectedKey).toBe('section-17');
  });

  it('keeps zoom, recenter, permanent eligible markers, and knowledge-mode fog functional', () => {
    const { container } = render(<MapPage catalog={catalog} state={state} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    const stage = screen.getByRole('region', { name: 'Interactive world map' });

    expect(stage.dataset.scale).toBe('1');
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(stage.dataset.scale)).toBeGreaterThan(1);
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    expect(stage.dataset.scale).toBe('1');
    expect(stage.dataset.panX).toBe('0');
    expect(stage.dataset.panY).toBe('0');

    expect(screen.queryByRole('button', { name: 'Toggle fog of war' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Toggle map markers' })).toBeNull();
    expect(container.querySelector('.map-fog')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Current location: Route 101' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Oldale Town' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Petalburg City' })).toBeNull();
  });

  it('shows the whole map in Discovered mode and keeps global Pokédex navigation available', () => {
    const openAreaDex = vi.fn();
    const { container } = render(<MapPage
      catalog={catalog}
      state={{ ...state, settings: { ...state.settings, knowledgeMode: 'DISCOVERED' } }}
      onOpenPokedex={openAreaDex}
      onOpenSettings={vi.fn()}
    />);

    expect(container.querySelector('.map-fog')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Petalburg City' }));
    const areaDex = screen.getByRole('button', { name: 'Open Pokédex' });
    expect(areaDex.hasAttribute('disabled')).toBe(false);
    fireEvent.click(areaDex);
    expect(openAreaDex).toHaveBeenCalledOnce();
  });
});

describe('optional local map presentation', () => {
  const localCatalog: Catalog = {
    ...catalog,
    localMaps: [{
      key: 'local/0010', displayName: 'Route 101', baseAreaId: 0x10,
      pixelWidth: 320, pixelHeight: 320, gridWidth: 20, gridHeight: 20,
      imageUrl: '/api/maps/local%2F0010%2Fmap.png', dynamicLighting: false,
    }],
  };
  const connectedCatalog: Catalog = {
    ...localCatalog,
    localMaps: [
      ...localCatalog.localMaps!,
      {
        key: 'local/0011', displayName: 'Oldale Town', baseAreaId: 0x11,
        pixelWidth: 384, pixelHeight: 320, gridWidth: 24, gridHeight: 20,
        imageUrl: '/api/maps/local%2F0011%2Fmap.png', dynamicLighting: false,
      },
    ],
    mapScenes: [{
      key: 'scene/0010', pixelWidth: 704, pixelHeight: 320, gridWidth: 44, gridHeight: 20,
      placements: [
        {
          localMapKey: 'local/0010', baseAreaId: 0x10, gridX: 0, gridY: 0,
          pixelX: 0, pixelY: 0, pixelWidth: 320, pixelHeight: 320, gridWidth: 20, gridHeight: 20,
          imageUrl: '/api/maps/local%2F0010%2Fmap.png', dynamicLighting: false,
        },
        {
          localMapKey: 'local/0011', baseAreaId: 0x11, gridX: 20, gridY: 0,
          pixelX: 320, pixelY: 0, pixelWidth: 384, pixelHeight: 320, gridWidth: 24, gridHeight: 20,
          imageUrl: '/api/maps/local%2F0011%2Fmap.png', dynamicLighting: false,
        },
      ],
    }],
  };

  it('shows every permitted POI and label at the starting Local zoom by default', () => {
    const updatePoiPreferences = vi.fn();
    const poiState = {
      ...state,
      currentMapPosition: { x: 12, y: 7 },
      localMapPois: [
        { key: 'place', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 1, tileY: 1, category: 'PLACE', state: 'IDENTIFIED', displayName: 'Route gate', service: null, itemId: null, itemName: null, destinationBaseAreaId: 0x11 },
        { key: 'service', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 4, tileY: 1, category: 'SERVICE', state: 'IDENTIFIED', displayName: 'Pokémon Center', service: 'POKEMON_CENTER', itemId: null, itemName: null, destinationBaseAreaId: null },
        { key: 'item', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 7, tileY: 1, category: 'AVAILABLE_ITEM', state: 'SILHOUETTE', displayName: null, service: null, itemId: null, itemName: null, destinationBaseAreaId: null },
        { key: 'collected', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 10, tileY: 1, category: 'COLLECTED_ITEM', state: 'COLLECTED', displayName: null, service: null, itemId: 13, itemName: 'Potion', destinationBaseAreaId: null },
        { key: 'unknown', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 13, tileY: 1, category: 'UNKNOWN', state: 'SILHOUETTE', displayName: null, service: null, itemId: null, itemName: null, destinationBaseAreaId: null },
      ],
      localMapPoiPreferences: {
        showPlaces: true,
        showServices: true,
        showAvailableItems: true,
        showCollectedItems: true,
        showUnknownPois: true,
        iconZoomThresholdPercent: 0,
        labelZoomThresholdPercent: 0,
      },
    } as State;
    const { container } = render(<MapPage
      catalog={localCatalog}
      state={poiState}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
      onUpdatePoiPreferences={updatePoiPreferences}
    />);

    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    expect(stage.dataset.poiZoomPercent).toBe('0');
    expect(container.querySelectorAll('.map-poi-marker')).toHaveLength(5);
    expect(container.querySelectorAll('.map-poi-label')).toHaveLength(3);
    expect(screen.getByText('Route gate')).toBeTruthy();
    expect(screen.queryByText('Place')).toBeNull();
    expect(screen.queryByText('Unknown')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Map POI filters' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Available items' }));
    expect(updatePoiPreferences).toHaveBeenCalledWith({ showAvailableItems: false });
  });

  it('highlights only a knowledge-visible POI selected from the Area Guide', () => {
    const poiState = {
      ...state,
      currentMapPosition: { x: 12, y: 7 },
      localMapPois: [
        { key: 'house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 4, tileY: 4, category: 'SERVICE', state: 'IDENTIFIED', displayName: 'Your House', service: 'BUILDING', itemId: null, itemName: null, destinationBaseAreaId: null },
      ],
      areaGuide: {
        trackedAreaBaseId: 0x10,
        areas: [{
          baseAreaId: 0x10, name: 'Route 101',
          overview: { knownPointCount: 1, totalPointCount: null, collectedItemCount: 0, exits: [] },
          encounters: [],
          placesAndServices: [{ key: 'house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 4, tileY: 4, category: 'SERVICE', state: 'IDENTIFIED', label: 'Your House', service: 'BUILDING', itemId: null, destinationBaseAreaId: null }],
          trainersAndPeople: [], items: [], objectives: [],
        }],
      },
    } as State;
    const { container } = render(<MapPage catalog={localCatalog} state={poiState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Area Guide' }));
    expect(screen.getAllByRole('button', { name: 'Map POI filters' })).toHaveLength(1);
    expect(screen.queryByText('Map details')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Show Your House on map' }));
    expect(container.querySelector('[data-poi-key="house"]')?.classList.contains('is-selected')).toBe(true);
  });

  it('applies normalized POI zoom thresholds above the starting Local zoom', () => {
    const thresholdState = {
      ...state,
      currentMapPosition: { x: 12, y: 7 },
      localMapPois: [{ key: 'item', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 3, tileY: 3, category: 'AVAILABLE_ITEM', state: 'IDENTIFIED', displayName: null, service: null, itemId: 13, itemName: 'Potion', destinationBaseAreaId: null }],
      localMapPoiPreferences: {
        showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true,
        iconZoomThresholdPercent: 20, labelZoomThresholdPercent: 60,
      },
    } as State;
    const { container } = render(<MapPage catalog={localCatalog} state={thresholdState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    expect(container.querySelector('.map-poi-marker')).toBeNull();
    for (let index = 0; index < 4; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(screen.getByRole('region', { name: 'Interactive local map' }).dataset.poiZoomPercent)).toBeGreaterThanOrEqual(20);
    expect(container.querySelector('.map-poi-marker')).toBeTruthy();
    expect(container.querySelector('.map-poi-label')).toBeNull();
    for (let index = 0; index < 4; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(screen.getByRole('region', { name: 'Interactive local map' }).dataset.poiZoomPercent)).toBeGreaterThanOrEqual(60);
    expect(container.querySelector('.map-poi-label')).toBeTruthy();
  });

  it('removes POI icons and labels when zooming below the starting Local view', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const poiState = {
      ...state,
      currentMapPosition: { x: 7, y: 9 },
      localMapPois: [
        { key: 'house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 7, tileY: 8, category: 'PLACE', state: 'IDENTIFIED', displayName: "BRENDAN's HOUSE", service: null, itemId: null, itemName: null, destinationBaseAreaId: 0x100 },
      ],
      localMapPoiPreferences: {
        showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true,
        iconZoomThresholdPercent: 0, labelZoomThresholdPercent: 0,
      },
    } as State;
    const { container } = render(<MapPage catalog={connectedCatalog} state={poiState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    expect(container.querySelector('.map-poi-marker')).toBeTruthy();
    expect(container.querySelector('.map-poi-label')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Zoom out' }));
    expect(container.querySelector('.map-poi-marker')).toBeNull();
    expect(container.querySelector('.map-poi-label')).toBeNull();
    rect.mockRestore();
  });

  it('uses the standard filter icon and map-control styling', () => {
    render(<MapPage catalog={localCatalog} state={{ ...state, currentMapPosition: { x: 7, y: 9 } }} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    const filter = screen.getByRole('button', { name: 'Map POI filters' });
    expect(filter.classList.contains('map-control')).toBe(true);
    expect(filter.querySelector('svg')?.dataset.semanticIcon).toBe('filter');
  });

  it('clusters nine overlapping Oldale-scale POIs and keeps every member individually selectable', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 538, bottom: 383, left: 0,
      width: 538, height: 383,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const localMapPois = Array.from({ length: 9 }, (_, index) => ({
      key: `point-${index + 1}`,
      localMapKey: 'local/0010',
      baseAreaId: 0x10,
      tileX: 7 + index % 3,
      tileY: 7 + Math.floor(index / 3),
      category: 'PLACE' as const,
      state: 'IDENTIFIED' as const,
      displayName: `Point ${index + 1}`,
      service: null,
      itemId: null,
      itemName: null,
      destinationBaseAreaId: 0x100 + index,
    }));
    const poiState = {
      ...state,
      currentMapPosition: { x: 7, y: 9 },
      localMapPois,
      localMapPoiPreferences: {
        showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true,
        iconZoomThresholdPercent: 0, labelZoomThresholdPercent: 0,
      },
    } as State;
    const { container } = render(<MapPage catalog={localCatalog} state={poiState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    expect(container.querySelectorAll('.map-poi-marker')).toHaveLength(1);
    const cluster = screen.getByRole('button', { name: '9 map points' });
    expect(cluster.getAttribute('data-poi-cluster-key')).toBe('cluster/point-1/9');
    fireEvent.click(cluster);
    expect(screen.getByRole('region', { name: 'Map point chooser' })).toBeTruthy();
    expect(container.querySelectorAll('.map-poi-cluster-list > button')).toHaveLength(9);
    fireEvent.click(screen.getByRole('button', { name: 'Select Point 9, point 9 of 9' }));
    expect(screen.queryByRole('region', { name: 'Map point chooser' })).toBeNull();
    expect(screen.getByRole('complementary', { name: 'Map point details' }).textContent).toContain('Point 9');

    fireEvent.click(screen.getByRole('button', { name: 'Close map point details' }));
    fireEvent.click(screen.getByRole('button', { name: '9 map points' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close map point chooser' }));
    expect(screen.queryByRole('region', { name: 'Map point chooser' })).toBeNull();

    for (let index = 0; index < 4; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(container.querySelectorAll('.map-poi-cluster')).toHaveLength(0);
    expect(container.querySelectorAll('.map-poi-marker')).toHaveLength(9);
    rect.mockRestore();
  });

  it('places nearby house labels on opposite sides instead of hiding one', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1024, bottom: 650, left: 0,
      width: 1024, height: 650,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const poiState = {
      ...state,
      currentMapPosition: { x: 12, y: 10 },
      localMapPois: [
        { key: 'player-house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 10, tileY: 8, category: 'PLACE', state: 'IDENTIFIED', displayName: 'Your House', service: null, itemId: null, itemName: null, destinationBaseAreaId: 0x100 },
        { key: 'birch-house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 12, tileY: 8, category: 'PLACE', state: 'IDENTIFIED', displayName: "Prof. Birch's House", service: null, itemId: null, itemName: null, destinationBaseAreaId: 0x102 },
      ],
      localMapPoiPreferences: {
        showPlaces: true, showServices: true, showAvailableItems: true, showCollectedItems: true, showUnknownPois: true,
        iconZoomThresholdPercent: 0, labelZoomThresholdPercent: 0,
      },
    } as State;
    const { container } = render(<MapPage catalog={connectedCatalog} state={poiState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);

    const labels = [...container.querySelectorAll('.map-poi-label')];
    expect(labels).toHaveLength(2);
    expect(labels.map(label => label.textContent).sort()).toEqual(["Prof. Birch's House", 'Your House']);
    expect(labels.some(label => label.classList.contains('is-above'))).toBe(true);
    rect.mockRestore();
  });

  it('uses Local as the only map surface when playable geography is available', () => {
    const { container } = render(<MapPage
      catalog={localCatalog}
      state={{ ...state, currentMapPosition: { x: 12, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    const localStage = screen.getByRole('region', { name: 'Interactive local map' });
    expect(localStage.dataset.mapMode).toBe('LOCAL');
    expect(container.querySelector('.map-plane img')?.getAttribute('src')).toBe('/api/maps/local%2F0010%2Fmap.png');
    const playerMarker = container.querySelector('.map-player-marker');
    expect(playerMarker?.getAttribute('aria-label')).toBe('Player position 12, 7');
    expect(playerMarker?.classList.contains('atlas-location-marker')).toBe(false);
    expect(playerMarker?.classList.contains('has-sprite')).toBe(false);
    expect(playerMarker?.querySelector('.map-player-dot')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'Show Atlas' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Show Local map' })).toBeNull();
  });

  it('uses the gender-selected ROM overworld sprite at its ROM-derived native map scale', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const { container } = render(<MapPage
      catalog={connectedCatalog}
      state={{
        ...state,
        currentMapPosition: { x: 12, y: 7 },
        trainerMapSpriteUrl: '/api/trainer-assets/trainer%2Foverworld%2Ffemale.png',
        trainerMapSpriteWidth: 32,
        trainerMapSpriteHeight: 32,
        trainer: {
          name: 'May', gender: 'FEMALE', publicTrainerId: 7, money: 3000,
          playTimeHours: 1, playTimeMinutes: 23, dexSeen: 4, dexCaught: 2,
          stars: 0, avatarUrl: '/api/trainer-assets/trainer%2Favatar%2Ffemale.png', badges: [],
        },
      }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    const marker = container.querySelector('.map-player-marker');
    expect(marker?.classList.contains('has-sprite')).toBe(true);
    expect(marker?.querySelector('img')?.getAttribute('src')).toBe('/api/trainer-assets/trainer%2Foverworld%2Ffemale.png');
    expect(marker?.querySelector('img')?.getAttribute('alt')).toBe('May');
    expect(marker?.querySelector('.map-player-dot')).toBeNull();
    for (let index = 0; index < 12; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    const rasterScale = Number(screen.getByRole('region', { name: 'Interactive local map' }).dataset.effectiveRasterScale);
    expect(Number.parseFloat((marker as HTMLElement).style.width)).toBeCloseTo(32 * Math.max(1, rasterScale), 8);
    expect(Number.parseFloat((marker as HTMLElement).style.height)).toBeCloseTo(32 * Math.max(1, rasterScale), 8);
    rect.mockRestore();
  });

  it('uses a native sixteen-pixel GB overworld sprite and preserves scale when recentering', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const { container } = render(<MapPage
      catalog={connectedCatalog}
      state={{
        ...state,
        currentMapPosition: { x: 12, y: 7 },
        trainerMapSpriteUrl: '/api/trainer-assets/trainer%2Foverworld%2Fplayer.png',
        trainerMapSpriteWidth: 16,
        trainerMapSpriteHeight: 16,
      }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    const marker = container.querySelector<HTMLElement>('.map-player-marker')!;
    const rasterScale = Number(stage.dataset.effectiveRasterScale);
    expect(marker.classList.contains('has-sprite')).toBe(true);
    expect(marker.querySelector('img')?.getAttribute('src')).toBe('/api/trainer-assets/trainer%2Foverworld%2Fplayer.png');
    expect(Number.parseFloat(marker.style.width)).toBeCloseTo(16 * Math.max(1, rasterScale), 8);
    expect(Number.parseFloat(marker.style.height)).toBeCloseTo(16 * Math.max(1, rasterScale), 8);
    expect(Number.parseFloat(marker.style.width)).toBeGreaterThanOrEqual(16);
    const scale = stage.dataset.scale;
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    expect(stage.dataset.scale).toBe(scale);
    rect.mockRestore();
  });

  it('renders a connected Local scene and preserves the viewport across its map boundary', () => {
    const view = render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 19, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    const images = view.container.querySelectorAll('.map-scene-tile');
    expect(images).toHaveLength(2);
    expect(images[0].getAttribute('src')).toBe('/api/maps/local%2F0010%2Fmap.png');
    expect(images[1].getAttribute('src')).toBe('/api/maps/local%2F0011%2Fmap.png');
    expect(images[1].getAttribute('style')).toContain('left: 45.4545');
    expect(stage.dataset.selectedKey).toBe('scene/0010');
    expect(view.container.querySelector('.map-player-marker')?.getAttribute('style')).toContain('left: 44.3181');
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    const zoomedScale = stage.dataset.scale;

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentAreaBaseId: 0x11, currentAreaName: 'Oldale Town', currentMapPosition: { x: 1, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(view.container.querySelectorAll('.map-scene-tile')).toHaveLength(2);
    expect(stage.dataset.scale).toBe(zoomedScale);
    expect(view.container.querySelector('.map-player-marker')?.getAttribute('style')).toContain('left: 48.8636');
    expect(view.container.querySelector('.map-player-marker')?.getAttribute('aria-label')).toBe('Player position 1, 7');
  });

  it('mounts only the current and adjacent revealed rasters within thirty-two MiB', () => {
    const placements = Array.from({ length: 100 }, (_, index) => ({
      localMapKey: `local/${index}`,
      baseAreaId: 0x10 + index,
      gridX: index * 64,
      gridY: 0,
      pixelX: index * 1024,
      pixelY: 0,
      pixelWidth: 1024,
      pixelHeight: 1024,
      gridWidth: 64,
      gridHeight: 64,
      imageUrl: `/api/maps/local%2F${index}%2Fmap.png`,
      dynamicLighting: true,
    }));
    const largeCatalog: Catalog = {
      ...connectedCatalog,
      mapScenes: [{
        key: 'scene/large',
        pixelWidth: 1024 * placements.length,
        pixelHeight: 1024,
        gridWidth: 64 * placements.length,
        gridHeight: 64,
        placements,
      }],
    };
    const view = render(<MapPage
      catalog={largeCatalog}
      state={{
        ...state,
        currentAreaBaseId: 0x10 + 50,
        revealedAreaBaseIds: placements.map(placement => placement.baseAreaId),
        gameTime: { hours: 18, minutes: 37, phase: 'DAY', phaseProgress: 0.8 },
      }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    const mounted = [...view.container.querySelectorAll('.map-scene-tile')];
    expect(mounted).toHaveLength(3);
    expect(mounted.map(image => image.getAttribute('data-local-map-key'))).toEqual([
      'local/50', 'local/49', 'local/51',
    ]);
    expect(Number(stage.dataset.mountedDecodedBytes)).toBe(3 * 1024 * 1024 * 4);
    expect(Number(stage.dataset.mountedDecodedBytes)).toBeLessThanOrEqual(32 * 1024 * 1024);
    expect(mounted.every(image => image.getAttribute('src')?.endsWith('?hour=18&minute=37'))).toBe(true);
  });

  it('does not load undiscovered Local rasters or an Atlas underlay in Organic mode', () => {
    const view = render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, revealedAreaBaseIds: [] }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();
    expect(view.container.innerHTML).not.toContain('/api/maps/local%2F0011%2Fmap.png');
    const images = view.container.querySelectorAll('.map-scene-tile');
    expect(images).toHaveLength(1);
    expect(images[0].getAttribute('data-local-map-key')).toBe('local/0010');
    expect(view.container.querySelector('img[data-local-map-key="local/0011"]')).toBeNull();
    const fog = view.container.querySelectorAll('.map-scene-placement-fog');
    expect(fog).toHaveLength(1);
    expect(fog[0].getAttribute('data-local-map-key')).toBe('local/0011');
    expect([...view.container.querySelectorAll('.map-local-poi-label')].map(label => label.textContent)).toEqual([]);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, settings: { ...state.settings, knowledgeMode: 'DISCOVERED' } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect(view.container.querySelectorAll('.map-scene-tile')).toHaveLength(2);
    expect(view.container.querySelectorAll('.map-scene-placement-fog')).toHaveLength(0);
    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();
    expect([...view.container.querySelectorAll('.map-local-poi-label')].map(label => label.textContent)).toEqual(['Oldale Town']);
  });

  it('renders final raster dimensions and recenters on the player without changing zoom', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const { container } = render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 19, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    const plane = container.querySelector<HTMLElement>('.map-plane')!;

    expect(Number(stage.dataset.scale)).toBeGreaterThan(1);
    expect(Number(stage.dataset.panX)).toBeGreaterThan(0);
    expect(parseFloat(plane.style.width)).toBeCloseTo(1240 * Number(stage.dataset.scale), 5);
    expect(parseFloat(plane.style.height)).toBeCloseTo((1240 / 704 * 320) * Number(stage.dataset.scale), 5);
    expect(plane.style.transform).not.toContain('scale(');
    for (let index = 0; index < 12; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(stage.dataset.scale)).toBeGreaterThan(4);
    const zoomedScale = Number(stage.dataset.scale);
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    expect(Number(stage.dataset.scale)).toBe(zoomedScale);
    expect(Number(stage.dataset.panX)).toBeCloseTo(-(((19.5 / 44) - 0.5) * 1240 * zoomedScale), 5);
    expect(Number(stage.dataset.panY)).toBeCloseTo(-(((7.5 / 20) - 0.5) * (1240 / 704 * 320) * zoomedScale), 5);
    expect(container.querySelectorAll('.map-scene-tile')).toHaveLength(2);
    expect(container.querySelector<HTMLElement>('.map-player-marker')?.style.width).toBe('');
    rect.mockRestore();
  });

  it('follows live player movement after recenter until manual viewport movement breaks tracking', async () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const view = render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 18, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    const centeredPanX = Number(stage.dataset.panX);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 19, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const targetPanX = -(((19.5 / 44) - 0.5) * 1240 * Number(stage.dataset.scale));
    expect(view.container.querySelector('.map-player-marker')?.getAttribute('aria-label')).toBe('Player position 19, 7');
    await waitFor(() => expect(Number(stage.dataset.panX)).not.toBeCloseTo(centeredPanX, 3));
    expect(Math.abs(Number(stage.dataset.panX) - targetPanX)).toBeLessThan(Math.abs(centeredPanX - targetPanX));
    expect(Number(stage.dataset.panX)).not.toBeCloseTo(targetPanX, 3);
    expect(view.container.querySelector('.map-plane')?.classList.contains('is-camera-gliding')).toBe(false);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 1, y: 18 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    await waitFor(() => expect(Number(stage.dataset.panX)).toBeCloseTo(-(((1.5 / 44) - 0.5) * 1240 * Number(stage.dataset.scale)), 5));

    fireEvent.wheel(stage, { deltaY: -1, clientX: 400, clientY: 300 });
    const manuallyPannedX = Number(stage.dataset.panX);
    expect(Number.isFinite(manuallyPannedX)).toBe(true);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, currentMapPosition: { x: 17, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect(Number(stage.dataset.panX)).toBeCloseTo(manuallyPannedX, 5);
    rect.mockRestore();
  });

  it('keeps local tracking, zoom, filters, fog, and mounted rasters intact while the Area Guide opens and closes', () => {
    const bounds = {
      x: 0, y: 0, top: 0, right: 1240, bottom: 825, left: 0,
      width: 1240, height: 825,
      toJSON: () => ({}),
    } as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(bounds);
    const guide = {
      trackedAreaBaseId: 0x10,
      areas: [{
        baseAreaId: 0x10, name: 'Route 101',
        overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
        encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
      }],
    };
    const initialState: State = {
      ...state,
      currentMapPosition: { x: 18, y: 7 },
      settings: { ...state.settings, mapFollowSmoothingPercent: 0 },
      areaGuide: guide,
    };
    const view = render(<MapPage catalog={connectedCatalog} state={initialState} onOpenPokedex={vi.fn()} onOpenSettings={vi.fn()} />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Recenter map' }));
    const scale = stage.dataset.scale;
    const mountedBytes = stage.dataset.mountedDecodedBytes;
    const rasterSources = [...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'));
    const fogCount = view.container.querySelectorAll('.map-scene-placement-fog').length;

    fireEvent.click(screen.getByRole('button', { name: 'Area Guide' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close area guide' }));
    expect(stage.dataset.scale).toBe(scale);
    expect(stage.dataset.mountedDecodedBytes).toBe(mountedBytes);
    expect([...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'))).toEqual(rasterSources);
    expect(view.container.querySelectorAll('.map-scene-placement-fog')).toHaveLength(fogCount);
    expect(screen.getAllByRole('button', { name: 'Map POI filters' })).toHaveLength(1);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...initialState, currentMapPosition: { x: 19, y: 7 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect(Number(stage.dataset.panX)).toBeCloseTo(-(((19.5 / 44) - 0.5) * 1240 * Number(scale)), 5);
    rect.mockRestore();
  });

  it('consumes companion Back in the Area Guide before leaving the map', async () => {
    const guide = {
      trackedAreaBaseId: 0x10,
      areas: [{
        baseAreaId: 0x10, name: 'Route 101',
        overview: { knownPointCount: 0, totalPointCount: null, collectedItemCount: 0, exits: [] },
        encounters: [], placesAndServices: [], trainersAndPeople: [], items: [], objectives: [],
      }],
    };
    render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, areaGuide: guide }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    fireEvent.click(screen.getByRole('button', { name: 'Area Guide' }));

    const back = new Event('dualdexback', { cancelable: true }) as Event & { dualdexHandled?: boolean };
    window.dispatchEvent(back);

    expect(back.defaultPrevented).toBe(true);
    expect(back.dualdexHandled).toBe(true);
    await waitFor(() => expect(screen.queryByRole('complementary', { name: 'Area guide' })).toBeNull());
    expect(screen.getByRole('region', { name: 'Interactive local map' })).toBeTruthy();
  });

  it('updates every dynamic raster in a connected scene without moving the viewport', () => {
    const dynamicSceneCatalog: Catalog = {
      ...connectedCatalog,
      mapScenes: connectedCatalog.mapScenes!.map(scene => ({
        ...scene,
        placements: scene.placements.map(placement => ({ ...placement, dynamicLighting: true })),
      })),
    };
    const view = render(<MapPage
      catalog={dynamicSceneCatalog}
      state={{ ...state, gameTime: { hours: null, minutes: null, phase: 'DAY', phaseProgress: null } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    const viewport = {
      scale: stage.dataset.scale,
      panX: stage.dataset.panX,
      panY: stage.dataset.panY,
      transform: view.container.querySelector<HTMLElement>('.map-plane')!.style.transform,
      placementStyles: [...view.container.querySelectorAll<HTMLElement>('.map-scene-tile')].map(image => image.style.cssText),
    };

    expect([...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'))).toEqual([
      '/api/maps/local%2F0010%2Fmap.png?lighting=DAY',
      '/api/maps/local%2F0011%2Fmap.png?lighting=DAY',
    ]);
    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();

    view.rerender(<MapPage
      catalog={dynamicSceneCatalog}
      state={{ ...state, gameTime: { hours: null, minutes: null, phase: 'NIGHT', phaseProgress: null } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect([...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'))).toEqual([
      '/api/maps/local%2F0010%2Fmap.png?lighting=NIGHT',
      '/api/maps/local%2F0011%2Fmap.png?lighting=NIGHT',
    ]);
    expect(stage.dataset.scale).toBe(viewport.scale);
    expect(stage.dataset.panX).toBe(viewport.panX);
    expect(stage.dataset.panY).toBe(viewport.panY);
    expect(view.container.querySelector<HTMLElement>('.map-plane')!.style.transform).toBe(viewport.transform);
    expect([...view.container.querySelectorAll<HTMLElement>('.map-scene-tile')].map(image => image.style.cssText))
      .toEqual(viewport.placementStyles);
    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();
  });

  it('changes only a dynamic Local image when game lighting changes and preserves zoom', () => {
    const dynamicCatalog: Catalog = {
      ...localCatalog,
      localMaps: localCatalog.localMaps!.map(map => ({ ...map, dynamicLighting: true })),
    };
    const view = render(<MapPage
      catalog={dynamicCatalog}
      state={{ ...state, gameTime: { hours: null, minutes: null, phase: 'DAY', phaseProgress: null } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    const stage = screen.getByRole('region', { name: 'Interactive local map' });
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    const zoomedScale = stage.dataset.scale;
    expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
      .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=DAY');

    view.rerender(<MapPage
      catalog={dynamicCatalog}
      state={{ ...state, gameTime: { hours: null, minutes: null, phase: 'NIGHT', phaseProgress: null } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
      .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=NIGHT');
    expect(stage.dataset.scale).toBe(zoomedScale);
    expect(view.container.querySelector('.header-game-time')?.textContent).toBe('Night');

    view.rerender(<MapPage
      catalog={dynamicCatalog}
      state={{ ...state, gameTime: null }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
      .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=DAY');
    expect(stage.dataset.scale).toBe(zoomedScale);
  });

  it('preserves the catalog query while appending timed lighting to every dynamic Local raster', () => {
    const dynamicCatalog: Catalog = {
      ...connectedCatalog,
      localMaps: connectedCatalog.localMaps!.map(map => ({
        ...map,
        imageUrl: `${map.imageUrl}?catalog=fixture-sha`,
        dynamicLighting: true,
      })),
      mapScenes: connectedCatalog.mapScenes!.map(scene => ({
        ...scene,
        placements: scene.placements.map(placement => ({
          ...placement,
          imageUrl: `${placement.imageUrl}?catalog=fixture-sha`,
          dynamicLighting: true,
        })),
      })),
    };
    const { container } = render(<MapPage
      catalog={dynamicCatalog}
      state={{ ...state, gameTime: { hours: 18, minutes: 37, phase: 'DAY', phaseProgress: 0.8 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    const sources = [...container.querySelectorAll<HTMLImageElement>('.map-scene-tile')].map(image => image.src);
    expect(sources).toHaveLength(2);
    for (const source of sources) {
      const query = new URL(source).searchParams;
      expect(query.get('catalog')).toBe('fixture-sha');
      expect(query.get('hour')).toBe('18');
      expect(query.get('minute')).toBe('37');
    }
  });

  it('uses numeric game time for a dynamic Gen III Local image', () => {
    const dynamicCatalog: Catalog = {
      ...localCatalog,
      localMaps: localCatalog.localMaps!.map(map => ({ ...map, dynamicLighting: true })),
    };
    const { container } = render(<MapPage
      catalog={dynamicCatalog}
      state={{ ...state, gameTime: { hours: 18, minutes: 37, phase: 'DAY', phaseProgress: 0.8 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(container.querySelector('.map-plane img')?.getAttribute('src'))
      .toBe('/api/maps/local%2F0010%2Fmap.png?hour=18&minute=37');
  });

  it('falls back to Atlas and disables the switch when the current local map is unavailable', () => {
    render(<MapPage
      catalog={localCatalog}
      state={{ ...state, currentAreaBaseId: 0x99, currentAreaName: 'Unknown' }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(screen.getByRole('region', { name: 'Interactive world map' }).dataset.mapMode).toBe('ATLAS');
    expect(screen.queryByRole('button', { name: 'Show Local map' })).toBeNull();
  });
});
