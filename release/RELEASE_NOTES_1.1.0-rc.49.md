# DualDex 1.1.0-rc.49

RC49 corrects the transition between catalog loading and the live companion. It preserves RC48's parser schema and performance changes, so the update itself does not require another catalog rebuild.

## Game initialization

- Completing the game-guide progress bar no longer opens an empty companion while the active game is still at its title or initialization screens.
- The finished bar is replaced by a circular waiting indicator until read-only live memory exposes a valid current area.
- A matching save file alone does not satisfy this gate; the current game session must actually be initialized.
- Generation I, II, and III use their existing live-area readers for the same readiness decision.
- Manually opened ROM catalogs and disconnected use remain available without waiting for RetroArch.

## Stored guide refreshes

- First-time preparation, an outdated or incomplete stored guide, and an unreadable stored guide are distinguished by the cache layer.
- If a stored guide cannot be reused, the loading screen now gives a concise player-facing reason.
- Normal screens do not expose schema numbers, hashes, parser stages, offsets, or other diagnostic details.

## Validation

- All 186 companion-web tests pass, including the bar-to-spinner transition, live-memory release, matched-save rejection, manual-ROM exception, and production-message boundary.
- Focused catalog-store, companion-core, and Android runtime suites pass for cache decisions, Gen I-III live-area readiness, catalog loading, and state projection.
- The production web bundle and Android debug compilation complete successfully.

## Delivery

- RC49 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010049`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
