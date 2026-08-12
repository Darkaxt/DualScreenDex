import { fireEvent, render, screen, waitFor } from '@testing-library/preact';
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
  action: vi.fn(async (type: string, values: Record<string, unknown> = {}) => ({ ...fixture.state, screen: type === 'SCREEN' ? values.screen : fixture.state.screen })),
  events: vi.fn(() => () => undefined),
  uploadRom: vi.fn(async () => fixture),
  diagnostics: vi.fn(async () => ({
    romName: fixture.state.catalogName, sha256: fixture.catalog!.hash, crc32: fixture.catalog!.crc32,
    family: fixture.catalog!.family, platform: fixture.catalog!.platform, activeRulesetId: 'default', rulesetAssumed: true,
    rulesets: fixture.catalog!.rulesets, capabilities: [], parserDiagnostics: [], species: null, move: null,
  }))
}));

import { App, catalogRefreshMarker, loadingPercentage } from './App';

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

  it('reports only truthful committed loading progress', () => {
    expect(loadingPercentage({ active: true, phase: 'RELATIONSHIPS', completedUnits: 3, totalUnits: 5 })).toBe(60);
    expect(loadingPercentage({ active: true, phase: 'IDENTIFYING', completedUnits: 0, totalUnits: 5 })).toBe(0);
    expect(loadingPercentage({ active: true, phase: 'IDLE', completedUnits: 0, totalUnits: 0 })).toBeNull();
  });

  it('opens the capability report from Settings without opening memory capture', async () => {
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
    fireEvent.click(await screen.findByRole('button', { name: 'CAPABILITY REPORT' }));

    expect(await screen.findByText('LOADED ROM · READ ONLY')).toBeTruthy();
    expect(screen.queryByText('ISSUE REPORT MEMORY CAPTURE')).toBeNull();
  });
});

describe('catalog refresh marker', () => {
  it('remains stable across ordinary state versions after one catalog phase', () => {
    const loading = { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 };
    expect(catalogRefreshMarker({ catalogName: 'game.gba', loading })).toBe(
      catalogRefreshMarker({ catalogName: 'game.gba', loading }),
    );
  });
});
