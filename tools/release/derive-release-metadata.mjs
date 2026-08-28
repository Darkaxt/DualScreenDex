import { appendFileSync, existsSync, readFileSync } from "node:fs";
import process from "node:process";
import { pathToFileURL } from "node:url";

const EXPECTED_APPLICATION_ID = "com.darkaxt.dualdex";
const FINAL_VERSION_QUALIFIER = 99;
const MAX_RC_NUMBER = FINAL_VERSION_QUALIFIER - 1;
const COMMIT = /^[0-9a-f]{40}$/;

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

function requireJson(path, description) {
  if (!path || !existsSync(path)) throw new Error(`${description} is missing: ${path ?? "<not provided>"}`);
  return JSON.parse(readFileSync(path, "utf8"));
}

function normalizeSha256(value, description) {
  const normalized = String(value ?? "")
    .replaceAll(":", "")
    .replaceAll(/\s/g, "")
    .toUpperCase();
  if (!/^[A-F0-9]{64}$/.test(normalized)) throw new Error(`${description} is not a SHA-256 fingerprint`);
  return normalized;
}

function deriveVersionCode(major, minor, patch, qualifier) {
  if (minor > 99 || patch > 99) throw new Error("Minor and patch versions must be between 0 and 99");
  const versionCode = major * 1_000_000 + minor * 10_000 + patch * 100 + qualifier;
  if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2_100_000_000) {
    throw new Error(`Derived Android versionCode is invalid: ${versionCode}`);
  }
  return versionCode;
}

function parseReleaseTag(tag) {
  const match = String(tag ?? "").match(
    /^v(\d+)\.(\d+)\.(\d+)(?:-rc\.([1-9]\d*)(?:-hotfix\.([1-9]\d*))?)?$/,
  );
  if (!match) return undefined;
  const [, majorText, minorText, patchText, rcText, hotfixText] = match;
  const major = Number(majorText);
  const minor = Number(minorText);
  const patch = Number(patchText);
  const isCandidate = rcText !== undefined;
  const rcNumber = isCandidate ? Number(rcText) : undefined;
  const hotfixNumber = hotfixText === undefined ? 0 : Number(hotfixText);
  const qualifier = isCandidate ? rcNumber + hotfixNumber : FINAL_VERSION_QUALIFIER;
  if (isCandidate && (rcNumber > MAX_RC_NUMBER || qualifier > MAX_RC_NUMBER)) {
    throw new Error(`RC number must be between 1 and ${MAX_RC_NUMBER}`);
  }
  return {
    tag,
    versionName: `${major}.${minor}.${patch}`,
    isCandidate,
    releaseVersionName: tag.slice(1),
    qualifier,
    versionCode: deriveVersionCode(major, minor, patch, qualifier),
  };
}

function validateReadyMarker(ready, versionName, certificateSha256, releaseEvidenceValidation) {
  if (ready.schema !== 1 || ready.stage !== 8 || ready.status !== "ready-for-github-signing") {
    throw new Error("Stage 8 release marker is not ready for GitHub signing");
  }
  if (ready.openV1LedgerItems !== 0) throw new Error("Stage 8 release marker still has open v1 ledger items");
  if (ready.applicationId !== EXPECTED_APPLICATION_ID) {
    throw new Error(`Unexpected application ID in release marker: ${ready.applicationId}`);
  }
  if (ready.versionName !== versionName) {
    throw new Error(`Tag version ${versionName} does not match release marker ${ready.versionName}`);
  }
  const markerCertificate = normalizeSha256(ready.productionCertificateSha256, "Release-marker certificate");
  if (markerCertificate !== certificateSha256) {
    throw new Error("Release marker and pinned certificate fingerprints disagree");
  }
  if (!releaseEvidenceValidation || releaseEvidenceValidation.schemaVersion !== 2) {
    throw new Error("Release evidence validation is missing or unsupported");
  }
  const closure = ready.qaClosure;
  if (closure?.schemaVersion !== 1 ||
      !COMMIT.test(closure.evidenceSourceCommit ?? "") ||
      closure.evidenceSourceCommit !== releaseEvidenceValidation.evidenceSourceCommit ||
      closure.stage7Closed !== true || closure.stage8Closed !== true ||
      releaseEvidenceValidation.stage7Closed !== true || releaseEvidenceValidation.stage8Closed !== true ||
      closure.openBlockers !== 0 || closure.openReferrals !== 0 ||
      releaseEvidenceValidation.inputCount !== 334) {
    throw new Error("Release readiness requires matching Stage 7 and Stage 8 closure with zero gaps");
  }
}

