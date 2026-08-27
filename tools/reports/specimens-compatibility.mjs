#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const FIELD_KEYS = Object.freeze([
  "identity", "speciesForm", "level", "nickname", "gender", "hpStatus", "experience",
  "nature", "ability", "heldItem", "moves", "ivDv", "rarity", "storageLocation",
]);
const SOURCE_KEYS = Object.freeze([
  "party", "boxes", "liveBoxes", "exactRecoveryBoxes", "recordIntegrity", "validatedEmpty",
]);
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
  return { covered, total, percent: total > 0 ? Math.round((covered / total) * 10_000) / 100 : null };
}

function evidenceCoverage(value) {
  return {
    ...ratio(value === 1 ? 1 : 0, value == null ? 0 : 1),
    notFound: value === 0 ? 1 : 0,
    notApplicable: value == null ? 1 : 0,
  };
}

function validateEvidenceValue(value, field, id) {
  if (value !== 0 && value !== 1 && value !== null) throw new Error(`invalid ${field} evidence for ${id}`);
}

function indexed(items, label) {
  const result = new Map();
  for (const item of items ?? []) {
    if (!item?.id || result.has(item.id)) throw new Error(`duplicate ${label} control ${item?.id ?? ""}`);
    result.set(item.id, item);
  }
  return result;
}

function aggregate(items) {
  const covered = items.reduce((sum, item) => sum + item.covered, 0);
  const total = items.reduce((sum, item) => sum + item.total, 0);
  return {
    ...ratio(covered, total),
    notFound: items.reduce((sum, item) => sum + item.notFound, 0),
    notApplicable: items.reduce((sum, item) => sum + item.notApplicable, 0),
  };
}

function cohortCoverage(controls, predicate) {
  const selected = controls.filter(predicate);
  return aggregate(selected.map((control) => control.coverage.applicableFields));
}

