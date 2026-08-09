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
