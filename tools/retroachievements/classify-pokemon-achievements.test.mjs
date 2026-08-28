import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildClassificationReport,
  classifyAchievement,
  classifyReferenceCorpus,
  validateClassificationDocument,
} from "./classify-pokemon-achievements.mjs";

const EXTRACTED_AT = "2026-08-26T14:30:00.000Z";
const TEST_TEMP_ROOT = process.env.DUALDEX_TEST_TEMP
  || (process.platform === "win32" ? "D:\\Temp" : tmpdir());

test("classifies high-signal semantic families and independent constraints", () => {
  const cases = [
    ["Defeat the Champion and earn the final badge.", "PROGRESSION", 2],
    ["Catch 50 different Pokemon.", "COLLECTION", 1],
    ["Visit every area in Kanto.", "COMPLETION", 2],
    ["Build a full party using only Fire-type Pokemon.", "PARTY", 1],
    ["Defeat a trainer in a double battle.", "BATTLE", 1],
    ["Finish the journey without saving or resetting.", "SESSION", 2],
    ["Win 10 battles in a row.", "STREAK", 1],
    ["Win the Bug-Catching Contest.", "MINIGAME", 3],
    ["Encounter a Pokemon at night.", "TIME", 1],
    ["Trade a Pokemon.", "COLLECTION", 1],
    ["Receive a key item.", "COLLECTION", 1],
    ["Defeat a named opponent in a rematch.", "BATTLE", 1],
    ["Reach Saffron City.", "EXPLORATION", 2],
    ["Reach maximum friendship with a Pokemon.", "PARTY", 1],
    ["Collect every fossil.", "COMPLETION", 2],
    ["Fully furnish a secret base.", "GAME_SPECIFIC", 3],
  ];

  for (const [description, semanticFamily, portabilityTier] of cases) {
    const result = classifyAchievement(record(description));
    assert.equal(result.semanticFamily, semanticFamily, description);
    assert.equal(result.portabilityTier, portabilityTier, description);
    assert.equal(result.outcome, "CLASSIFIED", description);
  }

  const constrained = classifyAchievement(record(
    "Defeat the Champion without using items with a party of at most 3 Pokemon at level 50 or lower.",
  ));
  assert.deepEqual(constrained.constraints, [
    { kind: "MAX_LEVEL", value: 50 },
    { kind: "MAX_PARTY_SIZE", value: 3 },
    { kind: "ITEM_USE_POLICY", value: "FORBIDDEN" },
  ]);
  assert.deepEqual(constrained.requiredFacts, ["trainer.progress.milestone.earned"]);
  assert.deepEqual(constrained.requiredEvents, ["PROGRESSION_MILESTONE_EARNED"]);
  assert.deepEqual(constrained.requiredCatalogRoles, ["PROGRESSION_MILESTONE"]);
  assert.equal(constrained.temporalScope, "PLAYTHROUGH");
  assert.equal(constrained.knowledgeVisibility, "CAPABILITY_AND_KNOWLEDGE_GATED");
  assert.deepEqual(constrained.predicateOperators, [
    "FACT_TRUE",
    "VALUE_BELOW",
    "EVENT_ONCE",
    "NO_FORBIDDEN_EVENT",
    "COMPLETE",
  ]);
  assert.equal(constrained.templateTitle, "Progress milestone");
  assert.equal(constrained.templateDescription, "Recognize a proven milestone in the current playthrough.");
});

test("fails closed when prose has no unambiguous semantic signal", () => {
  const result = classifyAchievement(record("A strange secret awaits."));

  assert.equal(result.semanticFamily, "UNCLASSIFIED");
  assert.equal(result.outcome, "UNCLASSIFIED");
  assert.equal(result.portabilityTier, 4);
  assert.match(result.reason, /ambiguous/i);
  assert.deepEqual(result.constraints, []);
  assert.deepEqual(result.requiredFacts, []);
  assert.deepEqual(result.requiredEvents, []);
  assert.deepEqual(result.requiredCatalogRoles, []);
  assert.deepEqual(result.predicateOperators, []);
  assert.equal(result.temporalScope, "UNRESOLVED");
  assert.equal(result.knowledgeVisibility, "DEVELOPER_RESEARCH_ONLY");
  assert.equal(result.templateTitle, null);
  assert.equal(result.templateDescription, null);
});

