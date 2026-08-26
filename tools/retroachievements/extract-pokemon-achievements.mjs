import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, rename, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const API_ENDPOINT = "https://retroachievements.org/API/API_GetGameExtended.php";
const SOURCE_SYSTEM = "RetroAchievements";
const RESEARCH_TOP_LEVEL_FIELDS = new Set([
  "schema", "sourceSystem", "generation", "sourceGameId", "sourceGameTitle",
  "expectedTitle", "sourceUrl", "extractedAt", "sourceModifiedAt", "achievements",
]);
const RESEARCH_ACHIEVEMENT_FIELDS = new Set([
  "sourceSystem", "sourceGameId", "sourceGameTitle", "achievementId", "title",
  "description", "officialClassification", "displayOrder", "author", "sourceModifiedAt",
  "sourceUrl", "extractedAt",
]);

export const OFFICIAL_POKEMON_GAMES = Object.freeze([
  game(1, 724, "Pokemon Red Version"),
  game(1, 586, "Pokemon Blue Version"),
  game(1, 723, "Pokemon Yellow Version"),
  game(2, 576, "Pokemon Gold Version"),
  game(2, 722, "Pokemon Silver Version"),
  game(2, 810, "Pokemon Crystal Version"),
  game(3, 790, "Pokemon Ruby Version"),
  game(3, 791, "Pokemon Sapphire Version"),
  game(3, 668, "Pokemon Emerald Version"),
  game(3, 515, "Pokemon FireRed Version"),
  game(3, 788, "Pokemon LeafGreen Version"),
]);

export class RetroAchievementsExtractionError extends Error {
  constructor(message, { retryable = false } = {}) {
    super(message);
    this.name = "RetroAchievementsExtractionError";
    this.retryable = retryable;
  }
}

export function buildGameExtendedUrl({ gameId, apiKey, endpoint = API_ENDPOINT }) {
  requirePositiveInteger(gameId, "gameId");
  requireNonEmptyString(apiKey, "apiKey");
  const url = new URL(endpoint);
  url.searchParams.set("i", String(gameId));
  url.searchParams.set("f", "3");
  url.searchParams.set("y", apiKey);
  return url.toString();
}

export function normalizeGameExtendedPayload({ descriptor, payload, extractedAt }) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw malformed(descriptor.gameId, "top-level response is not an object");
  }
  const sourceGameId = integerField(payload, "ID", "id");
  if (sourceGameId !== descriptor.gameId) {
    throw malformed(descriptor.gameId, `response ID is ${String(sourceGameId)}`);
  }
  const sourceGameTitle = stringField(payload, "Title", "title");
  const rawAchievements = payload.Achievements ?? payload.achievements;
  if (!rawAchievements || typeof rawAchievements !== "object" || Array.isArray(rawAchievements)) {
    throw malformed(descriptor.gameId, "Achievements is not an object");
  }
  const achievements = Object.values(rawAchievements)
    .map((achievement) => normalizeAchievement({
      achievement,
      sourceGameId,
      sourceGameTitle,
      extractedAt,
    }))
    .sort((left, right) => left.displayOrder - right.displayOrder || left.achievementId - right.achievementId);
  const declaredCount = optionalIntegerField(payload, "NumAchievements", "numAchievements");
  if (declaredCount !== null && declaredCount !== achievements.length) {
    throw malformed(
      descriptor.gameId,
      `NumAchievements is ${declaredCount} but ${achievements.length} core records were returned`,
    );
  }
  return {
    sourceSystem: SOURCE_SYSTEM,
    generation: descriptor.generation,
    sourceGameId,
    sourceGameTitle,
    expectedTitle: descriptor.expectedTitle,
    sourceUrl: `https://retroachievements.org/game/${sourceGameId}`,
    extractedAt,
    sourceModifiedAt: achievements.map(({ sourceModifiedAt }) => sourceModifiedAt).filter(Boolean).sort().at(-1) ?? null,
    achievements,
  };
}

