import assert from "node:assert/strict";
import test from "node:test";

import { buildDamageForecastCompatibility } from "./damage-forecast-compatibility.mjs";

function control(id, generation, moves = [10, 10], typeChart = [5, 5]) {
  return {
    id,
    name: id,
    generation,
    sha256: id.padEnd(64, "a"),
    catalogPersistedAndReopened: true,
    coverage: {
      moves: { covered: moves[0], total: moves[1], percent: moves[0] / moves[1] * 100 },
      typeChart: { covered: typeChart[0], total: typeChart[1], percent: typeChart[0] / typeChart[1] * 100 },
    },
  };
}

test("reports exact, bounded, absent and evidence outcomes independently", () => {
  const report = buildDamageForecastCompatibility({
    source: {
      errors: [],
      controls: [control("red", 1), control("emerald", 3), control("unbound", 3)],
    },
    date: "2026-08-27",
    requiredControlIds: ["red", "emerald", "unbound"],
  });

  assert.equal(report.controls[0].mechanics.formulaArithmetic, "EXACT");
  assert.equal(report.controls[0].mechanics.runtimeFormulaEvidence, "NOT_FOUND");
  assert.equal(report.controls[0].mechanics.ability, "NOT_APPLICABLE");
  assert.equal(report.controls[0].forecastOutcome, "ABSENT");

  assert.equal(report.controls[1].mechanics.runtimeFormulaEvidence, "EXACT");
  assert.equal(report.controls[1].mechanics.weather, "BOUNDED");
  assert.equal(report.controls[1].forecastOutcome, "EXACT");

  assert.equal(report.controls[2].mechanics.moveCore, "EXACT");
  assert.equal(report.controls[2].mechanics.runtimeFormulaEvidence, "NOT_FOUND");
  assert.equal(report.controls[2].forecastOutcome, "ABSENT");

  assert.deepEqual(report.aggregate.forecastOutcome.counts, {
    EXACT: 1, BOUNDED: 0, ABSENT: 2, NOT_FOUND: 0, NOT_APPLICABLE: 0, ERROR: 0,
  });
  assert.equal(report.aggregate.forecastOutcome.usablePercent, 33.33);
  assert.deepEqual(report.errors, []);
});

test("forms numeric denominators without converting missing mechanics into support labels", () => {
  const report = buildDamageForecastCompatibility({
    source: { errors: [], controls: [control("emerald", 3), control("unbound", 3, [9, 10])] },
    date: "2026-08-27",
    requiredControlIds: ["emerald", "unbound"],
  });

  assert.equal(report.aggregate.moveCore.applicable, 2);
  assert.equal(report.aggregate.moveCore.usable, 1);
  assert.equal(report.aggregate.moveCore.usablePercent, 50);
  assert.equal(report.aggregate.moveCore.counts.NOT_FOUND, 1);
  assert.equal(JSON.stringify(report).includes("fully supported"), false);
});

test("fails closed for missing, duplicate, unexpected or erroneous real controls", () => {
  const red = control("red", 1);
  assert.throws(
    () => buildDamageForecastCompatibility({ source: { errors: [], controls: [red] }, date: "2026-08-27", requiredControlIds: ["red", "emerald"] }),
    /expected 2 controls, got 1/,
  );
  assert.throws(
    () => buildDamageForecastCompatibility({ source: { errors: [], controls: [red, red] }, date: "2026-08-27", requiredControlIds: ["red"] }),
    /duplicate control/,
  );
  assert.throws(
    () => buildDamageForecastCompatibility({ source: { errors: ["boom"], controls: [red] }, date: "2026-08-27", requiredControlIds: ["red"] }),
    /source report contains errors/,
  );
});
