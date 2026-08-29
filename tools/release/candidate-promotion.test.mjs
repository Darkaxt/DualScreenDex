import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { validateReleaseAssetSet } from "./validate-candidate-promotion.mjs";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(testDirectory, "../..");
const script = join(testDirectory, "validate-candidate-promotion.mjs");
const certificateSha256 =
  "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA";

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex").toUpperCase();
}

function jsonBytes(value) {
  return Buffer.from(JSON.stringify(value));
}

function publishedEvidenceFiles() {
  const sourceCommit = "1".repeat(40);
  const releaseCommit = "2".repeat(40);
  const generatorSha256 = "3".repeat(64);
  const corpusDigest = "4".repeat(64);
  const rawReportSha256 = "5".repeat(64);
  const summary = jsonBytes({
    schemaVersion: 2,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorSha256 },
    rawReportSha256,
    corpusInputDigestSha256: corpusDigest,
    inputCount: 333,
    uniqueRomIdentities: 333,
    outcomes: { selected: 329, ambiguous: 2, noFamilyMatch: 2, total: 333, errors: 0 },
    dataCompatibility: { complete: 299, partial: 30, unresolved: 4, total: 333, errors: 0 },
    catalogs: { materialized: 329, persisted: 329, catalogErrors: 0, persistenceErrors: 0 },
    privacy: {
      containsRomIdentity: false,
      containsRomName: false,
      containsSourcePath: false,
      containsRomBytes: false,
    },
  });
  const receipt = jsonBytes({
    schemaVersion: 1,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorSha256 },
    rawReportSha256,
    inputCount: 333,
  });
  const stage7 = jsonBytes({
    schemaVersion: 1,
    stage: 7,
    status: "CLOSED",
    sourceCommit,
    openBlockers: 0,
    openReferrals: 0,
  });
  const stage8 = jsonBytes({
    schemaVersion: 1,
    stage: 8,
    status: "CLOSED",
    sourceCommit,
    openBlockers: 0,
    openReferrals: 0,
  });
  const artifacts = [
    ["CORPUS_SUMMARY", "docs/reports/qa-hardening/stage-07-corpus-evidence.json", summary],
    ["CORPUS_EXECUTION_RECEIPT", "docs/reports/qa-hardening/stage-07-corpus-execution.json", receipt],
    ["STAGE_7_CLOSURE", "docs/reports/qa-hardening/stage-07-closure.json", stage7],
    ["STAGE_8_CLOSURE", "docs/reports/qa-hardening/stage-08-closure.json", stage8],
  ].map(([role, path, bytes]) => ({ role, path, sha256: sha256(bytes).toLowerCase() }));
  const manifest = jsonBytes({
    schemaVersion: 2,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorSha256 },
    corpus: { inputDigestSha256: corpusDigest, inputCount: 333 },
    scopeDecision: {
      type: "NONPARSER_REUSE",
      attestation: "Only release metadata changed after this source-bound evidence was generated.",
    },
    artifacts,
  });
  const canonical = jsonBytes({
    schemaVersion: 2,
    inputCount: 333,
    uniqueRomIdentityCount: 333,
    inputDigestSha256: corpusDigest,
  });
  const validation = jsonBytes({
    schemaVersion: 2,
    releaseCommit,
    evidenceSourceCommit: sourceCommit,
    scopeDecision: "NONPARSER_REUSE",
    cacheDecision: "NOT_APPLICABLE",
    generatorSchemaVersion: 13,
    generatorSha256,
    corpusInputDigestSha256: corpusDigest,
    inputCount: 333,
    artifactCount: 4,
    stage7Closed: true,
    stage8Closed: true,
  });
  return new Map([
    ["compatibility-evidence.json", manifest],
    ["canonical-corpus.json", canonical],
    ["release-evidence-validation.json", validation],
    ["dualdex-stage-07-corpus-evidence.json", summary],
    ["dualdex-stage-07-corpus-execution.json", receipt],
    ["dualdex-stage-07-closure.json", stage7],
    ["dualdex-stage-08-closure.json", stage8],
  ]);
}

function packagedValidation(overrides = {}) {
  return {
    validationMode: "packaged-android-and-thor",
    releaseCiValidated: true,
    packagedAndroidValidated: true,
    packagedAndroidWorkflowRunUrl:
      "https://github.com/Darkaxt/DualDex/actions/runs/456",
    packagedAndroidEvidenceArtifactDigest: `sha256:${"D".repeat(64)}`,
    thorValidated: true,
    ...overrides,
  };
}