export async function extractOfficialPokemonAchievements({
  apiKey,
  games = OFFICIAL_POKEMON_GAMES,
  researchDirectory,
  manifestPath,
  extractedAt = new Date().toISOString(),
  reuseExisting = false,
  fetchImpl = globalThis.fetch,
}) {
  requireNonEmptyString(researchDirectory, "researchDirectory");
  requireNonEmptyString(manifestPath, "manifestPath");
  if (typeof fetchImpl !== "function") {
    throw new TypeError("fetchImpl must be a function");
  }
  await mkdir(researchDirectory, { recursive: true });
  const manifestGames = [];
  for (const descriptor of games) {
    const researchFile = `game-${descriptor.gameId}.json`;
    const researchPath = join(researchDirectory, researchFile);
    const cached = reuseExisting
      ? await readValidCachedGame({ descriptor, researchPath })
      : null;
    const responseText = cached?.responseText ?? await requestGame({ apiKey, descriptor, fetchImpl });
    let normalized;
    if (cached?.normalized) {
      normalized = cached.normalized;
    } else {
      let payload;
      try {
        payload = JSON.parse(responseText);
      } catch {
        throw malformed(descriptor.gameId, "invalid JSON response");
      }
      normalized = normalizeGameExtendedPayload({ descriptor, payload, extractedAt });
    }
    const researchText = `${JSON.stringify(toResearchPayload(normalized), null, 2)}\n`;
    if (!cached || cached.format !== "RESEARCH" || cached.responseText !== researchText) {
      await atomicWriteText(researchPath, researchText);
    }
    manifestGames.push({
      generation: descriptor.generation,
      gameId: descriptor.gameId,
      expectedTitle: descriptor.expectedTitle,
      sourceTitle: normalized.sourceGameTitle,
      sourceUrl: normalized.sourceUrl,
      sourceModifiedAt: normalized.sourceModifiedAt,
      achievementCount: normalized.achievements.length,
      researchFile,
      researchSha256: sha256(researchText),
      retrievedAt: normalized.extractedAt,
      retrievalMode: cached
        ? (cached.format === "RESEARCH" ? "CACHED" : "CACHED_SANITIZED")
        : "FETCHED",
    });
  }
  const manifest = {
    schema: 1,
    sourceSystem: SOURCE_SYSTEM,
    endpoint: API_ENDPOINT,
    extractedAt: manifestGames.map(({ retrievedAt }) => retrievedAt).sort().at(-1) ?? extractedAt,
    gameCount: manifestGames.length,
    achievementCount: manifestGames.reduce((sum, entry) => sum + entry.achievementCount, 0),
    games: manifestGames,
  };
  await atomicWriteJson(manifestPath, manifest);
  return manifest;
}

