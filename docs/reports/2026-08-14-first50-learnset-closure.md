# Exact first-50 learnset closure

Source commit: `39269f1`
Manifest SHA-256: `7146d2410231febfba550470d62ac179ef8c532d94bc15d2365211f862d03d5f`
Baseline report SHA-256: `c553d26c5d8a9b1d895716e3bd9d60faf3481eb0951bb84f2272d27cab45dd2f`
Current raw report SHA-256: `0c316918ee4fc16ce9722b0ee2b44ff4d430c7a11c5dcfe59c912642e52f4afa`

## Result

- Routing remains 50/50 selected: 30 FireRed/LeafGreen, 14 Emerald, 2 Ruby/Sapphire, 3 Gold/Silver, and 1 Crystal.
- Learnsets improve from 38 available / 4 partial / 8 not found to 40 available / 4 partial / 6 not found.
- Rows 34 and 50 are newly complete; no established learnset changed status.
- Evolutions remain 50/50 available.
- Catalog references remain clean: 0 affected rows and 0 reference errors.
- All 50 catalogs persisted and reopened with 12 sections. All 50 SQLite databases passed `PRAGMA quick_check` and `PRAGMA foreign_key_check`.
- Mean numeric compatibility increases from 95.21% to 95.34%.

The matrix used the first 50 unique manifest identities in manifest order. Every input was rehashed before parsing. A second full-corpus pass was intentionally not retained: it cannot change coverage, and the changed Dreamstone live test already asserts byte-semantic equality across two fresh parses.

## Newly complete rows

| Row | ROM | Family | Covered / expected species | Parsed entries | Capability |
|---:|---|---|---:|---:|---|
| 34 | Crippling Medical Debt Edition (v1.1).gba | Emerald | 1,525 / 1,525 | 22,177 | AVAILABLE |
| 50 | Dreamstone Mysteries.gba | Emerald | 1,522 / 1,522 | 22,164 | AVAILABLE |

## Resolver evidence

The production resolver does not select by ROM name, SHA, source symbol, fixed address, or a fixed field offset. It starts from the already-proven unified species-record root, stride, count, and active-row predicate, then admits a learnset field only when exactly one aligned record field points to a complete set of explicitly terminated wide `(move, level)` lists for every active species. Invalid pointers, unterminated lists, incomplete active coverage, and multiple complete fields fail closed.

For Dreamstone, the selected field is at record-relative offset 148 and yields a semantic SHA-256 of `5e80424ae344770807f1729338e69d61885ecb3af7867b8265252b2f79b093de`. Real-ROM mutations prove that duplicating the valid field creates ambiguity and that corrupting one pointer withholds the result.

Positive move relationships close referential identities only. They do not promote unresolved move names or details: Dreamstone's move catalog remains truthfully `NOT_FOUND` while its independently proven learnsets are `AVAILABLE`.
