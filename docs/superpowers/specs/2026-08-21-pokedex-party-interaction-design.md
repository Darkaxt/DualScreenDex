# Pokédex Badge and Party Roster Interaction Design

## Goal

Refine the remaining checklist-like Pokédex, Party, and Trainer Card presentation without redesigning navigation, data flow, theming, or unrelated screens:

- an affirmative caught badge attached to the Pokémon portrait;
- a full six-slot Party board arranged as two columns by three rows, with details opened only after selecting a Pokémon.
- a single cohesive Trainer Card composition containing every field already published by DualDex.

The supplied Pokémon Emerald Dual Screen screenshot is the visual reference. DualDex keeps its ROM-derived theme and multi-ROM data model rather than copying Emerald-specific colors or assets.

This is a bounded layout pass, not a full UI redesign. Existing header actions, routes, dynamic theme variables, state ownership, and parser/runtime contracts remain unchanged.

## Pokédex caught state

The caught Poké Ball is a compact badge at the bottom-right corner of the portrait in both Pokédex browse rows and the species detail identity card. The badge is rendered only when `SpeciesState.caught` is true. When the catalog has the captured ball artwork, the badge uses it; otherwise it uses the existing generic caught-ball mark.

The right edge of a Pokédex row remains available for encounter-window information and the explicit eye state outside Organic mode. Organic mode continues to omit the eye entirely. No negative or empty Poké Ball placeholder is rendered in any mode.

## Party board

The Party page gives the six stable party positions to one two-column grid. The grid never becomes a six-row list. Each occupied card contains:

- portrait or truthful fallback/silhouette;
- nickname/species name and level;
- a thin blue experience-progress line, directly above the HP bar and half its height, when normalized progress is available;
- HP value and proportional HP bar when both values are available;
- status text when present.

Empty positions remain visible as subdued open slots so party order stays legible.

There is no persistent detail panel beside or below the board. Selecting an occupied card opens a focused detail layer over the board. The existing nature, ability, held item, experience, stats, moves, type artwork, status artwork, and Pokédex link remain available inside that layer. A close control returns to the unchanged roster. Empty cards cannot open details.

## Interaction and accessibility

- Party cards remain real buttons with slot-aware accessible labels.
- The detail layer uses dialog semantics, names itself from the selected Pokémon, and exposes a visible close button.
- Escape closes the detail layer.
- Moving to a move, ability, or Pokédex destination uses the existing callbacks and routing.
- Live party refreshes close the layer if its selected slot becomes empty.
- Missing HP, types, sprites, held-item state, or other optional data remains explicitly unavailable and is never invented.

## Trainer Card

The Trainer Card keeps the existing name, avatar, public Trainer ID, gender, money, play time, Pokédex seen/caught totals, card stars, and eight badge positions. Those values are composed inside one `.trainer-card-shell` instead of three unrelated panels:

- card label and Trainer ID form the top strip;
- identity and avatar share the central card body;
- money, play time, seen, caught, and stars use aligned card rows;
- all eight badge positions form the bottom card strip.

Unavailable avatar, star, and badge artwork behavior remains unchanged. No new trainer fields are inferred.

## Responsive behavior

The 2×3 Party topology is preserved at the secondary-display sizes supported by DualDex. Compact layouts reduce card padding, sprite size, and typography rather than changing to one column. The detail layer scrolls independently if its content exceeds the available height.

## Verification

Automated component tests will prove:

- caught badge placement on browse and detail portraits;
- absence of an uncaught badge;
- Organic eye suppression and affirmative-only Poké Ball behavior;
- exactly six Party cards in a two-column board contract;
- no detail content before selection;
- click-to-open, close-button, Escape, empty-slot, and disappearing-slot behavior;
- existing move, ability, species, partial-data, silhouette, status, and held-item behavior.
- one Trainer Card shell contains all previously displayed fields and eight badge positions.

The complete companion-web suite and production build must pass before release preparation.
