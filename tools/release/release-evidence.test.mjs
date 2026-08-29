import assert from "node:assert/strict";
import test from "node:test";
import { sha256, validateReleaseEvidence } from "./validate-release-evidence.mjs";

const sourceCommit = "a".repeat(40);
const releaseCommit = "b".repeat(40);
const corpusDigest = "c".repeat(64);
const generatorDigest = "d".repeat(64);
const rawReportDigest = "e".repeat(64);

function jsonBytes(value) {
  return Buffer.from(JSON.stringify(value));
}

function fixture(overrides = {}) {
  const summary = jsonBytes({
    schemaVersion: 2,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorDigest },
    rawReportSha256: rawReportDigest,
    corpusInputDigestSha256: corpusDigest,
    inputCount: 333,
    uniqueRomIdentities: 333,
    outcomes: { selected: 329, ambiguous: 2, noFamilyMatch: 2, total: 333, errors: 0 },
    dataCompatibility: { complete: 299, partial: 30, unresolved: 4, total: 333, errors: 0 },
    catalogs: {
      materialized: 329,
      persisted: 329,
      catalogErrors: 0,
      persistenceErrors: 0,
    },
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
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorDigest },
    rawReportSha256: rawReportDigest,
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
  const artifacts = new Map([
    ["docs/reports/qa-hardening/stage-07-corpus-evidence.json", summary],
    ["docs/reports/qa-hardening/stage-07-corpus-execution.json", receipt],
    ["docs/reports/qa-hardening/stage-07-closure.json", stage7],
    ["docs/reports/qa-hardening/stage-08-closure.json", stage8],
  ]);
  const manifest = {
    schemaVersion: 2,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorDigest },
    corpus: { inputDigestSha256: corpusDigest, inputCount: 333 },
    scopeDecision: {
      type: "FRESH_EVIDENCE",
      attestation: "Fresh corpus evidence was generated from this exact source commit.",
    },
    artifacts: [
      artifact("CORPUS_SUMMARY", "docs/reports/qa-hardening/stage-07-corpus-evidence.json", summary),
      artifact("CORPUS_EXECUTION_RECEIPT", "docs/reports/qa-hardening/stage-07-corpus-execution.json", receipt),
      artifact("STAGE_7_CLOSURE", "docs/reports/qa-hardening/stage-07-closure.json", stage7),
      artifact("STAGE_8_CLOSURE", "docs/reports/qa-hardening/stage-08-closure.json", stage8),
    ],
    ...overrides,
  };
  return {
    manifest,
    canonicalCorpus: {
      schemaVersion: 2,
      inputCount: 333,
      uniqueRomIdentityCount: 333,
      inputDigestSha256: corpusDigest,
    },
    catalogSchemaRevision: 45,
    priorCatalogSchemaRevision: 45,
    artifacts,
  };
}

function artifact(role, path, bytes) {
  return { role, path, sha256: sha256(bytes) };
}

function replaceArtifact(evidence, role, value) {
  const entry = evidence.manifest.artifacts.find(candidate => candidate.role === role);
  const bytes = jsonBytes(value);
  evidence.artifacts.set(entry.path, bytes);
  entry.sha256 = sha256(bytes);
}

function artifactValue(evidence, role) {
  const entry = evidence.manifest.artifacts.find(candidate => candidate.role === role);
  return JSON.parse(evidence.artifacts.get(entry.path).toString("utf8"));
}

function validate(evidence, changedPaths = [], decisionPaths = []) {
  return validateReleaseEvidence({
    manifest: evidence.manifest,
    canonicalCorpus: evidence.canonicalCorpus,
    catalogSchemaRevision: evidence.catalogSchemaRevision,
    priorCatalogSchemaRevision: evidence.priorCatalogSchemaRevision,
    releaseCommit,
    changedPaths,
    decisionPaths,
    readArtifact: path => evidence.artifacts.get(path) ?? null,
  });
}

test("accepts exactly complete source-bound fresh evidence and zero-gap closures", () => {
  const result = validate(fixture(), [
    "docs/reports/qa-hardening/stage-07-corpus-evidence.json",
    "docs/reports/qa-hardening/stage-07-corpus-evidence.md",
    "docs/reports/qa-hardening/stage-07-corpus-execution.json",
    "docs/reports/qa-hardening/stage-07-closure.json",
    "docs/reports/qa-hardening/stage-08-closure.json",
    "release/compatibility-evidence.json",
    "release/canonical-corpus.json",
  ]);

  assert.equal(result.scopeDecision, "FRESH_EVIDENCE");
  assert.equal(result.inputCount, 333);
  assert.equal(result.stage7Closed, true);
  assert.equal(result.stage8Closed, true);
});

