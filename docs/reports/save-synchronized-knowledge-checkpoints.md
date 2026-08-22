# Save-synchronized knowledge checkpoint verification

Verified: 2026-08-22

DualDex keeps new discoveries in memory during play. It freezes and persists that ledger only after the SaveRAM monitor accepts a changed save file. Direct `.sav` and `.srm` sources receive an atomically replaced `<save>.dualdex.json` sibling; non-atomic sources use a separately keyed app-private fallback.

## Identity and lifecycle contract

A checkpoint is accepted only when all five saved properties match the active observation: ROM SHA-256, parsed save identity, save-file SHA-256, byte size, and modification time. Invalid hashes, negative metadata, schema mismatches, another ROM or save identity, changed file bytes, or changed metadata all fail closed. Legacy app-internal discovery ledgers are not checkpoint envelopes and are never promoted automatically.

The runtime binds live knowledge to one `(ROM, save identity, source)` tuple. The initial valid observation may read an exact checkpoint but performs zero checkpoint writes. An unchanged observation performs zero reads and zero writes. One validated changed observation writes exactly one frozen ledger. Switching source or save identity starts from an exact checkpoint or an empty ledger, never from the preceding playthrough.

## Measured regression evidence

| Contract | Result | Evidence |
| --- | ---: | --- |
| First valid observation | 0 writes | `SaveKnowledgeCheckpointCoordinatorTest.initialObservationReadsButDoesNotWriteCheckpoint` |
| Unchanged polling | 0 reads, 0 writes | `SaveKnowledgeCheckpointCoordinatorTest.unchangedAndResultsWithoutLiveObservationsNeverReadOrWrite` |
| One validated change | 1 write of the runtime-frozen ledger | `SaveKnowledgeCheckpointCoordinatorTest.changedObservationWritesExactlyTheRuntimeFrozenLedger` |
| Exact restart recovery | restored | `SaveKnowledgeCheckpointRestartIntegrationTest.directSaveChangeRestoresTheExactCheckpointAfterRuntimeRestart` closes the first runtime, opens a second runtime, and recovers the saved preference from a real temporary `Game.srm` sibling |
| Cross-ROM/save/file rejection | rejected | `SaveKnowledgeCheckpointCodecTest.checkpointRequiresEveryExactIdentity` and runtime playthrough-isolation tests |
| Legacy ledger promotion | rejected | `SaveKnowledgeCheckpointCodecTest.legacyLedgerIsNotACheckpoint` |
| Direct portable sidecar | passed | `SaveKnowledgeCheckpointStoreTest.directSaveWritesCompleteSiblingAndLeavesNoTemporaryFile` |
| App-private fallback | passed | `SaveKnowledgeCheckpointStoreTest.sourceWithoutAtomicSiblingUsesIsolatedFallback` |
| Atomic temporary cleanup | passed | direct-store tests verify that no `dualdex.tmp` file remains |
| Normal-page diagnostics | none added | checkpoint I/O failure is contained at the optional coordinator boundary and is not added to companion page state or loading copy |

The restart integration uses real filesystem I/O through `DirectSaveDocumentResolver`: it changes the `.srm` bytes and modification time, produces `Game.srm.dualdex.json`, closes the runtime, then requires a fresh runtime to restore the exact matching ledger. No emulator, device, ROM bytes, save contents, or private paths are part of this report.

## Verification commands

```text
./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.*" --tests "com.darkaxt.dualdex.save.*" --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest" --no-daemon --console=plain
./gradlew :save-core:test :companion-core:test :catalog-store:test :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

Both gates completed successfully on 2026-08-22. The affected-module gate completed 55 tasks with zero test or lint failures.
