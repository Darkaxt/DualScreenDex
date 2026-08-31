import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { State } from '../models';
import { SetupPage } from './SetupPage';

afterEach(cleanup);

describe('RetroArch setup', () => {
  it('separates permission, restart verification, session, and manual fallback states', () => {
    const send = vi.fn();
    render(<SetupPage state={state} send={send} />);

    expect(screen.queryByText('PASSIVE CONNECTION')).toBeNull();
    expect(screen.getByText('RETROARCH CONNECTION')).toBeTruthy();
    expect(screen.getByText(/automatically finds supported games and their save files/i)).toBeTruthy();
    expect(screen.getByText(/Android\/data and Android\/obb remain protected/i)).toBeTruthy();
    expect(screen.getByText(/public shared storage or use the folder fallback/i)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'RESCAN GAMES' }).getAttribute('href')).toBe('dualdex://games/rescan');
    expect(screen.getByText(/fully close RetroArch before setup/i)).toBeTruthy();
    expect(screen.getByText(/not considered active until DualDex verifies/i)).toBeTruthy();
    expect(screen.getByText(/Settings → Network → Network Commands/i)).toBeTruthy();
    expect(screen.getByText(/Saving → SaveRAM Autosave Interval/i)).toBeTruthy();
    expect(screen.getByText(/Directory → Save Files/i)).toBeTruthy();
    expect(screen.getByText(/Save Current Configuration/i)).toBeTruthy();
    expect(screen.getByText(/manual game loading remains available/i)).toBeTruthy();
    expect(screen.getByText(/12 games found/i)).toBeTruthy();
    expect(screen.queryByText('/storage/emulated/0/RetroArch/saves')).toBeNull();
    expect(document.querySelector('.setup-content')?.textContent).not.toMatch(/RESTART_REQUIRED|DISCONNECTED|NO_CONTENT|GRANTED/i);
  });

  it('makes All Files Access primary while retaining the two folder fallbacks', () => {
    render(<SetupPage state={{ ...state, retroArch: { ...state.retroArch, storageGrant: 'MISSING' } }} send={vi.fn()} />);

    expect(screen.getByRole('link', { name: 'GRANT ALL FILES ACCESS' }).getAttribute('href')).toBe('dualdex://grant/files');
    expect(screen.getByRole('link', { name: 'SELECT RETROARCH FOLDER' }).getAttribute('href')).toBe('dualdex://grant/retroarch');
    expect(screen.getByRole('link', { name: 'SELECT GAME FOLDER' }).getAttribute('href')).toBe('dualdex://grant/roms');
    expect(screen.getByRole('link', { name: 'OPEN RETROARCH' }).getAttribute('href')).toBe('dualdex://open/retroarch');
  });

  it('explains why save discovery needs storage access in player-facing language', () => {
    render(<SetupPage state={{ ...state, retroArch: { ...state.retroArch, storageGrant: 'MISSING' } }} send={vi.fn()} />);

    expect(screen.getByText(/Save files in separate folders cannot be found until storage access is granted/i)).toBeTruthy();
  });

  it('keeps granted storage ready while game discovery is indexing', () => {
    render(<SetupPage state={{
      ...state,
      retroArch: { ...state.retroArch, storageGrant: 'GRANTED', romGrant: 'INDEXING', indexedRoms: 0 },
    }} send={vi.fn()} />);

    expect(screen.queryByRole('link', { name: 'GRANT ALL FILES ACCESS' })).toBeNull();
    expect(screen.getByText('Ready')).toBeTruthy();
    expect(screen.getByText('Finding your games…')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'RESCAN GAMES' })).toBeNull();
  });

  it('keeps granted storage ready when indexing fails', () => {
    render(<SetupPage state={{
      ...state,
      retroArch: { ...state.retroArch, storageGrant: 'GRANTED', romGrant: 'FAILED', indexedRoms: 0 },
    }} send={vi.fn()} />);

    expect(screen.queryByRole('link', { name: 'GRANT ALL FILES ACCESS' })).toBeNull();
    expect(screen.getByText('Ready')).toBeTruthy();
    expect(screen.getByText(/Games could not be indexed/i)).toBeTruthy();
  });

  it('explains that a failed rescan retains the previous game index', () => {
    render(<SetupPage state={{
      ...state,
      retroArch: { ...state.retroArch, storageGrant: 'GRANTED', romGrant: 'FAILED', indexedRoms: 12 },
    }} send={vi.fn()} />);

    expect(screen.getByText(/previous game index remains active/i)).toBeTruthy();
    expect(screen.getByText(/12 games found/i)).toBeTruthy();
  });

  it('offers a direct folder reselect when RetroArch configuration cannot be verified', () => {
    render(<SetupPage state={{
      ...state,
      retroArch: { ...state.retroArch, configState: 'FAILED' },
    }} send={vi.fn()} />);

    expect(screen.getByRole('alert').textContent).toContain('could not be verified');
    expect(screen.getByRole('link', { name: 'RESELECT RETROARCH FOLDER' }).getAttribute('href')).toBe('dualdex://grant/retroarch');
  });

  it('offers an explicit guide retry only after a failed activation', () => {
    const failed = { ...state, retroArch: { ...state.retroArch, resolution: 'FAILED' } };
    const { rerender } = render(<SetupPage state={failed} send={vi.fn()} />);

    expect(screen.getByRole('link', { name: 'RETRY OPENING GAME GUIDE' }).getAttribute('href')).toBe('dualdex://guide/retry');

    rerender(<SetupPage state={state} send={vi.fn()} />);
    expect(screen.queryByRole('link', { name: 'RETRY OPENING GAME GUIDE' })).toBeNull();
  });

  it('returns to the previous screen', () => {
    const send = vi.fn();
    render(<SetupPage state={state} send={send} />);

    fireEvent.click(screen.getByLabelText('Back'));

    expect(send).toHaveBeenCalledWith('SCREEN', { screen: 'POKEDEX' });
  });
});

const state = {
  version: 1,
  screen: 'SETUP',
  priorScreen: 'POKEDEX',
  settingsReturnScreen: 'POKEDEX',
  selectedSpeciesId: null,
  filter: 'ALL',
  selectedAreaId: null,
  battleTab: 'ENTRY',
  settings: { knowledgeMode: 'ORGANIC', attackEnabled: true, rarityEnabled: true, movesEnabled: true, fontScale: 1, density: 'AUTO', highContrast: false, autoOpenTarget: true, ruleset: 'AUTO' },
  speciesState: {},
  observedMoves: {},
  battle: null,
  catalogReady: true,
  catalogName: 'Modern Emerald.gba',
  error: null,
  activeRulesetId: null,
  rulesetAssumed: true,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  retroArch: {
    storageGrant: 'GRANTED',
    configGrant: 'GRANTED',
    romGrant: 'GRANTED',
    configState: 'RESTART_REQUIRED',
    restartRequired: true,
    connection: 'DISCONNECTED',
    systemId: null,
    gameBasename: null,
    contentCrc32: null,
    resolution: 'NO_CONTENT',
    activeSource: null,
    savefileDirectory: '/storage/emulated/0/RetroArch/saves',
    indexedRoms: 12,
    message: 'Fully restart RetroArch, then return here.',
  },
} satisfies State;
