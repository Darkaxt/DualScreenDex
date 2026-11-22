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

## C4: expansion-era species extents (`LIB26-GBA-EXPANSION-ABI`)

Finding: the nine C4 rows do not share one ABI. They split by SpeciesInfo stride, measured from the published 0x1BC base-stats root:

| Rows | Stride | Shape |
| --- | --- | --- |
| Pisces, Sovereign of the Skies | 36 | The C1 36-byte layout. The species count, not the stride, was wrong. |
| Too Many Types | 36 | Three type bytes and type IDs up to 46, beyond the Gen III type domain. |
| Unova Emerald, Too Many Types 2, Recordkeepers | 84, 260, 296 | Version-specific expansion SpeciesInfo layouts. |
| Voyager, Voyager Demo, Lazarus | unknown | No retail Bulbasaur stat row anywhere in the ROM. |

Pisces and Sovereign size the species-name array by `NUM_SPECIES` and leave inactive slots all zero:

- Pisces: 1818 name slots, names 1..1468 populated, 1469..1817 zero. The unsized 36-byte SpeciesInfo array ends at row 1469, where Bulbasaur's level-up learnset starts, and the learnset pointer table's first entry targets exactly that address. The parser counted 1818 species, so stats (224/1818 at a 28-byte stride) and names (1469/1818) both failed.
- Sovereign of the Skies: 1208 slots bounded by the move-name root, last slot populated, 268 all-zero interior slots (for example 810..898). Stats already validated at 1208; names failed at 940/1208.

Fix (b3d2fec7):

- `Gen3DynamicTableResolver` trims the species count to the populated name prefix K only when every name slot from K is zero, the stats validate for exactly K rows at an inferred stride, row K is neither a stats row nor empty, and an aligned ROM word targets row K.
- `TableValidators.fixedNamesWithEmptySlots` treats all-zero name slots as uncovered placeholders. `CoreDatasetsStrategy` uses it for Gen III only after ordinary validation fails and only when the base stats validated the same species count. The last slot must be a name, at most 1% of slots may be malformed, and at least half must be names.
- A zero tail on its own still proves nothing.

Impact scan (`namescan.py`, published 0x144 name roots bounded by 0x148, 211 GBA headers): 9 ROMs have any all-zero name slot. Only Pisces has a pure zero tail. The scan does not cover name tables located through compiled geometry outside the published header.

| Run | Commit | Inputs | Result |
| --- | --- | --- | --- |
| Qualification | b3d2fec7 (jar manifest matches) | 12: the 9 scan rows (Pisces, Sovereign, Scale x Fang, DarkFire, Super Mariomon, Spades & Clubs, Celia, Tourmaline, Voyager) plus controls Radical Red, Unbound, Emerald Kaizo | Exit 0. 9 identical to R1. Pisces 8.13→54.17% and Sovereign 28.97→53.24%, both now SELECTED (Emerald). Scale x Fang carries only its C1 change (12.30%). |

Originals: all 12 inputs hash unchanged and match their corpus identity prefix.

Host tests at b3d2fec7: parser-core 2687 run, 240 skipped, 0 failed.

Follow-ups:

| ID | Gap |
| --- | --- |
| LIB26-GBA-EXPANSION-LEARNSETS | Pisces level-up learnsets (`LevelUpMove {u16 move; u16 level}`) and evolutions are not materialized; abilities are missing on both new selections. |
| LIB26-GBA-EXPANSION-DEX-DOMAIN | Sovereign excludes 997 of 1208 slots from the Pokédex semantic domain under the existing zero-Dex rule; audit against the species-to-dex map. |
| LIB26-GBA-EXPANSION-ABI (open) | Too Many Types (type domain), Unova Emerald, Too Many Types 2 and Recordkeepers (84/260/296-byte layouts), Voyager, Voyager Demo and Lazarus (no retail stat anchor). |
