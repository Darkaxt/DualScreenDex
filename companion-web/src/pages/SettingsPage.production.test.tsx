import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { SettingsPage } from './SettingsPage';

afterEach(cleanup);

describe('production settings copy', () => {
  it('keeps the normal settings surface concise and confines ROM diagnostics to Debug', () => {
    const { container } = render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.queryByText('PRESENTATION & KNOWLEDGE')).toBeNull();
    expect(screen.queryByText('ACTIVE GAME')).toBeNull();
    expect(container.querySelector('.rom-setting .eyebrow')?.textContent).toBe('GAME');
    expect(screen.getByLabelText('Change ROM or ZIP')).toBeTruthy();

    const debug = container.querySelector('.mapper-setting');
    expect(debug?.textContent).toContain('fixture.gba');
    expect(debug?.textContent).toContain('CRC32 1234ABCD');
    const normalCopy = Array.from(container.querySelectorAll('.setting-group:not(.mapper-setting)')).map(item => item.textContent).join(' ');
    expect(normalCopy).not.toContain('fixture.gba');
    expect(normalCopy).not.toContain('CRC32');
    expect(normalCopy).not.toMatch(/ROM SETTINGS|SaveRAM|catalog|polling|AMBIGUOUS|UNVERIFIED/i);
    expect(normalCopy).not.toMatch(/debug/i);
  });

  it('describes save-detected level-up Auto and manual recovery without claiming a full movepool switch', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.queryByText(/browser POC/i)).toBeNull();
    expect(screen.queryByText(/memory mapper will/i)).toBeNull();
    expect(screen.getByText(/Auto chooses the matching level-up list/i)).toBeTruthy();
    expect(screen.getByText(/Choose a list only when Auto cannot decide/i)).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Auto' })).toBeTruthy();
  });

  it('distinguishes loaded-ROM choices from global device ownership', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.getByText(/saved for the current game/i)).toBeTruthy();
    expect(screen.getByText(/screen choice and overlay size apply to every game/i)).toBeTruthy();
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
    expect(screen.getByText(/resizable 4:3 panel/i)).toBeTruthy();
    expect(screen.queryByText(/fixed 4:3 panel/i)).toBeNull();
  });

  it('exposes persisted theme and companion-display targeting', () => {
    const send = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={send} onUpload={vi.fn()} />);

    fireEvent.click(screen.getByRole('tab', { name: 'DARK' }));
    fireEvent.click(screen.getByRole('tab', { name: 'EXTERNAL' }));

    expect(send).toHaveBeenCalledWith('SETTINGS', { theme: 'DARK' });
    expect(send).toHaveBeenCalledWith('SETTINGS', { displayTarget: 'EXTERNAL' });
  });

  it('does not expose the retired Thor focus setting or status', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.queryByRole('checkbox', { name: 'Keep controls on top screen' })).toBeNull();
    expect(screen.queryByText(/Thor focus/i)).toBeNull();
    expect(screen.queryByText(/controller focus/i)).toBeNull();
  });

  it('offers a save choice without leaking raw save state outside Debug', () => {
    const send = vi.fn();
    render(<SettingsPage catalog={catalog} state={{ ...state, saveRam: {
      status: 'AMBIGUOUS', sourceName: null, sourceLastModifiedEpochMs: null, refreshedAtEpochMs: null,
      autosaveStatus: 'UNVERIFIED', capabilities: {}, message: 'Choose one.',
      candidates: [{ id: 'content://save/1', path: 'RetroArch/saves/game.srm', lastModifiedEpochMs: 10 }]
    } }} send={send} onUpload={vi.fn()} />);

    const saveSection = document.querySelector('.save-setting')!;
    expect(saveSection.textContent).not.toMatch(/AMBIGUOUS|UNVERIFIED|RetroArch\/saves|Choose one/i);
    fireEvent.click(screen.getByRole('button', { name: /SAVE 1.*game.srm/i }));

    expect(send).toHaveBeenCalledWith('SELECT_SAVE', { documentId: 'content://save/1' });
    expect(document.querySelector('.mapper-setting')?.textContent).toMatch(/AMBIGUOUS|UNVERIFIED|RetroArch\/saves\/game.srm/i);
  });

  it('opens the isolated mapper and clears only inactive catalog caches', () => {
    const send = vi.fn();
    const onOpenMapper = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={send} onUpload={vi.fn()} onOpenMapper={onOpenMapper} />);

    fireEvent.click(screen.getByRole('button', { name: 'CAPTURE MEMORY REPORT' }));
    fireEvent.click(screen.getByRole('button', { name: 'REMOVE UNUSED GAME DATA' }));

    expect(onOpenMapper).toHaveBeenCalledOnce();
    expect(send).toHaveBeenCalledWith('CLEAR_INACTIVE_CATALOGS');
    expect(screen.getByText(/never resets seen, caught, team, or move knowledge/i)).toBeTruthy();
  });

  it('keeps the capability report beside but independent from memory capture', () => {
    const onOpenCapabilities = vi.fn();
    const onOpenMapper = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} onOpenCapabilities={onOpenCapabilities} onOpenMapper={onOpenMapper} />);

    expect(screen.getByText('DEBUG')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'CAPABILITY REPORT' }));

    expect(onOpenCapabilities).toHaveBeenCalledOnce();
    expect(onOpenMapper).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'CAPTURE MEMORY REPORT' })).toBeTruthy();
  });

  it('disables the capability report when no ROM is loaded', () => {
    render(<SettingsPage catalog={null} state={{ ...state, catalogReady: false, catalogName: null }} send={vi.fn()} onUpload={vi.fn()} />);

    const button = screen.getByRole('button', { name: 'NO GAME LOADED' });
    expect((button as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/No game is open.*choices become your defaults/i)).toBeTruthy();
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
