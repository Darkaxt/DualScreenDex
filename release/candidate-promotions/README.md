# Candidate promotion records

Signed release candidates are created as draft prereleases. A candidate becomes public only through `.github/workflows/promote-candidate.yml`, which runs from the default branch inside the dedicated protected `release-promotion` environment.

For candidate tag `v1.1.0-rc.73`, commit the authorization record as `release/candidate-promotions/v1.1.0-rc.73.json`. The workflow downloads the existing draft APK, provenance, and checksum manifest. It verifies their exact APK SHA-256 and pinned signer, checks the required validation fields, confirms that the release asset ID did not change, and changes only the existing release's draft flag. It does not build, sign, upload, or replace an asset.

The `release-promotion` environment must allow only the default branch, require an authorized reviewer, and contain no secrets. Dispatch the workflow from the repository default branch and provide the existing candidate tag. Keep the tag-only `release-signing` environment and its production secrets unchanged.

## Packaged Android and physical-device mode

Use this mode after the reusable packaged Android workflow and the physical Thor checklist pass:

```json
{
  "schema": 1,
  "candidateTag": "v1.1.0-rc.73",
  "apkSha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "validatedSignerSha256": "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
  "releaseWorkflowRunUrl": "https://github.com/Darkaxt/DualScreenDex/actions/runs/123",
  "validationMode": "packaged-android-and-thor",
  "releaseCiValidated": true,
  "packagedAndroidValidated": true,
  "packagedAndroidWorkflowRunUrl": "https://github.com/Darkaxt/DualScreenDex/actions/runs/123",
  "packagedAndroidEvidenceArtifactDigest": "sha256:DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
  "thorValidated": true,
  "thorValidationRecord": {
    "status": "PASSED",
    "candidateTag": "v1.1.0-rc.73",
    "apkSha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "validatedSignerSha256": "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    "checklistVersion": 1
  }
}
```

Replace the sample tag, APK hash, run URL, and artifact digest with the candidate's published provenance and successful release workflow. That exact source-bound release run must contain the immutable `dualdex-packaged-android-evidence` artifact emitted after the reusable managed-device gate. The physical validation object must repeat the exact candidate tag, signed APK hash, and signer without device serials or personal details.

## Authorized passive-catalog substitution

A change limited to passive catalog generation and presentation may use the existing user-authorized substitution instead of claiming device validation:

```json
{
  "schema": 1,
  "candidateTag": "v1.1.0-rc.73",
  "apkSha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "validatedSignerSha256": "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
  "releaseWorkflowRunUrl": "https://github.com/Darkaxt/DualScreenDex/actions/runs/123",
  "validationMode": "automated-passive-catalog",
  "releaseCiValidated": true,
  "userAuthorizedAutomatedPromotion": true,
  "gameplayRuntimeChanged": false,
  "exactRomControls": 5,
  "catalogPersistenceValidated": true,
  "runtimeApiValidated": true,
  "webPresentationValidated": true,
  "automatedValidationWorkflowRunUrl": "https://github.com/Darkaxt/DualScreenDex/actions/runs/789",
  "automatedEvidencePath": "docs/reports/candidate-promotions/v1.1.0-rc.73.json",
  "automatedEvidenceSha256": "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"
}
```

The automated workflow URL must identify a successful `.github/workflows/ci.yml` run for the candidate source commit. The evidence path must stay under `docs/reports/candidate-promotions/`; its SHA-256 and required schema bind at least five exact-ROM controls and their catalog/runtime/web outcomes to the same source commit, tag, and signed APK. See `docs/reports/candidate-promotions/README.md`.

The validator rejects a different APK hash, cryptographic signer, signing workflow run, candidate tag, source commit, incomplete gate, evidence digest, or unrecognized validation mode.
