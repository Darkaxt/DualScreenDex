import { useState } from 'preact/hooks';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, SpecimenCollectionView } from '../models';
import { SpecimensPage } from './SpecimensPage';

afterEach(cleanup);

const collection: SpecimenCollectionView = {
  version: 7,
  speciesId: 25,
  speciesName: 'PIKACHU',
  specimens: [{
    key: 'individual:1',
    location: { kind: 'PARTY', label: 'Party · Slot 1', boxNumber: null, slotNumber: 1 },
    speciesId: 25, formId: null, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13],
    nickname: 'SPARK', level: 18, isEgg: false, gender: 'FEMALE', natureId: 3, nature: 'Adamant', abilityId: 9,
    abilityName: 'Static', heldItemId: 12, hasHeldItem: true, currentHp: 31, maximumHp: 45, status: 'PAR',
    experienceProgress: .5, rarity: { relativeTier: null, innateTier: 'ELITE', baseStars: 4, areaAdjustment: null, stars: 4 },
    stats: { HP: 45, ATTACK: 28 }, moves: [{ slot: 0, moveId: 85, name: 'Thunderbolt', currentPp: 12, maximumPp: 15 }],
    ivs: [31, 30, 29, 28, 27, 26], dvs: [],
  }, {
    key: 'individual:2',
    location: { kind: 'BOX', label: 'Box 2 · Slot 2', boxNumber: 2, slotNumber: 2 },
    speciesId: 25, formId: null, speciesName: 'PIKACHU', spriteUrl: '/api/sprites/species/25.png', typeIds: [13],
    nickname: 'VOLT', level: 12, isEgg: false, gender: 'MALE', natureId: null, nature: null, abilityId: null,
    abilityName: null, heldItemId: null, hasHeldItem: false, currentHp: null, maximumHp: null, status: null,
    experienceProgress: .2, rarity: null, stats: {}, moves: [], ivs: [12, 13, 14, 15, 16, 17], dvs: [],
  }],
};

