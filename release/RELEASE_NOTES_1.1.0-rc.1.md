# DualDex 1.1.0-rc.1

This first 1.1 candidate delivers Stage 1 of the live player-state expansion while preserving the complete DualDex 1.0 Pokédex, map, evolution, ability, SaveRAM, and passive battle baseline.

## Stage 1: normalized trainer, party, and bag decoding

- Adds normalized, generation-neutral models for Trainer Card data, detailed party members, and bag pockets. Later UI and live-memory stages can consume one stable contract instead of exposing engine-specific layouts.
- Extends checksum-valid Generation III party decoding with nickname, personality, nature, held item, experience progress, friendship, ability slot and catalog ability ID, four move IDs, current PP, PP bonuses, level, status, HP, and all six battle stats.
- Adds typed Generation III SaveBlock ABI support for Trainer Card and Bag data, including source-layout field validation, money and item-quantity XOR decoding with the saved encryption key, and independent per-section fail-closed results.
- Connects catalog-derived ability IDs, move PP, and the Generation III text codec to the production save parser. Invalid or incomplete optional fields are withheld without discarding an otherwise valid individual or base catalog.

## Deliberate boundary of RC1

- RC1 provides the shared models and save-decoding foundation. Trainer Card, detailed Party, and Bag presentation pages are scheduled for later 1.1 stages and are not claimed here.
- Live WRAM SaveBlock pointer resolution, secondary-display routing/recovery, and post-battle refresh are also later stages. This candidate adds no emulator-memory writes and sends no game commands.
- Trainer Card stars remain unavailable until each condition is mapped from source-backed game state instead of being guessed.

## Existing compatibility retained

- The stable 1.0 baseline remains: 50/50 selected first-corpus ROMs, 50/50 complete evolution catalogs, 26/50 complete normalized world-map catalogs, and source-backed official Generation III ability behavior.
- Unsupported or ambiguous optional data continues to fail closed while the normal Pokédex remains available.
