#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const BINDINGS = Object.freeze([
  "BADGE_COUNT",
  "ALL_BADGES",
  "REGIONAL_COLLECTION",
  "AREA_COLLECTIBLES",
  "GYM_LEADER_NO_ITEMS",
  "MINIGAME_RESULT",
]);

function ratio(covered, total) {
  return { covered, total, percent: total > 0 ? Math.round((covered / total) * 10_000) / 100 : null };
}

function indexed(items, label) {
  const result = new Map();
  for (const item of items ?? []) {
    if (!item?.id || result.has(item.id)) throw new Error(`duplicate or missing ${label} control ${item?.id ?? ""}`);
    result.set(item.id, item);
  }
  return result;
}

function validateTemplate(template) {
  if (!template?.key || !BINDINGS.includes(template.binding) || ![2, 3].includes(template.portabilityTier) ||
      !Array.isArray(template.requiredCatalogRoles) || template.requiredCatalogRoles.length !== 1 ||
      !Array.isArray(template.requiredCapabilities) || template.requiredCapabilities.length === 0 ||
      !["PLAYTHROUGH", "BATTLE_EPOCH", "AREA_EPOCH", "SESSION_EPOCH", "GAME_SPECIFIC"].includes(template.requiredTemporalWindow) ||
      template.organicSafe !== true || (template.portabilityTier === 3 && !(template.requiredAdapters?.length > 0))) {
    throw new Error(`invalid extended challenge template ${template?.key ?? "unknown"}`);
  }
}

function roleAvailable(template, inventory) {
  switch (template.binding) {
    case "BADGE_COUNT":
    case "ALL_BADGES": return inventory.badgeCount > 0;
    case "REGIONAL_COLLECTION": return inventory.regionalSpecies > 0;
    case "AREA_COLLECTIBLES": return inventory.collectibleAreas > 0;
    case "GYM_LEADER_NO_ITEMS": return inventory.gymLeaders > 0;
    case "MINIGAME_RESULT": return inventory.adapters > 0;
    default: return false;
  }
}

function observable(template, progress) {
  switch (template.binding) {
    case "BADGE_COUNT":
    case "ALL_BADGES": return progress.currentFields?.badges === true;
    case "REGIONAL_COLLECTION": return progress.currentFields?.caught === true;
    case "AREA_COLLECTIBLES": return progress.observableEvents?.pois === true;
    case "GYM_LEADER_NO_ITEMS": return progress.observableEvents?.trainerBattles === true &&
      progress.observableEvents?.battleItemUse === true;
    case "MINIGAME_RESULT": return progress.observableEvents?.minigameAdapter === true;
    default: return false;
  }
}

function expectedDefinitionCount(inventory) {
  return (inventory.badgeCount > 0 ? 2 : 0) +
    (inventory.regionalSpecies > 0 ? 1 : 0) +
    inventory.collectibleAreas + inventory.gymLeaders + inventory.adapters;
}

function combineCoverage(baseline, extension, denominator) {
  const covered = baseline.covered + extension.covered;
  return {
    ...ratio(covered, denominator),
    notApplicable: baseline.notApplicable ?? 0,
    notFound: extension.notFound ?? 0,
    error: baseline.error ?? 0,
  };
}

