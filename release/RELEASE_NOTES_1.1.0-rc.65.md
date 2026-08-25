# DualDex 1.1.0-rc.65

RC65 adds decisive pre-UI state evidence for transient live/recovery changes and keeps Pokédex filters visible by moving search and its counter to the bottom of the screen.

## Pre-UI state diagnostics

- Every semantically changed unified game-state snapshot receives a monotonic revision immediately before it reaches the UI.
- Pokédex seen/caught and Party/stored transitions record their source, count, and privacy-safe fingerprint, allowing a brief incorrect count to be distinguished as live decoding or recovery projection.
- The direct `DualDexState` ADB stream identifies the triggering live/recovery lifecycle event without exposing player names, Trainer IDs, save identities or paths, raw memory, decoded text, or species identifiers.
- The same immutable event is retained in the existing bounded two-segment app-private diagnostics log.
- State-change logging does not sample heap, PSS, CPU, GC, or thread metrics for every coordinate update, and diagnostics remain fail-open.

## Pokédex controls

- Filter tabs remain directly below the Pokédex header.
- The species list is the only expanding and scrolling middle region.
- Search and the tab-sensitive counter are docked at the bottom.
- Caught continues to show one total, other searchable tabs show `owned / found`, and Team shows neither search nor counter.
- Organic discovery, filtering, navigation, accessibility labels, and the 60-row virtualization bound are unchanged.

## Validation and delivery

- Focused unified-state and bounded-diagnostics tests pass.
- The complete companion web suite passes with 192 tests across 26 files, and the production web bundle builds successfully.
- Android debug lint, release lint-vital, and RC65 release assembly pass with version code `1010065`.
- Production signing and publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
