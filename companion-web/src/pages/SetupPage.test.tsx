import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { State } from '../models';
import { SetupPage } from './SetupPage';

afterEach(cleanup);

describe('RetroArch setup', () => {
  it('separates permission, restart verification, session, and manual fallback states', () => {
    const send = vi.fn();
    render(<SetupPage state={state} send={send} />);

    expect(screen.getByText('RETROARCH CONNECTION')).toBeTruthy();
    expect(screen.getByText(/fully close RetroArch before selecting/i)).toBeTruthy();
    expect(screen.getByText(/not considered active until DualDex verifies/i)).toBeTruthy();
    expect(screen.getByText(/Settings → Network → Network Commands/i)).toBeTruthy();
    expect(screen.getByText(/Save Current Configuration/i)).toBeTruthy();
    expect(screen.getByText(/manual ROM loading remains available/i)).toBeTruthy();
    expect(screen.getByText(/12 ROM sources/i)).toBeTruthy();
  });

  it('emits only the three native setup links', () => {
    render(<SetupPage state={state} send={vi.fn()} />);

    expect(screen.getByRole('link', { name: 'SELECT RETROARCH FOLDER' }).getAttribute('href')).toBe('dualdex://grant/retroarch');
    expect(screen.getByRole('link', { name: 'SELECT ROM FOLDER' }).getAttribute('href')).toBe('dualdex://grant/roms');
    expect(screen.getByRole('link', { name: 'OPEN RETROARCH' }).getAttribute('href')).toBe('dualdex://open/retroarch');
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
    indexedRoms: 12,
    message: 'Fully restart RetroArch, then return here.',
  },
} satisfies State;
