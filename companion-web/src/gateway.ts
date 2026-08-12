import type { Bootstrap, DiagnosticView, State } from './models';

export async function bootstrap(): Promise<Bootstrap> {
  const response = await fetch('/api/bootstrap');
  if (!response.ok) throw new Error(`Bootstrap failed (${response.status})`);
  return response.json();
}

export async function action(type: string, values: Record<string, string | number | boolean | null> = {}): Promise<State> {
  const response = await fetch('/api/actions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, ...values })
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error ?? `Action failed (${response.status})`);
  return payload;
}

export async function uploadRom(file: File): Promise<Bootstrap> {
  const response = await fetch(`/api/load?name=${encodeURIComponent(file.name)}`, { method: 'POST', body: file });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error ?? `ROM load failed (${response.status})`);
  return payload;
}

export async function diagnostics(speciesId?: number | null, moveId?: number | null): Promise<DiagnosticView> {
  const query = new URLSearchParams();
  if (speciesId != null) query.set('speciesId', String(speciesId));
  if (moveId != null) query.set('moveId', String(moveId));
  const response = await fetch(`/api/diagnostics?${query}`);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error ?? `Diagnostics failed (${response.status})`);
  return payload;
}

export function events(onState: (state: State) => void): () => void {
  let inFlight = false;
  const timer = window.setInterval(async () => {
    if (inFlight) return;
    inFlight = true;
    try {
      const response = await fetch('/api/state');
      if (response.ok) onState(await response.json());
    } finally {
      inFlight = false;
    }
  }, 750);
  return () => window.clearInterval(timer);
}
