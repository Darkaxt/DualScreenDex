import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SIGNING_PROTECTION_RULES = ["branch_policy"];
const PROMOTION_PROTECTION_RULES = ["branch_policy", "required_reviewers"];

export function verifyRepositoryPolicy({
  rulesets,
  signingEnvironment,
  signingEnvironmentPolicies,
  promotionEnvironment,
  promotionEnvironmentPolicies,
  promotionSigningSecretReferenceCount,
  tag,
  repository,
  defaultBranch,
}) {
  assert(/^v1\.[0-9A-Za-z.+-]+$/.test(tag ?? ""), "release tag must use the v1.* namespace");
  assert(typeof defaultBranch === "string" && defaultBranch.length > 0, "default branch is required");
  assert(Array.isArray(rulesets), "rulesets response must be an array");
  const ref = `refs/tags/${tag}`;
  const protectingRuleset = rulesets.find(ruleset => protectsTag(ruleset, ref));
  assert(protectingRuleset, `no active immutable tag ruleset protects ${ref}`);

  const signing = validateEnvironment({
    environment: signingEnvironment,
    policiesResponse: signingEnvironmentPolicies,
    expectedName: "release-signing",
    expectedPolicyType: "tag",
    expectedPolicyValue: tag,
    expectedPolicyDescription: `release tag ${tag}`,
    expectedProtectionRules: SIGNING_PROTECTION_RULES,
    requireReviewer: false,
  });
  const promotion = validateEnvironment({
    environment: promotionEnvironment,
    policiesResponse: promotionEnvironmentPolicies,
    expectedName: "release-promotion",
    expectedPolicyType: "branch",
    expectedPolicyValue: defaultBranch,
    expectedPolicyDescription: `default branch ${defaultBranch}`,
    expectedProtectionRules: PROMOTION_PROTECTION_RULES,
    requireReviewer: true,
  });
  assert(promotionSigningSecretReferenceCount === 0,
    "promotion workflow must reference zero production signing secrets");

  return {
    schemaVersion: 2,
    repository,
    tag,
    defaultBranch,
    tagRuleset: {
      id: protectingRuleset.id,
      name: protectingRuleset.name,
      enforcement: protectingRuleset.enforcement,
      requiredRuleTypes: ["deletion", "update"],
    },
    signingEnvironment: signing,
    promotionEnvironment: {
      ...promotion,
      signingSecretReferenceCount: promotionSigningSecretReferenceCount,
    },
  };
}

function validateEnvironment({
  environment,
  policiesResponse,
  expectedName,
  expectedPolicyType,
  expectedPolicyValue,
  expectedPolicyDescription,
  expectedProtectionRules,
  requireReviewer,
}) {
  assert(environment?.name === expectedName, `${expectedName} environment is missing or mismatched`);
  assert(environment.deployment_branch_policy?.protected_branches === false &&
    environment.deployment_branch_policy?.custom_branch_policies === true,
  `${expectedName} must use only custom deployment branch policies`);
  const policies = Array.isArray(policiesResponse?.branch_policies) ? policiesResponse.branch_policies : [];
  const matchingPolicies = policies.filter(policy =>
    policy?.type === expectedPolicyType && matchesRef(policy.name, expectedPolicyValue));
  assert(matchingPolicies.length === 1 && policies.length === 1,
    `${expectedName} must authorize exactly the ${expectedPolicyDescription}`);

  const protectionRules = Array.isArray(environment.protection_rules) ? environment.protection_rules : [];
  const reviewerRule = protectionRules.find(rule => rule?.type === "required_reviewers");
  const eligibleReviewers = Array.isArray(reviewerRule?.reviewers)
    ? reviewerRule.reviewers.filter(entry =>
        ["User", "Team"].includes(entry?.type) &&
        Number.isInteger(entry?.reviewer?.id) && entry.reviewer.id > 0)
    : [];
  if (requireReviewer) {
    assert(eligibleReviewers.length > 0,
      `${expectedName} requires at least one eligible reviewer`);
  }
  const preventSelfReview = reviewerRule?.prevent_self_review === true ||
    environment.prevent_self_review === true;
  const ruleTypes = [...new Set(protectionRules.map(rule => rule?.type))].sort();
  assert(protectionRules.length === expectedProtectionRules.length &&
    JSON.stringify(ruleTypes) === JSON.stringify(expectedProtectionRules),
  `${expectedName} must have the exact protection rules`);

  return {
    name: environment.name,
    deploymentBranchPolicy: matchingPolicies[0].name,
    requiredReviewerCount: eligibleReviewers.length,
    preventSelfReview,
    protectionRuleTypes: ruleTypes,
  };
}

function protectsTag(ruleset, ref) {
  if (ruleset?.target !== "tag" || ruleset.enforcement !== "active") return false;
  if (Array.isArray(ruleset.bypass_actors) && ruleset.bypass_actors.length > 0) return false;
  const names = ruleset.conditions?.ref_name;
  const included = Array.isArray(names?.include) && names.include.some(pattern => matchesRef(pattern, ref));
  const excluded = Array.isArray(names?.exclude) && names.exclude.some(pattern => matchesRef(pattern, ref));
  if (!included || excluded) return false;
  const ruleTypes = new Set((ruleset.rules ?? []).map(rule => rule?.type));
  return ruleTypes.has("deletion") && ruleTypes.has("update");
}

function matchesRef(pattern, ref) {
  if (pattern === "~ALL") return true;
  if (typeof pattern !== "string") return false;
  const doubleStar = "__DUALDEX_DOUBLE_STAR__";
  const expression = pattern
    .replace(/[.+^${}()|[\]\\]/g, "\\$&")
    .replaceAll("**", doubleStar)
    .replaceAll("*", "[^/]*")
    .replaceAll("?", "[^/]")
    .replaceAll(doubleStar, ".*");
  return new RegExp(`^${expression}$`).test(ref);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function parseArguments(arguments_) {
  const options = {};
  for (let index = 0; index < arguments_.length; index += 2) {
    const key = arguments_[index];
    const value = arguments_[index + 1];
    if (!key?.startsWith("--") || value == null) throw new Error(`Invalid argument: ${key ?? "<missing>"}`);
    options[key.slice(2)] = value;
  }
  return options;
}

function readJson(path) {
  return JSON.parse(readFileSync(resolve(path), "utf8"));
}

function main(arguments_) {
  const options = parseArguments(arguments_);
  const result = verifyRepositoryPolicy({
    rulesets: readJson(options.rulesets),
    signingEnvironment: readJson(options["signing-environment"]),
    signingEnvironmentPolicies: readJson(options["signing-environment-policies"]),
    promotionEnvironment: readJson(options["promotion-environment"]),
    promotionEnvironmentPolicies: readJson(options["promotion-environment-policies"]),
    promotionSigningSecretReferenceCount: Number(options["promotion-signing-secret-reference-count"]),
    tag: options.tag,
    repository: options.repository,
    defaultBranch: options["default-branch"],
  });
  const encoded = `${JSON.stringify(result, null, 2)}\n`;
  if (options.output) writeFileSync(resolve(options.output), encoded);
  else process.stdout.write(encoded);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    main(process.argv.slice(2));
  } catch (failure) {
    process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`);
    process.exitCode = 1;
  }
}
