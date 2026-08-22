# Loading Origin Colour Design

## Goal

Let the user distinguish a full ROM parse from a persisted-catalog reopen at a glance without exposing parser, database, or cache diagnostics.

## Contract

- A full parse renders the current loading message in red.
- A `CACHE_REOPEN` renders the current loading message in yellow.
- The progress bar keeps its existing colour so progress and origin remain separate visual signals.
- The mapping applies to both the full welcome/loading screen and the compact in-app loading indicator.
- Text remains organic and module-specific; no implementation provenance is added.
- Normal accessibility semantics remain intact: the loading message stays the status label, and colour is supplemental rather than the only machine-readable distinction.

## Boundaries

The origin is derived solely from the existing loading phase. No new API field or persisted setting is required. Unknown active phases are treated as full-parse work and therefore use red. Inactive/complete states receive no origin class.

## Verification

Component tests must exercise `ROM_IDENTITY` and `CACHE_REOPEN` on the welcome screen and assert the corresponding classes. The compact indicator receives the same classification helper. Existing module-copy and progress tests must remain unchanged.
