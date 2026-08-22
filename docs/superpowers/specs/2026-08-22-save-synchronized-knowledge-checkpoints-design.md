# Save-Synchronized Knowledge Checkpoints Design

## Objective

Make Organic/Discovered playthrough knowledge portable and save-consistent by checkpointing it beside the active `.sav` or `.srm` only when that validated save file changes. An APK update must not lose the checkpoint, a different save must never inherit it, and live discoveries must not be written continuously.

## User contract

- DualDex keeps discoveries in memory while the game is running.
- A checksum-valid save-file change is the only event that freezes the current ledger.
- The portable sibling is named after the complete save filename: `Game.srm.dualdex.json` or `Game.sav.dualdex.json`.
- The checkpoint represents knowledge as it existed when that exact save payload was observed.
- Loading requires an exact match on ROM SHA-256, save identity, save-file SHA-256, save size, and save modification time.
- A copied timestamp alone never authorizes a checkpoint.
- The write uses a temporary sibling followed by atomic replacement. Temporary debris is removed after failure.
- When atomic sibling replacement is unavailable, DualDex writes the same fingerprinted envelope to app-private fallback storage and marks it non-portable only in Debug Settings.
- Existing app-private ledgers are never promoted automatically. They have no save-file fingerprint and could reproduce the stale-playthrough contamination already observed.

## State model

`SavePollingMonitor` classifies a validated observation as one of:

- `INITIAL`: first validated observation in this process. It may load a matching checkpoint but does not create one.
- `UNCHANGED`: source ID, size, modification time, and save SHA-256 match the previous observation. It neither reloads nor writes.
- `CHANGED`: the same active save source and stable save identity have a new validated payload. It merges the current in-memory ledger with the new save snapshot and writes one checkpoint.
- `SWITCHED`: ROM, source ID, or save identity changed. It replaces the ledger from an exact matching checkpoint or an empty ledger before applying SaveRAM facts; it never carries knowledge from the previous playthrough.
- `RESTORED`: an app-private SaveRAM snapshot was restored because the source is absent. It may retain already loaded in-process knowledge but cannot write or authorize a checkpoint.

The save SHA-256 is computed over the byte array already read for parsing. The feature must not add another full save buffer.

## Checkpoint envelope

Schema 1 contains:

```json
{
  "schema": 1,
  "portable": true,
  "romSha256": "64 lowercase hex characters",
  "saveIdentity": "64 lowercase hex characters",
  "saveFileSha256": "64 lowercase hex characters",
  "saveSize": 131072,
  "saveLastModifiedEpochMs": 1787371200000,
  "capturedAtEpochMs": 1787371200123,
  "ledger": {}
}
```

`ledger` uses the existing deterministic schema-6 knowledge payload. Every decoded ledger is passed through `KnowledgeLedgerSanitizer` against the active catalog before use.

## Selection and merge order

For `INITIAL` or `SWITCHED`:

1. Parse and validate SaveRAM.
2. Derive ROM identity, stable save identity, and exact file fingerprint.
3. Read the sibling checkpoint; if unavailable, read only the fingerprinted app-private fallback.
4. Reject a malformed or nonmatching envelope without partially applying it.
5. Seed with the exact checkpoint ledger or `KnowledgeLedger()`.
6. Merge checksum-valid SaveRAM facts and publish the selected player snapshot.
7. Do not write merely because the app started.

For `CHANGED` on the same bound playthrough:

1. Freeze the current in-memory ledger.
2. Merge the new checksum-valid SaveRAM facts.
3. Publish the result.
4. Serialize one new exact envelope.
5. Atomically replace the sibling, or atomically replace the fingerprinted app-private fallback when a sibling target is unavailable.

## Storage boundaries

- `DirectSaveDocumentResolver` exposes an atomic sibling target rooted in the save's real parent directory.
- SAF/document-provider saves remain readable, but are considered non-portable unless the provider can offer true atomic sibling replacement. The first implementation uses app-private fallback for them rather than delete-then-rename semantics.
- `FileKnowledgeRepository` remains capable of reading legacy documents for explicit migration/debug tooling, but the production checkpoint path does not call it.
- The new app-private fallback uses a separate `knowledge-checkpoints` directory and the same fingerprinted envelope as the portable sidecar.

## Failure behavior

- Parser, hash, identity, schema, or ledger validation failure rejects the candidate and preserves the last valid in-memory state.
- Sidecar read/write failure never blocks SaveRAM application or live UI refresh.
- A failed write leaves the previous complete checkpoint untouched.
- A malformed sidecar does not fall back to a legacy unfingerprinted ledger.
- No checkpoint status or identity appears in normal pages. Storage diagnostics are allowed only in Debug Settings.

## Verification gates

- Direct-file exact-match, mismatch, atomic replacement, and temporary-cleanup tests.
- First observation does not write; unchanged observation does not write; one changed save writes exactly once.
- Same-playthrough changes retain live discoveries; switched saves do not.
- Legacy app-private knowledge is not promoted.
- Read-only/non-atomic sources use the isolated fingerprinted fallback.
- App restart loads only the checkpoint matching the exact current save bytes.
- Existing SaveRAM, knowledge, POI, Party, Battle, and catalog tests remain green.

## Out of scope

- Continuous event logging.
- Writing to the game save itself.
- Cloud synchronization or conflict merging.
- Automatic import of schema-6 legacy ledgers.
- UI outside the Debug Settings diagnostic surface.
