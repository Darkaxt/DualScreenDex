import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AreaGuideAreaView } from '../models';
import { AreaGuideDrawer } from './AreaGuideDrawer';

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
      { speciesId: 261, name: 'Poochyena', minimumLevel: 2, maximumLevel: 3, ratePercent: 60 },
      { speciesId: 263, name: 'Zigzagoon', minimumLevel: 2, maximumLevel: 4, ratePercent: null },
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
    render(<AreaGuideDrawer
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

    fireEvent.click(screen.getByRole('button', { name: 'Show Your House on map' }));
    expect(selectPoint).toHaveBeenCalledWith('house');
    fireEvent.click(screen.getByRole('button', { name: 'Open Oldale Town guide' }));
    expect(selectArea).toHaveBeenCalledWith(0x11);
    fireEvent.click(screen.getByRole('button', { name: 'Close area guide' }));
    expect(close).toHaveBeenCalledOnce();
  });

  it('windows long encounter lists instead of retaining every row', () => {
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
        })),
      }],
      placesAndServices: [],
      items: [],
    };
    const { container } = render(<AreaGuideDrawer area={longArea} onClose={vi.fn()} />);

    const list = screen.getByRole('list', { name: 'Wild Pokémon' });
    expect(list.dataset.totalItems).toBe('80');
    expect(container.querySelectorAll('.area-guide-encounter-row').length).toBeLessThan(80);
    expect(screen.getByText('Species 1')).toBeTruthy();

    Object.defineProperty(list, 'scrollTop', { configurable: true, value: 1200 });
    fireEvent.scroll(list);
    expect(screen.queryByText('Species 1')).toBeNull();
    expect(container.querySelectorAll('.area-guide-encounter-row').length).toBeLessThan(80);
  });
});
