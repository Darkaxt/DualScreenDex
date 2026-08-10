import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(testDirectory, "../..");
const workflow = readFileSync(
  join(repositoryRoot, ".github", "workflows", "release.yml"),
  "utf8",
);
const continuousIntegrationWorkflow = readFileSync(
  join(repositoryRoot, ".github", "workflows", "ci.yml"),
  "utf8",
);
const gradleBuild = readFileSync(
  join(repositoryRoot, "app", "build.gradle.kts"),
  "utf8",
);

test("keeps every production signing secret inside the protected signing job", () => {
  const signingJob = workflow.indexOf("  sign-and-publish:");
  const firstSecret = workflow.indexOf("${{ secrets.");

  assert.notEqual(signingJob, -1, "missing sign-and-publish job");
  assert.ok(firstSecret > signingJob, "a signing secret is referenced before the protected job");
  assert.match(workflow.slice(signingJob), /environment:\s*release-signing/);
  assert.match(workflow.slice(signingJob), /needs:\s*verify-and-build/);
});

test("tests and builds the unsigned APK before entering the signing environment", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(verifyJob, /node --test tools\/release\/\*\.test\.mjs/);
  assert.match(verifyJob, /gradlew.*test.*lintDebug.*assembleRelease/s);
  assert.match(verifyJob, /upload-artifact@[a-f0-9]{40}/);
  assert.match(verifyJob, /app-release-unsigned\.apk/);
  assert.doesNotMatch(verifyJob, /secrets\./);
});

test("requires the workflow to run from the exact protected source tag", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(verifyJob, /github\.ref_type/);
  assert.match(verifyJob, /github\.ref_name/);
  assert.match(verifyJob, /refs\/tags\/\$RELEASE_TAG/);
  assert.match(verifyJob, /git tag --list.*existing-release-tags/s);
  assert.match(verifyJob, /--existing-tags/);
  assert.match(workflow, /gh release create[\s\S]*--verify-tag/);
  assert.doesNotMatch(workflow, /--target "?\$GITHUB_SHA"?/);
});

test("reconstructs, verifies, signs, independently verifies, and publishes without replacement", () => {
  const signingJob = workflow.slice(workflow.indexOf("  sign-and-publish:"));

  assert.match(signingJob, /DUALDEX_RELEASE_KEYSTORE_B64/);
  assert.match(signingJob, /base64 --decode/);
  assert.match(signingJob, /keytool -exportcert/);
  assert.match(signingJob, /sha256sum/);
  assert.match(signingJob, /zipalign -c -P 16 4/);
  assert.match(signingJob, /apksigner sign/);
  assert.match(signingJob, /apksigner verify --verbose --print-certs/);
  assert.match(signingJob, /aapt dump badging/);
  assert.match(signingJob, /gh release create/);
  assert.match(signingJob, /Refusing to replace/);
  assert.doesNotMatch(signingJob, /upload-artifact/);
  assert.doesNotMatch(workflow, /signing-disabled-until-stage-8/);
});

test("derives release versions from protected Gradle properties", () => {
  assert.match(gradleBuild, /providers\.gradleProperty\("dualdexVersionName"\)/);
  assert.match(gradleBuild, /providers\.gradleProperty\("dualdexVersionCode"\)/);
  assert.doesNotMatch(gradleBuild, /DUALDEX_RELEASE_(KEYSTORE|STORE|KEY)/);
});

test("runs Android deployment safety checks in CI and before release signing", () => {
  const command = /pwsh -File tools\/android\/Test-DualDexAndroidTools\.ps1/;

  assert.match(continuousIntegrationWorkflow, command);
  assert.match(workflow.slice(0, workflow.indexOf("  sign-and-publish:")), command);
});

test("pins every action used by the release workflow to an immutable commit", () => {
  const actionReferences = [...workflow.matchAll(/uses:\s*([^\s#]+)/g)].map((match) => match[1]);

  assert.ok(actionReferences.length > 0);
  for (const reference of actionReferences) {
    assert.match(reference, /@[a-f0-9]{40}$/, `floating action reference: ${reference}`);
  }
});