test("rejects a noncanonical denominator, digest, or unique input set", () => {
  const wrongCount = fixture();
  wrongCount.canonicalCorpus.inputCount = 334;
  wrongCount.manifest.corpus.inputCount = 334;
  assert.throws(() => validate(wrongCount), /canonical corpus.*333/i);

  const wrongDigest = fixture();
  wrongDigest.canonicalCorpus.inputDigestSha256 = "f".repeat(64);
  assert.throws(() => validate(wrongDigest), /canonical corpus digest/i);

  const duplicate = fixture();
  const summary = artifactValue(duplicate, "CORPUS_SUMMARY");
  summary.uniqueRomIdentities = 332;
  replaceArtifact(duplicate, "CORPUS_SUMMARY", summary);
  assert.throws(() => validate(duplicate), /unique ROM identity count.*canonical/i);
});

test("rejects missing terminal outcomes and every error category", () => {
  for (const [path, message] of [
    ["outcomes.total", /terminal outcome total/i],
    ["outcomes.errors", /parser errors/i],
    ["dataCompatibility.errors", /compatibility errors/i],
    ["catalogs.catalogErrors", /catalog errors/i],
    ["catalogs.persistenceErrors", /persistence errors/i],
  ]) {
    const evidence = fixture();
    const summary = artifactValue(evidence, "CORPUS_SUMMARY");
    const [group, field] = path.split(".");
    summary[group][field] = field === "total" ? 332 : 1;
    if (path === "outcomes.errors") summary.outcomes.selected -= 1;
    if (path === "dataCompatibility.errors") summary.dataCompatibility.complete -= 1;
    replaceArtifact(evidence, "CORPUS_SUMMARY", summary);
    assert.throws(() => validate(evidence), message);
  }
});

test("rejects negative terminal counts or catalogs outside selected outcomes", () => {
  const negativeParser = fixture();
  const parserSummary = artifactValue(negativeParser, "CORPUS_SUMMARY");
  parserSummary.outcomes.selected = -1;
  parserSummary.outcomes.ambiguous = 332;
  replaceArtifact(negativeParser, "CORPUS_SUMMARY", parserSummary);
  assert.throws(() => validate(negativeParser), /nonnegative parser outcome counts/i);

  const negativeCompatibility = fixture();
  const compatibilitySummary = artifactValue(negativeCompatibility, "CORPUS_SUMMARY");
  compatibilitySummary.dataCompatibility.complete = -1;
  compatibilitySummary.dataCompatibility.partial = 330;
  replaceArtifact(negativeCompatibility, "CORPUS_SUMMARY", compatibilitySummary);
  assert.throws(() => validate(negativeCompatibility), /nonnegative compatibility counts/i);

  const extraCatalog = fixture();
  const catalogSummary = artifactValue(extraCatalog, "CORPUS_SUMMARY");
  catalogSummary.catalogs.materialized = 330;
  catalogSummary.catalogs.persisted = 330;
  replaceArtifact(extraCatalog, "CORPUS_SUMMARY", catalogSummary);
  assert.throws(() => validate(extraCatalog), /selected outcome.*materialized catalog/i);
});

test("rejects a pre-fix, relabeled, or digest-mismatched execution receipt", () => {
  const oldSchema = fixture();
  const receipt = artifactValue(oldSchema, "CORPUS_EXECUTION_RECEIPT");
  receipt.generator.schemaVersion = 12;
  replaceArtifact(oldSchema, "CORPUS_EXECUTION_RECEIPT", receipt);
  assert.throws(() => validate(oldSchema), /generator schema/i);

  const relabeled = fixture();
  const relabeledReceipt = artifactValue(relabeled, "CORPUS_EXECUTION_RECEIPT");
  relabeledReceipt.sourceCommit = "f".repeat(40);
  replaceArtifact(relabeled, "CORPUS_EXECUTION_RECEIPT", relabeledReceipt);
  assert.throws(() => validate(relabeled), /receipt source commit/i);

  const changedRaw = fixture();
  const changedReceipt = artifactValue(changedRaw, "CORPUS_EXECUTION_RECEIPT");
  changedReceipt.rawReportSha256 = "f".repeat(64);
  replaceArtifact(changedRaw, "CORPUS_EXECUTION_RECEIPT", changedReceipt);
  assert.throws(() => validate(changedRaw), /raw report digest/i);
});

test("rejects missing or nonzero Stage 7 and Stage 8 closure", () => {
  const missing = fixture();
  missing.manifest.artifacts = missing.manifest.artifacts.filter(entry => entry.role !== "STAGE_8_CLOSURE");
  assert.throws(() => validate(missing), /exactly one STAGE_8_CLOSURE/i);

  const open = fixture();
  const closure = artifactValue(open, "STAGE_7_CLOSURE");
  closure.openBlockers = 1;
  replaceArtifact(open, "STAGE_7_CLOSURE", closure);
  assert.throws(() => validate(open), /Stage 7.*zero blockers/i);
});