function automatedValidation(overrides = {}) {
  return {
    validationMode: "automated-passive-catalog",
    releaseCiValidated: true,
    userAuthorizedAutomatedPromotion: true,
    gameplayRuntimeChanged: false,
    exactRomControls: 5,
    catalogPersistenceValidated: true,
    runtimeApiValidated: true,
    webPresentationValidated: true,
    automatedValidationWorkflowRunUrl:
      "https://github.com/Darkaxt/DualDex/actions/runs/789",
    automatedEvidencePath:
      "docs/reports/candidate-promotions/v1.1.0-rc.73.json",
    automatedEvidenceSha256: "E".repeat(64),
    ...overrides,
  };
}

function runValidation(
  validation,
  recordOverrides = {},
  actualSigner = certificateSha256,
  mutatePublicFiles = () => {},
) {
  const directory = mkdtempSync(
    join(process.env.RUNNER_TEMP || tmpdir(), "dualdex-promotion-test-"),
  );
  const tag = "v1.1.0-rc.73";
  const apkName = `DualDex-${tag}.apk`;
  const apkBytes = Buffer.from("already-signed-apk-fixture");
  const apkSha256 = sha256(apkBytes);
  const provenance = {
    schema: 1,
    repository: "Darkaxt/DualDex",
    commit: "2".repeat(40),
    workflowRunId: "123",
    tag,
    releaseKind: "candidate",
    versionName: "1.1.0-rc.73",
    versionCode: 1010073,
    applicationId: "com.darkaxt.dualdex",
    apkSha256,
    certificateSha256,
    signingAuthority: "GitHub protected environment: release-signing",
  };
  const provenanceBytes = Buffer.from(JSON.stringify(provenance));
  const checksumBytes = Buffer.from(`${apkSha256.toLowerCase()}  ${apkName}\n`);
  const publicFiles = new Map([
    [apkName, apkBytes],
    ["provenance.json", provenanceBytes],
    ["SHA256SUMS.txt", checksumBytes],
    ["repository-policy.json", Buffer.from("repository-policy")],
    ...publishedEvidenceFiles(),
  ]);
  mutatePublicFiles(publicFiles);
  const releaseAssets = [...publicFiles].map(([name, bytes], index) => ({
    name,
    id: 100 + index,
    sha256: sha256(bytes),
  }));
  const record = {
    schema: 1,
    candidateTag: tag,
    sourceCommit: provenance.commit,
    candidateProvenanceSha256: sha256(provenanceBytes),
    releaseAssets,
    apkSha256,
    validatedSignerSha256: certificateSha256,
    releaseWorkflowRunUrl:
      "https://github.com/Darkaxt/DualDex/actions/runs/123",
    ...validation,
    ...(validation.validationMode === "packaged-android-and-thor"
      ? {
          thorValidationRecord: {
            status: "PASSED",
            candidateTag: tag,
            apkSha256,
            validatedSignerSha256: certificateSha256,
            checklistVersion: 1,
          },
        }
      : {}),
    ...recordOverrides,
  };
  const paths = {
    apk: join(directory, apkName),
    provenance: join(directory, "provenance.json"),
    record: join(directory, "record.json"),
    checksums: join(directory, "SHA256SUMS.txt"),
    releaseAssets: join(directory, "release-assets.json"),
    certificate: join(directory, "certificate.sha256"),
    signerVerification: join(directory, "apksigner-verification.txt"),
  };

  try {
    for (const [name, bytes] of publicFiles) writeFileSync(join(directory, name), bytes);
    writeFileSync(paths.record, JSON.stringify(record));
    writeFileSync(paths.releaseAssets, JSON.stringify(releaseAssets));
    writeFileSync(paths.certificate, `${certificateSha256}\n`);
    writeFileSync(
      paths.signerVerification,
      `Signer #1 certificate SHA-256 digest: ${actualSigner.toLowerCase()}\n`,
    );

    return spawnSync(
      process.execPath,
      [
        script,
        "--record",
        paths.record,
        "--provenance",
        paths.provenance,
        "--checksums",
        paths.checksums,
        "--apk",
        paths.apk,
        "--certificate-fingerprint",
        paths.certificate,
        "--apk-signer-verification",
        paths.signerVerification,
        "--release-assets",
        paths.releaseAssets,
        "--assets-directory",
        directory,
      ],
      { cwd: repositoryRoot, encoding: "utf8" },
    );
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test("accepts exact signed candidate evidence after packaged Android and Thor validation", () => {
  const result = runValidation(packagedValidation());

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /v1\.1\.0-rc\.73/);
  assert.match(result.stdout, /packaged-android-and-thor/);
});

test("accepts the authorized passive-catalog substitution without claiming device validation", () => {
  const result = runValidation(automatedValidation());

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /automated-passive-catalog/);
});

