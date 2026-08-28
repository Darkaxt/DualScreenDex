import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const FAMILY_RULES = Object.freeze([
  family("SESSION", /\b(without (?:saving|resetting|healing)|no resets?|one session|single session)\b/i, 2, "SESSION_CONDITION"),
  family("STREAK", /\b(consecutive|in a row|streak)\b/i, 1, "STREAK_TARGET"),
  family("MINIGAME", /\b(contest|game corner|slot machine|lottery|bug-catching)\b/i, 3, "MINIGAME_RESULT"),
  family("COMPLETION", /\b(all|every|entire)\b/i, 2, "BOUNDED_GROUP_COMPLETION"),
  family("PROGRESSION", /\b(defeat|beat|earn|obtain|finish|clear)\b.*\b(champion|badges?|gym leaders?|elite four|rival|story|journey|game)\b/i, 2, "PROGRESSION_MILESTONE"),
  family("COLLECTION", /\b(catch|capture|obtain|own|register|trade|receive|acquire|collect|buy|claim|steal|get|found|complete the pok[eé]dex)\b/i, 1, "COLLECTION_TARGET"),
  family("TIME", /\b(at night|during the day|in the morning|on (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b/i, 1, "TIME_WINDOW_EVENT"),
  family("EXPLORATION", /\b(visit|enter|discover|explore|reach|return to)\b.*\b(area|route|city|town|cave|forest|island|location|place|tower|center)\b/i, 2, "EXPLORATION_TARGET"),
  family("PARTY", /\b(party|team|friendship|mood)\b/i, 1, "PARTY_CONDITION"),
  family("BATTLE", /\b(defeat|beat|win|knock out|faint|rematch)\b/i, 1, "BATTLE_RESULT"),
  family("GAME_SPECIFIC", /\b(pok[eé]athlon|battle frontier symbol|voltorb flip|trainer hill|berry blender|secret base)\b/i, 3, "GAME_SPECIFIC_ADAPTER"),
]);

const SEMANTIC_FAMILIES = Object.freeze([
  "PROGRESSION", "COLLECTION", "EXPLORATION", "PARTY", "BATTLE", "SESSION",
  "STREAK", "MINIGAME", "TIME", "COMPLETION", "GAME_SPECIFIC", "UNCLASSIFIED",
]);

const PORTABLE_PREDICATE_OPERATORS = Object.freeze([
  "FACT_TRUE", "FACT_FALSE", "VALUE_EQUALS", "VALUE_DIFFERS", "VALUE_EXCEEDS",
  "VALUE_BELOW", "SET_CONTAINS", "SET_COUNT", "EVENT_ONCE", "EVENT_COUNT",
  "EVENTS_IN_ORDER", "CONDITION_AT_BATTLE_START", "CONDITION_THROUGHOUT_BATTLE",
  "CONDITION_AT_BATTLE_COMPLETION", "NO_FORBIDDEN_EVENT", "BOUNDED_GROUP_COMPLETE",
  "PROGRESS_CURRENT_TARGET", "RESET", "PAUSE", "MISS", "COMPLETE",
]);

const FAMILY_SEMANTICS = Object.freeze({
  PROGRESSION: semantics(
    "Progress milestone",
    "Recognize a proven milestone in the current playthrough.",
    ["trainer.progress.milestone.earned"],
    ["PROGRESSION_MILESTONE_EARNED"],
    ["PROGRESSION_MILESTONE"],
    "PLAYTHROUGH",
    ["FACT_TRUE", "EVENT_ONCE", "COMPLETE"],
  ),
  COLLECTION: semantics(
    "Collection target",
    "Track progress toward a proven collection target.",
    ["collection.entity.obtained"],
    ["COLLECTION_ENTITY_OBTAINED"],
    ["COLLECTION_ENTITY"],
    "PLAYTHROUGH",
    ["SET_CONTAINS", "SET_COUNT", "PROGRESS_CURRENT_TARGET", "COMPLETE"],
  ),
  EXPLORATION: semantics(
    "Exploration target",
    "Recognize a proven visit to a mapped area.",
    ["location.area.visited"],
    ["AREA_ENTERED"],
    ["AREA"],
    "AREA_EPOCH",
    ["FACT_TRUE", "EVENT_ONCE", "COMPLETE"],
  ),
  PARTY: semantics(
    "Party condition",
    "Evaluate a proven condition against the current party.",
    ["party.composition"],
    ["PARTY_CHANGED"],
    ["SPECIES", "TYPE"],
    "CURRENT_SNAPSHOT",
    ["VALUE_EQUALS", "SET_CONTAINS", "SET_COUNT", "COMPLETE"],
  ),
  BATTLE: semantics(
    "Battle condition",
    "Recognize a proven outcome within one battle.",
    ["battle.active", "battle.kind"],
    ["BATTLE_STARTED", "BATTLE_WON"],
    ["BATTLE_OPPONENT"],
    "BATTLE_EPOCH",
    ["EVENT_ONCE", "CONDITION_AT_BATTLE_COMPLETION", "COMPLETE"],
  ),
  SESSION: semantics(
    "Session condition",
    "Track a proven condition within one uninterrupted session.",
    ["session.epoch"],
    ["SESSION_STARTED", "SESSION_RESET"],
    [],
    "SESSION_EPOCH",
    ["CONDITION_THROUGHOUT_BATTLE", "NO_FORBIDDEN_EVENT", "RESET", "PAUSE", "MISS", "COMPLETE"],
  ),
  STREAK: semantics(
    "Consecutive-event target",
    "Count consecutive proven events without an invalidating break.",
    ["streak.current"],
    ["METRIC_COUNTER_ADVANCED"],
    [],
    "SESSION_EPOCH",
    ["EVENT_COUNT", "PROGRESS_CURRENT_TARGET", "RESET", "COMPLETE"],
  ),
  MINIGAME: semantics(
    "Minigame result",
    "Recognize a proven result from a mapped minigame.",
    ["minigame.result"],
    ["MINIGAME_COMPLETED"],
    ["MINIGAME"],
    "GAME_SPECIFIC",
    ["EVENT_ONCE", "VALUE_EQUALS", "COMPLETE"],
  ),
  TIME: semantics(
    "Time-window event",
    "Recognize a proven event during a resolved game-time period.",
    ["clock.period"],
    ["TIME_WINDOW_EVENT"],
    [],
    "CURRENT_SNAPSHOT",
    ["VALUE_EQUALS", "EVENT_ONCE", "COMPLETE"],
  ),
  COMPLETION: semantics(
    "Bounded completion target",
    "Track completion across a proven bounded catalog group.",
    ["catalog.group.completed"],
    ["COMPLETION_COUNTER_ADVANCED"],
    ["BOUNDED_CATALOG_GROUP"],
    "PLAYTHROUGH",
    ["BOUNDED_GROUP_COMPLETE", "PROGRESS_CURRENT_TARGET", "COMPLETE"],
  ),
  GAME_SPECIFIC: semantics(
    "Mapped mechanic target",
    "Recognize a proven result from a game-specific mechanic adapter.",
    ["game.mechanic.state"],
    ["GAME_MECHANIC_EVENT"],
    ["GAME_SPECIFIC_MECHANIC"],
    "GAME_SPECIFIC",
    ["EVENT_ONCE", "VALUE_EQUALS", "COMPLETE"],
  ),
});

const REQUIRED_CAPABILITIES = Object.freeze({
  PROGRESSION: ["PROGRESSION_FACTS"],
  COLLECTION: ["POKEDEX_FACTS"],
  EXPLORATION: ["LOCATION_EVENTS"],
  PARTY: ["PARTY_FACTS"],
  BATTLE: ["BATTLE_EVENTS"],
  SESSION: ["SESSION_EPOCH", "EVENT_HISTORY"],
  STREAK: ["EVENT_HISTORY"],
  MINIGAME: ["MINIGAME_ADAPTER"],
  TIME: ["GAME_CLOCK", "EVENT_HISTORY"],
  COMPLETION: ["CATALOG_GROUP", "COMPLETION_FACTS"],
  GAME_SPECIFIC: ["GAME_SPECIFIC_ADAPTER"],
  UNCLASSIFIED: [],
});

const RECOVERY_PATH_TIERS = Object.freeze({
  PERSISTENT_SOURCE_FACT: 2,
  NORMALIZED_LIVE_RULE: 2,
  GAME_SPECIFIC_ADAPTER: 3,
  SEQUENCE_SENSITIVE: 3,
});

const SEMANTIC_OVERRIDE_FIELDS = Object.freeze([
  "sourceGameId",
  "achievementId",
  "sourceDescriptionSha256",
  "semanticFamily",
  "portabilityTier",
  "recoveryPath",
]);

export function classifyAchievement(achievement, semanticOverride = null) {
  if (!achievement || typeof achievement.description !== "string") {
    throw new TypeError("achievement.description must be a string");
  }
  const description = achievement.description.trim();
  const sourceGameId = requiredInteger(achievement.sourceGameId, "sourceGameId");
  const achievementId = requiredInteger(achievement.achievementId, "achievementId");
  const descriptionFingerprint = sha256(description);
  if (semanticOverride !== null) {
    validateSemanticOverrideRecord(semanticOverride);
    if (semanticOverride.sourceGameId !== sourceGameId || semanticOverride.achievementId !== achievementId) {
      throw new Error(`semantic override identity mismatch for ${sourceGameId}:${achievementId}`);
    }
    if (semanticOverride.sourceDescriptionSha256 !== descriptionFingerprint) {
      throw new Error(`semantic override description fingerprint mismatch for ${sourceGameId}:${achievementId}`);
    }
  }
  const matched = semanticOverride
    ? FAMILY_RULES.find(({ name }) => name === semanticOverride.semanticFamily)
    : FAMILY_RULES.find(({ pattern }) => pattern.test(description));
  const semanticFamily = semanticOverride?.semanticFamily ?? matched?.name ?? "UNCLASSIFIED";
  const outcome = matched ? "CLASSIFIED" : "UNCLASSIFIED";
  const portabilityTier = semanticOverride?.portabilityTier ?? matched?.portabilityTier ?? 4;
  const constraints = extractConstraints(description);
  const semantic = matched ? FAMILY_SEMANTICS[semanticFamily] : null;
  return {
    sourceGameId,
    achievementId,
    sourceUrl: nullableString(achievement.sourceUrl),
    sourceModifiedAt: nullableString(achievement.sourceModifiedAt),
    officialClassification: nullableString(achievement.officialClassification),
    sourceTitleSha256: sha256(requiredString(achievement.title, "title")),
    sourceDescriptionSha256: descriptionFingerprint,
    semanticFamily,
    constraints,
    portabilityTier,
    templateKey: matched?.templateKey ?? null,
    templateTitle: semantic?.templateTitle ?? null,
    templateDescription: semantic?.templateDescription ?? null,
    requiredFacts: semantic?.requiredFacts ?? [],
    requiredEvents: semantic?.requiredEvents ?? [],
    requiredCatalogRoles: semantic?.requiredCatalogRoles ?? [],
    temporalScope: semantic?.temporalScope ?? "UNRESOLVED",
    predicateOperators: semantic
      ? derivePredicateOperators(semantic.predicateOperators, constraints)
      : [],
    knowledgeVisibility: semantic
      ? "CAPABILITY_AND_KNOWLEDGE_GATED"
      : "DEVELOPER_RESEARCH_ONLY",
    requiredCapabilities: REQUIRED_CAPABILITIES[semanticFamily],
    recoveryPath: semanticOverride?.recoveryPath ?? null,
    outcome,
    reason: semanticOverride
      ? `curated semantic review: ${semanticOverride.recoveryPath}`
      : matched
        ? `matched high-signal ${semanticFamily.toLowerCase()} language`
      : "description is ambiguous under the fail-closed vocabulary",
  };
}

export async function classifyReferenceCorpus({ manifest, researchDirectory, classifiedAt, semanticOverrides = null }) {
  validateManifest(manifest);
  const overrideIndex = indexSemanticOverrides(semanticOverrides);
  const usedOverrideKeys = new Set();
  const records = [];
  for (const game of manifest.games) {
    const researchText = await readFile(join(researchDirectory, game.researchFile), "utf8");
    if (sha256(researchText) !== game.researchSha256) {
      throw new Error(`fingerprint mismatch for game ${game.gameId}`);
    }
    let payload;
    try {
      payload = JSON.parse(researchText);
    } catch {
      throw new Error(`invalid JSON in research payload for game ${game.gameId}`);
    }
    validateSanitizedResearchPayload({ game, payload });
    for (const achievement of payload.achievements) {
      const key = semanticOverrideKey(achievement.sourceGameId, achievement.achievementId);
      const semanticOverride = overrideIndex.get(key) ?? null;
      if (semanticOverride !== null) usedOverrideKeys.add(key);
      records.push({
        generation: game.generation,
        displayOrder: achievement.displayOrder,
        ...classifyAchievement(achievement, semanticOverride),
      });
    }
  }
  const orphaned = [...overrideIndex.keys()].filter((key) => !usedOverrideKeys.has(key));
  if (orphaned.length > 0) {
    throw new Error(`orphaned semantic override: ${orphaned.sort()[0]}`);
  }
  records.sort((left, right) =>
    left.generation - right.generation
      || left.sourceGameId - right.sourceGameId
      || left.displayOrder - right.displayOrder
      || left.achievementId - right.achievementId,
  );
  const summary = summarize(records);
  const document = {
    schema: 1,
    sourceManifestSha256: sha256(stableJson(manifest)),
    classifiedAt: classifiedAt ?? manifest.extractedAt,
    summary,
    records,
  };
  validateClassificationDocument(document);
  return document;
}

function indexSemanticOverrides(document) {
  if (document === null || document === undefined) return new Map();
  if (!document || document.schema !== 1 || !Array.isArray(document.records)) {
    throw new TypeError("semantic overrides must use schema 1 and contain records");
  }
  const indexed = new Map();
  for (const record of document.records) {
    validateSemanticOverrideRecord(record);
    const key = semanticOverrideKey(record.sourceGameId, record.achievementId);
    if (indexed.has(key)) throw new Error(`duplicate semantic override: ${key}`);
    indexed.set(key, record);
  }
  return indexed;
}

function validateSemanticOverrideRecord(record) {
  if (!record || typeof record !== "object" || Array.isArray(record)) {
    throw new TypeError("semantic override must be an object");
  }
  const fields = Object.keys(record);
  if (fields.length !== SEMANTIC_OVERRIDE_FIELDS.length || fields.some((field) => !SEMANTIC_OVERRIDE_FIELDS.includes(field))) {
    throw new TypeError("semantic override contains an unsupported field");
  }
  requiredInteger(record.sourceGameId, "semantic override sourceGameId");
  requiredInteger(record.achievementId, "semantic override achievementId");
  if (typeof record.sourceDescriptionSha256 !== "string" || !/^[a-f0-9]{64}$/.test(record.sourceDescriptionSha256)) {
    throw new TypeError("semantic override requires a description fingerprint");
  }
  if (!Object.hasOwn(FAMILY_SEMANTICS, record.semanticFamily) || record.semanticFamily === "UNCLASSIFIED") {
    throw new TypeError(`semantic override contains unknown semantic family ${String(record.semanticFamily)}`);
  }
  if (!Object.hasOwn(RECOVERY_PATH_TIERS, record.recoveryPath)) {
    throw new TypeError(`semantic override contains unknown recovery path ${String(record.recoveryPath)}`);
  }
  if (record.portabilityTier !== RECOVERY_PATH_TIERS[record.recoveryPath]) {
    throw new TypeError(`semantic override portability tier does not match recovery path ${record.recoveryPath}`);
  }
}

function semanticOverrideKey(sourceGameId, achievementId) {
  return `${sourceGameId}:${achievementId}`;
}

export function validateClassificationDocument(document) {
  if (!document || document.schema !== 1 || !Array.isArray(document.records)) {
    throw new TypeError("classification document must use schema 1 and contain records");
  }
  if (!document.summary || document.summary.total !== document.records.length) {
    throw new TypeError("classification summary total must match record count");
  }
  const recoveryCounts = Object.fromEntries(Object.keys(RECOVERY_PATH_TIERS).map((path) => [path, 0]));
  for (const record of document.records) {
    if (!Number.isSafeInteger(record.sourceGameId) || !Number.isSafeInteger(record.achievementId)) {
      throw new TypeError("classification records require integer source IDs");
    }
    if (!/^[a-f0-9]{64}$/.test(record.sourceDescriptionSha256)) {
      throw new TypeError("classification records require a source description SHA-256");
    }
    if (!Array.isArray(record.constraints) || !Array.isArray(record.requiredCapabilities)) {
      throw new TypeError("classification constraints and capabilities must be arrays");
    }
    for (const field of ["requiredFacts", "requiredEvents", "requiredCatalogRoles", "predicateOperators"]) {
      if (!Array.isArray(record[field])) {
        throw new TypeError(`classification ${field} must be an array`);
      }
    }
    if (!record.predicateOperators.every((operator) => PORTABLE_PREDICATE_OPERATORS.includes(operator))) {
      throw new TypeError("classification contains an unknown predicate operator");
    }
    if (typeof record.temporalScope !== "string" || typeof record.knowledgeVisibility !== "string") {
      throw new TypeError("classification requires temporal scope and knowledge visibility");
    }
    if (!Number.isInteger(record.portabilityTier) || record.portabilityTier < 1 || record.portabilityTier > 4) {
      throw new TypeError("classification portability tier must be between 1 and 4");
    }
    if (record.recoveryPath !== null) {
      if (!Object.hasOwn(RECOVERY_PATH_TIERS, record.recoveryPath)) {
        throw new TypeError(`classification contains unknown recovery path ${String(record.recoveryPath)}`);
      }
      if (record.portabilityTier !== RECOVERY_PATH_TIERS[record.recoveryPath]) {
        throw new TypeError(`classification recovery path tier mismatch for ${record.recoveryPath}`);
      }
      if (record.outcome !== "CLASSIFIED" || record.semanticFamily === "UNCLASSIFIED") {
        throw new TypeError("recovered classification must have a classified semantic family");
      }
      recoveryCounts[record.recoveryPath] += 1;
    }
    if (!Object.hasOwn(REQUIRED_CAPABILITIES, record.semanticFamily)) {
      throw new TypeError(`unknown semantic family ${String(record.semanticFamily)}`);
    }
  }
  if (
    !document.summary.byRecoveryPath
    || Object.keys(recoveryCounts).some((path) => document.summary.byRecoveryPath[path] !== recoveryCounts[path])
    || Object.keys(document.summary.byRecoveryPath).some((path) => !Object.hasOwn(recoveryCounts, path))
  ) {
    throw new TypeError("classification recovery summary does not match records");
  }
  return true;
}

export function buildClassificationReport(document) {
  const { summary } = document;
  const classifiedPercent = percentage(summary.classified, summary.total);
  const expressiblePercent = percentage(summary.expressible, summary.classified);
  const familyRows = Object.entries(summary.byFamily)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([familyName, count]) => `| ${familyName} | ${count} | ${percentage(count, summary.total)} |`)
    .join("\n");
  const tierRows = Object.entries(summary.byTier)
    .sort(([left], [right]) => Number(left) - Number(right))
    .map(([tier, count]) => `| ${tier} | ${count} | ${percentage(count, summary.total)} |`)
    .join("\n");
  const recoveryRows = Object.entries(summary.byRecoveryPath ?? {})
    .map(([path, count]) => `| ${path} | ${count} | ${percentage(count, summary.total)} |`)
    .join("\n") || "| None | 0 | 0.00% |";
  const exclusionRows = Object.entries(summary.byExclusionReason ?? {})
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([reason, count]) => `| ${reason} | ${count} | ${percentage(count, summary.total)} |`)
    .join("\n") || "| None | 0 | 0.00% |";
  return `# Official Generation I–III Achievement Semantic Classification

| Measurement | Count | Percentage |
| --- | ---: | ---: |
| Extracted | ${summary.total} / ${summary.total} | ${percentage(summary.total, summary.total)} |
| Classified | ${summary.classified} / ${summary.total} | ${classifiedPercent} |
| Expressible | ${summary.expressible} / ${summary.classified} | ${expressiblePercent} |
| Unclassified | ${summary.unclassified} / ${summary.total} | ${percentage(summary.unclassified, summary.total)} |

## Semantic families

| Family | Count | Percentage of extracted |
| --- | ---: | ---: |
${familyRows}

## Portability tiers

| Tier | Count | Percentage of extracted |
| ---: | ---: | ---: |
${tierRows}

## Curated semantic recovery paths

| Recovery path | Count | Percentage of extracted |
| --- | ---: | ---: |
${recoveryRows}

## Exclusions by reason

| Reason | Count | Percentage of extracted |
| --- | ---: | ---: |
${exclusionRows}

The derived repository artifact contains source identifiers, hashes, semantic classifications, constraints, semantic facts/events/catalog roles, temporal scope, predicate vocabulary, capability requirements, and independently worded generic templates. Verbatim source titles and descriptions remain only in the uncommitted authenticated research payloads.
`;
}

