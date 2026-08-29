export function appendQueryParameters(
  url: string,
  parameters: Record<string, string | number | boolean | null | undefined>,
): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value != null) query.set(key, String(value));
  }
  const encoded = query.toString();
  if (!encoded) return url;

  const fragmentIndex = url.indexOf('#');
  const base = fragmentIndex >= 0 ? url.slice(0, fragmentIndex) : url;
  const fragment = fragmentIndex >= 0 ? url.slice(fragmentIndex) : '';
  const separator = base.includes('?') ? (base.endsWith('?') || base.endsWith('&') ? '' : '&') : '?';
  return `${base}${separator}${encoded}${fragment}`;
}
