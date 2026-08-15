# DualDex 1.1.0-rc.4-hotfix.1

This hotfix preserves every RC4 feature and fixes an Android heap crash observed while a ROM catalog and the automatic shared-storage ROM index were loading concurrently.

## Fixed

- ROM-library indexing now streams CRC32 and SHA-256 while retaining only the cartridge header and a 64 KiB buffer.
- Direct GB, GBC, and GBA files preserve their exact identity and platform classification.
- Single-ROM ZIP sources preserve their archive-entry identity and multiple-ROM archives still fail closed.
- The fix removes whole-ROM allocations from the background storage-index thread; catalog parsing and local-map construction remain unchanged.

## Evidence

- The retained Android crash was an exact `OutOfMemoryError` on `dualdex-storage-index` at the 256 MiB heap limit.
- The streaming path reproduces official Emerald SHA-256 `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` and CRC32 `1F1C08FB` from the complete 16 MiB ROM.
- The signed hotfix is installed and revalidated on the affected AYN Thor through ADB after publication.

No ROM parser rules, catalog tables, map reconstruction, WRAM behavior, or game-facing controls were changed.
