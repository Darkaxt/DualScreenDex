import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { validatePublicReleaseAsset } from "./validate-public-release-assets.mjs";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const staticPublicJson = new Map([
  ["dualdex-parser-compatibility.json", "reports/dualdex-parser-compatibility.json"],
  ["dualdex-rom-hacks-compatibility.json", "reports/dualdex-rom-hacks-compatibility.json"],
  ["dualdex-base-first50-release-gate.json", "docs/reports/2026-08-13-base-first50-release-gate.json"],
  ["dualdex-base-full332-compatibility-summary.json", "docs/reports/2026-08-13-base-full332-compatibility-summary.json"],
  ["dualdex-map-first50-release-gate.json", "docs/reports/2026-08-13-map-first50-release-gate-raw.json"],
  ["dualdex-evolution-first50-release-gate.json", "docs/reports/2026-08-14-first50-evolution-completeness-raw.json"],
  ["dualdex-gen1-gen3-table-coverage.json", "docs/reports/2026-08-20-gen1-gen3-table-coverage.json"],
  ["dualdex-unified-game-state-compatibility.json", "docs/reports/2026-08-25-unified-game-state-compatibility.json"],
  ["dualdex-party-analysis-compatibility.json", "docs/reports/passive-insights-progress/party-analysis-compatibility.json"],
  ["dualdex-area-guide-compatibility.json", "docs/reports/passive-insights-progress/area-guide-compatibility.json"],
  ["dualdex-progress-timeline-compatibility.json", "docs/reports/passive-insights-progress/progress-timeline-compatibility.json"],
  ["dualdex-specimens-compatibility.json", "docs/reports/passive-insights-progress/specimens-compatibility.json"],
  ["dualdex-damage-forecast-compatibility.json", "docs/reports/passive-insights-progress/damage-forecast-compatibility.json"],
  ["dualdex-challenge-expansion-compatibility.json", "docs/reports/passive-insights-progress/challenge-expansion-compatibility.json"],
  ["dualdex-ui-conformance-route-matrix.json", "docs/reports/passive-insights-progress/ui-conformance-route-matrix.json"],
  ["dualdex-ui-conformance-font-matrix.json", "docs/reports/passive-insights-progress/ui-conformance-font-matrix.json"],
  ["dualdex-ui-conformance-computed-styles.json", "docs/reports/passive-insights-progress/ui-conformance-computed-styles.json"],
  ["dualdex-ui-conformance-screenshots.json", "docs/reports/passive-insights-progress/ui-conformance-screenshots.json"],
  ["dualdex-ui-conformance-summary.json", "docs/reports/passive-insights-progress/ui-conformance-summary.json"],
]);

const validSummary = {
  schemaVersion: 2,
  sourceCommit: "a".repeat(40),
  generator: { name: "parser-cli", schemaVersion: 13, sha256: "b".repeat(64) },
  rawReportSha256: "c".repeat(64),
  corpusInputDigestSha256: "d".repeat(64),
  inputCount: 334,
  uniqueRomIdentities: 334,
  outcomes: { selected: 334, ambiguous: 0, noFamilyMatch: 0, total: 334, errors: 0 },
  dataCompatibility: { complete: 334, partial: 0, unresolved: 0, total: 334, errors: 0 },
  catalogs: { materialized: 334, persisted: 334, catalogErrors: 0, persistenceErrors: 0 },
  privacy: {
    containsRomIdentity: false,
    containsRomName: false,
    containsSourcePath: false,
    containsRomBytes: false,
  },
};

test("accepts a structurally known privacy-safe Stage 7 summary", () => {
  validatePublicReleaseAsset({
    name: "dualdex-stage-07-corpus-evidence.json",
    bytes: Buffer.from(JSON.stringify(validSummary)),
  });
});

