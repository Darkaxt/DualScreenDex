# DualDex 1.1.0-rc.62

RC62 fixes Gen III Pokédex integrity for a new playthrough and separates save-flag numbering from the catalog's displayed regional numbering while retaining RC61's expanded capture-ball support.

## Gen III Pokédex integrity

- A positively decoded empty live party now produces zero seen/caught discoveries instead of scanning unrelated SaveBlock2 bytes for a plausible flag layout.
- Missing party RAM remains unavailable and cannot masquerade as an empty validated party.
- Checksum-valid persisted saves retain recovery parsing even if an individual party record is damaged.
- Populated saves retain the anchored layout resolver, including expanded aligned layouts.
- Live and SaveRAM Pokédex flags are translated to catalog species IDs before they update Organic discovery state.
- Official Emerald's regional display numbers are no longer confused with its National-Dex save flags; published expansion and unified layouts retain their ROM-extracted National-Dex mapping.

## Validation and delivery

- The regression was reproduced from the live Modern Emerald state and its SaveBlock2 bytes before implementation.
- Focused codec, live-memory, mapper, coordinator, recovery, and runtime controls cover unavailable, empty, retail, persisted, and expanded layouts.
- A real official Emerald ROM control verifies explicit 15-seen/8-caught flags through catalog persistence and the unified live runtime.
- RC62 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010062`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
