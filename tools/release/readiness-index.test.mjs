import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = path => readFileSync(join(repositoryRoot, path), "utf8");

test("current reviewer entry points agree that final evidence and closure are blocked", () => {
  const index = read("docs/current-readiness.md");
  const readme = read("README.md");
  const redirect = read("docs/v1-requirement-matrix.md");
  const marker = JSON.parse(read("release/v1-ready.json"));

  assert.match(index, /v1\.1\.0-rc\.77/);
  assert.match(index, /blocked while project-wide QA/i);
  assert.match(index, /No final corpus or zero-gap closure evidence is currently tracked/i);
  assert.match(index, /canonical 334-input execution receipt/i);
  assert.equal(marker.stage, 7);
  assert.equal(marker.status, "blocked-pending-project-wide-qa-closure");
  assert.ok(marker.openV1LedgerItems > 0);
  assert.equal(existsSync(join(repositoryRoot, "release/compatibility-evidence.json")), false);
  assert.equal(existsSync(join(repositoryRoot, "release/canonical-corpus.json")), false);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-07-corpus-evidence.json")), false);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-08-closure.json")), false);
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
