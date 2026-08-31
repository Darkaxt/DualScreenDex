import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AreaGuideAreaView } from '../models';
import { AreaGuideDrawer, projectAreaGuideExits } from './AreaGuideDrawer';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const area: AreaGuideAreaView = {
  baseAreaId: 0x10,
  name: 'Route 101',
  overview: {
    knownPointCount: 2,
    totalPointCount: null,
    collectedItemCount: 0,
    exits: [{ baseAreaId: 0x11, name: 'Oldale Town' }],
  },
  encounters: [{
    name: 'Grass', windows: ['DAY', 'NIGHT'], species: [
      { speciesId: 261, name: 'Poochyena', minimumLevel: 2, maximumLevel: 3, ratePercent: 60, hasSprite: true },
      { speciesId: 263, name: 'Zigzagoon', minimumLevel: 2, maximumLevel: 4, ratePercent: null, hasSprite: false },
    ],
  }],
  placesAndServices: [{
    key: 'house', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 4, tileY: 4,
    category: 'SERVICE', state: 'IDENTIFIED', label: 'Your House', service: 'BUILDING', itemId: null,
    destinationBaseAreaId: 0x11,
  }],
  trainersAndPeople: [],
  items: [{
    key: 'hidden-item', localMapKey: 'local/0010', baseAreaId: 0x10, tileX: 6, tileY: 5,
    category: 'AVAILABLE_ITEM', state: 'SILHOUETTE', label: null, service: null, itemId: null,
    destinationBaseAreaId: null,
  }],
  objectives: [],
};

