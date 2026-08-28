import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const PRIVATE_PATH_PATTERNS = [
  /\b[A-Za-z]:[\\/]/,
  /\/(?:home|Users|private|tmp|var\/tmp)\/[A-Za-z0-9._-]+[\\/]/,
  /\/(?:data\/user|storage\/emulated|sdcard)\//,
];
const LOCAL_IDENTIFIER_PATTERNS = [
  /\b(?:deploymentTarget|deviceKey|deviceId|serialNumber|workspaceId)\b["']?\s*[:=]/i,
  /\bemulator-[0-9]{4,}\b/i,
];

const STATIC_JSON_SHAPE_SHA256 = new Map([
  ["dualdex-parser-compatibility.json", "0902d070a0bfbcb2bab3ac67d80cbc3c90f099e1caa5726414f92d61ff856cf8"],
  ["dualdex-rom-hacks-compatibility.json", "0528c84417bd638deaa21db3f4bdb7c538ffe64a8712cc63a46ae35eed51af53"],
  ["dualdex-base-first50-release-gate.json", "0178e428e14660c06146273e1eed2b5d0a08cba950b2ff62e333a1d9b948559c"],
  ["dualdex-base-full332-compatibility-summary.json", "5e85aaeab5e8c292e8b7936e2df1eb0a4be6593c4f4704bf53259686e1a04aaa"],
  ["dualdex-map-first50-release-gate.json", "a2ac204c6accc3a35168c33a6da0cda3ccffa0200ff016ad33e7b6a1461e338e"],
  ["dualdex-evolution-first50-release-gate.json", "931a4b5c05225fa4ad0ea84e3a0b2858c866175707c656cc36dd748186ae61fc"],
  ["dualdex-gen1-gen3-table-coverage.json", "8374fe9e170f6613a12d2caf7ef51ac6b64f4fff9c61411d1ba68a999565eafa"],
  ["dualdex-unified-game-state-compatibility.json", "9e434d86d57968aab108f4f331ddc0008746ae2aabf46062fc89d6ab4502512c"],
  ["dualdex-party-analysis-compatibility.json", "f9b6469a178b67333f20840013e0d7dc05335a8ce6772b70299e31ab256b8fbb"],
  ["dualdex-area-guide-compatibility.json", "c62a6750226b5722eb79f46a6cc9c5c905ff382579d48c0ee378d98d94647dc2"],
  ["dualdex-progress-timeline-compatibility.json", "698c0c717fb7fa46dad505d9a914692fc52c3b80df8ab2cf479123cdff3b8f95"],
  ["dualdex-specimens-compatibility.json", "0aa87ad37b09209fd3ebec08a80ff2b5f2a3ab5f108f8c037f67c6baeb49019f"],
  ["dualdex-damage-forecast-compatibility.json", "67a6626194eca6629e9642b2f4694a3177690a492f0f3aeebb4d82abc45636c8"],
  ["dualdex-challenge-expansion-compatibility.json", "aafc178f47e27e65efa613c551fc492f2ae94a9a9d5a41d0198e68c3d912c791"],
  ["dualdex-ui-conformance-route-matrix.json", "dee889b123252ad1d6a9c671ff85ed8e9745e44f23be6425a71ae658415db261"],
  ["dualdex-ui-conformance-font-matrix.json", "34e6f95ed0f621a4be624b864b253209601884bc4c193404a64975d1efa6c228"],
  ["dualdex-ui-conformance-computed-styles.json", "c6c21cbce8d7c8dbf14e805a6ca506400c41df1285ba4e6b37f347385c87c956"],
  ["dualdex-ui-conformance-screenshots.json", "fe77d60164c80f158e2e989ea48cb1be40baac74294ddcc898db7fa5b7a7a25f"],
  ["dualdex-ui-conformance-summary.json", "d006a8e0f8cb0e80b81e55e51a63190ce1439e453b37cdc567ff2d9e3ab4b23d"],
]);

export function validatePublicReleaseAsset({ name, bytes }) {
  const text = Buffer.from(bytes).toString("utf8");
  if (PRIVATE_PATH_PATTERNS.some(pattern => pattern.test(text))) {
    throw new Error(`Release asset ${name} contains a private path`);
  }
  if (LOCAL_IDENTIFIER_PATTERNS.some(pattern => pattern.test(text))) {
    throw new Error(`Release asset ${name} contains a local identifier`);
  }
  if (name === "compatibility-evidence.json") {
    validateCompatibilityEvidence(JSON.parse(text));
  } else if (name === "canonical-corpus.json") {
    validateCanonicalCorpus(JSON.parse(text));
  } else if (name === "release-evidence-validation.json") {
    validateReleaseEvidenceValidation(JSON.parse(text));
  } else if (name === "repository-policy.json") {
    validateRepositoryPolicy(JSON.parse(text));
  } else if (name === "provenance.json") {
    validateProvenance(JSON.parse(text));
  } else if (name === "dualdex-stage-07-corpus-evidence.json") {
    validateStage7Summary(JSON.parse(text));
  } else if (name === "dualdex-stage-07-corpus-execution.json") {
    const receipt = JSON.parse(text);
    assertExactKeys(receipt, [
      "schemaVersion", "sourceCommit", "generator", "rawReportSha256", "inputCount",
    ]);
    assertExactKeys(receipt.generator, ["name", "schemaVersion", "sha256"]);
  } else if (/^dualdex-stage-0[78]-closure\.json$/.test(name)) {
    assertExactKeys(JSON.parse(text), [
      "schemaVersion", "stage", "status", "sourceCommit", "openBlockers", "openReferrals",
    ]);
  } else if (STATIC_JSON_SHAPE_SHA256.has(name)) {
    validateStaticJsonShape(name, JSON.parse(text));
  } else if (name.toLowerCase().endsWith(".json")) {
    throw new Error(`Unrecognized public JSON evidence asset: ${name}`);
  }
}

function validateStaticJsonShape(name, value) {
  const digest = createHash("sha256").update(structuralShape(value)).digest("hex");
  if (digest !== STATIC_JSON_SHAPE_SHA256.get(name)) {
    throw new Error(`Public JSON evidence ${name} contains an unknown evidence field or shape`);
  }
}

function structuralShape(value) {
  if (value === null) return "null";
  if (Array.isArray(value)) {
    const itemShapes = [...new Set(value.map(structuralShape))].sort();
    return `array(${itemShapes.join("|")})`;
  }
  if (typeof value === "object") {
    const fields = Object.keys(value).sort().map(key =>
      `${JSON.stringify(key)}:${structuralShape(value[key])}`,
    );
    return `object(${fields.join(",")})`;
  }
  return typeof value;
}

function validateCompatibilityEvidence(manifest) {
  assertClosedKeys(manifest, [
    "schemaVersion", "sourceCommit", "generator", "corpus", "scopeDecision", "artifacts",
  ], ["cacheDecision"]);
  assertExactKeys(manifest.generator, ["name", "schemaVersion", "sha256"]);
  assertExactKeys(manifest.corpus, ["inputDigestSha256", "inputCount"]);
  assertExactKeys(manifest.scopeDecision, ["type", "attestation"]);
  assertObjectArray(manifest.artifacts, ["role", "path", "sha256"]);
  if (manifest.cacheDecision != null) {
    if (manifest.cacheDecision.type === "BUMP_REQUIRED") {
      assertExactKeys(manifest.cacheDecision, [
        "type", "revision", "previousRevision", "rationale", "seededRegressionTest",
      ]);
    } else if (manifest.cacheDecision.type === "OUTPUT_INVARIANT") {
      assertExactKeys(manifest.cacheDecision, ["type", "revision", "rationale", "behaviorTest"]);
    } else {
      throw new Error("Public JSON evidence has an unknown cache-decision shape");
    }
  }
}

function validateCanonicalCorpus(corpus) {
  assertExactKeys(corpus, ["schemaVersion", "inputCount", "inputDigestSha256"]);
}

function validateReleaseEvidenceValidation(validation) {
  assertExactKeys(validation, [
    "schemaVersion", "releaseCommit", "evidenceSourceCommit", "scopeDecision", "cacheDecision",
    "generatorSchemaVersion", "generatorSha256", "corpusInputDigestSha256", "inputCount",
    "artifactCount", "stage7Closed", "stage8Closed",
  ]);
}

function validateRepositoryPolicy(policy) {
  assertExactKeys(policy, [
    "schemaVersion", "repository", "tag", "defaultBranch", "tagRuleset",
    "signingEnvironment", "promotionEnvironment",
  ]);
  assertExactKeys(policy.tagRuleset, [
    "id", "name", "enforcement", "requiredRuleTypes",
  ]);
  validateEnvironmentPolicy(policy.signingEnvironment, false);
  validateEnvironmentPolicy(policy.promotionEnvironment, true);
}

function validateEnvironmentPolicy(environment, promotion) {
  const keys = [
    "name", "deploymentBranchPolicy", "requiredReviewerCount", "preventSelfReview",
    "protectionRuleTypes",
  ];
  if (promotion) keys.push("signingSecretCount");
  assertExactKeys(environment, keys);
}

function validateProvenance(provenance) {
  assertExactKeys(provenance, [
    "schema", "repository", "commit", "workflowRunId", "tag", "releaseKind", "versionName",
    "versionCode", "applicationId", "apkSha256", "certificateSha256", "signingAuthority",
    "compatibilityEvidence", "releaseEvidenceValidation", "repositoryPolicy",
  ]);
  validateCompatibilityEvidence(provenance.compatibilityEvidence);
  validateReleaseEvidenceValidation(provenance.releaseEvidenceValidation);
  validateRepositoryPolicy(provenance.repositoryPolicy);
}

function assertObjectArray(value, expectedKeys) {
  if (!Array.isArray(value)) throw new Error("Public JSON evidence has an invalid array shape");
  for (const entry of value) assertExactKeys(entry, expectedKeys);
}

function validateStage7Summary(summary) {
  assertExactKeys(summary, [
    "schemaVersion", "sourceCommit", "generator", "rawReportSha256",
    "corpusInputDigestSha256", "inputCount", "uniqueRomIdentities", "outcomes",
    "dataCompatibility", "catalogs", "privacy",
  ]);
  assertExactKeys(summary.generator, ["name", "schemaVersion", "sha256"]);
  assertExactKeys(summary.outcomes, ["selected", "ambiguous", "noFamilyMatch", "total", "errors"]);
  assertExactKeys(summary.dataCompatibility, ["complete", "partial", "unresolved", "total", "errors"]);
  assertExactKeys(summary.catalogs, ["materialized", "persisted", "catalogErrors", "persistenceErrors"]);
  assertExactKeys(summary.privacy, [
    "containsRomIdentity", "containsRomName", "containsSourcePath", "containsRomBytes",
  ]);
  if (Object.values(summary.privacy).some(value => value !== false)) {
    throw new Error("Stage 7 summary has an unsafe privacy declaration");
  }
}

function assertExactKeys(value, expected) {
  assertClosedKeys(value, expected, []);
}

function assertClosedKeys(value, required, optional) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Public JSON evidence has an invalid object shape");
  }
  const actual = Object.keys(value).sort();
  const allowed = [...required, ...optional];
  const hasAllRequired = required.every(key => Object.hasOwn(value, key));
  if (!hasAllRequired || actual.some(key => !allowed.includes(key))) {
    throw new Error("Public JSON evidence contains an unknown evidence field or omits a required field");
  }
}

function assetPaths(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return assetPaths(path);
    return entry.isFile() && statSync(path).isFile() ? [path] : [];
  });
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
  const directory = resolve(options.directory);
  const paths = assetPaths(directory);
  if (paths.length === 0) throw new Error("Release asset directory is empty");
  for (const path of paths) {
    validatePublicReleaseAsset({ name: basename(path), bytes: readFileSync(path) });
  }
  process.stdout.write(`${JSON.stringify({ schemaVersion: 1, assetCount: paths.length })}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    main(process.argv.slice(2));
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
