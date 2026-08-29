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

function environment(name) {
  return {
    name,
    deployment_branch_policy: { protected_branches: false, custom_branch_policies: true },
    protection_rules: [
      {
        type: "required_reviewers",
        prevent_self_review: true,
        reviewers: [{ type: "User", reviewer: { id: 7 } }],
      },
      { type: "branch_policy" },
    ],
  };
}

function fixture(overrides = {}) {
  return {
    rulesets: [protectedRuleset],
    signingEnvironment: environment("release-signing"),
    signingEnvironmentPolicies: {
      branch_policies: [{ id: 8, name: "v1.*", type: "tag" }],
    },
    promotionEnvironment: environment("release-promotion"),
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
  assert.equal(result.signingEnvironment.requiredReviewerCount, 1);
  assert.equal(result.signingEnvironment.preventSelfReview, true);
  assert.equal(result.promotionEnvironment.requiredReviewerCount, 1);
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

test("rejects missing or ineligible reviewers independently for both environments", () => {
  for (const key of ["signingEnvironment", "promotionEnvironment"]) {
    const name = key === "signingEnvironment" ? "release-signing" : "release-promotion";
    const missing = environment(name);
    missing.protection_rules = missing.protection_rules.filter(rule => rule.type !== "required_reviewers");
    assert.throws(
      () => verifyRepositoryPolicy(fixture({ [key]: missing })),
      /eligible reviewer/i,
      `${key} missing reviewer rule`,
    );

    const ineligible = environment(name);
    ineligible.protection_rules[0].reviewers = [{ type: "User", reviewer: null }];
    assert.throws(
      () => verifyRepositoryPolicy(fixture({ [key]: ineligible })),
      /eligible reviewer/i,
      `${key} ineligible reviewer`,
    );
  }
});

test("rejects self-review, wrong branch policy, extra rules, and promotion signing secrets", () => {
  const selfReview = environment("release-signing");
  selfReview.protection_rules[0].prevent_self_review = false;
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ signingEnvironment: selfReview })),
    /self-review prevention/i,
  );

  assert.throws(
    () => verifyRepositoryPolicy(fixture({
      promotionEnvironmentPolicies: {
        branch_policies: [{ id: 9, name: "develop", type: "branch" }],
      },
    })),
    /default branch/i,
  );

  const extraRules = environment("release-promotion");
  extraRules.protection_rules.push({ type: "wait_timer", wait_timer: 10 });
  assert.throws(
    () => verifyRepositoryPolicy(fixture({ promotionEnvironment: extraRules })),
    /exact protection rules/i,
  );

  const duplicateRule = environment("release-promotion");
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
