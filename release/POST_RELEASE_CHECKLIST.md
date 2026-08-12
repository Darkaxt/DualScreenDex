# DualDex post-release handoff

This handoff is mandatory for every signed candidate. The registry update is not allowed to outrun the artifact that it advertises.

## 1. Verify the protected GitHub release

- Confirm the tag is new and the release was created by the protected signing workflow.
- Download the APK, `SHA256SUMS.txt`, `provenance.json`, certificate, both parser compatibility documents, and both grouped ROM-property documents.
- Recompute every checksum and verify the APK package, version name/code, and certificate fingerprint against the release metadata.

## 2. Validate the signed APK on the physical AYN Thor

Run the repository validator against the downloaded asset, using the published checksum and candidate version:

```powershell
pwsh -File tools/android/validate-signed-candidate.ps1 `
  -ApkPath <downloaded-apk> `
  -ExpectedSha256 <published-sha256> `
  -ExpectedVersionName <candidate-version> `
  -ExpectedVersionCode <candidate-version-code> `
  -Target Thor -ThorSerial bfa98654 -Install
```

Verify the real user flow on the lower display: launch, ROM selection, catalog reopen, ROM-scoped settings persistence, capability report, and Docked/Overlay display behavior. Do not claim the candidate is Thor-validated from signer or installation evidence alone.

## 3. Refresh the existing GAFT registry entry

Only after sections 1 and 2 pass, refresh <https://github.com/andreyvelsk/GAFT>. Locate the existing DualScreenDex `content/<slug>/index.md`; update it instead of creating a duplicate.

Use the verified release URL, current screenshots/media, actual setup flow, AYN Thor dual-display guidance, first-50 compatibility scope, and truthful limitations. Run GAFT's documented preview/build checks and confirm links and media resolve.

Commit the single existing-entry update, push the contribution branch, and open the upstream pull request. Record the PR URL in the DualDex release handoff evidence.
