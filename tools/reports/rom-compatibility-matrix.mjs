#!/usr/bin/env node

import { createHash } from "node:crypto";
import { createReadStream, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPORT_SCHEMA = 16;
const FAMILY_LABELS = new Map([
  ["RED_BLUE", "Red/Blue family"],
  ["YELLOW", "Yellow family"],
  ["GOLD_SILVER", "Gold/Silver family"],
  ["CRYSTAL", "Crystal family"],
  ["RUBY_SAPPHIRE", "Ruby/Sapphire family"],
  ["EMERALD", "Emerald family"],
  ["FIRERED_LEAFGREEN", "FireRed/LeafGreen family"],
]);
const FAMILY_ORDER = [...FAMILY_LABELS.keys()];
const FAMILY_GENERATIONS = new Map([
  ["RED_BLUE", 1], ["YELLOW", 1],
  ["GOLD_SILVER", 2], ["CRYSTAL", 2],
  ["RUBY_SAPPHIRE", 3], ["EMERALD", 3], ["FIRERED_LEAFGREEN", 3],
]);
const CAPABILITIES = [
  ["SPECIES_CATALOG", "Species", "Species records"],
  ["SPECIES_NAMES", "Names", "ROM-native species names"],
  ["SPECIES_TYPES", "Types", "Species type assignments"],
  ["TYPE_CHART", "Chart", "Type matchup chart"],
  ["BASE_STATS", "Stats", "Species base stats"],
  ["SPRITES", "Sprites", "Species sprites"],
  ["POKEDEX_DESCRIPTIONS", "Dex text", "ROM-native Pokédex descriptions"],
  ["EVOLUTIONS", "Evos", "Evolution relationships"],
  ["MOVE_CATALOG", "Moves", "Move records and names"],
  ["MOVE_DETAILS", "Move data", "Move power, accuracy, PP, type, and category"],
  ["MOVE_DESCRIPTIONS", "Move text", "ROM-native move descriptions"],
  ["LEARNSETS", "Learnsets", "Level-up learnsets"],
  ["EGG_MOVES", "Egg", "Egg moves"],
  ["MACHINE_MOVES", "Machines", "TM/HM compatibility"],
  ["TUTOR_MOVES", "Tutors", "Tutor moves"],
  ["ABILITIES", "Abilities", "Ability records and names"],
  ["ABILITY_DESCRIPTIONS", "Ability text", "ROM-native ability descriptions"],
  ["ABILITY_MECHANICS", "Ability logic", "Structurally proven ability mechanics"],
  ["AREA_ENCOUNTERS", "Encounters", "Area encounter data"],
  ["TYPE_PRESENTATION", "Type UI", "ROM-derived type presentation"],
  ["BALL_CATALOG", "Balls", "Capture-ball catalog"],
  ["WORLD_MAP", "World map", "World-map data"],
  ["LOCAL_MAP", "Local maps", "Local-map data"],
  ["NATURES", "Natures", "Nature data"],
];
const CAPABILITY_NAMES = new Set(CAPABILITIES.map(([name]) => name));
const LOCALIZED_CAPABILITY_FALLBACKS = new Map([
  ["SPECIES_NAMES", "SPECIES_NAMES"],
  ["POKEDEX_DESCRIPTIONS", "SPECIES_DESCRIPTIONS"],
  ["MOVE_DESCRIPTIONS", "MOVE_DESCRIPTIONS"],
  ["ABILITY_DESCRIPTIONS", "ABILITY_DESCRIPTIONS"],
]);
const VALID_CAPABILITY_STATUSES = new Set([
  "AVAILABLE", "PARTIAL", "AMBIGUOUS", "NOT_FOUND", "NOT_APPLICABLE",
]);
const VALID_OUTCOMES = new Set(["SELECTED", "AMBIGUOUS", "NO_FAMILY_MATCH", "ERROR"]);

export async function streamCompatibilityReport(rawPath, onRow) {
  const digest = createHash("sha256");
  let prefix = Buffer.alloc(0);
  let header;
  let finished = false;
  let rootClosed = false;
  let arrayState = "VALUE_OR_END";
  let rowParts = [];
  let rowDepth = 0;
  let rowStart = -1;
  let inString = false;
  let escaped = false;
  let rowCount = 0;

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
          throw new Error(`result ${rowCount + 1} has no valid array separator`);
        }
        if (byte === 0x5d) {
          assert(arrayState === "VALUE_OR_END", "raw compatibility report has a trailing comma");
          finished = true;
          index += 1;
          continue;
        }
        assert(byte === 0x7b, `result ${rowCount + 1} has no valid array separator`);
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
          let row;
          try {
            row = JSON.parse(Buffer.concat(rowParts).toString("utf8"));
          } catch {
            throw new Error(`result ${rowCount + 1} is not valid JSON`);
          }
          onRow(row, rowCount);
          rowCount += 1;
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

  for await (const chunk of createReadStream(rawPath, { highWaterMark: 16 * 1024 * 1024 })) {
    digest.update(chunk);
    if (header == null) {
      prefix = Buffer.concat([prefix, chunk]);
      const resultsStart = findResultsArrayStart(prefix);
      if (resultsStart == null) {
        assert(prefix.length <= 16 * 1024 * 1024, "raw compatibility report results header is unbounded");
        continue;
      }
      try {
        header = JSON.parse(Buffer.concat([
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

  assert(header != null, "raw compatibility report has no results array");
  assert(rowStart < 0 && rowParts.length === 0, "raw compatibility report ends inside a result");
  assert(finished && rootClosed, "raw compatibility report is not terminated");
  return {
    schemaVersion: header.schemaVersion,
    execution: header.execution,
    rowCount,
    rawReportSha256: digest.digest("hex"),
  };
}

export function buildRomCompatibilityMatrix(rows, {
  rowSourceCommit,
  closureSourceCommit,
  expectedInputCount = rows.length,
  expectedSummary,
} = {}) {
  assert(Array.isArray(rows), "rows must be an array");
  assert(rows.length === expectedInputCount, `expected ${expectedInputCount} inputs, got ${rows.length}`);
  const selected = [];
  const unclassified = [];
  const outcomes = Object.fromEntries([...VALID_OUTCOMES].map(outcome => [outcome, 0]));

  rows.forEach((entry, index) => {
    const outcome = entry?.result?.status;
    assert(VALID_OUTCOMES.has(outcome), `result ${index + 1} has no valid parser outcome`);
    outcomes[outcome] += 1;
    if (outcome !== "SELECTED") {
      unclassified.push({
        name: publicRomName(entry?.displayName, index),
        platform: entry?.result?.header?.platform ?? "UNKNOWN",
        outcome,
      });
      return;
    }

    const family = entry.result.selectedFamily;
    assert(FAMILY_LABELS.has(family), `result ${index + 1} has an unknown selected family`);
    const matchingProbes = entry.result.probes?.filter(probe =>
      probe?.family === family && probe?.hardGatePassed === true && probe?.resolvedLayout != null) ?? [];
    assert(matchingProbes.length === 1, `result ${index + 1} has no unique selected layout`);
    const selectedProbe = matchingProbes[0];
    const layout = selectedProbe.resolvedLayout;
    const generation = layout.generation ?? FAMILY_GENERATIONS.get(family);
    assert(generation === FAMILY_GENERATIONS.get(family), `result ${index + 1} has an inconsistent generation`);
    const manifest = layout.languageManifest;
    assert(manifest?.status === "RESOLVED" || manifest?.status === "UNKNOWN",
      `result ${index + 1} has no resolved or unknown language manifest`);
    const languageCount = manifest.status === "RESOLVED" ? manifest.projections?.length : null;
    assert(languageCount == null || Number.isInteger(languageCount) && languageCount > 0,
      `result ${index + 1} has an invalid language projection count`);

    const capabilityMap = new Map();
    const addEvidence = evidence => {
      assert(CAPABILITY_NAMES.has(evidence.capability),
        `result ${index + 1} has an unknown capability ${evidence.capability}`);
      assert(VALID_CAPABILITY_STATUSES.has(evidence.status),
        `result ${index + 1} has an invalid ${evidence.capability} status`);
      assert(Number.isFinite(evidence.confidence) && evidence.confidence >= 0 && evidence.confidence <= 1,
        `result ${index + 1} has an invalid ${evidence.capability} confidence`);
      capabilityMap.set(evidence.capability, {
        status: evidence.status,
        confidence: evidence.confidence,
      });
    };
    for (const evidence of selectedProbe.capabilities ?? []) addEvidence(evidence);
    for (const evidence of entry.result.capabilities ?? []) addEvidence(evidence);
    for (const [capability, localizedCapability] of LOCALIZED_CAPABILITY_FALLBACKS) {
      const evidence = entry.catalog?.localizedCapabilities?.[localizedCapability];
      if (evidence != null) addEvidence({ capability, ...evidence });
    }
    for (const [capability] of CAPABILITIES) {
      assert(capabilityMap.has(capability), `result ${index + 1} is missing capability ${capability}`);
    }
    assert(Number.isFinite(entry.compatibilityPercent) && entry.compatibilityPercent >= 0 &&
      entry.compatibilityPercent <= 100, `result ${index + 1} has invalid compatibility coverage`);

    selected.push({
      name: publicRomName(entry.displayName, index),
      generation,
      family,
      languageCount,
      compatibilityPercent: entry.compatibilityPercent,
      capabilities: capabilityMap,
    });
  });

  const resolvedLanguageManifests = selected.filter(row => row.languageCount != null).length;
  const unknownLanguageManifests = selected.length - resolvedLanguageManifests;
  const multilingualCount = selected.filter(row => (row.languageCount ?? 0) > 1).length;
  if (expectedSummary) {
    assert(outcomes.SELECTED === expectedSummary.outcomes.selected, "selected outcome count does not match evidence");
    assert(outcomes.AMBIGUOUS === expectedSummary.outcomes.ambiguous, "ambiguous outcome count does not match evidence");
    assert(outcomes.NO_FAMILY_MATCH === expectedSummary.outcomes.noFamilyMatch,
      "no-family-match count does not match evidence");
    assert(outcomes.ERROR === expectedSummary.outcomes.errors, "parser-error count does not match evidence");
    assert(resolvedLanguageManifests === expectedSummary.languageManifests.resolved,
      "resolved language-manifest count does not match evidence");
    assert(unknownLanguageManifests === expectedSummary.languageManifests.unknown,
      "unknown language-manifest count does not match evidence");
  }

  selected.sort((left, right) =>
    left.generation - right.generation ||
    FAMILY_ORDER.indexOf(left.family) - FAMILY_ORDER.indexOf(right.family) ||
    left.name.localeCompare(right.name, "en", { sensitivity: "base", numeric: true }));
  unclassified.sort((left, right) =>
    left.platform.localeCompare(right.platform) || left.name.localeCompare(right.name, "en", { sensitivity: "base", numeric: true }));

  return {
    rowSourceCommit,
    closureSourceCommit,
    inputCount: rows.length,
    selectedCount: selected.length,
    ambiguousCount: outcomes.AMBIGUOUS,
    noFamilyMatchCount: outcomes.NO_FAMILY_MATCH,
    errorCount: outcomes.ERROR,
    resolvedLanguageManifests,
    unknownLanguageManifests,
    multilingualCount,
    selected,
    unclassified,
  };
}

export function renderRomCompatibilityMarkdown(matrix) {
  const lines = [
    "# ROM compatibility matrix",
    "",
    "This document lists every input in the current 333-ROM canonical benchmark. Routed ROMs are grouped by generation and detected engine family. The benchmark includes ROM hacks and official validation controls; a row describes the scanned build named in the **ROM** column, not every release of that project.",
    "",
    "Capability percentages are parser confidence derived from bounded structural evidence. They are not a probability that every feature behaves exactly as the ROM author intended. Unless separately identified by a live test, these rows are static parser and persisted-catalog observations.",
    "",
    `- Inputs: **${matrix.inputCount}**`,
    `- Detected family and catalog: **${matrix.selectedCount}**`,
    `- Resolved ROM-language manifests: **${matrix.resolvedLanguageManifests}**`,
    `- Resolved manifests reporting more than one language: **${matrix.multilingualCount}**`,
    `- Unknown ROM-language manifests: **${matrix.unknownLanguageManifests}**`,
    `- Ambiguous family: **${matrix.ambiguousCount}**`,
    `- No family match: **${matrix.noFamilyMatchCount}**`,
    `- Parser errors: **${matrix.errorCount}**`,
    "",
    "The **Languages** column is the number of parser-proven ROM-content projections. `?` means the parser could not establish language authority. It does not assume that every resolved ROM supports English.",
    "",
    "Cells use `P 64%` for partial support, `? 64%` for ambiguous evidence, `0%` for not found, and `N/A` where a capability does not apply to that generation. **Coverage** is the existing weighted applicable-capability score; capability cells are evidence confidence.",
    "",
    "## Capability legend",
    "",
    "| Column | Capability |",
    "|---|---|",
    ...CAPABILITIES.map(([, label, description]) => `| ${label} | ${description} |`),
    "",
    "## Family summary",
    "",
    "| Generation | Detected family | ROM inputs | Average coverage | Multilingual manifests |",
    "|---:|---|---:|---:|---:|",
  ];

  for (const generation of [1, 2, 3]) {
    for (const family of FAMILY_ORDER.filter(name => FAMILY_GENERATIONS.get(name) === generation)) {
      const rows = matrix.selected.filter(row => row.family === family);
      if (rows.length === 0) continue;
      const average = rows.reduce((sum, row) => sum + row.compatibilityPercent, 0) / rows.length;
      lines.push(`| ${generation} | ${FAMILY_LABELS.get(family)} | ${rows.length} | ${formatCoverage(average)} | ${rows.filter(row => (row.languageCount ?? 0) > 1).length} |`);
    }
  }

  for (const generation of [1, 2, 3]) {
    lines.push("", `## Generation ${romanNumeral(generation)}`);
    for (const family of FAMILY_ORDER.filter(name => FAMILY_GENERATIONS.get(name) === generation)) {
      const rows = matrix.selected.filter(row => row.family === family);
      if (rows.length === 0) continue;
      lines.push(
        "",
        `### ${FAMILY_LABELS.get(family)}`,
        "",
        `<details><summary>${rows.length} ROM input${rows.length === 1 ? "" : "s"}</summary>`,
        "",
        matrixHeader(),
        matrixDivider(),
        ...rows.map(renderMatrixRow),
        "",
        "</details>",
      );
    }
  }

  lines.push(
    "",
    "## Unclassified inputs",
    "",
    "These inputs remain in the 333-ROM denominator but do not have a selected family, so no family-specific capability claim is made.",
    "",
    "| ROM | Platform | Routing outcome |",
    "|---|---|---|",
    ...matrix.unclassified.map(row => `| ${escapeCell(row.name)} | ${escapeCell(row.platform)} | ${humanOutcome(row.outcome)} |`),
    "",
    "## Evidence and privacy",
    "",
    `The row-level matrix was generated from parser schema ${REPORT_SCHEMA}` +
      `${matrix.rowSourceCommit ? ` at source commit \`${matrix.rowSourceCommit}\`` : ""}, filtered to the exact canonical corpus identity multiset. ` +
      `The final localization closure${matrix.closureSourceCommit ? ` at \`${matrix.closureSourceCommit}\`` : ""} independently binds the aggregate routing and language-manifest totals. ` +
      "The document intentionally excludes ROM hashes, source paths, archive paths, decoded text, ROM bytes, save data, and parser diagnostics. Public ROM filenames are retained only to identify the benchmark rows. Aggregate release evidence is in [Stage 6 corpus evidence](reports/localization/stage-06-corpus-evidence.json).",
    "",
  );
  return `${lines.join("\n")}\n`;
}

function matrixHeader() {
  return `| ROM | Languages | Coverage | ${CAPABILITIES.map(([, label]) => label).join(" | ")} |`;
}

function matrixDivider() {
  return `|---|---:|---:|${CAPABILITIES.map(() => "---:").join("|")}|`;
}

function renderMatrixRow(row) {
  const cells = CAPABILITIES.map(([name]) => formatCapability(row.capabilities.get(name)));
  return `| ${escapeCell(row.name)} | ${row.languageCount ?? "?"} | ${formatCoverage(row.compatibilityPercent)} | ${cells.join(" | ")} |`;
}

function formatCapability(evidence) {
  if (evidence.status === "NOT_APPLICABLE") return "N/A";
  if (evidence.status === "NOT_FOUND") return "0%";
  const confidence = `${Math.round(evidence.confidence * 100)}%`;
  if (evidence.status === "PARTIAL") return `P ${confidence}`;
  if (evidence.status === "AMBIGUOUS") return `? ${confidence}`;
  return confidence;
}

function formatCoverage(value) {
  return `${value.toFixed(2)}%`;
}

function publicRomName(value, index) {
  assert(typeof value === "string" && value.trim() !== "", `result ${index + 1} has no display name`);
  const name = value.replaceAll("\\", "/").split("!").map(part => part.split("/").at(-1)).join("!");
  assert(!/[ -]/u.test(name), `result ${index + 1} display name has control characters`);
  return name;
}

function escapeCell(value) {
  return String(value).replaceAll("|", "\\|").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function romanNumeral(generation) {
  return ["I", "II", "III"][generation - 1];
}

function humanOutcome(outcome) {
  return {
    AMBIGUOUS: "Ambiguous family",
    NO_FAMILY_MATCH: "No family match",
    ERROR: "Parser error",
  }[outcome] ?? outcome;
}

export function selectCanonicalRows(rows, identityIndex) {
  const expected = new Set();
  for (const row of identityIndex?.romDeltas ?? []) {
    assert(typeof row.sha256 === "string" && typeof row.rom === "string",
      "identity index has an invalid ROM row");
    expected.add(`${row.sha256.toLowerCase()}:${row.rom}`);
  }
  for (const group of identityIndex?.duplicateGroups ?? []) {
    assert(typeof group.sha256 === "string" && Array.isArray(group.roms),
      "identity index has an invalid duplicate group");
    for (const name of group.roms) expected.add(`${group.sha256.toLowerCase()}:${name}`);
  }
  assert(expected.size === identityIndex.inputRows,
    `identity index describes ${expected.size} public rows, expected ${identityIndex.inputRows}`);

  const selected = [];
  const matched = new Set();
  for (const row of rows) {
    const hash = row?.result?.sha256?.toLowerCase();
    const key = `${hash}:${row?.displayName}`;
    if (!expected.has(key)) continue;
    assert(!matched.has(key), `retained report has duplicate canonical row ${row.displayName}`);
    matched.add(key);
    selected.push(row);
  }
  assert(matched.size === expected.size,
    `retained report matched ${matched.size} canonical rows, expected ${expected.size}`);
  return selected;
}

export function canonicalCorpusDigest(rows) {
  const identities = rows.map((row, index) => {
    const hash = row?.result?.sha256?.toLowerCase();
    const size = row?.result?.size;
    assert(/^[0-9a-f]{64}$/.test(hash ?? ""), `result ${index + 1} has no valid identity`);
    assert(Number.isInteger(size) && size > 0, `result ${index + 1} has no valid size`);
    return `${hash}:${size}`;
  }).sort();
  return {
    inputCount: identities.length,
    uniqueRomIdentityCount: new Set(identities.map(value => value.slice(0, 64))).size,
    inputDigestSha256: createHash("sha256").update(identities.join("\n")).digest("hex"),
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
  for (const required of ["raw", "receipt", "evidence", "canonical-corpus", "markdown"]) {
    if (!options[required]) throw new Error(`Missing --${required}`);
  }
  return options;
}

async function main(arguments_) {
  const options = parseArguments(arguments_);
  const receipt = JSON.parse(readFileSync(resolve(options.receipt), "utf8"));
  const evidence = JSON.parse(readFileSync(resolve(options.evidence), "utf8"));
  const canonicalCorpus = JSON.parse(readFileSync(resolve(options["canonical-corpus"]), "utf8"));
  const identityIndex = options["identity-index"]
    ? JSON.parse(readFileSync(resolve(options["identity-index"]), "utf8"))
    : null;
  const retainedRows = [];
  const metadata = await streamCompatibilityReport(resolve(options.raw), row => retainedRows.push(row));
  assert(metadata.schemaVersion === REPORT_SCHEMA, `expected parser report schema ${REPORT_SCHEMA}`);
  assert(metadata.schemaVersion === receipt.generator?.schemaVersion, "report schema does not match its receipt");
  assert(metadata.execution?.sourceCommit === receipt.sourceCommit, "report source commit does not match its receipt");
  assert(metadata.execution?.generatorSha256 === receipt.generator?.sha256,
    "report generator digest does not match its receipt");
  assert(metadata.rawReportSha256 === receipt.rawReportSha256, "raw report digest does not match its receipt");
  assert(metadata.rowCount === receipt.inputCount, "raw report input count does not match its receipt");

  const rows = identityIndex == null ? retainedRows : selectCanonicalRows(retainedRows, identityIndex);
  const identity = canonicalCorpusDigest(rows);
  assert(identity.inputCount === canonicalCorpus.inputCount, "canonical input count does not match");
  assert(identity.uniqueRomIdentityCount === canonicalCorpus.uniqueRomIdentityCount,
    "canonical unique-ROM count does not match");
  assert(identity.inputDigestSha256 === canonicalCorpus.inputDigestSha256,
    "canonical input digest does not match");

  const matrix = buildRomCompatibilityMatrix(rows, {
    rowSourceCommit: metadata.execution.sourceCommit,
    closureSourceCommit: evidence.sourceCommit,
    expectedInputCount: evidence.inputCount,
    expectedSummary: evidence,
  });
  writeFileSync(resolve(options.markdown), renderRomCompatibilityMarkdown(matrix));
  process.stdout.write(JSON.stringify({
    inputs: matrix.inputCount,
    selected: matrix.selectedCount,
    resolvedLanguageManifests: matrix.resolvedLanguageManifests,
    multilingualManifests: matrix.multilingualCount,
    unknownLanguageManifests: matrix.unknownLanguageManifests,
    ambiguous: matrix.ambiguousCount,
    noFamilyMatch: matrix.noFamilyMatchCount,
    errors: matrix.errorCount,
  }, null, 2) + "\n");
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main(process.argv.slice(2)).catch(failure => {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  });
}
