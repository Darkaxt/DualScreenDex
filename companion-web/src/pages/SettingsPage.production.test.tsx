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

  it('opens the RetroArch connection wizard from production settings', () => {
    const send = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={send} onUpload={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'RETROARCH SETUP' }));

    expect(send).toHaveBeenCalledWith('SCREEN', { screen: 'SETUP' });
  });

  it('offers docked and overlay display modes without enabling overlay automatically', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.getByRole('link', { name: 'DOCKED' }).getAttribute('href')).toBe('dualdex://overlay/dock');
    expect(screen.getByRole('link', { name: 'OVERLAY' }).getAttribute('href')).toBe('dualdex://overlay/show');
    expect(screen.getByRole('link', { name: 'DOCKED' }).getAttribute('data-active')).toBe('true');
    expect(screen.getByText(/fixed 4:3 panel/i)).toBeTruthy();
  });

  it('exposes persisted theme and companion-display targeting', () => {
    const send = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={send} onUpload={vi.fn()} />);

    fireEvent.click(screen.getByRole('tab', { name: 'DARK' }));
    fireEvent.click(screen.getByRole('tab', { name: 'EXTERNAL' }));

    expect(send).toHaveBeenCalledWith('SETTINGS', { theme: 'DARK' });
    expect(send).toHaveBeenCalledWith('SETTINGS', { displayTarget: 'EXTERNAL' });
  });

  it('shows SaveRAM health and lets an ambiguous match be selected', () => {
    const send = vi.fn();
    render(<SettingsPage catalog={catalog} state={{ ...state, saveRam: {
      status: 'AMBIGUOUS', sourceName: null, sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
      autosaveStatus: 'UNVERIFIED', capabilities: {}, message: 'Choose one.',
      candidates: [{ id: 'content://save/1', path: 'RetroArch/saves/game.srm', lastModifiedEpochMs: 10 }]
    } }} send={send} onUpload={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /RetroArch\/saves\/game.srm/i }));

    expect(send).toHaveBeenCalledWith('SELECT_SAVE', { documentId: 'content://save/1' });
    expect(screen.getByText(/autosave is unverified/i)).toBeTruthy();
  });

  it('opens the isolated mapper and clears only inactive catalog caches', () => {
    const send = vi.fn();
    const onOpenMapper = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={send} onUpload={vi.fn()} onOpenMapper={onOpenMapper} />);

    fireEvent.click(screen.getByRole('button', { name: 'OPEN MEMORY MAPPER LAB' }));
    fireEvent.click(screen.getByRole('button', { name: 'CLEAR INACTIVE CATALOGS' }));

    expect(onOpenMapper).toHaveBeenCalledOnce();
    expect(send).toHaveBeenCalledWith('CLEAR_INACTIVE_CATALOGS');
    expect(screen.getByText(/never resets seen, caught, team, or move knowledge/i)).toBeTruthy();
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
