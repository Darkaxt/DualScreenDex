import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  buildRomCompatibilityMatrix,
  canonicalCorpusDigest,
  renderRomCompatibilityMarkdown,
  selectCanonicalRows,
  streamCompatibilityReport,
} from "./rom-compatibility-matrix.mjs";

const ALL_CAPABILITIES = [
  "SPECIES_CATALOG", "SPECIES_NAMES", "SPECIES_TYPES", "TYPE_CHART", "BASE_STATS", "SPRITES",
  "POKEDEX_DESCRIPTIONS", "EVOLUTIONS", "MOVE_CATALOG", "MOVE_DETAILS", "MOVE_DESCRIPTIONS",
  "LEARNSETS", "EGG_MOVES", "MACHINE_MOVES", "TUTOR_MOVES", "ABILITIES",
  "ABILITY_DESCRIPTIONS", "ABILITY_MECHANICS", "AREA_ENCOUNTERS", "TYPE_PRESENTATION",
  "BALL_CATALOG", "WORLD_MAP", "LOCAL_MAP", "NATURES",
];

const capability = (name, status, confidence) => ({ capability: name, status, confidence });

function selectedRow({
  displayName,
  family,
  generation,
  capabilities,
  languages = ["en"],
  manifestStatus = "RESOLVED",
  source = "private/root/that-must-not-leak.gba",
}) {
  return {
    displayName,
    source,
    result: {
      sha256: "a".repeat(64),
      status: "SELECTED",
      selectedFamily: family,
      capabilities: ALL_CAPABILITIES.map(name =>
        capabilities.find(candidate => candidate.capability === name) ?? capability(name, "NOT_FOUND", 0)),
      probes: [{
        family,
        score: 80,
        hardGatePassed: true,
        resolvedLayout: {
          generation,
          languageManifest: {
            defaultLanguage: manifestStatus === "RESOLVED" ? languages[0] : null,
            projections: manifestStatus === "RESOLVED" ? languages.map(language => ({ language })) : [],
            status: manifestStatus,
          },
        },
      }],
    },
    compatibilityPercent: 72.5,
  };
}

