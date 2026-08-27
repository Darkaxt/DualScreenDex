# DualDex 1.1.0-rc.72

RC72 adds Pokédex Specimens: a read-only view of every decoded individual Pokémon you own in the Party or PC, inside that species' existing Pokédex entry.

## Pokédex Specimens

- Open `MORE` on a caught species to view all decoded Party and PC instances of that canonical species.
- Show each individual's sprite, nickname, level, gender, health/status where meaningful, experience, nature, ability, held item, moves, IV/DV values, rarity stars, and player-facing Party or box/slot location whenever those fields validate.
- Open the same detailed Pokémon card used by Party, including its nature, ability, move, and species destinations.
- Preserve the specimen list and scroll position when returning from individual details.
- Keep a caught badge without inventing an individual card when no owned record can be decoded.

## Live storage and recovery

- Decode Gen III PC storage through the existing bounded live-memory read plan; no second poller or full-memory copy was added.
- Prefer validated live boxes, use exact SaveRAM only while live storage is unavailable, and let a validated empty live box clear recovered records.
- Deduplicate Party and PC by stable individual identity when available, with a validated record fallback for older formats.
- Reject incomplete descriptors, invalid bounds, corrupt checksums, invalid species, and partially decoded storage instead of presenting guessed Pokémon.

## Measured compatibility

- The exact report covers 14 controls: official English Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, plus Modern Emerald, Pokémon Unbound, and Pokémon Odyssey.
- Applicable specimen fields: 148/178 (83.15%).
- Storage acquisition and integrity sources: 77/84 (91.67%).
- Official Gen III fields: 70/70 (100.00%).
- Modern Emerald, Unbound, and Odyssey fields: 42/42 (100.00%).
- Gen I/II fields that the current owned-record projection does not expose remain explicit `NOT_FOUND` or `NOT_APPLICABLE`; RC72 does not substitute stock-ROM values.

## Validation and delivery

- Reporter tests: 2/2.
- Affected JVM/Android suites: 1,836 tests with 0 failures and 0 errors.
- Companion browser suite: 220/220 across 30 files.
- Production TypeScript/Vite build completed successfully.
- RC72 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010072`.
- DualDex remains read-only. No device or emulator was used during implementation or publication.
