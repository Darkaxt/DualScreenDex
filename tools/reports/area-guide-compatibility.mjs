#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

function ratio(covered, total) {
  const safeCovered = Number.isFinite(covered) ? Math.max(0, covered) : 0;
  const safeTotal = Number.isFinite(total) ? Math.max(0, total) : 0;
  return {
    covered: safeCovered,
    total: safeTotal,
    percent: safeTotal > 0 ? Math.round((safeCovered / safeTotal) * 10_000) / 100 : null,
  };
}

function measured(covered, total, absence = "NOT_FOUND") {
  const result = ratio(covered, total);
  return result.total > 0 ? result : { ...result, absence };
}

function capability(entry, name) {
  return entry.result?.capabilities?.find((candidate) => candidate.capability === name) ?? null;
}

function validateParserResult(entry) {
  if (entry.error || entry.catalogError || entry.persistenceError) {
    throw new Error(`parser error for ${entry.result?.sha256 ?? "unknown identity"}`);
  }
  if (entry.result?.status !== "SELECTED") {
    throw new Error(`unselected parser result for ${entry.result?.sha256 ?? "unknown identity"}`);
  }
  if (!entry.catalog?.areaGuide) throw new Error(`missing Area Guide metrics for ${entry.result.sha256}`);
  if (!entry.persistence) throw new Error(`missing reopened catalog evidence for ${entry.result.sha256}`);
  if (entry.samples?.referenceErrors?.length) {
    throw new Error(`catalog reference errors for ${entry.result.sha256}`);
  }
}

function localMapCoverage(entry, metrics) {
  const evidence = capability(entry, "LOCAL_MAP");
  if (Number.isFinite(evidence?.expectedRecords) && evidence.expectedRecords > 0) {
    return ratio(evidence.coveredRecords ?? metrics.localMapCount, evidence.expectedRecords);
  }
  if (metrics.localMapCount > 0) return ratio(metrics.localMapCount, metrics.localMapCount);
  return measured(0, 0, evidence?.status === "NOT_APPLICABLE" ? "NOT_APPLICABLE" : "NOT_FOUND");
}

function absenceForLocal(entry) {
  return capability(entry, "LOCAL_MAP")?.status === "NOT_APPLICABLE" ? "NOT_APPLICABLE" : "NOT_FOUND";
}

function aggregateCoverage(controls, field) {
  return controls.reduce(
    (aggregate, control) => ratio(
      aggregate.covered + control.coverage[field].covered,
      aggregate.total + control.coverage[field].total,
    ),
    ratio(0, 0),
  );
}

export function buildAreaGuideCompatibility({ parserReport, identities, date }) {
  if (parserReport.schemaVersion !== 12) {
    throw new Error(`expected parser report schema 12, got ${parserReport.schemaVersion}`);
  }
  const identityList = identities.controls ?? [];
  const expectedByHash = new Map(identityList.map((identity) => [identity.sha256.toLowerCase(), identity]));
  if (expectedByHash.size !== identityList.length) throw new Error("duplicate expected ROM identity");

  const parserByHash = new Map();
  for (const entry of parserReport.results) {
    const hash = entry.result?.sha256?.toLowerCase();
    if (!hash || !expectedByHash.has(hash)) throw new Error(`unexpected parser identity ${hash ?? "missing"}`);
    if (parserByHash.has(hash)) throw new Error(`duplicate parser identity ${hash}`);
    validateParserResult(entry);
    parserByHash.set(hash, entry);
  }
  if (parserByHash.size !== identityList.length) {
    throw new Error(`expected ${identityList.length} parser controls, got ${parserByHash.size}`);
  }

  const controls = identityList.map((identity) => {
    const entry = parserByHash.get(identity.sha256.toLowerCase());
    const metrics = entry.catalog.areaGuide;
    const localAbsence = absenceForLocal(entry);
    return {
      id: identity.id,
      name: identity.name,
      generation: identity.generation,
      sha256: identity.sha256,
      catalogPersistedAndReopened: true,
      materializedRecords: {
        exits: metrics.exitRecords,
        points: metrics.poiRecords,
      },
      coverage: {
        areaNames: measured(metrics.namedAreaIdentities, metrics.areaIdentities),
        exits: measured(metrics.resolvedExitRecords, metrics.exitRecords, localAbsence),
        encounterSpecies: measured(metrics.namedEncounterSpeciesRecords, metrics.encounterSpeciesRecords),
        encounterWindows: measured(metrics.resolvedEncounterWindowGroups, metrics.encounterWindowGroups),
        encounterLevels: measured(metrics.resolvedEncounterLevelRecords, metrics.encounterLevelRecords),
        encounterRates: measured(metrics.resolvedEncounterRateRecords, metrics.encounterRateRecords),
        localMaps: localMapCoverage(entry, metrics),
        poiBearingMaps: measured(metrics.poiBearingMapCount, metrics.localMapCount, localAbsence),
        poiContent: measured(metrics.poiRecordsWithContent, metrics.poiRecords, localAbsence),
        filters: ratio(5, 5),
      },
    };
  });

  const fields = [
    "areaNames",
    "exits",
    "encounterSpecies",
    "encounterWindows",
    "encounterLevels",
    "encounterRates",
    "localMaps",
    "poiBearingMaps",
    "poiContent",
    "filters",
  ];
  return {
    schemaVersion: 1,
    date,
    feature: "Atlas Area Guide",
    definitions: {
      areaNames: "Distinct encounter, world-map, Local-map or runtime area identities with a player-facing name.",
      exits: "Materialized destination or adjacent-scene exit records whose target has a named Area Guide identity.",
      encounterSpecies: "Encounter slot records whose referenced species has a player-facing ROM-derived name.",
      encounterWindows: "Encounter groups with at least one normalized availability window; ANY is a valid all-day window.",
      encounterLevels: "Encounter slot records with a valid positive minimum and maximum level range.",
      encounterRates: "Encounter slot records with an explicit ROM-derived weight.",
      localMaps: "Local-map records persisted out of the parser-declared expected record count.",
      poiBearingMaps: "Persisted Local maps with at least one parsed POI; this is observed content density, not a claim that every map must contain a POI.",
      poiContent: "Parsed POI records with a name, gender-specific name, service role, or item identity.",
      filters: "Five shared ROM-save-scoped POI category controls used by both Local map markers and Area Guide rows.",
    },
    controlCount: controls.length,
    controls,
    aggregate: Object.fromEntries(fields.map((field) => [field, aggregateCoverage(controls, field)])),
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
  for (const required of ["parser", "identities", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const [parserReport, identities] = await Promise.all(
    [options.parser, options.identities].map(async (file) => JSON.parse(await readFile(file, "utf8"))),
  );
  const report = buildAreaGuideCompatibility({ parserReport, identities, date: options.date });
  await mkdir(path.dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
