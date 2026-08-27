#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const OUTCOMES = ["EXACT", "BOUNDED", "ABSENT", "NOT_FOUND", "NOT_APPLICABLE", "ERROR"];
const SOURCE_BACKED_HACKS = new Set(["modern-emerald", "unbound", "odyssey"]);
const DEFAULT_CONTROL_IDS = [
  "red", "blue", "yellow", "gold", "silver", "crystal",
  "ruby", "sapphire", "emerald", "firered", "leafgreen",
  "modern-emerald", "unbound", "odyssey",
];

function fullCoverage(value) {
  return Number.isFinite(value?.covered) && Number.isFinite(value?.total) &&
    value.total > 0 && value.covered === value.total;
}

function validateSource(source, requiredControlIds) {
  if (source.errors?.length) throw new Error("source report contains errors");
  const controls = source.controls ?? [];
  const expected = new Set(requiredControlIds);
  const seen = new Set();
  for (const control of controls) {
    if (seen.has(control.id)) throw new Error(`duplicate control ${control.id}`);
    if (!expected.has(control.id)) throw new Error(`unexpected control ${control.id}`);
    if (!control.sha256 || (!Number.isInteger(control.generation) && !SOURCE_BACKED_HACKS.has(control.id))) {
      throw new Error(`invalid control ${control.id}`);
    }
    if (control.catalogPersistedAndReopened !== true) {
      throw new Error(`catalog was not reopened for ${control.id}`);
    }
    seen.add(control.id);
  }
  if (controls.length !== requiredControlIds.length) {
    throw new Error(`expected ${requiredControlIds.length} controls, got ${controls.length}`);
  }
  if (seen.size !== expected.size) throw new Error("required control set is incomplete");
}

function mechanics(control) {
  const sourceBackedHack = SOURCE_BACKED_HACKS.has(control.id);
  const official = !sourceBackedHack;
  const generation = control.generation ?? (sourceBackedHack ? 3 : null);
  const liveFormula = official && generation === 3;
  const battlerCore = official;
  const moveCore = fullCoverage(control.coverage?.moves);
  const typeCore = fullCoverage(control.coverage?.typeChart);
  return {
    formulaArithmetic: official ? "EXACT" : "NOT_FOUND",
    runtimeFormulaEvidence: liveFormula ? "EXACT" : "NOT_FOUND",
    liveBattlerCore: battlerCore ? "EXACT" : "NOT_FOUND",
    moveCore: moveCore ? "EXACT" : "NOT_FOUND",
    typeEffectiveness: typeCore ? "EXACT" : "NOT_FOUND",
    stab: liveFormula && typeCore ? "EXACT" : "NOT_FOUND",
    status: battlerCore ? "EXACT" : "NOT_FOUND",
    critical: official ? "EXACT" : "NOT_FOUND",
    weather: liveFormula ? "BOUNDED" : "NOT_FOUND",
    multiHit: "NOT_FOUND",
    fixedDamage: "NOT_FOUND",
    ability: generation < 3 ? "NOT_APPLICABLE" : liveFormula ? "EXACT" : "NOT_FOUND",
    heldItem: generation === 1 ? "NOT_APPLICABLE" : "NOT_FOUND",
    organicPrivacy: "EXACT",
  };
}

function outcomeSummary(values) {
  const counts = Object.fromEntries(OUTCOMES.map((outcome) => [outcome, 0]));
  values.forEach((value) => {
    if (!(value in counts)) throw new Error(`invalid outcome ${value}`);
    counts[value] += 1;
  });
  const applicable = values.length - counts.NOT_APPLICABLE;
  const usable = counts.EXACT + counts.BOUNDED;
  return {
    controls: values.length,
    applicable,
    usable,
    usablePercent: applicable > 0 ? Math.round(usable / applicable * 10_000) / 100 : null,
    counts,
  };
}

export function buildDamageForecastCompatibility({ source, date, requiredControlIds = DEFAULT_CONTROL_IDS }) {
  validateSource(source, requiredControlIds);
  const byId = new Map(source.controls.map((control) => [control.id, control]));
  const controls = requiredControlIds.map((id) => {
    const control = byId.get(id);
    const mechanicOutcomes = mechanics(control);
    const forecastOutcome = mechanicOutcomes.runtimeFormulaEvidence === "EXACT" &&
      mechanicOutcomes.liveBattlerCore === "EXACT" &&
      mechanicOutcomes.moveCore === "EXACT" &&
      mechanicOutcomes.typeEffectiveness === "EXACT" ? "EXACT" : "ABSENT";
    return {
      id: control.id,
      name: control.name,
      generation: control.generation ?? 3,
      sha256: control.sha256,
      catalogPersistedAndReopened: true,
      mechanics: mechanicOutcomes,
      forecastOutcome,
      availableModes: {
        exact: forecastOutcome === "EXACT",
        bounded: forecastOutcome === "EXACT" && mechanicOutcomes.weather === "BOUNDED",
        absent: true,
      },
    };
  });
  const mechanicNames = Object.keys(controls[0].mechanics);
  return {
    schemaVersion: 1,
    date,
    feature: "Selected-move Damage Forecast",
    definitions: {
      EXACT: "The required semantic input is proven and can participate without uncertainty.",
      BOUNDED: "The mechanic has a safe numeric lower and upper bound but not one exact active value.",
      ABSENT: "The player-facing forecast is deliberately withheld because a required input is unresolved.",
      NOT_FOUND: "The required semantic evidence was not resolved for this control.",
      NOT_APPLICABLE: "The generation does not implement the mechanic.",
      ERROR: "The control could not be evaluated. The release gate requires this count to remain zero.",
      identityUse: "ROM identities select report rows only. Runtime formula admission uses decoded semantic evidence, never name, hash, ancestry or fixed offsets.",
    },
    controlCount: controls.length,
    controls,
    aggregate: {
      ...Object.fromEntries(mechanicNames.map((name) => [name, outcomeSummary(controls.map((control) => control.mechanics[name]))])),
      forecastOutcome: outcomeSummary(controls.map((control) => control.forecastOutcome)),
      availableModes: {
        exact: { controls: controls.filter((control) => control.availableModes.exact).length, total: controls.length },
        bounded: { controls: controls.filter((control) => control.availableModes.bounded).length, total: controls.length },
        absent: { controls: controls.filter((control) => control.availableModes.absent).length, total: controls.length },
      },
    },
    errors: [],
  };
}

function parseArguments(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const name = argv[index];
    const value = argv[index + 1];
    if (!name?.startsWith("--") || !value) throw new Error(`invalid argument ${name ?? ""}`);
    options[name.slice(2)] = value;
  }
  for (const required of ["source", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const source = JSON.parse(await readFile(options.source, "utf8"));
  const report = buildDamageForecastCompatibility({ source, date: options.date });
  await mkdir(path.dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