async function readValidCachedGame({ descriptor, researchPath }) {
  let responseText;
  let metadata;
  try {
    [responseText, metadata] = await Promise.all([
      readFile(researchPath, "utf8"),
      stat(researchPath),
    ]);
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
  let payload;
  try {
    payload = JSON.parse(responseText);
  } catch {
    return null;
  }
  if (isResearchPayload(payload)) {
    validateResearchPayload(descriptor, payload);
    return {
      responseText,
      normalized: payload,
      format: "RESEARCH",
      retrievedAt: payload.extractedAt,
    };
  }
  try {
    const normalized = normalizeGameExtendedPayload({
      descriptor,
      payload,
      extractedAt: metadata.mtime.toISOString(),
    });
    return {
      responseText,
      normalized,
      format: "API_RESPONSE",
      retrievedAt: normalized.extractedAt,
    };
  } catch {
    return null;
  }
}

function toResearchPayload(normalized) {
  return {
    schema: 1,
    sourceSystem: normalized.sourceSystem,
    generation: normalized.generation,
    sourceGameId: normalized.sourceGameId,
    sourceGameTitle: normalized.sourceGameTitle,
    expectedTitle: normalized.expectedTitle,
    sourceUrl: normalized.sourceUrl,
    extractedAt: normalized.extractedAt,
    sourceModifiedAt: normalized.sourceModifiedAt,
    achievements: normalized.achievements,
  };
}

function isResearchPayload(payload) {
  return payload?.schema === 1
    && payload.sourceSystem === SOURCE_SYSTEM
    && Array.isArray(payload.achievements);
}

function validateResearchPayload(descriptor, payload) {
  const extraTopLevel = Object.keys(payload).find((field) => !RESEARCH_TOP_LEVEL_FIELDS.has(field));
  if (extraTopLevel) {
    throw malformed(descriptor.gameId, `unexpected top-level field ${extraTopLevel}`);
  }
  if (payload.sourceGameId !== descriptor.gameId || payload.generation !== descriptor.generation) {
    throw malformed(descriptor.gameId, "cached research identity does not match");
  }
  if (payload.sourceSystem !== SOURCE_SYSTEM
      || typeof payload.sourceGameTitle !== "string"
      || typeof payload.expectedTitle !== "string"
      || typeof payload.sourceUrl !== "string"
      || typeof payload.extractedAt !== "string"
      || !Array.isArray(payload.achievements)) {
    throw malformed(descriptor.gameId, "cached research metadata is invalid");
  }
  for (const achievement of payload.achievements) {
    if (!achievement || typeof achievement !== "object" || Array.isArray(achievement)) {
      throw malformed(descriptor.gameId, "cached research achievement is not an object");
    }
    const extraAchievement = Object.keys(achievement)
      .find((field) => !RESEARCH_ACHIEVEMENT_FIELDS.has(field));
    if (extraAchievement) {
      throw malformed(descriptor.gameId, `unexpected achievement field ${extraAchievement}`);
    }
    if (achievement.sourceSystem !== SOURCE_SYSTEM
        || achievement.sourceGameId !== descriptor.gameId
        || !Number.isSafeInteger(achievement.achievementId)
        || !Number.isSafeInteger(achievement.displayOrder)
        || typeof achievement.title !== "string"
        || typeof achievement.description !== "string"
        || typeof achievement.extractedAt !== "string") {
      throw malformed(descriptor.gameId, "cached research achievement is invalid");
    }
  }
}

async function requestGame({ apiKey, descriptor, fetchImpl }) {
  const url = buildGameExtendedUrl({ gameId: descriptor.gameId, apiKey });
  let response;
  try {
    response = await fetchImpl(url);
  } catch {
    throw new RetroAchievementsExtractionError(
      `request failed for game ${descriptor.gameId}`,
      { retryable: true },
    );
  }
  if (!response || typeof response.text !== "function" || typeof response.status !== "number") {
    throw malformed(descriptor.gameId, "fetch returned an invalid response object");
  }
  if (!response.ok) {
    throw new RetroAchievementsExtractionError(
      `HTTP ${response.status} while requesting game ${descriptor.gameId}`,
      { retryable: response.status === 429 || response.status >= 500 },
    );
  }
  return response.text();
}

function normalizeAchievement({ achievement, sourceGameId, sourceGameTitle, extractedAt }) {
  if (!achievement || typeof achievement !== "object" || Array.isArray(achievement)) {
    throw malformed(sourceGameId, "achievement is not an object");
  }
  const achievementId = integerField(achievement, "ID", "id");
  return {
    sourceSystem: SOURCE_SYSTEM,
    sourceGameId,
    sourceGameTitle,
    achievementId,
    title: stringField(achievement, "Title", "title"),
    description: stringField(achievement, "Description", "description"),
    officialClassification: nullableStringField(achievement, "Type", "type"),
    displayOrder: optionalIntegerField(achievement, "DisplayOrder", "displayOrder") ?? Number.MAX_SAFE_INTEGER,
    author: nullableStringField(achievement, "Author", "author"),
    sourceModifiedAt: nullableStringField(achievement, "DateModified", "dateModified"),
    sourceUrl: `https://retroachievements.org/achievement/${achievementId}`,
    extractedAt,
  };
}

async function atomicWriteJson(path, value) {
  await atomicWriteText(path, `${JSON.stringify(value, null, 2)}\n`);
}

async function atomicWriteText(path, value) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = join(dirname(path), `.${randomUUID()}.tmp`);
  await writeFile(temporary, value, "utf8");
  await rename(temporary, path);
}

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function game(generation, gameId, expectedTitle) {
  return Object.freeze({ generation, gameId, expectedTitle });
}