test("rejects Windows backslash and forward-slash absolute paths and Unix home paths", () => {
  for (const privateText of [
    "workspace=C:\\Users\\local-user\\project",
    "workspace=D:/Users/local-user/project",
    "workspace=E:\\workspace\\project",
    "workspace=F:/workspace/project",
    "workspace=/home/local-user/project",
    "workspace=/Users/local-user/project",
    "database=/data/user/0/example/private.db",
  ]) {
    assert.throws(
      () => validatePublicReleaseAsset({ name: "public-evidence.txt", bytes: Buffer.from(privateText) }),
      /private path/i,
      privateText,
    );
  }
});

test("rejects local device and workspace identifiers without returning their values", () => {
  for (const privateText of [
    "deploymentTarget=local-device",
    "serialNumber=local-serial",
    "deviceId=local-device",
    '{"deviceId":"local-device"}',
    "emulator-5554",
  ]) {
    assert.throws(
      () => validatePublicReleaseAsset({ name: "public-evidence.txt", bytes: Buffer.from(privateText) }),
      error => error instanceof Error && /local identifier/i.test(error.message) && !error.message.includes(privateText),
    );
  }
});

test("rejects unknown fields from every machine-readable Stage 7 or 8 asset", () => {
  const execution = {
    schemaVersion: 1,
    sourceCommit: "a".repeat(40),
    generator: { name: "parser-cli", schemaVersion: 13, sha256: "b".repeat(64) },
    rawReportSha256: "c".repeat(64),
    inputCount: 334,
    localWorkspace: "redacted",
  };
  const closure = {
    schemaVersion: 1,
    stage: 8,
    status: "CLOSED",
    sourceCommit: "a".repeat(40),
    openBlockers: 0,
    openReferrals: 0,
    reviewerIdentity: "redacted",
  };

  for (const [name, value] of [
    ["dualdex-stage-07-corpus-execution.json", execution],
    ["dualdex-stage-08-closure.json", closure],
  ]) {
    assert.throws(
      () => validatePublicReleaseAsset({ name, bytes: Buffer.from(JSON.stringify(value)) }),
      /unknown evidence field/i,
    );
  }
});


test("rejects unknown or explicitly private Stage 7 evidence fields", () => {
  const unknown = structuredClone(validSummary);
  unknown.localWorkspace = "redacted";
  assert.throws(
    () => validatePublicReleaseAsset({
      name: "dualdex-stage-07-corpus-evidence.json",
      bytes: Buffer.from(JSON.stringify(unknown)),
    }),
    /unknown evidence field/i,
  );

  const privateFlag = structuredClone(validSummary);
  privateFlag.privacy.containsSourcePath = true;
  assert.throws(
    () => validatePublicReleaseAsset({
      name: "dualdex-stage-07-corpus-evidence.json",
      bytes: Buffer.from(JSON.stringify(privateFlag)),
    }),
    /privacy declaration/i,
  );
});

