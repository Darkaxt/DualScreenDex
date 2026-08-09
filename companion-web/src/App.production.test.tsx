import { render, screen, waitFor } from '@testing-library/preact';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bootstrap } from './models';

const fixture: Bootstrap = {
  catalog: {
    hash: 'sha', crc32: 'C3A9F204', family: 'EMERALD', platform: 'GBA', rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
    species: [], moves: [], types: [], areas: [], balls: [], capabilities: {}
  },
  state: {
    version: 1, screen: 'POKEDEX', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX',
    selectedSpeciesId: null, filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY',
    settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
    speciesState: {}, observedMoves: {}, battle: null, catalogReady: true,
    catalogName: 'Pokemon Modern Emerald.gba', error: null, activeRulesetId: null,
    rulesetAssumed: true, loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 }
  }
};

vi.mock('./gateway', () => ({
  bootstrap: vi.fn(async () => fixture),
  action: vi.fn(async () => fixture.state),
  events: vi.fn(() => () => undefined),
  uploadRom: vi.fn(async () => fixture)
}));

import { App } from './App';

describe('production application shell', () => {
  beforeEach(() => document.body.replaceChildren());

  it('keeps ROM identity visible without rendering simulator controls', async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByText('Pokemon Modern Emerald.gba')).toBeTruthy());
    expect(screen.getByText(/CRC32 C3A9F204/)).toBeTruthy();
    expect(screen.queryByText('Encounter feed')).toBeNull();
    expect(screen.queryByText('GENERATE ENCOUNTER')).toBeNull();
    expect(screen.queryByText(/Generate an encounter/i)).toBeNull();
  });
});
