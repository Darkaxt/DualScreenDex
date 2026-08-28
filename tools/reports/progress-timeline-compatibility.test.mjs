import assert from "node:assert/strict";
import test from "node:test";

import { buildProgressTimelineCompatibility } from "./progress-timeline-compatibility.mjs";

const identities = {
  official: [
    { id: "red", generation: 1, sha256: "a".repeat(64) },
  ],
  hacks: [
    { id: "unbound", generation: 3, sha256: "b".repeat(64) },
  ],
};

const liveReport = {
  controls: [
    { id: "red", generation: 1, trainer: 0, pokedex: 0, party: 0, battle: 1, area: 1 },
    { id: "unbound", generation: 3, trainer: 1, pokedex: 1, party: 1, battle: 1, area: 1 },
  ],
};

const areaGuideReport = {
  controls: [
    { id: "red", name: "Pokémon Red", sha256: "a".repeat(64), materializedRecords: { points: 20 } },
    { id: "unbound", name: "Pokémon Unbound", sha256: "b".repeat(64), materializedRecords: { points: 30 } },
  ],
};

const challenges = {
  schema: 1,
  challenges: [
    { key: "capture-one", operator: "COUNT_AT_LEAST", metric: "captures", target: 1, organicSafe: true, requiredCapabilities: ["POKEDEX_FACTS"] },
    { key: "evolve-one", operator: "COUNT_AT_LEAST", metric: "evolutions", target: 1, organicSafe: true, requiredCapabilities: ["OWNED_INDIVIDUALS"] },
    { key: "areas-five", operator: "COUNT_AT_LEAST", metric: "areas", target: 5, organicSafe: true, requiredCapabilities: ["LOCATION_FACTS"] },
    { key: "pois-five", operator: "COUNT_AT_LEAST", metric: "pois", target: 5, organicSafe: true, requiredCapabilities: ["POI_FACTS"] },
    { key: "battles-five", operator: "COUNT_AT_LEAST", metric: "battles", target: 5, organicSafe: true, requiredCapabilities: ["BATTLE_FACTS"] },
  ],
};

const classification = { summary: { total: 1003, classified: 1003, expressible: 1003 } };

test("reports current totals events and templates as separate numeric percentages", () => {
  const report = buildProgressTimelineCompatibility({
    identities,
    liveReport,
    areaGuideReport,
    challenges,
    classification,
    date: "2026-08-27",
  });

  assert.deepEqual(report.reference.classified, { covered: 1003, total: 1003, percent: 100 });
  assert.deepEqual(report.reference.expressible, { covered: 1003, total: 1003, percent: 100 });
  assert.deepEqual(report.controls[0].coverage.currentTotalFields, { covered: 0, total: 5, percent: 0, notFound: 5 });
  assert.deepEqual(report.controls[0].coverage.observableEventFamilies, { covered: 6, total: 9, percent: 66.67, notFound: 3 });
  assert.deepEqual(report.controls[0].coverage.baselineApplicableTemplates, { covered: 3, total: 5, percent: 60, notApplicable: 2 });
  assert.deepEqual(report.controls[0].coverage.fullyObservableTemplates, { covered: 3, total: 3, percent: 100, notFound: 0 });
  assert.deepEqual(report.controls[1].coverage.currentTotalFields, { covered: 5, total: 5, percent: 100, notFound: 0 });
  assert.deepEqual(report.controls[1].coverage.observableEventFamilies, { covered: 9, total: 9, percent: 100, notFound: 0 });
  assert.deepEqual(report.controls[1].coverage.validatedTemplates, { covered: 5, total: 5, percent: 100, error: 0 });
  assert.equal(JSON.stringify(report).includes("D:/"), false);
  assert.deepEqual(report.errors, []);
});

test("fails closed for a missing control or malformed baseline template", () => {
  assert.throws(
    () => buildProgressTimelineCompatibility({
      identities,
      liveReport: { controls: liveReport.controls.slice(0, 1) },
      areaGuideReport,
      challenges,
      classification,
      date: "2026-08-27",
    }),
    /missing live control unbound/,
  );
  assert.throws(
    () => buildProgressTimelineCompatibility({
      identities,
      liveReport,
      areaGuideReport,
      challenges: { schema: 1, challenges: [{ ...challenges.challenges[0], target: 0 }] },
      classification,
      date: "2026-08-27",
    }),
    /invalid baseline challenge/,
  );
});
