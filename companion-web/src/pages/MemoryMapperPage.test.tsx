import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/preact';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryMapperPage } from './MemoryMapperPage';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('memory mapper lab', () => {
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

function response(value: unknown) {
  return { ok: true, json: async () => value } as Response;
}