test("applies a curated semantic override only to the exact source description fingerprint", () => {
  const achievement = record("Glance at a mysterious bird in the distance using the binoculars.", {
    sourceGameId: 586,
    achievementId: 540269,
  });
  const semanticOverride = {
    sourceGameId: 586,
    achievementId: 540269,
    sourceDescriptionSha256: sha256(achievement.description),
    semanticFamily: "GAME_SPECIFIC",
    portabilityTier: 3,
    recoveryPath: "GAME_SPECIFIC_ADAPTER",
  };

  const result = classifyAchievement(achievement, semanticOverride);

  assert.equal(result.semanticFamily, "GAME_SPECIFIC");
  assert.equal(result.portabilityTier, 3);
  assert.equal(result.outcome, "CLASSIFIED");
  assert.equal(result.recoveryPath, "GAME_SPECIFIC_ADAPTER");
  assert.match(result.reason, /curated semantic review/i);
  assert.deepEqual(result.requiredCapabilities, ["GAME_SPECIFIC_ADAPTER"]);
  assert.equal(result.templateKey, "GAME_SPECIFIC_ADAPTER");

  assert.throws(
    () => classifyAchievement(achievement, { ...semanticOverride, sourceDescriptionSha256: "0".repeat(64) }),
    /description fingerprint/i,
  );
});

