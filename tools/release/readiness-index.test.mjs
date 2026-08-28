import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = path => readFileSync(join(repositoryRoot, path), "utf8");

test("current reviewer entry points agree on RC77 readiness", () => {
  const index = read("docs/current-readiness.md");
  const readme = read("README.md");
  const redirect = read("docs/v1-requirement-matrix.md");

  assert.match(index, /v1\.1\.0-rc\.77/);
  assert.match(index, /release\/RELEASE_NOTES_1\.1\.0-rc\.77\.md/);
  assert.match(index, /release\/compatibility-evidence\.json/);
  assert.match(index, /stage-07-corpus-evidence\.md/);
  assert.match(index, /archive\/v1-requirement-matrix-rc9\.md/);
  assert.match(readme, /Current release readiness\]\(docs\/current-readiness\.md\)/);
  assert.doesNotMatch(readme, /\[v1 requirement matrix\]\(docs\/v1-requirement-matrix\.md\)/i);
  assert.match(redirect, /stable redirect/);
  assert.match(redirect, /current-readiness\.md/);
  assert.doesNotMatch(redirect, /release remains blocked|physical .* pending/i);
  for (const target of [
    "release/RELEASE_NOTES_1.1.0-rc.77.md",
    "release/v1-ready.json",
    "release/compatibility-evidence.json",
    "docs/reports/qa-hardening/stage-07-corpus-evidence.json",
    "docs/reports/qa-hardening/stage-07-corpus-evidence.md",
    "docs/archive/v1-requirement-matrix-rc9.md",
  ]) {
    assert.ok(existsSync(join(repositoryRoot, target)), `readiness target is missing: ${target}`);
  }
});

test("preserves the RC9 matrix only as historical evidence", () => {
  const archive = read("docs/archive/v1-requirement-matrix-rc9.md");

  assert.match(archive, /^# DualDex 1\.0\.0 Requirement Matrix/m);
  assert.match(archive, /signed RC9/);
});