function validateFinalAuthorization({
  authorization,
  versionName,
  certificateSha256,
  candidatePromotion,
  repositoryState,
}) {
  if (!authorization || authorization.schema !== 2) {
    throw new Error("Final authorization is missing or has an unsupported schema");
  }
  if (authorization.versionName !== versionName) {
    throw new Error("Final authorization targets a different release version");
  }
  const sourceCandidate = parseReleaseTag(authorization.sourceCandidateTag);
  if (!sourceCandidate?.isCandidate || sourceCandidate.versionName !== versionName) {
    throw new Error("Final authorization does not name a matching release candidate");
  }
  const authorizedCandidateApkSha256 = normalizeSha256(
    authorization.githubSignedCandidateSha256,
    "Candidate APK hash",
  );
  const authorizedCertificate = normalizeSha256(authorization.validatedSignerSha256, "Validated signer");
  if (authorizedCertificate !== certificateSha256) {
    throw new Error("Final authorization was validated with a different signer");
  }
  if (!COMMIT.test(authorization.sourceCandidateCommit ?? "") ||
      authorization.sourceCandidateCommit !== repositoryState?.sourceCandidateCommit) {
    throw new Error("Final authorization does not match the candidate source commit");
  }
  if (!COMMIT.test(authorization.sourceCandidateTree ?? "") ||
      authorization.sourceCandidateTree !== repositoryState?.sourceCandidateTree) {
    throw new Error("Final authorization does not match the candidate source tree");
  }
  const provenanceSha256 = normalizeSha256(
    authorization.candidateProvenanceSha256,
    "Authorized candidate provenance",
  );
  const downloadedProvenanceSha256 = normalizeSha256(
    repositoryState?.candidateProvenanceSha256,
    "Downloaded candidate provenance",
  );
  if (candidatePromotion?.schema !== 1 ||
      candidatePromotion.candidateTag !== authorization.sourceCandidateTag ||
      candidatePromotion.sourceCommit !== authorization.sourceCandidateCommit ||
      normalizeSha256(candidatePromotion.candidateProvenanceSha256, "Promotion candidate provenance") !== provenanceSha256 ||
      downloadedProvenanceSha256 !== provenanceSha256) {
    throw new Error("Final authorization does not match the verified candidate provenance");
  }
  if (normalizeSha256(candidatePromotion.apkSha256, "Promotion candidate APK") !== authorizedCandidateApkSha256 ||
      normalizeSha256(repositoryState?.candidateApkSha256, "Downloaded candidate APK") !== authorizedCandidateApkSha256) {
    throw new Error("Final authorization does not match the verified candidate APK");
  }
  const productChanges = (repositoryState.changedPaths ?? []).filter(path =>
    !isAllowedFinalMetadataPath(path, authorization.sourceCandidateTag, versionName));
  if (productChanges.length > 0) {
    throw new Error(`Stable product source differs from validated candidate: ${productChanges.join(", ")}`);
  }

  const deviceValidated = authorization.avdValidated === true && authorization.thorValidated === true;
  const automatedPassiveChangeValidated =
    authorization.validationMode === "automated-passive-catalog" &&
    authorization.userAuthorizedAutomatedPromotion === true &&
    authorization.gameplayRuntimeChanged === false &&
    authorization.exactRomControls >= 5 &&
    authorization.catalogPersistenceValidated === true &&
    authorization.runtimeApiValidated === true &&
    authorization.webPresentationValidated === true &&
    authorization.releaseCiValidated === true;
  if (!deviceValidated && !automatedPassiveChangeValidated) {
    throw new Error(
      "Final authorization requires device validation or complete automated passive-catalog validation",
    );
  }
}

