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
const protectedEnvironment = {
  name: "release-signing",
  deployment_branch_policy: { protected_branches: false, custom_branch_policies: true },
  protection_rules: [
    { type: "required_reviewers", reviewers: [{ type: "User", reviewer: { id: 7 } }] },
    { type: "branch_policy" },
  ],
};
const protectedEnvironmentPolicies = {
  branch_policies: [{ id: 8, name: "v1.*", type: "tag" }],
};

test("records active immutable tag and protected signing environment policy", () => {
  const result = verifyRepositoryPolicy({
    rulesets: [protectedRuleset],
    environment: protectedEnvironment,
    environmentPolicies: protectedEnvironmentPolicies,
    tag: "v1.1.0-rc.78",
    repository: "Darkaxt/DualScreenDex",
  });

  assert.equal(result.tagRuleset.id, 42);
  assert.equal(result.signingEnvironment.deploymentTagPolicy, "v1.*");
  assert.equal(result.signingEnvironment.requiredReviewerCount, 1);
  assert.deepEqual(result.signingEnvironment.protectionRuleTypes, ["branch_policy", "required_reviewers"]);
  assert.doesNotMatch(JSON.stringify(result), /reviewer.*id/i);
});

test("rejects a tag outside the ruleset condition", () => {
  assert.throws(
    () => verifyRepositoryPolicy({
      rulesets: [{ ...protectedRuleset, conditions: { ref_name: { include: ["refs/tags/v2.*"], exclude: [] } } }],
      environment: protectedEnvironment,
      environmentPolicies: protectedEnvironmentPolicies,
      tag: "v1.1.0-rc.78",
    }),
    /no active immutable tag ruleset/,
  );
});

test("rejects inactive, mutable, or excluded tag policy", () => {
  for (const ruleset of [
    { ...protectedRuleset, enforcement: "disabled" },
    { ...protectedRuleset, rules: [{ type: "deletion" }] },
    { ...protectedRuleset, bypass_actors: [{ actor_type: "RepositoryRole", actor_id: 5, bypass_mode: "always" }] },
    { ...protectedRuleset, conditions: { ref_name: { include: ["~ALL"], exclude: ["refs/tags/v1.*"] } } },
  ]) {
    assert.throws(
      () => verifyRepositoryPolicy({
        rulesets: [ruleset],
        environment: protectedEnvironment,
        environmentPolicies: protectedEnvironmentPolicies,
        tag: "v1.1.0",
      }),
      /no active immutable tag ruleset/,
    );
  }
});

test("rejects signing without configured environment authorization", () => {
  assert.throws(
    () => verifyRepositoryPolicy({
      rulesets: [protectedRuleset],
      environment: { ...protectedEnvironment, protection_rules: [] },
      environmentPolicies: { branch_policies: [] },
      tag: "v1.1.0",
    }),
    /does not authorize the release tag/,
  );
});
