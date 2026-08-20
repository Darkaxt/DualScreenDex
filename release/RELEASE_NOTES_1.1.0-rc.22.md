# DualDex 1.1.0-rc.22

RC22 makes loading progress truthful, prevents one save file from inheriting another playthrough's Pokédex knowledge, and simplifies Organic-mode status presentation. It retains RC21's Radical Red correction and the completed v1.1 companion feature set.

## Trusted loading and knowledge

- The loading bar now names the module currently being decoded: ROM identity, data layout, records, sprites, evolutions and learnsets, encounters, moves, abilities, maps, trainer/theme, and catalog storage.
- The existing five SQLite checkpoint writes remain independent and crash-safe; completed persistence checkpoints no longer masquerade as current parser work.
- Persistent knowledge is keyed by both ROM SHA-256 and validated save identity. Opening a catalog starts from an empty ledger until a checksum-valid save is accepted.
- The current save replaces seen, caught, owned, and team truth. Same-save observations can persist, while stale species, move, area, and matchup references are removed against the active catalog.
- Legacy ROM-only knowledge files remain untouched on disk but are no longer automatically trusted.

## Organic presentation and clock visibility

- Organic mode no longer repeats identity knowledge with eye icons or negative Poké Ball placeholders.
- A Poké Ball is displayed only for a confirmed capture, including ROM-derived ball artwork when available.
- The clock and day/night orbit use an adaptive dark contrast plate, stronger track, and brighter celestial artwork so ROM-derived themes cannot wash them out.

## Delivery

- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- RC22 is an in-place prerelease update of `com.darkaxt.dualdex`; no device installation is performed by the build workflow.
