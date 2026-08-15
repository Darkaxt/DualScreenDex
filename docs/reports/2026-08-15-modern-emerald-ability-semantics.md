# Modern Emerald 3.5 ability semantics

This change integrates a bounded ability-mechanics slice for Modern Emerald 3.5. The exact ROM
control has SHA-256 `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895`.
The source oracle is `resetes12/pokeemerald` Release 3.5 at commit
`01a4212b9718886b19ce6e45c332ba618cc92a26`.

## Coverage boundary

- Source behavior inventory: 81 of 81 named abilities.
- ROM comparisons linked to the typed battle-ability field: 78 of 81 abilities.
- Binary-decoded normalized mechanics: 2 of 81 abilities (Huge Power and Pure Power).
- Source-backed normalized mechanics admitted by the compiled ROM contract: 5 additional abilities.
- Implementation behavior currently presented in Ability Detail: 81 of 81 abilities.
- Normalized numeric or structured values currently presented: 7 of 81 abilities.

The 78 field-linked abilities are not claimed as decoded mechanics. They establish where the
compiled code consumes the battle-record ability field; a comparison alone does not prove the
effect, predicates, target, or writeback.

## Published mechanics

| ID | Ability | Presentation | Provenance |
|---:|---|---|---|
| 22 | Intimidate | Opponents' Attack -1 stage on switch-in | Source-backed |
| 37 | Huge Power | Attack x2 | Binary-decoded |
| 55 | Hustle | Attack x1.5 | Source-backed |
| 61 | Shed Skin | 1/3 chance to cure nonvolatile status | Source-backed |
| 62 | Guts | Attack x1.5 while affected by status | Source-backed |
| 74 | Pure Power | Attack x2 | Binary-decoded |
| 81 | Pixilate | Damaging Normal moves become Fairy | Source-backed |

Source-backed values are admitted only when the loaded ROM independently proves all of these
structural conditions: the complete 81-entry ability domain and canonical semantic keys, the
40-byte `SpeciesInfo` table with byte ability slots at offsets 22 and 23, the 0x58 battle-record
ABI with Attack at 2 and ability at 0x20, the 12-byte move ABI, and binary Huge Power/Pure Power
Attack x2 anchors. ROM filenames, hashes, symbol addresses, and routine offsets are not production
selectors.

## Production path

The exact control is verified through parser selection, catalog materialization, schema-11 SQLite
write/reopen, typed runtime projection, loopback `/api/bootstrap`, and Ability Detail condition
labels. Cache schema 11 prevents older entries without complete behavior profiles from being
reused.

The standalone source/binary audit is frozen at commit
`9ea2500e10803504c592d2312615380de7134f6b`; its generated matrix covers all 81 abilities and keeps
source behavior, field-linked binary evidence, and normalized mechanics as separate columns.
