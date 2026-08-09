# DualDex v1 Delivery Ledger

This ledger records discrepancies found while executing the staged v1 plan. Planned later stages are not defects. A public release requires zero open `STOP-SAFETY`, `STOP-CORE`, `CONVERGENCE`, or `TUNING` entries tied to the v1 specification.

| ID | Spec reference | Stage found | Class | Observed | Expected | Evidence | Temporary disposition | Fixing commit | Status |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| `V1-001` | First-release design 12.2 | 0 | `CONVERGENCE` | The encrypted credential export is recoverable only by the current Windows user profile, although GitHub can continue signing with its environment secrets. | A portable, independently verified recovery path for the long-lived signer. | `H:\My Drive\Keys\DualDex\RECOVERY-CONTEXT.txt` documents the current limitation. | Keep GitHub signing continuity active; perform and document a portable restore drill before Stage 8. |  | Open |

## Stage evidence

### Stage 0 baseline

- Kotlin/JVM module regression: passed before implementation.
- Browser regression: 9 files and 22 tests passed; production Vite build passed.
- Existing running AVD at start: `NavicReaderLab` on `emulator-5554`.
- Dedicated AVD: `DualDex_RA_API35` stored under `D:\Android\avd` and launched separately on `emulator-5556`.
- Existing-emulator package inventory SHA-256 before/after dedicated AVD creation: `2355CB55D9A87B9A837518C66AA067D25A6A4599DAD9D7CAC40ABBADFE87D7F4`.
- Debug package: `com.darkaxt.dualdex.debug`, version `1.0.0-debug`, Android debug signer only.
- Local release signing configuration: absent by design.
- Dedicated RetroArch: official universal 1.22.2 APK, SHA-256 `2ECA60B3A540697CD7676EDEA2FBABDFAF54C3AE958584168231981099D2868A`, installed as x86_64 package `com.retroarch` only on the dedicated AVD.
- Dedicated AVD checkpoint: snapshot `dualdex-stage0-apps` contains only the scoped Stage 0 app installations.
- Production certificate SHA-256: `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`.
- GitHub signing boundary: `release-signing` environment contains the four required secrets and accepts only `v1.*` tags; the workflow remains fail-closed until Stage 7.
- Recovery context: private keystore/credentials are excluded from Git; public certificate and fingerprint only are tracked.

### Stage 1 ROM Pokédex

- Debug APK: `com.darkaxt.dualdex.debug` built at 13,072,345 bytes with SHA-256 `B8097BF3BBBA8BABF5A060EF70294776DFC1A6044805E0F40FDB5FDF10D8F439`; installed only on `DualDex_RA_API35` / `emulator-5556`.
- Android host: production Vite assets are packaged under `dualdex-web`; the native server's sole app-owned listener was `0000000000000000FFFF00000100007F`, the IPv4-mapped loopback address. Native recovery remains available when startup fails.
- Permission audit: the app requests only `android.permission.INTERNET`; the package manager's additional `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is an AndroidX-generated signature permission. OCR, accessibility, media projection, notification, storage, and screenshot permissions/services are absent.
- Direct-ROM run: `Pokemon - Emerald Version (USA, Europe).gba`, CRC32 `1F1C08FB`, completed `5/5` catalog phases and rendered 386 species, 355 moves, and 18 types.
- ZIP-stream run: untouched `Pokemon - Modern Emerald Version v3.5 (USA, Europe).zip`, inner CRC32 `8C7DBECA`, completed `5/5` catalog phases and rendered 428 species, 369 moves, 19 types, 231 areas, and two selectable rulesets without a permanently extracted ROM.
- AVD visual checks covered the full browse list, persistent ROM identity, Settings replacement picker, ROM sprites/type colors, Charizard Entry/Stats/Moves, and the Flamethrower detail page. System-bar insets keep the loaded-ROM strip and scrolling content out from under Android chrome.
- Production asset audit: Vite emitted one 40.67 kB JavaScript entry and one 20.52 kB stylesheet; the dedicated development entry was tree-shaken. Scans found no `DualDex Lab`, encounter generator, seed, attack-reference, or diagnostics strings in `dist`.
- Browser regression: 12 files and 27 tests passed, including ROM-derived capture-ball artwork; production TypeScript/Vite build passed; no `DualDexConsole`, uncaught JavaScript, or Android runtime errors appeared during the final Modern Emerald run.
- Kotlin/Android regression: 162 parser/CLI/core/simulator/server/app unit tests passed with zero failures or skips. The dedicated AVD instrumentation test passed and asserted launch, loopback origin, WebView presence, and top/bottom system-bar insets.
- Stage 1 planned deferrals are unchanged: durable SQLite caching/progressive reopen, automatic RetroArch active-ROM matching, and save-derived Organic/Team/capture state belong to Stages 2–5.

### Stage 2 persistent ROM catalog

