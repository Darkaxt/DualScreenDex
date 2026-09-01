import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Catalog, State } from '../models';
import { SETTINGS_CATEGORIES, SettingsPage } from './SettingsPage';

afterEach(cleanup);

describe('Settings navigation', () => {
  it('shows exactly seven categories and only the selected category content', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    expect(screen.getByRole('img', { name: 'Settings, current page' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getAllByRole('button', { name: /^(General|Connection|Display|Information|Accessibility|Behavior|Advanced)$/ })).toHaveLength(7);
    expect(SETTINGS_CATEGORIES.map(category => category.label)).toEqual([
      'General',
      'Connection',
      'Display',
      'Information',
      'Accessibility',
      'Behavior',
      'Advanced',
    ]);
    expect(screen.queryByText('INFORMATION POLICY')).toBeNull();
    const information = screen.getByRole('button', { name: 'Information' });
    expect(document.getElementById(information.getAttribute('aria-describedby')!)?.textContent).toBe('Guide, map, move, and battle data');
    const content = document.querySelector('.settings-content') as HTMLDivElement;
    content.scrollTop = 120;

    fireEvent.click(information);

    expect(content.scrollTop).toBe(0);
    expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Information' }));
    expect(screen.getByText('INFORMATION POLICY')).toBeTruthy();
    expect(screen.getByText('LOCAL MAP DETAILS')).toBeTruthy();
    expect(screen.getByText('LEVEL-UP MOVES')).toBeTruthy();
    expect(screen.getByText('BATTLE TABS')).toBeTruthy();
    expect(screen.queryByText('READABILITY')).toBeNull();
  });

  it('uses Back to return to the category index before closing Settings', () => {
    const onBack = vi.fn();
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} onBack={onBack} />);

    fireEvent.click(screen.getByRole('button', { name: 'Display' }));
    fireEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(onBack).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Display' })).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Display' }));

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it('preserves server-owned values while moving between categories', () => {
    render(<SettingsPage catalog={catalog} state={state} send={vi.fn()} onUpload={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'Accessibility' }));
    expect(screen.getByRole('button', { name: 'COMPACT' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('checkbox', { name: 'High contrast' })).toHaveProperty('checked', true);

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    fireEvent.click(screen.getByRole('button', { name: 'Information' }));
    expect(screen.getByRole('combobox', { name: 'Move list' })).toHaveProperty('value', 'modern');
  });

  it('opens and focuses the catalog-bound Move List recovery target', () => {
    render(<SettingsPage
      catalog={catalog}
      state={state}
      send={vi.fn()}
      onUpload={vi.fn()}
      initialCategory="INFORMATION"
      initialControl="MOVE_LIST"
    />);

    const moveList = screen.getByRole('combobox', { name: 'Move list' });
    expect(document.activeElement).toBe(moveList);
    expect(screen.queryByRole('button', { name: 'General' })).toBeNull();
  });
});

const catalog = {
  hash: 'sha',
  crc32: '1234ABCD',
  family: 'EMERALD',
  platform: 'GBA',
  species: [],
  moves: [],
  types: [],
  areas: [],
  balls: [],
  capabilities: {},
  rulesets: [
    { id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true },
    { id: 'modern', label: 'Modern', sourceOffset: 1, confidence: 0.9, primary: false },
  ],
} satisfies Catalog;

const state = {
  version: 1,
  screen: 'SETTINGS',
  priorScreen: 'POKEDEX',
  settingsReturnScreen: 'POKEDEX',
  selectedSpeciesId: null,
  filter: 'ALL',
  selectedAreaId: null,
  battleTab: 'ENTRY',
  speciesState: {},
  observedMoves: {},
  battle: null,
  catalogReady: true,
  catalogName: 'fixture.gba',
  error: null,
  activeRulesetId: 'modern',
  rulesetAssumed: false,
  loading: { active: false, phase: 'COMPLETE', completedUnits: 5, totalUnits: 5 },
  settings: {
    knowledgeMode: 'DISCOVERED',
    attackEnabled: true,
    rarityEnabled: true,
    movesEnabled: true,
    fontScale: 1,
    density: 'COMPACT',
    highContrast: true,
    autoOpenTarget: true,
    ruleset: 'modern',
  },
} satisfies State;