function isAllowedFinalMetadataPath(path, candidateTag, versionName) {
  const escapedTag = candidateTag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const escapedVersion = versionName.replaceAll(".", "\\.");
  return path === "release/v1-final-authorization.json" ||
    path === "release/v1-ready.json" ||
    new RegExp(`^release/RELEASE_NOTES_${escapedVersion}\\.md$`).test(path) ||
    new RegExp(`^release/candidate-promotions/${escapedTag}\\.json$`).test(path) ||
    /^docs\/reports\/candidate-promotions\/[A-Za-z0-9._-]+\.json$/.test(path);
}

export function deriveReleaseMetadata({
  tag,
  ready,
  certificateFingerprint,
  releaseEvidenceValidation,
  finalAuthorization,
  candidatePromotion,
  repositoryState,
  existingTags = [],
}) {
  const parsedTag = parseReleaseTag(tag);
  if (!parsedTag) throw new Error(`Unsupported release tag: ${tag ?? "<missing>"}`);
  const certificateSha256 = normalizeSha256(certificateFingerprint, "Pinned certificate");
  validateReadyMarker(ready, parsedTag.versionName, certificateSha256, releaseEvidenceValidation);
  if (!parsedTag.isCandidate) {
    validateFinalAuthorization({
      authorization: finalAuthorization,
      versionName: parsedTag.versionName,
      certificateSha256,
      candidatePromotion,
      repositoryState,
    });
  }

  const newerOrEqualTag = existingTags
    .filter(existingTag => existingTag !== tag)
    .map(existingTag => parseReleaseTag(existingTag))
    .filter(Boolean)
    .filter(existingTag => existingTag.versionName === parsedTag.versionName)
    .find(existingTag => existingTag.versionCode >= parsedTag.versionCode);
  if (newerOrEqualTag) {
    throw new Error(
      `Release versionCode ${parsedTag.versionCode} is not monotonic after ${newerOrEqualTag.tag} (${newerOrEqualTag.versionCode})`,
    );
  }

  return {
    tag,
    version_name: parsedTag.releaseVersionName,
    version_code: String(parsedTag.versionCode),
    release_kind: parsedTag.isCandidate ? "candidate" : "final",
    draft: String(parsedTag.isCandidate),
    prerelease: String(parsedTag.isCandidate),
    application_id: EXPECTED_APPLICATION_ID,
    certificate_sha256: certificateSha256,
  };
}

function runCli() {
  const argumentsMap = parseArguments(process.argv.slice(2));
  const ready = requireJson(argumentsMap.ready, "Stage 8 release marker");
  const releaseEvidenceValidation = requireJson(
    argumentsMap["release-evidence-validation"],
    "Release evidence validation",
  );
  const certificateFingerprint = readFileSync(argumentsMap["certificate-fingerprint"], "utf8");
  const finalAuthorization = argumentsMap["final-authorization"]
    ? requireJson(argumentsMap["final-authorization"], "Final authorization")
    : undefined;
  const candidatePromotion = argumentsMap["candidate-promotion"]
    ? requireJson(argumentsMap["candidate-promotion"], "Candidate promotion record")
    : undefined;
  const repositoryState = finalAuthorization ? {
    sourceCandidateCommit: argumentsMap["candidate-source-commit"],
    sourceCandidateTree: argumentsMap["candidate-source-tree"],
    candidateProvenanceSha256: argumentsMap["candidate-provenance-sha256"],
    candidateApkSha256: argumentsMap["candidate-apk-sha256"],
    changedPaths: readFileSync(argumentsMap["changed-paths"], "utf8")
      .split(/\r?\n/)
      .filter(Boolean),
  } : undefined;
  const existingTags = argumentsMap["existing-tags"]
    ? readFileSync(argumentsMap["existing-tags"], "utf8")
        .split(/\r?\n/)
        .map(line => line.trim())
        .filter(Boolean)
    : [];
  const metadata = deriveReleaseMetadata({
    tag: argumentsMap.tag,
    ready,
    certificateFingerprint,
    releaseEvidenceValidation,
    finalAuthorization,
    candidatePromotion,
    repositoryState,
    existingTags,
  });
  const output = Object.entries(metadata).map(([key, value]) => `${key}=${value}`).join("\n");
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `${output}\n`);
  else process.stdout.write(`${JSON.stringify(metadata, null, 2)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    runCli();
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
