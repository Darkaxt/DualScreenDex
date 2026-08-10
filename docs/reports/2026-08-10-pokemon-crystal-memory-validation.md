# Pokémon Crystal Rev 1 memory validation

## Scope

This report validates the user-provided `Pokemon - Crystal Version (USA, Europe) (Rev 1)` RetroArch export for production Generation II battle reads. It records addresses, decoded identifiers, transitions, and hashes only; no raw memory bytes are committed.

- Export SHA-256: `A5E1C0E0EB77F4CD7A1D5C5A59A2A68B56658D15C40B0D928B127791F970830F`
- Regions: 8,192-byte WRAM at `C000` and 127-byte HRAM at `FF80`
- Evidence: nine snapshots, eight complete diffs, zero omitted ranges
- Source correspondence: [`pokecrystal11.sym`](https://github.com/pret/pokecrystal/blob/symbols/pokecrystal11.sym) and [`ram/wram.asm`](https://github.com/pret/pokecrystal/blob/master/ram/wram.asm)

## Resolved sequence

| Capture | Validated state |
| --- | --- |
| `BATTLE_START` | `wBattleMode=1`; Rattata (`19`) Lv 2 is initialized, while the player battle structure is not ready yet. |
| `MOVE_SELECTED` | Cyndaquil (`155`) Lv 5, 20/20 HP, Tackle (`33`) and Leer (`43`); Rattata 13/13 HP, Tackle (`33`) and Tail Whip (`39`); selected move is Tackle. |
| First `MOVE_EXECUTED` | Cyndaquil Tackle PP changes 35 to 34 and Rattata HP changes 13 to 8. |
| Second `MOVE_EXECUTED` | Rattata Tackle PP changes 35 to 34, `wLastEnemyMove=33`, and Cyndaquil HP changes 20 to 17. |
| `BATTLE_END` | The user label precedes the engine transition; `wBattleMode` is still 1. |
| Final `OVERWORLD` | `wBattleMode=0`, providing the authoritative automatic close signal. |

## Production mapping

| Symbol | Crystal Rev 1 address | Use |
| --- | ---: | --- |
| `wBattleMon` | `C62C` | Player species, level, HP, DVs, moves, PP, and active types |
| `wCurPlayerMove` | `C6E3` | Selected player attack |
| `wCurEnemyMove` | `C6E4` | Current opponent attack candidate |
| `wLastEnemyMove` | `C71C` | Executed opponent attack latch |
| `wBattleEnded` | `C734` | Early terminal indication when populated |
| `wEnemyMon` | bank 1 `D206` | Opponent species, level, HP, DVs, moves, PP, and active types |
| `wBattleMode` | bank 1 `D22D` | Wild/trainer battle lifecycle |
| `wBattleType` | bank 1 `D230` | Battle-mode validation |

Gold and Silver retain the same Gen II battle-structure format and the `wEnemyMon` to `wBattleMode` delta of `0x27`, but use their own stable WRAM relationship. The production resolver validates both official shapes and falls back to catalog-coupled shape discovery rather than selecting behavior by filename or CRC alone.

## Runtime rules

- Do not publish a battle merely because `wBattleMode` became nonzero; both battler structures must independently pass species, move, PP, type, level, HP, and stat validation.
- Treat an invalid sample while `wBattleMode` remains active as a transient read, not as battle end.
- End only on a validated `wBattleMode=0` sequence or an independently validated terminal flag.
- Count opponent moves from PP decreases first. Use `wLastEnemyMove` as the compatible execution latch without double-counting the same PP event.
- Route by the parsed catalog generation. RetroArch reports this Crystal session as `game_boy`, so the core system string cannot distinguish Generation I from Generation II.

## Supported result

This evidence is sufficient for Generation II single-battle identity, automatic target, selected attack, active HP/types, DV rarity, effectiveness lookup, opponent move-frequency learning, and automatic battle exit. Generation II has no double battles, so multiple-opponent targeting is correctly not applicable.
