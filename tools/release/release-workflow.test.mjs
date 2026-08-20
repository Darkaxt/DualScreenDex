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
  assert.match(verifyJob, /bash \.\/gradlew.*test.*lintDebug.*assembleRelease/s);
  assert.doesNotMatch(verifyJob, /^\s*\.\/gradlew/m);
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
  assert.ok(
    signingJob.includes(
      "sed -n -E 's/^(Signer #[0-9]+|V[0-9.]+ Signer):? certificate SHA-256 digest: //p'",
    ),
    "certificate parsing must support both legacy and scheme-qualified apksigner labels",
  );
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

test("publishes the independently gated base, map, evolution, and ARM7 compatibility evidence", () => {
  const requiredEvidence = [
    "dualdex-base-first50-release-gate.json",
    "dualdex-base-first50-release-gate.md",
    "dualdex-base-full332-compatibility-summary.json",
    "dualdex-base-full332-compatibility.md",
    "dualdex-map-first50-release-gate.json",
    "dualdex-map-first50-release-gate.md",
    "dualdex-evolution-first50-release-gate.json",
    "dualdex-evolution-first50-release-gate.md",
    "dualdex-arm7-first50-compatibility.md",
    "dualdex-v1.1-player-state-verification.md",
    "dualdex-unbound-odyssey-static-completion.md",
    "dualdex-unbound-odyssey-map-completion.md",
    "dualdex-unbound-odyssey-save-runtime-completion.md",
    "dualdex-unbound-odyssey-parser-completion.md",
    "dualdex-rom-derived-companion-theme.md",
    "dualdex-gen2-gen3-dynamic-local-map-lighting.md",
  ];

  for (const asset of requiredEvidence) {
    assert.match(workflow, new RegExp(asset.replaceAll(".", "\\.")));
    assert.match(
      workflow.slice(workflow.indexOf("gh release create")),
      new RegExp(asset.replaceAll(".", "\\.")),
      `release upload list is missing ${asset}`,
    );
  }
  assert.match(workflow, /\.summary\.available >= 25/);
  assert.match(workflow, /\.summary\.statusDistribution\.SELECTED == 50/);
  assert.match(workflow, /\.summary\.completeTables == 50/);
  assert.match(workflow, /\.uniqueSha256Identities == 332/);
  assert.match(workflow, /\.mapFirst50Available == 26/);
  assert.match(workflow, /\.evolutionFirst50Complete == 50/);
  assert.match(workflow, /\.v11Stage1NormalizedLiveStateModels == true/);
  assert.match(workflow, /\.v11Stage1DetailedGen3PartyCodec == true/);
  assert.match(workflow, /\.v11Stage1Gen3TrainerBagSaveCodec == true/);
  assert.match(workflow, /\.v11Stage2EmeraldRuntimeLayout == true/);
  assert.match(workflow, /\.v11Stage2TrainerAssets == true/);
  assert.match(workflow, /\.v11Stage2CatalogPersistence == true/);
  assert.match(workflow, /\.v11Stage2TrainerAssetApi == true/);
  assert.match(workflow, /\.v11Stage3PointerFirstSnapshot == true/);
  assert.match(workflow, /\.v11Stage3IndependentLiveSections == true/);
  assert.match(workflow, /\.v11Stage3LiveOverSaveAuthority == true/);
  assert.match(workflow, /\.v11Stage3LifecycleFallback == true/);
  assert.match(workflow, /\.v11Stage4TrainerCard == true/);
  assert.match(workflow, /\.v11Stage4PartyPresentation == true/);
  assert.match(workflow, /\.v11Stage4DynamicFeatureNavigation == true/);
  assert.match(workflow, /\.v11Stage4LocalMapConvergence == true/);
  assert.match(workflow, /\.v11Stage5DisplayContinuityPolicy == true/);
  assert.match(workflow, /\.v11Stage5DisplayLifecycleRecovery == true/);
  assert.match(workflow, /\.v11Stage6OfficialEmeraldVertical == true/);
  assert.match(workflow, /\.v11Stage6UnsupportedDescriptorFallback == true/);
  assert.match(workflow, /\.v11Stage6OpponentMovePrivacy == true/);
  assert.match(workflow, /\.v11Rc16DoubleBattleCommandOwnership == true/);
  assert.match(workflow, /\.v11Rc16MatchupEvidenceMigration == true/);
  assert.match(workflow, /\.v11Stage8NormalizedPartyArtwork == true/);
  assert.match(workflow, /\.v11Stage8PrivacySafePartyFallbacks == true/);
  assert.match(workflow, /\.v11Stage8PartyBrowserGate == true/);
  assert.match(workflow, /\.v11Rc18UnboundOdysseyParserComplete == true/);
  assert.match(workflow, /\.v11Rc18UnboundOdysseyMapComplete == true/);
  assert.match(workflow, /\.v11Rc18OdysseySaveRuntimeComplete == true/);
  assert.match(workflow, /\.v11Rc18UnboundExpandedSaveAbi == true/);
  assert.match(workflow, /\.v11Rc18RomDerivedTheme == true/);
  assert.match(workflow, /\.v11Rc18ThemePersistenceAndApi == true/);
  assert.match(workflow, /\.v11Rc18ThemeBrowserGate == true/);
  assert.match(workflow, /\.v11Rc19Gen2DynamicLocalMaps == true/);
  assert.match(workflow, /\.v11Rc19Gen3TimedLocalMaps == true/);
  assert.match(workflow, /\.v11Rc19DynamicMapPersistenceAndApi == true/);
  assert.match(workflow, /\.v11Rc19DynamicMapWebGate == true/);
  assert.match(workflow, /RELEASE_NOTES_1\.1\.0-rc\.19\.md/);
  assert.match(workflow, /\.v11Rc4HotfixBoundedRomIndex == true/);
  assert.match(workflow, /has\(\"debugApkSha256\"\) \| not/);
});

test("builds only the unsigned release APK before protected signing", () => {
  assert.match(workflow, /:app:assembleRelease/);
  assert.doesNotMatch(workflow, /assembleDebug/);
  assert.doesNotMatch(workflow, /app-debug\.apk/);
  assert.doesNotMatch(readFileSync(join(repositoryRoot, "release", "v1-ready.json"), "utf8"), /debugApkSha256/);
});

test("pins every CI and release action to an immutable commit", () => {
  for (const [name, source] of [
    ["CI", continuousIntegrationWorkflow],
    ["release", workflow],
  ]) {
    const actionReferences = [...source.matchAll(/uses:\s*([^\s#]+)/g)].map(
      (match) => match[1],
    );
    assert.ok(actionReferences.length > 0);
    for (const reference of actionReferences) {
      assert.match(reference, /@[a-f0-9]{40}$/, `${name} floating action reference: ${reference}`);
    }
  }
});
