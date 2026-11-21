# B2 Gen II relationships: affected qualification at parser schema 143 (2026-11-22)

Roadmap stage B2 ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)), item `LIB26-G2-RELATIONSHIPS-REMAINING`: evolutions and learnsets for the eight Gen II hacks that lacked both. All eight now gain both datasets.

## Rules added

- **Compiled move count** (`Gen2CompiledMoveCountResolver`): the Metronome exclusion loop compares the random move against a bound (`cp N; jr nc` → N−1 moves; `cp $FF; jr z` → 254). The count is used only if it is unique, larger than the inherited count, and every move name validates against the final relocated move-name table.
  - **Battle Tower fallback** (new): when no Metronome exclusion loop exists at all, the Battle Tower random-move bound (two equal `cp NN` operands in the move-generation loop; count = NN−1) is used under the same validation. Conflicting bounds fail closed. A ROM with a Metronome loop ignores its Battle Tower bound.
- **Compiled relationship ABI** (`TableRecordFormat.GEN2_COMPILED_RELATIONSHIPS`): for layouts found by the compiled relationship walker, evolution rows are 4 bytes for the stat method (5) and 3 bytes otherwise, and learn levels may reach 255. The heuristic Gen I/II validators are unchanged for every other layout.
- **Compiled species domain from pre-evolution walkers** (new): the compiled core may exceed the inherited species count only when every complete pre-evolution walker in the ROM compares against the same bound and that bound equals the compiled count.
- **Compact relationship contract** (new, `TableRecordFormat.GEN2_COMPACT_RELATIONSHIPS`): for compact-engine cores, the evos/attacks pointer table is bound through its sole `GetEvosAttacksPointer` consumer. That consumer must call the same base-index helper (and decrement) as the compact base-stat consumer, so row N is base-stat row N. Exactly one row ABI must then be proven:
  - `$FF`-terminated rows with `species, form` targets: a width consumer, in any bank, whose two far-read bank operands name the table bank and whose three compared methods take one extra byte. Every such consumer must agree.
  - 0-terminated rows with species-byte targets: every complete pre-evolution walker in the table bank roots at the table with a bound within its rows.
  - A bank-local trade-vocabulary proof is mandatory: either a `cp 3` trade branch before the link-mode rejection, or a link-mode rejection before any method comparison. When the engine has no trade method, rows using method 3 keep their learnsets but their evolutions are refused rather than labelled as trades.
  - Admission is all-or-nothing over every physical row: targets must be slot IDs, moves 1..count, and learn levels 1..254 (`$FF` ABI) or 1..255 (0 ABI).
- **TM/tutor list tie-break**: a wider move domain can admit unrelated byte runs as extra TM/HM/tutor list candidates. When more than one candidate exists, only lists loaded by `ld hl, list` from code in their own bank are kept. Exactly one must survive. The single-candidate path is unchanged.
- Parser schema 143.

Commits: ea77e7e7, 3b823905, 10cd23f3 (first four ROMs), efcadaff, 7a0e10cb (remaining four).

## Run

- Inputs: every GOLD_SILVER/CRYSTAL row selected in the [R1 baseline](2026-11-21-r1-corpus-refresh.md), plus Gen I controls Red Kaizo (.gb and .gbc) and Yellow Kaizo 1.0.4. 22 inputs, read in place.
- Parser CLI built through the Gradle gate at `7a0e10cb688d861be034c04d61188e5f86b3b68e`. One batch, one worker, `-Xmx3g`, run through the pipeline gate; workflow exit 0, process tree exit verified.
- Originals: all 22 were hashed and stat-checked before and after. All were unchanged. 0 errors.
- Host tests at the same source: parser-core 2672, catalog-store 130, app 693, 0 failed.
- Earlier qualification runs that failed and were corrected:
  - **ea77e7e7:** the move count was checked before move-name relocation, so only Crystal Kaizo gained.
  - **3b823905:** Mystic Crystal lost MACHINE_MOVES and TUTOR_MOVES because of an ambiguous TM list.
  - **efcadaff:** Polished Crystal stayed unbound. Its width consumer is in bank 15 while it reads the bank-6 table, but the scan covered only the table bank. Fixed at 7a0e10cb; the test fixture now places the consumer in another bank.

## Outcome vs. R1 (by SHA-256 identity)

| ROM | R1 | B2 | Change |
| --- | --- | --- | --- |
| Crystal Kaizo (01.08.26) | 73.68% | 84.21% | EVOLUTIONS and LEARNSETS available (251) |
| Gold 97 Reforged (v6.1f) | 72.22% | 83.33% | EVOLUTIONS and LEARNSETS available (253); move catalog/details 251→254 |
| Silver 97 Reforged (v6.1f) | 72.22% | 83.33% | same as Gold |
| Mystic Crystal (v1.0) | 72.55% | 83.08% | EVOLUTIONS and LEARNSETS available (251); move catalog 251→252; TM/tutor lists kept |
| Ambrosia (v2.9.0) | 63.10% | 73.62% | EVOLUTIONS and LEARNSETS available (253); 254-move domain from the Battle Tower bound |
| Anniversary Crystal (v1.2.3) | 51.81% | 62.34% | EVOLUTIONS and LEARNSETS available (252, including PHANCERO) |
| Polished Crystal (14.09.26) | 29.17% | 37.50% | EVOLUTIONS and LEARNSETS available (289, form targets) |
| Crystal Inheritance (v1.1) | 29.17% | 37.30% | LEARNSETS available (254); EVOLUTIONS partial, 242/254 (see limitation) |

- **Regressions: none.** 14 rows are identical: the 3 Gen I controls and the Gen II controls (Crystal Legacy, Timeless, Bronze ×2, Bronze 2, Crystal Clear ×2, Kalos, Digimon, Dark Energy, Sour).
- Polished Crystal and Crystal Inheritance remain low overall because the compact catalog path still lacks sprites, type chart, machine/tutor/egg moves and encounters. Those are separate datasets outside B2.

## Known limitation (tracked)

| ID | ROM | Limitation | Target | Acceptance |
| --- | --- | --- | --- | --- |
| DEF-G2-INHERITANCE-METHOD-VOCAB | Crystal Inheritance (v1.1) | Its engine numbers evolution methods LEVEL 1, ITEM 2, HOLDING 3, HAPPINESS 4, STAT 5, LOCATION 6, MOVE 7. The Gen ≤2 presentation only knows level/item/trade, so the 12 species whose rows use method 3 have their evolutions refused (fail closed) rather than shown as trades. Their learnsets are available. | B5+ (`LIB26-G2-OPTIONAL`) | A parsed per-ROM method vocabulary drives the presentation; those 12 species show their real evolution conditions. |
