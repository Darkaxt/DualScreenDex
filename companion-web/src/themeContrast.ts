import type { CatalogTheme } from './models';

export interface SemanticColorPair {
  background: string;
  foreground: string;
  border: string;
}

export interface SemanticTheme {
  primary: SemanticColorPair;
  secondary: SemanticColorPair;
  selected: SemanticColorPair;
  surface: SemanticColorPair;
  danger: SemanticColorPair;
  status: SemanticColorPair;
  header: SemanticColorPair;
  focus: string;
}

const DARK_NEUTRAL = '#07170f';
const LIGHT_NEUTRAL = '#fffde8';
const BLACK = '#000000';
const WHITE = '#ffffff';
const DANGER_BACKGROUND = '#8d2428';
const STATUS_BACKGROUND = '#805500';

export function deriveSemanticTheme(tokens: CatalogTheme['tokens']): SemanticTheme {
  const primary = semanticPair(tokens.accent, tokens.accentText, tokens.border, '#1f6049');
  const secondary = semanticPair(tokens.header, tokens.accentText, tokens.border, '#134535');
  const surface = semanticPair(tokens.panel, tokens.text, tokens.border, '#f1efd8');
  return {
    primary,
    secondary,
    selected: { ...primary },
    surface,
    danger: semanticPair(DANGER_BACKGROUND, LIGHT_NEUTRAL, LIGHT_NEUTRAL, DANGER_BACKGROUND),
    status: semanticPair(STATUS_BACKGROUND, LIGHT_NEUTRAL, LIGHT_NEUTRAL, STATUS_BACKGROUND),
    header: semanticPair(tokens.header, tokens.accentText, tokens.border, '#134535'),
    focus: readableColor(surface.background, tokens.accent, 3),
  };
}

export function semanticThemeCssVariables(theme: SemanticTheme): Record<string, string> {
  return {
    '--semantic-primary-bg': theme.primary.background,
    '--semantic-primary-fg': theme.primary.foreground,
    '--semantic-primary-border': theme.primary.border,
    '--semantic-secondary-bg': theme.secondary.background,
    '--semantic-secondary-fg': theme.secondary.foreground,
    '--semantic-secondary-border': theme.secondary.border,
    '--semantic-selected-bg': theme.selected.background,
    '--semantic-selected-fg': theme.selected.foreground,
    '--semantic-selected-border': theme.selected.border,
    '--semantic-surface-bg': theme.surface.background,
    '--semantic-surface-fg': theme.surface.foreground,
    '--semantic-surface-border': theme.surface.border,
    '--semantic-danger-bg': theme.danger.background,
    '--semantic-danger-fg': theme.danger.foreground,
    '--semantic-danger-border': theme.danger.border,
    '--semantic-status-bg': theme.status.background,
    '--semantic-status-fg': theme.status.foreground,
    '--semantic-status-border': theme.status.border,
    '--semantic-header-bg': theme.header.background,
    '--semantic-header-fg': theme.header.foreground,
    '--semantic-header-border': theme.header.border,
    '--semantic-focus': theme.focus,
  };
}

export function contrastRatio(first: string, second: string): number {
  const firstRgb = parseHexColor(first);
  const secondRgb = parseHexColor(second);
  if (!firstRgb || !secondRgb) throw new TypeError('contrast colors must be opaque hex values');
  const firstLuminance = relativeLuminance(firstRgb);
  const secondLuminance = relativeLuminance(secondRgb);
  return (Math.max(firstLuminance, secondLuminance) + .05) / (Math.min(firstLuminance, secondLuminance) + .05);
}

function semanticPair(background: string, preferredForeground: string, preferredBorder: string, fallbackBackground: string): SemanticColorPair {
  const safeBackground = normalizeHexColor(background) ?? fallbackBackground;
  return {
    background: safeBackground,
    foreground: readableColor(safeBackground, preferredForeground, 4.5),
    border: readableColor(safeBackground, preferredBorder, 3),
  };
}

function readableColor(background: string, preferred: string, minimum: number): string {
  const safePreferred = normalizeHexColor(preferred);
  if (safePreferred && contrastRatio(safePreferred, background) >= minimum) return safePreferred;
  const darkRatio = contrastRatio(DARK_NEUTRAL, background);
  const lightRatio = contrastRatio(LIGHT_NEUTRAL, background);
  const neutral = darkRatio >= lightRatio ? DARK_NEUTRAL : LIGHT_NEUTRAL;
  if (Math.max(darkRatio, lightRatio) >= minimum) return neutral;
  return contrastRatio(BLACK, background) >= contrastRatio(WHITE, background) ? BLACK : WHITE;
}

function normalizeHexColor(value: string): string | null {
  const rgb = parseHexColor(value);
  if (!rgb) return null;
  return `#${rgb.map(channel => channel.toString(16).padStart(2, '0')).join('')}`;
}

function parseHexColor(value: string): [number, number, number] | null {
  const match = value.trim().match(/^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i);
  if (!match) return null;
  const hex = match[1];
  if (hex.length === 8 && hex.slice(6).toLowerCase() !== 'ff') return null;
  const rgb = hex.length === 3
    ? hex.split('').map(channel => Number.parseInt(channel + channel, 16))
    : [0, 2, 4].map(index => Number.parseInt(hex.slice(index, index + 2), 16));
  return rgb as [number, number, number];
}

function relativeLuminance(rgb: [number, number, number]): number {
  const [red, green, blue] = rgb.map(channel => {
    const normalized = channel / 255;
    return normalized <= .04045 ? normalized / 12.92 : ((normalized + .055) / 1.055) ** 2.4;
  });
  return .2126 * red + .7152 * green + .0722 * blue;
}
