import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CapabilityReportPage } from './CapabilityReportPage';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('loaded ROM capability report', () => {
  it('shows exact complete, partial, ambiguous, missing, and not-applicable evidence', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(diagnosticFixture)));

    render(<CapabilityReportPage romHash={diagnosticFixture.sha256} refreshMarker="COMPLETE:5:5" onBack={vi.fn()} />);

    expect(await screen.findByText("Celia's Stupid Romhack (1.1.4).gba")).toBeTruthy();
    expect(screen.getByText(/FIRERED LEAFGREEN · GBA/i)).toBeTruthy();
    expect(screen.getByText(/CRC32 8204E1A5/i)).toBeTruthy();
    expect(screen.getByText(/SHA-256 81AC9B9D4E7B/i)).toBeTruthy();
    expect(screen.getByText(/Default · ASSUMED/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: /Species names AVAILABLE/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Learnsets PARTIAL 7 \/ 10 records · 70\.0%/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Evolutions AMBIGUOUS/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Contest data NOT FOUND/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Abilities N\/A/i })).toBeTruthy();
  });

  it('expands layout evidence and distinguishes not-found from not-applicable values', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response(diagnosticFixture)));
    render(<CapabilityReportPage romHash={diagnosticFixture.sha256} refreshMarker="COMPLETE:5:5" onBack={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: /Learnsets PARTIAL/i }));
    expect(screen.getByText('0x7713F8')).toBeTruthy();
    expect(screen.getByText('4 bytes', { selector: '.capability-element-size' })).toBeTruthy();
    expect(screen.getByText('MANUAL REVIEW')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Contest data NOT FOUND/i }));
    expect(screen.getAllByText('N/F').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /Abilities N\/A/i }));
    expect(screen.getAllByText('N/A').length).toBeGreaterThan(0);
  });

  it('copies only the stable diagnostic contract and reports fetch failures with retry', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce({ ok: false, json: async () => ({ error: 'Parser snapshot unavailable' }) })
      .mockResolvedValueOnce(response({ ...diagnosticFixture, rawMemory: 'SECRET', romPath: 'D:/private/game.gba', parserDiagnostics: ['Loaded D:/private/game.gba'] }));
    vi.stubGlobal('fetch', fetch);
    const writeText = vi.fn(async (_value: string) => undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });

    render(<CapabilityReportPage romHash={diagnosticFixture.sha256} refreshMarker="COMPLETE:5:5" onBack={vi.fn()} />);
    expect((await screen.findByRole('alert')).textContent).toContain('Parser snapshot unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'RETRY' }));
    fireEvent.click(await screen.findByRole('button', { name: 'COPY REPORT' }));

    await waitFor(() => expect(writeText).toHaveBeenCalledOnce());
    const copied = writeText.mock.calls[0][0];
    expect(copied).toContain('"capabilities"');
    expect(copied).not.toContain('SECRET');
    expect(copied).not.toContain('D:/private');
    expect(copied).toContain('[path omitted]');
    expect(await screen.findByText('REPORT COPIED')).toBeTruthy();
  });

  it('never shows the previous ROM evidence while a different ROM snapshot loads', async () => {
    let finishSecond!: (value: Response) => void;
    const second = new Promise<Response>(resolve => { finishSecond = resolve; });
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(response(diagnosticFixture))
      .mockReturnValueOnce(second));
    const rendered = render(<CapabilityReportPage romHash={diagnosticFixture.sha256} refreshMarker="COMPLETE:5:5" onBack={vi.fn()} />);
    expect(await screen.findByText("Celia's Stupid Romhack (1.1.4).gba")).toBeTruthy();

    const next = { ...diagnosticFixture, romName: 'Next Hack.gba', sha256: 'b'.repeat(64), crc32: 'B00B1E55' };
    rendered.rerender(<CapabilityReportPage romHash={next.sha256} refreshMarker="IDENTIFYING:0:5" onBack={vi.fn()} />);

    expect(screen.queryByText("Celia's Stupid Romhack (1.1.4).gba")).toBeNull();
    expect(screen.getByRole('status').textContent).toContain('LOADING CAPABILITIES');
    finishSecond(response(next));
    expect(await screen.findByText('Next Hack.gba')).toBeTruthy();
  });
});

const diagnosticFixture = {
  romName: "Celia's Stupid Romhack (1.1.4).gba",
  sha256: '81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148',
  crc32: '8204e1a5', family: 'FIRERED_LEAFGREEN', platform: 'GBA',
  activeRulesetId: 'default', rulesetAssumed: true,
  rulesets: [{ id: 'default', label: 'Default', sourceOffset: 0, confidence: 1, primary: true }],
  capabilities: [
    { capability: 'SPECIES_NAMES', status: 'AVAILABLE', confidence: 1, offset: 7600000, count: 1301, recordSize: 11, elementSize: 1, validRecords: 1301, totalRecords: 1301, reviewStatus: 'NONE', reasons: [] },
    { capability: 'LEARNSETS', status: 'PARTIAL', confidence: .7, offset: 0x7713f8, count: 10, recordSize: 4, elementSize: 4, validRecords: 7, totalRecords: 10, reviewStatus: 'MANUAL_REVIEW', reasons: ['7/10 records structurally valid'] },
    { capability: 'EVOLUTIONS', status: 'AMBIGUOUS', confidence: .65, offset: null, count: null, recordSize: null, elementSize: null, validRecords: null, totalRecords: null, reviewStatus: 'MANUAL_REVIEW', reasons: ['two equal candidates'] },
    { capability: 'CONTEST_DATA', status: 'NOT_FOUND', confidence: 0, offset: null, count: null, recordSize: null, elementSize: null, validRecords: null, totalRecords: null, reviewStatus: 'NONE', reasons: [] },
    { capability: 'ABILITIES', status: 'NOT_APPLICABLE', confidence: 0, offset: null, count: null, recordSize: null, elementSize: null, validRecords: null, totalRecords: null, reviewStatus: 'NONE', reasons: [] },
  ],
  parserDiagnostics: ['fixture'], species: null, move: null,
};

function response(value: unknown) {
  return { ok: true, json: async () => value } as Response;
}
