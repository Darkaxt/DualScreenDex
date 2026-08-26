# README Live Feature Tour Design

**Date:** 2026-08-26

## Objective

Replace the seven-image promotional gallery with structured feature documentation. The README must show every materially different tab captured in the verified RC66 recording, group those captures by product feature, and explain what DualDex resolves for each feature.

This design supersedes the gallery layout in `2026-08-26-readme-live-capture-gallery-design.md`; it preserves the verified recording and existing assets while expanding their documentation role.

## Source contract

- Recording: `screen-20260826-111430.mp4`
- SHA-256: `B485F6F0CD2BCA3BB773A4567611F1593C30932F344EE523458BF81E62C09053`
- Installed binary at recording time: `v1.1.0-rc.66`, version code `1010066`
- Image format: source-resolution 1240 × 1080 lossless WebP
- Example session: real Modern Emerald gameplay on an AYN Thor

Frame order remains authoritative because the source recording contains discontinuous presentation timestamps.

## Feature categories and captures

### Local Map

One capture shows the rendered Route 101 local map, trainer sprite, current clock, controls, and a discovered location label.

| Asset | Source frame |
| --- | ---: |
| `dualdex-rc66-local-map.webp` | 5105 |

The paragraph explains that ROM-derived map graphics and events are combined with live player coordinates, Organic discovery, fog-of-war state, POI categories, and follow/recenter controls.

### Wild Encounter

All four battle tabs are shown as a two-by-two grid.

| Tab | Asset | Source frame |
| --- | --- | ---: |
| Entry | `dualdex-rc66-wild-entry.webp` | 6168 |
| Attack | `dualdex-rc66-wild-attack.webp` | 6490 |
| Rarity | `dualdex-rc66-wild-rarity.webp` | 5799 |
| Moves | `dualdex-rc66-wild-moves.webp` | 7084 |

The paragraph explains automatic live target resolution, Organic knowledge gating, selected-move metadata/effectiveness, IV/level recruitment rating, and observed-move history without exposing the opponent's unrevealed loadout.

### Pokédex

The browser and every captured detail tab are shown. Entry and More each use two captures because their vertically separated subfeatures cannot be represented legibly in one lower-screen frame.

| View | Asset | Source frame |
| --- | --- | ---: |
| Browser | `dualdex-rc66-pokedex-browser.webp` | 7624 |
| Entry text | `dualdex-rc66-pokedex-entry.webp` | 9703 |
| Height comparison | `dualdex-rc66-height-comparison.webp` | 9792 |
| Stats | `dualdex-rc66-pokedex-stats.webp` | 10200 |
| Moves | `dualdex-rc66-pokedex-moves.webp` | 10238 |
| Area | `dualdex-rc66-pokedex-area.webp` | 11517 |
| More: ability behavior | `dualdex-rc66-ability-behavior.webp` | 10757 |
| More: evolutions and locations | `dualdex-rc66-pokedex-evolutions.webp` | 10856 |

The paragraph explains the ROM-derived species catalog, live seen/caught/team state, tab-specific Organic disclosure, trainer-relative height visualization, stat projection, learnsets, habitat rendering, parsed ability conditions, and evolutions.

### Party

The live party overview and its three captured drill-downs are shown as a two-by-two grid.

| View | Asset | Source frame |
| --- | --- | ---: |
| Overview | `dualdex-rc66-party-overview.webp` | 8949 |
| Pokémon detail | `dualdex-rc66-party-detail.webp` | 8361 |
| Nature detail | `dualdex-rc66-nature-detail.webp` | 8533 |
| Ability detail | `dualdex-rc66-party-ability-detail.webp` | 8770 |

The paragraph explains that the unified live snapshot supplies party membership and current Pokémon fields, while the ROM catalog supplies translated nature effects and parsed ability behavior.

### Trainer Card

One approved, unredacted capture shows the live card.

| Asset | Source frame |
| --- | ---: |
| `dualdex-rc66-trainer-card.webp` | 7925 |

The paragraph explains the live identity, money, play time, Pokédex totals, trainer sprite, and badge state supplied through the unified snapshot. The recording owner explicitly approved publishing the visible card fields.

## README layout

- Keep the `Thor-first UI direction` introduction.
- Add `## Live feature tour` immediately after it.
- Use `###` headings for the five feature categories.
- Put one concise explanatory paragraph directly below each heading.
- Use a common 92% row width: a centered 92% image for a single view or two 46% images for a two-column row. Keep captions naming every tab or subview.
- End the tour with one provenance sentence covering signed RC66, the real Modern Emerald session, ROM-derived catalog data, and the unified live snapshot.
- Keep the later architecture and compatibility documentation intact for this change.

## Presentation boundaries

- Include every captured product tab listed above; do not substitute a generic representative gallery.
- Do not include the Android performance panel, setup controls, debug pages, or diagnostic text.
- Do not include loading transitions because they are not tabs in the requested feature tour.
- Do not synthesize or edit gameplay values.
- Do not claim that this single Modern Emerald session proves equivalent runtime support for every ROM family.

## Acceptance criteria

- The README contains five feature categories and 18 unique RC66 image references.
- Wild Encounter shows Entry, Attack, Rarity, and Moves.
- Pokédex shows Browser, Entry text, height comparison, Stats, Moves, Area, ability behavior, and evolutions/locations.
- Party shows overview, Pokémon detail, Nature detail, and Ability detail.
- Local Map and Trainer Card each have their own category and paragraph.
- All 18 referenced files exist as 1240 × 1080 lossless WebP assets.
- Every image has descriptive alt text and a visible caption.
- Single-image and two-image rows occupy the same 92% content width.
- The original seven-image generic gallery is absent.
- No system/debug frame is published.
- `git diff --check` passes and only the README, 11 new images, the design, and the plan change.
