# Stage 4 POI applicability correction

This checkpoint corrects the premature Task 438 claim published at `cc96a259`. That checkpoint treated nominal Gen III sign structure as sufficient and missed bounded compiled message wrappers. Task 438 closes only with the corrected exact-key oracles and the remaining-script audit below. Parser schema is **79**; SQL schema remains **2** and codec versions remain **1**.

## Independent semantic authority

Public source supplies structural meaning, while exact compiled controls remain authoritative for every retained record:

- `pret/pokered` `2ab2421410b764e4dfebeddf8d9249d2cba947c4` and `pret/pokeyellow` `e6ba56989b0f2694f393e6924820be11dcc1fbb8` establish Gen I `LAST_MAP`, the Silph Co. Elevator reserved destination, and text-command behavior.
- `pret/pokegold` `a0dad0957ac8a9ffa67e950ee3ab6715a212ded5` and `PikalaxALT/pokekuristaru` `2fb3ee8f8c06aeaa89c37defbea8b85466f61260` establish Gen II event dispatch, `jumpstd` services, and text consumers.
- `pret/pokeruby` `63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1`, `pret/pokeemerald` `9a83a2bbe8e097e62c00f1dbd56849766775d7b6`, and `pret/pokefirered` `c75f352304d529f6ba92d4f74b9cf8b5c3810788` establish Gen III event and script structure. Japanese Ruby Rev 1, Emerald, and FireRed Rev 1 supply execution authority.

The retained obligations are:

- `DIRECT_TEXT` only when a bounded compiled message/text ABI proves a static first headline.
- `GENDERED_DIRECT_TEXT` only when compiled gender dispatch proves both first headlines.
- `ITEM_NAME` and `DESTINATION_NAME` only for their typed joins.
- `CONTEXTUAL_TEXT` when runtime state determines the first headline or destination.
- `NO_TEXT` only when compiled behavior positively proves a textless event.
- `UNRESOLVED` for unknown or unsupported scripts. Event kind, source label, or a later message never defaults a record to direct text.

Runtime substitution after a proven first static headline does not invalidate that headline. Runtime substitution in the first headline is contextual. Missing output and decode failure are not applicability waivers.

## Generation-specific results

### Gen I

Standalone warps with destination `LAST_MAP` or the reserved Silph Co. Elevator value retain context rather than collapsing into an unavailable destination. Exact Japanese controls contain **250** such Red/Blue warp POIs and **252** Yellow warp POIs. Six prize-vendor/vending-machine backgrounds are contextual in each family, yielding **256** Red/Blue and **258** Yellow contextual POIs.

Text roots below `0x4000` resolve through home bank 0 even when referenced by a banked map text table. Direct `TX_START`/`TX_FAR` roots remain direct; positively identified vending/prize scripts are contextual; arbitrary executable `TX_START_ASM` roots fail closed. Exact unresolved background sets are **25** Red/Blue and **49** Yellow. Eleven Pokémon Center signs and eight Mart signs are direct ROM-native labels.

### Gen II

Gold/Silver and Crystal use exact per-record oracles. Gold/Silver has **182** contextual and **12** positively textless POIs; Crystal has **214** contextual and **12** positively textless POIs. Contextual sets include context-dependent destinations and compiled runtime/script dispatch; textless rows are exact compiled elevator-button paths.

Korean paired text retains its scalar dictionary. A paired runtime token becomes contextual only when its compiled consumer resolves to WRAM; a ROM-literal target remains direct. Exact paragraph, continuation, and player-name-plus-gender consumers recover Japanese Crystal declarations without accepting unknown controls. Gen II `jumpstd` Pokémon Center and Mart signs retain ROM-native labels and service metadata.

### Gen III

Standalone `MAP_DYNAMIC` warps and warps into `MAPSEC_DYNAMIC` maps are contextual. Exact outcomes are:

- Ruby/Sapphire: **123 contextual** = 116 destination cases plus seven runtime-first-headline signs; **75 no-text** secret-base backgrounds.
- Emerald: **101 contextual** = 94 destination cases plus seven runtime-first-headline signs; **75 no-text** secret-base backgrounds.
- FireRed/LeafGreen: **22 contextual** = 19 dynamic destinations plus three runtime-first-headline signs.

The four Hoenn gender-dispatch records previously claimed as gendered direct are contextual: exact Japanese first headlines contain runtime substitutions. The Dewford Hall bookshelf is also contextual in Japanese Ruby and Emerald. Its bounded callee is message-free and returning, but its exact first headline contains `STR_VAR_1`; the English source string is not execution authority for the Japanese controls.

Direct first-headline recovery now recognizes these bounded compiled forms:

- `loadword 0, pointer; callstd type`;
- `message pointer; waitmessage`;
- `braillemessage pointer; waitbuttonpress`, including Ruby's erase-box continuation and FireRed's close-message continuation;
- lock/face, literal special-variable setup, reviewed stable specials, movement, Pokémon-picture, money-window, and FireRed text-color preludes;
- the exact Hoenn message-free trendy-phrase callee, bounded by a returning opcode whitelist.

The scanner examines at most 48 pre-message bytes and rejects unknown branches, calls, specials, malformed pointers, and non-returning or message-bearing callees. First-headline runtime substitutions `0xFD 0x01..0x06` produce contextual text. A later-line substitution does not erase a static first headline.

