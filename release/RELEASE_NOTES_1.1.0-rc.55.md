# DualDex 1.1.0-rc.55

RC55 reduces steady-state parsing and UI update churn and adds bounded performance evidence for user-driven Android validation.

## Runtime profiling

- Load stages and minute-level runtime samples are recorded as structured NDJSON with Java, native, PSS, CPU, GC, thread, WebView, server, map-cache, mapper, live-memory, and state-routing metrics where available.
- Profiler retention is capped at 1 MiB across two app-private segments and failures cannot abort ROM loading or live play.
- Export remains available only from the Debug section of Settings. No profiler, parser, ROM-identity, or diagnostic text is added to normal pages.

## Reduced live-state churn

- The unified decoder now identifies exact Recovery, Player, Party, Overworld, and Battle changes instead of notifying every consumer for every sample.
- Semantic no-ops do not advance the gateway version or trigger listeners.
- After game initialization, seconds-only clock updates no longer cause a web-state body or client update while the live seconds remain available to the readiness gate.

## Memory and decode reuse

- Each core-memory read session reuses one bounded packet scratch buffer.
- Completed logical-region arrays transfer to the consumer without cloning and are never mutated after terminal completion.
- Gen III Player/Pokédex, Party, Overworld, and Bag/event-flag translations are reused using context-scoped SHA-256 fingerprints over only their consumed source slices.
- Raw WRAM/EWRAM regions are not retained by the translated-value cache.

## Compatibility and validation

- The complete host gate passed with zero test failures and zero Android lint errors.
- Exact live-state controls passed for all 11 official Gen I–III ROMs plus Modern Emerald 3.5, Unbound 2.1.1.1, and Odyssey 4.1.1.
- All in-scope official/Modern/Unbound/Odyssey world/local-map persistence controls passed, as did deterministic Unbound/Odyssey map reconstruction and the selected ROM-native Nature controls.
- Android process, renderer, cold/cache, and long-session profiler evidence remains intentionally pending user-driven RC55 testing; this prerelease is not described as device-runtime validated.

## Delivery

- RC55 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010055`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