test("rejects reuse for every generator, build, wrapper, and evidence-generator category", () => {
  const changedCategories = [
    "parser-cli/src/main/kotlin/Main.kt",
    "parser-cli/build.gradle.kts",
    "app/build.gradle.kts",
    "app/build.gradle",
    "settings.gradle.kts",
    "settings.gradle",
    "build.gradle",
    "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradlew",
    "tools/corpus/Invoke-DualDexCorpusValidation.ps1",
  ];

  for (const changedPath of changedCategories) {
    const evidence = fixture({
      scopeDecision: {
        type: "NONPARSER_REUSE",
        attestation: "Only nonparser product behavior changed; parser output remains invariant.",
      },
    });
    assert.throws(
      () => validate(evidence, [changedPath]),
      /evidence-affecting paths changed/i,
      changedPath,
    );
  }
});

test("allows downstream evidence-policy corrections with nonparser reuse", () => {
  const evidence = fixture({
    scopeDecision: {
      type: "NONPARSER_REUSE",
      attestation: "Only downstream evidence policy changed; parser output and generated evidence remain invariant.",
    },
  });

  const result = validate(evidence, [
    "tools/release/validate-release-evidence.mjs",
    "tools/release/validate-candidate-promotion.mjs",
    "tools/release/derive-release-metadata.mjs",
    "tools/release/summarize-compatibility-evidence.mjs",
  ]);

  assert.equal(result.scopeDecision, "NONPARSER_REUSE");
  assert.equal(result.inputCount, 333);
});

test("requires a matching cache decision for parser or catalog changes", () => {
  const noDecision = fixture();
  assert.throws(
    () => validate(noDecision, [], ["parser-core/src/main/kotlin/Parser.kt"]),
    /cache decision/i,
  );

  const invariant = fixture({
    cacheDecision: {
      type: "OUTPUT_INVARIANT",
      revision: 45,
      rationale: "The report lineage changes do not alter persisted catalog output.",
      behaviorTest: "parser-cli/src/test/kotlin/ExecutionReceiptTest.kt",
    },
  });
  assert.equal(
    validate(invariant, [], [
      "parser-cli/src/main/kotlin/ExecutionReceipt.kt",
      "parser-cli/src/test/kotlin/ExecutionReceiptTest.kt",
    ]).cacheDecision,
    "OUTPUT_INVARIANT",
  );

  const bump = fixture({
    cacheDecision: {
      type: "BUMP_REQUIRED",
      previousRevision: 44,
      revision: 45,
      rationale: "Persisted parser output changed and must be rebuilt.",
      seededRegressionTest: "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    },
  });
  bump.priorCatalogSchemaRevision = 44;
  assert.equal(
    validate(bump, [], [
      "parser-core/src/main/kotlin/Parser.kt",
      "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    ]).cacheDecision,
    "BUMP_REQUIRED",
  );

  const inventedPrior = fixture({
    cacheDecision: {
      type: "BUMP_REQUIRED",
      previousRevision: 44,
      revision: 45,
      rationale: "This invents an older comparison revision instead of reading Git.",
      seededRegressionTest: "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    },
  });
  assert.throws(
    () => validate(inventedPrior, [], [
      "parser-core/src/main/kotlin/Parser.kt",
      "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    ]),
    /comparison release schema|actual schema advance/i,
  );

  const stableTransformation = fixture({
    scopeDecision: {
      type: "NONPARSER_REUSE",
      attestation: "Only allowlisted stable release metadata changed after the validated candidate.",
    },
    cacheDecision: {
      type: "BUMP_REQUIRED",
      previousRevision: 44,
      revision: 45,
      rationale: "The candidate parser range advanced persisted catalog output.",
      seededRegressionTest: "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    },
  });
  stableTransformation.priorCatalogSchemaRevision = 44;
  assert.equal(
    validate(stableTransformation, ["release/v1-final-authorization.json"], [
      "parser-core/src/main/kotlin/Parser.kt",
      "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    ]).cacheDecision,
    "BUMP_REQUIRED",
  );

  const mismatched = fixture({
    cacheDecision: {
      type: "BUMP_REQUIRED",
      previousRevision: 45,
      revision: 45,
      rationale: "This claims a bump without advancing the revision.",
      seededRegressionTest: "catalog-store/src/test/kotlin/CatalogCacheSchemaTest.kt",
    },
  });
  assert.throws(
    () => validate(mismatched, [], ["parser-core/src/main/kotlin/Parser.kt"]),
    /advance parser schema revision/i,
  );
});
