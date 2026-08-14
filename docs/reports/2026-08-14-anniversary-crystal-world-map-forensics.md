# Anniversary Crystal world-map forensics

Date: 2026-08-14

## Outcome

- Corpus row: 239
- Compatibility before: **0%** (`asset-loader`)
- Compatibility after the isolated correction: **100%**
- ROM SHA-256: `638dfbf61aa7a6e0bf1dcf75518dd69ed9e2f038f1dc09ab318ef4bbcdc29f5c`
- Selected family: Crystal
- Published regions: 2
- Deterministic: yes, across two complete catalog parses

The first correction completed the Town Map asset chain, then exposed a second fail-closed join: the ROM's encounter records use a compiled 53-byte ABI rather than the canonical 47-byte Gen II ABI. Treating those records as canonical produced false group/map IDs, so they could not be joined to the real 35-group map-header table.

## Source oracle

The public `TwitchPlaysPokemon/tppcrystal251pub` source was inspected locally at commit `e1abfe7656534b1d5e698ec7bbec1ecb07391434`. It was used only to identify likely roles:

- `FillJohtoMap` selects `JohtoMap`;
- `FillKantoMap` tests a status bit and selects either `KantoMap` or `EGKMap` before entering one shared terminated copy loop;
- `GetAnyMapHeaderMember` switches to `BANK(MapGroupPointers)` before calling the common pointer consumer;
- the grass and water encounter consumers both advance by `0x35` bytes;
- each compact encounter record stores a group/map pair, encounter rate, base level, configuration byte, and three 16-species time windows;
- the configuration byte selects compiled probability and level-adjustment tables.

The source comments' absolute offsets differ from the corpus ROM. Those offsets were rejected; every accepted address, branch, table, and payload below was established from raw ROM bytes.

## Raw Town Map authority

The complete guarded map owner is in bank `0x24` at `0x9202F`. Its raw control flow is:

- `ld de, 0x6139`; `jr` to the shared copy loop for Johto;
- load status byte `0xD84C`; `bit 5, a`;
- `ld de, 0x62A2`; `jr nz` to the shared copy loop for primary Kanto;
- otherwise `ld de, 0x640B` for the alternate EGK plane;
- one `0xFF`-terminated plane-copy loop shared by all three paths.

The three raw plane roots are contiguous and exactly 361 bytes apart:

| Plane | Root | Payload SHA-256 |
| --- | --- | --- |
| Johto | `0x92139` | `d32fa5c06e8cf8a38caa290f677b5d292f411bf2a4661d4826d24ba83e69d7b3` |
| Kanto | `0x922A2` | `ae8f5d73bd61d2121c2e5b40b611967d2f6da20184edc59d213224339f453d08` |
| EGK | `0x9240B` | `0668d75d4dbd622391de483e616feac39c28c68e75ced789d4b6775c0e2d8b57` |

Each plane contains 360 tile cells followed by `0xFF`, and every tile ID is below 48. The parser validates all three runtime variants and publishes the source-designated primary `KantoMap` as normalized Kanto.

The same bank also proves the remaining asset chain:

- packed palette consumer at `0x9204D`, with `cp 0x60; jr nc`;
- packed palette-map root `0x92085`;
- graphics loader at `0x9212C`, loading exactly 48 tiles into `0x9000`;
- compressed graphics root `0xF8BA0`;
- one complete six-palette layout authority.

## Raw map-header and encounter authority

The map-pointer consumer is at `0x2BE4`. Its compiled `GetAnyMapHeaderMember` caller at `0x2C03` saves the active ROM bank, loads bank `0x23`, switches banks, calls the pointer consumer, reads the requested header member, and restores the previous bank. That proves map-group table root `0x8ECB3` without a fixed production bank. The table contains 35 valid group pointers, matching the raw compact encounter domain.

The compact encounter owner is in bank `0x68`:

- combined grass/water stride consumer: `0x1A060A`;
- record stride loaded on every relevant path: `0x0035` (53 bytes);
- probability and level-selection consumer: `0x1A0290`;
- four raw 16-slot cumulative probability tables, each ending at 200;
- 15 three-time-window level profiles and 41 raw 16-slot signed adjustment tables;
- runtime variance: `+0..4`, clamped to levels 1–100.

The structurally referenced terminated arrays are:

