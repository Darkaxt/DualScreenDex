import { createHash } from "node:crypto";
import { basename, join } from "node:path";
import { pathToFileURL } from "node:url";
import { readFileSync } from "node:fs";
import process from "node:process";

const EXPECTED_APPLICATION_ID = "com.darkaxt.dualdex";

function parseArguments(argumentsList) {
  const parsed = {};
  for (let index = 0; index < argumentsList.length; index += 2) {
    const key = argumentsList[index];
    const value = argumentsList[index + 1];
    if (!key?.startsWith("--") || value === undefined) {
      throw new Error(`Invalid argument list near ${key ?? "<end>"}`);
    }
    parsed[key.slice(2)] = value;
  }
  return parsed;
}

function requireArgument(argumentsMap, name) {
  const value = argumentsMap[name];
  if (!value) throw new Error(`Required argument --${name} is missing`);
  return value;
}

function requireJson(path, description) {
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch (failure) {
    throw new Error(`${description} could not be read: ${failure.message}`);
  }
}

function requireCondition(condition, message) {
  if (!condition) throw new Error(message);
}

function normalizeSha256(value, description) {
  const normalized = String(value ?? "")
    .replaceAll(":", "")
    .replaceAll(/\s/g, "")
    .toUpperCase();
  if (!/^[A-F0-9]{64}$/.test(normalized)) {
    throw new Error(`${description} is not a SHA-256 fingerprint`);
  }
  return normalized;
}

function fileSha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex").toUpperCase();
}

function verifiedApkSigner(path) {
  const verification = readFileSync(path, "utf8");
  const match = /^(?:Signer #[0-9]+|V[0-9.]+ Signer):? certificate SHA-256 digest: ([A-Fa-f0-9:]{64,95})$/m.exec(
    verification,
  );
  requireCondition(match != null, "APK signer verification did not report a certificate SHA-256");
  return normalizeSha256(match[1], "Cryptographically verified APK signer");
}

function checksumFor(checksumsPath, assetName) {
  const matches = readFileSync(checksumsPath, "utf8")
    .split(/\r?\n/)
    .map((line) => /^([A-Fa-f0-9]{64}) [ *](.+)$/.exec(line))
    .filter((match) => match?.[2] === assetName);
  requireCondition(matches.length === 1, `Checksum manifest must name ${assetName} exactly once`);
  return normalizeSha256(matches[0][1], "Checksum-manifest APK hash");
}

function isWorkflowRunUrl(value, repository) {
  try {
    const url = new URL(value);
    const prefix = `/${repository}/actions/runs/`;
    return (
      url.protocol === "https:" &&
      url.hostname === "github.com" &&
      url.username === "" &&
      url.password === "" &&
      url.search === "" &&
      url.hash === "" &&
      url.pathname.startsWith(prefix) &&
      /^[1-9]\d*$/.test(url.pathname.slice(prefix.length))
    );
  } catch {
    return false;
  }
}

function validatePackagedAndroidAndThor(
  record,
  repository,
  artifactIdentity,
) {
  requireCondition(
    record.packagedAndroidValidated === true &&
      isWorkflowRunUrl(record.packagedAndroidWorkflowRunUrl, repository) &&
      /^sha256:[A-Fa-f0-9]{64}$/.test(
        record.packagedAndroidEvidenceArtifactDigest ?? "",
      ),
    "Packaged Android validation requires a successful workflow-run URL",
  );
  const thor = record.thorValidationRecord;
  requireCondition(
    record.thorValidated === true &&
      thor?.status === "PASSED" &&
      thor.candidateTag === artifactIdentity.candidateTag &&
      normalizeSha256(thor.apkSha256, "Thor-validated APK hash") ===
        artifactIdentity.apkSha256 &&
      normalizeSha256(thor.validatedSignerSha256, "Thor-validated signer") ===
        artifactIdentity.certificateSha256 &&
      thor.checklistVersion === 1,
    "Thor validation must identify the exact candidate artifact and checklist",
  );
}

