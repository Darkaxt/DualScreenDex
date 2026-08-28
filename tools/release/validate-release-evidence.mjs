import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { isAbsolute, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const SHA256 = /^[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const PARSER_CATALOG_PATH = /^(?:parser-core|parser-assets|parser-cli\/src\/main|catalog-store|save-core)\//;
const BUILD_LOGIC_PATH = /^(?:settings\.gradle\.kts|build\.gradle\.kts|gradle\/libs\.versions\.toml)$/;
const EVIDENCE_PACKAGING_PATH = /^(?:release\/compatibility-evidence\.json|docs\/reports\/qa-hardening\/stage-07-corpus-evidence\.(?:json|md))$/;

export function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

export function validateReleaseEvidence({
  manifest,
  releaseCommit,
  changedPaths,
  readArtifact,
}) {
  assert(manifest?.schemaVersion === 1, "evidence manifest schemaVersion must be 1");
  assert(COMMIT.test(manifest.sourceCommit ?? ""), "evidence sourceCommit must be a full lowercase commit");
  assert(COMMIT.test(releaseCommit ?? ""), "release commit must be a full lowercase commit");
  assert(manifest.generator?.name === "parser-cli", "evidence generator name must be parser-cli");
  assert(Number.isInteger(manifest.generator?.schemaVersion) && manifest.generator.schemaVersion > 0,
    "evidence generator schemaVersion must be positive");
  assert(SHA256.test(manifest.corpus?.inputDigestSha256 ?? ""),
    "corpus inputDigestSha256 must be a lowercase SHA-256");
  assert(Number.isInteger(manifest.corpus?.inputCount) && manifest.corpus.inputCount > 0,
    "corpus inputCount must be positive");
  assert(["FRESH_EVIDENCE", "NONPARSER_REUSE"].includes(manifest.scopeDecision?.type),
    "scopeDecision.type must be FRESH_EVIDENCE or NONPARSER_REUSE");
  assert(typeof manifest.scopeDecision?.attestation === "string" && manifest.scopeDecision.attestation.trim().length >= 20,
    "scopeDecision requires a meaningful attestation");
  assert(Array.isArray(manifest.artifacts) && manifest.artifacts.length > 0,
    "evidence manifest requires artifacts");

  const affectingPaths = changedPaths.filter(path => PARSER_CATALOG_PATH.test(path) || BUILD_LOGIC_PATH.test(path));
  assert(affectingPaths.length === 0,
    `parser/catalog-affecting paths changed after evidence source: ${affectingPaths.join(", ")}`);
  const nonPackagingPaths = changedPaths.filter(path => !EVIDENCE_PACKAGING_PATH.test(path));
  if (manifest.scopeDecision.type === "FRESH_EVIDENCE") {
    assert(nonPackagingPaths.length === 0,
      `FRESH_EVIDENCE cannot cover post-evidence product changes: ${nonPackagingPaths.join(", ")}`);
  }

  let corpusSummary = null;
  for (const artifact of manifest.artifacts) {
    assert(artifact && typeof artifact.path === "string" && isSafeRelativePath(artifact.path),
      "artifact paths must be normalized repository-relative paths");
    assert(SHA256.test(artifact.sha256 ?? ""), `artifact ${artifact.path} has an invalid SHA-256`);
    const bytes = readArtifact(artifact.path);
    assert(bytes != null, `evidence artifact is missing: ${artifact.path}`);
    assert(sha256(bytes) === artifact.sha256, `evidence artifact digest mismatch: ${artifact.path}`);
    if (artifact.role === "CORPUS_SUMMARY") {
      assert(corpusSummary == null, "evidence manifest must contain exactly one corpus summary");
      corpusSummary = JSON.parse(Buffer.from(bytes).toString("utf8"));
    }
  }

  assert(corpusSummary != null, "evidence manifest requires one CORPUS_SUMMARY artifact");
  assert(corpusSummary.schemaVersion === 1, "corpus summary schemaVersion must be 1");
  assert(corpusSummary.sourceCommit === manifest.sourceCommit, "corpus summary sourceCommit does not match manifest");
  assert(corpusSummary.generatorSchemaVersion === manifest.generator.schemaVersion,
    "corpus summary generator schema does not match manifest");
  assert(corpusSummary.corpusInputDigestSha256 === manifest.corpus.inputDigestSha256,
    "corpus summary input digest does not match manifest");
  assert(corpusSummary.inputCount === manifest.corpus.inputCount,
    "corpus summary input count does not match manifest");
  assert(corpusSummary.outcomes?.errors === 0, "corpus evidence contains parser errors");
  assert(corpusSummary.catalogs?.persistenceErrors === 0, "corpus evidence contains persistence errors");
  assert(corpusSummary.catalogs?.materialized > 0, "corpus evidence materialized no catalogs");
  assert(corpusSummary.catalogs.persisted === corpusSummary.catalogs.materialized,
    "not every materialized catalog was persisted and reopened");

  return {
    schemaVersion: 1,
    releaseCommit,
    evidenceSourceCommit: manifest.sourceCommit,
    scopeDecision: manifest.scopeDecision.type,
    generatorSchemaVersion: manifest.generator.schemaVersion,
    corpusInputDigestSha256: manifest.corpus.inputDigestSha256,
    inputCount: manifest.corpus.inputCount,
    artifactCount: manifest.artifacts.length,
  };
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

function main(arguments_) {
  const options = parseArguments(arguments_);
  const repositoryRoot = resolve(options["repository-root"] ?? ".");
  const manifestPath = resolve(repositoryRoot, options.manifest ?? "release/compatibility-evidence.json");
  const releaseCommit = options["release-commit"];
  assert(existsSync(manifestPath), `Evidence manifest is missing: ${manifestPath}`);
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  execFileSync("git", ["merge-base", "--is-ancestor", manifest.sourceCommit, releaseCommit], {
    cwd: repositoryRoot,
    stdio: "ignore",
  });
  const changedPaths = execFileSync(
    "git",
    ["diff", "--name-only", `${manifest.sourceCommit}..${releaseCommit}`],
    { cwd: repositoryRoot, encoding: "utf8" },
  ).split(/\r?\n/).filter(Boolean);
  const rootPrefix = repositoryRoot.endsWith(sep) ? repositoryRoot : `${repositoryRoot}${sep}`;
  const result = validateReleaseEvidence({
    manifest,
    releaseCommit,
    changedPaths,
    readArtifact: path => {
      const absolute = resolve(repositoryRoot, path);
      assert(absolute.startsWith(rootPrefix), `Artifact escapes repository root: ${path}`);
      return existsSync(absolute) ? readFileSync(absolute) : null;
    },
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    main(process.argv.slice(2));
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
