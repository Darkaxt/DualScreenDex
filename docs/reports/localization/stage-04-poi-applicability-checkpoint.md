# Stage 4 POI applicability checkpoint

This checkpoint completes Task 438's destination/sign classification within Task 386's broader `POI_TEXT` audit. It does not issue the final 44-control oracle or make incomplete matrix cells appear complete. Parser schema is **78**; SQL schema remains **2** and codec versions remain **1**.

## Independent semantic authority

Public source supplies structural meaning, while the exact compiled controls remain authoritative for every retained record:

- `pret/pokered` `2ab2421410b764e4dfebeddf8d9249d2cba947c4` and `pret/pokeyellow` `e6ba56989b0f2694f393e6924820be11dcc1fbb8` establish Gen I `LAST_MAP`, the Silph Co. Elevator's reserved `UNUSED_MAP_ED` destination and text-command behavior. The compiled controls prove the retained record sets, runtime floor rewrite and pointer roots.
- `pret/pokegold` `a0dad0957ac8a9ffa67e950ee3ab6715a212ded5` and `PikalaxALT/pokekuristaru` `2fb3ee8f8c06aeaa89c37defbea8b85466f61260` establish Gen II event dispatch, `jumpstd` service scripts and text consumers. Compiled dispatch remains required for direction-sensitive events, runtime substitutions and textless paths.
- `pret/pokeruby` `63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1`, `pret/pokeemerald` `9a83a2bbe8e097e62c00f1dbd56849766775d7b6` and `pret/pokefirered` `c75f352304d529f6ba92d4f74b9cf8b5c3810788` establish Gen III background kinds, `MAP_DYNAMIC`, `MAPSEC_DYNAMIC`, simple-message scripts, conditional gender branches and bounded sound-prefaced sign helpers.

The retained obligations are:

- `DIRECT_TEXT` only when a bounded compiled text/message ABI proves a static first headline.
- `GENDERED_DIRECT_TEXT` only when compiled gender dispatch proves both branches.
- `ITEM_NAME` and `DESTINATION_NAME` only for their typed joins.
- `CONTEXTUAL_TEXT` when runtime state determines the first headline or destination.
- `NO_TEXT` only when compiled behavior positively proves a textless event.
- `UNRESOLVED` for unknown or unsupported scripts. Event kind alone never defaults to direct text.

Runtime substitution after a first static headline does not invalidate that headline. Runtime substitution in the first headline remains contextual. Missing output and decoding failure are not applicability waivers.

## Generation-specific results

### Gen I

Standalone warps with destination `LAST_MAP` or the reserved Silph Co. Elevator value `UNUSED_MAP_ED` now retain a context-dependent flag rather than collapsing into an unavailable destination. Exact compiled Japanese controls contain **250** such Red/Blue warp POIs and **252** Yellow warp POIs. Six prize-vendor/vending-machine backgrounds are also contextual in each family, yielding **256** Red/Blue and **258** Yellow contextual POIs.

Text roots below `0x4000` resolve through home bank 0 even when referenced by a banked map text table. Direct `TX_START`/`TX_FAR` roots remain direct; positively identified vending/prize scripts are contextual; arbitrary executable `TX_START_ASM` roots fail closed. Exact unresolved background sets are **25** Red/Blue and **49** Yellow.

Eleven Pokémon Center signs and eight Mart signs are source-ratified direct ROM-native labels. Gen I has no equivalent compiled semantic service-dispatch type, so these rows intentionally carry no service metadata.

### Gen II

Gold/Silver and Crystal now use exact per-record oracles rather than aggregate expectations. Gold/Silver has **182** contextual and **12** positively textless POIs; Crystal has **214** contextual and **12** positively textless POIs. The contextual sets include context-dependent destinations and compiled runtime/script dispatch. The textless rows are exact compiled elevator-button paths.

Korean paired text retains its scalar dictionary. A paired runtime token becomes contextual only when its compiled consumer resolves to WRAM; the same byte with a ROM literal target remains direct. Runtime substitution after the first static headline does not erase that direct headline. Exact paragraph, continuation and player-name-plus-gender consumers recover the Japanese Crystal declarations without accepting unknown controls; the Korean continuation path uses its compiled `CALL Z` opcode. Gen II `jumpstd` Pokémon Center and Mart signs retain their compiled service metadata and ROM-native labels.

### Gen III

