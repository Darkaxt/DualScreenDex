# DualDex 1.1.0-rc.52

RC52 makes receiving the first Pokémon the Trainer Card license instead of requiring every Trainer Card field to resolve first.

## Trainer Card license

- The first catalog-validated occupied party record unlocks the Trainer Card from either live memory or the matching save.
- The milestone is one-way for that ROM-save playthrough: a later empty or unavailable party sample cannot hide the card again.
- Unknown or invalid species records cannot grant the milestone.
- The milestone is stored with the existing save-synchronized knowledge checkpoint and is restored only for its matching ROM and save identity.

## Partial Trainer Card

- Trainer Card navigation is independent from complete save-backed Trainer Card data.
- Live trainer name, gender, and ROM-derived artwork can populate the card immediately after unlock.
- ID, money, play time, Pokédex counts, card stars, and badge state render as neutral dashes when they have not resolved.
- Normal pages do not expose parser, capability, or failure diagnostics.

## Compatibility and validation

- The knowledge checkpoint advances to schema 7 while continuing to read schemas 4 through 6; older checkpoints begin locked and unlock from the next valid party observation.
- The ROM parser and catalog schema are unchanged, so an existing valid catalog cache does not need to be rebuilt for RC52.
- Live-party, SaveRAM, checkpoint, API, production UI, and full Android regression gates cover the change.

## Delivery

- RC52 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010052`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