describe('AreaGuideDrawer', () => {
  it('renders only supported player-facing sections and invokes knowledge-visible actions', () => {
    const close = vi.fn();
    const selectPoint = vi.fn();
    const selectArea = vi.fn();
    const { container } = render(<AreaGuideDrawer
      area={area}
      onClose={close}
      onSelectPoint={selectPoint}
      onSelectArea={selectArea}
      selectablePointKeys={new Set(['house'])}
    />);

    expect(screen.getByRole('complementary', { name: 'Area guide' })).toBeTruthy();
    expect(screen.getByText('OVERVIEW')).toBeTruthy();
    expect(screen.getByText('ENCOUNTERS')).toBeTruthy();
    expect(screen.getByText('PLACES & SERVICES')).toBeTruthy();
    expect(screen.getByText('ITEMS')).toBeTruthy();
    expect(screen.queryByText('TRAINERS & PEOPLE')).toBeNull();
    expect(screen.queryByText('OBJECTIVES')).toBeNull();
    expect(screen.queryByText('Place')).toBeNull();
    expect(screen.getByText('Lv. 2–3 · 60%')).toBeTruthy();
    expect(screen.getByText('Lv. 2–4')).toBeTruthy();
    expect(container.querySelectorAll('.area-guide-encounter-row img')).toHaveLength(1);
    expect(container.querySelector('img')?.getAttribute('src')).toBe('/api/sprites/species/261.png');
    expect(container.querySelectorAll('.area-guide-sprite-unavailable')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'Show Your House on map' }));
    expect(selectPoint).toHaveBeenCalledWith('house');
    fireEvent.click(screen.getByRole('button', { name: 'Open Oldale Town guide' }));
    expect(selectArea).toHaveBeenCalledWith(0x11);
    fireEvent.click(screen.getByRole('button', { name: 'Close area guide' }));
    expect(close).toHaveBeenCalledOnce();
  });

  it('windows long encounter lists from the one outer scroll owner', () => {
    const longArea: AreaGuideAreaView = {
      ...area,
      encounters: [{
        name: null,
        windows: ['ANY'],
        species: Array.from({ length: 80 }, (_, index) => ({
          speciesId: index + 1,
          name: `Species ${index + 1}`,
          minimumLevel: 2,
          maximumLevel: 4,
          ratePercent: 1,
          hasSprite: false,
        })),
      }],
      placesAndServices: [],
      items: [],
      objectives: [{ key: 'final-objective', title: 'Final objective' }],
    };
    const { container } = render(<AreaGuideDrawer area={longArea} onClose={vi.fn()} />);

    const content = container.querySelector<HTMLElement>('.area-guide-content')!;
    const list = screen.getByRole('list', { name: 'Wild Pokémon' });
    expect(list.dataset.totalItems).toBe('80');
    expect(list.classList.contains('is-windowed')).toBe(true);
    expect(container.querySelectorAll('.area-guide-encounter-row').length).toBeLessThan(80);
    expect(screen.getByText('Species 1')).toBeTruthy();

    Object.defineProperty(content, 'scrollTop', { configurable: true, value: 1200 });
    fireEvent.scroll(content);
    expect(screen.queryByText('Species 1')).toBeNull();
    expect(container.querySelectorAll('.area-guide-encounter-row').length).toBeLessThan(80);

    Object.defineProperty(content, 'scrollTop', { configurable: true, value: 4200 });
    fireEvent.scroll(content);
    expect(screen.getByText('Species 80')).toBeTruthy();
    expect(screen.getByText('Final objective')).toBeTruthy();
  });

  it('does not clip six or seven row sections and resets outer scroll when the area changes', () => {
    const sevenRows: AreaGuideAreaView = {
      ...area,
      encounters: [{
        name: null,
        windows: ['ANY'],
        species: Array.from({ length: 7 }, (_, index) => ({
          speciesId: index + 1,
          name: `Species ${index + 1}`,
          minimumLevel: 2,
          maximumLevel: 4,
          ratePercent: 1,
          hasSprite: false,
        })),
      }],
      placesAndServices: [],
      items: [],
    };
    const view = render(<AreaGuideDrawer area={sevenRows} onClose={vi.fn()} />);
    const content = view.container.querySelector<HTMLElement>('.area-guide-content')!;
    const list = screen.getByRole('list', { name: 'Wild Pokémon' });

    expect(screen.getByText('Species 7')).toBeTruthy();
    expect(list.style.maxHeight).toBe('');
    Object.defineProperty(content, 'scrollTop', { configurable: true, writable: true, value: 420 });
    fireEvent.scroll(content);
    view.rerender(<AreaGuideDrawer area={{ ...sevenRows, baseAreaId: 0x11, name: 'Oldale Town' }} onClose={vi.fn()} />);
    expect(content.scrollTop).toBe(0);
  });

  it('groups repeated destinations and differentiates equal visible names without changing destination ids', () => {
    expect(projectAreaGuideExits([
      { baseAreaId: 0x10, name: 'Route 101', count: 2 },
      { baseAreaId: 0x10, name: 'Route 101', count: 1 },
      { baseAreaId: 0x11, name: 'Oldale Town', count: 1 },
      { baseAreaId: 0x12, name: 'Oldale Town', count: 1 },
    ])).toEqual([
      { key: 'exit/16', baseAreaId: 0x10, label: 'Route 101 · 3 EXITS', ariaLabel: 'Open Route 101 guide, 3 exits' },
      { key: 'exit/17', baseAreaId: 0x11, label: 'Oldale Town · EXIT 1 OF 2', ariaLabel: 'Open Oldale Town guide, exit 1 of 2' },
      { key: 'exit/18', baseAreaId: 0x12, label: 'Oldale Town · EXIT 2 OF 2', ariaLabel: 'Open Oldale Town guide, exit 2 of 2' },
    ]);
  });

  it('shows only supplied knowledge-safe objectives', () => {
    render(<AreaGuideDrawer
      area={{ ...area, objectives: [{ key: 'open-road', title: 'Open Road' }] }}
      onClose={vi.fn()}
    />);

    expect(screen.getByText('OBJECTIVES')).toBeTruthy();
    expect(screen.getByText('Open Road')).toBeTruthy();
  });
});
