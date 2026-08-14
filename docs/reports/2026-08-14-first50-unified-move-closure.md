# First-50 unified move-table closure — 2026-08-14

## Outcome

Dreamstone Mysteries now resolves its compiled-referenced 48-byte unified `MoveInfo` table for the complete ordinary move domain used by its decoded learnsets.

- First-50 numeric compatibility average: **95.34% → 95.44%**.
- Dreamstone numeric compatibility: **57.14% → 61.90%** (**12/21 → 13/21**).
- First-50 `MOVE_CATALOG`: **47/50 → 48/50 AVAILABLE**.
- Dreamstone `MOVE_DETAILS`: remains AVAILABLE, but expands from an unrelated 355-row/16-byte candidate to **848/848 typed 48-byte rows**.
- Dreamstone `MOVE_DESCRIPTIONS`: remains AVAILABLE, but expands from 342 decoded entries to **842 decoded entries from the selected unified table**.
- First-50 `EVOLUTIONS`: unchanged at **50/50 AVAILABLE**.
- First-50 `LEARNSETS`: unchanged at **44/50 AVAILABLE**.

## Structural authority

Production selection uses only ROM-derived evidence:

- the ordinary move domain derived from complete decoded learnset relationships;
- compiled GBA references nominating candidate roots;
- a typed 48-byte `MoveInfo` ABI with pointer name/description fields and packed scalar fields;
- complete validation of every row in the selected domain;
- fail-closed uniqueness.

No ROM name, SHA-256, source symbol, or absolute ROM offset participates in production selection. Exact ROM identity and offsets exist only in the live regression. Corrupting a real name pointer withholds the table; introducing a second complete compiled-referenced copy makes the result ambiguous and withholds it.

The comparative source oracle was Dreamstone Mysteries source commit `f7997186345885bfa23a170e5f573851fc034b9b`. The exact live ROM SHA-256 was `ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220`.

## Verification

- Full `:parser-core:test`: **1,043 tests, 0 failures, 0 errors, 90 fixture-gated skips**.
- Real focused controls: Dreamstone, Crippling Medical Debt Edition, Altered Emerald, and Modern Emerald passed.
- Exact first-50: **50/50 SELECTED**, 0 ambiguous, 0 no-family, 0 errors.
- Exact manifest SHA set: **50/50**.
- Routing deltas: **0/50**; first-33 routing/reference deltas: **0/33**.
- Reference-error rows: **0/50**.
- Persistence: **50/50** catalogs written and reopened; all SQLite `quick_check` results `ok`, all foreign-key checks empty, and every catalog contains 12 sections.
- Raw JSON SHA-256: `32ac997b736f9f010fb14d7bdb9e46cf64d1f01c0fe9f1a2e859cc0b25fe21c5`.
- Raw Markdown SHA-256: `2d8017db8a71cee27658e004e51eb2611b4a0c4bf067acbf19751c34cc0e64fd`.

Only one final exact-50 matrix was run after the focused implementation and controls.
