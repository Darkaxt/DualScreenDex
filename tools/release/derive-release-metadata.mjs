import { appendFileSync, existsSync, readFileSync } from "node:fs";
import process from "node:process";
import { pathToFileURL } from "node:url";

const EXPECTED_APPLICATION_ID = "com.darkaxt.dualdex";
const FINAL_VERSION_QUALIFIER = 99;
const MAX_RC_NUMBER = FINAL_VERSION_QUALIFIER - 1;

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
  if (!path || !existsSync(path)) {
    throw new Error(`${description} is missing: ${path ?? "<not provided>"}`);
  }
  return JSON.parse(readFileSync(path, "utf8"));
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

function deriveVersionCode(major, minor, patch, qualifier) {
  if (minor > 99 || patch > 99) {
    throw new Error("Minor and patch versions must be between 0 and 99");
  }
  const versionCode = major * 1_000_000 + minor * 10_000 + patch * 100 + qualifier;
  if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2_100_000_000) {
    throw new Error(`Derived Android versionCode is invalid: ${versionCode}`);
  }
  return versionCode;
}

function parseReleaseTag(tag) {
  const match = /^v(\d+)\.(\d+)\.(\d+)(?:-rc\.([1-9]\d*))?$/.exec(tag ?? "");
  if (!match) return undefined;

  const [, majorText, minorText, patchText, rcText] = match;
  const major = Number(majorText);
  const minor = Number(minorText);
  const patch = Number(patchText);
  const isCandidate = rcText !== undefined;
  const qualifier = isCandidate ? Number(rcText) : FINAL_VERSION_QUALIFIER;
  if (isCandidate && qualifier > MAX_RC_NUMBER) {
    throw new Error(`RC number must be between 1 and ${MAX_RC_NUMBER}`);
  }
  return {
    tag,
    versionName: `${major}.${minor}.${patch}`,
    isCandidate,
    qualifier,
    versionCode: deriveVersionCode(major, minor, patch, qualifier),
  };
}

function validateReadyMarker(ready, versionName, certificateSha256) {
  if (ready.schema !== 1 || ready.stage !== 7 || ready.status !== "ready-for-github-signing") {
    throw new Error("Stage 7 release marker is not ready for GitHub signing");
  }
  if (ready.openV1LedgerItems !== 0) {
    throw new Error("Stage 7 release marker still has open v1 ledger items");
  }
  if (ready.applicationId !== EXPECTED_APPLICATION_ID) {
    throw new Error(`Unexpected application ID in release marker: ${ready.applicationId}`);
  }
  if (ready.versionName !== versionName) {
    throw new Error(`Tag version ${versionName} does not match release marker ${ready.versionName}`);
  }
  const markerCertificate = normalizeSha256(
    ready.productionCertificateSha256,
    "Release-marker certificate",
  );
  if (markerCertificate !== certificateSha256) {
    throw new Error("Release marker and pinned certificate fingerprints disagree");
  }
}

function validateFinalAuthorization(authorization, versionName, certificateSha256) {
  if (!authorization || authorization.schema !== 1) {
    throw new Error("Final authorization is missing or has an unsupported schema");
  }
  if (authorization.versionName !== versionName) {
    throw new Error("Final authorization targets a different release version");
  }
  const sourceCandidate = parseReleaseTag(authorization.sourceCandidateTag);
  if (!sourceCandidate?.isCandidate || sourceCandidate.versionName !== versionName) {
    throw new Error("Final authorization does not name a matching release candidate");
  }
  normalizeSha256(authorization.githubSignedCandidateSha256, "Candidate APK hash");
  const authorizedCertificate = normalizeSha256(
    authorization.validatedSignerSha256,
    "Validated signer",
  );
  if (authorizedCertificate !== certificateSha256) {
    throw new Error("Final authorization was validated with a different signer");
  }
  if (authorization.avdValidated !== true || authorization.thorValidated !== true) {
    throw new Error("Final authorization requires successful AVD and Thor validation");
  }
}

export function deriveReleaseMetadata({
  tag,
  ready,
  certificateFingerprint,
  finalAuthorization,
  existingTags = [],
}) {
  const parsedTag = parseReleaseTag(tag);
  if (!parsedTag) {
    throw new Error(`Unsupported release tag: ${tag ?? "<missing>"}`);
  }

  const certificateSha256 = normalizeSha256(certificateFingerprint, "Pinned certificate");
  validateReadyMarker(ready, parsedTag.versionName, certificateSha256);

  if (!parsedTag.isCandidate) {
    validateFinalAuthorization(finalAuthorization, parsedTag.versionName, certificateSha256);
  }

  const newerOrEqualTag = existingTags
    .filter((existingTag) => existingTag !== tag)
    .map((existingTag) => parseReleaseTag(existingTag))
    .filter(Boolean)
    .filter((existingTag) => existingTag.versionName === parsedTag.versionName)
    .find((existingTag) => existingTag.versionCode >= parsedTag.versionCode);
  if (newerOrEqualTag) {
    throw new Error(
      `Release versionCode ${parsedTag.versionCode} is not monotonic after ${newerOrEqualTag.tag} (${newerOrEqualTag.versionCode})`,
    );
  }

  return {
    tag,
    version_name: parsedTag.isCandidate
      ? `${parsedTag.versionName}-rc.${parsedTag.qualifier}`
      : parsedTag.versionName,
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
  const ready = requireJson(argumentsMap.ready, "Stage 7 release marker");
  const certificateFingerprint = readFileSync(
    argumentsMap["certificate-fingerprint"],
    "utf8",
  );
  const finalAuthorization = argumentsMap["final-authorization"]
    ? requireJson(argumentsMap["final-authorization"], "Final authorization")
    : undefined;
  const existingTags = argumentsMap["existing-tags"]
    ? readFileSync(argumentsMap["existing-tags"], "utf8")
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
    : [];
  const metadata = deriveReleaseMetadata({
    tag: argumentsMap.tag,
    ready,
    certificateFingerprint,
    finalAuthorization,
    existingTags,
  });

  const output = Object.entries(metadata)
    .map(([key, value]) => `${key}=${value}`)
    .join("\n");
  if (process.env.GITHUB_OUTPUT) {
    appendFileSync(process.env.GITHUB_OUTPUT, `${output}\n`);
  } else {
    process.stdout.write(`${JSON.stringify(metadata, null, 2)}\n`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    runCli();
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
