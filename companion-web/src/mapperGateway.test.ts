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

  it('uses the structured API presentation message instead of the diagnostic text', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({
      error: {
        code: 'INTERNAL_ERROR',
        message: 'private mapper diagnostic',
        retryable: true,
        presentationMessage: { code: 'API_INTERNAL_ERROR' },
      },
    }, 500)));

    await expect(mapperState()).rejects.toThrow('The server could not complete the request.');
  });

  it('uses bounded stable text for malformed mapper errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => response({ error: { message: { nested: 'unsafe' } } }, 503)));

    await expect(mapperState()).rejects.toThrow('Mapper state failed (503)');
  });
});
