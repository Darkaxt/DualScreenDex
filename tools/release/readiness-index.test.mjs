import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = path => readFileSync(join(repositoryRoot, path), "utf8");

test("current reviewer entry points agree on source-bound zero-gap closure", () => {
  const index = read("docs/current-readiness.md");
  const readme = read("README.md");
  const redirect = read("docs/v1-requirement-matrix.md");
  const marker = JSON.parse(read("release/v1-ready.json"));
  const canonical = JSON.parse(read("release/canonical-corpus.json"));

  assert.match(index, /v1\.1\.0-rc\.86/);
  assert.equal(existsSync(join(repositoryRoot, "release/RELEASE_NOTES_1.1.0-rc.86.md")), true);
  assert.match(index, /Stages 7[–-]8 are closed with zero blockers and zero referrals/i);
  assert.match(index, /333 scanner-eligible.*334-file physical inventory/i);
  assert.equal(canonical.schemaVersion, 2);
  assert.equal(canonical.inputCount, 333);
  assert.equal(canonical.uniqueRomIdentityCount, 331);
  assert.equal(marker.stage, 8);
  assert.equal(marker.status, "ready-for-github-signing");
  assert.equal(marker.openV1LedgerItems, 0);
  assert.equal(marker.qaClosure.stage7Closed, true);
  assert.equal(marker.qaClosure.stage8Closed, true);
  assert.equal(marker.qaClosure.openBlockers, 0);
  assert.equal(marker.qaClosure.openReferrals, 0);
  assert.equal(existsSync(join(repositoryRoot, "release/compatibility-evidence.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "release/canonical-corpus.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-07-corpus-evidence.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-08-closure.json")), true);
  assert.match(readme, /Current release readiness\]\(docs\/current-readiness\.md\)/);
  assert.doesNotMatch(readme, /\[v1 requirement matrix\]\(docs\/v1-requirement-matrix\.md\)/i);
  assert.match(redirect, /stable redirect/);
  assert.match(redirect, /current-readiness\.md/);
});

test("preserves the RC9 matrix only as historical evidence", () => {
  const archive = read("docs/archive/v1-requirement-matrix-rc9.md");

  assert.match(archive, /^# DualDex 1\.0\.0 Requirement Matrix/m);
  assert.match(archive, /signed RC9/);
});
