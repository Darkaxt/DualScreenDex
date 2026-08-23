import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { PokedexBrowse } from './PokedexBrowse';

afterEach(cleanup);

const catalog: Catalog = {
  hash: 'fixture', crc32: '12345678', family: 'EMERALD', platform: 'GBA', rulesets: [], moves: [], types: [
    { id: 12, name: 'GRASS', foreground: '#ffffff', background: '#78c850', border: '#4e8234' },
    { id: 10, name: 'FIRE', foreground: '#ffffff', background: '#f08030', border: '#9c531f' },
  ], areas: [], balls: [], capabilities: {},
  species: [
    { id: 1, dex: 1, name: 'Bulbasaur', typeIds: [12], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
    { id: 4, dex: 4, name: 'Charmander', typeIds: [10], stats: null, description: null, height: null, weight: null, learnset: [], learnsets: {}, normalizedLearnsets: {}, moveAcquisitions: [], abilities: [], evolutions: [], hasSprite: false },
  ],
};

const state: State = {
  version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null,
  battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {
    1: { seen: true, caught: false, team: false, ballId: null },
    4: { seen: false, caught: false, team: false, ballId: null },
  },
  observedMoves: {},
  battle: null, catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
};

describe('Pokédex knowledge modes', () => {
  it('uses a title-only header without parser family or policy diagnostics', () => {
    const { container } = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByText('POKÉDEX')).toBeTruthy();
    expect(container.querySelector('.header-title small')).toBeNull();
    expect(screen.queryByText(/EMERALD|ORGANIC/)).toBeNull();
  });

  it('shows a compact owned versus found count for the current results', () => {
    const rendered = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByLabelText('0 owned, 1 found').textContent).toBe('0 / 1');

    rendered.rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      speciesState: {
        1: { ...state.speciesState[1], caught: true, team: true },
        4: { ...state.speciesState[4], seen: true },
      },
    }} send={vi.fn()} />);
    expect(screen.getByLabelText('1 owned, 2 found').textContent).toBe('1 / 2');

    rendered.rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      filter: 'CAUGHT',
      speciesState: {
        1: { ...state.speciesState[1], caught: true, team: true },
        4: { ...state.speciesState[4], seen: true },
      },
    }} send={vi.fn()} />);
    expect(screen.getByLabelText('1 caught').textContent).toBe('1');

    fireEvent.input(screen.getByPlaceholderText('NAME OR NUMBER'), { target: { value: 'Charmander' } });
    expect(screen.getByLabelText('0 caught').textContent).toBe('0');
  });

  it('omits search and the counter from the Team tab', () => {
    render(<PokedexBrowse catalog={catalog} state={{ ...state, filter: 'TEAM' }} send={vi.fn()} />);

    expect(screen.queryByPlaceholderText('NAME OR NUMBER')).toBeNull();
    expect(screen.queryByText(/\d+ \/ \d+/)).toBeNull();
  });

  it('places a semantic Map shortcut in the existing header only for normalized maps', () => {
    const onOpenMap = vi.fn();
    const mappedCatalog: Catalog = {
      ...catalog,
      worldMaps: [{
        key: 'gen3-region-0', displayName: 'Hoenn', pixelWidth: 224, pixelHeight: 120,
        gridWidth: 28, gridHeight: 15, imageUrl: '/api/maps/world%2Fgen3-region-0.png', locations: [],
      }],
    };

    const { rerender, container } = render(<PokedexBrowse catalog={mappedCatalog} state={state} send={vi.fn()} onOpenMap={onOpenMap} />);

    const map = screen.getByRole('button', { name: 'Open Map' });
    expect(map.closest('.app-header')).toBeTruthy();
    expect(map.querySelector('svg')?.dataset.semanticIcon).toBe('map');
    expect(container.querySelector('[data-map-navigation-row]')).toBeNull();
    fireEvent.click(map);
    expect(onOpenMap).toHaveBeenCalledOnce();

    rerender(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} onOpenMap={onOpenMap} />);
    expect(screen.queryByRole('button', { name: 'Open Map' })).toBeNull();
  });

  it('hides the redundant Seen filter only in Organic mode', () => {
    const { rerender } = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.queryByRole('button', { name: 'SEEN' })).toBeNull();

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    }} send={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'SEEN' })).toBeTruthy();

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'HIDDEN' },
    }} send={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'SEEN' })).toBeTruthy();
  });

  it('treats a persisted Seen filter as All after entering Organic mode', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      filter: 'SEEN',
      speciesState: {
        ...state.speciesState,
        4: { seen: false, caught: true, team: false, ballId: null },
      },
    }} send={vi.fn()} />);

    expect(screen.getByText('Charmander')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'ALL' }).classList.contains('active')).toBe(true);
  });

  it('keeps unseen species out of the Organic Pokédex', () => {
    render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.queryByText('Charmander')).toBeNull();
    expect(screen.queryByLabelText('Seen')).toBeNull();
    expect(screen.queryByLabelText('Not caught')).toBeNull();
  });

  it('shows only an affirmative Poké Ball for captured Organic entries', () => {
    const { container } = render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      speciesState: { ...state.speciesState, 1: { ...state.speciesState[1], caught: true } },
    }} send={vi.fn()} />);

    const caught = screen.getByLabelText('Caught');
    expect(caught.closest('.pokedex-avatar')).toBeTruthy();
    expect(caught.closest('.caught-avatar-badge')).toBeTruthy();
    expect(container.querySelector('.species-row-meta [aria-label="Caught"]')).toBeNull();
    expect(screen.queryByLabelText('Seen')).toBeNull();
  });

  it('does not reserve an avatar badge for an uncaught species', () => {
    const { container } = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(container.querySelector('.pokedex-avatar')).toBeTruthy();
    expect(container.querySelector('.caught-avatar-badge')).toBeNull();
    expect(screen.queryByLabelText('Not caught')).toBeNull();
  });

  it('allows full ROM navigation in Discovered mode', () => {
    const { container } = render(<PokedexBrowse catalog={catalog} state={{ ...state, settings: { ...state.settings, knowledgeMode: 'DISCOVERED' } }} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.getByText('Charmander')).toBeTruthy();
    expect(container.querySelectorAll('.species-row-identity')).toHaveLength(2);
    expect(container.querySelectorAll('.species-row-types .type-chip')).toHaveLength(2);
  });

  it('shows type metadata only after the active knowledge policy unlocks it', () => {
    const rendered = render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);
    expect(rendered.container.querySelector('.species-row-types')).toBeNull();

    rendered.rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      speciesState: { ...state.speciesState, 1: { ...state.speciesState[1], caught: true } },
    }} send={vi.fn()} />);
    expect(rendered.container.querySelector('.species-row-types .type-chip')?.textContent).toBe('GRASS');
  });

  it('lists only recruited species in Hidden mode', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      settings: { ...state.settings, knowledgeMode: 'HIDDEN' },
      speciesState: { ...state.speciesState, 1: { ...state.speciesState[1], caught: true } }
    }} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('disables save-backed filters when their capabilities are unavailable', () => {
    render(<PokedexBrowse catalog={catalog} state={state} send={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'TEAM' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'AREA' }).hasAttribute('disabled')).toBe(true);
  });

  it('gates Team and Area independently', () => {
    const { rerender } = render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      saveRam: {
        status: 'MATCHED', sourceName: 'fixture.srm', sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'VERIFIED', capabilities: { PARTY: 'AVAILABLE', SPECIES: 'AVAILABLE' }, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'TEAM' }) as HTMLButtonElement).disabled).toBe(false);
    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(true);

    rerender(<PokedexBrowse catalog={catalog} state={{
      ...state,
      currentAreaIds: [1],
      saveRam: {
        status: 'MATCHED', sourceName: 'fixture.srm', sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'VERIFIED', capabilities: { CURRENT_AREA: 'AVAILABLE' }, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'TEAM' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('enables Area from resolved live memory without SaveRAM capability or an active battle', () => {
    render(<PokedexBrowse catalog={catalog} state={{
      ...state,
      currentAreaIds: [1],
      saveRam: {
        status: 'NOT_FOUND', sourceName: null, sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
        autosaveStatus: 'UNKNOWN', capabilities: {}, candidates: [], message: null,
      },
    }} send={vi.fn()} />);

    expect((screen.getByRole('button', { name: 'AREA' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows ROM-derived day and night markers only while the Area filter is active', () => {
    const windowCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 10, name: 'Route 1', methodId: 1, speciesIds: [1], windows: ['DAY'], slots: [] },
        { id: 11, name: 'Route 1', methodId: 2, speciesIds: [4], windows: ['NIGHT'], slots: [] },
      ],
    };
    const areaState: State = {
      ...state,
      filter: 'AREA',
      currentAreaIds: [10, 11],
      currentAreaSpeciesIds: [1, 4],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    };

    const { rerender } = render(<PokedexBrowse catalog={windowCatalog} state={areaState} send={vi.fn()} />);

    expect(screen.getByLabelText('Day encounter')).toBeTruthy();
    expect(screen.getByLabelText('Night encounter')).toBeTruthy();

    rerender(<PokedexBrowse catalog={windowCatalog} state={{ ...areaState, filter: 'ALL' }} send={vi.fn()} />);
    expect(screen.queryByLabelText('Day encounter')).toBeNull();
    expect(screen.queryByLabelText('Night encounter')).toBeNull();
  });

  it('shows no marker when a species is available both day and night or without a time restriction', () => {
    const windowCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 20, name: 'Route 2', methodId: 1, speciesIds: [1], windows: ['DAY', 'NIGHT'], slots: [] },
        { id: 21, name: 'Route 2 water', methodId: 2, speciesIds: [4], windows: ['ANY'], slots: [] },
      ],
    };
    render(<PokedexBrowse catalog={windowCatalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaIds: [20, 21],
      currentAreaSpeciesIds: [1, 4],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
    }} send={vi.fn()} />);

    expect(screen.queryByTestId('encounter-window-icon')).toBeNull();
  });

  it('shows the parsed roster in Discovered mode', () => {
    const areaCatalog: Catalog = {
      ...catalog,
      areas: [
        { id: 10, name: 'Route 1', methodId: 1, speciesIds: [1, 4], windows: ['ANY'], slots: [] },
      ],
    };
    const areaState: State = {
      ...state,
      filter: 'AREA',
      currentAreaIds: [10],
      currentAreaSpeciesIds: [1],
      settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
      speciesState: {
        1: { seen: true, caught: false, team: false, ballId: null },
        4: { seen: true, caught: false, team: false, ballId: null },
      },
    };

    const { rerender } = render(<PokedexBrowse catalog={areaCatalog} state={areaState} send={vi.fn()} />);

    expect(screen.getByText('Bulbasaur')).toBeTruthy();
    expect(screen.getByText('Charmander')).toBeTruthy();

    rerender(<PokedexBrowse catalog={areaCatalog} state={{
      ...areaState,
      speciesState: { ...areaState.speciesState, 4: { ...areaState.speciesState[4], caught: true } },
    }} send={vi.fn()} />);
    expect(screen.getByText('Charmander')).toBeTruthy();
  });

  it('orders known Area encounters first and masks unseen parsed encounters without inserting caught gifts', () => {
    const areaCatalog: Catalog = {
      ...catalog,
      species: [
        catalog.species[0],
        { ...catalog.species[1], hasSprite: true },
        { ...catalog.species[0], id: 7, dex: 7, name: 'Squirtle', hasSprite: true },
        { ...catalog.species[0], id: 252, dex: 252, name: 'Treecko', hasSprite: true },
      ],
      areas: [
        { id: 10, name: 'Route 1', methodId: 1, speciesIds: [1, 4, 7], windows: ['NIGHT'], slots: [] },
      ],
    };
    const { container } = render(<PokedexBrowse catalog={areaCatalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaIds: [10],
      currentAreaSpeciesIds: [1],
      speciesState: {
        1: { seen: true, caught: false, team: false, ballId: null },
        4: { seen: false, caught: false, team: false, ballId: null },
        7: { seen: false, caught: true, team: false, ballId: null },
        252: { seen: false, caught: true, team: false, ballId: null },
      },
    }} send={vi.fn()} />);

    expect(Array.from(container.querySelectorAll('.species-row strong')).map(node => node.textContent)).toEqual([
      'Bulbasaur',
      'Squirtle',
      '??????????',
    ]);
    expect(screen.getByText('#???')).toBeTruthy();
    expect((screen.getByLabelText('Unidentified encounter') as HTMLButtonElement).disabled).toBe(true);
    expect(container.querySelector('.identity-silhouette')).toBeTruthy();
    expect(screen.queryByText('Treecko')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Open Map' })).toBeNull();

    fireEvent.input(screen.getByPlaceholderText('NAME OR NUMBER'), { target: { value: 'Charmander' } });
    expect(screen.queryByText('??????????')).toBeNull();
    expect(screen.queryByText('Charmander')).toBeNull();
  });

  it('uses the same unknown seen and captured avatar states as evolution rows', () => {
    const send = vi.fn();
    const identityCatalog: Catalog = {
      ...catalog,
      species: [
        { ...catalog.species[0], hasSprite: true },
        { ...catalog.species[1], hasSprite: true },
        { ...catalog.species[0], id: 7, dex: 7, name: 'Squirtle', hasSprite: true },
      ],
      areas: [{ id: 10, name: 'Route 1', methodId: 1, speciesIds: [1, 4, 7], windows: ['ANY'], slots: [] }],
    };
    const { container } = render(<PokedexBrowse catalog={identityCatalog} state={{
      ...state,
      filter: 'AREA',
      currentAreaIds: [10],
      speciesState: {
        1: { seen: true, caught: false, team: false, ballId: null },
        4: { seen: false, caught: false, team: false, ballId: null },
        7: { seen: false, caught: true, team: false, ballId: null },
      },
    }} send={send} />);

    expect(screen.getByAltText('Bulbasaur sprite').classList.contains('identity-seen')).toBe(true);
    expect(screen.getByAltText('Unidentified Pokémon').classList.contains('identity-silhouette')).toBe(true);
    expect(screen.getByText('??????????')).toBeTruthy();
    const captured = screen.getByAltText('Squirtle sprite');
    expect(captured.classList.contains('identity-seen')).toBe(false);
    expect(captured.classList.contains('identity-silhouette')).toBe(false);

    fireEvent.click(screen.getByText('Bulbasaur').closest('button')!);
    fireEvent.click(screen.getByText('Squirtle').closest('button')!);
    expect(send).toHaveBeenNthCalledWith(1, 'OPEN_SPECIES', { speciesId: 1 });
    expect(send).toHaveBeenNthCalledWith(2, 'OPEN_SPECIES', { speciesId: 7 });
    expect((container.querySelector('.identity-hidden') as HTMLButtonElement).disabled).toBe(true);
  });

  it('keeps a nine-hundred-entry catalog within sixty mounted rows while preserving counts and search', () => {
    const species = Array.from({ length: 900 }, (_, index) => ({
      ...catalog.species[0],
      id: index + 1,
      dex: index + 1,
      name: `MON ${String(index + 1).padStart(4, '0')}`,
      hasSprite: true,
    }));
    const speciesState = Object.fromEntries(species.slice(0, 100).map((entry, index) => [
      entry.id,
      { seen: true, caught: index < 10, team: false, ballId: null },
    ]));
    const rendered = render(<PokedexBrowse
      catalog={{ ...catalog, species }}
      state={{
        ...state,
        settings: { ...state.settings, knowledgeMode: 'DISCOVERED' },
        speciesState,
      }}
      send={vi.fn()}
    />);

    expect(rendered.container.querySelectorAll('.species-row').length).toBeLessThanOrEqual(60);
    expect(screen.getByLabelText('10 owned, 100 found').textContent).toBe('10 / 100');
    expect([...rendered.container.querySelectorAll<HTMLImageElement>('.species-row img')]
      .every(image => image.getAttribute('loading') === 'lazy' && image.getAttribute('decoding') === 'async')).toBe(true);

    const list = rendered.container.querySelector<HTMLElement>('.species-list')!;
    Object.defineProperty(list, 'clientHeight', { configurable: true, value: 564 });
    Object.defineProperty(list, 'scrollTop', { configurable: true, value: 94 * 100, writable: true });
    fireEvent.scroll(list);
    expect(rendered.container.querySelectorAll('.species-row').length).toBeLessThanOrEqual(60);
    expect(screen.queryByText('MON 0001')).toBeNull();

    fireEvent.input(screen.getByPlaceholderText('NAME OR NUMBER'), { target: { value: 'MON 0899' } });
    expect(rendered.container.querySelectorAll('.species-row')).toHaveLength(1);
    expect(screen.getByText('MON 0899')).toBeTruthy();
  });
});
