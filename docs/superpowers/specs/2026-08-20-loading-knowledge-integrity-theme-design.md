# DualDex Loading, Knowledge Integrity, and Clock Contrast Design

## Problem

RC21 exposes three related trust and presentation defects:

1. Catalog persistence checkpoints are presented as current parser modules. A checkpoint is emitted only after its work completes, so the label is both coarse and one step behind the work actually running.
2. Organic knowledge is persisted by ROM SHA only even though every validated save already has a stable `saveIdentity`. A new playthrough of the same ROM can therefore inherit seen/caught observations from another save before SaveRAM reconciliation.
3. The clock dial uses fixed pale colors. ROM-derived themes such as Modern Emerald can place it on a similarly bright header, making the orbit and celestial icon difficult to see.

Organic Pokédex rows also duplicate their identity state: the sprite/silhouette already communicates seen/unseen, while persistent eye and negative Poké Ball icons add no action or information.

## Approaches Considered

### Recommended: separate work progress, save-scoped trust, adaptive clock plate

Keep the existing catalog checkpoints for transactional SQLite writes, add an independent current-work event for the load screen, scope durable knowledge to `(ROM SHA, save identity)`, sanitize every restored ledger against the active catalog, and place the clock dial on a compact contrast-controlled plate derived from existing theme variables. This preserves crash safety and legitimate Organic history without trusting another playthrough.

### Reset all knowledge whenever a ROM loads

This is simple and safe but destroys legitimate discoveries on every restart. It conflicts with the purpose of the Organic ledger.

### Keep ROM-scoped knowledge and reconcile later

This preserves existing files but continues showing unverified state between catalog load and save detection. It reproduces the reported corruption-like behavior and is therefore rejected.

## Loading Architecture

`CatalogMaterializationPhase` remains an internal persistence concept. A new immutable work-progress model reports the module that is starting and its position in the complete work sequence. Parser orchestration and catalog materialization publish explicit work items before expensive operations, including:

- ROM identity and family/layout analysis
- core species, move, type, and ability records
- Pokédex entries and sprites
- evolutions and learnsets
- encounters and runtime metadata
- move acquisition and descriptions
- ability descriptions and mechanics
- world and local maps
- trainer/theme assets
- catalog persistence and reopen

The Android runtime forwards work progress to the existing loading state. SQLite writes continue to use the original five checkpoint units. The UI label describes `currentWork`, never the last completed checkpoint. Progress remains bounded and monotonic; modules that do not apply still advance as completed work rather than being presented as successful table resolution.

## Knowledge Integrity

Durable knowledge becomes save-scoped:

- A validated `SaveSnapshot.saveIdentity` is the authority for selecting a persisted ledger.
- On catalog load, the visible ledger starts empty and untrusted. ROM-only schema-3 ledgers are retained on disk for recovery but are not automatically projected into the UI.
- Once a checksum-valid save is selected, the repository reads the matching `(ROM SHA, save identity)` ledger, sanitizes it, and then merges the authoritative current save.
- Save-derived seen, caught, owned, and team state replaces prior values. Organic battle observations and discovered matchups may accumulate only inside the same save lineage.
- If no validated save identity is available, live observations remain session-local and are not written into a durable cross-playthrough ledger.

The sanitizer enforces catalog membership and internal consistency:

- species, move, area, and matchup IDs must exist in the active catalog;
- caught species are also seen;
- owned/team species must be valid;
- observed moves and matchup keys must reference valid species and moves;
- area observations must reference valid encounter base areas.

Repository schema 4 stores both identities and uses the save identity in the filename. Schema 1-3 ROM-only files remain readable only for explicit migration tooling; ordinary runtime restore fails closed to an empty ledger.

## Organic Pokédex Presentation

In Organic mode:

- unseen species remain silhouettes and masked names;
- seen species use the already-defined desaturated sprite treatment;
- the eye icon is omitted for every row;
- a Poké Ball is rendered only for caught species;
- uncaught species have no gray or negative ball placeholder.

Discovered and Hidden modes retain their existing status affordances unless their visibility policy already masks the row.

## Clock Contrast

The dial retains the current single-icon sun-or-moon behavior and position. A small dark translucent plate, outline, and stronger track/icon contrast isolate it from ROM-derived header colors without replacing the theme. Day and night receive distinct accessible colors, while the numeric time remains the visual anchor. The plate must remain compact enough not to collide with the title or header actions at 1240x1080.

## Failure Handling

- Invalid or mismatched knowledge documents return an empty ledger without deleting evidence.
- A missing save identity never falls back to ROM-only durable knowledge.
- Progress-reporting failures cannot affect catalog parsing or persistence.
- Unknown work items fall back to a readable normalized label.
- Theme contrast uses stable local CSS variables and does not inspect ROM identity or hardcode an Emerald exception.

## Verification

The implementation must prove:

1. RED/GREEN loading tests show a current module rather than a completed checkpoint and keep persistence checkpoints unchanged.
2. A second save identity for the same ROM cannot see the first save's ledger.
3. Legacy ROM-only knowledge is not projected automatically.
4. Invalid catalog references are removed while valid same-save Organic observations survive.
5. Current SaveRAM with an empty Pokédex clears stale seen/caught state.
6. Organic rows contain no eye and no negative ball; caught rows retain a positive ball.
7. Clock component/style tests and a real-browser 1240x1080 screenshot demonstrate a legible dial over the ROM-derived Emerald theme.
8. Affected Kotlin suites, web tests, and the production web build pass before commit or release.
