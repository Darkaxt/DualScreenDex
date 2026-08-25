# DualDex 1.1.0-rc.63

RC63 makes the unified game-state snapshot the sole authority for transient data and removes the parallel state paths that could make Pokédex, Trainer Card, Party, battle, and Atlas disagree.

## Unified transient state

- Trainer Card, Pokédex, Party and ownership, bag, event flags, battle, battle observations, area, position, clock, readiness, and recovery are projected atomically from one resolved snapshot.
- Live values replace matching recovery values, including validated empty Pokédex and Party collections; stale recovery can no longer remain visible beside newer live state.
- One Pokédex flag resolves to one base-form catalog species. A starter flag can no longer expand into dozens of alias/form rows.
- The old battle callback, section-specific resolved actions, mirrored Trainer fields, saved-Party merge, and normal-UI ledger caught/owned fallbacks have been removed across Android and the companion server.
- Checkpoint persistence remains save-synchronized: only a validated same-playthrough changed save freezes and writes knowledge. Initial, unchanged, live-memory, and recovery events do not write.

## Compatibility evidence

- Battle, area, and position publish through the same snapshot for all official Gen I–III controls, Modern Emerald, Unbound, and Odyssey.
- Gen II time-of-day publishes through that snapshot. Unsupported Gen I/II fields remain explicitly unavailable and independently recoverable.
- All nine measured live groups resolve for Ruby, Sapphire, Emerald, Modern Emerald, and Unbound; eight of nine resolve for FireRed, LeafGreen, and Odyssey because no source-proven live clock is published.
- The complete machine-readable and human-readable transient-state matrices are included with the release evidence.

## Validation and delivery

- 1,804 Gradle/JUnit tests completed with zero failures and zero errors; Android lint completed with zero errors.
- 191 web tests, the production web build, 18 release-policy tests, secure dependency verification, and Android deployment-safety checks passed.
- RC63 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010063`.
- Production signing and publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
