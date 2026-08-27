# DualDex post-release handoff

This handoff is mandatory for every signed candidate. The registry update is not allowed to outrun the artifact that it advertises.

## 1. Verify the protected GitHub draft release

- Confirm the tag is new, the release was created as a nonpublic draft prerelease by the protected signing workflow, and the signed APK has not been replaced.
- Download the APK, `SHA256SUMS.txt`, `provenance.json`, certificate, both Parser Compatibility documents, both ROM Hacks Compatibility documents, the exact first-50 base/map/evolution/ARM7 gate documents, and the full-332 base summary/matrix.
- Recompute every checksum and verify the APK package, version name/code, and certificate fingerprint against the release metadata.

## 2. Validate the signed APK on the physical AYN Thor

The user performs device installation and validation. Agents must not invoke
ADB, an emulator, the user's console, or the install mode of the repository
validator. The user may run the repository validator against the downloaded
asset, using the published checksum and candidate version:

```powershell
pwsh -File tools/android/validate-signed-candidate.ps1 `
  -ApkPath <downloaded-apk> `
  -ExpectedSha256 <published-sha256> `
  -ExpectedVersionName <candidate-version> `
  -ExpectedVersionCode <candidate-version-code> `
  -Target Thor -ThorSerial bfa98654 -Install
```

Verify the real user flow on the lower display: launch, ROM selection, catalog reopen, ROM-scoped settings persistence, capability report, and Docked/Overlay display behavior. Do not claim the candidate is Thor-validated from signer or installation evidence alone.

For a release whose changes are limited to passive catalog generation and presentation, the user
may instead authorize automated promotion. That path must record the exact signed candidate hash,
pinned signer, at least five exact real-ROM controls, CatalogStore reopen, runtime API projection,
web presentation verification, successful release CI, and `gameplayRuntimeChanged: false`. It must
not claim AVD or Thor validation.

## 3. Promote the exact signed candidate

Only after sections 1 and 2 pass, add the canonical record described in `release/candidate-promotions/README.md` on the default branch. Its candidate tag, APK SHA-256, signer, and release workflow run must match the downloaded provenance exactly. Record either the successful packaged Android workflow plus Thor validation or every field required by the authorized passive-catalog substitution.

Dispatch `.github/workflows/promote-candidate.yml` from the default branch for that tag and approve its dedicated protected `release-promotion` environment. Confirm the workflow changed the existing release from draft to public prerelease without changing the release ID or signed APK asset ID. The promotion environment must contain no signing secrets; `release-signing` remains tag-only.

## 4. Refresh the existing GAFT registry entry

Only after section 3 passes, refresh <https://github.com/andreyvelsk/GAFT>. Locate the existing DualScreenDex `content/<slug>/index.md`; update it instead of creating a duplicate.

Use the verified release URL, current screenshots/media, actual setup flow, AYN Thor dual-display guidance, first-50 compatibility scope, and truthful limitations. Run GAFT's documented preview/build checks and confirm links and media resolve.

Commit the single existing-entry update, push the contribution branch, and open the upstream pull request. Record the PR URL in the DualDex release handoff evidence.
