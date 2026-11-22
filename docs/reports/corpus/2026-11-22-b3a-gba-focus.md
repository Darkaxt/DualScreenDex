# B3a GBA focus: published-header and engine follow-ups (2026-11-22)

This stage follows [B3](2026-11-22-b3-unmatched-triage.md). It works through the GBA clusters that B3 assigned to the GBA focus, in the roadmap order: C1 header layouts, the C3 FireRed/Emerald tie, then the C4 expansion ABIs.

## C1: one-seen-flag header at 0x1B8 (`LIB26-GBA-HEADER-1B8`)

Finding: the Emerald published header has `pokedexOffset`, `seen1Offset` and `seen2Offset` at 0x158–0x160. Four hacks keep only one seen offset, so `pokedexVar` moves from 0x164 to 0x160 and the seven-pointer data block (species info, ability names, ability descriptions, items, moves, ball graphics, ball palettes) moves from 0x1BC to 0x1B8. The parser accepted only 0x1AC, 0x1B4 and 0x1BC. In these hacks 0x1B4 and 0x1BC both scored six pointers, so the block was rejected as ambiguous.

Fix (20f4b0af): 0x1B8 is a fourth candidate start. Selection is unchanged: semantic base-stat and move-data roles rank first, pointer count second, and ties are still refused.

Impact scan: every one of the 211 corpus `.gba` headers was scanned (first 512 bytes). Only the four C1 rows give 0x1B8 a strict lead. Nine more rows can tie it at five or fewer pointers. Voyager (Battle Frontier Demo) is the only one where 0x1BC previously led on its own. All 13, plus three controls, were qualified.

| Run | Commit | Inputs | Result |
| --- | --- | --- | --- |
| Qualification | 20f4b0af (jar manifest matches) | 16: the 4 C1 rows, the 9 tie rows (Soulgold, Voyager Demo, Light and Shadow, Elite Redux, Battle Theater, WaveBlue, Valiant, FireEmerald, Heart and Soul), controls Radical Red, Unbound, Emerald Kaizo | Exit 0. 12 identical to R1. Scorched Silver 8.31→79.17%, Parallel Emerald 8.31→75.00% and Team Rocket Edition 8.31→70.83%, all now SELECTED (Emerald). Scale x Fang 4.15→12.30% (gains EVOLUTIONS, MOVE_DETAILS, partial MOVE_CATALOG; still unmatched). |

Originals: all 16 inputs hash to their corpus identity prefix after the run.

Host tests at 20f4b0af: parser-core 2675 run, 240 skipped, 0 failed.

Remaining C1 rows:

| ID | ROM | Remaining gap |
| --- | --- | --- |
| ROW-8f58e67c994e | Scale x Fang | 36-byte species records start 4 bytes past a stride boundary from the published pointer; no proven table start. |
| ROW-35cbc369eb8f | Light and Shadow | 0x1BC header with zeroed ability slots. |
| ROW-9c66085c61cf | WaveBlue | 0x1BC header with zeroed ability slots. |
| ROW-55b887450b93 | Elite Redux | 0x1BC header with missing ball slots; innates species ABI. |

## C3: FireRed/Emerald tie (`LIB26-GBA-FRLG-EM-TIE`)

Finding: Ultra Violet and Lugias Ocean replace the cartridge game code (`PUVV`, `2008`), and both family probes score 80 on identical vanilla FireRed tables. The type-chart tie-break cannot separate them, because both charts are complete. These two were the only `AMBIGUOUS` rows in R1.

Engine evidence: both engines decode money as `*moneyPtr ^ gSaveBlock2Ptr->encryptionKey`, but `struct SaveBlock2` places `encryptionKey` at 0xF20 in pokefirered and at 0xAC in pokeemerald. A scan of the compiled `GetMoney` consumers gives:

| ROM | Key-offset materialisation | Offset |
| --- | --- | --- |
| Ultra Violet, Lugias Ocean, Radical Red | `movs r2,#0xF2; lsls r2,#4; adds r1,r2` | 0xF20 |
| Emerald Kaizo | `adds r1,#0xAC` | 0xAC |

Fix (c4697b98): `Gen3SaveBlock2EngineDiscriminator` decodes only that exact Thumb shape. It must load an IWRAM pointer literal, dereference it, add the offset, load through R0, load the key, `eors r0` and `bx lr`. Every match must agree on one offset. `ParserOrchestrator.select` asks it only for an exact FireRed/Emerald score tie that the type chart leaves open. Conflicting, missing or unknown offsets keep the tie. Near ties stay refused.

| Run | Commit | Inputs | Result |
| --- | --- | --- | --- |
| Qualification | c4697b98 (jar manifest matches) | 5: Ultra Violet, Lugias Ocean, controls Radical Red, Unbound, Emerald Kaizo | Exit 0. 3 identical to R1. Ultra Violet 45.83→99.50% and Lugias Ocean 50.00→91.15%, both now SELECTED (FireRed). Lugias Ocean WORLD_MAP is ambiguous and LOCAL_MAP partial. |

Coverage: the rule can only change rows that end in an exact family tie. R1 had exactly these two, and no later B3/B3a run produced another.

Originals: all 5 inputs hash to their corpus identity prefix after the run.

Host tests at c4697b98: parser-core 2679 run, 240 skipped, 0 failed.
