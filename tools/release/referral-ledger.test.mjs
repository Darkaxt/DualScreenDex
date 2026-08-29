import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const requiredFields = [
  "Requirements",
  "Modules",
  "Reason",
  "Target",
  "Dependency",
  "Acceptance",
];

function read(path) {
  return readFileSync(join(repositoryRoot, path), "utf8");
}

function lintReferralLedger(markdown) {
  const referralSection = markdown.match(
    /^### Tracked referrals\s*$([\s\S]*?)(?=^##\s|\z)/m,
  );
  const errors = [];

  if (!referralSection) {
    return ["missing Tracked referrals section"];
  }

  const records = referralSection[1]
    .split(/^####\s+/m)
    .slice(1)
    .map(record => record.trim());
  const identifiers = new Set();

  if (records.length === 0) {
    errors.push("Tracked referrals section has no records");
  }

  for (const record of records) {
    const heading = record.match(/^([^\n]+)$/m)?.[1] ?? "";
    const identifier = heading.match(/^([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+)\s+—\s+/)?.[1];
    const label = identifier ?? `invalid heading ${JSON.stringify(heading)}`;

    if (!identifier) {
      errors.push(`${label}: missing unique ID`);
    } else if (identifiers.has(identifier)) {
      errors.push(`${identifier}: duplicate unique ID`);
    } else {
      identifiers.add(identifier);
    }

    for (const field of requiredFields) {
      const value = record.match(
        new RegExp(`^- \\*\\*${field}:\\*\\*\\s*(\\S[\\s\\S]*?)\\s*$`, "m"),
      )?.[1];
      if (!value) {
        errors.push(`${label}: missing ${field}`);
      }
    }
  }

  return errors;
}

function assertReferralLedgerIsComplete(markdown) {
  assert.deepEqual(lintReferralLedger(markdown), []);
}

test("Stage 1 referral records contain the complete governance ledger", () => {
  assertReferralLedgerIsComplete(read("docs/reports/qa-hardening/stage-01-closure.md"));
});

test("documentation lint rejects a referral record without Dependency", () => {
  const ledger = read("docs/reports/qa-hardening/stage-01-closure.md");
  const fixture = ledger.replace(
    /^- \*\*Dependency:\*\*.*$/m,
    "",
  );

  assert.notEqual(fixture, ledger, "fixture must omit a real Dependency field");
  assert.throws(
    () => assertReferralLedgerIsComplete(fixture),
    /S1-REF-01: missing Dependency/,
  );
});