function validateAutomatedPassiveCatalog(record, repository) {
  const complete =
    record.userAuthorizedAutomatedPromotion === true &&
    record.gameplayRuntimeChanged === false &&
    Number.isInteger(record.exactRomControls) &&
    record.exactRomControls >= 5 &&
    record.catalogPersistenceValidated === true &&
    record.runtimeApiValidated === true &&
    record.webPresentationValidated === true &&
    isWorkflowRunUrl(record.automatedValidationWorkflowRunUrl, repository) &&
    /^docs\/reports\/candidate-promotions\/[A-Za-z0-9._-]+\.json$/.test(
      record.automatedEvidencePath ?? "",
    ) &&
    /^[A-Fa-f0-9]{64}$/.test(record.automatedEvidenceSha256 ?? "");
  requireCondition(
    complete,
    "Automated passive-catalog promotion requires complete authorized substitution evidence",
  );
}

function parsePublishedJson(assets, name) {
  try {
    return JSON.parse(assets.get(name).toString("utf8"));
  } catch {
    throw new Error(`Published evidence asset is not valid JSON: ${name}`);
  }
}

function validatePublishedEvidenceAssets(assets) {
  const manifest = parsePublishedJson(assets, "compatibility-evidence.json");
  const canonical = parsePublishedJson(assets, "canonical-corpus.json");
  const validation = parsePublishedJson(assets, "release-evidence-validation.json");
  const summary = parsePublishedJson(assets, "dualdex-stage-07-corpus-evidence.json");
  const receipt = parsePublishedJson(assets, "dualdex-stage-07-corpus-execution.json");
  const stage7 = parsePublishedJson(assets, "dualdex-stage-07-closure.json");
  const stage8 = parsePublishedJson(assets, "dualdex-stage-08-closure.json");

  requireCondition(manifest?.schemaVersion === 2, "Published release evidence schemaVersion must be 2");
  requireCondition(/^[0-9a-f]{40}$/.test(manifest.sourceCommit ?? ""),
    "Published release evidence source commit is invalid");
  validatePublishedGenerator(manifest.generator, "Published release evidence");
  requireCondition(canonical?.schemaVersion === 1 && canonical.inputCount === 334 &&
    /^[0-9a-f]{64}$/.test(canonical.inputDigestSha256 ?? ""),
  "Published canonical corpus must bind exactly 334 inputs");
  validatePublishedSummary(summary, manifest, canonical);
  validatePublishedReceipt(receipt, manifest, summary);

  validatePublishedClosure(stage7, 7, manifest.sourceCommit);
  validatePublishedClosure(stage8, 8, manifest.sourceCommit);

  const roleToName = new Map([
    ["CORPUS_SUMMARY", "dualdex-stage-07-corpus-evidence.json"],
    ["CORPUS_EXECUTION_RECEIPT", "dualdex-stage-07-corpus-execution.json"],
    ["STAGE_7_CLOSURE", "dualdex-stage-07-closure.json"],
    ["STAGE_8_CLOSURE", "dualdex-stage-08-closure.json"],
  ]);
  for (const [role, name] of roleToName) {
    const matches = Array.isArray(manifest.artifacts)
      ? manifest.artifacts.filter(artifact => artifact?.role === role)
      : [];
    requireCondition(matches.length === 1, `Published release evidence requires exactly one ${role}`);
    const digest = createHash("sha256").update(assets.get(name)).digest("hex");
    requireCondition(matches[0].sha256 === digest,
      `Published release evidence digest mismatch for ${role}`);
  }

  const expectedCacheDecision = manifest.cacheDecision?.type ?? "NOT_APPLICABLE";
  requireCondition(validation?.schemaVersion === 2 &&
    /^[0-9a-f]{40}$/.test(validation.releaseCommit ?? "") &&
    validation.evidenceSourceCommit === manifest.sourceCommit &&
    validation.scopeDecision === manifest.scopeDecision?.type &&
    validation.cacheDecision === expectedCacheDecision &&
    validation.generatorSchemaVersion === manifest.generator.schemaVersion &&
    validation.generatorSha256 === manifest.generator.sha256 &&
    validation.inputCount === 334 &&
    validation.corpusInputDigestSha256 === canonical.inputDigestSha256 &&
    validation.artifactCount === manifest.artifacts.length &&
    validation.stage7Closed === true && validation.stage8Closed === true,
  "Published release evidence validation does not match the manifest generator or prove zero-gap closure");
  return { releaseCommit: validation.releaseCommit };
}

