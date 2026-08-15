# Official Generation III ability coverage

Date: 2026-08-15

The production catalog now identifies the complete named ability domain in all five
official English Generation III controls: Ruby Rev 2, Sapphire Rev 2, Emerald,
FireRed Rev 1, and LeafGreen Rev 1.

## Exact domain

Official Generation III defines ability IDs 1 through 77. ID 0 is `NONE` and is not
an ability record. ID 76, `CACOPHONY`, is retained as a named but inactive engine
entry; ID 77 is `AIR LOCK`.

| ROM | SHA-256 | Source-backed behavior | Binary field-linked IDs | Normalized numeric mechanics |
|---|---|---:|---:|---:|
| Ruby Rev 2 | `0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c` | 77/77 | 55/77 | 2/77 |
| Sapphire Rev 2 | `02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb` | 77/77 | 55/77 | 2/77 |
| Emerald | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` | 77/77 | 58/77 | 2/77 |
| FireRed Rev 1 | `729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059` | 77/77 | 57/77 | 2/77 |
| LeafGreen Rev 1 | `2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825` | 77/77 | 57/77 | 2/77 |

The source behavior profile records whether each ability is implemented through
compiled code, battle scripts, both, or is defined but inactive. Binary field linkage
means decoded code consumes the typed battle-record ability field for that ID; it is
discovery evidence and is not presented as a decoded numeric formula. The separately
normalized numeric values remain the proven Attack x2 mechanics for Huge Power and
Pure Power.

## Production eligibility

The behavior profile is admitted only after the parser independently proves all of
these ROM structures:

- the exact canonical 77-name ability domain for the selected source family;
- the retail 0x58 battle-record ABI, including Attack `u16` at 2 and ability `u8` at
  0x20;
- the selected 12-byte move ABI; and
- decoded Huge Power and Pure Power Attack x2 mechanics as binary anchors.

ROM filenames, hashes, symbols, and absolute routine offsets are test provenance only;
they are not production selectors.

## Exact verification

All five controls passed the real-ROM parser regression and the complete
CatalogParser to schema-11 SQLite write/reopen to production runtime/API path. The
fresh CLI run selected all five ROMs, persisted and reopened all five 12-section
catalogs, reported zero errors, and measured 77/77 ability behavior records in each.
Every complete official catalog reports 22/22 capabilities and 100.00% compatibility.

Generated evidence:

- JSON SHA-256: `4D40F0E2F4A5FAB0DBF6683888163CF49A760A0D711B2C13B3A438D3ACEBBA3E`
- Markdown SHA-256: `254937C8764475904FB7E54C0FA2A489ABFB3E806D05CE0A12B76B9272E5EF87`
