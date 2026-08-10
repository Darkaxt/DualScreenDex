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

export async function mapperState(): Promise<MapperState> {
  const response = await fetch('/api/mapper/state');
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error ?? `Mapper state failed (${response.status})`);
  return payload;
}

export async function mapperAction(type: string, values: Record<string, string | boolean | null> = {}): Promise<MapperState> {
  const response = await fetch('/api/mapper/actions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type, ...values }),
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error ?? `Mapper action failed (${response.status})`);
  return payload;
}

export async function mapperExport(): Promise<Blob> {
  const response = await fetch('/api/mapper/export', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
  });
  if (!response.ok) {
    const payload = await response.json();
    throw new Error(payload.error ?? `Mapper export failed (${response.status})`);
  }
  return response.blob();
}
