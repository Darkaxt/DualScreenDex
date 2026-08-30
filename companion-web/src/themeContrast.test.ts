import { describe, expect, it } from 'vitest';
import type { CatalogTheme } from './models';
import { contrastRatio, deriveSemanticTheme, semanticThemeCssVariables } from './themeContrast';

type ThemeTokens = CatalogTheme['tokens'];

const modernEmerald: ThemeTokens = {
  field: '#0245e6',
  fieldPattern: '#205ae8',
  header: '#dcdc02',
  headerShadow: '#888801',
  menu: '#fcfcfc',
  menuShadow: '#929292',
  panel: '#fdfdfd',
  border: '#010101',
  text: '#030303',
  textShadow: '#000000',
  accent: '#356ffb',
  accentText: '#000000',
};

const syntheticThemes: ThemeTokens[] = [
  {
    ...modernEmerald,
    header: '#050505', menu: '#080808', panel: '#101010', border: '#111111',
    text: '#171717', accent: '#202020', accentText: '#222222',
  },
  {
    ...modernEmerald,
    header: '#f8f8f8', menu: '#fafafa', panel: '#ffffff', border: '#f0f0f0',
    text: '#eeeeee', accent: '#f6f6f6', accentText: '#f2f2f2',
  },
  {
    ...modernEmerald,
    header: '#ff00aa', menu: '#00ff66', panel: '#00ddff', border: '#00ccee',
    text: '#00eedd', accent: '#ff3300', accentText: '#ff6600',
  },
  {
    ...modernEmerald,
    header: '#7c7c7c', menu: '#808080', panel: '#848484', border: '#888888',
    text: '#828282', accent: '#868686', accentText: '#848484',
  },
];

describe('semantic theme contrast', () => {
  it('turns the exact Modern Emerald palette into readable semantic pairs', () => {
    const semantic = deriveSemanticTheme(modernEmerald);

    expect(semantic.primary).toMatchObject({ background: '#356ffb', foreground: '#000000' });
    expect(semantic.secondary).toMatchObject({ background: '#dcdc02', foreground: '#000000' });
    expect(semantic.surface).toMatchObject({ background: '#fdfdfd', foreground: '#030303' });
    expectSemanticContrast(semantic);

    const css = semanticThemeCssVariables(semantic);
    expect(css['--semantic-primary-bg']).toBe('#356ffb');
    expect(css['--semantic-secondary-fg']).toBe('#000000');
    expect(css['--semantic-surface-fg']).toBe('#030303');
  });

  it('corrects near-black, near-white, saturated, and low-separation token sets', () => {
    for (const tokens of syntheticThemes) expectSemanticContrast(deriveSemanticTheme(tokens));
  });

  it('keeps destructive and status actions independent from ROM decoration', () => {
    const first = deriveSemanticTheme(modernEmerald);
    const second = deriveSemanticTheme(syntheticThemes[2]);

    expect(first.danger).toEqual(second.danger);
    expect(first.status).toEqual(second.status);
  });

  it('fails closed to fixed semantic surfaces when theme colors are malformed', () => {
    const malformed = {
      ...modernEmerald,
      header: 'not-a-color',
      panel: '#12',
      accent: 'transparent',
    };

    const semantic = deriveSemanticTheme(malformed);
    expect(semantic.primary.background).toMatch(/^#[0-9a-f]{6}$/i);
    expect(semantic.secondary.background).toMatch(/^#[0-9a-f]{6}$/i);
    expect(semantic.surface.background).toMatch(/^#[0-9a-f]{6}$/i);
    expectSemanticContrast(semantic);
  });
});

function expectSemanticContrast(semantic: ReturnType<typeof deriveSemanticTheme>) {
  for (const pair of [semantic.primary, semantic.secondary, semantic.selected, semantic.surface, semantic.danger, semantic.status, semantic.header]) {
    expect(contrastRatio(pair.foreground, pair.background), `${pair.foreground} on ${pair.background}`).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(pair.border, pair.background), `${pair.border} boundary on ${pair.background}`).toBeGreaterThanOrEqual(3);
  }
  expect(contrastRatio(semantic.focus, semantic.surface.background)).toBeGreaterThanOrEqual(3);
}