- Database contract: parser schema 1 uses one metadata row plus ten required `gzip+json` sections for species, moves, types, abilities, the type chart, encounters, capture balls, learnset rulesets, capabilities, and diagnostics. A catalog is reopenable only after a single transaction marks all required sections complete.
- Identity contract: the database filename and authoritative join key are the full ROM SHA-256. CRC32, byte size, internal title, family, platform, source kind, and selected ZIP entry are retained only as lookup and verification evidence; basenames never authorize a cache match.
- Recovery checks: incomplete transactions are not published, incompatible parser-schema catalogs are invalidated, corrupt private cache files are rejected, and direct and ZIP sources containing identical ROM bytes resolve to one database.
- Android native SQLite: official Emerald produced `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af.sqlite` at 569,344 bytes with CRC32 `1F1C08FB`, direct-source metadata, all ten sections, and `PRAGMA integrity_check = ok`. Modern Emerald produced its independent SHA database at 663,552 bytes with ZIP-entry provenance.
- Cold versus reopen: Modern Emerald completed a cold ZIP parse and persistence in 101,783 ms; process-start cache reopen completed in 3,677 ms without ROM/provider access (about 27.7 times faster). Official Emerald completed its direct-file parse and persistence in 109,288 ms after selection; the preceding file-picker wait is excluded from parser time.
- Progressive presentation: committed parser work publishes 0/20/40/60/80/100 percent and renders `Loading... (N%)`; the value is derived only from committed work units. Finer `EXTENDED` sub-progress remains a Stage 7 tuning candidate only if real parser sub-units are introduced.
- Corpus: all 14 in-scope named ROMs selected, wrote, closed, reopened, and decoded equal to their source catalog. This includes 11 exact official games and Modern Emerald, Sword and Shield Ultimate Plus, and Unbound as structural derivatives; spin-offs and Mystery Dungeon remain excluded. All 14 SQLite files passed `PRAGMA integrity_check` and the report contains no decoded text, sprites, ROM bytes, or save data.
- Regression: 171 JVM/Android unit tests passed with zero failures or skips; all 95 requested Gradle tasks completed; 12 browser files and 28 tests passed; the production TypeScript/Vite build passed; and both dedicated-AVD instrumentation tests passed, including the native Android SQLite transaction path.
- Debug APK: 13,149,500 bytes, SHA-256 `90FD9168989AD2615F2697DB85C5410A46B4BA686F9BDAFA08B0EFF174410CD4`, installed only on `DualDex_RA_API35` / `emulator-5556`. The existing `emulator-5554` was not addressed by any Stage 2 install or test command.

### Stage 3 RetroArch activation and display overlay

- RetroArch test baseline: the dedicated AVD was updated in place to the official 2026-08-09 nightly, package `com.retroarch`, version `1.22.2_GIT`, version code `1786291263`, APK SHA-256 `4513C020E468E012D89FDEE9653F8317885FECD67180ACFDCEEBA0D7DC4AE757`. This nightly contains the Android Network Command listener correction made after stable 1.22.2; no RetroArch package on `emulator-5554` or the physical Thor was changed.
- Configuration contract: the granted active file is `/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg`. Read-back shows `network_cmd_enable = "true"`, port `55355`, `network_remote_enable = "false"`, `stdin_cmd_enable = "false"`, and `config_save_on_exit = "true"`. Only the two approved Network Command keys are patched. The contextual `retroarch.cfg.dualdex-recovery` document was removed after verified read-back.
- Restart verification: a config write or already-correct document enters `RESTART_REQUIRED`. A listener that was already connected cannot satisfy verification until a disconnect/reconnect is observed; a listener first reached after RetroArch was stopped satisfies the same handshake on its first valid reply.
- Current NCI compatibility: the parser accepts the nightly's actual response `GET_STATUS PLAYING game_boy_advance,Pokemon - Modern Emerald Version v3.5 (USA, Europe)` without inventing a CRC field, preserves commas in content names, and accepts underscore system slugs. CRC remains an optional extension when a build supplies it.
- Active-content resolution: two granted sources were indexed, including the untouched Modern Emerald ZIP. The current status resolved its inner GBA member, reopened the SHA-256-keyed catalog, and published CRC32 `8C7DBECA`, 428 species, resolution `ACTIVE`, and the full ZIP/member source name. Catalog activation is published only after the verified catalog actually opens; last-cache restoration cannot overwrite a newer live-session activation.
- Fallbacks: stopping RetroArch moved the session to `DISCONNECTED` without unloading the Modern Emerald catalog. Manual ROM loading remained available, and the setup page exposed exact Network Commands and Save Current Configuration breadcrumbs whenever automatic verification was not complete.
- Display modes: Settings defaults to `DOCKED`. Opting into `OVERLAY` starts a user-enabled `specialUse` foreground service, backgrounds the normal activity, and renders the active ROM's Poké Ball sprite in a draggable 64 dp bubble. Tapping it toggles the same companion in a fixed 4:3 panel. With that panel visible, window focus remained on RetroArch while the companion API simultaneously reported `PLAYING`, `ACTIVE`, and the verified Modern Emerald source. Choosing Docked removed the panel and bubble, stopped the service, and restored `MainActivity` to the foreground.
- Overlay failure path: with `SYSTEM_ALERT_WINDOW` denied, selecting Overlay opened Android's package-specific permission page. Returning without approval left no overlay service or window and restored `displayMode = DOCKED`; the permission was restored only after this check on the dedicated AVD.
- Regression: 198 JVM/Android unit tests passed with zero failures, errors, or skips; all 13 browser test files and 33 tests passed; the production TypeScript/Vite build passed; and both Android instrumentation tests passed when installed and invoked explicitly only on `emulator-5556`.
- Debug APK: 14,315,090 bytes, SHA-256 `5909A3E7F53FC19C2BECA3CC54693940D46A45B2571DE92ED72A7C0353983A51`, installed only on `DualDex_RA_API35` / `emulator-5556`. It remains a local Android-debug-signed artifact and is not a public release.
