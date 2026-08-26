import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  OFFICIAL_POKEMON_GAMES,
  buildGameExtendedUrl,
  extractOfficialPokemonAchievements,
  normalizeGameExtendedPayload,
} from "./extract-pokemon-achievements.mjs";

const FIXED_TIME = "2026-08-26T14:30:00.000Z";
const TEST_TEMP_ROOT = process.env.DUALDEX_TEST_TEMP
  || (process.platform === "win32" ? "D:\\Temp" : tmpdir());

test("defines the eleven official English Generation I-III controls exactly once", () => {
  assert.deepEqual(
    OFFICIAL_POKEMON_GAMES.map(({ generation, gameId }) => [generation, gameId]),
    [
      [1, 724], [1, 586], [1, 723],
      [2, 576], [2, 722], [2, 810],
      [3, 790], [3, 791], [3, 668], [3, 515], [3, 788],
    ],
  );
  assert.equal(new Set(OFFICIAL_POKEMON_GAMES.map(({ gameId }) => gameId)).size, 11);
});

test("builds the documented core-achievement request without requiring a username", () => {
  const url = new URL(buildGameExtendedUrl({ gameId: 724, apiKey: "test-secret" }));

  assert.equal(url.origin, "https://retroachievements.org");
  assert.equal(url.pathname, "/API/API_GetGameExtended.php");
  assert.equal(url.searchParams.get("i"), "724");
  assert.equal(url.searchParams.get("f"), "3");
  assert.equal(url.searchParams.get("y"), "test-secret");
});

test("normalizes and sorts only the permitted research fields", () => {
  const normalized = normalizeGameExtendedPayload({
    descriptor: OFFICIAL_POKEMON_GAMES[0],
    payload: {
      ID: 724,
      Title: "Pokemon Red Version",
      NumAchievements: 2,
      Achievements: {
        "900": {
          ID: 900,
          Title: "Second",
          Description: "Win second.",
          Type: "progression",
          DisplayOrder: 2,
          Author: "Author B",
          DateModified: "2026-01-02 03:04:05",
          MemAddr: "0xH1234=1",
          BadgeName: "90000",
        },
        "100": {
          ID: 100,
          Title: "First",
          Description: "Win first.",
          Type: null,
          DisplayOrder: 1,
          Author: "Author A",
          DateModified: "2026-01-01 03:04:05",
          MemAddr: "0xH5678=1",
          BadgeName: "10000",
        },
      },
    },
    extractedAt: FIXED_TIME,
  });

  assert.equal(normalized.sourceGameId, 724);
  assert.equal(normalized.sourceGameTitle, "Pokemon Red Version");
  assert.equal(normalized.sourceModifiedAt, "2026-01-02 03:04:05");
  assert.deepEqual(normalized.achievements.map(({ achievementId }) => achievementId), [100, 900]);
  assert.deepEqual(Object.keys(normalized.achievements[0]), [
    "sourceSystem",
    "sourceGameId",
    "sourceGameTitle",
    "achievementId",
    "title",
    "description",
    "officialClassification",
    "displayOrder",
    "author",
    "sourceModifiedAt",
    "sourceUrl",
    "extractedAt",
  ]);
  assert.equal(JSON.stringify(normalized).includes("MemAddr"), false);
  assert.equal(JSON.stringify(normalized).includes("BadgeName"), false);
});

