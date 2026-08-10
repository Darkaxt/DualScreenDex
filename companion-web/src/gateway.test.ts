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

    const stop = events(onState);
    await vi.advanceTimersByTimeAsync(750);

    expect(fetchMock).toHaveBeenCalledWith('/api/state');
    expect(onState).toHaveBeenCalledWith(state);
    stop();
    await vi.advanceTimersByTimeAsync(1500);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
