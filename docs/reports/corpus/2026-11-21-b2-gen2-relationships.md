# B2 Gen II relationships: affected qualification at parser revision 142 (2026-11-21)

Roadmap stage B2 ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)), item `LIB26-G2-RELATIONSHIPS-REMAINING`: evolutions and learnsets for the Gen II hacks that lacked both.

## Rules added

- **Compiled move count** (`Gen2CompiledMoveCountResolver`): the Metronome exclusion loop compares the random move against a bound (`cp N; jr nc` → N−1 moves; `cp $FF; jr z` → 254). The count is used only if it is unique, larger than the inherited count, and every move name validates against the final relocated move-name table.
- **Compiled relationship ABI** (`TableRecordFormat.GEN2_COMPILED_RELATIONSHIPS`): for layouts found by the compiled relationship walker, evolution rows are 4 bytes for the stat method (5) and 3 bytes otherwise, and learn levels may reach 255. The heuristic Gen I/II validators are unchanged for every other layout.
- **TM/tutor list tie-break**: a wider move domain can admit unrelated byte runs as extra TM/HM/tutor list candidates. When more than one candidate exists, only lists loaded by `ld hl, list` from code in their own bank are kept. Exactly one must survive. The single-candidate path is unchanged.
- Parser schema 142.

Commits: ea77e7e7, 3b823905, 10cd23f3.

## Run

- Inputs: every GOLD_SILVER/CRYSTAL row selected in the [R1 baseline](2026-11-21-r1-corpus-refresh.md), plus Gen I controls Red Kaizo (.gb and .gbc) and Yellow Kaizo 1.0.4. 22 inputs, read in place.
- Parser CLI built through the Gradle gate at `10cd23f37589ba96ff571beada8c7692cb58f26c`. One batch, one worker, `-Xmx3g`, run through the pipeline gate.
- Originals: all 22 were hashed and stat-checked before and after. All were unchanged. 0 errors.
- Host tests at the same source: parser-core 2667, catalog-store 130, app 693, 0 failed.
- Two earlier qualification runs failed and were corrected:
  - **ea77e7e7:** the move count was checked before move-name relocation, so only Crystal Kaizo gained.
  - **3b823905:** Mystic Crystal lost MACHINE_MOVES and TUTOR_MOVES because of an ambiguous TM list.

## Outcome vs. R1 (by SHA-256 identity)

| ROM | R1 | B2 | Change |
| --- | --- | --- | --- |
| Crystal Kaizo (01.08.26) | 73.68% | 84.21% | EVOLUTIONS and LEARNSETS available (251) |
| Gold 97 Reforged (v6.1f) | 72.22% | 83.33% | EVOLUTIONS and LEARNSETS available (253); move catalog/details 251→254 |
| Silver 97 Reforged (v6.1f) | 72.22% | 83.33% | same as Gold |
| Mystic Crystal (v1.0) | 72.55% | 83.08% | EVOLUTIONS and LEARNSETS available (251); move catalog 251→252; TM/tutor lists kept |

- **Regressions: none.** 17 rows are identical: the 3 Gen I controls, the unresolved Ambrosia, Polished Crystal and Crystal Inheritance, and the Gen II controls (Crystal Legacy, Timeless, Bronze ×2, Bronze 2, Crystal Clear ×2, Kalos, Digimon, Dark Energy, Sour).
- **Anniversary Crystal:** the move catalog count is now 254, the Metronome authority. MOVE_DETAILS stays not found; its confidence moved 0.283→0.280 only because the denominator changed. Compatibility is unchanged at 51.81%.

## Not resolved (tracked deferrals)

| ID | ROM | Blocker | Acceptance |
| --- | --- | --- | --- |
| DEF-G2-AMBROSIA-MOVE-DOMAIN | Ambrosia (v2.9.0) | Learnsets use moves up to 254, but its Metronome has no exclusion loop, so there is no compiled move-count authority. | A compiled consumer proves the 254-move domain; then relationships resolve. |
| DEF-G2-ANNIVERSARY-SPECIES-252 | Anniversary Crystal (v1.2.3) | Species 252 (PHANCERO) exists; the pre-evolution walker compares against 252, but the parser's species domain is 251. | The species domain resolves to 252 from a compiled consumer; then relationships resolve. |
| DEF-G2-POLISHED-ENGINE | Polished Crystal, Crystal Inheritance | The relationship consumers differ from the pokecrystal-shaped contract. | A separate compiled contract for the Polished-engine relationship stream. |

Target for all three: B5+ (`LIB26-G2-*`).
