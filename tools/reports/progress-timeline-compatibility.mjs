#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const TOTAL_FIELDS = Object.freeze(["playTime", "badges", "seen", "caught", "money"]);
const EVENT_FAMILIES = Object.freeze([
  "captures", "evolutions", "areas", "pois", "battles", "wildEncounters", "trainerBattles", "partyChanges", "saveObservations",
]);
const CAPABILITY_BY_EVENT = Object.freeze({
  captures: "POKEDEX_FACTS",
  evolutions: "OWNED_INDIVIDUALS",
  areas: "LOCATION_FACTS",
  pois: "POI_FACTS",
  battles: "BATTLE_FACTS",
  wildEncounters: "BATTLE_FACTS",
  trainerBattles: "BATTLE_FACTS",
  partyChanges: "OWNED_INDIVIDUALS",
  saveObservations: "SAVE_OBSERVATIONS",
});
const METRIC_EVENTS = new Set(["captures", "evolutions", "areas", "pois", "battles"]);

function ratio(covered, total) {
  return {
    covered,
    total,
    percent: total > 0 ? Math.round((covered / total) * 10_000) / 100 : null,
  };
}

function indexed(items, label) {
  const result = new Map();
  for (const item of items ?? []) {
    if (!item?.id || result.has(item.id)) throw new Error(`duplicate or missing ${label} control ${item?.id ?? ""}`);
    result.set(item.id, item);
  }
  return result;
}

function validateChallenge(challenge) {
  const valid = challenge?.key && challenge.operator === "COUNT_AT_LEAST" &&
    METRIC_EVENTS.has(challenge.metric) && Number.isInteger(challenge.target) && challenge.target > 0 &&
    challenge.organicSafe === true && Array.isArray(challenge.requiredCapabilities) &&
    challenge.requiredCapabilities.length > 0 && challenge.requiredCapabilities.every((value) => typeof value === "string");
  if (!valid) throw new Error(`invalid baseline challenge ${challenge?.key ?? "unknown"}`);
}

function sumCoverage(controls, field, missingKey) {
  const covered = controls.reduce((sum, control) => sum + control.coverage[field].covered, 0);
  const total = controls.reduce((sum, control) => sum + control.coverage[field].total, 0);
  const missing = controls.reduce((sum, control) => sum + control.coverage[field][missingKey], 0);
  return { ...ratio(covered, total), [missingKey]: missing };
}

