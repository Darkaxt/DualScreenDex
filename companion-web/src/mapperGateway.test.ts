import { afterEach, describe, expect, it, vi } from 'vitest';
import { mapperState } from './mapperGateway';

function response(payload: unknown, status = 200, contentType = 'application/json'): Response {
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

describe('mapper gateway errors', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses the structured API error message without stringifying the error object', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({
      error: { code: 'MAPPER_UNAVAILABLE', message: 'Memory capture is unavailable.', retryable: true },
    }, 503)));

    await expect(mapperState()).rejects.toThrow('Memory capture is unavailable.');
  });

  it('uses bounded stable text for malformed mapper errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ error: { message: { nested: 'unsafe' } } }, 503)));

    await expect(mapperState()).rejects.toThrow('Mapper state failed (503)');
  });
});
