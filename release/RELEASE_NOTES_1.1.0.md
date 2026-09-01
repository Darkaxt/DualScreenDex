# DualDex 1.1.0

DualDex 1.1.0 expands the passive companion from its v1.0 catalog baseline into a richer read-only live experience for Pokémon games from Game Boy through Game Boy Advance. It includes the complete product tree promoted as signed candidate `v1.1.0-rc.86`.

## Live companion and reliability

- Strengthen read-only Gen I–III live-state authority, battle lifecycle handling, party updates, trainer identity, game clock, and save-synchronized knowledge without sending game commands or writing emulator memory.
- Keep ROM, SaveRAM, and validated live WRAM roles explicit; unavailable or ambiguous optional modules fail closed while the core Pokédex remains usable.
- Harden catalog persistence, cache invalidation, setup recovery, bounded reads, session fencing, and clean-start behavior.

## Maps, Atlas, and area knowledge

- Add ROM-derived Local maps across supported Gen I–III structures, including continuous navigation, player following, discovery fog, dynamic lighting where available, POIs, clustering, Area Guide integration, and Atlas fallback.
- Preserve truthful habitat and observation boundaries: shortcuts appear only when catalogued habitat evidence exists.
- Show the normal map player without extra emphasis by default, with an optional Accessibility setting for a high-visibility pulse.

## Pokédex and party experience

- Add virtualized Pokédex browsing, live filter counts, ROM-derived sprites and descriptions, height comparison, specimen views, challenge data, progress timelines, damage forecasts, and party analysis.
- Refresh the Thor lower-display interface with consistent destination headers, compact layouts, clearer shortcuts, readable experience bars, corrected type-label geometry, and accessible dialogs and settings.
- Keep Organic and Hidden knowledge policies privacy-safe and evidence-bound.

## Compatibility and evidence

- Expand source-backed structural parsing for official games and ROM hacks without production selection by filename, ROM hash, symbol, or fixed per-ROM profile.
- Publish parser, map, evolution, feature, and ROM-hack compatibility evidence for the bounded 333-input corpus while distributing no ROM, save, or private memory data.
- Preserve exact SQLite reopen checks and fail-closed capability reporting for unsupported layouts.

## Signed delivery

- Stable Android identity: `com.darkaxt.dualdex`, version `1.1.0`, version code `1010099`.
- Production signing remains isolated to the protected GitHub `release-signing` environment and is verified against the pinned certificate.
- The final release is source-, provenance-, checksum-, and candidate-bound through the protected release workflow.
