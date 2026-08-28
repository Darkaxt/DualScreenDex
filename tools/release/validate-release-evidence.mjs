import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { isAbsolute, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const SHA256 = /^[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const REQUIRED_INPUT_COUNT = 334;
const REQUIRED_GENERATOR_SCHEMA = 13;
const PARSER_CATALOG_PATH = /^(?:parser-core|parser-assets|parser-cli|catalog-store|save-core)\//;
const BUILD_LOGIC_PATH = /^(?:buildSrc|build-logic|gradle)\/|^(?:gradlew(?:\.bat)?|gradle\.properties|settings\.gradle(?:\.kts)?|build\.gradle(?:\.kts)?)$|\/build\.gradle(?:\.kts)?$/;
const EVIDENCE_TOOL_PATH = /^tools\/(?:release|corpus)\//;
const EVIDENCE_PACKAGING_PATH = /^(?:release\/(?:compatibility-evidence|canonical-corpus)\.json|docs\/reports\/qa-hardening\/stage-(?:07-(?:corpus-evidence\.(?:json|md)|corpus-execution\.json|closure\.(?:json|md))|08-closure\.(?:json|md)))$/;
const REQUIRED_ROLES = [
  "CORPUS_SUMMARY",
  "CORPUS_EXECUTION_RECEIPT",
  "STAGE_7_CLOSURE",
  "STAGE_8_CLOSURE",
];

export function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

export function validateReleaseEvidence({
  manifest,
  canonicalCorpus,
  releaseCommit,
  changedPaths,
  decisionPaths = [],
  catalogSchemaRevision,
  priorCatalogSchemaRevision,
  readArtifact,
}) {
  assert(manifest?.schemaVersion === 2, "evidence manifest schemaVersion must be 2");
  assert(COMMIT.test(manifest.sourceCommit ?? ""), "evidence sourceCommit must be a full lowercase commit");
  assert(COMMIT.test(releaseCommit ?? ""), "release commit must be a full lowercase commit");
  validateGenerator(manifest.generator, "evidence manifest");
  validateCanonicalCorpus(canonicalCorpus);
  assert(manifest.corpus?.inputCount === canonicalCorpus.inputCount,
    "manifest corpus input count does not match canonical corpus");
  assert(manifest.corpus?.inputDigestSha256 === canonicalCorpus.inputDigestSha256,
    "manifest corpus digest does not match canonical corpus digest");
  assert(["FRESH_EVIDENCE", "NONPARSER_REUSE"].includes(manifest.scopeDecision?.type),
    "scopeDecision.type must be FRESH_EVIDENCE or NONPARSER_REUSE");
  assert(typeof manifest.scopeDecision?.attestation === "string" && manifest.scopeDecision.attestation.trim().length >= 20,
    "scopeDecision requires a meaningful attestation");
  assert(Array.isArray(manifest.artifacts), "evidence manifest requires artifacts");

  const evidenceAffectingPaths = changedPaths.filter(isEvidenceAffectingPath);
  assert(evidenceAffectingPaths.length === 0,
    `evidence-affecting paths changed after evidence source: ${evidenceAffectingPaths.join(", ")}`);
  if (manifest.scopeDecision.type === "FRESH_EVIDENCE") {
    const nonPackagingPaths = changedPaths.filter(path => !EVIDENCE_PACKAGING_PATH.test(path));
    assert(nonPackagingPaths.length === 0,
      `FRESH_EVIDENCE cannot cover post-evidence product changes: ${nonPackagingPaths.join(", ")}`);
  }

  const cacheDecision = validateCacheDecision({
    decision: manifest.cacheDecision,
    decisionPaths,
    catalogSchemaRevision,
    priorCatalogSchemaRevision,
  });

  const parsedArtifacts = new Map();
  const seenPaths = new Set();
  for (const artifact of manifest.artifacts) {
    assert(artifact && typeof artifact.path === "string" && isSafeRelativePath(artifact.path),
      "artifact paths must be normalized repository-relative paths");
    assert(!seenPaths.has(artifact.path), `duplicate evidence artifact path: ${artifact.path}`);
    seenPaths.add(artifact.path);
    assert(SHA256.test(artifact.sha256 ?? ""), `artifact ${artifact.path} has an invalid SHA-256`);
    const bytes = readArtifact(artifact.path);
    assert(bytes != null, `evidence artifact is missing: ${artifact.path}`);
    assert(sha256(bytes) === artifact.sha256, `evidence artifact digest mismatch: ${artifact.path}`);
    if (REQUIRED_ROLES.includes(artifact.role)) {
      assert(!parsedArtifacts.has(artifact.role), `evidence manifest must contain exactly one ${artifact.role}`);
      parsedArtifacts.set(artifact.role, JSON.parse(Buffer.from(bytes).toString("utf8")));
    }
  }
  for (const role of REQUIRED_ROLES) {
    assert(parsedArtifacts.has(role), `evidence manifest must contain exactly one ${role}`);
  }

  const summary = parsedArtifacts.get("CORPUS_SUMMARY");
  const receipt = parsedArtifacts.get("CORPUS_EXECUTION_RECEIPT");
  validateSummary(summary, manifest, canonicalCorpus);
  validateReceipt(receipt, manifest, summary);
  validateClosure(parsedArtifacts.get("STAGE_7_CLOSURE"), 7, manifest.sourceCommit);
  validateClosure(parsedArtifacts.get("STAGE_8_CLOSURE"), 8, manifest.sourceCommit);

  return {
    schemaVersion: 2,
    releaseCommit,
    evidenceSourceCommit: manifest.sourceCommit,
    scopeDecision: manifest.scopeDecision.type,
    cacheDecision,
    generatorSchemaVersion: manifest.generator.schemaVersion,
    generatorSha256: manifest.generator.sha256,
    corpusInputDigestSha256: canonicalCorpus.inputDigestSha256,
    inputCount: canonicalCorpus.inputCount,
    artifactCount: manifest.artifacts.length,
    stage7Closed: true,
    stage8Closed: true,
  };
}

function validateCanonicalCorpus(canonicalCorpus) {
  assert(canonicalCorpus?.schemaVersion === 1, "canonical corpus schemaVersion must be 1");
  assert(canonicalCorpus.inputCount === REQUIRED_INPUT_COUNT,
    `canonical corpus must contain exactly ${REQUIRED_INPUT_COUNT} inputs`);
  assert(SHA256.test(canonicalCorpus.inputDigestSha256 ?? ""),
    "canonical corpus input digest must be a lowercase SHA-256");
}

function validateGenerator(generator, description) {
  assert(generator?.name === "parser-cli", `${description} generator name must be parser-cli`);
  assert(generator?.schemaVersion === REQUIRED_GENERATOR_SCHEMA,
    `${description} generator schema must be ${REQUIRED_GENERATOR_SCHEMA}`);
  assert(SHA256.test(generator?.sha256 ?? ""), `${description} generator digest must be a lowercase SHA-256`);
}

function validateSummary(summary, manifest, canonicalCorpus) {
  assert(summary?.schemaVersion === 2, "corpus summary schemaVersion must be 2");
  assert(summary.sourceCommit === manifest.sourceCommit, "corpus summary sourceCommit does not match manifest");
  validateGenerator(summary.generator, "corpus summary");
  assert(summary.generator.schemaVersion === manifest.generator.schemaVersion &&
    summary.generator.sha256 === manifest.generator.sha256,
  "corpus summary generator does not match manifest");
  assert(SHA256.test(summary.rawReportSha256 ?? ""), "corpus summary raw report digest is invalid");
  assert(summary.corpusInputDigestSha256 === canonicalCorpus.inputDigestSha256,
    "corpus summary input digest does not match canonical corpus");
  assert(summary.inputCount === REQUIRED_INPUT_COUNT, `corpus summary must contain exactly ${REQUIRED_INPUT_COUNT} inputs`);
  assert(summary.uniqueRomIdentities === REQUIRED_INPUT_COUNT,
    `corpus summary must contain exactly ${REQUIRED_INPUT_COUNT} unique ROM identities`);
  assert(hasNonnegativeIntegerFields(summary.outcomes, ["selected", "ambiguous", "noFamilyMatch", "errors"]),
    "corpus summary requires nonnegative parser outcome counts");
  assert(summary.outcomes?.total === REQUIRED_INPUT_COUNT, "corpus terminal outcome total must equal 334");
  assert(sumFields(summary.outcomes, ["selected", "ambiguous", "noFamilyMatch", "errors"]) === REQUIRED_INPUT_COUNT,
    "corpus terminal outcomes do not sum to 334");
  assert(summary.outcomes.errors === 0, "corpus evidence contains parser errors");
  assert(hasNonnegativeIntegerFields(summary.dataCompatibility, ["complete", "partial", "unresolved", "errors"]),
    "corpus summary requires nonnegative compatibility counts");
  assert(summary.dataCompatibility?.total === REQUIRED_INPUT_COUNT, "compatibility terminal total must equal 334");
  assert(sumFields(summary.dataCompatibility, ["complete", "partial", "unresolved", "errors"]) === REQUIRED_INPUT_COUNT,
    "compatibility outcomes do not sum to 334");
  assert(summary.dataCompatibility.errors === 0, "corpus evidence contains compatibility errors");
  assert(summary.catalogs?.catalogErrors === 0, "corpus evidence contains catalog errors");
  assert(summary.catalogs?.persistenceErrors === 0, "corpus evidence contains persistence errors");
  assert(Number.isInteger(summary.catalogs?.materialized) && summary.catalogs.materialized > 0,
    "corpus evidence materialized no catalogs");
  assert(summary.catalogs.materialized === summary.outcomes.selected,
    "every selected outcome must have exactly one materialized catalog");
  assert(summary.catalogs.persisted === summary.catalogs.materialized,
    "not every materialized catalog was persisted and reopened");
  assert(summary.privacy?.containsRomIdentity === false &&
    summary.privacy?.containsRomName === false &&
    summary.privacy?.containsSourcePath === false &&
    summary.privacy?.containsRomBytes === false,
  "corpus summary privacy declaration is not safe");
}

function validateReceipt(receipt, manifest, summary) {
  assert(receipt?.schemaVersion === 1, "execution receipt schemaVersion must be 1");
  assert(receipt.sourceCommit === manifest.sourceCommit, "execution receipt source commit does not match manifest");
  validateGenerator(receipt.generator, "execution receipt");
  assert(receipt.generator.schemaVersion === manifest.generator.schemaVersion &&
    receipt.generator.sha256 === manifest.generator.sha256,
  "execution receipt generator does not match manifest");
  assert(receipt.rawReportSha256 === summary.rawReportSha256,
    "execution receipt raw report digest does not match corpus summary");
  assert(receipt.inputCount === REQUIRED_INPUT_COUNT, "execution receipt input count must be 334");
}

function validateClosure(closure, stage, sourceCommit) {
  assert(closure?.schemaVersion === 1 && closure.stage === stage && closure.status === "CLOSED",
    `Stage ${stage} closure is missing or not CLOSED`);
  assert(closure.sourceCommit === sourceCommit, `Stage ${stage} closure source commit does not match evidence`);
  assert(closure.openBlockers === 0, `Stage ${stage} closure must have zero blockers`);
  assert(closure.openReferrals === 0, `Stage ${stage} closure must have zero referrals`);
}

function validateCacheDecision({
  decision,
  decisionPaths,
  catalogSchemaRevision,
  priorCatalogSchemaRevision,
}) {
  const affectingPaths = decisionPaths.filter(path => PARSER_CATALOG_PATH.test(path));
  if (affectingPaths.length === 0) {
    assert(decision == null, "cache decision is permitted only for parser/catalog-affecting changes");
    return "NOT_APPLICABLE";
  }
  assert(Number.isInteger(catalogSchemaRevision) && catalogSchemaRevision > 0,
    "catalog parser schema revision is invalid");
  assert(Number.isInteger(priorCatalogSchemaRevision) && priorCatalogSchemaRevision > 0,
    "comparison release schema revision is invalid");
  assert(decision && ["BUMP_REQUIRED", "OUTPUT_INVARIANT"].includes(decision.type),
    "parser/catalog-affecting changes require exactly one cache decision");
  assert(decision.revision === catalogSchemaRevision, "cache decision revision does not match production schema");
  assert(typeof decision.rationale === "string" && decision.rationale.trim().length >= 20,
    "cache decision requires a bounded rationale");
  if (decision.type === "BUMP_REQUIRED") {
    assert(decision.previousRevision === priorCatalogSchemaRevision,
      "BUMP_REQUIRED previousRevision must match the comparison release schema");
    assert(catalogSchemaRevision > priorCatalogSchemaRevision,
      "BUMP_REQUIRED must advance parser schema revision from the actual comparison release");
    assert(isChangedTest(decision.seededRegressionTest, decisionPaths, "catalog-store"),
      "BUMP_REQUIRED requires a changed seeded prior-version rejection/rebuild regression");
  } else {
    assert(catalogSchemaRevision === priorCatalogSchemaRevision,
      "OUTPUT_INVARIANT cannot accompany a parser schema revision change");
    assert(isChangedTest(decision.behaviorTest, decisionPaths),
      "OUTPUT_INVARIANT requires a changed behavior test proving persisted output remains valid");
  }
  return decision.type;
}

function isChangedTest(path, changedPaths, requiredModule) {
  return typeof path === "string" && isSafeRelativePath(path) &&
    path.includes("/src/test/") && (!requiredModule || path.startsWith(`${requiredModule}/`)) &&
    changedPaths.includes(path);
}

function hasNonnegativeIntegerFields(value, fields) {
  return fields.every(field => Number.isInteger(value?.[field]) && value[field] >= 0);
}

function sumFields(value, fields) {
  return fields.reduce((total, field) => total + (Number.isInteger(value?.[field]) ? value[field] : Number.NaN), 0);
}

function isEvidenceAffectingPath(path) {
  return PARSER_CATALOG_PATH.test(path) || BUILD_LOGIC_PATH.test(path) || EVIDENCE_TOOL_PATH.test(path);
}

function isSafeRelativePath(path) {
  return path.length > 0 && !isAbsolute(path) && !path.includes("\\") &&
    !path.split("/").some(segment => segment === "" || segment === "." || segment === "..");
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function parseArguments(arguments_) {
  const options = {};
  for (let index = 0; index < arguments_.length; index += 2) {
    const key = arguments_[index];
    const value = arguments_[index + 1];
    if (!key?.startsWith("--") || value == null) throw new Error(`Invalid argument: ${key ?? "<missing>"}`);
    options[key.slice(2)] = value;
  }
  return options;
}

function parseCatalogSchemaRevisionSource(source) {
  const match = source.match(/const val parserSchemaVersion\s*=\s*(\d+)/);
  assert(match != null, "could not read parser schema revision");
  return Number(match[1]);
}

function parseCatalogSchemaRevision(path) {
  return parseCatalogSchemaRevisionSource(readFileSync(path, "utf8"));
}

function runGit(repositoryRoot, arguments_) {
  const result = spawnSync("git", arguments_, { cwd: repositoryRoot, encoding: "utf8" });
  assert(result.status === 0, "git source-lineage validation failed");
  return result.stdout;
}

function main(arguments_) {
  const options = parseArguments(arguments_);
  const repositoryRoot = resolve(options["repository-root"] ?? ".");
  const manifestPath = resolve(repositoryRoot, options.manifest ?? "release/compatibility-evidence.json");
  const canonicalPath = resolve(repositoryRoot, options["canonical-corpus"] ?? "release/canonical-corpus.json");
  const releaseCommit = options["release-commit"];
  assert(existsSync(manifestPath), `Evidence manifest is missing: ${manifestPath}`);
  assert(existsSync(canonicalPath), `Canonical corpus contract is missing: ${canonicalPath}`);
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const canonicalCorpus = JSON.parse(readFileSync(canonicalPath, "utf8"));
  runGit(repositoryRoot, ["merge-base", "--is-ancestor", manifest.sourceCommit, releaseCommit]);
  const changedPaths = runGit(repositoryRoot, ["diff", "--name-only", `${manifest.sourceCommit}..${releaseCommit}`])
    .split(/\r?\n/)
    .filter(Boolean);
  const decisionPaths = options["decision-paths"]
    ? readFileSync(resolve(options["decision-paths"]), "utf8").split(/\r?\n/).filter(Boolean)
    : [];
  const catalogSchemaPath = options["catalog-schema"];
  assert(typeof catalogSchemaPath === "string" && isSafeRelativePath(catalogSchemaPath),
    "catalog schema path must be repository-relative");
  const comparisonCommit = runGit(repositoryRoot, ["rev-parse", `${options["comparison-ref"]}^{commit}`]).trim();
  assert(COMMIT.test(comparisonCommit), "comparison release commit is invalid");
  const priorCatalogSchemaRevision = parseCatalogSchemaRevisionSource(
    runGit(repositoryRoot, ["show", `${comparisonCommit}:${catalogSchemaPath}`]),
  );
  const rootPrefix = repositoryRoot.endsWith(sep) ? repositoryRoot : `${repositoryRoot}${sep}`;
  const result = validateReleaseEvidence({
    manifest,
    canonicalCorpus,
    releaseCommit,
    changedPaths,
    decisionPaths,
    catalogSchemaRevision: parseCatalogSchemaRevision(resolve(repositoryRoot, catalogSchemaPath)),
    priorCatalogSchemaRevision,
    readArtifact: path => {
      const absolute = resolve(repositoryRoot, path);
      assert(absolute.startsWith(rootPrefix), `Artifact escapes repository root: ${path}`);
      return existsSync(absolute) ? readFileSync(absolute) : null;
    },
  });
  const encoded = `${JSON.stringify(result, null, 2)}\n`;
  if (options.output) writeFileSync(resolve(options.output), encoded);
  else process.stdout.write(encoded);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    main(process.argv.slice(2));
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
