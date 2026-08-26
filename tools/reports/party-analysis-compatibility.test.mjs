import assert from "node:assert/strict";
import test from "node:test";

import { buildPartyAnalysisCompatibility } from "./party-analysis-compatibility.mjs";

const identities = {
  official: [{ id: "red", generation: 1, sha256: "a".repeat(64) }],
  hacks: [{ id: "unbound", generation: 3, sha256: "b".repeat(64) }],
};

function parserEntry(hash, overrides = {}) {
  return {
    displayName: "D:/private/rom.gba",
    source: "D:/private/rom.gba",
    result: {
      sha256: hash,
      status: "SELECTED",
      capabilities: [
        { capability: "TYPE_CHART", status: "AVAILABLE", coveredRecords: 3, expectedRecords: 4 },
        { capability: "EVOLUTIONS", status: "AVAILABLE", coveredRecords: 8, expectedRecords: 10 },
      ],
    },
    catalog: {
      moves: 10,
      movesWithDetails: 9,
      movesWithCategories: 7,
      typeMatchups: 4,
      evolutionEdges: 8,
      abilities: 5,
      abilitiesWithProvenTypedModifiers: 2,
      provenTypedAbilityModifiers: 3,
    },
    samples: { referenceErrors: [] },
    persistence: { bytes: 123, reopenMillis: 1 },
    ...overrides,
  };
}

test("publishes independent numeric coverage without private paths", () => {
  const report = buildPartyAnalysisCompatibility({
    parserReport: {
      schemaVersion: 12,
      roots: ["D:/private/roms"],
      results: [parserEntry("a".repeat(64)), parserEntry("b".repeat(64))],
    },
    liveReport: { controls: [{ id: "red", party: 0 }, { id: "unbound", party: 1 }] },
    identities,
    date: "2026-08-26",
  });

  assert.deepEqual(report.controls[0].coverage.partyFields, { covered: 0, total: 6, percent: 0 });
  assert.deepEqual(report.controls[1].coverage.partyFields, { covered: 6, total: 6, percent: 100 });
  assert.deepEqual(report.controls[1].coverage.moves, { covered: 9, total: 10, percent: 90 });
  assert.deepEqual(report.controls[1].coverage.moveCategories, { covered: 7, total: 10, percent: 70 });
  assert.deepEqual(report.controls[1].coverage.typeChart, { covered: 3, total: 4, percent: 75 });
  assert.deepEqual(report.controls[1].coverage.evolutions, { covered: 8, total: 10, percent: 80 });
  assert.deepEqual(report.controls[1].coverage.provenAbilityModifiers, {
    covered: 2,
    total: 5,
    percent: 40,
    modifierRecords: 3,
  });
  assert.equal(JSON.stringify(report).includes("D:/private"), false);
  assert.deepEqual(report.errors, []);
});

test("fails closed for missing, duplicate or erroneous controls", () => {
  const liveReport = { controls: [{ id: "red", party: 0 }, { id: "unbound", party: 1 }] };
  assert.throws(
    () => buildPartyAnalysisCompatibility({
      parserReport: { schemaVersion: 12, results: [parserEntry("a".repeat(64))] },
      liveReport,
      identities,
      date: "2026-08-26",
    }),
    /expected 2 parser controls, got 1/,
  );
  assert.throws(
    () => buildPartyAnalysisCompatibility({
      parserReport: {
        schemaVersion: 12,
        results: [parserEntry("a".repeat(64)), parserEntry("a".repeat(64)), parserEntry("b".repeat(64))],
      },
      liveReport,
      identities,
      date: "2026-08-26",
    }),
    /duplicate parser identity/,
  );
  assert.throws(
    () => buildPartyAnalysisCompatibility({
      parserReport: {
        schemaVersion: 12,
        results: [parserEntry("a".repeat(64)), parserEntry("b".repeat(64), { error: "boom" })],
      },
      liveReport,
      identities,
      date: "2026-08-26",
    }),
    /parser error for b+/,
  );
});
