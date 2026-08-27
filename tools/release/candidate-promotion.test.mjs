import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(testDirectory, "../..");
const script = join(testDirectory, "validate-candidate-promotion.mjs");
const certificateSha256 =
  "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA";

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex").toUpperCase();
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

function runValidation(validation, recordOverrides = {}, actualSigner = certificateSha256) {
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
    commit: "1".repeat(40),
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
  const record = {
    schema: 1,
    candidateTag: tag,
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
    certificate: join(directory, "certificate.sha256"),
    signerVerification: join(directory, "apksigner-verification.txt"),
  };

  try {
    writeFileSync(paths.apk, apkBytes);
    writeFileSync(paths.provenance, JSON.stringify(provenance));
    writeFileSync(paths.record, JSON.stringify(record));
    writeFileSync(paths.checksums, `${apkSha256.toLowerCase()}  ${apkName}\n`);
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
