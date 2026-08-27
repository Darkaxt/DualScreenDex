import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
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
