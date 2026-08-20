# DualDex 1.1.0-rc.19

RC19 adds live time-of-day rendering for Gen II and source-backed Gen III Local maps without multiplying stored PNGs. It retains RC18's completed Pokémon Unbound/Odyssey support, ROM-derived GAME theme, Trainer Card, richer Party view, Atlas/local maps, archive loading, display recovery, double-battle ownership, and privacy behavior.

## Dynamic Gen II Local maps

- Official Pokémon Gold, Silver, and Crystal Local maps now store one compressed indexed raster per map with MORNING, DAY, NIGHT, and DARK palettes.
- A structurally resolved, bounded one-byte WRAM selector drives the shared game-clock state. Invalid or unavailable lighting falls back to DAY.
- Map-header lighting policies preserve forced indoor, cave, and other non-automatic lighting behavior.
- Android and desktop endpoints render PNGs lazily and vary their ETags by effective lighting without resetting map pan, zoom, selection, or player position.

## Source-backed Gen III timed maps

- Modern Emerald 3.5 natural-light maps now store one compressed 256-index raster plus base/alternate palettes and a structurally decoded time-blend model.
- Production recognition requires a unique compiled normal/bright lighting-table pair with independent pointer evidence. It does not use ROM filenames, hashes, fixed offsets, per-ROM profiles, or hack allowlists.
- The renderer reproduces alternate-palette mixing and the night/twilight/day tint schedule from numeric game time. Route 102 was verified at noon, evening, twilight, and night.
- Official Ruby, Sapphire, Emerald, FireRed, and LeafGreen retain exact static Local-map output. Indoor, unsupported, or ambiguous maps safely remain static PNGs.

## Persistence and failure isolation

- Catalog schema 21 persists Gen II indexed rasters, Gen III timed rasters, and RC18 themes together.
- Invalid/incomplete numeric time is rejected without affecting runtime health. Corrupt or unavailable optional Local-map assets disable only that response/capability; Pokédex, battle, Trainer, Party, Atlas, and theme functionality remain available.
- The existing `gameTime` API and clock widget remain the single source of time state for both generations.

## Verification and delivery

- The full parser gate passed 1,138 tests with zero failures/errors. Nine exact Local-map controls passed across official Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, and Modern Emerald.
- The integrated catalog-store, companion-core, companion-server, and Android-host gates covered 254 test cases: 225 executed and 29 optional external-fixture controls skipped, with zero failures/errors.
- The web gate passed 126 Vitest tests and the TypeScript/Vite production build.
- The production APK is built and signed only by the protected GitHub workflow. It is not installed or launched by this release task; device acceptance remains with the user.