describe('Pokédex specimens', () => {
  it('lists Party and PC instances and opens the shared individual detail without diagnostic copy', async () => {
    const openDetail = vi.fn();
    const load = vi.fn().mockResolvedValue(collection);
    const rendered = render(<SpecimensPage
      catalog={catalog}
      speciesId={25}
      stateVersion={7}
      detailKey={null}
      onBack={vi.fn()}
      onOpenDetail={openDetail}
      onCloseDetail={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openNature={vi.fn()}
      openSpecies={vi.fn()}
      load={load}
    />);

    expect(await screen.findByRole('button', { name: 'Open SPARK details' })).toBeTruthy();
    expect(screen.getByText('Party · Slot 1')).toBeTruthy();
    expect(screen.getByText('Box 2 · Slot 2')).toBeTruthy();
    expect(screen.queryByText(/address|pointer|decoder|recovery|live source/i)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Open SPARK details' }));
    expect(openDetail).toHaveBeenCalledWith('individual:1');

    rendered.rerender(<SpecimensPage
      catalog={catalog}
      speciesId={25}
      stateVersion={7}
      detailKey="individual:1"
      onBack={vi.fn()}
      onOpenDetail={openDetail}
      onCloseDetail={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openNature={vi.fn()}
      openSpecies={vi.fn()}
      load={load}
    />);
    await waitFor(() => expect(screen.getByRole('dialog', { name: 'SPARK details' })).toBeTruthy());
    expect(document.querySelector('.owned-individual-detail')).toBeTruthy();
    expect(screen.getByText('IVs')).toBeTruthy();
    expect(screen.getByText('31 / 30 / 29 / 28 / 27 / 26')).toBeTruthy();
  });

  it('renders no specimen card when the endpoint returns no decoded record', async () => {
    render(<SpecimensPage
      catalog={catalog}
      speciesId={25}
      stateVersion={8}
      detailKey={null}
      onBack={vi.fn()}
      onOpenDetail={vi.fn()}
      onCloseDetail={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openNature={vi.fn()}
      openSpecies={vi.fn()}
      load={vi.fn().mockResolvedValue({ ...collection, version: 8, specimens: [] })}
    />);

    expect(await screen.findByText('NO SPECIMENS AVAILABLE')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /details/i })).toBeNull();
  });

  it('keeps scrolled first, middle, and last card dialogs in the screen host and restores each trigger', async () => {
    const longCollection = {
      ...collection,
      specimens: Array.from({ length: 9 }, (_, index) => ({
        ...collection.specimens[index % collection.specimens.length],
        key: `individual:${index}`,
        nickname: `SPECIMEN ${index}`,
      })),
    };
    const load = vi.fn().mockResolvedValue(longCollection);
    function Harness() {
      const [detailKey, setDetailKey] = useState<string | null>(null);
      return <SpecimensPage
        catalog={catalog}
        speciesId={25}
        stateVersion={10}
        detailKey={detailKey}
        onBack={vi.fn()}
        onOpenDetail={setDetailKey}
        onCloseDetail={() => setDetailKey(null)}
        openMove={vi.fn()}
        openAbility={vi.fn()}
        openNature={vi.fn()}
        openSpecies={vi.fn()}
        initialScrollTop={140}
        load={load}
      />;
    }
    const { container } = render(<Harness />);
    await screen.findByRole('button', { name: 'Open SPECIMEN 8 details' });
    const scrollRegion = container.querySelector<HTMLElement>('.specimens-content')!;
    await waitFor(() => expect(scrollRegion.scrollTop).toBe(140));

    for (const index of [0, 4, 8]) {
      const trigger = screen.getByRole('button', { name: `Open SPECIMEN ${index} details` });
      fireEvent.click(trigger);
      const dialog = screen.getByRole('dialog', { name: `SPECIMEN ${index} details` });
      expect(dialog.closest('.screen')?.lastElementChild).toBe(dialog.parentElement);
      fireEvent.click(screen.getByRole('button', { name: `Close SPECIMEN ${index} details` }));
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(document.activeElement).toBe(trigger);
      expect(scrollRegion.scrollTop).toBe(140);
    }
  });

  it('keeps an active dialog and its focus stable during a live collection refresh', async () => {
    let finishRefresh!: (value: SpecimenCollectionView) => void;
    const refresh = new Promise<SpecimenCollectionView>(resolve => { finishRefresh = resolve; });
    const load = vi.fn()
      .mockResolvedValueOnce(collection)
      .mockReturnValueOnce(refresh);
    const props = {
      catalog,
      speciesId: 25,
      detailKey: 'individual:1',
      onBack: vi.fn(),
      onOpenDetail: vi.fn(),
      onCloseDetail: vi.fn(),
      openMove: vi.fn(),
      openAbility: vi.fn(),
      openNature: vi.fn(),
      openSpecies: vi.fn(),
      load,
    };
    const rendered = render(<SpecimensPage {...props} stateVersion={7} />);
    const dialog = await screen.findByRole('dialog', { name: 'SPARK details' });
    const close = screen.getByRole('button', { name: 'Close SPARK details' });
    close.focus();

    rendered.rerender(<SpecimensPage {...props} stateVersion={8} />);
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('dialog', { name: 'SPARK details' })).toBe(dialog);
    expect(document.activeElement).toBe(close);

    await act(async () => {
      finishRefresh({ ...collection, version: 8 });
      await refresh;
    });
    expect(screen.getByRole('dialog', { name: 'SPARK details' })).toBe(dialog);
    expect(document.activeElement).toBe(close);
  });

  it('bounds a stalled request and retries without changing the active species', async () => {
    vi.useFakeTimers();
    const load = vi.fn()
      .mockReturnValueOnce(new Promise<SpecimenCollectionView>(() => undefined))
      .mockResolvedValueOnce(collection);
    try {
      render(<SpecimensPage
        catalog={catalog}
        speciesId={25}
        stateVersion={8}
        detailKey={null}
        onBack={vi.fn()}
        onOpenDetail={vi.fn()}
        onCloseDetail={vi.fn()}
        openMove={vi.fn()}
        openAbility={vi.fn()}
        openNature={vi.fn()}
        openSpecies={vi.fn()}
        load={load}
        requestTimeoutMillis={25}
      />);

      await act(async () => { await vi.advanceTimersByTimeAsync(25); });
      expect(screen.getByRole('alert').textContent).toContain('took too long');
      vi.useRealTimers();
      fireEvent.click(screen.getByRole('button', { name: 'RETRY' }));

      expect(await screen.findByRole('button', { name: 'Open SPARK details' })).toBeTruthy();
      expect(load).toHaveBeenNthCalledWith(1, 25);
      expect(load).toHaveBeenNthCalledWith(2, 25);
    } finally {
      vi.useRealTimers();
    }
  });

  it('restores and reports the species-list scroll position', async () => {
    const onScrollTopChange = vi.fn();
    render(<SpecimensPage
      catalog={catalog}
      speciesId={25}
      stateVersion={9}
      detailKey={null}
      onBack={vi.fn()}
      onOpenDetail={vi.fn()}
      onCloseDetail={vi.fn()}
      openMove={vi.fn()}
      openAbility={vi.fn()}
      openNature={vi.fn()}
      openSpecies={vi.fn()}
      initialScrollTop={73}
      onScrollTopChange={onScrollTopChange}
      load={vi.fn().mockResolvedValue({ ...collection, version: 9 })}
    />);

    await screen.findByRole('button', { name: 'Open SPARK details' });
    const scrollRegion = document.querySelector<HTMLElement>('[data-scroll-region]');
    expect(scrollRegion).toBeTruthy();
    await waitFor(() => expect(scrollRegion!.scrollTop).toBe(73));

    scrollRegion!.scrollTop = 119;
    fireEvent.scroll(scrollRegion!);
    expect(onScrollTopChange).toHaveBeenLastCalledWith(119);
  });
});

const catalog = {
  hash: 'sha', crc32: '1234', family: 'EMERALD', platform: 'GBA', rulesets: [], areas: [], balls: [], capabilities: {},
  species: [], moves: [{ id: 85, name: 'Thunderbolt', typeId: 13, category: 'SPECIAL', power: 90, accuracy: 100, pp: 15, priority: 0, effectId: null, description: null }],
  types: [{ id: 13, name: 'ELECTRIC', foreground: '#251f00', background: '#f2d342', border: '#8b7616' }],
  natures: [{ id: 3, name: 'Adamant', statMultipliers: { ATTACK: 110, DEFENSE: 100, SPEED: 100, SPECIAL_ATTACK: 90, SPECIAL_DEFENSE: 100 }, raisedStat: 'ATTACK', loweredStat: 'SPECIAL_ATTACK', positivePercent: 110, negativePercent: 90, likedFlavor: 'SPICY', dislikedFlavor: 'DRY' }],
} satisfies Catalog;
