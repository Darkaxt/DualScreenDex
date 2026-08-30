# RetroArch-free raw-memory QA validation — 2026-08-30

Validated checkpoint: `4efbd188` (`feat: package raw live-memory QA scenarios`)

## Static and packaged boundary

- Focused `RawLiveMemory*` and `RetroArchFreeUiQa*` debug tests passed.
- Debug and release APK assembly passed, including release lint-vital analysis.
- Artifact-enforced `RetroArchFreeUiQaIsolationTest` passed against both APKs.
- Debug instrumentation sources compiled.
- The debug APK contained the simulator, controls, and raw scenario asset; the release APK contained none of them and no ROM-like asset.
- The sanitizer reproduced the committed asset byte-for-byte from the reviewed source dump.

The parser corpus was not rerun because parser, catalog, build-wrapper, and corpus-execution code did not change.

## Emulator authority and geometry

Validation used only the thread-owned `DualDexThorQaApi35` AVD on `emulator-5556`. The physical Thor and the unrelated `NavicReaderLab` emulator were not accessed.

- Physical display: `1240×1080`, `369 dpi`, font scale `0.95`.
- App-area overlay: display `3`, `1240×1025`, `369 dpi`.
- Production WebView metrics:
  - `innerWidth=538`
  - `innerHeight=445`
  - `devicePixelRatio=2.3062500953674316`
  - `visualViewport.width=538.1029663085938`
  - `visualViewport.height=445.3116455078125`
  - landscape and `(max-width: 680px)=true`

## Exact Modern Emerald activation

An external ROM was verified before use:

- Name: `Modern Emerald (v3.5).gba`
- SHA-256: `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895`
- CRC32: `8C7DBECA`
- Size: `33,554,432` bytes

The ROM was staged only in emulator shared storage and indexed through the production all-files storage path. Production state reported:

- `catalogReady=true`
- `gameAccessReady=true`
- `knowledgeMode=DISCOVERED`
- `retroArch.connection=PAUSED`
- `retroArch.resolution=ACTIVE`
- `retroArch.activeSource=Modern Emerald (v3.5).gba`
- exact content SHA-256 and CRC32
- `indexedRoms=1`
- session epoch `1`

The first sanitized overworld frame published through the production decoder and runtime as area `10`, position `(6,17)`, clock `15:55 DAY`, and two occupied party slots.

## Native controls and failure isolation

The debug-native control Activity rendered at the full `1240×1025` app area.

- Initial state: `modern-normal`, `overworld-1 (1/6)`, paused.
- **Step** advanced exactly once to `battle-start (2/6)` and remained paused.
- A new control Activity instance retained `battle-start (2/6)`, proving that control state is application-owned rather than Activity-owned.
- **Play** entered playing state and advanced frames; **Pause** froze the selected frame.
- The battle-start frame published a live wild Poochyena battle through production decoding and rendered the production battle/Rarity UI at exact Thor WebView geometry.
- `modern-malformed` kept the process and API responsive with no global error while clearing battle, party, area, and map-position modules.
- `stale-identity` produced `resolution=NOT_FOUND`, cleared active source/content SHA/session epoch and all volatile game state, without crashing.
- Returning to `modern-normal` restored exact SHA authority, `resolution=ACTIVE`, area, position, clock, and party data at session epoch `5`.

`POST /api/load` was confirmed to load a catalog but not to grant/index a ROM for session authority. The runbook now requires the external storage grant/index path.

## Cleanup

The external emulator ROM, debug package, temporary device XML, and ADB forwards were removed. The cleaned AVD was saved as snapshot `DualDexRawMemoryQaClean-4efbd188` and stopped. The usable QA gate is closed; emulator-rendered Thor usability analysis can proceed without RetroArch.
