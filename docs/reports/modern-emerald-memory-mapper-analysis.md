# Modern Emerald battle-memory mapper analysis

## Evidence scope

This report analyzes one local raw mapper session captured from Pokémon Modern Emerald 3.5 while Treecko Lv7 fought Weedle Lv3. The session contains `BATTLE_START`, `MOVE_SELECTED`, and `BATTLE_END` snapshots of the complete mGBA EWRAM and IWRAM regions. Raw bytes, trainer identifiers, personalities, and the export itself are not stored in this repository.

| Identity | Value |
| --- | --- |
| ROM CRC32 | `8C7DBECA` |
| ROM SHA-256 | `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895` |
| Source reference | Modern Emerald `Release3.5`, commit `01a4212b9718886b19ce6e45c332ba618cc92a26` |
| Core system | `game_boy_advance` |
| Captured regions | EWRAM `0x02000000..0x0203FFFF`; IWRAM `0x03000000..0x03007FFF` |

All six exported regions decoded to their declared sizes and matched their stored SHA-256 hashes.

## Confirmed live structure

Modern Emerald retains the Generation III `struct BattlePokemon` layout from `include/pokemon.h`, with a record size of `0x58`. A catalog-coupled scan required two consecutive records to satisfy all of these checks:

- species ID exists in the parsed ROM catalog;
- level is within `1..100`;
- `0 < hp <= maxHP` at battle start;
- every nonzero move ID exists in the parsed ROM catalog;
- both live type IDs equal the parsed species types; and
- stats, PP, ability, and record bounds are structurally valid.

Exactly one EWRAM location passed: `gBattleMons = 0x0200143C`. Copies in the controller buffers contained recognizable species, level, and moves but failed the live type/HP validation, so they were correctly rejected rather than mistaken for the authoritative array.

| Field | Treecko / battler 0 | Weedle / battler 1 |
| --- | ---: | ---: |
| Species ID | `277` | `13` |
| Level | `7` | `3` |
| Types | Grass / Grass | Bug / Poison |
| Moves in record | Absorb, Pound, Leer | Poison Sting, String Shot |
| HP at start | `23 / 23` | `15 / 15` |
| HP in final snapshot | `16 / 23` | `0 / 15` |
| Average IV | `14.17` | `13.50` |
| Recruitment tier | `STANDARD` | `STANDARD` |

The opponent's complete move array is intentionally classified as hidden information. It validates the structure but must not enter Organic move history merely because it is present. This capture does, however, contain an independent observed-use signal: Poison Sting drops from 35 to 34 PP and String Shot drops from 40 to 38 PP. Organic history can therefore record Poison Sting once and String Shot twice without exposing any unused move.

## Confirmed selection and topology globals

The source declaration order plus independent snapshot transitions identify these globals for this exact build:

| Symbol | Address | Evidence |
| --- | ---: | --- |
| `gBattlersCount` | `0x02001420` | Value `2` in all three single-battle snapshots |
| `gBattlerPositions` | `0x0200142C` | Active positions `0, 1`; absent positions `0xFF, 0xFF` |
| `gBattleMons` | `0x0200143C` | Unique catalog-validated pair of `0x58` records |
| `gChosenMoveByBattler` | `0x0200162C` | Opponent choice `Poison Sting` in the selection snapshot; last round `Pound` and `String Shot` in the final snapshot |
| `gActionSelectionCursor` | `0x02001870` | Adjacent source-layout anchor |
| `gMoveSelectionCursor` | `0x02001874` | Player cursor changes from slot `0` to slot `1` |
| `gTargetSelectionCursor` | `0x02001878` | Source-layout anchor; remains zero in this single battle |
| `gTargetSelectionMove` | `0x0200187C` | Source-layout anchor; remains zero in this single battle |

At `MOVE_SELECTED`, player battler 0 has cursor slot `1`; `gBattleMons[0].moves[1]` is ROM move `1`, Pound. The final PP counters show Pound was consumed three times. Weedle's final PP counters show one Poison Sting and two String Shots were consumed. Those aggregate deltas are sufficient for Organic move discovery and frequency counting while Weedle remains uncaught. DualDex does not need the order or timestamp of each use, and it suppresses this observation metric once the species is captured.

The safe live rule is to establish a PP baseline only after the battler identity validates, then record a move solely when that same battler's PP for the slot decreases. A newly observed battler resets the baseline. `gChosenMoveByBattler` can corroborate the most recent use after the PP change, but it must never reveal the opponent's pending choice before the action becomes observable in-game.

For a single battle, the target is deterministic from the active topology: the only living opposing record is battler 1, Weedle. Pound is Normal against Bug/Poison and resolves to neutral effectiveness through the already parsed ROM type chart.

The layout itself is not limited to a fixed opponent count. `gBattlersCount` defines the active portion of the contiguous `gBattleMons` array and `gBattlerPositions` identifies each record's side and field position. A future capability can therefore enumerate every validated active opponent dynamically. The first production reader should still publish only the validated single-opponent case until a labeled double-battle capture proves live target switching.

## Dynamic mapping strategy

The exact addresses above are evidence for this ROM, not a per-ROM profile requirement. The reusable resolver should:

1. scan exposed EWRAM for consecutive `0x58` candidates;
2. validate species, move, type, level, HP, PP, and ability values against the active parsed ROM catalog;
3. require a unique candidate before publishing `BATTLE_LAYOUT`;
4. derive nearby globals from the validated `gBattleMons` anchor and validate every derived value independently; and
5. publish feature flags separately so an unsupported field cannot disable the fields that did validate.

For this source shape, the useful relative offsets are:

- battler count: `gBattleMons - 0x1C`;
- battler positions: `gBattleMons - 0x10`;
- chosen moves: `gBattleMons + 0x1F0`;
- action cursor: `gBattleMons + 0x434`;
- move cursor: `gBattleMons + 0x438`;
- remembered target cursor: `gBattleMons + 0x43C`; and
- remembered target move: `gBattleMons + 0x440`.

These deltas must compete as a validated layout shape. They are not accepted merely because a ROM resembles Emerald.

## Capability verdict

| Capability | Current evidence |
| --- | --- |
| Opponent identity, level, HP, types, ability, moves, IV tier | **Validated for this exact Modern Emerald build** |
| Player highlighted move | **Validated for this exact Modern Emerald build** |
| Single-battle target | **Validated from topology** |
| Parsed-ROM effectiveness for highlighted move | **Available once highlighted move and target validate** |
| Double-battle live target | **Not yet validated**; the live hover uses `gMultiUsePlayerCursor`, while the remembered target fields update only under specific cursor-memory behavior |
| Organic opponent move discovery and frequency | **Validated** through PP decreases: Poison Sting ×1 and String Shot ×2. Ordering and timestamps are not product requirements |
| Battle enter/leave boundary | **Partially validated**; the final snapshot was captured while battle data was still resident and before `gBattleOutcome` changed |
| Active ruleset selector | **Not evaluated by this session** |

## Fastest safe path to live battle UI

The current evidence is sufficient to implement a catalog-coupled battle-array locator, highlighted-move reader, and Organic opponent-move frequency tracking through PP deltas behind exact capability flags. Double-target switching and reliable battle-exit detection still require a denser labeled capture containing:

- overworld immediately before and after battle;
- double battle while hovering each opposing target;
- target change without confirming the move.

Until those remaining event boundaries validate, DualDex can safely render identity, level, rarity, highlighted attack, effectiveness, and PP-delta-observed opponent move frequencies for single battles. It must not expose unchanged hidden opponent moves or claim automatic double-target tracking.
