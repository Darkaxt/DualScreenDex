import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const COMMIT = /^[0-9a-f]{40}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const RAW_REPORT_SCHEMA_VERSION = 13;
const TERMINAL_OUTCOMES = new Set(["SELECTED", "AMBIGUOUS", "NO_FAMILY_MATCH", "ERROR"]);
const COMPATIBILITY_OUTCOMES = new Set(["COMPLETE", "PARTIAL", "UNRESOLVED", "ERROR"]);

export function summarizeCompatibilityEvidence(rawReportBytes, receipt, canonicalCorpus) {
  const bytes = Buffer.from(rawReportBytes);
  const report = JSON.parse(bytes.toString("utf8"));
  validateReceipt(receipt, bytes);
  validateCanonicalCorpus(canonicalCorpus);

  assert(report?.schemaVersion === RAW_REPORT_SCHEMA_VERSION,
    `raw compatibility report schemaVersion must be ${RAW_REPORT_SCHEMA_VERSION}`);
  assert(report.execution?.sourceCommit === receipt.sourceCommit,
    "raw report source commit does not match execution receipt");
  assert(report.execution?.generatorSha256 === receipt.generator.sha256,
    "raw report generator digest does not match execution receipt");
  assert(Array.isArray(report.results) && report.results.length > 0, "raw compatibility report has no results");
  assert(report.results.length === receipt.inputCount, "execution receipt input count does not match raw report");
  assert(report.results.length === canonicalCorpus.inputCount, "raw report input count does not match canonical corpus");

  const identities = report.results.map((row, index) => {
    const identity = row?.result?.sha256;
    const size = row?.result?.size;
    assert(SHA256.test(identity ?? ""), `result ${index + 1} has no valid SHA-256 identity`);
    assert(Number.isInteger(size) && size > 0, `result ${index + 1} has no valid input size`);
    return `${identity}:${size}`;
  }).sort();
  const inputDigest = createHash("sha256").update(identities.join("\n")).digest("hex");
  assert(inputDigest === canonicalCorpus.inputDigestSha256,
    "raw report input digest does not match canonical corpus");

  const outcomes = countStrict(report.results, row => row?.result?.status, TERMINAL_OUTCOMES,
    "terminal parser outcome");
  const compatibility = countStrict(report.results, row => row?.dataCompatibility, COMPATIBILITY_OUTCOMES,
    "data compatibility outcome");
  const sourceErrors = report.results.filter(row => row?.error != null).length;
  const parserErrors = outcomes.ERROR ?? 0;
  const catalogErrors = report.results.filter(row => row?.catalogError != null).length;
  const compatibilityErrors = compatibility.ERROR ?? 0;
  const persistenceErrors = report.results.filter(row => row?.persistenceError != null).length;
  const persisted = report.results.filter(row => row?.persistence != null).length;
  const materialized = report.results.filter(row => row?.catalog != null).length;
  const selectedWithoutCatalog = report.results.filter(row =>
    row?.result?.status === "SELECTED" && row?.catalog == null).length;

  assert(sourceErrors === 0, "raw corpus evidence contains source errors");
  assert(parserErrors === 0, "raw corpus evidence contains parser errors");
  assert(catalogErrors === 0, "raw corpus evidence contains catalog errors");
  assert(compatibilityErrors === 0, "raw corpus evidence contains compatibility errors");
  assert(persistenceErrors === 0, "raw corpus evidence contains persistence errors");
  assert(selectedWithoutCatalog === 0, "selected corpus inputs are missing materialized catalogs");
  assert(persisted === materialized, "not every materialized catalog was persisted and reopened");

  return {
    schemaVersion: 2,
    sourceCommit: receipt.sourceCommit,
    generator: { ...receipt.generator },
    rawReportSha256: receipt.rawReportSha256,
    corpusInputDigestSha256: inputDigest,
    inputCount: report.results.length,
    uniqueRomIdentities: new Set(identities.map(value => value.slice(0, 64))).size,
    outcomes: {
      selected: outcomes.SELECTED ?? 0,
      ambiguous: outcomes.AMBIGUOUS ?? 0,
      noFamilyMatch: outcomes.NO_FAMILY_MATCH ?? 0,
      total: sumCounts(outcomes),
      errors: parserErrors + sourceErrors,
    },
    dataCompatibility: {
      complete: compatibility.COMPLETE ?? 0,
      partial: compatibility.PARTIAL ?? 0,
      unresolved: compatibility.UNRESOLVED ?? 0,
      total: sumCounts(compatibility),
      errors: compatibilityErrors,
    },
    catalogs: {
      materialized,
      persisted,
      catalogErrors,
      persistenceErrors,
    },
    privacy: {
      containsRomIdentity: false,
      containsRomName: false,
      containsSourcePath: false,
      containsRomBytes: false,
    },
  };
}

