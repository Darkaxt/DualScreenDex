# DualDex All-Files Storage Design

## Outcome

DualDex requests Android All files access as its primary v1 storage mode. Once granted, it discovers GB/GBC/GBA ROMs across shared-storage console folders and reads the effective RetroArch SaveRAM directory without requiring a separate SAF tree for every sibling folder. Existing SAF grants remain a fallback for devices or users that do not grant broad access.

## Confirmed failure

On the physical AYN Thor, signed RC6 resolved and parsed `Pokemon - Modern Emerald Version v3.5 (USA, Europe).gba` from the granted ROM tree, but reported zero discoveries. RetroArch's matching checksum-valid save existed at `/storage/emulated/0/RetroArch/saves/mGBA/Pokemon - Modern Emerald Version v3.5 (USA, Europe).srm`. DualDex had a read grant for `/storage/emulated/0/Games/ROMs` but no grant covering `/storage/emulated/0/RetroArch`, so SaveRAM discovery returned no candidates.

## Storage behavior

- The manifest declares `android.permission.MANAGE_EXTERNAL_STORAGE`.
- Setup opens Android's app-specific All files access page and rechecks the grant in `MainActivity.onResume()`.
- When granted, DualDex indexes supported ROM files from every mounted shared-storage root. It excludes Android's protected application directories and accepts only `.gb`, `.gbc`, `.gba`, and single-ROM `.zip` inputs already validated by `RomSourceLoader`.
- Direct file entries use canonical `file:` URIs and retain SHA-256 as the authoritative identity.
- Save discovery reads the effective `savefile_directory` reported by RetroArch and the active ROM's containing directory. It searches only `.srm` and `.sav` candidates whose basename matches the active ROM before checksum validation.
- The public `RetroArch/retroarch.cfg` is read directly when All files access is present. Existing exact-key edit, verified readback, recovery-document cleanup, and restart semantics remain unchanged.
- SAF configuration and ROM-tree paths remain available as fallback, but they are not required after All files access is granted.

## Presentation

Setup begins with a single `ALL FILES ACCESS` action and reports `GRANTED`, `MISSING`, or `INDEXING`. The ROM count covers all mounted shared-storage roots. SaveRAM status distinguishes missing storage access from an invalid or absent save. The older individual folder actions remain under fallback guidance rather than being the primary path.

## Safety and release

All access is still read-only for ROMs and SaveRAM. The only write remains the already approved exact-key RetroArch configuration edit plus its contextual recovery file. No file is deleted outside the existing verified recovery cleanup. The correction is tested only as a debug build on `DualDex_RA_API35`; the Thor receives only a GitHub-signed RC7 update.

## Acceptance

1. A fresh install exposes the Android All files access action.
2. Granting it indexes ROMs located in sibling `GB`, `GBC`, and `GBA` directories without more pickers.
3. The active Modern Emerald ROM resolves from direct storage.
4. Its `RetroArch/saves/mGBA` save reaches `MATCHED`, publishes all supported capabilities, and reveals the existing owned Pokémon in Organic mode.
5. Revoking broad access degrades to the existing SAF/manual paths without losing cached catalogs or the last valid save snapshot.
6. The mapper remains disabled and independent.
