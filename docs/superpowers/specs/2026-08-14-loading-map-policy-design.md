# Loading and map-policy design

## Goal

Make catalog loading truthful and make map visibility follow the existing knowledge mode. Remove the redundant fog override and suppress the non-actionable `map location has no encounter area` warning.

## Loading presentation

The existing loading state remains the sole UI contract. The runtime must publish each real `CatalogMaterializationPhase` through `CatalogLoadingChanged`; the web UI converts the phase identifier to a concise module label:

- `IDENTIFYING` — ROM identity
- `ESSENTIAL` — core catalog
- `SPECIES_MEDIA` — sprites & entries
- `RELATIONSHIPS` — evolutions & areas
- `EXTENDED` — extended data
- `CACHE_REOPEN` — saved catalog

The visible message is `Loading <module>`. No percentage is shown because phase work is not evenly weighted. Unknown future phases fall back to a lowercase humanized label.

## Fog policy

The Map page has no manual fog button.

- `DISCOVERED` shows the normalized map without fog.
- `ORGANIC` and `HIDDEN` render fog from the current and persisted visited-area ledger.

This keeps map visibility consistent with the global knowledge mode and prevents a page-local toggle from overriding it. Pan, pinch, recenter, marker controls, and black outer fog edges are unchanged.

## Locations without encounter areas

When the selected normalized map location maps to no encounter-area record:

- Area Dex is visibly disabled and sends no request.
- A stale or direct `MAP_AREA` request is a silent no-op.
- The current physical location and current screen remain unchanged.

Invalid region/location keys remain errors because they indicate a malformed request; only the valid-but-unmapped location case becomes silent.

## Verification

- Runtime tests prove phase publication and silent no-op behavior.
- Web tests prove module labels, fog-by-mode, removal of the fog control, and disabled Area Dex.
- The production web build and focused Android/runtime tests must pass.
- The existing real-browser map contract is rerun if its sanitized fixture is available, with knowledge mode driving the clear/fog state.