function validatePublishedGenerator(generator, description) {
  requireCondition(generator?.name === "parser-cli" && generator.schemaVersion === 13 &&
    /^[0-9a-f]{64}$/.test(generator.sha256 ?? ""),
  `${description} generator is invalid`);
}

function validatePublishedSummary(summary, manifest, canonical) {
  requireCondition(summary?.schemaVersion === 2 && summary.sourceCommit === manifest.sourceCommit,
    "Published corpus summary source lineage is inconsistent");
  validatePublishedGenerator(summary.generator, "Published corpus summary");
  requireCondition(summary.generator.sha256 === manifest.generator.sha256 &&
    /^[0-9a-f]{64}$/.test(summary.rawReportSha256 ?? "") &&
    manifest.corpus?.inputCount === 334 && summary.inputCount === 334 &&
    summary.uniqueRomIdentities === 334 &&
    manifest.corpus?.inputDigestSha256 === canonical.inputDigestSha256 &&
    summary.corpusInputDigestSha256 === canonical.inputDigestSha256,
  "Published corpus summary does not match the canonical 334-input evidence");
  validateTerminalCounts(summary.outcomes, ["selected", "ambiguous", "noFamilyMatch", "errors"],
    "Published corpus summary terminal outcomes");
  requireCondition(summary.outcomes.errors === 0, "Published corpus summary contains parser errors");
  validateTerminalCounts(summary.dataCompatibility, ["complete", "partial", "unresolved", "errors"],
    "Published corpus summary compatibility outcomes");
  requireCondition(summary.dataCompatibility.errors === 0,
    "Published corpus summary contains compatibility errors");
  requireCondition(summary.catalogs?.catalogErrors === 0 && summary.catalogs.persistenceErrors === 0 &&
    summary.catalogs.materialized === summary.outcomes.selected &&
    summary.catalogs.persisted === summary.catalogs.materialized,
  "Published corpus summary does not prove catalog materialization and persistence");
  requireCondition(summary.privacy?.containsRomIdentity === false &&
    summary.privacy.containsRomName === false && summary.privacy.containsSourcePath === false &&
    summary.privacy.containsRomBytes === false,
  "Published corpus summary privacy declaration is unsafe");
}

function validateTerminalCounts(counts, fields, description) {
  requireCondition(fields.every(field => Number.isInteger(counts?.[field]) && counts[field] >= 0) &&
    counts?.total === 334 && fields.reduce((sum, field) => sum + counts[field], 0) === 334,
  `${description} must sum to 334`);
}

function validatePublishedReceipt(receipt, manifest, summary) {
  requireCondition(receipt?.schemaVersion === 1,
    "Published execution receipt schemaVersion must be 1");
  requireCondition(receipt.sourceCommit === manifest.sourceCommit,
    "Published execution receipt source commit is inconsistent");
  validatePublishedGenerator(receipt.generator, "Published execution receipt");
  requireCondition(receipt.generator.sha256 === manifest.generator.sha256 &&
    receipt.rawReportSha256 === summary.rawReportSha256 && receipt.inputCount === 334,
  "Published execution receipt does not match schema-2 release evidence");
}

function validatePublishedClosure(closure, stage, sourceCommit) {
  requireCondition(closure?.schemaVersion === 1 && closure.stage === stage &&
    closure.status === "CLOSED" && closure.sourceCommit === sourceCommit,
  `Stage ${stage} closure is missing or invalid`);
  requireCondition(closure.openBlockers === 0, `Stage ${stage} closure must have zero blockers`);
  requireCondition(closure.openReferrals === 0, `Stage ${stage} closure must have zero referrals`);
}

