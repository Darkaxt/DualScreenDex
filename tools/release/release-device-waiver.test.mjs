import assert from "node:assert/strict";
import test from "node:test";
import { deriveReleaseMetadata } from "./derive-release-metadata.mjs";

function waivedFinal() {
  const sourceCommit = "dfadcc2369763ec8f0f9c3136458771c48af3329";
  const tree = "1".repeat(40);
  const signer = "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA";
  const provenance = "2E620102B25B6D8A188AAE44F26E69E67B4A8787B5067FE52C4F7A7CAB455A08";
  const apk = "6D373B3A6C60D08C233541F66CC3DBBC18D03E6A0C8B88D770E8063B321F7A3E";
  const waiver = {
    authorizedBy: "user",
    authorizedDate: "2026-10-18",
    instruction: "Skip, the validation",
    scope: "v1.2.3 from v1.2.3-rc.4 only",
  };
  return {
    tag: "v1.2.3",
    ready: {
      schema: 1, stage: 8, status: "ready-for-github-signing", openV1LedgerItems: 0,
      applicationId: "com.darkaxt.dualdex", versionName: "1.2.3", productionCertificateSha256: signer,
      qaClosure: {
        schemaVersion: 1, evidenceSourceCommit: "9".repeat(40), inputCount: 342,
        stage7Closed: true, stage8Closed: true, localizationClosed: true, openBlockers: 0, openReferrals: 0,
      },
    },
    certificateFingerprint: signer,
    releaseEvidenceValidation: {
      schemaVersion: 2, evidenceSourceCommit: "9".repeat(40), inputCount: 342,
      stage7Closed: true, stage8Closed: true, localizationClosed: true,
    },
    finalAuthorization: {
      schema: 2, versionName: "1.2.3", sourceCandidateTag: "v1.2.3-rc.4",
      sourceCandidateCommit: sourceCommit, sourceCandidateTree: tree,
      candidateProvenanceSha256: provenance, githubSignedCandidateSha256: apk, validatedSignerSha256: signer,
      validationMode: "packaged-android-user-waiver", userAuthorizedSkipDeviceValidation: true,
      deviceValidationWaiver: waiver, avdValidated: false, thorValidated: false,
    },
    candidatePromotion: {
      schema: 1, candidateTag: "v1.2.3-rc.4", sourceCommit,
      candidateProvenanceSha256: provenance, apkSha256: apk, validatedSignerSha256: signer,
      validationMode: "packaged-android-user-waiver", userAuthorizedSkipDeviceValidation: true,
      deviceValidationWaiver: structuredClone(waiver), thorValidated: false,
      releaseCiValidated: true, packagedAndroidValidated: true,
      releaseWorkflowRunUrl: "https://github.com/Darkaxt/DualScreenDex/actions/runs/39119545357",
      packagedAndroidWorkflowRunUrl: "https://github.com/Darkaxt/DualScreenDex/actions/runs/39119545357",
      packagedAndroidEvidenceArtifactDigest: "sha256:0b3f5a1c7926c3fb9d9dbd8abec93812c97a121d9bdc225389a8118d38f07488",
    },
    repositoryState: {
      sourceCandidateCommit: sourceCommit, sourceCandidateTree: tree,
      candidateProvenanceSha256: provenance, candidateApkSha256: apk,
      changedPaths: ["release/v1-final-authorization.json"],
    },
  };
}

test("accepts the explicit current-release device waiver without claiming device validation", () => {
  const result = deriveReleaseMetadata(waivedFinal());
  assert.equal(result.version_code, "1020399");
  assert.equal(result.release_kind, "final");
  assert.equal(result.draft, "false");
});

test("does not turn the current-release waiver into permission for another artifact or release", () => {
  for (const mutate of [
    input => input.candidatePromotion.candidateTag = "v1.2.3-rc.5",
    input => input.candidatePromotion.sourceCommit = "2".repeat(40),
    input => input.candidatePromotion.apkSha256 = "A".repeat(64),
    input => input.candidatePromotion.candidateProvenanceSha256 = "B".repeat(64),
    input => { input.tag = "v1.2.4"; input.ready.versionName = "1.2.4"; input.finalAuthorization.versionName = "1.2.4"; },
  ]) {
    const input = waivedFinal();
    mutate(input);
    assert.throws(() => deriveReleaseMetadata(input));
  }
});

test("waiver still requires explicit authority, exact hosted evidence and no invented physical pass", () => {
  for (const mutate of [
    input => input.candidatePromotion.userAuthorizedSkipDeviceValidation = false,
    input => input.finalAuthorization.userAuthorizedSkipDeviceValidation = false,
    input => input.candidatePromotion.deviceValidationWaiver.authorizedBy = "assistant",
    input => input.finalAuthorization.deviceValidationWaiver.scope = "all releases",
    input => input.candidatePromotion.deviceValidationWaiver.authorizedDate = "2026-10-17",
    input => input.candidatePromotion.thorValidated = true,
    input => input.candidatePromotion.thorValidationRecord = { status: "PASSED" },
    input => input.finalAuthorization.thorValidated = true,
    input => input.finalAuthorization.avdValidated = true,
    input => input.candidatePromotion.releaseCiValidated = false,
    input => input.candidatePromotion.packagedAndroidValidated = false,
    input => input.candidatePromotion.packagedAndroidWorkflowRunUrl += "?different=true",
    input => input.candidatePromotion.packagedAndroidEvidenceArtifactDigest = `sha256:${"A".repeat(64)}`,
    input => input.repositoryState.changedPaths.push("app/src/main/java/Product.kt"),
  ]) {
    const input = waivedFinal();
    mutate(input);
    assert.throws(() => deriveReleaseMetadata(input));
  }
});
