import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { SettingsPage } from './SettingsPage';

afterEach(cleanup);

describe('production settings copy', () => {
  it('explains automatic ruleset selection without POC or future-feature disclaimers', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.queryByText(/browser POC/i)).toBeNull();
    expect(screen.queryByText(/memory mapper will/i)).toBeNull();
    expect(screen.getByText(/Auto uses the ROM default/i)).toBeTruthy();
  });

  it('can replace the active ROM without exposing simulator controls', () => {
    const onUpload = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={onUpload} />);
    const rom = new File([new Uint8Array([1, 2, 3])], 'next.gba');

    fireEvent.change(screen.getByLabelText('Change ROM or ZIP'), { target: { files: [rom] } });

    expect(onUpload).toHaveBeenCalledWith(rom);
    expect(screen.queryByText('Encounter feed')).toBeNull();
    expect(screen.queryByText('GENERATE ENCOUNTER')).toBeNull();
  });
});

const catalog = {
  hash: 'sha', crc32: '1234ABCD', family: 'EMERALD', platform: 'GBA', species: [], moves: [], types: [], areas: [], balls: [], capabilities: {},
  rulesets: [
    { id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true },
    { id: 'modern', label: 'Modern', sourceOffset: 1, confidence: 0.9, primary: false },
  ],
} satisfies Catalog;

const state = {
  version: 1, screen: 'SETTINGS', priorScreen: 'POKEDEX', settingsReturnScreen: 'POKEDEX', selectedSpeciesId: null,
  filter: 'ALL', selectedAreaId: null, battleTab: 'ENTRY', speciesState: {}, observedMoves: {}, battle: null,
  catalogReady: true, catalogName: 'fixture.gba', error: null, activeRulesetId: null, rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  settings: { knowledgeMode: 'DISCOVERED', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
} satisfies State;
