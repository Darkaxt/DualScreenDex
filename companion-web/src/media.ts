export function catalogMediaUrl(url: string, catalogHash: string): string;
export function catalogMediaUrl(url: null | undefined, catalogHash: string): null;
export function catalogMediaUrl(url: string | null | undefined, catalogHash: string): string | null;
export function catalogMediaUrl(url: string | null | undefined, catalogHash: string): string | null {
  if (!url) return null;
  if (/[?&]catalog=/.test(url)) return url;
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}catalog=${encodeURIComponent(catalogHash)}`;
}
