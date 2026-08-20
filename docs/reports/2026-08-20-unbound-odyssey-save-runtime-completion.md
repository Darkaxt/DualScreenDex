# Unbound and Odyssey save/runtime completion

Date: 2026-08-20

This report covers the save/runtime stage for the exact reference ROMs. It does not count an
erased save as a valid game and it does not use ROM names, hashes, symbols, or linked addresses
to select production layouts.

## Exact controls

| Control | SHA-256 |
| --- | --- |
| Pokémon Unbound v2.1.1.1 ROM | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` |
| Locally discovered Unbound SaveRAM artifact | `b5a41c3758763bbec72769fab4a2533bf2db0b6312d93d25a695f9e4b9e02260` |
| Pokémon Odyssey v4.1.1 ROM | `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0` |
| Locally discovered populated Odyssey SaveRAM artifact | `645282db3d0f6e5723930cc35793a39f2044f96cf1d658f139f04af5482fdcf3` |

Neither SaveRAM artifact was supplied by the user. The Unbound artifact is exactly 128 KiB of
`FF`. It is not a populated or checksum-valid slot, so the parser continues to reject it instead
of publishing empty runtime values.

## Numeric result

### Odyssey

Odyssey moved from **12/14 to 14/14 available save domains** on its populated exact save:

| Domain | Result |
| --- | ---: |
| Save slot | 1/1 |
| Seen species | 41 |
| Caught species | 40 |
| Current area | 1/1 |
| Party | 6 |
| Stored individuals | 26 |
| Species identity | 32 |
| Form identity | 0 applicable records in this save |
| Level | 32 |
| Egg state | 0 eggs in this save |
| IVs | 32 |
| Capture ball | 32 |
| Trainer Card | 1/1 |
| Bag pockets | 5/5 |

The exact Trainer Card result is gender `0`, money `6140`, play time `03:12`, and badge mask `0`.
The five Bag pockets contain `1/0/0/3/0` occupied entries for Items, Key Items, Balls, TM/HM,
and Berries respectively. The same bytes decode identically on two independent parses.

The catalog and runtime ABI are written to SQLite, reopened, and used by
`ProductionCompanionRuntime`. The public runtime projection retains money `6140`, play time
`03:12`, and six occupied party slots.

### Unbound

The exact ROM now resolves and persists a typed CFRU-style expanded-save ABI:

- SaveBlock1 size: `0x3D68`
- SaveBlock2 size: `0x0F24`
- Expanded-save data size: `0x2EA4`
- Five expanded Bag pockets: capacities `450/75/50/128/75`
- Expanded Bag offsets: `0x09AC/0x10B4/0x11E0/0x12A8/0x14A8`

Production selection derives these relationships from the source-defined 14-entry save directory,
decoded RAM-root assignments, the complete five-pocket descriptor, and the complete parasite
fragment boundary lattice. Tests freeze the exact addresses for the reference binary; production
code does not contain them.

The locally discovered all-`FF` SaveRAM remains **0/14 decoded runtime domains** because it is not a valid
save. This is an evidence limitation, not a parser claim. A populated exact Unbound save is still
required to measure the fourteen decoded values end to end.

## Save-format behavior

The decoder now supports source-defined CFRU save mechanics without weakening standard Gen III
validation:

- logical sections use their declared per-section checksum lengths;
- physical sector data keeps the `0xFF0` stride;
- parasite tails from logical sections 0, 4, and 13 are reconstructed in source order;
- special physical sectors 30 and 31 require the Gen III signature and their source-defined
  data checksum stored in the section-ID field;
- a missing or corrupt special sector withholds only the expanded Bag while retaining a valid
  standard Trainer Card;
- incomplete, erased, ambiguous, or checksum-invalid slots fail closed.

The official FireRed Rev 1 control remains exact on the standard layout: SaveBlock1 `0x3D68`,
SaveBlock2 `0x0F24`, no expanded-save window, and standard Bag capacities
`42/30/13/58/43`.

## Source authority

- Dynamic Pokémon Expansion / Unbound source snapshot: `fe058e0`
- Complete FireRed Upgrade source snapshot: `b637a27`
- Pokémon Odyssey documentation/source snapshot: `31b1eff`
- pret/pokefirered is the official FireRed structural control.

The DPE and CFRU snapshots are semantic format authorities, not claims that their current commits
produce the exact analyzed Unbound ROM.

## Verification

The final affected-module gate completed successfully:

```text
gradlew :parser-core:test :save-core:test :battle-memory:test :catalog-store:test \
  :companion-core:test :app:testDebugUnitTest \
  --tests com.darkaxt.dualdex.web.UnboundOdysseySaveCompletionLiveRomTest \
  --tests com.darkaxt.dualdex.web.OfficialEmeraldPlayerStateRealControlTest

BUILD SUCCESSFUL in 13m 36s
```

The exact SQLite/runtime-only rerun also completed successfully in 3m55s. `git diff --check`
passed, and a production-source scan found no Unbound/Odyssey name, SHA, linked-address, or
test-fixture selector.