test("rejects duplicate, orphaned, and malformed semantic overrides", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-overrides-"));
  try {
    const researchDirectory = join(root, "research");
    const achievement = record("A strange secret awaits.", {
      sourceGameId: 724,
      achievementId: 7,
    });
    const researchText = JSON.stringify({
      schema: 1,
      sourceSystem: "RetroAchievements",
      generation: 1,
      sourceGameId: 724,
      sourceGameTitle: "Pokemon Red Version",
      expectedTitle: "Pokemon Red Version",
      sourceUrl: "https://retroachievements.org/game/724",
      extractedAt: EXTRACTED_AT,
      sourceModifiedAt: "2026-01-02 00:00:00",
      achievements: [achievement],
    });
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), researchText, "utf8");
    const manifest = {
      schema: 1,
      extractedAt: EXTRACTED_AT,
      gameCount: 1,
      achievementCount: 1,
      games: [{
        gameId: 724,
        generation: 1,
        achievementCount: 1,
        researchFile: "game-724.json",
        researchSha256: sha256(researchText),
      }],
    };
    const validRecord = {
      sourceGameId: 724,
      achievementId: 7,
      sourceDescriptionSha256: sha256(achievement.description),
      semanticFamily: "PROGRESSION",
      portabilityTier: 2,
      recoveryPath: "PERSISTENT_SOURCE_FACT",
    };

    await assert.rejects(
      classifyReferenceCorpus({
        manifest,
        researchDirectory,
        semanticOverrides: { schema: 1, records: [validRecord, validRecord] },
      }),
      /duplicate semantic override/i,
    );
    await assert.rejects(
      classifyReferenceCorpus({
        manifest,
        researchDirectory,
        semanticOverrides: {
          schema: 1,
          records: [{ ...validRecord, achievementId: 8 }],
        },
      }),
      /orphaned semantic override/i,
    );
    await assert.rejects(
      classifyReferenceCorpus({
        manifest,
        researchDirectory,
        semanticOverrides: {
          schema: 1,
          records: [{ ...validRecord, recoveryPath: "TRIGGER_BYTECODE" }],
        },
      }),
      /recovery path/i,
    );
    await assert.rejects(
      classifyReferenceCorpus({
        manifest,
        researchDirectory,
        semanticOverrides: {
          schema: 1,
          records: [{ ...validRecord, semanticFamily: "UNCLASSIFIED" }],
        },
      }),
      /semantic family/i,
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("classifies sanitized research payloads deterministically without redistributing source prose", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-classify-"));
  try {
    const researchDirectory = join(root, "research");
    const researchText = JSON.stringify({
      schema: 1,
      sourceSystem: "RetroAchievements",
      generation: 1,
      sourceGameId: 724,
      sourceGameTitle: "Pokemon Red Version",
      expectedTitle: "Pokemon Red Version",
      sourceUrl: "https://retroachievements.org/game/724",
      extractedAt: EXTRACTED_AT,
      sourceModifiedAt: "2026-01-02 00:00:00",
      achievements: [
        record("Catch 10 different Pokemon.", { achievementId: 1, title: "Private source title one", displayOrder: 1 }),
        record("A strange secret awaits.", { achievementId: 2, title: "Private source title two", displayOrder: 2 }),
      ],
    });
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), researchText, "utf8");
    const manifest = {
      schema: 1,
      sourceSystem: "RetroAchievements",
      endpoint: "https://retroachievements.org/API/API_GetGameExtended.php",
      extractedAt: EXTRACTED_AT,
      gameCount: 1,
      achievementCount: 2,
      games: [{
        generation: 1,
        gameId: 724,
        expectedTitle: "Pokemon Red Version",
        sourceTitle: "Pokemon Red Version",
        sourceUrl: "https://retroachievements.org/game/724",
        sourceModifiedAt: "2026-01-02 00:00:00",
        achievementCount: 2,
        researchFile: "game-724.json",
        researchSha256: sha256(researchText),
      }],
    };

    const first = await classifyReferenceCorpus({ manifest, researchDirectory, classifiedAt: EXTRACTED_AT });
    const second = await classifyReferenceCorpus({ manifest, researchDirectory, classifiedAt: EXTRACTED_AT });
    const serialized = JSON.stringify(first);

    assert.deepEqual(first, second);
    assert.equal(validateClassificationDocument(first), true);
    assert.deepEqual(first.records.map(({ achievementId }) => achievementId), [1, 2]);
    assert.deepEqual(first.summary, {
      total: 2,
      classified: 1,
      unclassified: 1,
      expressible: 1,
      byFamily: {
        PROGRESSION: 0,
        COLLECTION: 1,
        EXPLORATION: 0,
        PARTY: 0,
        BATTLE: 0,
        SESSION: 0,
        STREAK: 0,
        MINIGAME: 0,
        TIME: 0,
        COMPLETION: 0,
        GAME_SPECIFIC: 0,
        UNCLASSIFIED: 1,
      },
      byTier: { "1": 1, "2": 0, "3": 0, "4": 1 },
      byRecoveryPath: {
        PERSISTENT_SOURCE_FACT: 0,
        NORMALIZED_LIVE_RULE: 0,
        GAME_SPECIFIC_ADAPTER: 0,
        SEQUENCE_SENSITIVE: 0,
      },
      byExclusionReason: { "description is ambiguous under the fail-closed vocabulary": 1 },
    });
    assert.equal(serialized.includes("Private source title"), false);
    assert.equal(serialized.includes("Catch 10 different"), false);
    assert.equal(serialized.includes("A strange secret"), false);
    assert.match(first.records[0].sourceDescriptionSha256, /^[a-f0-9]{64}$/);
    assert.equal(first.records[0].templateKey, "COLLECTION_TARGET");
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("uses the immutable manifest extraction time when no classification time is supplied", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-classified-at-"));
  try {
    const researchDirectory = join(root, "research");
    const researchText = JSON.stringify({
      schema: 1,
      sourceSystem: "RetroAchievements",
      generation: 1,
      sourceGameId: 724,
      sourceGameTitle: "Pokemon Red Version",
      expectedTitle: "Pokemon Red Version",
      sourceUrl: "https://retroachievements.org/game/724",
      extractedAt: EXTRACTED_AT,
      sourceModifiedAt: "2026-01-02 00:00:00",
      achievements: [record("Catch one Pokemon.")],
    });
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), researchText, "utf8");
    const manifest = {
      schema: 1,
      extractedAt: EXTRACTED_AT,
      gameCount: 1,
      achievementCount: 1,
      games: [{
        gameId: 724,
        generation: 1,
        achievementCount: 1,
        researchFile: "game-724.json",
        researchSha256: sha256(researchText),
      }],
    };

    const document = await classifyReferenceCorpus({ manifest, researchDirectory });

    assert.equal(document.classifiedAt, EXTRACTED_AT);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("rejects a research payload whose fingerprint no longer matches the manifest", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-mismatch-"));
  try {
    const researchDirectory = join(root, "research");
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), "{}", "utf8");
    await assert.rejects(
      classifyReferenceCorpus({
        manifest: {
          schema: 1,
          sourceSystem: "RetroAchievements",
          extractedAt: EXTRACTED_AT,
          gameCount: 1,
          achievementCount: 0,
          games: [{ gameId: 724, generation: 1, researchFile: "game-724.json", researchSha256: "0".repeat(64) }],
        },
        researchDirectory,
        classifiedAt: EXTRACTED_AT,
      }),
      /fingerprint mismatch.*game 724/i,
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("rejects an unsanitized API response even when its fingerprint matches", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-unsanitized-"));
  try {
    const researchDirectory = join(root, "research");
    const apiText = JSON.stringify({
      ID: 724,
      Title: "Pokemon Red Version",
      Achievements: { "1": { ID: 1, Title: "Source", Description: "Catch one Pokemon.", MemAddr: "0xH1234" } },
    });
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), apiText, "utf8");
    await assert.rejects(
      classifyReferenceCorpus({
        manifest: {
          schema: 1,
          sourceSystem: "RetroAchievements",
          extractedAt: EXTRACTED_AT,
          gameCount: 1,
          achievementCount: 1,
          games: [{
            gameId: 724,
            generation: 1,
            researchFile: "game-724.json",
            researchSha256: sha256(apiText),
          }],
        },
        researchDirectory,
        classifiedAt: EXTRACTED_AT,
      }),
      /sanitized research payload.*game 724/i,
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("renders numeric coverage and exclusion reasons in the research report", () => {
  const report = buildClassificationReport({
    schema: 1,
    sourceManifestSha256: "a".repeat(64),
    classifiedAt: EXTRACTED_AT,
    summary: {
      total: 10,
      classified: 8,
      unclassified: 2,
      expressible: 6,
      byFamily: { COLLECTION: 3, GAME_SPECIFIC: 2, UNCLASSIFIED: 2, BATTLE: 3 },
      byTier: { "1": 4, "2": 2, "3": 2, "4": 2 },
      byRecoveryPath: {
        PERSISTENT_SOURCE_FACT: 1,
        NORMALIZED_LIVE_RULE: 1,
        GAME_SPECIFIC_ADAPTER: 0,
        SEQUENCE_SENSITIVE: 0,
      },
      byExclusionReason: { "description is ambiguous under the fail-closed vocabulary": 2 },
    },
    records: [],
  });

  assert.match(report, /Classified.*8 \/ 10.*80\.00%/);
  assert.match(report, /Expressible.*6 \/ 8.*75\.00%/);
  assert.match(report, /UNCLASSIFIED.*2/);
  assert.match(report, /Exclusions by reason/);
  assert.match(report, /ambiguous under the fail-closed vocabulary.*2/);
  assert.match(report, /PERSISTENT_SOURCE_FACT.*1.*10\.00%/);
  assert.match(report, /NORMALIZED_LIVE_RULE.*1.*10\.00%/);
  assert.doesNotMatch(report, /good|partial|mostly/i);
});

test("publishes a closed semantic vocabulary schema", async () => {
  const schemaPath = fileURLToPath(new URL(
    "../../docs/research/retroachievements/semantic-vocabulary.schema.json",
    import.meta.url,
  ));
  const schema = JSON.parse(await readFile(schemaPath, "utf8"));

  assert.equal(schema.$schema, "https://json-schema.org/draft/2020-12/schema");
  assert.deepEqual(schema.required, [
    "schema",
    "sourceManifestSha256",
    "classifiedAt",
    "summary",
    "records",
  ]);
  assert.deepEqual(
    schema.$defs.semanticFamily.enum,
    [
      "PROGRESSION", "COLLECTION", "EXPLORATION", "PARTY", "BATTLE", "SESSION",
      "STREAK", "MINIGAME", "TIME", "COMPLETION", "GAME_SPECIFIC", "UNCLASSIFIED",
    ],
  );
  assert.ok(schema.$defs.constraint.properties.kind.enum.includes("MAX_PARTY_SIZE"));
  assert.ok(schema.$defs.constraint.properties.kind.enum.includes("NO_FORBIDDEN_EVENT"));
  assert.deepEqual(schema.$defs.predicateOperator.enum, [
    "FACT_TRUE", "FACT_FALSE", "VALUE_EQUALS", "VALUE_DIFFERS", "VALUE_EXCEEDS",
    "VALUE_BELOW", "SET_CONTAINS", "SET_COUNT", "EVENT_ONCE", "EVENT_COUNT",
    "EVENTS_IN_ORDER", "CONDITION_AT_BATTLE_START", "CONDITION_THROUGHOUT_BATTLE",
    "CONDITION_AT_BATTLE_COMPLETION", "NO_FORBIDDEN_EVENT", "BOUNDED_GROUP_COMPLETE",
    "PROGRESS_CURRENT_TARGET", "RESET", "PAUSE", "MISS", "COMPLETE",
  ]);
  for (const field of [
    "templateTitle", "templateDescription", "requiredFacts", "requiredEvents",
    "requiredCatalogRoles", "temporalScope", "predicateOperators", "knowledgeVisibility",
    "recoveryPath",
  ]) {
    assert.ok(schema.$defs.record.required.includes(field), field);
  }
  assert.deepEqual(schema.$defs.recoveryPath.enum, [
    "PERSISTENT_SOURCE_FACT",
    "NORMALIZED_LIVE_RULE",
    "GAME_SPECIFIC_ADAPTER",
    "SEQUENCE_SENSITIVE",
  ]);
  assert.deepEqual(schema.$defs.record.properties.recoveryPath.anyOf, [
    { $ref: "#/$defs/recoveryPath" },
    { type: "null" },
  ]);
  assert.ok(schema.$defs.summary.required.includes("byRecoveryPath"));
  assert.equal(schema.$defs.summary.properties.byRecoveryPath.additionalProperties, false);
  assert.deepEqual(schema.$defs.summary.properties.byRecoveryPath.required, [
    "PERSISTENT_SOURCE_FACT",
    "NORMALIZED_LIVE_RULE",
    "GAME_SPECIFIC_ADAPTER",
    "SEQUENCE_SENSITIVE",
  ]);
  assert.equal(schema.$defs.record.additionalProperties, false);
});

test("committed official corpus recovers every reviewed reference without trigger bytecode", async () => {
  const classificationPath = fileURLToPath(new URL(
    "../../docs/research/retroachievements/official-gen1-gen3-classification.json",
    import.meta.url,
  ));
  const document = JSON.parse(await readFile(classificationPath, "utf8"));
  const recoveryCounts = document.records.reduce((counts, entry) => {
    if (entry.recoveryPath !== null) counts[entry.recoveryPath] = (counts[entry.recoveryPath] ?? 0) + 1;
    return counts;
  }, {});

  assert.equal(document.summary.total, 1003);
  assert.equal(document.summary.classified, 1003);
  assert.equal(document.summary.unclassified, 0);
  assert.equal(document.summary.expressible, 1003);
  assert.deepEqual(recoveryCounts, {
    PERSISTENT_SOURCE_FACT: 56,
    NORMALIZED_LIVE_RULE: 13,
    GAME_SPECIFIC_ADAPTER: 43,
    SEQUENCE_SENSITIVE: 8,
  });
  assert.equal(JSON.stringify(document).includes("MemAddr"), false);
  assert.equal(JSON.stringify(document).includes("Trigger"), false);
});

test("classification validation rejects unknown recovery paths and inconsistent recovery counts", async () => {
  const classificationPath = fileURLToPath(new URL(
    "../../docs/research/retroachievements/official-gen1-gen3-classification.json",
    import.meta.url,
  ));
  const document = JSON.parse(await readFile(classificationPath, "utf8"));
  const recoveredIndex = document.records.findIndex((entry) => entry.recoveryPath !== null);
  assert.notEqual(recoveredIndex, -1);

  const unknownPath = structuredClone(document);
  unknownPath.records[recoveredIndex].recoveryPath = "TRIGGER_BYTECODE";
  assert.throws(() => validateClassificationDocument(unknownPath), /recovery path/i);

  const inconsistentSummary = structuredClone(document);
  inconsistentSummary.summary.byRecoveryPath.PERSISTENT_SOURCE_FACT -= 1;
  assert.throws(() => validateClassificationDocument(inconsistentSummary), /recovery summary/i);

  const inconsistentOutcome = structuredClone(document);
  inconsistentOutcome.records[recoveredIndex].outcome = "UNCLASSIFIED";
  assert.throws(() => validateClassificationDocument(inconsistentOutcome), /recovered classification/i);
});

function record(description, overrides = {}) {
  return {
    sourceSystem: "RetroAchievements",
    sourceGameId: 724,
    achievementId: 1,
    title: "Source title",
    description,
    officialClassification: null,
    displayOrder: 1,
    sourceModifiedAt: "2026-01-01 00:00:00",
    sourceUrl: "https://retroachievements.org/achievement/1",
    extractedAt: EXTRACTED_AT,
    ...overrides,
  };
}

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}