export function validateReleaseAssetSet({
  recordAssets,
  releaseAssets,
  readAsset,
  candidateTag,
}) {
  requireCondition(Array.isArray(recordAssets) && recordAssets.length > 0,
    "Promotion record requires the immutable release asset set");
  requireCondition(Array.isArray(releaseAssets), "Current immutable release asset set is missing");
  const normalize = (asset, description) => {
    requireCondition(asset && typeof asset.name === "string" && asset.name.length > 0,
      `${description} has an invalid name`);
    requireCondition(Number.isInteger(asset.id) && asset.id > 0, `${description} has an invalid asset ID`);
    return {
      name: asset.name,
      id: asset.id,
      sha256: normalizeSha256(asset.sha256, `${description} digest`),
    };
  };
  const expected = recordAssets.map((asset, index) => normalize(asset, `record asset ${index + 1}`))
    .sort((left, right) => left.name.localeCompare(right.name));
  const actual = releaseAssets.map((asset, index) => normalize(asset, `release asset ${index + 1}`))
    .sort((left, right) => left.name.localeCompare(right.name));
  requireCondition(new Set(expected.map(asset => asset.name)).size === expected.length,
    "Promotion record contains duplicate asset names");
  requireCondition(new Set(actual.map(asset => asset.name)).size === actual.length,
    "Release contains duplicate asset names");
  requireCondition(JSON.stringify(actual) === JSON.stringify(expected),
    "Current immutable release asset set differs from the promotion record");

  const requiredNames = [
    `DualDex-${candidateTag}.apk`,
    "provenance.json",
    "SHA256SUMS.txt",
    "compatibility-evidence.json",
    "canonical-corpus.json",
    "release-evidence-validation.json",
    "repository-policy.json",
    "dualdex-stage-07-corpus-evidence.json",
    "dualdex-stage-07-corpus-execution.json",
    "dualdex-stage-07-closure.json",
    "dualdex-stage-08-closure.json",
  ];
  requireCondition(requiredNames.every(name => expected.some(asset => asset.name === name)),
    "Immutable release asset set omits required release evidence, execution receipt, or zero-gap closure");
  const downloaded = new Map();
  for (const asset of expected) {
    const bytes = readAsset(asset.name);
    requireCondition(bytes != null, "Immutable release asset set is missing a downloaded asset");
    requireCondition(
      createHash("sha256").update(bytes).digest("hex").toUpperCase() === asset.sha256,
      "Immutable release asset set contains a local digest mismatch",
    );
    downloaded.set(asset.name, Buffer.from(bytes));
  }
  const evidence = validatePublishedEvidenceAssets(downloaded);
  return { assetCount: expected.length, releaseCommit: evidence.releaseCommit };
}

