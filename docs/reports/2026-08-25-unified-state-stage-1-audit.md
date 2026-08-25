# Unified State Stage 1 Audit — Pokédex and Trainer

| Requirement | Code path | Automated evidence | Actual result | Disposition |
| --- | --- | --- | --- | --- |
| Resolved Pokédex values use canonical species IDs | `UnifiedGameStateDecoder.resolvePokedexSpecies` translates the selected live/recovery flag set using `SaveParseContext` before publication | `UnifiedGameStateDecoderTest`; `ProductionCompanionRuntimeTest.unifiedPlayerFieldsPopulateApiIndependentlyWithoutSaveRam` | Species IDs `84` and `300` are published from nonidentity flag numbers `25` and `277`; the runtime performs no flag translation | Implemented |
| Live Pokédex replaces stale recovery in every consumer | `ResolvedPokedexProjection` is replaced by each PLAYER snapshot; `ApiViewBuilder` reads that projection | `livePokedexReplacesPreviouslyExposedRecoveryStateForEveryConsumer` | Recovery `52/52` changes to live `2/1`; both Trainer Card and exact Pokédex sets agree | Implemented |
| Valid empty live sets retract prior values | The resolved projection distinguishes available empty sets from unavailable fields | Same vertical test | Both counts become `0`; no species remains seen or caught | Implemented |
| Matching recovery returns only when live becomes unavailable | Authority remains selected per field in `UnifiedGameStateDecoder`; runtime only projects the result | Same vertical test plus `UnifiedGameStateDecoderTest` | Suspending live restores `52/52` consistently | Implemented |
| Save-derived flags do not enter Organic knowledge | `applyRecoveryState` preserves checkpoint Organic seen/caught values after mapping Party/passive recovery | `unifiedRecoveryEventSeedsCheckpointAndAppliesSaveStatusWithoutADirectCallback`; vertical replacement test | Save flag `52` does not remain in Organic ledger; an exact checkpoint observation remains visible | Implemented |
| Area-filter captured entries use the same current caught set | `ApiViewBuilder.currentAreaSpeciesIds` uses the resolved effective caught projection | `companion-core:test` | Area projection cannot retain recovery-only caught entries after live replacement | Implemented |
| Existing Party/battle/coordinator behavior still compiles and passes focused controls | No polling, transport, map parser, or renderer change | `ProductionCompanionRuntimeTest`, `UnifiedGameStateDecoderTest`, `BattleMemoryCoordinatorTest`, complete `companion-core:test` | All selected tests passed | Implemented |

## Gate results

- RED evidence: the vertical regression initially reported expected `[1, 2]` but actual `[1..52]` after live publication.
- GREEN focused regression: 1/1 passed.
- Runtime and decoder suites: 81/81 passed.
- Companion core and battle-coordinator affected gate: passed with zero failures.
- `git diff --check`: zero errors.

## Remaining work assigned to later stages

- Stage 2 must remove the remaining SaveRAM Party/passive merge and make Party, ownership, Trainer license, bag, event flags, and checkpoint projection replacement-based.
- Stage 3 must route battle observations through the unified snapshot instead of the remaining direct publisher.
- Stage 4 must collapse the remaining overworld projection actions.
- Stage 5 must delete the compatibility actions and add a structural forbidden-route gate.

There is no Stage 1 blocker for Stage 2. These items are not omitted requirements; they are explicitly assigned to their plan stages.
