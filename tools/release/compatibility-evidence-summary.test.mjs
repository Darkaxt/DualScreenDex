import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import {
  renderCompatibilityEvidenceMarkdown,
  summarizeCompatibilityEvidence,
  summarizeCompatibilityEvidenceFile,
} from "./summarize-compatibility-evidence.mjs";

const sourceCommit = "a".repeat(40);
const generatorDigest = "b".repeat(64);

function row(index, status, dataCompatibility, persisted = status === "SELECTED") {
  return {
    displayName: `Private Game ${index}.gba`,
    source: `D:/private/roms/game-${index}.gba`,
    result: {
      sha256: index.toString(16).padStart(64, "0"),
      size: 1_024 + index,
      status,
    },
    catalog: status === "SELECTED" ? { species: 1 } : null,
    persistence: persisted ? { bytes: 100 } : null,
    catalogError: null,
    persistenceError: null,
    error: null,
    dataCompatibility,
  };
}

function evidence(rows) {
  const report = {
    schemaVersion: 13,
    execution: {
      sourceCommit,
      generatorSha256: generatorDigest,
    },
    roots: ["D:/private/roms"],
    results: rows,
  };
  const raw = Buffer.from(JSON.stringify(report));
  const identities = rows
    .map(value => `${value.result.sha256}:${value.result.size}`)
    .sort();
  const canonical = {
    schemaVersion: 2,
    inputCount: rows.length,
    uniqueRomIdentityCount: new Set(rows.map(value => value.result.sha256)).size,
    inputDigestSha256: createHash("sha256").update(identities.join("\n")).digest("hex"),
  };
  const receipt = {
    schemaVersion: 1,
    sourceCommit,
    generator: { name: "parser-cli", schemaVersion: 13, sha256: generatorDigest },
    rawReportSha256: createHash("sha256").update(raw).digest("hex"),
    inputCount: rows.length,
  };
  return { raw, receipt, canonical };
}

test("summarizes receipt-bound corpus evidence without private input details", () => {
  const input = evidence([
    row(1, "SELECTED", "COMPLETE"),
    row(2, "AMBIGUOUS", "PARTIAL", false),
    row(3, "NO_FAMILY_MATCH", "UNRESOLVED", false),
  ]);
  const summary = summarizeCompatibilityEvidence(input.raw, input.receipt, input.canonical);
  const encoded = JSON.stringify(summary);
  const markdown = renderCompatibilityEvidenceMarkdown(summary);

  assert.equal(summary.schemaVersion, 2);
  assert.equal(summary.sourceCommit, sourceCommit);
  assert.equal(summary.generator.sha256, generatorDigest);
  assert.equal(summary.rawReportSha256, input.receipt.rawReportSha256);
  assert.equal(summary.inputCount, 3);
  assert.equal(summary.outcomes.total, 3);
  assert.equal(summary.dataCompatibility.total, 3);
  assert.equal(summary.catalogs.persisted, 1);
  assert.match(markdown, /Data compatibility: 1 complete, 1 partial, 1 unresolved, 0 errors/);
  assert.doesNotMatch(encoded, /Private Game|D:\/private|0000000000000001/);
  assert.doesNotMatch(markdown, /Private Game|D:\/private|0000000000000001/);
});

