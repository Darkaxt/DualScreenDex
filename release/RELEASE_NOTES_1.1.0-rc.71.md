# DualDex 1.1.0-rc.71

RC71 hardens game discovery and guide loading so Android storage indexing and memory pressure no longer look like revoked access or repeatedly crash the companion.

## Storage and game discovery

- Keep Android All Files Access shown as ready while DualDex discovers games.
- Track permission and game-index state independently, including a clear finding-games phase.
- Preserve the last usable direct index when a refresh fails and retain the selected-folder index as a fallback.
- Replace raw indexing exceptions and internal state names with concise player-facing messages.

## Recoverable guide loading

- Contain both ordinary guide-load failures and memory exhaustion at the ROM-source and parser boundaries.
- Keep incomplete parser checkpoints out of the active guide and withhold the previous ROM's catalog during a game switch.
- Stop the connection heartbeat from reopening the same failed source repeatedly.
- Offer `RETRY OPENING GAME GUIDE` only after a guide failure; retry affects the currently resolved ROM and does not add another timer or poller.
- Keep failure stage and class in the existing Debug performance section while ordinary pages show only recovery guidance.

## Current master integration

- Include the current master decoder for aligned hybrid move records used by ROM hacks with mixed-width move layouts.

## Validation and delivery

- All 215 companion browser tests pass across 29 files and the production web bundle builds successfully.
- Android app tests, catalog transaction tests, lint, and release assembly pass together, including the broad real-ROM unified-state control.
- All 18 release-policy tests pass.
- RC71 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010071`.
- DualDex remains read-only. No device or emulator was used during implementation or publication.
