# ROM-derived companion theme verification

## Outcome

DualScreenDex now materializes one complete, deterministic theme from normalized
catalog artwork and persists it with the catalog. The companion applies those
tokens only when `GAME` is selected. `DARK`, `LIGHT`, and high contrast remain
fixed alternatives. Theme discovery is optional: insufficient, malformed,
ambiguous, or exceptional evidence produces the complete neutral theme without
aborting any other catalog feature.

Core/persistence checkpoint: `757185a` (`Persist deterministic ROM-derived themes`).
Final verification was performed on branch `feature/unbound-odyssey-full-compat`
with parser schema 20 and CatalogStore theme-section version 1.

## Exact reference controls

Every control was rehashed, parsed twice from fresh `RomImage` instances, and
compared for complete theme equality. A separate production vertical parsed
each control once, wrote and reopened SQLite, verified database integrity, and
compared the companion API projection with the reopened catalog.

| Control | SHA-256 | Method | Equal-weight normalized contributors | Contrast corrected |
|---|---|---|---|---:|
| Red | `5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b` | `MULTI_ASSET_QUANTIZATION` | WORLD_MAP, SPECIES | yes |
| Crystal Rev 1 | `fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2` | `MULTI_ASSET_QUANTIZATION` | WORLD_MAP, SPECIES | yes |
| Emerald | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` | `MULTI_ASSET_QUANTIZATION` | TRAINER, WORLD_MAP, LOCAL_MAP, SPECIES | yes |
| Unbound 2.1.1.1 | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` | `MULTI_ASSET_QUANTIZATION` | WORLD_MAP, LOCAL_MAP, SPECIES | yes |
| Odyssey 4.1.1 | `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0` | `MULTI_ASSET_QUANTIZATION` | WORLD_MAP, LOCAL_MAP, SPECIES | yes |

Token order below is `field`, `fieldPattern`, `header`, `headerShadow`, `menu`,
`menuShadow`, `panel`, `border`, `text`, `textShadow`, `accent`, `accentText`.

| Control | Exact RGB tokens |
|---|---|
| Red | `4c4c4c 616161 6e6e6e 444444 fcfcfc 929292 fdfdfd 010101 030303 000000 030303 ffffff` |
| Crystal Rev 1 | `e60202 e62019 02dc02 018801 edfcc5 899272 f7fde5 010101 030303 000000 6767fc 000000` |
| Emerald | `0245e6 205ae8 dcdc02 888801 fcfcfc 929292 fdfdfd 010101 030303 000000 356ffb 000000` |
| Unbound 2.1.1.1 | `e63d02 e85320 0e73dd 084789 fcfcfc 929292 fdfdfd 010101 030303 000000 fbd30b 000000` |
| Odyssey 4.1.1 | `0253e6 2067e8 dcc002 887701 fcfcfc 929292 fdfdfd 010101 030303 000000 fb03fb 000000` |

No control is reported as direct-palette evidence or fallback. All five resolved
from at least two independent normalized asset classes.

## Resolution and failure boundaries

- Inputs are decoded normalized rasters, grouped by semantic asset class. ROM
  title, filename, family, SHA, symbols, and fixed ROM addresses are not inputs.
- Sampling is bounded per asset and per class; each class has equal influence,
  so a large species set cannot drown out Trainer or map artwork.
- Transparent pixels are ignored and stable RGB/count ordering makes input
  iteration order irrelevant.
- `DIRECT_UI_PALETTE` requires exactly one complete structurally nominated
  palette. Multiple nominees fall through to multi-asset resolution.
- Multi-asset resolution requires at least two usable classes. Empty, malformed,
  single-class, or unreadable evidence returns `NEUTRAL_FALLBACK`.
- Theme validation requires normal-text contrast on panel, accent, and header,
  plus control-surface contrast. Minimal deterministic correction is recorded.
- Only theme materialization is exception-isolated. A theme failure cannot hide
  an otherwise valid Pokédex, map, evolution, ability, Trainer, or Party catalog.
- Theme is a required schema-20 CatalogStore section; older caches are reparsed.

## Browser contract

Helium ran the production web app at 1024x768 with the exact Emerald theme.
The test covered Pokédex, Pokémon detail, Trainer Card, Party, Battle, loading,
Settings, current local map, Atlas, Pokémon AREA map, Dark, Light, and high
contrast. The same GAME variables remained installed on every applicable
screen; fixed alternatives omitted the ROM variables.

The field pattern uses the exact derived pattern token at 16% opacity, retaining
the intended grid grammar without overwhelming empty panels. Visual inspection
confirmed consistent compact headers, cream surfaces, hard paired borders, and
restrained pixel shadows without layout overflow.

The sanitized map raster remained byte-exact:
`e5a9ea67be860e1110678118267e50b30b4002001d42f12eea8575436283f493`.
Local-map-first navigation, explicit Atlas switching, Pokédex return, Organic
fog with opaque outer edges and a revealed current area, map pixels, and map
geometry remained intact.

Screenshots are stored under `D:\Temp\dualdex-rom-theme-evidence`. Representative
final hashes:

| Screenshot | SHA-256 |
|---|---|
| `pokedex.png` | `5db600dc23c66f0143ad34b3cded8bd28ae04383937d6d3425c8d423ca3727f6` |
| `trainer.png` | `ba3e2d45614a0617bad2887e04b108f960584f876d542833331becafd9c115c7` |
| `party.png` | `9a6a06bf792f9fbf5b08ef48745b59b70d462d6a6dbb7e7215e4bb871af05516` |
| `battle.png` | `abd307b21f4641879704b3b2b694d7f0af8663238ccbd1b5b2482cc807907795` |
| `local-map.png` | `8975bdbb0da54b3c82d98fd4f46d6f211237bca7dd7c55c646fc2b4f6038eb02` |
| `atlas.png` | `62ef29387d953a2c7dee69b62a0dccf023e41dd0262cc8d1638265b0aa00fadd` |
| `dark.png` | `e77d78651c3834aeaed1240e3a69ebd98b41f58edbcf97fa23a9e48f1a1dca2c` |
| `light.png` | `77de9f14b207fd154277d0538c09fbd961d1cc1ffbe298ed53973a79e40ca04a` |
| `high-contrast.png` | `50b3082c74dae59d1a470f782b84637967bfe1ea77b4d4e94d1e862ada93f3b8` |

## Verification

- Exact five-ROM double parse: `RomDerivedThemeLiveRomTest` — 1/1, five
  controls, ten parses; `BUILD SUCCESSFUL in 8m 40s`.
- Exact SQLite/API vertical: `WorldMapCatalogApiRealControlTest.exactReferenceThemesSurviveCatalogStoreAndApiProjection`
  — 1/1, five controls; `BUILD SUCCESSFUL in 5m 52s`.
- Affected Kotlin modules: parser-core 1123 tests, catalog-store 18 tests,
  companion-core 50 tests; zero failures/errors; `BUILD SUCCESSFUL in 7m 35s`.
- Companion web: 21 files / 123 Vitest tests, zero failures; production Vite
  build succeeded.
- Theme Playwright: 1/1 in Helium, including all screens, map/fog preservation,
  fixed alternatives, high contrast, and no overflow.
- `git diff --check` passed. Production theme files contain no ROM identity,
  title, hash, family-theme switch, symbol, or fixed-address selector.