| Table | Root | Records | Raw table SHA-256 |
| --- | --- | ---: | --- |
| Johto grass | `0x1A073B` | 49 | `da98b26d6a6c5f116bc0b360227645f16f678ea74d920e8d43f8c2da35ae8d25` |
| Johto water | `0x1A1161` | 34 | `7601792ce8fd358f0c2332e49343a34f7c6fb532e892b66cdd6389ae1967513a` |
| Kanto grass | `0x1A186C` | 53 | `85c779756737ced1d42dfaa1102c4493fd90e3466c0bbca51793735bf9e1f8b9` |
| Kanto water | `0x1A2366` | 25 | `30b558ed823494299423e5b8f035026283cb03df3f2796a30e5848aa3705a1e0` |
| Swarm grass | `0x1A2894` | 2 | `33df18f43fbd8e2878d1aa3b3a2907a75e92727d91b83dc84326d936f97f924b` |
| EGK grass | `0x1A2900` | 15 | `8e88bec3d04f51222f109176aec7500930a5021ed682049a9a47fe99fe68fc3a` |

After duplicate runtime variants are merged, materialization produces 410 encounter areas covering 137 unique group/map IDs. All 137 IDs join through the compiled map-header, landmark, and region-classifier chain; none is filtered or left unbound. The deterministic complete encounter fingerprint is `8bc6b49c14234887082577358111426afd8f499661d1dd5ae56cd36a012536fb`.

## Generic corrections

The resolver now accepts a guarded Gen II Town Map loader only when:

- one Johto pointer branches directly to a proven terminated copy loop;
- a loaded status byte is tested by `bit n, a`;
- one primary and one alternate second-region pointer converge on that same loop;
- all three planes are complete 20×18 planes using only the proven 48 tiles;
- the existing palette-map, graphics, palette, same-bank, and uniqueness checks also pass.

Map-header bank selection now comes from the compiled save/switch/call/read/restore consumer rather than a fixed bank number.

Encounter materialization selects the compact ABI only when one complete compiled consumer proves all of the following together:

- identical 53-byte strides on the grass, water, and lookup paths;
- Johto, primary Kanto, alternate Kanto, water, and swarm table pointers;
- a status-bit primary/alternate Kanto selector;
- one 16-slot probability consumer and its four valid cumulative tables;
- one three-time-window level-profile consumer and in-bank adjustment tables;
- complete terminated records whose map IDs, levels, configuration nibbles, and species IDs pass validation.

Ambiguous or malformed compact authority fails closed. The canonical 47-byte/9-byte Gen II scanners remain unchanged when the compiled compact consumer is absent. No ROM name, digest, fixed production offset, bank allowlist, or per-ROM profile was added.

## Rendered output

Both parser-generated PNGs were inspected and are coherent maps rather than gibberish:

| Region | Raster | Locations | Bindings | ARGB SHA-256 | PNG SHA-256 |
| --- | --- | ---: | ---: | --- | --- |
| Johto | 160×144, 20×18 | 42 | 65 | `adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9` | `ebe217ea89ce694707bda4c459c93c5a1ab1f3fdbf441add84bd1d5ad5933683` |
| Kanto | 160×144, 20×18 | 39 | 72 | `074aacb3e08341b1293aa445cf4c4bc398d54e297b810b7677f8ca515f41da91` | `cb07590e002797158ef541273de450fd1a66c194d8a88df063de91ef4e95b531` |

The complete location/geometry/binding fingerprint is `7dd5ca12862111503bb473fc9cdf627a30242e3ae2b5ba1dc23cbaab5795d85b`.

The retained render directory is:

`D:\Temp\dualdex-anniversary-crystal-poc-render`

## Bounded GB/GBC regression evidence

Completed matrix evidence was reused rather than rerun. A raw structural survey covered the same 120 retained GB/GBC corpus paths and established:

- the guarded three-plane map consumer appears only in Anniversary Crystal;
- the compiled 53-byte encounter consumer appears only in Anniversary Crystal;
- the five previously available two-region Gen II controls each have one unambiguous compiled map-header caller selecting bank `0x25`, exactly matching their previous effective bank;
- the existing direct two-plane parser and canonical encounter scanners are unchanged when the new structural consumers are absent.

The five affected-bank controls were Crystal Kaizo, Crystal Legacy, Crystal Legacy Timeless, Digimon Crystal, and Mystic Crystal. Their retained exact projection evidence remains applicable because the newly derived caller bank is the same raw bank used previously, while neither new loader branch nominates an authority in those ROMs.

The focused real-ROM control verifies Anniversary Crystal's exact encounter projection, complete 137-ID join, region geometry, and both raster hashes. No broad matrix or redundant already-completed suite was run.
