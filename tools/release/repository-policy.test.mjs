import assert from "node:assert/strict";
import test from "node:test";
import { verifyRepositoryPolicy } from "./verify-repository-policy.mjs";

const protectedRuleset = {
  id: 42,
  name: "immutable-v1-release-tags",
  target: "tag",
  enforcement: "active",
  conditions: {
    ref_name: { include: ["refs/tags/v1.*"], exclude: [] },
  },
  rules: [{ type: "deletion" }, { type: "update" }],
};

function signingEnvironment() {
  return {
    name: "release-signing",
    deployment_branch_policy: { protected_branches: false, custom_branch_policies: true },
    protection_rules: [{ type: "branch_policy" }],
  };
}

function promotionEnvironment() {
  return {
    name: "release-promotion",
    deployment_branch_policy: { protected_branches: false, custom_branch_policies: true },
    protection_rules: [
      {
        type: "required_reviewers",
        prevent_self_review: false,
        reviewers: [{ type: "User", reviewer: { id: 7 } }],
      },
      { type: "branch_policy" },
    ],
  };
}

function fixture(overrides = {}) {
  return {
    rulesets: [protectedRuleset],
    signingEnvironment: signingEnvironment(),
    signingEnvironmentPolicies: {
      branch_policies: [{ id: 8, name: "v1.*", type: "tag" }],
    },
    promotionEnvironment: promotionEnvironment(),
    promotionEnvironmentPolicies: {
      branch_policies: [{ id: 9, name: "main", type: "branch" }],
    },
    promotionSigningSecretCount: 0,
    tag: "v1.1.0-rc.78",
    repository: "example/DualDex",
    defaultBranch: "main",
    ...overrides,
  };
}

test("records exact protected signing and promotion environment policy", () => {
  const result = verifyRepositoryPolicy(fixture());

  assert.equal(result.tagRuleset.id, 42);
  assert.equal(result.signingEnvironment.requiredReviewerCount, 0);
  assert.equal(result.signingEnvironment.preventSelfReview, false);
  assert.equal(result.promotionEnvironment.requiredReviewerCount, 1);
  assert.equal(result.promotionEnvironment.preventSelfReview, false);
  assert.equal(result.promotionEnvironment.deploymentBranchPolicy, "main");
  assert.equal(result.promotionEnvironment.signingSecretCount, 0);
  assert.doesNotMatch(JSON.stringify(result), /reviewer.*id/i);
});

test("rejects a tag outside active immutable rules", () => {
  assert.throws(
    () => verifyRepositoryPolicy(fixture({
      rulesets: [{
        ...protectedRuleset,
        conditions: { ref_name: { include: ["refs/tags/v2.*"], exclude: [] } },
      }],
    })),
    /no active immutable tag ruleset/i,
  );
});

test("requires an eligible promotion reviewer while signing remains tag-gated", () => {
  const missing = promotionEnvironment();
  missing.protection_rules = missing.protection_rules.filter(rule => rule.type !== "required_reviewers");
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionEnvironment: missing })),
    /eligible reviewer/i,
  );

  const ineligible = promotionEnvironment();
  ineligible.protection_rules[0].reviewers = [{ type: "User", reviewer: null }];
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionEnvironment: ineligible })),
    /eligible reviewer/i,
  );
});

test("rejects wrong branch policy, extra rules, and promotion signing secrets", () => {
  const extraSigningRule = signingEnvironment();
  extraSigningRule.protection_rules.unshift({
    type: "required_reviewers",
    prevent_self_review: false,
    reviewers: [{ type: "User", reviewer: { id: 7 } }],
  });
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ signingEnvironment: extraSigningRule })),
    /exact protection rules/i,
  );

  assert.throws(
    () => verifyRepositoryPolicy(fixture({
      promotionEnvironmentPolicies: {
        branch_policies: [{ id: 9, name: "develop", type: "branch" }],
      },
    })),
    /default branch/i,
  );

  const extraRules = promotionEnvironment();
  extraRules.protection_rules.push({ type: "wait_timer", wait_timer: 10 });
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionEnvironment: extraRules })),
    /exact protection rules/i,
  );

  const duplicateRule = promotionEnvironment();
  duplicateRule.protection_rules.push({ type: "branch_policy" });
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionEnvironment: duplicateRule })),
    /exact protection rules/i,
  );

  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionSigningSecretCount: 1 })),
    /promotion.*signing secrets/i,
  );
});