export async function writeClassificationArtifacts({ document, outputPath, reportPath }) {
  validateClassificationDocument(document);
  await atomicWrite(outputPath, `${JSON.stringify(document, null, 2)}\n`);
  await atomicWrite(reportPath, buildClassificationReport(document));
}

function extractConstraints(description) {
  const constraints = [];
  const maxLevel = description.match(/\b(?:level|lv\.?)[ ]*(\d+)[ ]*(?:or lower|or less|or below|maximum|max)?\b/i);
  if (maxLevel && /or lower|or less|or below|maximum|max/i.test(maxLevel[0])) {
    constraints.push({ kind: "MAX_LEVEL", value: Number(maxLevel[1]) });
  }
  const maxParty = description.match(/\bparty of (?:at most|no more than|up to)[ ]*(\d+)\b/i);
  if (maxParty) constraints.push({ kind: "MAX_PARTY_SIZE", value: Number(maxParty[1]) });
  if (/\b(without using (?:any )?items?|no items?)\b/i.test(description)) {
    constraints.push({ kind: "ITEM_USE_POLICY", value: "FORBIDDEN" });
  }
  if (/\b(without (?:saving|resetting)|no resets?)\b/i.test(description)) {
    constraints.push({ kind: "RESET_POLICY", value: "FORBIDDEN" });
  }
  const timeWindow = description.match(/\b(night|day|morning|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b/i);
  if (timeWindow) constraints.push({ kind: "TIME_WINDOW", value: timeWindow[1].toUpperCase() });
  const battleMode = description.match(/\b(single|double|triple|rotation) battle\b/i);
  if (battleMode) constraints.push({ kind: "BATTLE_MODE", value: battleMode[1].toUpperCase() });
  const requiredCount = description.match(/\b(?:catch|capture|register|defeat|beat|win|obtain|visit|find)[ ]+(\d+)\b/i);
  if (requiredCount) constraints.push({ kind: "REQUIRED_COUNT", value: Number(requiredCount[1]) });
  const allowedType = description.match(/\busing only ([A-Za-z]+)-type\b/i);
  if (allowedType) constraints.push({ kind: "ALLOWED_TYPE", value: normalizeName(allowedType[1]) });
  const forbiddenType = description.match(/\bwithout (?:using )?(?:any )?([A-Za-z]+)-type\b/i);
  if (forbiddenType) constraints.push({ kind: "FORBIDDEN_TYPE", value: normalizeName(forbiddenType[1]) });
  if (/\b(before|after|in order)\b/i.test(description)) {
    constraints.push({ kind: "ORDERING", value: "REQUIRED" });
  }
  if (/\b(then|followed by)\b/i.test(description)) {
    constraints.push({ kind: "SEQUENCE", value: "REQUIRED" });
  }
  return constraints;
}

function summarize(records) {
  const byFamily = Object.fromEntries(SEMANTIC_FAMILIES.map((familyName) => [familyName, 0]));
  const byTier = { "1": 0, "2": 0, "3": 0, "4": 0 };
  const byRecoveryPath = Object.fromEntries(Object.keys(RECOVERY_PATH_TIERS).map((path) => [path, 0]));
  const byExclusionReason = {};
  let classified = 0;
  let expressible = 0;
  for (const record of records) {
    byFamily[record.semanticFamily] = (byFamily[record.semanticFamily] ?? 0) + 1;
    byTier[String(record.portabilityTier)] = (byTier[String(record.portabilityTier)] ?? 0) + 1;
    if (record.recoveryPath !== null) byRecoveryPath[record.recoveryPath] += 1;
    if (record.outcome === "CLASSIFIED") classified += 1;
    if (record.outcome === "CLASSIFIED" && record.templateKey !== null && record.portabilityTier <= 3) {
      expressible += 1;
    }
    if (record.outcome !== "CLASSIFIED") {
      byExclusionReason[record.reason] = (byExclusionReason[record.reason] ?? 0) + 1;
    }
  }
  return {
    total: records.length,
    classified,
    unclassified: records.length - classified,
    expressible,
    byFamily,
    byTier,
    byRecoveryPath,
    byExclusionReason,
  };
}

function derivePredicateOperators(baseOperators, constraints) {
  const selected = new Set(baseOperators);
  for (const { kind } of constraints) {
    if (kind === "MAX_LEVEL" || kind === "MAX_PARTY_SIZE") selected.add("VALUE_BELOW");
    if (["TIME_WINDOW", "BATTLE_MODE", "ALLOWED_TYPE", "FORBIDDEN_TYPE", "AREA_BOUNDARY"].includes(kind)) {
      selected.add("VALUE_EQUALS");
    }
    if (kind === "REQUIRED_COUNT") {
      selected.add("EVENT_COUNT");
      selected.add("PROGRESS_CURRENT_TARGET");
    }
    if (["ITEM_USE_POLICY", "RESET_POLICY", "NO_FORBIDDEN_EVENT"].includes(kind)) {
      selected.add("NO_FORBIDDEN_EVENT");
    }
    if (kind === "ORDERING" || kind === "SEQUENCE") selected.add("EVENTS_IN_ORDER");
  }
  return PORTABLE_PREDICATE_OPERATORS.filter((operator) => selected.has(operator));
}

function semantics(
  templateTitle,
  templateDescription,
  requiredFacts,
  requiredEvents,
  requiredCatalogRoles,
  temporalScope,
  predicateOperators,
) {
  return Object.freeze({
    templateTitle,
    templateDescription,
    requiredFacts: Object.freeze(requiredFacts),
    requiredEvents: Object.freeze(requiredEvents),
    requiredCatalogRoles: Object.freeze(requiredCatalogRoles),
    temporalScope,
    predicateOperators: Object.freeze(predicateOperators),
  });
}

function validateManifest(manifest) {
  if (!manifest || manifest.schema !== 1 || !Array.isArray(manifest.games)) {
    throw new TypeError("manifest must use schema 1 and contain games");
  }
  if (manifest.gameCount !== manifest.games.length) {
    throw new TypeError("manifest gameCount must match games");
  }
  for (const game of manifest.games) {
    requiredInteger(game.gameId, "gameId");
    requiredInteger(game.generation, "generation");
    requiredString(game.researchFile, "researchFile");
    if (!/^[a-f0-9]{64}$/.test(game.researchSha256)) {
      throw new TypeError(`manifest game ${game.gameId} has an invalid research SHA-256`);
    }
  }
}

function validateSanitizedResearchPayload({ game, payload }) {
  const topLevelFields = new Set([
    "schema", "sourceSystem", "generation", "sourceGameId", "sourceGameTitle",
    "expectedTitle", "sourceUrl", "extractedAt", "sourceModifiedAt", "achievements",
  ]);
  const achievementFields = new Set([
    "sourceSystem", "sourceGameId", "sourceGameTitle", "achievementId", "title",
    "description", "officialClassification", "displayOrder", "author", "sourceModifiedAt",
    "sourceUrl", "extractedAt",
  ]);
  if (!payload || payload.schema !== 1 || payload.sourceSystem !== "RetroAchievements"
      || payload.generation !== game.generation || payload.sourceGameId !== game.gameId
      || !Array.isArray(payload.achievements)
      || Object.keys(payload).some((field) => !topLevelFields.has(field))) {
    throw new TypeError(`expected sanitized research payload for game ${game.gameId}`);
  }
  if (payload.achievements.length !== game.achievementCount) {
    throw new TypeError(`research achievement count mismatch for game ${game.gameId}`);
  }
  for (const achievement of payload.achievements) {
    if (!achievement || typeof achievement !== "object" || Array.isArray(achievement)
        || Object.keys(achievement).some((field) => !achievementFields.has(field))
        || achievement.sourceSystem !== "RetroAchievements"
        || achievement.sourceGameId !== game.gameId
        || !Number.isSafeInteger(achievement.achievementId)
        || !Number.isSafeInteger(achievement.displayOrder)
        || typeof achievement.title !== "string"
        || typeof achievement.description !== "string") {
      throw new TypeError(`expected sanitized research payload for game ${game.gameId}`);
    }
  }
}

function family(name, pattern, portabilityTier, templateKey) {
  return Object.freeze({ name, pattern, portabilityTier, templateKey });
}

function percentage(numerator, denominator) {
  if (denominator === 0) return "0.00%";
  return `${((numerator / denominator) * 100).toFixed(2)}%`;
}

function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function requiredInteger(value, name) {
  if (!Number.isSafeInteger(value)) throw new TypeError(`${name} must be an integer`);
  return value;
}

function requiredString(value, name) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new TypeError(`${name} must be a non-empty string`);
  }
  return value.trim();
}

