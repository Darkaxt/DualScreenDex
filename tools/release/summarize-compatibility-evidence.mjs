import { createHash } from "node:crypto";
import { createReadStream, readFileSync, writeFileSync } from "node:fs";
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
  return summarizeParsedReport(
    report,
    receipt,
    canonicalCorpus,
    createHash("sha256").update(bytes).digest("hex"),
  );
}

export async function summarizeCompatibilityEvidenceFile(rawPath, receipt, canonicalCorpus) {
  const rawDigest = createHash("sha256");
  const rows = [];
  let prefix = Buffer.alloc(0);
  let metadata;
  let finished = false;
  let rootClosed = false;
  let arrayState = "VALUE_OR_END";
  let rowParts = [];
  let rowDepth = 0;
  let rowStart = -1;
  let inString = false;
  let escaped = false;

  const consume = chunk => {
    let index = 0;
    while (index < chunk.length) {
      const byte = chunk[index];
      if (finished) {
        if (isJsonWhitespace(byte)) {
          index += 1;
          continue;
        }
        assert(byte === 0x7d && !rootClosed, "raw compatibility report has trailing content");
        rootClosed = true;
        index += 1;
        continue;
      }
      if (rowStart < 0) {
        if (isJsonWhitespace(byte)) {
          index += 1;
          continue;
        }
        if (arrayState === "COMMA_OR_END") {
          if (byte === 0x2c) {
            arrayState = "VALUE";
            index += 1;
            continue;
          }
          if (byte === 0x5d) {
            finished = true;
            index += 1;
            continue;
          }
          throw new Error(`result ${rows.length + 1} has no valid array separator`);
        }
        if (byte === 0x5d) {
          assert(arrayState === "VALUE_OR_END", "raw compatibility report has a trailing comma");
          finished = true;
          index += 1;
          continue;
        }
        assert(byte === 0x7b, `result ${rows.length + 1} has no valid array separator`);
        rowStart = index;
        rowDepth = 0;
        inString = false;
        escaped = false;
      }

      if (inString) {
        if (escaped) escaped = false;
        else if (byte === 0x5c) escaped = true;
        else if (byte === 0x22) inString = false;
      } else if (byte === 0x22) {
        inString = true;
      } else if (byte === 0x7b) {
        rowDepth += 1;
      } else if (byte === 0x7d) {
        rowDepth -= 1;
        if (rowDepth === 0) {
          rowParts.push(chunk.subarray(rowStart, index + 1));
          let parsed;
          try {
            parsed = JSON.parse(Buffer.concat(rowParts).toString("utf8"));
          } catch {
            throw new Error(`result ${rows.length + 1} is not valid JSON`);
          }
          rows.push(compactEvidenceRow(parsed));
          rowParts = [];
          rowStart = -1;
          arrayState = "COMMA_OR_END";
        }
      }
      index += 1;
    }
    if (rowStart >= 0) {
      rowParts.push(chunk.subarray(rowStart));
      rowStart = 0;
    }
  };

  for await (const chunk of createReadStream(rawPath, { highWaterMark: 1024 * 1024 })) {
    rawDigest.update(chunk);
    if (metadata == null) {
      prefix = Buffer.concat([prefix, chunk]);
      const resultsStart = findResultsArrayStart(prefix);
      if (resultsStart == null) {
        assert(prefix.length <= 16 * 1024 * 1024, "raw compatibility report results header is unbounded");
        continue;
      }
      try {
        metadata = JSON.parse(Buffer.concat([
          prefix.subarray(0, resultsStart),
          Buffer.from("[]}"),
        ]).toString("utf8"));
      } catch {
        throw new Error("raw compatibility report header is invalid");
      }
      consume(prefix.subarray(resultsStart + 1));
      prefix = Buffer.alloc(0);
    } else {
      consume(chunk);
    }
  }

  assert(metadata != null, "raw compatibility report has no results array");
  assert(rowStart < 0 && rowParts.length === 0, "raw compatibility report ends inside a result");
  assert(finished, "raw compatibility report results array is unterminated");
  assert(rootClosed, "raw compatibility report root terminator is missing");
  return summarizeParsedReport(
    { ...metadata, results: rows },
    receipt,
    canonicalCorpus,
    rawDigest.digest("hex"),
  );
}

