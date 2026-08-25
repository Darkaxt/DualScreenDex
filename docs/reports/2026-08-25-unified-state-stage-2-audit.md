# Unified State Stage 2 Audit

| Requirement | Implemented path | Evidence | Result |
| --- | --- | --- | --- |
| Party, stored ownership, bag, and event flags share the resolved snapshot | `ResolvedGameSnapshot.storedIndividuals`; `applyResolvedPartyAndProgression`; replacement fields in `AppSnapshot` | `unifiedPartyUsesLiveThenRecoveryAndValidZeroClearsEveryConsumer`; bag/event decoder tests | Pass |
| Available empty Party clears current Team state | Party replacement projection; no Party-to-ledger merge | `unifiedPartyUsesLiveThenRecoveryAndValidZeroClearsEveryConsumer`; `endingUnifiedSessionClearsPartyAndCurrentTeamProjection` | Pass |
| Trainer license remains earned for the active playthrough | `CompanionGateway.ResolvedPartyStateChanged` sticky license projection | Party vertical regression | Pass |
| Save recovery does not mutate canonical UI state through the ledger | Recovery fields resolve in `UnifiedGameStateDecoder`; runtime no longer has `applyRecoveryState` or `savedPlayerState` | Static scan finds no `SaveKnowledgeMapper.merge`, `savedPlayerState`, or `recovery.snapshot` in production paths | Pass |
| Exact Modern Emerald pre-starter recovery is zero | Exact 128 KiB pulled SaveRAM replayed through the real parsed Modern Emerald catalog | Private real-save control returned `0` caught; raw save was not committed | Pass |
| One Pokédex flag cannot expand into every form/alias | Canonical inverse mapping prefers base form, then deterministic species ID | RED fixture: one flag plus 51 aliases produced 52; GREEN fixture produces 1. Real Modern Emerald Treecko `277` / flag `252` resolves to `{277}` | Pass |
| Old checkpoint mirrors cannot reintroduce 52 | Decoder strips seen/caught, owned/Team, Trainer-license, and current-area mirrors on checkpoint input and output | `unifiedRecoveryRejectsMirroredPokedexFieldsFromCheckpointButKeepsTransientPreferences` | Pass |
| Checkpoint still preserves genuine companion state | Organic/POI knowledge and map preferences remain in the transient checkpoint projection | checkpoint regression preserves `showPlaces=false`; changed-save freeze test preserves preferences | Pass |
| Checkpoints are written only with a changed save | Existing coordinator write gate remains `SaveObservationKind.CHANGED` | `SaveKnowledgeCheckpointCoordinatorTest` and checkpoint suite | Pass |
| Active live Pokédex retracts recovery values | Live fields replace recovery fields in the resolved projection | recovery 52 -> live 2/1 -> live 0/0 vertical regression | Pass |

## Verification

- `:app:testDebugUnitTest` for `UnifiedGameStateDecoderTest`, `ProductionCompanionRuntimeTest`, and `SaveKnowledgeCheckpoint*`: 91 tests passed.
- `:companion-core:test`: passed.
- `:save-core:test`: passed.
- `:app:testDebugUnitTest --tests *BattleMemoryCoordinatorTest`: passed.
- Real Modern Emerald canonical Treecko mapping control: passed.
- `git diff --check`: passed.

## Deferred by specification

- Stage 3 owns the remaining direct battle tracker publication and encounter-title migration.
- Stage 4 owns Atlas/clock/readiness/POI projection consolidation.
- Stage 5 owns deletion of unused legacy mapper/action classes and Gen I/II structural parity.

## Stage decision

No Stage 2 blocker remains. Stage 3 may begin.