test("streams reports whose retained catalog payload crosses read boundaries", async () => {
  const rows = [
    { ...row(1, "SELECTED", "COMPLETE"), catalog: { padding: "x".repeat(2_000_000) } },
    row(2, "NO_FAMILY_MATCH", "UNRESOLVED", false),
  ];
  const input = evidence(rows);
  const directory = mkdtempSync(join(tmpdir(), "dualdex-summary-test-"));
  const rawPath = join(directory, "corpus-raw.json");

  try {
    writeFileSync(rawPath, input.raw);
    const summary = await summarizeCompatibilityEvidenceFile(rawPath, input.receipt, input.canonical);

    assert.equal(summary.inputCount, 2);
    assert.equal(summary.outcomes.selected, 1);
    assert.equal(summary.catalogs.persisted, 1);
    assert.equal(summary.rawReportSha256, input.receipt.rawReportSha256);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("rejects malformed streaming array separators and root termination", async () => {
  const rows = [row(1, "SELECTED", "COMPLETE")];
  const input = evidence(rows);
  const header = JSON.stringify({
    schemaVersion: 13,
    execution: { sourceCommit, generatorSha256: generatorDigest },
    roots: ["private-root"],
  }).slice(0, -1);
  const encodedRow = JSON.stringify(rows[0]);
  const malformedReports = [
    `${header},"results":[,${encodedRow}]}`,
    `${header},"results":[${encodedRow},]}`,
    `${header},"results":[${encodedRow}${encodedRow}]}`,
    `${header},"results":[${encodedRow}]`,
    `${header},"results":[${encodedRow}]}}`,
  ];
  const directory = mkdtempSync(join(tmpdir(), "dualdex-summary-malformed-"));
  const rawPath = join(directory, "corpus-raw.json");

  try {
    for (const malformed of malformedReports) {
      const raw = Buffer.from(malformed);
      input.receipt.rawReportSha256 = createHash("sha256").update(raw).digest("hex");
      writeFileSync(rawPath, raw);
      await assert.rejects(
        () => summarizeCompatibilityEvidenceFile(rawPath, input.receipt, input.canonical),
        /separator|trailing comma|terminator|trailing content/i,
      );
    }
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("rejects relabeling an older raw report even when its corpus digest matches", () => {
  const input = evidence([row(1, "SELECTED", "COMPLETE")]);
  input.receipt.sourceCommit = "c".repeat(40);

  assert.throws(
    () => summarizeCompatibilityEvidence(input.raw, input.receipt, input.canonical),
    /source commit/i,
  );
});

test("rejects stale schemas and raw-report or generator digest drift", () => {
  const stale = evidence([row(1, "SELECTED", "COMPLETE")]);
  stale.receipt.generator.schemaVersion = 12;
  assert.throws(
    () => summarizeCompatibilityEvidence(stale.raw, stale.receipt, stale.canonical),
    /schemaVersion must be 13/i,
  );

  const changedRaw = evidence([row(1, "SELECTED", "COMPLETE")]);
  changedRaw.receipt.rawReportSha256 = "c".repeat(64);
  assert.throws(
    () => summarizeCompatibilityEvidence(changedRaw.raw, changedRaw.receipt, changedRaw.canonical),
    /raw report digest/i,
  );

  const changedGenerator = evidence([row(1, "SELECTED", "COMPLETE")]);
  const parsed = JSON.parse(changedGenerator.raw.toString("utf8"));
  parsed.execution.generatorSha256 = "c".repeat(64);
  changedGenerator.raw = Buffer.from(JSON.stringify(parsed));
  changedGenerator.receipt.rawReportSha256 = createHash("sha256")
    .update(changedGenerator.raw)
    .digest("hex");
  assert.throws(
    () => summarizeCompatibilityEvidence(
      changedGenerator.raw,
      changedGenerator.receipt,
      changedGenerator.canonical,
    ),
    /generator digest/i,
  );
});

test("rejects missing terminal outcomes and all raw error channels", () => {
  const missing = evidence([row(1, undefined, "COMPLETE")]);
  assert.throws(
    () => summarizeCompatibilityEvidence(missing.raw, missing.receipt, missing.canonical),
    /terminal parser outcome/i,
  );

  for (const [field, value, message] of [
    ["error", "read failed", /source errors/i],
    ["catalogError", "catalog failed", /catalog errors/i],
    ["persistenceError", "database failed", /persistence errors/i],
    ["dataCompatibility", "ERROR", /compatibility errors/i],
  ]) {
    const failingRow = row(1, "SELECTED", "COMPLETE");
    failingRow[field] = value;
    const input = evidence([failingRow]);
    assert.throws(
      () => summarizeCompatibilityEvidence(input.raw, input.receipt, input.canonical),
      message,
    );
  }
});
