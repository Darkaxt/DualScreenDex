import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { deriveReleaseMetadata } from "./derive-release-metadata.mjs";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(testDirectory, "../..");
const script = join(testDirectory, "derive-release-metadata.mjs");
const fingerprintFile = join(repositoryRoot, "signing", "dualdex-release-cert.sha256");
const testEvidenceSourceCommit = "9".repeat(40);

function readyMarker() {
  return {
    schema: 1,
    stage: 8,
    status: "ready-for-github-signing",
    openV1LedgerItems: 0,
    applicationId: "com.darkaxt.dualdex",
    versionName: "1.1.0",
    productionCertificateSha256:
      "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    qaClosure: {
      schemaVersion: 1,
      evidenceSourceCommit: testEvidenceSourceCommit,
      stage7Closed: true,
      stage8Closed: true,
      openBlockers: 0,
      openReferrals: 0,
    },
  };
}

function createTemporaryDirectory() {
  return mkdtempSync(join(process.env.RUNNER_TEMP || tmpdir(), "dualdex-release-test-"));
}

function runMetadata(tag, finalAuthorization, existingTags = []) {
  const directory = createTemporaryDirectory();
  try {
    const outputFile = join(directory, "github-output.txt");
    const readyFile = join(directory, "ready.json");
    const evidenceValidationFile = join(directory, "release-evidence-validation.json");
    writeFileSync(readyFile, JSON.stringify(readyMarker()));
    writeFileSync(evidenceValidationFile, JSON.stringify({
      schemaVersion: 2,
      evidenceSourceCommit: testEvidenceSourceCommit,
      inputCount: 333,
      stage7Closed: true,
      stage8Closed: true,
    }));
    const argumentsList = [
      script,
      "--tag",
      tag,
      "--ready",
      readyFile,
      "--release-evidence-validation",
      evidenceValidationFile,
      "--certificate-fingerprint",
      fingerprintFile,
    ];
    if (finalAuthorization) {
      const sourceCandidateCommit = "1".repeat(40);
      const sourceCandidateTree = "2".repeat(40);
      const candidateProvenanceSha256 = "3".repeat(64);
      const enrichedAuthorization = {
        ...finalAuthorization,
        schema: 2,
        sourceCandidateCommit,
        sourceCandidateTree,
        candidateProvenanceSha256,
      };
      const candidateApkSha256 = enrichedAuthorization.githubSignedCandidateSha256;
      const finalAuthorizationFile = join(directory, "final-authorization.json");
      const candidatePromotionFile = join(directory, "candidate-promotion.json");
      const changedPathsFile = join(directory, "changed-paths.txt");
      writeFileSync(finalAuthorizationFile, JSON.stringify(enrichedAuthorization));
      writeFileSync(candidatePromotionFile, JSON.stringify({
        schema: 1,
        candidateTag: enrichedAuthorization.sourceCandidateTag,
        sourceCommit: sourceCandidateCommit,
        candidateProvenanceSha256,
        apkSha256: candidateApkSha256,
      }));
      writeFileSync(changedPathsFile, "release/v1-final-authorization.json\n");
      argumentsList.push(
        "--final-authorization", finalAuthorizationFile,
        "--candidate-promotion", candidatePromotionFile,
        "--candidate-source-commit", sourceCandidateCommit,
        "--candidate-source-tree", sourceCandidateTree,
        "--candidate-provenance-sha256", candidateProvenanceSha256,
        "--candidate-apk-sha256", candidateApkSha256,
        "--changed-paths", changedPathsFile,
      );
    }
    if (existingTags.length > 0) {
      const existingTagsFile = join(directory, "existing-tags.txt");
      writeFileSync(existingTagsFile, `${existingTags.join("\n")}\n`);
      argumentsList.push("--existing-tags", existingTagsFile);
    }

    const result = spawnSync(process.execPath, argumentsList, {
      cwd: repositoryRoot,
      encoding: "utf8",
      env: { ...process.env, GITHUB_OUTPUT: outputFile },
    });
    return {
      ...result,
      outputs:
        result.status === 0
          ? Object.fromEntries(
              readFileSync(outputFile, "utf8")
                .trim()
                .split(/\r?\n/)
                .map((line) => line.split(/=(.*)/s).slice(0, 2)),
            )
          : {},
    };
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test("derives a monotonic, nonpublic draft identity for an RC", () => {
  const result = runMetadata("v1.1.0-rc.4");

  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.outputs, {
    tag: "v1.1.0-rc.4",
    version_name: "1.1.0-rc.4",
    version_code: "1010004",
    release_kind: "candidate",
    draft: "true",
    prerelease: "true",
    application_id: "com.darkaxt.dualdex",
    certificate_sha256: "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
  });
});

test("derives a monotonic RC hotfix identity without replacing the original candidate", () => {
  const result = runMetadata(
    "v1.1.0-rc.4-hotfix.1",
    undefined,
    ["v1.1.0-rc.4"],
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.tag, "v1.1.0-rc.4-hotfix.1");
  assert.equal(result.outputs.version_name, "1.1.0-rc.4-hotfix.1");
  assert.equal(result.outputs.version_code, "1010005");
  assert.equal(result.outputs.release_kind, "candidate");
  assert.equal(result.outputs.prerelease, "true");
});

test("reserves the highest qualifier for the final release", () => {
  const candidate = runMetadata("v1.1.0-rc.98");
  const invalidCandidate = runMetadata("v1.1.0-rc.99");

  assert.equal(candidate.status, 0, candidate.stderr);
  assert.equal(candidate.outputs.version_code, "1010098");
  assert.notEqual(invalidCandidate.status, 0);
  assert.match(invalidCandidate.stderr, /RC number must be between 1 and 98/);
});

test("rejects a candidate whose versionCode is not newer than an existing release tag", () => {
  const olderCandidate = runMetadata(
    "v1.1.0-rc.2",
    undefined,
    ["v1.1.0-rc.1", "v1.1.0-rc.3", "unrelated-tag"],
  );
  const newerCandidate = runMetadata(
    "v1.1.0-rc.4",
    undefined,
    ["v1.1.0-rc.1", "v1.1.0-rc.3", "unrelated-tag"],
  );

  assert.notEqual(olderCandidate.status, 0);
  assert.match(olderCandidate.stderr, /not monotonic/i);
  assert.equal(newerCandidate.status, 0, newerCandidate.stderr);
  assert.equal(newerCandidate.outputs.version_code, "1010004");
});

test("ignores semantic tags from a different application version lineage", () => {
  const result = runMetadata(
    "v1.1.0-rc.1",
    undefined,
    ["v2.0.1", "v1.0.0", "v0.9.9"],
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.version_code, "1010001");
});

test("refuses a final release without signed-candidate device authorization", () => {
  const result = runMetadata("v1.1.0");

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /final authorization/i);
});

