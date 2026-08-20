# Loading Progress Design

## Goal

Replace the Welcome screen's `LOAD ROM OR ZIP` and `CONNECT RETROARCH` actions while startup or catalog loading is active with an honest progress presentation driven by the existing catalog-loading state.

## Behavior

- The Welcome actions are hidden while the initial bootstrap request is pending or `state.loading.active` is true.
- During initial bootstrap, the Welcome screen shows an indeterminate `Loading companion state` bar because no catalog phase has arrived yet.
- During catalog work, the bar label uses the existing phase mapping: ROM identity, core catalog, sprites and entries, evolutions and areas, extended data, or saved catalog.
- When `totalUnits > 0`, the fill is exactly `completedUnits / totalUnits`; no fabricated timer or percentage text is shown.
- When the load ends without a catalog or fails, the existing upload and RetroArch recovery actions return.
- When a catalog becomes available, the normal UI replaces the Welcome screen. Catalog refreshes retain the compact floating loading label rather than covering the active UI.

## Components and data flow

`App` passes the existing `busy` bootstrap flag and `state.loading` to `Welcome`. `Welcome` chooses between `WelcomeLoadingProgress` and the existing action row. No backend, API, catalog, parser, SQLite, RetroArch, or Map contracts change.

## Accessibility

The loading block is a named status region. Its bar uses `role="progressbar"`; determinate phases expose the real minimum, maximum, and current values, while bootstrap loading omits `aria-valuenow` to remain indeterminate.

## Error and recovery behavior

Loading never removes recovery permanently. Buttons return as soon as neither bootstrap nor catalog loading is active. Existing error text remains visible alongside those actions.

## Map scope

The local-map switch remains unchanged. It is the bottom-left `A`/`L` canvas control and appears only when a local-map asset matches the live current base-area ID.

The Atlas header is simplified in the same UI hotfix:

- Remove the generic `WORLD MAP`/ROM-family title because it reads like diagnostics rather than navigation context.
- Keep only the selected location and its `CURRENT` or `MAP POINT` state on the left.
- Place Settings and Area Pokédex actions in the header's right action group, matching the Pokédex header pattern.
- Keep marker visibility and multi-region selection as map-canvas utilities rather than presenting them as global header identity.
- Frame the raster with a fixed warm-brown perimeter line implemented as a non-layout-affecting shadow, so panning exposes an intentional map edge without changing intrinsic dimensions, fit, fog, or gesture math.