export function renderCompatibilityEvidenceMarkdown(summary) {
  return `# Stage 7 Source-Bound Corpus Evidence\n\n` +
    `- Source commit: \`${summary.sourceCommit}\`\n` +
    `- Generator: \`${summary.generator.name}\` schema ${summary.generator.schemaVersion}\n` +
    `- Generator digest: \`${summary.generator.sha256}\`\n` +
    `- Raw-report digest: \`${summary.rawReportSha256}\`\n` +
    `- Privacy-safe corpus digest: \`${summary.corpusInputDigestSha256}\`\n` +
    `- Inputs: ${summary.inputCount} (${summary.uniqueRomIdentities} unique ROM identities)\n` +
    `- Outcomes: ${summary.outcomes.selected} selected, ${summary.outcomes.ambiguous} ambiguous, ` +
    `${summary.outcomes.noFamilyMatch} without a family match, ${summary.outcomes.errors} errors\n` +
    `- Catalogs: ${summary.catalogs.materialized} materialized, ${summary.catalogs.persisted} persisted and reopened, ` +
    `${summary.catalogs.catalogErrors} catalog errors, ${summary.catalogs.persistenceErrors} persistence errors\n\n` +
    `The published summary contains no ROM identity, ROM name, source path, or ROM bytes. ` +
    `The aggregate digest binds the sorted input identities and sizes without publishing an individual identity.\n`;
}

function validateReceipt(receipt, rawReportBytes) {
  assert(receipt?.schemaVersion === 1, "execution receipt schemaVersion must be 1");
  assert(COMMIT.test(receipt.sourceCommit ?? ""), "execution receipt source commit is invalid");
  assert(receipt.generator?.name === "parser-cli", "execution receipt generator must be parser-cli");
  assert(receipt.generator?.schemaVersion === RAW_REPORT_SCHEMA_VERSION,
    `execution receipt generator schemaVersion must be ${RAW_REPORT_SCHEMA_VERSION}`);
  assert(SHA256.test(receipt.generator?.sha256 ?? ""), "execution receipt generator digest is invalid");
  assert(SHA256.test(receipt.rawReportSha256 ?? ""), "execution receipt raw report digest is invalid");
  assert(receipt.rawReportSha256 === createHash("sha256").update(rawReportBytes).digest("hex"),
    "execution receipt raw report digest does not match report bytes");
  assert(Number.isInteger(receipt.inputCount) && receipt.inputCount > 0,
    "execution receipt input count must be positive");
}

function validateCanonicalCorpus(canonicalCorpus) {
  assert(canonicalCorpus?.schemaVersion === 1, "canonical corpus schemaVersion must be 1");
  assert(Number.isInteger(canonicalCorpus.inputCount) && canonicalCorpus.inputCount > 0,
    "canonical corpus input count must be positive");
  assert(SHA256.test(canonicalCorpus.inputDigestSha256 ?? ""),
    "canonical corpus input digest must be a lowercase SHA-256");
}

function countStrict(values, key, allowed, description) {
  const counts = {};
  values.forEach((value, index) => {
    const resolved = key(value);
    assert(allowed.has(resolved), `result ${index + 1} has no valid ${description}`);
    counts[resolved] = (counts[resolved] ?? 0) + 1;
  });
  return counts;
}

function sumCounts(counts) {
  return Object.values(counts).reduce((total, count) => total + count, 0);
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
  const raw = readFileSync(resolve(options.raw));
  const receipt = JSON.parse(readFileSync(resolve(options.receipt), "utf8"));
  const canonicalCorpus = JSON.parse(readFileSync(resolve(options["canonical-corpus"]), "utf8"));
  const summary = summarizeCompatibilityEvidence(raw, receipt, canonicalCorpus);
  writeFileSync(resolve(options.json), `${JSON.stringify(summary, null, 2)}\n`);
  writeFileSync(resolve(options.markdown), renderCompatibilityEvidenceMarkdown(summary));
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    main(process.argv.slice(2));
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