Standalone `MAP_DYNAMIC` warps and warps into maps declared with section `MAPSEC_DYNAMIC` are contextual. Ruby/Sapphire retains **116** contextual POIs (**41** dynamic destinations plus **75** fixed warps into runtime-named sections); Emerald retains **94** (**45** plus **49**); FireRed/LeafGreen retains **19** dynamic destinations. Source declarations bind section `0x57` to `MAPSEC_DYNAMIC`, while the region-map consumer selects its effective location context instead of treating the blank section-table entry as a fixed name. Ruby/Sapphire and Emerald each retain **75** source-and-compiled-proven secret-base backgrounds as `NO_TEXT`.

A direct headline requires the exact `loadword 0, <ROM text>; callstd <message type>` shape, optionally behind the bounded compiled sound-preface used by two Hoenn gender branches. The exact `lockall; checkplayergender` prefix accepts the compiled `goto_if` and `call_if` variants only when both gender targets yield bounded headlines. Japanese GBA placeholder controls other than the player-name token remain codec-validated instead of being rejected wholesale. This recovers all four Hoenn gendered signs and the residual Ruby/Sapphire and FireRed/LeafGreen direct signs. Other nominal sign scripts fail closed: **212** Ruby/Sapphire, **234** Emerald and **267** FireRed/LeafGreen records remain `UNRESOLVED`; FireRed/LeafGreen has no retained gender-conditioned record.

## Coverage contract

A fresh nine-control run passed every structural exclusion, persistence, reopened-projection and API boundary. The retained `POI_TEXT` counters are: Japanese Red/Blue **813/1094**, Yellow **802/1109**, Gold/Silver **1757/1951**, Crystal **1874/2100**, Ruby/Sapphire **1501/1904**, Emerald **1733/2136**, FireRed/LeafGreen **1843/2129**; Korean Gold and Silver are each **1755/1949**. The remaining denominator is preserved by `CONTEXTUAL_TEXT`, `NO_TEXT` and `UNRESOLVED`; no missing required direct, gendered, item or fixed-destination label remains.

Every retained POI remains in `POI_TEXT.expectedRecords`. Only labels resolved through the correct direct, gendered, item or destination projection enter `coveredRecords`. `CONTEXTUAL_TEXT`, `NO_TEXT` and `UNRESOLVED` remain in the denominator and cannot borrow another label or receive fabricated text.

Fresh exact-control results are:

| Control | Structural obligations | `POI_TEXT` |
|---|---|---:|
| Japanese Red/Blue | 256 contextual; 25 unresolved | 813/1,094 PARTIAL |
| Japanese Yellow | 258 contextual; 49 unresolved | 802/1,109 PARTIAL |
| Japanese Gold/Silver | 182 contextual; 12 no-text | 1,757/1,951 PARTIAL |
| Japanese Crystal | 214 contextual; 12 no-text | 1,874/2,100 PARTIAL |
| Korean Gold | 182 contextual; 12 no-text | 1,755/1,949 PARTIAL |
| Korean Silver | 182 contextual; 12 no-text | 1,755/1,949 PARTIAL |
| Japanese Ruby/Sapphire | 116 contextual; 75 no-text; 4 gendered; 212 unresolved | 1,501/1,904 PARTIAL |
| Japanese Emerald | 94 contextual; 75 no-text; 4 gendered; 234 unresolved | 1,733/2,136 PARTIAL |
| Japanese FireRed/LeafGreen | 19 contextual; 267 unresolved | 1,843/2,129 PARTIAL |

## Verification

Fresh focused verification passed **99/99** resolver/sign tests with zero failures, errors or skips: ten Gen I cases, 81 Gen II cases and eight Gen III cases. Fresh end-to-end verification then passed **9/9** exact native controls with zero failures, errors or skips. Each exact control completed parse, materialization, actual SQLite close/reopen, full-catalog equality, API projection and zero-reparse checks.

The exact test-only oracles bind every retained contextual, no-text, unresolved, gender-conditioned and service key for all seven engine families. Unknown Gen I and Gen III scripts were reviewed under the positive-compiled-proof rule and remain `UNRESOLVED`; source names or nominal event kinds were not used to make the matrix greener.

## Remaining Task 386 work

Task 438's destination/sign classification is bound, but Task 386 remains open until the other 15-capability dispositions and explicit exclusions are reconciled across all 43 language/family cells. No final matrix, final oracle, corpus package, Android/device work, signing or release is included here. Stage 4 remains open and Stages 5–6 remain blocked until that broader audit legitimately closes.