test("writes sanitized research payloads and a deterministic secret-free manifest", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-extract-"));
  try {
    const researchDirectory = join(root, "research");
    const manifestPath = join(root, "manifest.json");
    const games = OFFICIAL_POKEMON_GAMES.slice(0, 2);
    const payloads = new Map(games.map((game, index) => [
      game.gameId,
      JSON.stringify({
        ID: game.gameId,
        Title: game.expectedTitle,
        NumAchievements: 1,
        Achievements: {
          [index + 1]: {
            ID: index + 1,
            Title: `Achievement ${index + 1}`,
            Description: `Description ${index + 1}`,
            DisplayOrder: index,
            Author: "Verifier",
            DateModified: `2026-01-0${index + 1} 00:00:00`,
            MemAddr: "excluded-trigger-expression",
            BadgeName: "excluded-badge-reference",
          },
        },
      }),
    ]));
    const requestedIds = [];
    const fetchImpl = async (url) => {
      const request = new URL(url);
      const gameId = Number(request.searchParams.get("i"));
      requestedIds.push(gameId);
      assert.equal(request.searchParams.get("y"), "stage0-secret");
      return response(200, payloads.get(gameId));
    };

    const first = await extractOfficialPokemonAchievements({
      apiKey: "stage0-secret",
      games,
      researchDirectory,
      manifestPath,
      extractedAt: FIXED_TIME,
      fetchImpl,
    });
    const firstBytes = await readFile(manifestPath, "utf8");
    const second = await extractOfficialPokemonAchievements({
      apiKey: "stage0-secret",
      games,
      researchDirectory,
      manifestPath,
      extractedAt: FIXED_TIME,
      fetchImpl,
    });
    const secondBytes = await readFile(manifestPath, "utf8");

    assert.deepEqual(requestedIds, [724, 586, 724, 586]);
    assert.deepEqual(first, second);
    assert.equal(firstBytes, secondBytes);
    assert.equal(firstBytes.includes("stage0-secret"), false);
    assert.equal(first.gameCount, 2);
    assert.equal(first.achievementCount, 2);
    for (const game of games) {
      const researchText = await readFile(join(researchDirectory, `game-${game.gameId}.json`), "utf8");
      const research = JSON.parse(researchText);
      assert.equal(research.schema, 1);
      assert.equal(research.sourceGameId, game.gameId);
      assert.equal(research.achievements.length, 1);
      assert.equal(researchText.includes("MemAddr"), false);
      assert.equal(researchText.includes("BadgeName"), false);
      assert.equal(researchText.includes("NumAwarded"), false);
      assert.match(first.games.find((entry) => entry.gameId === game.gameId).researchSha256, /^[a-f0-9]{64}$/);
    }
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("fails closed on malformed payloads without leaking the API key", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-malformed-"));
  try {
    const manifestPath = join(root, "manifest.json");
    await assert.rejects(
      extractOfficialPokemonAchievements({
        apiKey: "never-print-this",
        games: OFFICIAL_POKEMON_GAMES.slice(0, 1),
        researchDirectory: join(root, "research"),
        manifestPath,
        extractedAt: FIXED_TIME,
        fetchImpl: async () => response(200, "{not-json"),
      }),
      (error) => {
        assert.match(error.message, /game 724.*invalid JSON/i);
        assert.equal(error.message.includes("never-print-this"), false);
        return true;
      },
    );
    await assert.rejects(readFile(manifestPath), { code: "ENOENT" });
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("classifies rate limits and server failures as retryable without retrying implicitly", async () => {
  let calls = 0;
  await assert.rejects(
    extractOfficialPokemonAchievements({
      apiKey: "secret",
      games: OFFICIAL_POKEMON_GAMES.slice(0, 1),
      researchDirectory: "unused",
      manifestPath: "unused.json",
      extractedAt: FIXED_TIME,
      fetchImpl: async () => {
        calls += 1;
        return response(429, "rate limited");
      },
    }),
    (error) => {
      assert.equal(error.retryable, true);
      assert.match(error.message, /HTTP 429.*game 724/);
      assert.equal(error.message.includes("secret"), false);
      return true;
    },
  );
  assert.equal(calls, 1);
});

test("resumes an interrupted pull by validating cached payloads and requesting only missing games", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-resume-"));
  try {
    const researchDirectory = join(root, "research");
    const manifestPath = join(root, "manifest.json");
    const games = OFFICIAL_POKEMON_GAMES.slice(0, 2);
    const cached = JSON.stringify(gamePayload(games[0], 1));
    const fetched = JSON.stringify(gamePayload(games[1], 2));
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, `game-${games[0].gameId}.json`), cached, "utf8");
    const requestedIds = [];

    const manifest = await extractOfficialPokemonAchievements({
      apiKey: "stage0-secret",
      games,
      researchDirectory,
      manifestPath,
      extractedAt: FIXED_TIME,
      reuseExisting: true,
      fetchImpl: async (url) => {
        const gameId = Number(new URL(url).searchParams.get("i"));
        requestedIds.push(gameId);
        return response(200, fetched);
      },
    });

    assert.deepEqual(requestedIds, [586]);
    assert.equal(manifest.games[0].retrievalMode, "CACHED_SANITIZED");
    assert.equal(manifest.games[1].retrievalMode, "FETCHED");
    assert.match(manifest.games[0].retrievedAt, /^\d{4}-\d{2}-\d{2}T/);
    assert.equal(manifest.games[1].retrievedAt, FIXED_TIME);
    const sanitizedCache = await readFile(join(researchDirectory, "game-724.json"), "utf8");
    assert.equal(sanitizedCache === cached, false);
    assert.equal(JSON.parse(sanitizedCache).sourceGameId, 724);
    assert.equal(sanitizedCache.includes("NumAchievements"), false);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("preserves source extraction time when regenerating entirely from sanitized research", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-reuse-"));
  try {
    const researchDirectory = join(root, "research");
    const manifestPath = join(root, "manifest.json");
    const game = OFFICIAL_POKEMON_GAMES[0];
    const first = await extractOfficialPokemonAchievements({
      apiKey: "stage0-secret",
      games: [game],
      researchDirectory,
      manifestPath,
      extractedAt: FIXED_TIME,
      fetchImpl: async () => response(200, JSON.stringify(gamePayload(game, 1))),
    });
    const firstResearch = await readFile(join(researchDirectory, "game-724.json"), "utf8");

    const second = await extractOfficialPokemonAchievements({
      games: [game],
      researchDirectory,
      manifestPath,
      extractedAt: "2026-08-26T18:45:00.000Z",
      reuseExisting: true,
      fetchImpl: async () => {
        throw new Error("cache-only regeneration must not fetch");
      },
    });
    const secondResearch = await readFile(join(researchDirectory, "game-724.json"), "utf8");

    assert.equal(second.extractedAt, FIXED_TIME);
    assert.equal(second.games[0].retrievedAt, FIXED_TIME);
    assert.equal(second.games[0].retrievalMode, "CACHED");
    assert.equal(secondResearch, firstResearch);
    assert.equal(second.games[0].researchSha256, first.games[0].researchSha256);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("rejects forbidden fields in a purported sanitized research payload", async () => {
  const root = await mkdtemp(join(TEST_TEMP_ROOT, "dualdex-ra-forbidden-"));
  try {
    const researchDirectory = join(root, "research");
    const manifestPath = join(root, "manifest.json");
    const game = OFFICIAL_POKEMON_GAMES[0];
    const research = {
      schema: 1,
      sourceSystem: "RetroAchievements",
      generation: game.generation,
      sourceGameId: game.gameId,
      sourceGameTitle: game.expectedTitle,
      expectedTitle: game.expectedTitle,
      sourceUrl: `https://retroachievements.org/game/${game.gameId}`,
      extractedAt: FIXED_TIME,
      sourceModifiedAt: "2026-01-01 00:00:00",
      achievements: [{
        sourceSystem: "RetroAchievements",
        sourceGameId: game.gameId,
        sourceGameTitle: game.expectedTitle,
        achievementId: 1,
        title: "Private title",
        description: "Catch one Pokemon.",
        officialClassification: null,
        displayOrder: 1,
        author: "Verifier",
        sourceModifiedAt: "2026-01-01 00:00:00",
        sourceUrl: "https://retroachievements.org/achievement/1",
        extractedAt: FIXED_TIME,
        MemAddr: "forbidden-trigger-expression",
      }],
    };
    await import("node:fs/promises").then(({ mkdir }) => mkdir(researchDirectory, { recursive: true }));
    await writeFile(join(researchDirectory, "game-724.json"), JSON.stringify(research), "utf8");

    await assert.rejects(
      extractOfficialPokemonAchievements({
        games: [game],
        researchDirectory,
        manifestPath,
        extractedAt: FIXED_TIME,
        reuseExisting: true,
        fetchImpl: async () => {
          throw new Error("invalid sanitized research must fail closed without fetching");
        },
      }),
      /game 724.*unexpected achievement field MemAddr/i,
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async text() {
      return body;
    },
  };
}

function gamePayload(game, achievementId) {
  return {
    ID: game.gameId,
    Title: game.expectedTitle,
    NumAchievements: 1,
    Achievements: {
      [achievementId]: {
        ID: achievementId,
        Title: `Achievement ${achievementId}`,
        Description: `Description ${achievementId}`,
        DisplayOrder: achievementId,
        Author: "Verifier",
        DateModified: "2026-01-01 00:00:00",
      },
    },
  };
}
