# DualDex 1.1.0-rc.31

RC31 converges three previously separate implementation lines: Theme consolidation, Nature resolution support, and Global Local Map rendering.

## Theme consolidation

- Restores the approved cross-page presentation from the Theme consolidation work, including the white grid-backed Emerald Battle surfaces.
- Restores the clean Pokédex navigation glyph instead of the framed olive fallback.
- Removes ROM filename, family, CRC, and other parser diagnostics from normal production pages. Diagnostics remain confined to Debug Settings.

## Nature resolution support

- Preserves the ROM-derived Gen III Nature catalog, Party Nature links, and Nature detail presentation.
- The converged resolver, catalog model, and Nature UI are byte-identical to the already shipped Nature implementation rather than a parallel replacement.

## Global Local Map rendering

- Preserves RC30's sharp full-resolution Local rasters, connected scenes, player-centered recentering, discovery-safe loading, and ROM-derived trainer marker path.
- Advances the parser cache schema once so an older RC29/RC30 catalog cannot hide newly resolved trainer portraits behind a valid stale cache.
- Modern Emerald remains the real-ROM control: the schema-28 catalog reopens with both 64×64 trainer avatars and its 557 Local maps.

## Delivery

- RC31 is an in-place prerelease update of `com.darkaxt.dualdex`.
- The first ROM load after updating rebuilds the catalog once; subsequent loads use the rebuilt SQLite catalog.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
