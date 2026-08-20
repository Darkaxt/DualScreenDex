# DualDex 1.1.0-rc.2

This second 1.1 candidate adds the ROM-derived runtime and artwork layer needed by the future Trainer Card and detailed Party screens. It preserves the complete DualDex 1.0 baseline and the Stage 1 normalized decoding contract from RC1.

## Stage 2: official Emerald runtime descriptors and Trainer artwork

- Resolves the official Emerald SaveBlock1 and SaveBlock2 pointer globals from compiled ROM consumers and publishes a typed Trainer Card and Bag ABI, including name, gender, Trainer ID, play time, money, badge flags, encryption key, and five bag pockets.
- Publishes typed live Party and battle-menu addresses for official Emerald and preserves the separately verified Modern Emerald layout. These are immutable catalog descriptors; RC2 still performs no emulator-memory writes and sends no game commands.
- Decodes the official Emerald Brendan and May 64×64 Trainer portraits plus all eight 16×16 Hoenn badge images from ROM-owned sprite, palette, and loader roles.
- Persists the normalized Trainer assets in schema 12 and serves only catalog-owned assets through the loopback API with immutable caching and fail-closed missing/traversal handling.
- Keeps the runtime descriptor and artwork sections independently optional. A missing or ambiguous optional role leaves the base Pokédex usable instead of inventing a fallback.

## Deliberate boundary of RC2

- Trainer Card, detailed Party, and Bag presentation pages are later 1.1 stages. RC2 supplies their verified descriptors and artwork but does not claim those screens yet.
- Live player snapshots, post-battle refresh, and secondary-display selection/recovery remain deferred.
- Trainer Card stars remain unavailable until each condition is mapped from source-backed game state.

## Existing compatibility retained

- The stable baseline remains 50/50 selected first-corpus ROMs, 50/50 complete evolution catalogs, 26/50 complete normalized world-map catalogs, and source-backed official Generation III ability behavior.
- Unsupported or ambiguous optional data continues to fail closed while the normal Pokédex remains available.