test("rejects a promotion record for a different signed APK", () => {
  const result = runValidation(packagedValidation(), { apkSha256: "A".repeat(64) });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /APK hash/i);
});

test("rejects an APK whose cryptographically verified signer differs", () => {
  const result = runValidation(
    packagedValidation(),
    {},
    "B".repeat(64),
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /APK signer/i);
});

test("rejects release evidence validation for a different candidate commit", () => {
  const result = runValidation(
    packagedValidation(),
    {},
    certificateSha256,
    files => {
      const validation = JSON.parse(files.get("release-evidence-validation.json"));
      validation.releaseCommit = "6".repeat(40);
      files.set("release-evidence-validation.json", jsonBytes(validation));
    },
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /release evidence validation.*candidate commit/i);
});

test("rejects physical validation evidence for a different artifact", () => {
  const result = runValidation(packagedValidation(), {
    thorValidationRecord: {
      status: "PASSED",
      candidateTag: "v1.1.0-rc.72",
      apkSha256: "A".repeat(64),
      validatedSignerSha256: certificateSha256,
      checklistVersion: 1,
    },
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Thor validation/i);
});

test("rejects packaged validation without immutable artifact evidence", () => {
  const result = runValidation(
    packagedValidation({ packagedAndroidEvidenceArtifactDigest: "" }),
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /packaged Android/i);
});

test("rejects incomplete packaged Android validation evidence", () => {
  const result = runValidation(
    packagedValidation({ packagedAndroidWorkflowRunUrl: "" }),
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /packaged Android/i);
});

test("rejects automated substitution without immutable workflow evidence", () => {
  const result = runValidation(
    automatedValidation({ automatedValidationWorkflowRunUrl: "" }),
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /passive-catalog/i);
});

test("rejects incomplete automated substitution evidence", () => {
  const result = runValidation(
    automatedValidation({ catalogPersistenceValidated: false }),
  );

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /passive-catalog/i);
});

function immutableAssetFixture() {
  const files = new Map([
    ["DualDex-v1.1.0-rc.73.apk", Buffer.from("apk")],
    ["provenance.json", Buffer.from("provenance")],
    ["SHA256SUMS.txt", Buffer.from("checksums")],
    ["repository-policy.json", Buffer.from("policy")],
    ...publishedEvidenceFiles(),
  ]);
  const releaseAssets = [...files].map(([name, bytes], index) => ({
    name,
    id: index + 100,
    sha256: sha256(bytes),
  }));
  return {
    files,
    releaseAssets,
    recordAssets: structuredClone(releaseAssets),
  };
}

function replaceImmutableAsset(fixture, name, value) {
  const bytes = jsonBytes(value);
  fixture.files.set(name, bytes);
  for (const assets of [fixture.releaseAssets, fixture.recordAssets]) {
    assets.find(asset => asset.name === name).sha256 = sha256(bytes);
  }
}

function replacePublishedArtifact(fixture, name, role, value) {
  const bytes = jsonBytes(value);
  replaceImmutableAsset(fixture, name, value);
  const manifest = JSON.parse(fixture.files.get("compatibility-evidence.json"));
  manifest.artifacts.find(artifact => artifact.role === role).sha256 = sha256(bytes).toLowerCase();
  replaceImmutableAsset(fixture, "compatibility-evidence.json", manifest);
}

function removeImmutableAsset(fixture, name) {
  fixture.files.delete(name);
  fixture.releaseAssets = fixture.releaseAssets.filter(asset => asset.name !== name);
  fixture.recordAssets = fixture.recordAssets.filter(asset => asset.name !== name);
}

