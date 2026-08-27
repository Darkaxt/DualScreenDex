import assert from "node:assert/strict";
import test from "node:test";

import { buildChallengeExpansionCompatibility } from "./challenge-expansion-compatibility.mjs";

const templates = [
  ["badge-one", "BADGE_COUNT", 2, "PLAYTHROUGH", []],
  ["badges-all", "ALL_BADGES", 2, "PLAYTHROUGH", []],
  ["regional", "REGIONAL_COLLECTION", 2, "PLAYTHROUGH", []],
  ["areas", "AREA_COLLECTIBLES", 2, "PLAYTHROUGH", []],
  ["leaders", "GYM_LEADER_NO_ITEMS", 2, "BATTLE_EPOCH", []],
  ["minigame", "MINIGAME_RESULT", 3, "GAME_SPECIFIC", ["MINIGAME"]],
].map(([key, binding, portabilityTier, requiredTemporalWindow, requiredAdapters]) => ({
  key,
  title: key,
  description: key,
  binding,
  portabilityTier,
  requiredTemporalWindow,
  requiredAdapters,
  requiredCapabilities: ["FACTS"],
  requiredCatalogRoles: [binding],
  organicSafe: true,
}));

function fixture() {
  const identities = {
    official: Array.from({ length: 11 }, (_, index) => ({
      id: `official-${index}`,
      generation: index < 6 ? 2 : 3,
      sha256: (index + 1).toString(16).padStart(64, "0"),
    })),
    hacks: Array.from({ length: 3 }, (_, index) => ({
      id: `hack-${index}`,
      generation: 3,
      sha256: (index + 12).toString(16).padStart(64, "0"),
    })),
  };
  const all = [...identities.official, ...identities.hacks];
  const progressReport = {
    controls: all.map((control, index) => ({
      ...control,
      name: control.id,
      currentFields: { badges: index >= 6, caught: index >= 6 },
      observableEvents: { pois: true, trainerBattles: true },
      coverage: {
        baselineApplicableTemplates: { covered: index >= 6 ? 6 : 3, total: 6, notApplicable: index >= 6 ? 0 : 3 },
        fullyObservableTemplates: { covered: index >= 6 ? 6 : 3, total: index >= 6 ? 6 : 3, notFound: 0 },
        validatedTemplates: { covered: index >= 6 ? 6 : 3, total: index >= 6 ? 6 : 3, error: 0 },
      },
    })),
  };
  const inventoryManifest = {
    controls: all.map((control, index) => ({
      id: control.id,
      sha256: control.sha256,
      inventory: index >= 6 ? 5 : 3,
      badgeCount: index >= 6 ? 8 : 0,
      regionalSpecies: 151,
      collectibleAreas: 2,
      gymLeaders: 0,
      adapters: 0,
    })),
  };
  return {
    identities,
    progressReport,
    baselineCatalog: { schema: 1, challenges: Array.from({ length: 6 }, (_, index) => ({ key: `base-${index}` })) },
    extendedCatalog: { schema: 1, templates: structuredClone(templates) },
    classification: { summary: { total: 1003, classified: 883, unclassified: 120, expressible: 883, byTier: { 2: 401, 3: 33, 4: 120 } } },
    inventoryManifest,
    date: "2026-08-27",
  };
}

test("reports the five numeric challenge measures across exactly fourteen controls", () => {
  const report = buildChallengeExpansionCompatibility(fixture());

  assert.deepEqual(report.reference.descriptionsClassified, { covered: 883, total: 1003, percent: 88.04 });
  assert.deepEqual(report.reference.templatesExpressible, { covered: 883, total: 883, percent: 100 });
  assert.deepEqual(report.controls[0].coverage.allApplicableTemplates, {
    covered: 5, total: 12, percent: 41.67, notApplicable: 3, notFound: 4, error: 0,
  });
  assert.deepEqual(report.controls[0].coverage.allFullyObservableTemplates, {
    covered: 4, total: 5, percent: 80, notApplicable: 0, notFound: 1, error: 0,
  });
  assert.deepEqual(report.controls[6].coverage.allApplicableTemplates, {
    covered: 10, total: 12, percent: 83.33, notApplicable: 0, notFound: 2, error: 0,
  });
  assert.deepEqual(report.controls[6].coverage.allValidatedTemplates, {
    covered: 10, total: 10, percent: 100, notApplicable: 0, notFound: 0, error: 0,
  });
  assert.equal(report.controlCount, 14);
  assert.deepEqual(report.errors, []);
});

test("rejects identity drift malformed temporal rules and inventory drift", () => {
  const identityDrift = fixture();
  identityDrift.inventoryManifest.controls[0].sha256 = "f".repeat(64);
  assert.throws(() => buildChallengeExpansionCompatibility(identityDrift), /identity mismatch/);

  const temporalDrift = fixture();
  temporalDrift.extendedCatalog.templates[0].requiredTemporalWindow = "FRAME_EXACT";
  assert.throws(() => buildChallengeExpansionCompatibility(temporalDrift), /invalid extended challenge template/);

  const inventoryDrift = fixture();
  inventoryDrift.inventoryManifest.controls[0].inventory = 99;
  assert.throws(() => buildChallengeExpansionCompatibility(inventoryDrift), /definition inventory mismatch/);
});
