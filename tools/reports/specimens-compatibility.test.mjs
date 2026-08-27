import assert from "node:assert/strict";
import test from "node:test";

import { buildSpecimensCompatibility } from "./specimens-compatibility.mjs";

const fields = {
  identity: 1,
  speciesForm: 1,
  level: 1,
  nickname: 0,
  gender: null,
  hpStatus: 0,
  experience: 0,
  nature: null,
  ability: null,
  heldItem: null,
  moves: 0,
  ivDv: 1,
  rarity: 1,
  storageLocation: 1,
};
const sources = {
  party: 1,
  boxes: 1,
  liveBoxes: 0,
  exactRecoveryBoxes: 1,
  recordIntegrity: 1,
  validatedEmpty: 1,
};
const identities = {
  official: [{ id: "red", generation: 1, sha256: "a".repeat(64) }],
  // The authoritative identity manifest deliberately omits a redundant
  // generation property from GBA hacks; membership in `hacks` is the Gen III
  // assertion used by the application controls.
  hacks: [{ id: "unbound", sha256: "b".repeat(64) }],
};
const evidence = {
  schemaVersion: 1,
  evidenceRefs: ["docs/reports/gen1-gen2-saveram-compatibility.md"],
  profiles: {
    gen3: {
      fields: Object.fromEntries(Object.keys(fields).map((field) => [field, 1])),
      sources: Object.fromEntries(Object.keys(sources).map((source) => [source, 1])),
    },
  },
  controls: [
    { id: "red", generation: 1, sha256: "a".repeat(64), fields, sources },
    { id: "unbound", generation: 3, sha256: "b".repeat(64), profile: "gen3" },
  ],
};

test("reports every specimen field and source as an independent numeric percentage", () => {
  const report = buildSpecimensCompatibility({ identities, evidence, date: "2026-08-27" });

  assert.deepEqual(report.controls[0].coverage.nickname, {
    covered: 0, total: 1, percent: 0, notFound: 1, notApplicable: 0,
  });
  assert.deepEqual(report.controls[0].coverage.gender, {
    covered: 0, total: 0, percent: null, notFound: 0, notApplicable: 1,
  });
  assert.deepEqual(report.controls[1].coverage.nickname, {
    covered: 1, total: 1, percent: 100, notFound: 0, notApplicable: 0,
  });
  assert.deepEqual(report.aggregate.fields.nickname, {
    covered: 1, total: 2, percent: 50, notFound: 1, notApplicable: 0,
  });
  assert.deepEqual(report.aggregate.fields.gender, {
    covered: 1, total: 1, percent: 100, notFound: 0, notApplicable: 1,
  });
  assert.deepEqual(report.aggregate.sources.liveBoxes, {
    covered: 1, total: 2, percent: 50, notFound: 1, notApplicable: 0,
  });
  assert.equal(report.controls[0].coverage.applicableFields.percent, 60);
  assert.equal(report.controls[1].coverage.applicableFields.percent, 100);
  assert.equal(JSON.stringify(report).includes("D:/"), false);
  assert.deepEqual(report.errors, []);
});

test("fails closed for missing, duplicate, mismatched, or malformed controls", () => {
  assert.throws(
    () => buildSpecimensCompatibility({ identities, evidence: { ...evidence, controls: evidence.controls.slice(0, 1) }, date: "2026-08-27" }),
    /missing specimen evidence control unbound/,
  );
  assert.throws(
    () => buildSpecimensCompatibility({ identities, evidence: { ...evidence, controls: [...evidence.controls, evidence.controls[0]] }, date: "2026-08-27" }),
    /duplicate specimen evidence control red/,
  );
  assert.throws(
    () => buildSpecimensCompatibility({
      identities,
      evidence: { ...evidence, controls: [{ ...evidence.controls[0], sha256: "c".repeat(64) }, evidence.controls[1]] },
      date: "2026-08-27",
    }),
    /identity mismatch for red/,
  );
  assert.throws(
    () => buildSpecimensCompatibility({
      identities,
      evidence: { ...evidence, controls: [{ ...evidence.controls[0], fields: { ...fields, nickname: 2 } }, evidence.controls[1]] },
      date: "2026-08-27",
    }),
    /invalid nickname evidence for red/,
  );
});