test("groups selected ROMs by generation and family with confidence and language counts", () => {
  const rows = [
    selectedRow({
      displayName: "folder/Pokémon Example.gba",
      family: "EMERALD",
      generation: 3,
      languages: ["en", "es"],
      capabilities: [
        capability("SPECIES_CATALOG", "AVAILABLE", 0.97),
        capability("LOCAL_MAP", "PARTIAL", 0.64),
        capability("ABILITIES", "NOT_APPLICABLE", 0),
      ],
    }),
    selectedRow({
      displayName: "Pokémon Unknown.gb",
      family: "RED_BLUE",
      generation: 1,
      languages: [],
      manifestStatus: "UNKNOWN",
      capabilities: [capability("SPECIES_CATALOG", "NOT_FOUND", 0)],
    }),
    {
      displayName: "Unmatched.gbc",
      source: "private/unmatched.gbc",
      result: { status: "NO_FAMILY_MATCH", header: { platform: "GBC" } },
    },
  ];

  const matrix = buildRomCompatibilityMatrix(rows, {
    rowSourceCommit: "1".repeat(40),
    closureSourceCommit: "2".repeat(40),
    expectedInputCount: 3,
  });
  const markdown = renderRomCompatibilityMarkdown(matrix);

  assert.equal(matrix.selectedCount, 2);
  assert.equal(matrix.resolvedLanguageManifests, 1);
  assert.equal(matrix.unknownLanguageManifests, 1);
  assert.equal(matrix.multilingualCount, 1);
  assert.match(markdown, /## Generation I/);
  assert.match(markdown, /### Red\/Blue family/);
  assert.match(markdown, /## Generation III/);
  assert.match(markdown, /### Emerald family/);
  assert.match(markdown, /Pokémon Example\.gba/);
  assert.match(markdown, /\| 2 \| 72\.50% \| 97% \|/);
  assert.match(markdown, /P 64%/);
  assert.match(markdown, /## Unclassified inputs/);
  assert.doesNotMatch(markdown, /private\/root|private\/unmatched|a{64}/);
});

test("uses localized catalog evidence when final results omit text capabilities", () => {
  const row = selectedRow({
    displayName: "Localized Example.gba",
    family: "EMERALD",
    generation: 3,
    capabilities: [capability("SPECIES_CATALOG", "AVAILABLE", 0.88)],
  });
  const localizedCapabilities = new Set([
    "SPECIES_NAMES",
    "POKEDEX_DESCRIPTIONS",
    "MOVE_DESCRIPTIONS",
    "ABILITY_DESCRIPTIONS",
  ]);
  row.result.capabilities = row.result.capabilities.filter(evidence =>
    !localizedCapabilities.has(evidence.capability));
  row.result.probes[0].capabilities = ALL_CAPABILITIES.map(name =>
    capability(name, name === "SPECIES_NAMES" ? "AVAILABLE" : "NOT_FOUND", name === "SPECIES_NAMES" ? 0.4 : 0));
  row.catalog = {
    localizedCapabilities: {
      SPECIES_NAMES: { status: "AVAILABLE", confidence: 0.99 },
      SPECIES_DESCRIPTIONS: { status: "PARTIAL", confidence: 0.73 },
      MOVE_DESCRIPTIONS: { status: "AMBIGUOUS", confidence: 0.61 },
      ABILITY_DESCRIPTIONS: { status: "NOT_FOUND", confidence: 0 },
    },
  };

  const matrix = buildRomCompatibilityMatrix([row]);
  const evidence = matrix.selected[0].capabilities;

  assert.deepEqual(evidence.get("SPECIES_CATALOG"), { status: "AVAILABLE", confidence: 0.88 });
  assert.deepEqual(evidence.get("SPECIES_NAMES"), { status: "AVAILABLE", confidence: 0.99 });
  assert.deepEqual(evidence.get("POKEDEX_DESCRIPTIONS"), { status: "PARTIAL", confidence: 0.73 });
  assert.deepEqual(evidence.get("MOVE_DESCRIPTIONS"), { status: "AMBIGUOUS", confidence: 0.61 });
  assert.deepEqual(evidence.get("ABILITY_DESCRIPTIONS"), { status: "NOT_FOUND", confidence: 0 });
});

test("selects the exact public canonical row set and derives its identity digest", () => {
  const rows = [
    { displayName: "One.gb", result: { sha256: "1".repeat(64), size: 1024 } },
    { displayName: "Alias A.gbc", result: { sha256: "2".repeat(64), size: 2048 } },
    { displayName: "Alias B.gbc", result: { sha256: "2".repeat(64), size: 2048 } },
    { displayName: "Extra.gba", result: { sha256: "3".repeat(64), size: 4096 } },
  ];
  const identityIndex = {
    inputRows: 3,
    romDeltas: [
      { sha256: "1".repeat(64), rom: "One.gb" },
      { sha256: "2".repeat(64), rom: "Alias A.gbc" },
    ],
    duplicateGroups: [{ sha256: "2".repeat(64), roms: ["Alias A.gbc", "Alias B.gbc"] }],
  };

  const selected = selectCanonicalRows(rows, identityIndex);
  const identity = canonicalCorpusDigest(selected);

  assert.deepEqual(selected.map(row => row.displayName), ["One.gb", "Alias A.gbc", "Alias B.gbc"]);
  assert.equal(identity.inputCount, 3);
  assert.equal(identity.uniqueRomIdentityCount, 2);
  assert.match(identity.inputDigestSha256, /^[0-9a-f]{64}$/);
});

test("streams result rows without retaining the raw report", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "dualdex-rom-matrix-"));
  const reportPath = path.join(directory, "compatibility.json");
  const report = {
    schemaVersion: 16,
    execution: { sourceCommit: "2".repeat(40), generatorSha256: "3".repeat(64) },
    roots: ["private-root"],
    results: [
      selectedRow({
        displayName: "First.gba",
        family: "RUBY_SAPPHIRE",
        generation: 3,
        capabilities: [capability("SPECIES_CATALOG", "AVAILABLE", 1)],
      }),
      { displayName: "Second.gb", result: { status: "AMBIGUOUS", header: { platform: "GB" } } },
    ],
  };

  await writeFile(reportPath, JSON.stringify(report));
  const rows = [];
  try {
    const metadata = await streamCompatibilityReport(reportPath, row => rows.push(row));
    assert.equal(metadata.schemaVersion, 16);
    assert.equal(metadata.execution.sourceCommit, "2".repeat(40));
    assert.equal(metadata.rowCount, 2);
    assert.match(metadata.rawReportSha256, /^[0-9a-f]{64}$/);
    assert.deepEqual(rows.map(row => row.displayName), ["First.gba", "Second.gb"]);
    assert.equal("roots" in metadata, false);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
