import assert from "node:assert/strict";
import test from "node:test";
import { sha256, validateReleaseEvidence } from "./validate-release-evidence.mjs";

const sourceCommit = "a".repeat(40);
const releaseCommit = "b".repeat(40);

function fixture(overrides = {}) {
  const summary = Buffer.from(JSON.stringify({
    schemaVersion: 1,
    sourceCommit,
    generatorSchemaVersion: 12,
    corpusInputDigestSha256: "c".repeat(64),
    inputCount: 334,
    outcomes: { selected: 330, ambiguous: 2, noFamilyMatch: 2, errors: 0 },
    catalogs: { materialized: 330, persisted: 330, persistenceErrors: 0 },
  }));
  const manifest = {
    schemaVersion: 1,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 12 },
    corpus: { inputDigestSha256: "c".repeat(64), inputCount: 334 },
    scopeDecision: {
      type: "FRESH_EVIDENCE",
      attestation: "Fresh corpus evidence was generated from this source commit.",
    },
    artifacts: [{
      role: "CORPUS_SUMMARY",
      path: "docs/reports/qa-hardening/stage-07-corpus-evidence.json",
      sha256: sha256(summary),
    }],
    ...overrides,
  };
  return { manifest, summary };
}

function validate({ manifest, summary }, changedPaths = []) {
  return validateReleaseEvidence({
    manifest,
    releaseCommit,
    changedPaths,
    readArtifact: path => path === manifest.artifacts[0].path ? summary : null,
  });
}

test("accepts fresh evidence followed only by evidence packaging", () => {
  const result = validate(fixture(), [
    "docs/reports/qa-hardening/stage-07-corpus-evidence.json",
    "docs/reports/qa-hardening/stage-07-corpus-evidence.md",
    "release/compatibility-evidence.json",
  ]);

  assert.equal(result.scopeDecision, "FRESH_EVIDENCE");
  assert.equal(result.inputCount, 334);
  assert.equal(result.artifactCount, 1);
});

test("requires an explicit nonparser decision for product changes after fresh evidence", () => {
  assert.throws(
    () => validate(fixture(), ["app/src/main/Setup.kt"]),
    /FRESH_EVIDENCE cannot cover post-evidence product changes/,
  );
});

test("accepts reuse only with an explicit nonparser scope attestation", () => {
  const evidence = fixture({
    scopeDecision: {
      type: "NONPARSER_REUSE",
      attestation: "Only Android setup wording changed; parser and catalog output are invariant.",
    },
  });

  assert.equal(validate(evidence, ["app/src/main/Setup.kt"]).scopeDecision, "NONPARSER_REUSE");
});

test("rejects parser or catalog changes after the evidence source", () => {
  assert.throws(
    () => validate(fixture(), ["parser-core/src/main/kotlin/Parser.kt"]),
    /parser\/catalog-affecting paths changed/,
  );
  assert.throws(
    () => validate(fixture(), ["catalog-store/src/main/kotlin/CatalogSchema.kt"]),
    /parser\/catalog-affecting paths changed/,
  );
});

test("rejects an artifact whose bytes do not match the manifest", () => {
  const evidence = fixture();
  evidence.summary = Buffer.from("{}");

  assert.throws(() => validate(evidence), /artifact digest mismatch/);
});

test("rejects corpus parser or persistence failures", () => {
  const parserFailure = fixture();
  const parserSummary = JSON.parse(parserFailure.summary.toString("utf8"));
  parserSummary.outcomes.errors = 1;
  parserFailure.summary = Buffer.from(JSON.stringify(parserSummary));
  parserFailure.manifest.artifacts[0].sha256 = sha256(parserFailure.summary);
  assert.throws(() => validate(parserFailure), /parser errors/);

  const incompletePersistence = fixture();
  const persistenceSummary = JSON.parse(incompletePersistence.summary.toString("utf8"));
  persistenceSummary.catalogs.persisted -= 1;
  incompletePersistence.summary = Buffer.from(JSON.stringify(persistenceSummary));
  incompletePersistence.manifest.artifacts[0].sha256 = sha256(incompletePersistence.summary);
  assert.throws(() => validate(incompletePersistence), /not every materialized catalog/);
});

test("rejects missing or inconsistent corpus binding fields", () => {
  const missingAttestation = fixture({
    scopeDecision: { type: "NONPARSER_REUSE", attestation: "too short" },
  });
  assert.throws(() => validate(missingAttestation), /meaningful attestation/);

  const wrongDigest = fixture();
  wrongDigest.manifest.corpus.inputDigestSha256 = "d".repeat(64);
  assert.throws(() => validate(wrongDigest), /input digest does not match/);
});
