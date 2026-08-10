# Generation III SaveRAM compatibility

This report covers the Pokémon SaveRAM samples used for the Generation III vertical slice. DualDex read every source file without modifying it. ROM and save bytes, trainer data, private paths, and file hashes are not included.

## Result legend

- **Full**: a matching parsed ROM catalog was available, so saved identifiers were translated and joined to ROM data.
- **Structural**: the save structure and owned records were decoded, but the matching ROM was unavailable, so catalog-dependent fields are not asserted.
- **N/A**: the check requires a matching ROM catalog and was not applicable to that run.
- **N/F**: the parser looked for the field in the available records but did not find evidence for it.

| Game / save | Result | Newest slot | Raw seen | Raw caught | Party | Stored | Current area |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Pokemon - Modern Emerald Version v3.5 (USA, Europe) | Full | 4 | 6 | 2 | 2 | 0 | 2:2 |
| Pokemon - Emerald Rogue | Structural | 2 | N/A | N/A | 1 | 0 | Decoded |
| Pokemon - Odyssey (USA) | Structural | 5 | N/A | N/A | 6 | 26 | Decoded |

Modern Emerald's two owned party species are the same Treecko and Poochyena represented by its caught flags. The production knowledge model therefore exposes 6 seen, 2 caught, and 2 Team species without double-counting party evidence.

## Capability evidence

| Capability | Modern Emerald | Emerald Rogue | Odyssey |
| --- | --- | --- | --- |
| Checksum-valid slot competition | Available | Available | Available |
| Seen / caught translation | Available | N/A | N/A |
| Current area | Available | Available | Available |
| Party | 2 records | 1 record | 6 records |
| Boxes | 0 records | 0 records | 26 records |
| Species identity | 2 records | 1 record | 32 records |
| Form identity | Available, no explicit form records | N/A | N/A |
| Level | 2 records | 1 record | Partial: 6 of 32 |
| Egg flag | Available, no eggs | Available, no eggs | Available, no eggs |
| IVs | 2 records | 1 record | 32 records |
| Capture Ball | 2 records | N/F: no owned record exposed it | 32 records |

## Android integration evidence

- On the dedicated `DualDex_RA_API35` AVD, mGBA and Modern Emerald resolved through RetroArch to the SHA-256-keyed catalog and checksum-valid SaveRAM.
- Organic mode exposed only the effective 6 seen / 2 caught species, identified both Team members, selected each preferred owned individual, and rendered ROM-derived capture-ball markers.
- With RetroArch fully stopped, a process restart reopened the catalog and restored the last committed 6 seen / 2 caught / 2 Team snapshot from SQLite.
- Replacing only the AVD copy with a 1 KiB partial file produced `STALE`, retained all last-good knowledge, and did not crash. Restoring the complete file returned the monitor to `MATCHED`.
- The AVD save and all private source files had their original SHA-256 values after validation.

Non-Pokémon saves were used only as negative family-detection fixtures and are intentionally excluded from this Pokémon compatibility report.
