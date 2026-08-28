import assert from "node:assert/strict";
import test from "node:test";
import {
  renderCompatibilityEvidenceMarkdown,
  summarizeCompatibilityEvidence,
} from "./summarize-compatibility-evidence.mjs";

const sourceCommit = "a".repeat(40);

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
    persistenceError: null,
    dataCompatibility,
  };
}

test("summarizes schema 12 corpus evidence without private input details", () => {
  const summary = summarizeCompatibilityEvidence({
    schemaVersion: 12,
    roots: ["D:/private/roms"],
    results: [
      row(1, "SELECTED", "COMPLETE"),
      row(2, "AMBIGUOUS", "PARTIAL", false),
      row(3, "NO_FAMILY_MATCH", "UNRESOLVED", false),
    ],
  }, sourceCommit);
  const encoded = JSON.stringify(summary);
  const markdown = renderCompatibilityEvidenceMarkdown(summary);

  assert.equal(summary.inputCount, 3);
  assert.equal(summary.outcomes.selected, 1);
  assert.equal(summary.outcomes.ambiguous, 1);
  assert.equal(summary.outcomes.noFamilyMatch, 1);
  assert.equal(summary.catalogs.persisted, 1);
  assert.match(summary.corpusInputDigestSha256, /^[0-9a-f]{64}$/);
  assert.doesNotMatch(encoded, /Private Game|D:\/private|0000000000000001/);
  assert.doesNotMatch(markdown, /Private Game|D:\/private|0000000000000001/);
});

test("rejects stale generator schemas and missing source identities", () => {
  assert.throws(
    () => summarizeCompatibilityEvidence({ schemaVersion: 11, results: [row(1, "SELECTED", "COMPLETE")] }, sourceCommit),
    /schemaVersion must be 12/,
  );
  const invalid = row(1, "SELECTED", "COMPLETE");
  invalid.result.sha256 = null;
  assert.throws(
    () => summarizeCompatibilityEvidence({ schemaVersion: 12, results: [invalid] }, sourceCommit),
    /valid SHA-256 identity/,
  );
});
