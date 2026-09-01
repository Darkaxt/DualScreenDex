import { cleanup, fireEvent, render, screen } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Dialog, Header, Tabs } from './components';

afterEach(cleanup);

describe('application header icons', () => {
  it('uses one detailed Poké Ball for Party instead of two cryptic circles', () => {
    const { container } = render(<Header title="POKÉDEX" onParty={vi.fn()} />);
    const button = screen.getByRole('button', { name: 'Party' });
    const icon = button.querySelector('[data-semantic-icon="party"]')!;

    expect(icon.querySelectorAll('.party-ball-body')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-upper')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-divider')).toHaveLength(1);
    expect(icon.querySelectorAll('.party-ball-button')).toHaveLength(1);
    expect(container.querySelectorAll('.party-ball-body')).toHaveLength(1);
  });

  it('marks the current root destination with its semantic icon', () => {
    render(<Header title="POKÉDEX" currentDestination="POKEDEX" onParty={vi.fn()} />);

    const current = screen.getByRole('img', { name: 'Pokédex, current page' });
    expect(current.getAttribute('aria-current')).toBe('page');
    expect(current.querySelector('[data-semantic-icon="pokedex"]')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Party' }).hasAttribute('aria-current')).toBe(false);
  });

  it('provides the route programmatic heading', () => {
    render(<Header title="POKÉDEX" />);

    expect(screen.getByRole('heading', { level: 1, name: 'POKÉDEX' })).toBeTruthy();
  });
});

describe('shared dialog', () => {
  it('labels the modal, inerts the background, traps focus, closes, and restores its trigger', () => {
    const close = vi.fn();
    const rendered = render(<div class="device-screen">
      <section class="screen">
        <button type="button">OPEN DETAILS</button>
        <div>BACKGROUND</div>
      </section>
      <div class="global-feedback"><button type="button">DISMISS ERROR</button></div>
    </div>);
    const trigger = screen.getByRole('button', { name: 'OPEN DETAILS' });
    trigger.focus();

    rendered.rerender(<div class="device-screen">
      <section class="screen">
        <button type="button">OPEN DETAILS</button>
        <div>BACKGROUND</div>
        <Dialog label="Partner details" closeLabel="Close partner details" onClose={close} restoreFocus={trigger}>
          <button type="button">FIRST ACTION</button>
          <button type="button">LAST ACTION</button>
        </Dialog>
      </section>
      <div class="global-feedback"><button type="button">DISMISS ERROR</button></div>
    </div>);

    const dialog = screen.getByRole('dialog', { name: 'Partner details' });
    const closeButton = screen.getByRole('button', { name: 'Close partner details' });
    const first = screen.getByRole('button', { name: 'FIRST ACTION' });
    const last = screen.getByRole('button', { name: 'LAST ACTION' });
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.parentElement).toBe(dialog.closest('.screen')?.lastElementChild);
    expect(dialog.closest('.screen')?.firstElementChild?.hasAttribute('inert')).toBe(true);
    expect(document.querySelector('.global-feedback')?.hasAttribute('inert')).toBe(true);
    expect(document.querySelector('.global-feedback')?.getAttribute('aria-hidden')).toBe('true');
    expect(document.activeElement).toBe(closeButton);

    last.focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(document.activeElement).toBe(closeButton);
    closeButton.focus();
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(last);
    first.focus();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(close).toHaveBeenCalledOnce();

    rendered.rerender(<div class="device-screen">
      <section class="screen">
        <button type="button">OPEN DETAILS</button>
        <div>BACKGROUND</div>
      </section>
      <div class="global-feedback"><button type="button">DISMISS ERROR</button></div>
    </div>);
    expect(document.querySelector('.global-feedback')?.hasAttribute('inert')).toBe(false);
    expect(document.activeElement?.textContent).toBe('OPEN DETAILS');
  });
});

describe('shared tabs', () => {
  it('associates tabs with a panel and uses roving arrow, Home, and End focus', () => {
    const select = vi.fn();
    render(<Tabs
      values={['ENTRY', 'STATS', 'MOVES', 'AREA', 'MORE']}
      active="ENTRY"
      disabledValues={['STATS']}
      columns={3}
      panelPrefix="detail"
      onSelect={select}
      label="Pokédex detail"
    />);

    const entry = screen.getByRole('tab', { name: 'ENTRY' });
    const stats = screen.getByRole('tab', { name: 'STATS' });
    const moves = screen.getByRole('tab', { name: 'MOVES' });
    const area = screen.getByRole('tab', { name: 'AREA' });
    const more = screen.getByRole('tab', { name: 'MORE' });
    expect(entry.getAttribute('tabindex')).toBe('0');
    expect(moves.getAttribute('tabindex')).toBe('-1');
    expect(entry.getAttribute('aria-controls')).toBe('detail-entry-panel');
    expect(stats.getAttribute('aria-disabled')).toBe('true');

    entry.focus();
    fireEvent.keyDown(entry, { key: 'ArrowRight' });
    expect(document.activeElement).toBe(moves);
    expect(select).toHaveBeenLastCalledWith('MOVES');
    fireEvent.keyDown(moves, { key: 'End' });
    expect(document.activeElement).toBe(more);
    fireEvent.keyDown(more, { key: 'Home' });
    expect(document.activeElement).toBe(entry);
    fireEvent.keyDown(entry, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(area);
  });
});
