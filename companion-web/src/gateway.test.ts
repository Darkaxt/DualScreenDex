import { afterEach, describe, expect, it, vi } from 'vitest';
import { action, bootstrap, events } from './gateway';
import type { State } from './models';

function response(
  payload: unknown,
  status = 200,
  contentType = 'application/json; charset=utf-8',
): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => contentType },
    json: async () => {
      if (payload instanceof Error) throw payload;
      return payload;
    },
  } as unknown as Response;
}

describe('production state heartbeat', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('polls the small state endpoint and can be stopped', async () => {
    vi.useFakeTimers();
    const state = { version: 2 } as State;
    const fetchMock = vi.fn(async () => response(state));
    vi.stubGlobal('fetch', fetchMock);
    const onState = vi.fn();

    const stop = events(() => 1, onState);
    await vi.advanceTimersByTimeAsync(750);

    expect(fetchMock).toHaveBeenCalledWith('/api/state?sinceVersion=1', expect.objectContaining({ signal: expect.any(AbortSignal) }));
    expect(onState).toHaveBeenCalledWith(state);
    stop();
    await vi.advanceTimersByTimeAsync(1500);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('does not parse or publish an unchanged state response', async () => {
    vi.useFakeTimers();
    const json = vi.fn();
    const fetchMock = vi.fn(async () => ({ ...response(null, 204, ''), json }));
    vi.stubGlobal('fetch', fetchMock);
    const onState = vi.fn();

    const stop = events(() => 2, onState);
    await vi.advanceTimersByTimeAsync(750);

    expect(json).not.toHaveBeenCalled();
    expect(onState).not.toHaveBeenCalled();
    stop();
  });

  it('aborts a stalled poll before retrying', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn((_url: string, options?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      options?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));
    vi.stubGlobal('fetch', fetchMock);
    const onConnection = vi.fn();

    const stop = events(() => 1, vi.fn(), onConnection);
    await vi.advanceTimersByTimeAsync(750);
    expect(fetchMock).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(5000);

    expect(onConnection).toHaveBeenLastCalledWith('RECONNECTING');
    stop();
  });

  it('backs off after rejection and refreshes once after reconnecting', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('connection dropped'))
      .mockResolvedValueOnce(response({ version: 2 } as State));
    vi.stubGlobal('fetch', fetchMock);
    const onState = vi.fn();
    const onConnection = vi.fn();
    const onRefreshRequired = vi.fn();

    const stop = events(() => 1, onState, onConnection, onRefreshRequired);
    await vi.advanceTimersByTimeAsync(750);
    expect(onConnection).toHaveBeenLastCalledWith('RECONNECTING');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1499);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onConnection).toHaveBeenLastCalledWith('CONNECTED');
    expect(onRefreshRequired).toHaveBeenCalledOnce();
    expect(onState).not.toHaveBeenCalled();
    stop();
  });

  it('retries bootstrap refresh when recovery finishes before bootstrap is ready', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('connection dropped'))
      .mockResolvedValue(response({ version: 2 } as State));
    vi.stubGlobal('fetch', fetchMock);
    const onConnection = vi.fn();
    const onRefreshRequired = vi.fn()
      .mockRejectedValueOnce(new TypeError('bootstrap not ready'))
      .mockResolvedValueOnce(undefined);

    const stop = events(() => 1, vi.fn(), onConnection, onRefreshRequired);
    await vi.advanceTimersByTimeAsync(750 + 1500 + 1500);

    expect(onRefreshRequired).toHaveBeenCalledTimes(2);
    expect(onConnection).toHaveBeenLastCalledWith('CONNECTED');
    stop();
  });

  it('enters failed state after bounded retries without overlapping requests', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(async () => { throw new TypeError('offline'); });
    vi.stubGlobal('fetch', fetchMock);
    const onConnection = vi.fn();

    const stop = events(() => 1, vi.fn(), onConnection);
    await vi.advanceTimersByTimeAsync(750 + 1500 + 3000 + 6000);

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(onConnection).toHaveBeenLastCalledWith('FAILED');
    stop();
  });

  it('requests a bootstrap when the server state version resets', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn(async () => response({ version: 1 } as State)));
    const onState = vi.fn();
    const onRefreshRequired = vi.fn();

    const stop = events(() => 9, onState, vi.fn(), onRefreshRequired);
    await vi.advanceTimersByTimeAsync(750);

    expect(onRefreshRequired).toHaveBeenCalledOnce();
    expect(onState).not.toHaveBeenCalled();
    stop();
  });
});

describe('JSON gateway responses', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses the structured API error message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({
      error: { code: 'INVALID_REQUEST', message: 'The action is invalid.', retryable: false },
    }, 400)));

    await expect(action('BROKEN')).rejects.toThrow('The action is invalid.');
  });

  it('does not parse non-JSON or malformed success responses', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(response('server failure', 500, 'text/plain'))
      .mockResolvedValueOnce(response(new SyntaxError('broken JSON'))));

    await expect(action('BROKEN')).rejects.toThrow('Action failed (500)');
    await expect(bootstrap()).rejects.toThrow('Bootstrap returned malformed JSON');
  });
});
