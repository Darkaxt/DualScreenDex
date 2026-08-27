import type { Bootstrap, DiagnosticView, SpecimenCollectionView, State } from './models';

export type ConnectionStatus = 'CONNECTED' | 'RECONNECTING' | 'FAILED';

const POLL_INTERVAL_MILLIS = 750;
const POLL_REQUEST_TIMEOUT_MILLIS = 5_000;
const MAX_RETRY_MILLIS = 12_000;
const FAILED_AFTER_ATTEMPTS = 4;

export async function bootstrap(): Promise<Bootstrap> {
  return requestJson(await fetch('/api/bootstrap'), 'Bootstrap');
}

export async function action(type: string, values: Record<string, string | number | boolean | null> = {}): Promise<State> {
  const response = await fetch('/api/actions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, ...values })
  });
  return requestJson(response, 'Action');
}

export async function uploadRom(file: File): Promise<Bootstrap> {
  const response = await fetch(`/api/load?name=${encodeURIComponent(file.name)}`, { method: 'POST', body: file });
  return requestJson(response, 'ROM load');
}

export async function diagnostics(speciesId?: number | null, moveId?: number | null): Promise<DiagnosticView> {
  const query = new URLSearchParams();
  if (speciesId != null) query.set('speciesId', String(speciesId));
  if (moveId != null) query.set('moveId', String(moveId));
  const response = await fetch(`/api/diagnostics?${query}`);
  return requestJson(response, 'Diagnostics');
}

export async function specimens(speciesId: number): Promise<SpecimenCollectionView> {
  const response = await fetch(`/api/specimens?speciesId=${encodeURIComponent(speciesId)}`);
  return requestJson(response, 'Specimens');
}

export function events(
  currentVersion: () => number,
  onState: (state: State) => void,
  onConnectionStatus: (status: ConnectionStatus) => void = () => undefined,
  onRefreshRequired: () => void | Promise<void> = () => undefined,
): () => void {
  let stopped = false;
  let timer: number | undefined;
  let requestTimer: number | undefined;
  let controller: AbortController | undefined;
  let failures = 0;
  let connectionStatus: ConnectionStatus = 'CONNECTED';

  const publishConnectionStatus = (next: ConnectionStatus) => {
    if (next === connectionStatus) return;
    connectionStatus = next;
    onConnectionStatus(next);
  };

  const schedule = (delay: number) => {
    if (!stopped) timer = window.setTimeout(poll, delay);
  };

  const poll = async () => {
    if (stopped) return;
    controller = new AbortController();
    requestTimer = window.setTimeout(() => controller?.abort(), POLL_REQUEST_TIMEOUT_MILLIS);
    try {
      const requestedVersion = currentVersion();
      const response = await fetch(`/api/state?sinceVersion=${requestedVersion}`, { signal: controller.signal });
      let incoming: State | null = null;
      if (response.status !== 204) incoming = await requestJson<State>(response, 'State refresh');
      if (stopped) return;

      const recovered = failures > 0;
      failures = 0;
      if (recovered || (incoming != null && incoming.version < requestedVersion)) {
        await onRefreshRequired();
      } else if (incoming != null) {
        onState(incoming);
      }
      publishConnectionStatus('CONNECTED');
      schedule(POLL_INTERVAL_MILLIS);
    } catch {
      if (stopped) return;
      failures += 1;
      publishConnectionStatus(failures >= FAILED_AFTER_ATTEMPTS ? 'FAILED' : 'RECONNECTING');
      schedule(Math.min(MAX_RETRY_MILLIS, POLL_INTERVAL_MILLIS * 2 ** failures));
    } finally {
      if (requestTimer != null) window.clearTimeout(requestTimer);
      requestTimer = undefined;
      controller = undefined;
    }
  };

  schedule(POLL_INTERVAL_MILLIS);
  return () => {
    stopped = true;
    if (timer != null) window.clearTimeout(timer);
    if (requestTimer != null) window.clearTimeout(requestTimer);
    controller?.abort();
  };
}

async function requestJson<T>(response: Response, operation: string): Promise<T> {
  const contentType = response.headers.get('Content-Type')?.toLowerCase() ?? '';
  if (!contentType.includes('application/json')) {
    if (!response.ok) throw new Error(`${operation} failed (${response.status})`);
    throw new Error(`${operation} returned a non-JSON response`);
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    if (!response.ok) throw new Error(`${operation} failed (${response.status})`);
    throw new Error(`${operation} returned malformed JSON`);
  }
  if (!response.ok) throw new Error(apiErrorMessage(payload) ?? `${operation} failed (${response.status})`);
  return payload as T;
}

function apiErrorMessage(payload: unknown): string | null {
  if (!isRecord(payload) || !isRecord(payload.error)) return null;
  return validMessage(payload.error.message);
}

function validMessage(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 && value.length <= 256 ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value != null && !Array.isArray(value);
}
