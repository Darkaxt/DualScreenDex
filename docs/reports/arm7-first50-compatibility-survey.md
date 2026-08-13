# ARM7TDMI exact-first-50 compatibility survey

- Production-equivalent survey commit: `c33960889abe1a4a11466d447693126ede883217`
- Corpus identity: first 50 unique SHA-256 rows in manifest order; every extracted ROM was freshly rehashed.
- Runs: two separate immutable Gradle invocations; each invocation performed two fresh parser and mechanics analyses per row.
- Run times: 32m50s and 36m23s.
- Evidence packet SHA-256: `D24E61F1FE4DD9809C75E0376515DD7DF8E818556A7E241CF5297E487F8E6D0E` for both packets.
- Scope: mechanics-core production resolver evidence only. No catalog, API, app, emulator, device, or release wiring was included in this survey.
- Selection rule: production resolution uses decoded calls, caller use-def, parser-selected typed layouts, CFG field provenance, and semantic effects. It does not select by ROM name, SHA, source symbol, routine offset, or byte signature.

`COMPLETE_PROOF` means the normal survey path supplied or derived every prerequisite and every emitted tuple has typed battle ABI, selected move-table, caller-role, field-use, predicate, effect, and writeback proof, with no contradictory extra effect. It does not claim that every ability in the ROM has been modeled.

## Aggregate

| Outcome | Count |
|---|---:|
| Total manifest rows | 50 |
| Applicable GBA headers | 46 |
| Gen I/II GBC headers (`NOT_APPLICABLE`) | 4 |
| Production `COMPLETE_PROOF` | 38 |
| Analyzer-only `COMPLETE_PROOF` | 1 |
| `UNSUPPORTED` (production fail closed) | 1 |
| `AMBIGUOUS` (production fail closed) | 1 |
| `PARSER_LAYOUT_UNAVAILABLE` | 3 |
| `STATIC_TYPED_LAYOUT_UNAVAILABLE` | 3 |
| `BUDGET` | 0 |
| Nondeterministic rows | 0 |

The mechanics compatibility gate is met at 38/46 genuine applicable production successes. The four Gen I/II rows are truthful controls and are not counted. Analyzer-only Classic evidence is also not counted.

## Production projection after the frozen survey

The survey above remains immutable evidence of resolver coverage. The RC25
production commits `04af720`, `fa2583f`, and `02132b1` project only
`COMPLETE_PROOF` tuples into the existing catalog, persistence, API, and
Ability Detail contracts. The final correction propagates immutable proof from
the parser's shared `RomAnalysisSession`; it does not open a second whole-ROM
analysis. Focused real-ROM architecture/materialization tests and the complete
parser, catalog-store, companion-core, and companion-server suites are green.

Classic remains `NOT_FOUND` and Clover remains `AMBIGUOUS` on the normal
production path, so their analyzer-only evidence is not promoted into the APK.
No emulator or device result is claimed by this document.

The two complete evidence packets are byte-identical. All 50 rows, both within-run analyses, aggregate counts, structural clusters, exact tuples, withheld reasons, and first-33 observations match.

## Production complete proofs

| Parser family | Complete | Rows |
|---|---:|---|
| FireRed / LeafGreen | 29 | 1–8, 12, 13, 15, 16, 18, 26, 27, 30–32, 35–37, 39–45, 49 |
| Emerald | 7 | 9–11, 19, 20, 24, 28 |
| Ruby / Sapphire | 2 | 14, 48 |

All 38 production proofs emitted exactly these tuples and no extras:

- ability 37: attacker Attack ×2
- ability 74: attacker Attack ×2

Both tuples depend on a decoded attacker ability comparison, typed `u16` Attack read, exact multiply/writeback semantics, and a parser-selected retail-12 move layout. No ability is selected from its name or description.

## Structural diversity

All 38 production completions use the typed retail battle-record contract: direct attacker/defender pointers, stride `0x58`, Attack `u16@0x02`, and ability `u8@0x20`. The resolver independently decoded eight caller/reference proof shapes:

| Decoded callers / role proofs / move references | Count | Rows |
|---|---:|---|
| 6 / 1 / 2 | 8 | 3, 30–32, 36, 39, 41, 49 |
| 6 / 2 / 2 | 4 | 15, 16, 43, 44 |
| 6 / 2 / 3 | 1 | 40 |
| 6 / 2 / 5 | 19 | 1, 2, 4–8, 12–14, 18, 26–28, 35, 37, 42, 45, 48 |
| 7 / 2 / 2 | 1 | 20 |
| 7 / 2 / 3 | 1 | 19 |
| 7 / 2 / 5 | 3 | 10, 11, 24 |
| 8 / 3 / 5 | 1 | 9 |

Routine locations and runtime roots are retained only as diagnostics; neither is a production selector. A single complete caller is accepted only when whole-caller role/use-def, unique routine selection, typed move relation, complete tuple/writeback semantics, and counterfactual dependency agree.

## Analyzer-only evidence

Classic (row 29) remains production `UNSUPPORTED` because its parser-selected move ABI is battle-engine-20, while the normal production resolver currently accepts retail-12 only. It is not included in 38/46.

The isolated source-control analyzer nevertheless emitted exactly these binary-proven `CalcAttackStat` / Q4.12 tuples and no extras:

- ability 37: attacker Attack ×2, physical move
- ability 74: attacker Attack ×2, physical move
- ability 55: attacker Attack ×3/2, physical move
- ability 62: attacker Attack ×3/2, physical move and attacker status low byte nonzero

That analyzer used the typed indexed-array contract: stride `0x5c`, Attack `u16@0x02`, ability `u16@0x20`, status `u32@0x50`. It withheld 288 bounded paths. This is useful semantic evidence, not production integration evidence.

Clover (row 33) is production `AMBIGUOUS`. Its explicit-ABI computed-dispatch test is not a survey success: the normal parser path does not yet supply the typed ABI needed to disambiguate the production resolver. No Clover tuple is published.

## Other withheld rows

- Static typed move prerequisites are unavailable for rows 17, 25, and 47.
- The parser does not select one engine layout for rows 34, 46, and 50.
- Rows 21–23 and 38 are actual-header Gen I/II GBC ROMs and remain `NOT_APPLICABLE`.

Every withheld row emits no mechanic. Failure of one ROM or mechanic does not invalidate successfully proven mechanics on another ROM.

## First-33 preservation

Parser status and selected family remained stable for all first 33 rows in all four fresh analyses. Row 14, Arcoiris, still differs only in stored catalog reference-error text: every fresh session produces zero reference errors, while the baseline records missing-ability references. This deterministic baseline drift remains a separate failed field and was not used to grant mechanics eligibility.

## Stage meanings

- `NOT_APPLICABLE`: actual ROM header is Gen I/II GBC; never counted as a mechanics success.
- `PARSER_LAYOUT_UNAVAILABLE`: parser did not select one engine layout.
- `STATIC_TYPED_LAYOUT_UNAVAILABLE`: selected family did not yield typed static move or ability prerequisites.
- `UNSUPPORTED`: structural or semantic prerequisites were incomplete; all mechanics were withheld.
- `AMBIGUOUS`: more than one complete routine or ABI interpretation survived; all mechanics were withheld.
- `BUDGET`: a proof budget was exhausted; all candidates were withheld.
- `SEMANTIC_MISMATCH`: a source-controlled tuple set differed from its exact oracle.
- `COMPLETE_PROOF`: emitted tuples were exact, contradiction-free, and deterministic through the normal production-equivalent path.