export function buildChallengeExpansionCompatibility({
  identities,
  progressReport,
  baselineCatalog,
  extendedCatalog,
  classification,
  inventoryManifest,
  date,
}) {
  const templates = extendedCatalog?.templates;
  if (extendedCatalog?.schema !== 1 || !Array.isArray(templates) || templates.length !== BINDINGS.length) {
    throw new Error("invalid extended challenge catalog");
  }
  templates.forEach(validateTemplate);
  if (new Set(templates.map((template) => template.key)).size !== templates.length ||
      new Set(templates.map((template) => template.binding)).size !== BINDINGS.length) {
    throw new Error("duplicate challenge template key or binding");
  }
  const baselineTotal = baselineCatalog?.challenges?.length;
  if (baselineCatalog?.schema !== 1 || baselineTotal !== 6) throw new Error("invalid baseline challenge catalog");

  const summary = classification?.summary;
  if (!Number.isInteger(summary?.total) || !Number.isInteger(summary?.classified) ||
      !Number.isInteger(summary?.expressible) || summary.classified > summary.total ||
      summary.expressible > summary.classified || summary.byTier?.["4"] !== summary.unclassified) {
    throw new Error("invalid challenge reference classification");
  }

  const identitiesList = [...(identities?.official ?? []), ...(identities?.hacks ?? [])];
  const progressById = indexed(progressReport?.controls, "progress");
  const inventoryById = indexed(inventoryManifest?.controls, "inventory");
  const seenHashes = new Set();
  const controls = identitiesList.map((identity) => {
    if (!identity?.id || !identity.sha256?.match(/^[0-9a-f]{64}$/i) || seenHashes.has(identity.sha256.toLowerCase())) {
      throw new Error(`invalid or duplicate expected identity ${identity?.id ?? ""}`);
    }
    seenHashes.add(identity.sha256.toLowerCase());
    const progress = progressById.get(identity.id);
    const inventory = inventoryById.get(identity.id);
    if (!progress) throw new Error(`missing progress control ${identity.id}`);
    if (!inventory) throw new Error(`missing inventory control ${identity.id}`);
    if (progress.sha256?.toLowerCase() !== identity.sha256.toLowerCase() ||
        inventory.sha256?.toLowerCase() !== identity.sha256.toLowerCase()) {
      throw new Error(`identity mismatch for ${identity.id}`);
    }
    if (expectedDefinitionCount(inventory) !== inventory.inventory) {
      throw new Error(`definition inventory mismatch for ${identity.id}`);
    }

    const applicableTemplates = templates.filter((template) => roleAvailable(template, inventory));
    const fullyObservableTemplates = applicableTemplates.filter((template) => observable(template, progress));
    const baselineApplicable = progress.coverage?.baselineApplicableTemplates;
    const baselineObservable = progress.coverage?.fullyObservableTemplates;
    const baselineValidated = progress.coverage?.validatedTemplates;
    if (![baselineApplicable, baselineObservable, baselineValidated].every((entry) => Number.isInteger(entry?.covered))) {
      throw new Error(`missing baseline coverage for ${identity.id}`);
    }

    const extensionApplicable = {
      ...ratio(applicableTemplates.length, templates.length),
      notFound: templates.length - applicableTemplates.length,
      notApplicable: 0,
    };
    const extensionObservable = {
      ...ratio(fullyObservableTemplates.length, applicableTemplates.length),
      notFound: applicableTemplates.length - fullyObservableTemplates.length,
    };
    const extensionValidated = {
      ...ratio(fullyObservableTemplates.length, fullyObservableTemplates.length),
      error: 0,
    };
    const combinedTotal = baselineTotal + templates.length;

    return {
      id: identity.id,
      name: progress.name ?? identity.id,
      generation: identity.generation,
      sha256: identity.sha256.toLowerCase(),
      resolvedRoles: {
        badgeCount: inventory.badgeCount,
        regionalSpecies: inventory.regionalSpecies,
        collectibleAreas: inventory.collectibleAreas,
        gymLeaders: inventory.gymLeaders,
        provenAdapters: inventory.adapters,
      },
      generatedDefinitions: inventory.inventory,
      applicableTemplateKeys: applicableTemplates.map((template) => template.key),
      fullyObservableTemplateKeys: fullyObservableTemplates.map((template) => template.key),
      coverage: {
        extensionApplicableTemplates: extensionApplicable,
        extensionFullyObservableTemplates: extensionObservable,
        extensionValidatedTemplates: extensionValidated,
        allApplicableTemplates: combineCoverage(baselineApplicable, extensionApplicable, combinedTotal),
        allFullyObservableTemplates: combineCoverage(
          baselineObservable,
          extensionObservable,
          baselineApplicable.covered + extensionApplicable.covered,
        ),
        allValidatedTemplates: combineCoverage(
          baselineValidated,
          extensionValidated,
          baselineObservable.covered + extensionObservable.covered,
        ),
      },
    };
  });

  if (controls.length !== 14 || progressById.size !== controls.length || inventoryById.size !== controls.length) {
    throw new Error(`expected exactly 14 controls, found ${controls.length}`);
  }
  const sum = (field, property) => controls.reduce((total, control) => total + control.coverage[field][property], 0);
  const aggregate = (field) => {
    const covered = sum(field, "covered");
    const total = sum(field, "total");
    return {
      ...ratio(covered, total),
      notApplicable: sum(field, "notApplicable"),
      notFound: sum(field, "notFound"),
      error: sum(field, "error"),
    };
  };

  return {
    schemaVersion: 1,
    date,
    feature: "Portable Challenge Engine Expansion",
    definitions: {
      templatesApplicable: "Offline templates whose complete parsed semantic role is resolved for the control.",
      templatesFullyObservable: "Applicable templates whose complete current-snapshot or journal inputs are observable.",
      templatesValidated: "Fully observable templates proven by deterministic binding, evaluation, identity, and mutation tests.",
    },
    reference: {
      descriptionsClassified: ratio(summary.classified, summary.total),
      templatesExpressible: ratio(summary.expressible, summary.classified),
      tier2Classified: summary.byTier?.["2"] ?? 0,
      tier3Classified: summary.byTier?.["3"] ?? 0,
      tier4ResearchExclusions: {
        count: summary.byTier?.["4"] ?? 0,
        outcome: "NOT_APPLICABLE",
      },
    },
    templateCatalog: { baseline: baselineTotal, extended: templates.length, total: baselineTotal + templates.length },
    controlCount: controls.length,
    controls,
    aggregate: {
      allApplicableTemplates: aggregate("allApplicableTemplates"),
      allFullyObservableTemplates: aggregate("allFullyObservableTemplates"),
      allValidatedTemplates: aggregate("allValidatedTemplates"),
    },
    unresolved: {
      gymLeaderRole: { controls: controls.filter((control) => control.resolvedRoles.gymLeaders === 0).length, outcome: "NOT_FOUND" },
      tier3Adapters: { controls: controls.filter((control) => control.resolvedRoles.provenAdapters === 0).length, outcome: "NOT_FOUND" },
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
  for (const required of ["identities", "progress", "baseline", "extended", "classification", "inventory", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const [identities, progressReport, baselineCatalog, extendedCatalog, classification, inventoryManifest] =
    await Promise.all(["identities", "progress", "baseline", "extended", "classification", "inventory"]
      .map(async (key) => JSON.parse(await readFile(options[key], "utf8"))));
  const report = buildChallengeExpansionCompatibility({
    identities, progressReport, baselineCatalog, extendedCatalog, classification, inventoryManifest, date: options.date,
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
