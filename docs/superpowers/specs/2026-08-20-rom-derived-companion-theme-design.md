# ROM-derived companion theme design

Date: 2026-08-20
Status: approved design, pending implementation plan

## Goal

Replace the current fixed forest/acid `GAME` theme with one stable theme derived from the loaded
ROM's own palette and extracted assets. The result should use the compact mint/cream, hard-border,
game-like visual grammar of `pokeemerald-dualscreen` while allowing every ROM and hack to retain
its own colors.

Navigation, information hierarchy, density, Atlas behavior, Pokédex behavior, Trainer Card, Party,
Battle, settings, and accessibility behavior remain unchanged.

## Selected visual direction

The selected direction is the **Emerald hybrid**:

- compact game-like framing rather than a literal recreation of GBA menus;
- ROM-derived field, header, menu, panel, border, accent, text, and shadow colors;
- subtle low-resolution field texture;
- hard two-level borders and restrained pixel-like shadows;
- current responsive layout, touch targets, icons, and information density;
- no page-specific palette switching.

The visual reference implementation in `pokeemerald-dualscreen` uses mint field colors, green
headers, cream menu bars, hard brown/gray borders, white panels, and text shadows. Those values are
reference evidence for the intended visual grammar, not production defaults selected by an Emerald
identity.

## User-visible behavior

`GAME` becomes the ROM-derived theme. It is resolved once for a catalog and remains stable across
Atlas, local maps, Pokédex, Pokémon details, Trainer Card, Party, Battle, loading, and settings.

The existing `DARK` and `LIGHT` themes remain fixed alternatives. High contrast remains an
independent accessibility override applied after the selected theme. Pokémon type colors, HP
colors, status colors, map rasters, sprites, Trainer Card artwork, and badges remain semantic or
source-rendered assets and are never recolored by the shell theme.

## Catalog contract

Add a persisted `CatalogTheme` containing semantic RGB tokens:

- `field`
- `fieldPattern`
- `header`
- `headerShadow`
- `menu`
- `menuShadow`
- `panel`
- `border`
- `text`
- `textShadow`
- `accent`
- `accentText`

The model also records:

- the resolution method: `DIRECT_UI_PALETTE`, `MULTI_ASSET_QUANTIZATION`, or `NEUTRAL_FALLBACK`;
- the normalized asset classes that contributed evidence;
- whether contrast correction altered any derived token.

The catalog always contains a valid theme. Missing optional theme evidence must never fail base
catalog parsing.

## Resolution pipeline

### 1. Structurally resolved UI palette

Prefer a UI, window, menu, or text palette when its role is structurally proven from loader or
consumer relationships. A candidate is not selected by ROM title, filename, SHA, family signature,
linked address, or source symbol.

Generation-specific palette formats may be decoded, but every resolver terminates in the common
`CatalogTheme` contract:

- GB four-level palette indices;
- SGB/CGB BGR555 palette data;
- GBA BGR555 palette data.

For a DMG-only ROM, the bytes establish ordered intensity levels but not a physical screen hue.
Without SGB/CGB color evidence, the resolver must use a neutral four-tone theme rather than invent
an olive tint.

### 2. Multi-asset quantization fallback

When a direct UI palette is unavailable, build a deterministic palette from several independent
normalized asset classes. Eligible classes include:

- Trainer Card and badge graphics;
- world-map and local-map rasters;
- interface graphics;
- a bounded representative set of species sprites.

No single sprite, map, or transparent image may determine the result. Sampling is capped per asset
class so a large map cannot overwhelm all other evidence. Fully transparent pixels are ignored.
Near-black and near-white samples remain available for text/panel roles but cannot dominate accent
selection. Quantization has no random seed and must return identical tokens for identical catalog
inputs.

### 3. Semantic role assignment

Quantized colors are assigned by luminance, saturation, population, and pairwise contrast:

- field and field-pattern form a low-contrast background pair;
- header is the strongest suitable chromatic surface;
- menu is a lighter complementary or neighboring surface;
- panel is the clearest content surface;
- border and shadows are darker related colors;
- text and accent-text are selected for readability;
- accent remains visually distinct from field, menu, and panel.

Only the emitted theme tokens may be adjusted for contrast; source pixels and palettes remain
unchanged.

### 4. Neutral fallback

If palette evidence is missing, malformed, overwhelmingly transparent, contradictory, or incapable
of producing a readable token set, publish a neutral game-like theme with explicit
`NEUTRAL_FALLBACK` provenance. This is a successful optional fallback, not evidence that the ROM's
palette was decoded.

## Contrast and accessibility

Normal text must reach at least a 4.5:1 contrast ratio against its assigned surface. Large text,
essential borders, and controls must reach at least 3:1. Corrections should preserve source hue and
chroma as far as possible while moving luminance minimally.

The existing high-contrast mode is applied after ROM theme resolution. It may strengthen borders
and replace text/surface pairs, but it does not mutate the persisted catalog theme.

## Persistence and API

Theme resolution runs after the normalized asset-producing catalog phases and before the coherent
catalog snapshot is committed. `CatalogTheme` is persisted in SQLite with a parser-schema bump and
must round-trip byte-for-byte at the token level.

The companion API returns the semantic theme and provenance with catalog presentation data. The
web application maps the tokens to CSS custom properties on the root device shell when
`theme=GAME`. `DARK` and `LIGHT` continue to use their fixed CSS token sets.

## Error handling

- Resolver exceptions become `NEUTRAL_FALLBACK`; they do not abort the catalog.
- Multiple equally valid direct UI palettes fall through to multi-asset quantization rather than
  selecting by address or order.
- Insufficient independent asset classes fall through to neutral fallback.
- Invalid RGB/BGR555 data, impossible dimensions, transparent-only assets, and failed contrast
  correction are rejected.
- The API and web shell accept only complete token sets; partial themes are never rendered.

## Verification

Exact controls will freeze the deterministic token sets and provenance for:

- official Pokémon Red;
- official Pokémon Crystal;
- official Pokémon Emerald;
- Pokémon Unbound v2.1.1.1;
- Pokémon Odyssey v4.1.1.

The verification matrix will include:

- direct UI palette resolution where structurally available;
- multi-asset fallback with reordered input assets producing identical output;
- monochrome, transparent-heavy, near-black, near-white, low-contrast, and contradictory inputs;
- rejection of one-asset dominance;
- SQLite write/reopen equality;
- API token/provenance equality;
- CSS-variable application for `GAME`;
- preservation of fixed `DARK` and `LIGHT` themes;
- high-contrast override behavior;
- representative Atlas, Pokédex, Pokémon detail, Trainer Card, Party, Battle, loading, and settings
  screenshots at the production 4:3 viewport;
- no navigation, interaction, sizing, fog, map, or information-policy regression.

## Non-goals

- Recoloring ROM-derived sprites, maps, badges, or other raster assets.
- Selecting a theme from ROM names, hashes, base-ROM signatures, or hardcoded hack identities.
- Maintaining a manually curated theme database.
- Changing layouts or navigation.
- Animating or changing the palette between pages, locations, battles, or day/night phases.
- Replacing the existing Dark, Light, or high-contrast settings.
