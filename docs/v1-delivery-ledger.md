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