test("accepts the exact immutable public release asset set", () => {
  const fixture = immutableAssetFixture();

  const result = validateReleaseAssetSet({
    recordAssets: fixture.recordAssets,
    releaseAssets: fixture.releaseAssets,
    readAsset: name => fixture.files.get(name) ?? null,
    candidateTag: "v1.1.0-rc.73",
  });

  assert.equal(result.assetCount, fixture.releaseAssets.length);
});

test("rejects legacy or incomplete candidate evidence assets", () => {
  const legacy = immutableAssetFixture();
  const legacyManifest = JSON.parse(legacy.files.get("compatibility-evidence.json"));
  legacyManifest.schemaVersion = 1;
  replaceImmutableAsset(legacy, "compatibility-evidence.json", legacyManifest);
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: legacy.recordAssets,
      releaseAssets: legacy.releaseAssets,
      readAsset: name => legacy.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /schemaVersion must be 2|schema-2/i,
  );

  const missingReceipt = immutableAssetFixture();
  removeImmutableAsset(missingReceipt, "dualdex-stage-07-corpus-execution.json");
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: missingReceipt.recordAssets,
      releaseAssets: missingReceipt.releaseAssets,
      readAsset: name => missingReceipt.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /execution receipt/i,
  );

  const openClosure = immutableAssetFixture();
  const closure = JSON.parse(openClosure.files.get("dualdex-stage-08-closure.json"));
  closure.openBlockers = 1;
  replaceImmutableAsset(openClosure, "dualdex-stage-08-closure.json", closure);
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: openClosure.recordAssets,
      releaseAssets: openClosure.releaseAssets,
      readAsset: name => openClosure.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /Stage 8.*zero blockers|zero-gap/i,
  );
});

test("rejects malformed published execution and validation lineage", () => {
  const staleReceipt = immutableAssetFixture();
  const receipt = JSON.parse(staleReceipt.files.get("dualdex-stage-07-corpus-execution.json"));
  receipt.schemaVersion = 0;
  replacePublishedArtifact(
    staleReceipt,
    "dualdex-stage-07-corpus-execution.json",
    "CORPUS_EXECUTION_RECEIPT",
    receipt,
  );
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: staleReceipt.recordAssets,
      releaseAssets: staleReceipt.releaseAssets,
      readAsset: name => staleReceipt.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /execution receipt.*schemaVersion|schemaVersion.*execution receipt/i,
  );

  const mismatchedValidation = immutableAssetFixture();
  const validation = JSON.parse(mismatchedValidation.files.get("release-evidence-validation.json"));
  validation.generatorSha256 = "9".repeat(64);
  replaceImmutableAsset(mismatchedValidation, "release-evidence-validation.json", validation);
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: mismatchedValidation.recordAssets,
      releaseAssets: mismatchedValidation.releaseAssets,
      readAsset: name => mismatchedValidation.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /validation.*generator|generator.*validation/i,
  );

  const parserErrors = immutableAssetFixture();
  const summary = JSON.parse(parserErrors.files.get("dualdex-stage-07-corpus-evidence.json"));
  summary.outcomes = { selected: 328, ambiguous: 2, noFamilyMatch: 2, total: 333, errors: 1 };
  replacePublishedArtifact(
    parserErrors,
    "dualdex-stage-07-corpus-evidence.json",
    "CORPUS_SUMMARY",
    summary,
  );
  assert.throws(
    () => validateReleaseAssetSet({
      recordAssets: parserErrors.recordAssets,
      releaseAssets: parserErrors.releaseAssets,
      readAsset: name => parserErrors.files.get(name) ?? null,
      candidateTag: "v1.1.0-rc.73",
    }),
    /parser errors|terminal outcomes/i,
  );
});

test("rejects replaced, deleted, added, or reuploaded public assets", () => {
  for (const mutate of [
    fixture => fixture.releaseAssets[0].sha256 = "A".repeat(64),
    fixture => fixture.releaseAssets.pop(),
    fixture => fixture.releaseAssets.push({ name: "added.txt", id: 999, sha256: "A".repeat(64) }),
    fixture => fixture.releaseAssets[0].id += 1,
  ]) {
    const fixture = immutableAssetFixture();
    mutate(fixture);
    assert.throws(
      () => validateReleaseAssetSet({
        recordAssets: fixture.recordAssets,
        releaseAssets: fixture.releaseAssets,
        readAsset: name => fixture.files.get(name) ?? null,
        candidateTag: "v1.1.0-rc.73",
      }),
      /immutable release asset set/i,
    );
  }
});
