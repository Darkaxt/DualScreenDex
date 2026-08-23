# DualDex 1.1.0-rc.44

RC44 corrects the catalog reopen failure seen with map-heavy ROMs and restores the trainer and Pokédex artwork expected in Pokémon details.

## Reliable catalog reopening

- Large catalog sections are now stored as ordered Android-safe SQLite chunks instead of one oversized row.
- Existing RC43 catalogs rebuild once under parser schema 33; later launches reopen the completed cache normally.
- Save snapshots remain intact during that one-time catalog migration. ROM catalog caching remains independent of game-save activity.
- Cache absence, incompatibility, successful reopen, and read rejection now have explicit logcat evidence without exposing diagnostics in the normal UI.

## Modern Emerald trainer artwork

- Modern Emerald v3.5 now resolves its compiled adjacent SaveBlock1/SaveBlock2 globals from the real ROM.
- The source-confirmed Emerald trainer fields publish the live gender and the matching ROM-derived 64x64 trainer avatar.
- Height Comparison, Trainer Card, and other trainer-art consumers can therefore use the player's actual trainer artwork instead of the generic fallback after live identity becomes available.

## Party Pokédex action

- Party Pokémon details now use the same shared Pokédex SVG and transparent action treatment as Combat.
- The low-contrast `DEX` text square has been removed without changing its navigation target.

## Verification

- Android-safe large-section persistence, schema migration, save preservation, and cache-decision tests: passed.
- Real Modern Emerald v3.5 runtime control with exact SHA-256 and source-backed trainer offsets: passed.
- Companion trainer-avatar projection and Party/Combat/Pokédex navigation suites: passed.
- Production web build, release policy, Android release compilation, signing, checksum, and provenance remain enforced by the protected release workflow.

## Delivery

- RC44 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010044`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- Device installation and runtime acceptance remain with the user.
