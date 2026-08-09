import type { Bootstrap, State } from './models';

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

export function events(onState: (state: State) => void): () => void {
  const stream = new EventSource('/api/events');
  stream.onmessage = event => onState(JSON.parse(event.data));
  return () => stream.close();
}