test("accepts a final release only after the GitHub candidate passed both devices", () => {
  const result = runMetadata("v1.1.0", {
    schema: 1,
    versionName: "1.1.0",
    sourceCandidateTag: "v1.1.0-rc.1",
    githubSignedCandidateSha256: "A".repeat(64),
    validatedSignerSha256:
      "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    avdValidated: true,
    thorValidated: true,
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.version_code, "1010099");
  assert.equal(result.outputs.release_kind, "final");
  assert.equal(result.outputs.draft, "false");
  assert.equal(result.outputs.prerelease, "false");
});

test("accepts user-authorized automated promotion for a passive catalog-only change", () => {
  const result = runMetadata("v1.1.0", {
    schema: 1,
    versionName: "1.1.0",
    sourceCandidateTag: "v1.1.0-rc.1",
    githubSignedCandidateSha256: "B".repeat(64),
    validatedSignerSha256:
      "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    validationMode: "automated-passive-catalog",
    userAuthorizedAutomatedPromotion: true,
    gameplayRuntimeChanged: false,
    exactRomControls: 5,
    catalogPersistenceValidated: true,
    runtimeApiValidated: true,
    webPresentationValidated: true,
    releaseCiValidated: true,
    avdValidated: false,
    thorValidated: false,
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.version_code, "1010099");
  assert.equal(result.outputs.release_kind, "final");
  assert.equal(result.outputs.prerelease, "false");
});

test("rejects incomplete automated promotion evidence", () => {
  const result = runMetadata("v1.1.0", {
    schema: 1,
    versionName: "1.1.0",
    sourceCandidateTag: "v1.1.0-rc.1",
    githubSignedCandidateSha256: "B".repeat(64),
    validatedSignerSha256:
      "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    validationMode: "automated-passive-catalog",
    userAuthorizedAutomatedPromotion: true,
    gameplayRuntimeChanged: false,
    exactRomControls: 5,
    catalogPersistenceValidated: true,
    runtimeApiValidated: true,
    webPresentationValidated: true,
    releaseCiValidated: false,
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /complete automated passive-catalog validation/i);
});

function finalFixture(overrides = {}) {
  const evidenceSourceCommit = "9".repeat(40);
  const candidateCommit = "1".repeat(40);
  const candidateTree = "2".repeat(40);
  const provenanceSha256 = "3".repeat(64);
  const certificate = "A".repeat(64);
  return {
    tag: "v1.1.0",
    ready: {
      schema: 1,
      stage: 8,
      status: "ready-for-github-signing",
      openV1LedgerItems: 0,
      applicationId: "com.darkaxt.dualdex",
      versionName: "1.1.0",
      productionCertificateSha256: certificate,
      qaClosure: {
        schemaVersion: 1,
        evidenceSourceCommit,
        stage7Closed: true,
        stage8Closed: true,
        openBlockers: 0,
        openReferrals: 0,
      },
    },
    certificateFingerprint: certificate,
    releaseEvidenceValidation: {
      schemaVersion: 2,
      evidenceSourceCommit,
      inputCount: 333,
      stage7Closed: true,
      stage8Closed: true,
    },
    finalAuthorization: {
      schema: 2,
      versionName: "1.1.0",
      sourceCandidateTag: "v1.1.0-rc.1",
      sourceCandidateCommit: candidateCommit,
      sourceCandidateTree: candidateTree,
      candidateProvenanceSha256: provenanceSha256,
      githubSignedCandidateSha256: "B".repeat(64),
      validatedSignerSha256: certificate,
      avdValidated: true,
      thorValidated: true,
    },
    candidatePromotion: {
      schema: 1,
      candidateTag: "v1.1.0-rc.1",
      sourceCommit: candidateCommit,
      candidateProvenanceSha256: provenanceSha256,
      apkSha256: "B".repeat(64),
    },
    repositoryState: {
      sourceCandidateCommit: candidateCommit,
      sourceCandidateTree: candidateTree,
      candidateProvenanceSha256: provenanceSha256,
      candidateApkSha256: "B".repeat(64),
      changedPaths: [
        "release/v1-final-authorization.json",
        "release/v1-ready.json",
        "release/RELEASE_NOTES_1.1.0.md",
      ],
    },
    existingTags: [],
    ...overrides,
  };
}

test("binds a stable release to the verified candidate source and provenance", () => {
  const result = deriveReleaseMetadata(finalFixture());

  assert.equal(result.release_kind, "final");
  assert.equal(result.version_code, "1010099");
});

test("rejects stable authorization for a different candidate source or provenance", () => {
  const wrongCommit = finalFixture();
  wrongCommit.finalAuthorization.sourceCandidateCommit = "4".repeat(40);
  assert.throws(() => deriveReleaseMetadata(wrongCommit), /candidate source commit/i);

  const wrongProvenance = finalFixture();
  wrongProvenance.candidatePromotion.candidateProvenanceSha256 = "4".repeat(64);
  assert.throws(() => deriveReleaseMetadata(wrongProvenance), /candidate provenance/i);

  const replacedCandidateProvenance = finalFixture();
  replacedCandidateProvenance.repositoryState.candidateProvenanceSha256 = "5".repeat(64);
  assert.throws(() => deriveReleaseMetadata(replacedCandidateProvenance), /candidate provenance/i);

  const replacedCandidateApk = finalFixture();
  replacedCandidateApk.repositoryState.candidateApkSha256 = "6".repeat(64);
  assert.throws(() => deriveReleaseMetadata(replacedCandidateApk), /candidate APK/i);
});

test("rejects any stable product-tree change outside enumerated release metadata", () => {
  const changedProduct = finalFixture();
  changedProduct.repositoryState.changedPaths.push("app/src/main/java/Product.kt");

  assert.throws(() => deriveReleaseMetadata(changedProduct), /product source differs/i);
});

test("readiness requires matching machine-validated Stage 7 and Stage 8 zero-gap closure", () => {
  const missingValidation = finalFixture({ releaseEvidenceValidation: undefined });
  assert.throws(() => deriveReleaseMetadata(missingValidation), /release evidence validation/i);

  const openStage = finalFixture();
  openStage.releaseEvidenceValidation.stage8Closed = false;
  assert.throws(() => deriveReleaseMetadata(openStage), /Stage 7 and Stage 8 closure/i);
});
