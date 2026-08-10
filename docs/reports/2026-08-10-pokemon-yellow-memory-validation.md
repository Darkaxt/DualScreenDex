# Pokémon Yellow Memory Export Validation

Validated against the eight-snapshot Thor issue report for the official USA/Europe Pokémon Yellow ROM and the public [`pret/pokeyellow`](https://github.com/pret/pokeyellow) WRAM symbols. The raw report remains outside the repository.

## Recovered events

| Snapshot | Memory result |
| --- | --- |
| `83a494ae` `OVERWORLD` | No battle and no party Pokémon. |
| `2387bb20` `BATTLE_START` | Dedicated Oak capture battle: `wIsInBattle=1`, `wBattleType=4`, Pikachu (`0x54`) Lv 5, 19/19 HP, ThunderShock (`0x54`) and Growl (`0x2D`). |
| `533e6cd0` `BATTLE_END` | The labeled moment is still inside the capture battle. The production reader must use memory state, not the issue-report label, to close a battle. |
| `ab1c949a` `BATTLE_START` | Not yet inside the rival battle. It proves Pikachu acquisition: party count is 1, slot 1 is Pikachu Lv 5 with DVs `91FB`. |
| `f64c63a1` `MOVE_SELECTED` | Active trainer battle: rival Eevee (`0x66`) Lv 5, 21/21 HP, Tackle (`0x21`) and Tail Whip (`0x27`), DVs `9888`. Player Pikachu selected ThunderShock. |
| `0dde910d` `MOVE_EXECUTED` | ThunderShock consumed one PP (30 to 29), Eevee fell from 21 to 16 HP, and the executed-move latches expose player ThunderShock and enemy Tackle. |
| `91922b27` `BATTLE_END` | Eevee is at 0/21 HP. Pikachu ThunderShock PP is 26, proving four uses since selection. The latest enemy selection is Tail Whip. |
| `07e5f223` `BATTLE_END` | Post-KO reward state: Pikachu is Lv 6 and has learned Tail Whip. `wIsInBattle` is still 2, so teardown has not completed yet. |

## Stable shape

Yellow places `wIsInBattle` at `D056`, the enemy battle struct at `CFE4`, and the player battle struct at `D013`. Red/Blue place the same symbols one byte later. In both families:

- enemy struct = battle flag minus `0x72`
- player struct = battle flag minus `0x43`
- battle type = battle flag plus `3`
- party count = battle flag plus `0x10C`

The production Gen I resolver scans for that validated shape instead of selecting a CRC-specific address profile. It validates species, level, HP, types, moves, PP and DVs against the parsed ROM catalog.

## Move history

Gen I enemy PP did not decrement in this trainer battle. This is not a special rival-battle exception: Yellow's [`DecrementPP`](https://github.com/pret/pokeyellow/blob/master/engine/battle/decrement_pp.asm) routine is player-only, and the battle engine explicitly documents that [non-link enemies have unlimited PP](https://github.com/pret/pokeyellow/blob/master/engine/battle/effects.asm#L1400). PP deltas therefore remain the primary observation signal when a compatible derivative actually changes them, but they cannot count official Yellow enemy moves.

Yellow exposes short-lived executed-move latches at `wPlayerUsedMove` and `wEnemyUsedMove`. The tracker counts a non-zero latch edge once, ignores its held value, and rearms after the latch clears. A simultaneous PP delta wins through deduplication. Player PP deltas continue to support Organic matchup discovery.

After one eight-chunk WRAM discovery pass, the production coordinator caches the validated layout and polls a sub-1 KiB window containing the move latches, battler structs, battle result, battle flag, and battle type. That requires one RetroArch request per sample; with the 20 ms heartbeat and alternating start/read heartbeats, steady-state publication cadence is approximately 40 ms.

## Result

The same raw export passed the production `Gen1BattleLayoutResolver` for the Oak/Pikachu capture, the rival Eevee battle, move selection, executed Tackle, and fainted-Eevee state. The prematurely labeled rival `BATTLE_START` was correctly rejected because memory still said no battle.
