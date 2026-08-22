# DualDex 1.1.0-rc.41

RC41 prevents discovery data from a previous test or playthrough from appearing in a different active save. Knowledge remains live in memory during play and is frozen only when RetroArch updates the validated SaveRAM file.

## Save-synchronized knowledge

- Direct `.sav` and `.srm` files receive a portable `<save>.dualdex.json` checkpoint beside the save.
- The checkpoint is written through a temporary sibling and atomically replaced; incomplete temporary files are cleaned up.
- Storage providers that cannot perform an atomic sibling write use a separately keyed app-private fallback.
- The initial save observation and unchanged polling perform no writes. A validated change produces one checkpoint containing the current discovery ledger.

## Playthrough integrity

- Restore requires an exact ROM hash, parsed save identity, save-file hash, byte size, and modification time match.
- Switching ROM, save identity, or save source cannot inherit live discoveries from the previous playthrough.
- Old app-internal ledgers are not automatically promoted because they lack a save-file fingerprint.
- SaveRAM remains read-only; DualDex writes only its own JSON sidecar or isolated fallback.

## Verification

- Real direct-`.srm` restart integration: passed, including sidecar creation, runtime closure, fresh runtime creation, and exact recovery.
- Focused knowledge, SaveRAM, and production-runtime suites: passed.
- Affected `save-core`, `companion-core`, `catalog-store`, Android unit-test, and lint gate: passed (55 tasks, zero failures).
- Release metadata and protected-workflow policy: 18/18 passed.
- The verification report is shipped as `dualdex-save-synchronized-knowledge-checkpoints.md`.

## Delivery

- RC41 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- No APK was installed and no emulator or device was used during this implementation gate.
