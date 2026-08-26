import assert from "node:assert/strict";
import test from "node:test";

import { buildAreaGuideCompatibility } from "./area-guide-compatibility.mjs";

const identities = {
  controls: [
    { id: "emerald", name: "Pokémon Emerald", generation: 3, sha256: "a".repeat(64) },
    { id: "unbound", name: "Pokémon Unbound", generation: 3, sha256: "b".repeat(64) },
  ],
};

function parserEntry(hash, areaGuide = {}) {
  return {
    displayName: "D:/private/rom.gba",
    source: "D:/private/rom.gba",
    result: {
      sha256: hash,
      status: "SELECTED",
      capabilities: [{
        capability: "LOCAL_MAP", status: "PARTIAL", coveredRecords: 8, expectedRecords: 10,
      }],
    },
    catalog: {
      areaGuide: {
        areaIdentities: 10,
        namedAreaIdentities: 9,
        exitRecords: 8,
        resolvedExitRecords: 7,
        encounterSpeciesRecords: 20,
        namedEncounterSpeciesRecords: 19,
        encounterWindowGroups: 4,
        resolvedEncounterWindowGroups: 4,
        encounterLevelRecords: 20,
        resolvedEncounterLevelRecords: 20,
        encounterRateRecords: 20,
        resolvedEncounterRateRecords: 18,
        localMapCount: 8,
        poiBearingMapCount: 6,
        poiRecords: 12,
        poiRecordsWithContent: 9,
        ...areaGuide,
      },
    },
    samples: { referenceErrors: [] },
    persistence: { bytes: 123, reopenMillis: 1 },
  };
}

test("publishes exact per-table percentages from persisted real-control metrics", () => {
  const report = buildAreaGuideCompatibility({
    parserReport: {
      schemaVersion: 12,
      roots: ["D:/private"],
      results: [parserEntry("a".repeat(64)), parserEntry("b".repeat(64), {
        namedAreaIdentities: 10,
        resolvedEncounterRateRecords: 20,
        poiRecordsWithContent: 12,
      })],
    },
    identities,
    date: "2026-08-26",
  });

  assert.deepEqual(report.controls[0].coverage.areaNames, { covered: 9, total: 10, percent: 90 });
  assert.deepEqual(report.controls[0].coverage.exits, { covered: 7, total: 8, percent: 87.5 });
  assert.deepEqual(report.controls[0].coverage.encounterRates, { covered: 18, total: 20, percent: 90 });
  assert.deepEqual(report.controls[0].coverage.localMaps, { covered: 8, total: 10, percent: 80 });
  assert.deepEqual(report.controls[0].coverage.poiBearingMaps, { covered: 6, total: 8, percent: 75 });
  assert.deepEqual(report.controls[1].coverage.poiContent, { covered: 12, total: 12, percent: 100 });
  assert.deepEqual(report.controls[1].coverage.filters, { covered: 5, total: 5, percent: 100 });
  assert.equal(JSON.stringify(report).includes("D:/private"), false);
  assert.deepEqual(report.errors, []);
});

test("uses explicit absence states only when a denominator cannot be formed", () => {
  const entry = parserEntry("a".repeat(64), {
    exitRecords: 0,
    resolvedExitRecords: 0,
    localMapCount: 0,
    poiBearingMapCount: 0,
    poiRecords: 0,
    poiRecordsWithContent: 0,
  });
  entry.result.capabilities[0].status = "NOT_FOUND";
  entry.result.capabilities[0].coveredRecords = 0;
  entry.result.capabilities[0].expectedRecords = null;
  const report = buildAreaGuideCompatibility({
    parserReport: { schemaVersion: 12, results: [entry] },
    identities: { controls: [identities.controls[0]] },
    date: "2026-08-26",
  });

  assert.deepEqual(report.controls[0].coverage.exits, {
    covered: 0, total: 0, percent: null, absence: "NOT_FOUND",
  });
  assert.deepEqual(report.controls[0].coverage.localMaps, {
    covered: 0, total: 0, percent: null, absence: "NOT_FOUND",
  });
  assert.deepEqual(report.controls[0].coverage.poiContent, {
    covered: 0, total: 0, percent: null, absence: "NOT_FOUND",
  });
});

test("fails closed for missing, duplicate, unexpected, or erroneous controls", () => {
  const expected = identities;
  const a = parserEntry("a".repeat(64));
  const b = parserEntry("b".repeat(64));
  assert.throws(
    () => buildAreaGuideCompatibility({ parserReport: { schemaVersion: 12, results: [a] }, identities: expected, date: "2026-08-26" }),
    /expected 2 parser controls, got 1/,
  );
  assert.throws(
    () => buildAreaGuideCompatibility({ parserReport: { schemaVersion: 12, results: [a, a, b] }, identities: expected, date: "2026-08-26" }),
    /duplicate parser identity/,
  );
  assert.throws(
    () => buildAreaGuideCompatibility({ parserReport: { schemaVersion: 12, results: [a, { ...b, catalogError: "boom" }] }, identities: expected, date: "2026-08-26" }),
    /parser error/,
  );
});
