import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { MapPage } from './MapPage';

afterEach(cleanup);

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
    expect(container.querySelector('.map-player-marker')?.getAttribute('aria-label')).toBe('Player position 12, 7');
    expect(container.querySelector('.map-player-marker')?.classList.contains('atlas-location-marker')).toBe(false);
    expect(screen.queryByRole('button', { name: 'Show Atlas' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Show Local map' })).toBeNull();
  });

  it('uses the ROM-derived trainer avatar for the live player marker', () => {
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
    expect(marker?.querySelector('img')?.getAttribute('src')).toBe('/api/trainer-assets/trainer%2Favatar%2Ffemale.png');
    expect(marker?.querySelector('img')?.getAttribute('alt')).toBe('May');
    expect(marker?.querySelector('.map-player-dot')).toBeNull();
    expect((marker as HTMLElement).style.width).toBe('64px');
    expect((marker as HTMLElement).style.height).toBe('64px');
    for (let index = 0; index < 12; index += 1) fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(Number(screen.getByRole('region', { name: 'Interactive local map' }).dataset.effectiveRasterScale)).toBeCloseTo(4, 8);
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

  it('does not load undiscovered Local rasters or an Atlas underlay in Organic mode', () => {
    const view = render(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, revealedAreaBaseIds: [] }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();
    const images = view.container.querySelectorAll('.map-scene-tile');
    expect(images).toHaveLength(1);
    expect(images[0].getAttribute('data-local-map-key')).toBe('local/0010');
    expect(view.container.querySelector('img[data-local-map-key="local/0011"]')).toBeNull();
    const fog = view.container.querySelectorAll('.map-scene-placement-fog');
    expect(fog).toHaveLength(1);
    expect(fog[0].getAttribute('data-local-map-key')).toBe('local/0011');
    expect([...view.container.querySelectorAll('.map-local-poi-label')].map(label => label.textContent)).toEqual(['Route 101']);

    view.rerender(<MapPage
      catalog={connectedCatalog}
      state={{ ...state, settings: { ...state.settings, knowledgeMode: 'DISCOVERED' } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect(view.container.querySelectorAll('.map-scene-tile')).toHaveLength(2);
    expect(view.container.querySelectorAll('.map-scene-placement-fog')).toHaveLength(0);
    expect(view.container.querySelector('.map-scene-atlas-fallback')).toBeNull();
    expect([...view.container.querySelectorAll('.map-local-poi-label')].map(label => label.textContent)).toEqual([
      'Route 101',
      'Oldale Town',
    ]);
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

  it('updates every dynamic raster in a connected scene from one game clock', () => {
    const dynamicSceneCatalog: Catalog = {
      ...connectedCatalog,
      mapScenes: connectedCatalog.mapScenes!.map(scene => ({
        ...scene,
        placements: scene.placements.map(placement => ({ ...placement, dynamicLighting: true })),
      })),
    };
    const view = render(<MapPage
      catalog={dynamicSceneCatalog}
      state={{ ...state, gameTime: { hours: 18, minutes: 37, phase: 'DAY', phaseProgress: 0.8 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);

    expect([...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'))).toEqual([
      '/api/maps/local%2F0010%2Fmap.png?hour=18&minute=37',
      '/api/maps/local%2F0011%2Fmap.png?hour=18&minute=37',
    ]);

    view.rerender(<MapPage
      catalog={dynamicSceneCatalog}
      state={{ ...state, gameTime: { hours: 20, minutes: 4, phase: 'NIGHT', phaseProgress: 0.1 } }}
      onOpenPokedex={vi.fn()}
      onOpenSettings={vi.fn()}
    />);
    expect([...view.container.querySelectorAll('.map-scene-tile')].map(image => image.getAttribute('src'))).toEqual([
      '/api/maps/local%2F0010%2Fmap.png?hour=20&minute=4',
      '/api/maps/local%2F0011%2Fmap.png?hour=20&minute=4',
    ]);
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
