# ARM7TDMI exact-first-50 compatibility survey

- Resolver commit: `4194316`
- Held-out retail controls: `211fccf`
- Corpus identity: first 50 unique SHA-256 rows in manifest order; every extracted ROM was freshly rehashed.
- Sessions: two fresh parser and mechanics analyses per row in one immutable run.
- Runtime: 37m46s.
- Scope: mechanics-core evidence only. No catalog, API, app, emulator, device, or release wiring.
- Selection rule: production resolution uses decoded calls, caller use-def, parser-selected typed layouts, CFG field provenance, and semantic effects. It does not select by ROM name, SHA, source symbol, routine offset, or byte signature.

`COMPLETE_PROOF` means every emitted tuple has independent caller-role, typed battle ABI, selected move-table, field-use, predicate, and effect proof, with no contradictory extra effect. It does not claim that every ability in the ROM has been modeled.

## Aggregate

| Outcome | Count |
|---|---:|
| Total manifest rows | 50 |
| Applicable GBA headers | 46 |
| Gen I/II GBC headers (`NOT_APPLICABLE`) | 4 |
| `COMPLETE_PROOF` | 30 |
| `UNSUPPORTED` (fail closed) | 10 |
| `PARSER_LAYOUT_UNAVAILABLE` | 3 |
| `STATIC_TYPED_LAYOUT_UNAVAILABLE` | 3 |
| `AMBIGUOUS` | 0 |
| `BUDGET` | 0 |
| Nondeterministic rows | 0 |

The mechanics compatibility gate of at least 25 genuine applicable successes is met at 30/46. The four Gen I/II rows are truthful controls and are not counted as successes.

## Complete proofs by parser family

| Family | Complete | Rows |
|---|---:|---|
| FireRed / LeafGreen | 21 | 1, 2, 4–8, 12, 13, 15, 16, 18, 26, 27, 35, 37, 40, 42–45 |
| Emerald, including Classic | 7 | 9–11, 19, 24, 28, 29 |
| Ruby / Sapphire | 2 | 14, 48 |

All 29 retail-layout successes emitted exactly these two tuples and no extras:

- ability 37: attacker Attack ×2
- ability 74: attacker Attack ×2

Classic (row 29) emitted exactly its four source-supported `CalcAttackStat` / Q4.12 tuples and no extras:

- ability 37: attacker Attack ×2, physical move
- ability 74: attacker Attack ×2, physical move
- ability 55: attacker Attack ×3/2, physical move
- ability 62: attacker Attack ×3/2, physical move and attacker status low byte nonzero

## Structural diversity

The 30 complete proofs contain four independently decoded routine locations, four runtime battle-array roots, two typed battle ABIs, seven caller/reference proof shapes, and two exact semantic tuple sets. Routine locations and roots are recorded as diagnostics; they are not production selectors.

| Typed ABI / proof shape | Count | Rows |
|---|---:|---|
| Direct pointers, stride `0x58`, Attack `u16@0x02`, ability `u8@0x20`; 6 decoded callers / 2 role proofs / 5 move refs | 19 | 1, 2, 4–8, 12–14, 18, 26–28, 35, 37, 42, 45, 48 |
| Same retail ABI; 6 / 2 / 2 | 4 | 15, 16, 43, 44 |
| Same retail ABI; 7 / 2 / 5 | 3 | 10, 11, 24 |
| Same retail ABI; 6 / 2 / 3 | 1 | 40 |
| Same retail ABI; 7 / 2 / 2 | 1 | 19 |
| Same retail ABI; 8 / 3 / 5 | 1 | 9 |
| Indexed array, stride `0x5c`, Attack `u16@0x02`, ability `u16@0x20`, status `u32@0x50`; Classic Q4.12 | 1 | 29 |

## Withheld rows

Ten ROMs fail closed as `UNSUPPORTED`:

- caller-role provenance has only one complete independent caller: rows 3, 30–32, 36, 39, 41, 49
- caller, move-root, and candidate-field stages resolve but no complete semantic mechanics survive: rows 20 and 33

No routine or ABI is selected for these rows, and no tuple is emitted.

Static prerequisites remain unavailable for rows 17, 25, and 47. The parser does not select one engine layout for rows 34, 46, and 50.

Rows 21–23 and 38 are actual-header Gen I/II GBC ROMs and remain `NOT_APPLICABLE`.

## First-33 preservation

Status and selected family remained stable for all first 33 rows. Row 14, Arcoiris, still differs only in stored catalog reference-error text: both fresh sessions produce zero reference errors, while the baseline records missing-ability references. This deterministic baseline drift remains a separate failed field and was not used to grant mechanics eligibility.

## Stage meanings

- `NOT_APPLICABLE`: actual ROM header is Gen I/II GBC; never counted as a mechanics success.
- `PARSER_LAYOUT_UNAVAILABLE`: parser did not select one engine layout.
- `STATIC_TYPED_LAYOUT_UNAVAILABLE`: selected family did not yield typed static move or ability prerequisites.
- `UNSUPPORTED`: structural or semantic proof was incomplete; the mechanic was withheld.
- `AMBIGUOUS`: more than one complete routine or ABI candidate survived; all were withheld.
- `BUDGET`: a proof budget was exhausted; all candidates were withheld.
- `SEMANTIC_MISMATCH`: a source-controlled tuple set differed from its exact oracle.
- `COMPLETE_PROOF`: emitted tuples were exact, contradiction-free, and deterministic across both fresh analyses.
