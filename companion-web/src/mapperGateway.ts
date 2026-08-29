import { requestJson } from './gateway';

export interface MapperState {
  enabled: boolean;
  privacyAcknowledged: boolean;
  coreIdentity: string | null;
  contentIdentity: string | null;
  descriptors: { id: string; label: string; baseAddress: number; size: number }[];
  captureLabel: string | null;
  completedBytes: number;
  totalBytes: number;
  snapshots: { id: string; label: string; customLabel: string | null; capturedAtEpochMs: number; bytes: number }[];
  latestDiff: { changedBytes: number; ranges: number; omittedRanges: number } | null;
  error: string | null;
}

export async function mapperState(signal?: AbortSignal): Promise<MapperState> {
  const response = await fetch('/api/mapper/state', { signal });
  return requestJson(response, 'Mapper state');
}

export async function mapperAction(
  type: string,
  values: Record<string, string | boolean | null> = {},
  signal?: AbortSignal,
): Promise<MapperState> {
  const response = await fetch('/api/mapper/actions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type, ...values }), signal,
  });
  return requestJson(response, 'Mapper action');
}

export async function mapperExport(signal?: AbortSignal): Promise<Blob> {
  const response = await fetch('/api/mapper/export', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}', signal,
  });
  if (!response.ok) return requestJson<never>(response, 'Mapper export');
  return response.blob();
}
