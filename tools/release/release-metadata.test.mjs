import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(testDirectory, "../..");
const script = join(testDirectory, "derive-release-metadata.mjs");
const readyFile = join(repositoryRoot, "release", "v1-ready.json");
const fingerprintFile = join(repositoryRoot, "signing", "dualdex-release-cert.sha256");

function createTemporaryDirectory() {
  return mkdtempSync(join(process.env.RUNNER_TEMP || tmpdir(), "dualdex-release-test-"));
}

function runMetadata(tag, finalAuthorization, existingTags = []) {
  const directory = createTemporaryDirectory();
  try {
    const outputFile = join(directory, "github-output.txt");
    const argumentsList = [
      script,
      "--tag",
      tag,
      "--ready",
      readyFile,
      "--certificate-fingerprint",
      fingerprintFile,
    ];
    if (finalAuthorization) {
      const finalAuthorizationFile = join(directory, "final-authorization.json");
      writeFileSync(finalAuthorizationFile, JSON.stringify(finalAuthorization));
      argumentsList.push("--final-authorization", finalAuthorizationFile);
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

test("derives a monotonic, public prerelease identity for an RC", () => {
  const result = runMetadata("v1.0.0-rc.1");

  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.outputs, {
    tag: "v1.0.0-rc.1",
    version_name: "1.0.0-rc.1",
    version_code: "1000001",
    release_kind: "candidate",
    draft: "false",
    prerelease: "true",
    application_id: "com.darkaxt.dualdex",
    certificate_sha256: "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
  });
});

test("reserves the highest qualifier for the final release", () => {
  const candidate = runMetadata("v1.0.0-rc.98");
  const invalidCandidate = runMetadata("v1.0.0-rc.99");

  assert.equal(candidate.status, 0, candidate.stderr);
  assert.equal(candidate.outputs.version_code, "1000098");
  assert.notEqual(invalidCandidate.status, 0);
  assert.match(invalidCandidate.stderr, /RC number must be between 1 and 98/);
});

test("rejects a candidate whose versionCode is not newer than an existing release tag", () => {
  const olderCandidate = runMetadata(
    "v1.0.0-rc.2",
    undefined,
    ["v1.0.0-rc.1", "v1.0.0-rc.3", "unrelated-tag"],
  );
  const newerCandidate = runMetadata(
    "v1.0.0-rc.4",
    undefined,
    ["v1.0.0-rc.1", "v1.0.0-rc.3", "unrelated-tag"],
  );

  assert.notEqual(olderCandidate.status, 0);
  assert.match(olderCandidate.stderr, /not monotonic/i);
  assert.equal(newerCandidate.status, 0, newerCandidate.stderr);
  assert.equal(newerCandidate.outputs.version_code, "1000004");
});

test("ignores semantic tags from a different application version lineage", () => {
  const result = runMetadata(
    "v1.0.0-rc.1",
    undefined,
    ["v2.0.1", "v1.1.0", "v0.9.9"],
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.version_code, "1000001");
});

test("refuses a final release without signed-candidate device authorization", () => {
  const result = runMetadata("v1.0.0");

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /final authorization/i);
});

test("accepts a final release only after the GitHub candidate passed both devices", () => {
  const result = runMetadata("v1.0.0", {
    schema: 1,
    versionName: "1.0.0",
    sourceCandidateTag: "v1.0.0-rc.1",
    githubSignedCandidateSha256: "A".repeat(64),
    validatedSignerSha256:
      "C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA",
    avdValidated: true,
    thorValidated: true,
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.version_code, "1000099");
  assert.equal(result.outputs.release_kind, "final");
  assert.equal(result.outputs.draft, "false");
  assert.equal(result.outputs.prerelease, "false");
});
