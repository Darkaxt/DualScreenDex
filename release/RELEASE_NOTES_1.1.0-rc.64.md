# DualDex 1.1.0-rc.64

RC64 corrects Modern Emerald live Trainer Card decoding and prevents recovery data from appearing as current player state before live memory establishes the active session.

## Live-memory mapper

- Emerald-derived money encryption keys are resolved from the compiled `GetMoney` THUMB data flow instead of assuming retail Emerald's `SaveBlock2` layout.
- Retail Emerald continues to resolve its `0xAC` key member; Modern Emerald resolves its shifted `0xBC` member and publishes the observed `3300` money value from every retained snapshot.
- The parser schema advances to 42 so catalogs created with the obsolete Modern Emerald runtime ABI are rebuilt once.
- The production replay control validates raw-region bounds and hashes, generation routing, battle state, area, coordinates, and generation-specific clock/player fields across all four retained JSON files and 26 snapshots.

## Recovery integrity

- Trainer, Pokédex, Party, boxed individuals, and Bag recovery values remain unavailable until a valid live sample establishes the ROM session.
- Exact save/checkpoint metadata and transient preferences can still restore before that sample.
- After live establishment, recovery remains available during a disconnect, including Gen I/II fields that have no live decoder.
- This removes the transient startup flash where stale or misprojected recovery could briefly report unrelated seen/caught/owned species.

## Validation and delivery

- The complete Gradle/JUnit gate, Android debug lint, release lint, secure dependency gate, web tests/build, and RC64 release assembly passed.
- Real-ROM controls cover all official Gen I-III games plus Modern Emerald, Unbound, and Odyssey.
- RC64 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010064`.
- Production signing and publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
