import { afterEach, describe, expect, it, vi } from 'vitest';
import { events } from './gateway';
import type { State } from './models';

describe('production state heartbeat', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('polls the small state endpoint and can be stopped', async () => {
    vi.useFakeTimers();
    const state = { version: 2 } as State;
    const fetchMock = vi.fn(async () => ({ ok: true, json: async () => state }));
    vi.stubGlobal('fetch', fetchMock);
    const onState = vi.fn();

    const stop = events(() => 1, onState);
    await vi.advanceTimersByTimeAsync(750);

    expect(fetchMock).toHaveBeenCalledWith('/api/state?sinceVersion=1');
    expect(onState).toHaveBeenCalledWith(state);
    stop();
    await vi.advanceTimersByTimeAsync(1500);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('does not parse or publish an unchanged state response', async () => {
    vi.useFakeTimers();
    const json = vi.fn();
    const fetchMock = vi.fn(async () => ({ status: 204, ok: true, json }));
    vi.stubGlobal('fetch', fetchMock);
    const onState = vi.fn();

    const stop = events(() => 2, onState);
    await vi.advanceTimersByTimeAsync(750);

    expect(fetchMock).toHaveBeenCalledWith('/api/state?sinceVersion=2');
    expect(json).not.toHaveBeenCalled();
    expect(onState).not.toHaveBeenCalled();
    stop();
  });
});
