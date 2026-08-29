import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryMapperPage } from './MemoryMapperPage';

afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('memory mapper lab', () => {
  it('keeps one mapper poll active and aborts it when the page unmounts', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | undefined;
    const fetch = vi.fn((_url: string, options?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      signal = options?.signal ?? undefined;
      signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));
    vi.stubGlobal('fetch', fetch);

    const view = render(<MemoryMapperPage onBack={vi.fn()} />);
    await act(async () => { await Promise.resolve(); });
    expect(fetch).toHaveBeenCalledOnce();
    await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
    expect(fetch).toHaveBeenCalledOnce();

    view.unmount();
    expect(signal?.aborted).toBe(true);
  });

  it('clears a stale polling error after a later successful mapper state response', async () => {
    vi.useFakeTimers();
    const fetch = vi.fn()
      .mockResolvedValueOnce(response({ error: { message: 'Mapper is temporarily unavailable.' } }, false))
      .mockResolvedValueOnce(response(mapperState(false)));
    vi.stubGlobal('fetch', fetch);

    render(<MemoryMapperPage onBack={vi.fn()} />);
    await act(async () => { await Promise.resolve(); });
    expect((await screen.findByRole('alert')).textContent).toBe('Mapper is temporarily unavailable.');

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000); });
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('ignores a stale mapper poll that completes after an action result', async () => {
    let resolvePoll!: (response: Response) => void;
    const fetch = vi.fn()
      .mockImplementationOnce(() => new Promise<Response>(resolve => { resolvePoll = resolve; }))
      .mockResolvedValueOnce(response(mapperState(true)));
    vi.stubGlobal('fetch', fetch);
    vi.stubGlobal('confirm', vi.fn(() => true));

    render(<MemoryMapperPage onBack={vi.fn()} />);
    await act(async () => { await Promise.resolve(); });
    fireEvent.click(screen.getByRole('button', { name: 'ENABLE FOR THIS SESSION' }));
    expect(await screen.findByText('Nintendo - Game Boy')).toBeTruthy();

    await act(async () => {
      resolvePoll(response(mapperState(false)));
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(screen.getByText('Nintendo - Game Boy')).toBeTruthy();
  });

  it('aborts a mapper poll that exceeds its bounded request timeout', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | undefined;
    const fetch = vi.fn((_url: string, options?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      signal = options?.signal ?? undefined;
      signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));
    vi.stubGlobal('fetch', fetch);

    render(<MemoryMapperPage onBack={vi.fn()} />);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000); });

    expect(signal?.aborted).toBe(true);
  });

  it('starts disabled and uses one confirmation when enabled', async () => {
    const disabled = mapperState(false);
    const enabled = mapperState(true);
    const fetch = vi.fn()
      .mockResolvedValueOnce(response(disabled))
      .mockResolvedValueOnce(response(enabled));
    vi.stubGlobal('fetch', fetch);
    const confirm = vi.fn(() => true);
    vi.stubGlobal('confirm', confirm);

    render(<MemoryMapperPage onBack={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: 'ENABLE FOR THIS SESSION' }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/mapper/actions', expect.objectContaining({
      body: JSON.stringify({ type: 'ENABLE', privacyAcknowledged: true }),
    })));
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.queryByText(/include and export raw memory bytes/i)).toBeNull();
  });
});

function mapperState(enabled: boolean) {
  return {
    enabled, privacyAcknowledged: enabled, coreIdentity: enabled ? 'Nintendo - Game Boy' : null,
    contentIdentity: enabled ? '1234ABCD' : null, descriptors: [], captureLabel: null,
    completedBytes: 0, totalBytes: 0, snapshots: [], latestDiff: null, error: null,
  };
}

function response(value: unknown, ok = true) {
  return {
    ok,
    status: ok ? 200 : 503,
    headers: { get: () => 'application/json' },
    json: async () => value,
  } as unknown as Response;
}