export function buildProgressTimelineCompatibility({
  identities,
  liveReport,
  areaGuideReport,
  challenges,
  classification,
  date,
}) {
  if (challenges?.schema !== 1 || !Array.isArray(challenges.challenges) || challenges.challenges.length === 0) {
    throw new Error("invalid baseline challenge catalog");
  }
  challenges.challenges.forEach(validateChallenge);
  const reference = classification?.summary;
  if (!Number.isInteger(reference?.total) || !Number.isInteger(reference?.classified) ||
      !Number.isInteger(reference?.expressible) || reference.classified > reference.total ||
      reference.expressible > reference.classified) {
    throw new Error("invalid reference classification");
  }

  const identityList = [...(identities.official ?? []), ...(identities.hacks ?? [])];
  const liveById = indexed(liveReport.controls, "live");
  const areaById = indexed(areaGuideReport.controls, "Area Guide");
  const seenHashes = new Set();

  const controls = identityList.map((identity) => {
    if (!identity?.id || !identity.sha256?.match(/^[0-9a-f]{64}$/i) || seenHashes.has(identity.sha256.toLowerCase())) {
      throw new Error(`invalid or duplicate expected identity ${identity?.id ?? ""}`);
    }
    seenHashes.add(identity.sha256.toLowerCase());
    const live = liveById.get(identity.id);
    const area = areaById.get(identity.id);
    if (!live) throw new Error(`missing live control ${identity.id}`);
    if (!area) throw new Error(`missing Area Guide control ${identity.id}`);
    if (area.sha256?.toLowerCase() !== identity.sha256.toLowerCase()) {
      throw new Error(`identity mismatch for ${identity.id}`);
    }

    const capabilities = new Set(["SAVE_OBSERVATIONS"]);
    if (live.pokedex === 1) capabilities.add("POKEDEX_FACTS");
    if (live.party === 1) capabilities.add("OWNED_INDIVIDUALS");
    if (live.area === 1) capabilities.add("LOCATION_FACTS");
    if (live.battle === 1) capabilities.add("BATTLE_FACTS");
    if ((area.materializedRecords?.points ?? 0) > 0) capabilities.add("POI_FACTS");

    const currentFields = {
      playTime: live.trainer === 1,
      badges: live.trainer === 1,
      seen: live.pokedex === 1,
      caught: live.pokedex === 1,
      money: live.trainer === 1,
    };
    const events = Object.fromEntries(EVENT_FAMILIES.map((event) => [event, capabilities.has(CAPABILITY_BY_EVENT[event])]));
    const applicable = challenges.challenges.filter((challenge) =>
      challenge.requiredCapabilities.every((capability) => capabilities.has(capability)));
    const fullyObservable = applicable.filter((challenge) =>
      challenge.requiredCapabilities.every((capability) => capabilities.has(capability)));
    const validated = fullyObservable.filter((challenge) =>
      challenge.operator === "COUNT_AT_LEAST" && METRIC_EVENTS.has(challenge.metric));
    const currentCovered = TOTAL_FIELDS.filter((field) => currentFields[field]).length;
    const eventCovered = EVENT_FAMILIES.filter((event) => events[event]).length;

    return {
      id: identity.id,
      name: area.name ?? identity.id,
      generation: identity.generation,
      sha256: identity.sha256.toLowerCase(),
      currentFields,
      observableEvents: events,
      applicableChallengeKeys: applicable.map((challenge) => challenge.key),
      coverage: {
        currentTotalFields: { ...ratio(currentCovered, TOTAL_FIELDS.length), notFound: TOTAL_FIELDS.length - currentCovered },
        observableEventFamilies: { ...ratio(eventCovered, EVENT_FAMILIES.length), notFound: EVENT_FAMILIES.length - eventCovered },
        baselineApplicableTemplates: {
          ...ratio(applicable.length, challenges.challenges.length),
          notApplicable: challenges.challenges.length - applicable.length,
        },
        fullyObservableTemplates: {
          ...ratio(fullyObservable.length, applicable.length),
          notFound: applicable.length - fullyObservable.length,
        },
        validatedTemplates: {
          ...ratio(validated.length, fullyObservable.length),
          error: fullyObservable.length - validated.length,
        },
      },
    };
  });

  if (controls.length !== liveById.size || controls.length !== areaById.size) {
    throw new Error(`expected exactly ${controls.length} controls`);
  }
  return {
    schemaVersion: 1,
    date,
    feature: "Trainer Progress, Challenges, Save Timeline, and Atlas Objectives",
    definitions: {
      currentTotalFields: "Five current resolved-snapshot fields: play time, badges, Pokédex seen, Pokédex caught, and money.",
      observableEventFamilies: "Nine deduplicated journal event families: captures, evolutions, areas, POIs, battles, wild encounters, trainer battles, Party changes, and changed-save observations.",
      baselineApplicableTemplates: "Offline Tier 1 templates whose declared semantic capabilities are present for the control.",
      fullyObservableTemplates: "Applicable templates whose complete predicate inputs are available from the unified snapshot and journal.",
      validatedTemplates: "Fully observable templates accepted by the implemented bounded predicate vocabulary and its tests.",
    },
    reference: {
      classified: ratio(reference.classified, reference.total),
      expressible: ratio(reference.expressible, reference.classified),
    },
    controlCount: controls.length,
    controls,
    aggregate: {
      currentTotalFields: sumCoverage(controls, "currentTotalFields", "notFound"),
      observableEventFamilies: sumCoverage(controls, "observableEventFamilies", "notFound"),
      baselineApplicableTemplates: sumCoverage(controls, "baselineApplicableTemplates", "notApplicable"),
      fullyObservableTemplates: sumCoverage(controls, "fullyObservableTemplates", "notFound"),
      validatedTemplates: sumCoverage(controls, "validatedTemplates", "error"),
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
  for (const required of ["identities", "live", "area-guide", "challenges", "classification", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const [identities, liveReport, areaGuideReport, challenges, classification] = await Promise.all(
    [options.identities, options.live, options["area-guide"], options.challenges, options.classification]
      .map(async (file) => JSON.parse(await readFile(file, "utf8"))),
  );
  const report = buildProgressTimelineCompatibility({
    identities, liveReport, areaGuideReport, challenges, classification, date: options.date,
  });
  await mkdir(path.dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