function validPublicEvidence() {
  const sourceCommit = "a".repeat(40);
  const compatibilityEvidence = {
    schemaVersion: 2,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: "b".repeat(64) },
    corpus: { inputDigestSha256: "d".repeat(64), inputCount: 334 },
    scopeDecision: { type: "NONPARSER_REUSE", attestation: "Release-only changes reuse source-bound evidence." },
    artifacts: [{ role: "CORPUS_SUMMARY", path: "docs/summary.json", sha256: "e".repeat(64) }],
  };
  const canonicalCorpus = {
    schemaVersion: 1,
    inputCount: 334,
    inputDigestSha256: "d".repeat(64),
  };
  const releaseEvidenceValidation = {
    schemaVersion: 2,
    releaseCommit: "f".repeat(40),
    evidenceSourceCommit: sourceCommit,
    scopeDecision: "NONPARSER_REUSE",
    cacheDecision: "NOT_APPLICABLE",
    generatorSchemaVersion: 13,
    generatorSha256: "b".repeat(64),
    corpusInputDigestSha256: "d".repeat(64),
    inputCount: 334,
    artifactCount: 1,
    stage7Closed: true,
    stage8Closed: true,
  };
  const environment = {
    name: "release-signing",
    deploymentBranchPolicy: "v1.2.3-rc.1",
    requiredReviewerCount: 1,
    preventSelfReview: true,
    protectionRuleTypes: ["branch_policy", "required_reviewers"],
  };
  const repositoryPolicy = {
    schemaVersion: 2,
    repository: "Darkaxt/DualDex",
    tag: "v1.2.3-rc.1",
    defaultBranch: "master",
    tagRuleset: {
      id: 1,
      name: "immutable releases",
      enforcement: "active",
      requiredRuleTypes: ["deletion", "update"],
    },
    signingEnvironment: environment,
    promotionEnvironment: { ...environment, name: "release-promotion", signingSecretCount: 0 },
  };
  const provenance = {
    schema: 1,
    repository: "Darkaxt/DualDex",
    commit: "f".repeat(40),
    workflowRunId: "1",
    tag: "v1.2.3-rc.1",
    releaseKind: "candidate",
    versionName: "1.2.3-rc.1",
    versionCode: 1020301,
    applicationId: "com.darkaxt.dualdex",
    apkSha256: "1".repeat(64),
    certificateSha256: "2".repeat(64),
    signingAuthority: "GitHub protected environment: release-signing",
    compatibilityEvidence,
    releaseEvidenceValidation,
    repositoryPolicy,
  };
  return new Map([
    ["compatibility-evidence.json", compatibilityEvidence],
    ["canonical-corpus.json", canonicalCorpus],
    ["release-evidence-validation.json", releaseEvidenceValidation],
    ["repository-policy.json", repositoryPolicy],
    ["provenance.json", provenance],
  ]);
}

test("accepts every closed public release evidence schema", () => {
  for (const [name, value] of validPublicEvidence()) {
    validatePublicReleaseAsset({ name, bytes: Buffer.from(JSON.stringify(value)) });
  }
});


test("rejects unknown nested fields from every generated public release evidence schema", () => {
  for (const [name, value] of validPublicEvidence()) {
    const mutated = structuredClone(value);
    if (name === "compatibility-evidence.json") mutated.generator.localBuildRoot = "redacted";
    else if (name === "canonical-corpus.json") mutated.localCorpusLabel = "redacted";
    else if (name === "release-evidence-validation.json") mutated.localValidator = { label: "redacted" };
    else if (name === "repository-policy.json") mutated.signingEnvironment.reviewerIdentity = "redacted";
    else mutated.compatibilityEvidence.artifacts[0].localArtifactSource = "redacted";

    assert.throws(
      () => validatePublicReleaseAsset({ name, bytes: Buffer.from(JSON.stringify(mutated)) }),
      /unknown evidence field/i,
      name,
    );
  }
});

function firstNestedObject(value) {
  for (const child of Object.values(value)) {
    if (child && typeof child === "object" && !Array.isArray(child)) return child;
    if (Array.isArray(child)) {
      for (const entry of child) {
        if (entry && typeof entry === "object" && !Array.isArray(entry)) return entry;
      }
    }
  }
  throw new Error("Fixture has no nested object");
}

test("rejects unrecognized public JSON evidence assets", () => {
  assert.throws(
    () => validatePublicReleaseAsset({
      name: "unregistered-evidence.json",
      bytes: Buffer.from(JSON.stringify({ schemaVersion: 1, localIdentity: "redacted" })),
    }),
    /unrecognized public JSON evidence/i,
  );
});

test("rejects unknown nested fields from every static public JSON evidence type", () => {
  for (const [name, path] of staticPublicJson) {
    const bytes = readFileSync(resolve(repositoryRoot, path));
    validatePublicReleaseAsset({ name, bytes });

    const mutated = JSON.parse(bytes);
    firstNestedObject(mutated).unexpectedLocalEvidence = "redacted";
    assert.throws(
      () => validatePublicReleaseAsset({ name, bytes: Buffer.from(JSON.stringify(mutated)) }),
      /unknown evidence field/i,
      name,
    );
  }
});
