import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

export function summarizeCompatibilityEvidence(report, sourceCommit) {
  assert(report?.schemaVersion === 12, "raw compatibility report schemaVersion must be 12");
  assert(Array.isArray(report.results) && report.results.length > 0, "raw compatibility report has no results");
  assert(/^[0-9a-f]{40}$/.test(sourceCommit ?? ""), "sourceCommit must be a full lowercase commit");

  const identities = report.results.map((row, index) => {
    const identity = row?.result?.sha256;
    const size = row?.result?.size;
    assert(/^[0-9a-f]{64}$/.test(identity ?? ""), `result ${index + 1} has no valid SHA-256 identity`);
    assert(Number.isInteger(size) && size > 0, `result ${index + 1} has no valid input size`);
    return `${identity}:${size}`;
  }).sort();
  const outcomes = countBy(report.results, row => row?.result?.status ?? "ERROR");
  const compatibility = countBy(report.results, row => row?.dataCompatibility ?? "ERROR");
  const persistenceErrors = report.results.filter(row => row?.persistenceError != null).length;
  const persisted = report.results.filter(row => row?.persistence != null).length;
  const materialized = report.results.filter(row => row?.catalog != null).length;

  return {
    schemaVersion: 1,
    sourceCommit,
    generator: "parser-cli",
    generatorSchemaVersion: report.schemaVersion,
    corpusInputDigestSha256: createHash("sha256").update(identities.join("\n")).digest("hex"),
    inputCount: report.results.length,
    uniqueRomIdentities: new Set(identities.map(value => value.slice(0, 64))).size,
    outcomes: {
      selected: outcomes.SELECTED ?? 0,
      ambiguous: outcomes.AMBIGUOUS ?? 0,
      noFamilyMatch: outcomes.NO_FAMILY_MATCH ?? 0,
      errors: outcomes.ERROR ?? 0,
    },
    dataCompatibility: {
      complete: compatibility.COMPLETE ?? 0,
      partial: compatibility.PARTIAL ?? 0,
      unresolved: compatibility.UNRESOLVED ?? 0,
      errors: compatibility.ERROR ?? 0,
    },
    catalogs: {
      materialized,
      persisted,
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
    `- Generator: \`${summary.generator}\` schema ${summary.generatorSchemaVersion}\n` +
    `- Privacy-safe corpus digest: \`${summary.corpusInputDigestSha256}\`\n` +
    `- Inputs: ${summary.inputCount} (${summary.uniqueRomIdentities} unique ROM identities)\n` +
    `- Outcomes: ${summary.outcomes.selected} selected, ${summary.outcomes.ambiguous} ambiguous, ` +
    `${summary.outcomes.noFamilyMatch} without a family match, ${summary.outcomes.errors} errors\n` +
    `- Catalogs: ${summary.catalogs.materialized} materialized, ${summary.catalogs.persisted} persisted and reopened, ` +
    `${summary.catalogs.persistenceErrors} persistence errors\n\n` +
    `The published summary contains no ROM identity, ROM name, source path, or ROM bytes. ` +
    `The aggregate digest binds the sorted input identities and sizes without publishing an individual identity.\n`;
}

function countBy(values, key) {
  const counts = {};
  for (const value of values) {
    const resolved = key(value);
    counts[resolved] = (counts[resolved] ?? 0) + 1;
  }
  return counts;
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
  const report = JSON.parse(readFileSync(resolve(options.raw), "utf8"));
  const summary = summarizeCompatibilityEvidence(report, options["source-commit"]);
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
