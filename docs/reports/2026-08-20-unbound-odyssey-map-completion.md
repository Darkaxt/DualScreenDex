# Unbound and Odyssey map completion

Date: 2026-08-20

This report freezes the real-ROM world-map and local-map completion gate for Pokémon Unbound v2.1.1.1 and Pokémon Odyssey v4.1.1. It counts only normalized catalogs that parse twice identically and survive catalog persistence and the runtime PNG API. A typed failure or an empty fallback is not counted as support.

## Exact controls and source authority

| Control | Exact SHA-256 | Source authority |
|---|---|---|
| Pokémon Unbound v2.1.1.1 | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` | Dynamic-Pokemon-Expansion Unbound branch `fe058e0`; Complete-Fire-Red-Upgrade `b637a27` |
| Pokémon Odyssey v4.1.1 | `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0` | Creator-authored Odyssey v4.1.1 workbook `31b1eff`; official FireRed loader and map-structure source |

The available repositories are semantic source authorities, not exact whole-ROM link maps. Production does not select by ROM name, title, SHA, source symbol, or fixed address.

## Numeric result

| ROM | Before WORLD | After WORLD | Before LOCAL | After LOCAL | Parser capabilities |
|---|---:|---:|---:|---:|---:|
| Unbound | 0 regions | 1 region / 1 asset | 0 maps | 294 maps / 258 unique assets | 20/23 -> 22/23 |
| Odyssey | 0 regions | 4 regions / 4 assets | 0 maps | 168 maps / 147 unique assets | 20/23 -> 22/23 |

The remaining parser capability for both controls is ability mechanics; maps are no longer part of the missing set.

## Structural resolution

### Unbound

- The compiled `gMapGroups[group][map]` consumer loads an encoded group pointer, applies its ROM-derived key, then indexes the ordinary map-header pointer array. The table root and key are both derived from that decoded consumer.
- The world-map loader resolves the retained crop-complete text plane, graphics, palette, and background role. Identical static region slots collapse to one normalized Borrius region only after their complete asset identities agree.
- The local-map catalog begins with every encounter-backed map and follows source-defined `WarpEvent` and `MapConnection` destinations. This retains 294 statically reachable maps without packaging unrelated editor or retired layouts.

### Odyssey

- The FRLG-style loader proves the graphics background argument, palette destination, four region destinations, and separate background destination from decoded call roles.
- The 660-byte semantic layout is decoded as the source-defined 22x15 map layer followed by the 22x15 dungeon layer. A required section uses the dungeon layer only when absent from the primary layer.
- The same encounter-seeded warp/connection graph retains 168 statically reachable local maps.

The existing limits remain unchanged: two million pixels per map, 100 million unique local-map pixels in aggregate, and 64 MiB of unique encoded PNG assets. Byte-identical PNGs share one asset key. There is no stock-map fallback and no budget bypass.

## Deterministic hashes

| Control | World projection SHA-256 | Local projection SHA-256 |
|---|---|---|
| Unbound | `2faad5f33b96a4401b119317bc76ce966fc69fc5add5c3caa714a9fef03bc97c` | `46e9af4157e5f738fd771c9767e749756c432b1bc4225243b08f97d974c75e26` |
| Odyssey | `68f9954e1038db0dd7b2c88bb157c945680c98d773b9b90830089936587796ae` | `77def226bbffab306160553f3a24d53a093002ea2647978849b920edcc89cb90` |

World PNG SHA-256:

- Unbound: `47e97e55526df3a85db6776d3554d84169f21d1618468fac2592238ef2e5cc7d`
- Odyssey region 0: `70b94d44f4ee45651b3147395b7f40a65092e8774c84fd3b94c23647f1ae417a`
- Odyssey region 1: `7532f93f3c1070c8fbd341315981753cb3df60dce8d8e048f49ee6b9d76bcc33`
- Odyssey region 2: `790abca2ec290c272f3a99f678158ef14c7fa615316f823574b4b107e9a0ffa7`
- Odyssey region 3: `5ecec734a5eb76c0d59997fd95151083033cc910f03814135a4f0968805d18c5`

Both catalogs were parsed twice from independently read ROM byte arrays and produced identical ordered world and local projections.

## Persistence, API, and collateral controls

- Each exact catalog was written to SQLite, reopened, loaded into `ProductionCompanionRuntime`, and served through `/api/maps/{key}.png`; all world PNGs and representative local PNGs remained byte-exact.
- Official FireRed and LeafGreen retain 4 exact world regions and 425 local maps each.
- The source-defined FRLG dungeon layer also corrects Dark Cry's fourth encounter-backed region. Dark Cry now resolves 4 regions with location counts 28/3/2/1 instead of being incorrectly rejected for a missing primary-layer binding.
- The normalized Unbound Borrius raster and all four Odyssey rasters were visually inspected as coherent maps with correct intrinsic geometry.

## Verification

The completion gate includes:

- exact Unbound and Odyssey two-pass parser controls;
- official FireRed and LeafGreen world/local controls;
- Dark Cry source-layer regression;
- catalog materialization controls;
- SQLite reopen and Android loopback PNG-byte controls;
- focused world/local map unit suites;
- `git diff --check` and a production-selector scan.