export function buildSpecimensCompatibility({ identities, evidence, date }) {
  if (evidence?.schemaVersion !== 1) throw new Error(`expected specimen evidence schema 1`);
  const evidenceRefs = evidence.evidenceRefs ?? [];
  if (!Array.isArray(evidenceRefs) || evidenceRefs.length === 0 || evidenceRefs.some((ref) =>
    typeof ref !== "string" || !ref.startsWith("docs/") || /^(?:[A-Za-z]:|[\\/]|(?:file|https?):)/.test(ref))) {
    throw new Error("specimen evidence references must be repository-relative documentation paths");
  }

  const identityList = [
    ...(identities.official ?? []),
    ...(identities.hacks ?? []).map((identity) => ({ ...identity, generation: 3 })),
  ];
  const expectedIds = new Set();
  const expectedHashes = new Set();
  for (const identity of identityList) {
    const hash = identity?.sha256?.toLowerCase();
    if (!identity?.id || !hash?.match(/^[0-9a-f]{64}$/) || expectedIds.has(identity.id) || expectedHashes.has(hash)) {
      throw new Error(`invalid or duplicate expected identity ${identity?.id ?? ""}`);
    }
    expectedIds.add(identity.id);
    expectedHashes.add(hash);
  }
  const evidenceById = indexed(evidence.controls, "specimen evidence");
  const controls = identityList.map((identity) => {
    const control = evidenceById.get(identity.id);
    if (!control) throw new Error(`missing specimen evidence control ${identity.id}`);
    if (control.generation !== identity.generation || control.sha256?.toLowerCase() !== identity.sha256.toLowerCase()) {
      throw new Error(`identity mismatch for ${identity.id}`);
    }
    const resolved = control.profile == null ? control : evidence.profiles?.[control.profile];
    if (!resolved) throw new Error(`missing specimen evidence profile ${control.profile} for ${identity.id}`);
    for (const field of FIELD_KEYS) validateEvidenceValue(resolved.fields?.[field], field, identity.id);
    for (const source of SOURCE_KEYS) validateEvidenceValue(resolved.sources?.[source], source, identity.id);
    if (Object.keys(resolved.fields ?? {}).sort().join() !== [...FIELD_KEYS].sort().join()) {
      throw new Error(`incomplete specimen field evidence for ${identity.id}`);
    }
    if (Object.keys(resolved.sources ?? {}).sort().join() !== [...SOURCE_KEYS].sort().join()) {
      throw new Error(`incomplete specimen source evidence for ${identity.id}`);
    }
    const fields = Object.fromEntries(FIELD_KEYS.map((field) => [field, evidenceCoverage(resolved.fields[field])]));
    const sources = Object.fromEntries(SOURCE_KEYS.map((source) => [source, evidenceCoverage(resolved.sources[source])]));
    return {
      id: identity.id,
      name: DISPLAY_NAMES[identity.id] ?? identity.id,
      generation: identity.generation,
      sha256: identity.sha256.toLowerCase(),
      coverage: {
        ...fields,
        applicableFields: aggregate(Object.values(fields)),
      },
      sourceCoverage: {
        ...sources,
        applicableSources: aggregate(Object.values(sources)),
      },
    };
  });
  if (controls.length !== evidenceById.size) throw new Error(`expected exactly ${controls.length} specimen evidence controls`);

  const fields = Object.fromEntries(FIELD_KEYS.map((field) => [field, aggregate(controls.map((control) => control.coverage[field]))]));
  const sources = Object.fromEntries(SOURCE_KEYS.map((source) => [source, aggregate(controls.map((control) => control.sourceCoverage[source]))]));
  return {
    schemaVersion: 1,
    date,
    feature: "Pokédex Specimens",
    definitions: {
      identity: "A native individual identity when the format provides one; otherwise the specified ROM-save, location and validated-record-digest key.",
      speciesForm: "Canonical species identity plus form/alias normalization when the game stores a form.",
      level: "Structurally validated level or level derived from validated experience and growth data.",
      nickname: "Decoded owned-record nickname.",
      gender: "Decoded or derived gender where the generation implements Pokémon gender.",
      hpStatus: "Current/max HP and status where meaningful for the decoded representation.",
      experience: "Experience progress toward the next level.",
      nature: "Nature identity where the generation implements natures.",
      ability: "Ability identity where the generation implements abilities.",
      heldItem: "Held-item identity or affirmative presence where the generation stores held items.",
      moves: "Owned-record move slots and PP.",
      ivDv: "Six IVs or the generation-appropriate five-value DV projection.",
      rarity: "Existing IV/DV-based rarity-star assessment.",
      storageLocation: "Player-facing Party slot or PC box and slot.",
      liveBoxes: "PC records decoded from the existing live polling read plan, without another poller.",
      exactRecoveryBoxes: "Checksum- and ROM-save-identity-validated SaveRAM recovery when live PC data is unavailable.",
    },
    evidenceRefs,
    controlCount: controls.length,
    controls,
    aggregate: {
      fields,
      sources,
      applicableFields: aggregate(controls.map((control) => control.coverage.applicableFields)),
      applicableSources: aggregate(controls.map((control) => control.sourceCoverage.applicableSources)),
    },
    cohorts: {
      officialGen1: cohortCoverage(controls, (control) => control.generation === 1),
      officialGen2: cohortCoverage(controls, (control) => control.generation === 2),
      officialGen3: cohortCoverage(controls, (control) => control.generation === 3 && !["modern-emerald", "unbound", "odyssey"].includes(control.id)),
      sourceBackedHacks: cohortCoverage(controls, (control) => ["modern-emerald", "unbound", "odyssey"].includes(control.id)),
      allControls: cohortCoverage(controls, () => true),
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
  for (const required of ["identities", "evidence", "out", "date"]) {
    if (!options[required]) throw new Error(`--${required} is required`);
  }
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const [identities, evidence] = await Promise.all(
    [options.identities, options.evidence].map(async (file) => JSON.parse(await readFile(file, "utf8"))),
  );
  const report = buildSpecimensCompatibility({ identities, evidence, date: options.date });
  await mkdir(path.dirname(options.out), { recursive: true });
  await writeFile(options.out, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
