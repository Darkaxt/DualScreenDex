#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const DISPLAY_NAMES = Object.freeze({
  red: "Pokémon Red",
  blue: "Pokémon Blue",
  yellow: "Pokémon Yellow",
  gold: "Pokémon Gold",
  silver: "Pokémon Silver",
  crystal: "Pokémon Crystal",
  ruby: "Pokémon Ruby",
  sapphire: "Pokémon Sapphire",
  emerald: "Pokémon Emerald",
  firered: "Pokémon FireRed",
  leafgreen: "Pokémon LeafGreen",
  "modern-emerald": "Modern Emerald",
  unbound: "Pokémon Unbound",
  odyssey: "Pokémon Odyssey",
});

function ratio(covered, total) {
  const safeCovered = Number.isFinite(covered) ? Math.max(0, covered) : 0;
  const safeTotal = Number.isFinite(total) ? Math.max(0, total) : 0;
  return {
    covered: safeCovered,
    total: safeTotal,
    percent: safeTotal > 0 ? Math.round((safeCovered / safeTotal) * 10_000) / 100 : null,
  };
}

function capability(result, name) {
  return result.result?.capabilities?.find((entry) => entry.capability === name) ?? null;
}

function tableCoverage(evidence, materializedRecords = 0) {
  const expected = evidence?.expectedRecords ?? evidence?.totalRecords;
  const covered = evidence?.coveredRecords ?? evidence?.validRecords;
  if (Number.isFinite(expected) && expected > 0) return ratio(covered ?? 0, expected);
  if (evidence?.status === "NOT_APPLICABLE") return ratio(0, 0);
  if (evidence?.status === "AVAILABLE" && materializedRecords > 0) {
    return ratio(materializedRecords, materializedRecords);
  }
  return ratio(0, evidence ? 1 : 0);
}

function validateParserResult(entry) {
  if (entry.error || entry.catalogError || entry.persistenceError) {
    throw new Error(`parser error for ${entry.result?.sha256 ?? "unknown identity"}`);
  }
  if (entry.result?.status !== "SELECTED") {
    throw new Error(`unselected parser result for ${entry.result?.sha256 ?? "unknown identity"}`);
  }
  if (!entry.catalog) throw new Error(`missing catalog for ${entry.result.sha256}`);
  if (!entry.persistence) throw new Error(`missing reopened catalog evidence for ${entry.result.sha256}`);
  if (entry.samples?.referenceErrors?.length) {
    throw new Error(`catalog reference errors for ${entry.result.sha256}`);
  }
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

export function buildPartyAnalysisCompatibility({ parserReport, liveReport, identities, date }) {
  if (parserReport.schemaVersion !== 12) {
    throw new Error(`expected parser report schema 12, got ${parserReport.schemaVersion}`);
  }
  const identityList = [...identities.official, ...identities.hacks];
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

  const liveById = new Map(liveReport.controls.map((control) => [control.id, control]));
  const controls = identityList.map((identity) => {
    const parser = parserByHash.get(identity.sha256.toLowerCase());
    const live = liveById.get(identity.id);
    if (!parser) throw new Error(`missing parser control ${identity.id}`);
    if (!live) throw new Error(`missing live control ${identity.id}`);
    const catalog = parser.catalog;
    const moves = ratio(catalog.movesWithDetails, catalog.moves);
    const moveCategories = ratio(catalog.movesWithCategories, catalog.moves);
    const typeChart = tableCoverage(capability(parser, "TYPE_CHART"), catalog.typeMatchups);
    const evolutions = tableCoverage(capability(parser, "EVOLUTIONS"), catalog.evolutionEdges);
    const provenAbilityModifiers = ratio(catalog.abilitiesWithProvenTypedModifiers, catalog.abilities);

    return {
      id: identity.id,
      name: DISPLAY_NAMES[identity.id] ?? identity.id,
      generation: identity.generation,
      sha256: identity.sha256,
      catalogPersistedAndReopened: true,
      coverage: {
        partyFields: ratio(live.party === 1 ? 6 : 0, 6),
        moves,
        moveCategories,
        typeChart,
        evolutions,
        provenAbilityModifiers: {
          ...provenAbilityModifiers,
          modifierRecords: catalog.provenTypedAbilityModifiers,
        },
      },
    };
  });

  const fields = [
    "partyFields",
    "moves",
    "moveCategories",
    "typeChart",
    "evolutions",
    "provenAbilityModifiers",
  ];
  return {
    schemaVersion: 1,
    date,
    feature: "Party Analysis",
    definitions: {
      partyFields: "Six live fields: species, level, HP, status, ability and moves.",
      moves: "ROM move records with type, power, accuracy and PP.",
      moveCategories: "ROM move records with a non-unknown physical, special or status category.",
      typeChart: "Semantically covered type-matchup records, or all materialized records when no semantic denominator exists.",
      evolutions: "Semantically covered evolution records, or all materialized edges when no semantic denominator exists.",
      provenAbilityModifiers: "Named abilities with at least one typed multiplicative mechanic proven from ROM data.",
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
  for (const required of ["parser", "live", "identities", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const [parserReport, liveReport, identities] = await Promise.all(
    [options.parser, options.live, options.identities].map(async (file) => JSON.parse(await readFile(file, "utf8"))),
  );
  const report = buildPartyAnalysisCompatibility({ parserReport, liveReport, identities, date: options.date });
  await mkdir(path.dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