These additions recover native labels for ordinary wrapped signs, battle-rule headings, size records, museum and Pokémon displays, movement-driven notices, money-window notices, Ruby Braille walls, Emerald Braille declarations, FireRed Braille alphabets and directional inscriptions, and the other exact-key records bound by `Gen3PoiSemanticOracle`.

## Remaining Gen III script audit

Every remaining Gen III unresolved record was joined from its family-qualified exact key and script pointer to its source map/script root. The final **502 records / 339 source roots** stop on a disallowed compiled opcode before any bounded first-message authority; none merely runs past the 48-byte bound. The exhaustive first-rejection partition is:

| First rejected form | Ruby/Sapphire | Emerald | FireRed/LeafGreen | Meaning in reviewed roots |
|---|---:|---:|---:|---|
| non-whitelisted `call` (`0x04`) | 1 | 1 | 5 | dynamic paintings or interactive mansion statues |
| transfer (`0x05`) | 11 | 13 | 46 | shared/runtime handlers, including slots, quizzes, clocks, records, and trash cans |
| script switch (`0x19`) | 1 | 1 | 0 | Trick House state dispatch |
| compare/runtime test (`0x21`) | 26 | 26 | 2 | doors, generators, TV/sign state, and similar branches |
| non-whitelisted `special` (`0x25`) | 5 | 19 | 5 | rankings, contest/frontier results, memorial/PC and minigame UIs |
| other runtime producer (`0x26`) | 2 | 3 | 0 | museum, cycling, and blender state |
| state mutation (`0x28`) | 5 | 0 | 0 | Trick House switches |
| flag test (`0x2B`) | 53 | 52 | 139 | gym statues, doors, cages, elevators, signs, puzzles, encounters, and network state |
| machine/state read (`0x47`) | 24 | 24 | 0 | slot/roulette interactions |
| movement/facing state (`0x60`) | 10 | 10 | 0 | Petalburg Gym door interactions |
| Braille without immediate wait proof (`0x78`) | 2 | 0 | 2 | stateful cave-opening/Sapphire-room scripts |
| special UI command (`0x97`) | 6 | 7 | 1 | contest/frontier/trainer records and winners |
| **Total `UNRESOLVED`** | **146** | **156** | **200** | fail closed |

Source-root review groups these as runtime menu/record interactions, stateful world interactions, branch-dependent labels, or unsupported compiled flow. Examples include elevator floor selection, gym-statue badge branches, Cinnabar quizzes, Vermilion trash cans, Battle Frontier rankings, contest winners, slot/roulette tables, Trick House state, locked doors, switches, clocks, Southern Island encounters, network machines, and cave-opening Braille. Their source meaning explains why they are not direct, but does not replace the missing positive compiled first-headline proof required to relabel them contextual or textless.

All item records remain `ITEM_NAME`; all fixed destinations remain `DESTINATION_NAME`; all dynamic destinations remain contextual. No retained background is promoted from its nominal sign kind. Unknown or stateful flows remain `UNRESOLVED` rather than borrowing a later message.

## Coverage contract

Every retained POI remains in `POI_TEXT.expectedRecords`. Only labels resolved through direct, gendered, item, or destination projection enter `coveredRecords`. `CONTEXTUAL_TEXT`, `NO_TEXT`, and `UNRESOLVED` stay in the denominator and cannot borrow another label or receive fabricated text.

| Control | Structural obligations | `POI_TEXT` |
|---|---|---:|
| Japanese Red/Blue | 256 contextual; 25 unresolved | 813/1,094 PARTIAL |
| Japanese Yellow | 258 contextual; 49 unresolved | 802/1,109 PARTIAL |
| Japanese Gold/Silver | 182 contextual; 12 no-text | 1,757/1,951 PARTIAL |
| Japanese Crystal | 214 contextual; 12 no-text | 1,874/2,100 PARTIAL |
| Korean Gold | 182 contextual; 12 no-text | 1,755/1,949 PARTIAL |
| Korean Silver | 182 contextual; 12 no-text | 1,755/1,949 PARTIAL |
| Japanese Ruby/Sapphire | 123 contextual; 75 no-text; 146 unresolved | 1,560/1,904 PARTIAL |
| Japanese Emerald | 101 contextual; 75 no-text; 156 unresolved | 1,804/2,136 PARTIAL |
| Japanese FireRed/LeafGreen | 22 contextual; 200 unresolved | 1,907/2,129 PARTIAL |

## Verification boundary

Focused verification passed **50/50** resolver tests (10 Gen I, 26 Gen II, and 14 Gen III) plus the revision-78 cache rejection test, with zero failures, errors, or skips. A fresh **9/9** exact-control gate then passed with zero failures, errors, or skips. It binds every corrected contextual, no-text, and unresolved key through parse, SQLite close/reopen, full-catalog equality, API projection, and zero-reparse checks. Cache revision 78 is stale under parser schema 79.

Task 438 closes with this correction. Task 386 remains open until the independent all-15-capability audit reconciles every disposition across all 43 language/family cells. This checkpoint does not issue the final matrix oracle or authorize Android/device, signing, or release work. Stage 4 remains open and Stages 5–6 remain blocked until that broader audit legitimately closes.
