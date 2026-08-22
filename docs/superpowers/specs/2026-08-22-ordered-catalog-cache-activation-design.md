# Ordered catalog cache activation

## Problem

Application startup currently attempts to restore the last catalog before RetroArch has connected and identified the active ROM. The setup coordinator later activates the verified ROM through a second path. Those paths race, exposing the sequence `saved catalog` -> `waiting for ROM` -> `ROM parsing` and may decode the same catalog twice.

The loading colour is also selected too early: verified-ROM activation publishes `ROM_IDENTITY` before it checks the exact SHA-keyed catalog cache. Red therefore does not reliably mean that parsing has started.

## Required behavior

Catalog activation has one ordered owner and one visible sequence:

1. While no ROM is connected and identified, show the existing waiting state. Do not inspect or present the last catalog.
2. After RetroArch identifies and SHA-256-verifies the active ROM, begin the cache check for that exact SHA and show the yellow cache-loading state.
3. On a cache hit, publish the cached catalog without entering a red phase or parsing ROM tables.
4. On a cache miss or rejected cache, begin the real parser and switch to the red from-scratch state when parser work actually starts.
5. Cache and parser failures retain the existing fail-closed behavior.

## Implementation boundary

- Remove the eager `lastCatalogSha256` restore from `DualDexApplication` startup.
- Make `ProductionCompanionRuntime.loadInternal` begin with `CACHE_REOPEN`, because the verified ROM SHA is known at that point and the first operation is `readComplete`.
- Allow the parser's existing work callbacks to replace `CACHE_REOPEN` with the first real parser module when and only when cache reopen returns no catalog.
- Keep the explicit restore methods as non-startup seams; do not introduce a second activation owner.
- Do not change catalog contents, ROM parsing, save checkpoints, or UI page design.

## Verification

Automated tests must prove:

- Application startup does not request an eager catalog restore.
- A verified-ROM cache hit exposes `CACHE_REOPEN`, performs no parse, and completes from the stored catalog.
- A verified-ROM cache miss exposes `CACHE_REOPEN` before the first parser work phase, then completes from parsing.
- The existing runtime and Android unit suites remain green.

Device verification is limited to installing the new RC and observing the ordered loading phases through ADB; it must not operate the game or emulator.