function integerField(value, ...names) {
  const resolved = optionalIntegerField(value, ...names);
  if (resolved === null) {
    throw new TypeError(`required integer field ${names.join("/")} is missing`);
  }
  return resolved;
}

function optionalIntegerField(value, ...names) {
  const raw = firstField(value, names);
  if (raw === undefined || raw === null || raw === "") return null;
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed)) {
    throw new TypeError(`field ${names.join("/")} must be an integer`);
  }
  return parsed;
}

function stringField(value, ...names) {
  const resolved = nullableStringField(value, ...names);
  if (resolved === null) {
    throw new TypeError(`required string field ${names.join("/")} is missing`);
  }
  return resolved;
}

function nullableStringField(value, ...names) {
  const raw = firstField(value, names);
  if (raw === undefined || raw === null) return null;
  if (typeof raw !== "string") {
    throw new TypeError(`field ${names.join("/")} must be a string`);
  }
  const trimmed = raw.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function firstField(value, names) {
  for (const name of names) {
    if (Object.hasOwn(value, name)) return value[name];
  }
  return undefined;
}

function requirePositiveInteger(value, name) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new TypeError(`${name} must be a positive integer`);
  }
}

function requireNonEmptyString(value, name) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new TypeError(`${name} must be a non-empty string`);
  }
}

function malformed(gameId, detail) {
  return new RetroAchievementsExtractionError(`invalid response for game ${gameId}: ${detail}`);
}

async function runCli() {
  const options = parseArguments(process.argv.slice(2));
  const apiKey = process.env.RETROACHIEVEMENTS_WEB_API_KEY;
  const reuseExisting = process.env.DUALDEX_RA_REUSE_EXISTING === "1";
  if (!apiKey && !reuseExisting) {
    throw new RetroAchievementsExtractionError(
      "RETROACHIEVEMENTS_WEB_API_KEY is required for an explicit developer extraction",
    );
  }
  const manifest = await extractOfficialPokemonAchievements({
    apiKey,
    researchDirectory: options.researchDirectory,
    manifestPath: options.manifestPath,
    reuseExisting,
  });
  process.stdout.write(
    `Extracted ${manifest.achievementCount} core achievements from ${manifest.gameCount} games.\n`,
  );
}

function parseArguments(args) {
  const repositoryManifest = fileURLToPath(
    new URL("../../docs/research/retroachievements/official-gen1-gen3-manifest.json", import.meta.url),
  );
  const defaultResearchDirectory = process.platform === "win32"
    ? "D:\\Temp\\dualdex-retroachievements\\research"
    : join(tmpdir(), "dualdex-retroachievements", "research");
  const options = {
    researchDirectory: process.env.DUALDEX_RA_RESEARCH_DIRECTORY || defaultResearchDirectory,
    manifestPath: repositoryManifest,
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    const value = args[index + 1];
    if (argument === "--research-dir" && value) {
      options.researchDirectory = value;
      index += 1;
    } else if (argument === "--manifest" && value) {
      options.manifestPath = value;
      index += 1;
    } else {
      throw new RetroAchievementsExtractionError(`unknown or incomplete argument: ${argument}`);
    }
  }
  return options;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runCli().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