function summarizeParsedReport(report, receipt, canonicalCorpus, rawReportSha256) {
  validateReceipt(receipt, rawReportSha256);
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
  const uniqueRomIdentityCount = new Set(identities.map(value => value.slice(0, 64))).size;
  assert(inputDigest === canonicalCorpus.inputDigestSha256,
    `raw report input digest ${inputDigest} does not match canonical corpus ${canonicalCorpus.inputDigestSha256}`);
  assert(uniqueRomIdentityCount === canonicalCorpus.uniqueRomIdentityCount,
    "raw report unique ROM identity count does not match canonical corpus");

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
    uniqueRomIdentities: uniqueRomIdentityCount,
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

function compactEvidenceRow(row) {
  return {
    result: row?.result == null ? null : {
      sha256: row.result.sha256,
      size: row.result.size,
      status: row.result.status,
    },
    catalog: row?.catalog == null ? null : true,
    catalogError: row?.catalogError,
    persistence: row?.persistence == null ? null : true,
    persistenceError: row?.persistenceError,
    error: row?.error,
    dataCompatibility: row?.dataCompatibility,
  };
}

function findResultsArrayStart(buffer) {
  const marker = Buffer.from("\"results\"");
  let offset = 0;
  while (offset < buffer.length) {
    const markerIndex = buffer.indexOf(marker, offset);
    if (markerIndex < 0) return null;
    let cursor = markerIndex + marker.length;
    while (cursor < buffer.length && isJsonWhitespace(buffer[cursor])) cursor += 1;
    if (cursor >= buffer.length) return null;
    if (buffer[cursor] !== 0x3a) {
      offset = markerIndex + marker.length;
      continue;
    }
    cursor += 1;
    while (cursor < buffer.length && isJsonWhitespace(buffer[cursor])) cursor += 1;
    if (cursor >= buffer.length) return null;
    if (buffer[cursor] === 0x5b) return cursor;
    offset = markerIndex + marker.length;
  }
  return null;
}

function isJsonWhitespace(byte) {
  return byte === 0x20 || byte === 0x09 || byte === 0x0a || byte === 0x0d;
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
    `- Data compatibility: ${summary.dataCompatibility.complete} complete, ${summary.dataCompatibility.partial} partial, ` +
    `${summary.dataCompatibility.unresolved} unresolved, ${summary.dataCompatibility.errors} errors\n` +
    `- Catalogs: ${summary.catalogs.materialized} materialized, ${summary.catalogs.persisted} persisted and reopened, ` +
    `${summary.catalogs.catalogErrors} catalog errors, ${summary.catalogs.persistenceErrors} persistence errors\n\n` +
    `The published summary contains no ROM identity, ROM name, source path, or ROM bytes. ` +
    `The aggregate digest binds the sorted input identities and sizes as a multiset without publishing an individual identity.\n`;
}

function validateReceipt(receipt, rawReportSha256) {
  assert(receipt?.schemaVersion === 1, "execution receipt schemaVersion must be 1");
  assert(COMMIT.test(receipt.sourceCommit ?? ""), "execution receipt source commit is invalid");
  assert(receipt.generator?.name === "parser-cli", "execution receipt generator must be parser-cli");
  assert(receipt.generator?.schemaVersion === RAW_REPORT_SCHEMA_VERSION,
    `execution receipt generator schemaVersion must be ${RAW_REPORT_SCHEMA_VERSION}`);
  assert(SHA256.test(receipt.generator?.sha256 ?? ""), "execution receipt generator digest is invalid");
  assert(SHA256.test(receipt.rawReportSha256 ?? ""), "execution receipt raw report digest is invalid");
  assert(receipt.rawReportSha256 === rawReportSha256,
    "execution receipt raw report digest does not match report bytes");
  assert(Number.isInteger(receipt.inputCount) && receipt.inputCount > 0,
    "execution receipt input count must be positive");
}

function validateCanonicalCorpus(canonicalCorpus) {
  assert(canonicalCorpus?.schemaVersion === 2, "canonical corpus schemaVersion must be 2");
  assert(Number.isInteger(canonicalCorpus.inputCount) && canonicalCorpus.inputCount > 0,
    "canonical corpus input count must be positive");
  assert(Number.isInteger(canonicalCorpus.uniqueRomIdentityCount) &&
    canonicalCorpus.uniqueRomIdentityCount > 0 &&
    canonicalCorpus.uniqueRomIdentityCount <= canonicalCorpus.inputCount,
  "canonical corpus unique ROM identity count is invalid");
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

async function main(arguments_) {
  const options = parseArguments(arguments_);
  const receipt = JSON.parse(readFileSync(resolve(options.receipt), "utf8"));
  const canonicalCorpus = JSON.parse(readFileSync(resolve(options["canonical-corpus"]), "utf8"));
  const summary = await summarizeCompatibilityEvidenceFile(resolve(options.raw), receipt, canonicalCorpus);
  writeFileSync(resolve(options.json), `${JSON.stringify(summary, null, 2)}\n`);
  writeFileSync(resolve(options.markdown), renderCompatibilityEvidenceMarkdown(summary));
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main(process.argv.slice(2)).catch(failure => {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  });
}
