# First-50 Celia move-details closure — 2026-08-14

## Outcome

Celia's Stupid Romhack now resolves its complete widened retail move-details table.

- First-50 numeric compatibility average: **95.44% → 95.53%**.
- Celia numeric compatibility: **85.64% → 90.40%** (**18/21 → 19/21** applicable features).
- First-50 `MOVE_DETAILS`: **47/50 → 48/50 AVAILABLE**.
- Celia publishes **1,188 named moves with 1,188 typed detail records**; ID 0 and the adjacent non-table suffix remain excluded.
- Exact scalar values are preserved, including accuracy 238 and power 30,000.

## Structural authority

Production selection uses ROM-derived structure only:

- compiled references nominate candidates;
- the independently resolved move domain supplies the candidate cardinality;
- a typed 16-byte ABI validates widened `u16` effect/power/target fields, byte type/accuracy/PP/chance/flags/string/dance fields, and two zero tail-padding bytes;
- the selected type-chart cardinality bounds type IDs;
- only one non-record suffix may be trimmed after a complete populated prefix;
- candidate ambiguity and malformed active rows fail closed.

No ROM name, SHA-256, source symbol, or absolute ROM offset participates in production selection. Exact identity and offsets exist only in the live regression.

The comparative source oracle is Celia's Stupid Repository commit `8b31f2472810f75571d122159d164467e149d4a8`. Its move struct and type constants agree with the exact ROM table. The exact live ROM SHA-256 is `81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148`.

## Fail-closed controls

- Mutating one real active record's required tail padding withholds typed move details and leaves every detail field unavailable.
- The adjacent pointer record is rejected instead of becoming move ID 1189.
- Retail, CFRU, Battle Engine, Modern, Classic, Delta Emerald, and unified MoveInfo controls retain their frozen layouts and semantic hashes.
- Classic's explicit split continues to outrank power-derived fallback category semantics.

## Verification

- Implementation commit: `892fe7c` (`Resolve widened retail move tables`).
- Focused codec, resolver, dynamic-table, and five-real-ROM move-detail gate: **BUILD SUCCESSFUL**.
- Exact first-50: **50/50 SELECTED**, 0 ambiguous, 0 no-family, 0 errors.
- Exact manifest identity/order: **50/50**; routing deltas: **0/50**; first-33 routing/reference deltas: **0/33**.
- Reference errors: **0/50**.
- Persistence: **50/50** catalogs written and reopened; each contains 12 sections.
- SQLite validation: **50/50** `quick_check=ok`; **0** foreign-key findings.
- Celia SQLite: **1,495,040 bytes**, 12 sections, reopened successfully.
- Raw JSON SHA-256: `82ef47d6cf8449bc0020a915b98cafd26d1fd4244ba2c2bcc8d51505bc1aa4ad`.
- Raw Markdown SHA-256: `d01fed0a9e6831338b01a0cbeb6e1214b6773edd1c85184bb1bc4ef91b891033`.

Only one exact-50 corpus pass was run after the focused implementation and controls.