export function validateCandidatePromotion({
  record,
  provenance,
  provenanceSha256,
  checksumsPath,
  apkPath,
  certificateFingerprint,
  apkSignerVerificationPath,
  releaseAssets,
  assetsDirectory,
}) {
  requireCondition(record?.schema === 1, "Promotion record schema is unsupported");
  requireCondition(
    /^v\d+\.\d+\.\d+-rc\.[1-9]\d*(?:-hotfix\.[1-9]\d*)?$/.test(record.candidateTag ?? ""),
    "Promotion record does not name a release candidate",
  );
  requireCondition(provenance?.schema === 1, "Candidate provenance schema is unsupported");
  requireCondition(provenance.releaseKind === "candidate", "Provenance is not for a candidate");
  requireCondition(
    provenance.tag === record.candidateTag,
    "Promotion record and provenance candidate tags disagree",
  );
  requireCondition(
    provenance.applicationId === EXPECTED_APPLICATION_ID,
    "Candidate provenance has an unexpected application ID",
  );
  requireCondition(
    /^[0-9a-f]{40}$/.test(record.sourceCommit ?? "") && provenance.commit === record.sourceCommit,
    "Promotion record does not bind the candidate source commit",
  );
  requireCondition(
    normalizeSha256(record.candidateProvenanceSha256, "Promotion-record provenance hash") ===
      normalizeSha256(provenanceSha256, "Downloaded provenance hash"),
    "Promotion record does not bind the candidate provenance",
  );
  const assetSet = validateReleaseAssetSet({
    recordAssets: record.releaseAssets,
    releaseAssets,
    readAsset: name => {
      try {
        return readFileSync(join(assetsDirectory, name));
      } catch {
        return null;
      }
    },
    candidateTag: record.candidateTag,
  });
  requireCondition(assetSet.releaseCommit === provenance.commit,
    "Published release evidence validation does not bind the candidate commit");

  const actualApkSha256 = fileSha256(apkPath);
  const recordApkSha256 = normalizeSha256(record.apkSha256, "Promotion-record APK hash");
  const provenanceApkSha256 = normalizeSha256(provenance.apkSha256, "Provenance APK hash");
  const manifestApkSha256 = checksumFor(checksumsPath, basename(apkPath));
  requireCondition(
    [recordApkSha256, provenanceApkSha256, manifestApkSha256].every(
      (hash) => hash === actualApkSha256,
    ),
    "APK hash does not match the promotion record, provenance, and checksum manifest",
  );

  const pinnedCertificateSha256 = normalizeSha256(
    certificateFingerprint,
    "Pinned certificate",
  );
  const recordCertificateSha256 = normalizeSha256(
    record.validatedSignerSha256,
    "Promotion-record signer",
  );
  const provenanceCertificateSha256 = normalizeSha256(
    provenance.certificateSha256,
    "Provenance signer",
  );
  const actualCertificateSha256 = verifiedApkSigner(apkSignerVerificationPath);
  requireCondition(
    actualCertificateSha256 === pinnedCertificateSha256 &&
      recordCertificateSha256 === pinnedCertificateSha256 &&
      provenanceCertificateSha256 === pinnedCertificateSha256,
    "APK signer does not match the pinned production certificate",
  );

  const expectedReleaseRunUrl =
    `https://github.com/${provenance.repository}/actions/runs/${provenance.workflowRunId}`;
  requireCondition(
    record.releaseWorkflowRunUrl === expectedReleaseRunUrl,
    "Promotion record does not identify the signing workflow run from provenance",
  );
  requireCondition(record.releaseCiValidated === true, "Release CI validation is required");

  const artifactIdentity = {
    candidateTag: record.candidateTag,
    apkSha256: actualApkSha256,
    certificateSha256: pinnedCertificateSha256,
  };
  if (record.validationMode === "packaged-android-and-thor") {
    validatePackagedAndroidAndThor(record, provenance.repository, artifactIdentity);
  } else if (record.validationMode === "automated-passive-catalog") {
    validateAutomatedPassiveCatalog(record, provenance.repository);
  } else {
    throw new Error(`Unsupported candidate validation mode: ${record.validationMode ?? "<missing>"}`);
  }

  return {
    candidateTag: record.candidateTag,
    apkSha256: actualApkSha256,
    certificateSha256: pinnedCertificateSha256,
    validationMode: record.validationMode,
    sourceCommit: record.sourceCommit,
    candidateProvenanceSha256: normalizeSha256(provenanceSha256, "Downloaded provenance hash"),
    assetCount: assetSet.assetCount,
  };
}

function runCli() {
  const argumentsMap = parseArguments(process.argv.slice(2));
  const record = requireJson(requireArgument(argumentsMap, "record"), "Promotion record");
  const releaseAssets = requireJson(
    requireArgument(argumentsMap, "release-assets"),
    "Release asset metadata",
  );
  const assetsDirectory = requireArgument(argumentsMap, "assets-directory");
  if (argumentsMap["asset-set-only"] === "true") {
    const result = validateReleaseAssetSet({
      recordAssets: record.releaseAssets,
      releaseAssets,
      readAsset: name => {
        try {
          return readFileSync(join(assetsDirectory, name));
        } catch {
          return null;
        }
      },
      candidateTag: record.candidateTag,
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
    return;
  }

  const provenancePath = requireArgument(argumentsMap, "provenance");
  const result = validateCandidatePromotion({
    record,
    provenance: requireJson(provenancePath, "Candidate provenance"),
    provenanceSha256: fileSha256(provenancePath),
    checksumsPath: requireArgument(argumentsMap, "checksums"),
    apkPath: requireArgument(argumentsMap, "apk"),
    certificateFingerprint: readFileSync(
      requireArgument(argumentsMap, "certificate-fingerprint"),
      "utf8",
    ),
    apkSignerVerificationPath: requireArgument(
      argumentsMap,
      "apk-signer-verification",
    ),
    releaseAssets,
    assetsDirectory,
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    runCli();
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
