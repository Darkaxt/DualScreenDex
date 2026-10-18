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
  const currentCorpus = JSON.parse(read("release/current-library-corpus.json"));
  const evidence = JSON.parse(read("release/compatibility-evidence.json"));
  const summary = JSON.parse(read(evidence.artifacts.find(artifact => artifact.role === "CORPUS_SUMMARY").path));

  const stable = index.match(/Latest public stable:\*\* \[`(v\d+\.\d+\.\d+)`\]\(https:\/\/github\.com\/Darkaxt\/DualScreenDex\/releases\/tag\/\1\)/);
  assert.ok(stable, "latest stable link must identify the same version in its label and URL");
  assert.equal(existsSync(join(repositoryRoot, `release/RELEASE_NOTES_${stable[1].slice(1)}.md`)), true);
  assert.ok(index.includes(`**Prepared next release:** \`v${marker.versionName}\``));
  assert.match(index, /Historical Stage7\/8 infrastructure closures retain their original source attribution/i);
  assert.equal(marker.qaClosure.evidenceSourceCommit, evidence.sourceCommit);
  assert.equal(summary.sourceCommit, evidence.sourceCommit);
  assert.equal(marker.qaClosure.inputCount, currentCorpus.inputCount);
  assert.equal(summary.inputCount, currentCorpus.inputCount);
  assert.equal(summary.uniqueRomIdentities, currentCorpus.uniqueRomIdentityCount);
  for (const artifact of evidence.artifacts) {
    assert.equal(existsSync(join(repositoryRoot, artifact.path)), true, artifact.path);
  }
  assert.match(index, /333 scanner-eligible inputs.*334-file physical inventory/i);
  assert.equal(canonical.schemaVersion, 2);
  assert.equal(canonical.inputCount, 333);
  assert.equal(canonical.uniqueRomIdentityCount, 331);
  assert.equal(marker.stage, 8);
  assert.equal(marker.status, "ready-for-github-signing");
  assert.equal(marker.openV1LedgerItems, 0);
  assert.equal(marker.qaClosure.stage7Closed, true);
  assert.equal(marker.qaClosure.stage8Closed, true);
  assert.equal(marker.qaClosure.localizationClosed, true);
  assert.equal(marker.qaClosure.openBlockers, 0);
  assert.equal(marker.qaClosure.openReferrals, 0);
  assert.equal(existsSync(join(repositoryRoot, "release/compatibility-evidence.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "release/canonical-corpus.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-07-corpus-evidence.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/qa-hardening/stage-08-closure.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/localization/stage-06-corpus-evidence.json")), true);
  assert.equal(existsSync(join(repositoryRoot, "docs/reports/localization/stage-06-corpus-execution.json")), true);
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
