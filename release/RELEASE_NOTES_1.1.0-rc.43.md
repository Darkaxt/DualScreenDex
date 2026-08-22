# DualDex 1.1.0-rc.43

RC43 completes the cache and navigation continuity fixes while making tracked Local-map movement feel continuous between live coordinate samples.

## Continuity and navigation

- ROM identity is established before a cached guide is opened. Yellow now means that the matching cache is actually being checked; red parsing phases begin only after a genuine cache miss.
- Nested pages use a bounded navigation history. Party → Pokémon → Ability → Back now returns to the same Pokémon details instead of skipping to the Party grid.
- Visual Back and Android companion Back follow the same one-step route order, while an auto-opened battle still takes priority.
- The Party shortcut is now one detailed Poké Ball instead of two ambiguous circles.

## Local-map following

- The trainer position updates immediately while the camera follows through one continuous accelerated motion rather than restarting a short animation for every coordinate poll.
- Nearby coordinate samples preserve camera velocity, preventing the visible start-stop cadence seen during continuous walking.
- Teleports, distant jumps, area changes, reduced-motion mode, and 0% smoothing still snap safely.
- A global Local-map follow smoothing control ranges from 0% Responsive to 100% Smooth and applies consistently to every game. Manual pan or zoom still releases tracking until Recenter is selected.

## Verification

- Complete web UI suite: 176/176 tests passed.
- Focused cache/runtime suite: 53/53 tests passed.
- Affected companion-core, catalog-store, Android unit-test, lint-vital, and unsigned release-assembly gate: passed (88 tasks, zero failures).
- Production web build and release-policy tests: passed.

## Delivery

- RC43 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010043`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The published signed APK is installed without operating RetroArch or the game.
