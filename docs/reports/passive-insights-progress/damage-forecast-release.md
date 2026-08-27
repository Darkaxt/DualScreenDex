# RC73 Damage Forecast candidate handoff

**Result:** SIGNED DRAFT VERIFIED; PUBLIC PROMOTION PENDING — the protected workflow created `v1.1.0-rc.73-hotfix.1` from commit `7bc0e2ea44778a82d5f19883ceb05cfebfebce34`. The separately downloaded signed APK matches the release checksum and provenance, exposes the exact package identity, and is signed by the pinned DualDex release certificate.

The first immutable `v1.1.0-rc.73` tag failed before signing because two new real-ROM tests asserted private local fallback paths on the Linux runner. No release or signed asset was created for that tag. The corrected tests still run the local real controls, skip only when neither an explicit control nor the verified local corpus exists, and fail when an explicitly configured control is missing. Tag immutability was preserved by using `v1.1.0-rc.73-hotfix.1` rather than moving the failed tag or jumping to RC74.

## Candidate evidence

| Measurement | Verified value |
| --- | --- |
| Draft release | Authenticated draft `v1.1.0-rc.73-hotfix.1`, prerelease, not public |
| Protected workflow | [`33075756111`](https://github.com/Darkaxt/DualScreenDex/actions/runs/33075756111), successful |
| Source | Annotated tag `v1.1.0-rc.73-hotfix.1` resolves to `7bc0e2ea44778a82d5f19883ceb05cfebfebce34` |
| APK | `DualDex-v1.1.0-rc.73-hotfix.1.apk`, 18,444,860 bytes |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.73-hotfix.1`, code `1010074` |
| APK SHA-256 | `3191E17BEC6A4E8C15D7F86385650EAAAEB5FE8EB6BA9D33CFFB494ADB5B0EDA` |
| Signer SHA-256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Draft assets | 41 assets, including the signed APK, provenance, certificate, checksums, Damage Forecast audit, and exact Damage Forecast compatibility report |

## Completed gates

- Local affected JVM and Android unit inventory: 1,836 tests, 0 failures, 0 errors.
- Local browser suite: 225/225 across 30 files; production TypeScript/Vite build passed.
- Debug lint, unsigned release assembly, Android-test compilation, release-policy tests, and numeric reporter tests passed.
- Protected exact-tag source, release-policy, compatibility-documentation, Android deployment-safety, web, full-module, lint, unsigned package identity, and handoff gates passed.
- The managed packaged-app acceptance workflow passed and uploaded immutable source-bound evidence.
- The isolated signing job reconstructed the protected key, verified the pinned signer, signed and reverified the APK, generated provenance/checksums, and created a new non-replacing draft release.
- An independent authenticated download matched the APK, certificate, Damage Forecast documents, and provenance against `SHA256SUMS.txt`; `aapt` and `apksigner` confirmed the expected package, version, one signer, and V3 signature.

## Promotion boundary

Damage Forecast changes live runtime projection and Battle presentation, so `gameplayRuntimeChanged: false` would be untrue. The automated passive-catalog substitution is therefore forbidden. The exact signed draft must remain unchanged until the user validates it on the physical AYN Thor and the protected promotion workflow verifies the resulting authorization record. No local device, ADB, or emulator interaction was performed during implementation or candidate verification.