function nullableString(value) {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function normalizeName(value) {
  return `${value[0].toUpperCase()}${value.slice(1).toLowerCase()}`;
}

async function atomicWrite(path, value) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = join(dirname(path), `.${randomUUID()}.tmp`);
  await writeFile(temporary, value, "utf8");
  await rename(temporary, path);
}

async function runCli() {
  const repositoryRoot = fileURLToPath(new URL("../../", import.meta.url));
  const researchDirectory = process.env.DUALDEX_RA_RESEARCH_DIRECTORY
    ?? (process.platform === "win32" ? "D:\\Temp\\dualdex-retroachievements\\research" : join(repositoryRoot, "output", "retroachievements", "research"));
  const manifestPath = process.env.DUALDEX_RA_MANIFEST
    ?? join(repositoryRoot, "docs", "research", "retroachievements", "official-gen1-gen3-manifest.json");
  const semanticOverridesPath = process.env.DUALDEX_RA_SEMANTIC_OVERRIDES
    ?? join(repositoryRoot, "docs", "research", "retroachievements", "official-gen1-gen3-semantic-overrides.json");
  const outputPath = join(repositoryRoot, "docs", "research", "retroachievements", "official-gen1-gen3-classification.json");
  const reportPath = join(repositoryRoot, "docs", "reports", "passive-insights-progress", "reference-classification.md");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const semanticOverrides = JSON.parse(await readFile(semanticOverridesPath, "utf8"));
  const document = await classifyReferenceCorpus({ manifest, researchDirectory, semanticOverrides });
  await writeClassificationArtifacts({ document, outputPath, reportPath });
  process.stdout.write(
    `Classified ${document.summary.classified}/${document.summary.total}; `
      + `${document.summary.unclassified} remain unclassified.\n`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runCli().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
