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
const promotionWorkflow = readFileSync(
  join(repositoryRoot, ".github", "workflows", "promote-candidate.yml"),
  "utf8",
);
const packagedAndroidWorkflow = readFileSync(
  join(repositoryRoot, ".github", "workflows", "packaged-android.yml"),
  "utf8",
);
const releaseMetadata = readFileSync(
  join(repositoryRoot, "tools", "release", "derive-release-metadata.mjs"),
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
const catalogSchema = readFileSync(
  join(repositoryRoot, "catalog-store", "src", "main", "kotlin", "com", "darkaxt", "dualdex", "catalog", "CatalogSchema.kt"),
  "utf8",
);

test("keeps candidates draft until protected exact-artifact promotion", () => {
  assert.match(releaseMetadata, /draft:\s*String\(parsedTag\.isCandidate\)/);
  assert.match(workflow, /if \[\[ "\$DRAFT" == "true" \]\]; then flags\+=\(--draft\); fi/);
  assert.match(promotionWorkflow, /environment:\s*release-promotion/);
  assert.match(promotionWorkflow, /permissions:\s*\n\s*contents:\s*write\s*\n\s*deployments:\s*read\s*\n\s*actions:\s*read/);
  assert.match(promotionWorkflow, /release\/candidate-promotions\/\$RELEASE_TAG\.json/);
  assert.match(promotionWorkflow, /validate-candidate-promotion\.mjs/);
  assert.match(promotionWorkflow, /apksigner verify --verbose --print-certs/);
  assert.match(promotionWorkflow, /--apk-signer-verification/);
  assert.match(promotionWorkflow, /actions\/runs\/\$run_id/);
  assert.match(promotionWorkflow, /\.conclusion == "success"/);
  assert.match(promotionWorkflow, /gh release download/);
  assert.match(promotionWorkflow, /initial-release-assets\.json/);
  assert.match(promotionWorkflow, /current-release-assets\.json/);
  assert.match(promotionWorkflow, /-F draft=false/);
  assert.match(promotionWorkflow, /-F prerelease=true/);
});

test("runs reusable installed-app acceptance before signing or promotion", () => {
  assert.match(gradleBuild, /localDevices\s*\{[\s\S]*create\("qaApi35"\)/);
  assert.match(gradleBuild, /testInstrumentationRunnerArguments\["useTestStorageService"\]\s*=\s*"true"/);
  assert.match(packagedAndroidWorkflow, /workflow_call:/);
  assert.match(packagedAndroidWorkflow, /workflow_dispatch:/);
  assert.match(packagedAndroidWorkflow, /:app:qaApi35DebugAndroidTest --stacktrace/);
  assert.match(packagedAndroidWorkflow, /managed_device_android_test_additional_output/);
  assert.match(packagedAndroidWorkflow, /androidTest-results\/managedDevice/);
  assert.match(packagedAndroidWorkflow, /dualdex-packaged-android-evidence/);
  assert.ok(
    packagedAndroidWorkflow.indexOf(":app:qaApi35DebugAndroidTest") <
      packagedAndroidWorkflow.indexOf("dualdex-packaged-android-evidence"),
    "packaged evidence must be emitted only after the managed-device task",
  );
  assert.match(continuousIntegrationWorkflow, /uses:\s*\.\/\.github\/workflows\/packaged-android\.yml/);
  assert.match(workflow, /packaged-android-acceptance:[\s\S]*uses:\s*\.\/\.github\/workflows\/packaged-android\.yml/);
  assert.match(workflow, /needs:\s*\[verify-and-build, packaged-android-acceptance\]/);
});

test("gates CI and release with portable public Chromium acceptance", () => {
  const browserJob = continuousIntegrationWorkflow.slice(
    continuousIntegrationWorkflow.indexOf("  browser-e2e:"),
    continuousIntegrationWorkflow.indexOf("  packaged-android:"),
  );
  const unsignedReleaseJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(browserJob, /runs-on:\s*ubuntu-latest/);
  assert.match(browserJob, /working-directory:\s*companion-web[\s\S]*run:\s*npm ci/);
  assert.match(browserJob, /npx playwright install --with-deps chromium/);
  assert.match(browserJob, /npm run test:e2e:ci/);
  assert.match(unsignedReleaseJob, /npx playwright install --with-deps chromium/);
  assert.match(unsignedReleaseJob, /npm run test:e2e:ci/);
  assert.ok(
    unsignedReleaseJob.indexOf("npm run test:e2e:ci") <
      unsignedReleaseJob.indexOf(":app:assembleRelease"),
    "public Chromium acceptance must finish before the unsigned release build",
  );
});

test("promotion verifies immutable packaged evidence from the pinned workflow", () => {
  assert.match(promotionWorkflow, /\.path == \$path/);
  assert.match(promotionWorkflow, /\.github\/workflows\/release\.yml/);
  assert.match(promotionWorkflow, /dualdex-packaged-android-evidence/);
  assert.match(promotionWorkflow, /packagedAndroidEvidenceArtifactDigest/);
  assert.match(promotionWorkflow, /actions\/artifacts\/\$artifact_id\/zip/);
  assert.match(promotionWorkflow, /qaApi35DebugAndroidTest/);
});

test("promotion never rebuilds, resigns, uploads, or replaces the candidate APK", () => {
  assert.doesNotMatch(promotionWorkflow, /gradlew|assemble|apksigner sign|zipalign/);
  assert.doesNotMatch(promotionWorkflow, /\$\{\{\s*secrets\.|gh release create|gh release upload/);
  assert.doesNotMatch(promotionWorkflow, /actions\/upload-artifact/);
});

test("keeps every production signing secret inside the protected signing job", () => {
  const signingJob = workflow.indexOf("  sign-and-publish:");
  const firstSecret = workflow.indexOf("${{ secrets.");

  assert.notEqual(signingJob, -1, "missing sign-and-publish job");
  assert.ok(firstSecret > signingJob, "a signing secret is referenced before the protected job");
  assert.match(workflow.slice(signingJob), /environment:\s*release-signing/);
  assert.match(workflow.slice(signingJob), /needs:\s*\[verify-and-build, packaged-android-acceptance\]/);
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
  assert.doesNotMatch(verifyJob, /\$\{\{\s*secrets\./);
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

test("audits source-tag and signing-environment policy before unsigned handoff", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(verifyJob, /repos\/\$GITHUB_REPOSITORY\/rulesets/);
  assert.match(verifyJob, /repos\/\$GITHUB_REPOSITORY\/environments\/release-signing/);
  assert.match(verifyJob, /release-signing\/deployment-branch-policies/);
  assert.match(verifyJob, /--signing-environment-policies/);
  assert.match(verifyJob, /verify-repository-policy\.mjs/);
  assert.match(workflow, /permissions:\s*\n\s*contents:\s*read\s*\n\s*deployments:\s*read\s*\n\s*actions:\s*read/);
  assert.match(verifyJob, /repository-policy\.json/);
  assert.ok(
    verifyJob.indexOf("verify-repository-policy.mjs") < verifyJob.indexOf("Stage unsigned build handoff"),
    "repository policy must pass before unsigned handoff",
  );
});

test("binds compatibility evidence and repository policy into provenance", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );
  const signingJob = workflow.slice(workflow.indexOf("  sign-and-publish:"));

  assert.match(verifyJob, /validate-release-evidence\.mjs/);
  assert.match(verifyJob, /--manifest release\/compatibility-evidence\.json/);
  assert.match(verifyJob, /--release-commit "\$GITHUB_SHA"/);
  assert.ok(
    verifyJob.indexOf("validate-release-evidence.mjs") < verifyJob.indexOf(":app:assembleRelease"),
    "source-bound evidence must pass before the release build",
  );
  assert.match(signingJob, /compatibilityEvidence: \$compatibilityEvidence\[0\]/);
  assert.match(signingJob, /repositoryPolicy: \$repositoryPolicy\[0\]/);
  for (const asset of ["compatibility-evidence.json", "repository-policy.json"]) {
    assert.match(signingJob, new RegExp(asset.replaceAll(".", "\\.")));
    assert.match(
      signingJob.slice(signingJob.indexOf("gh release create")),
      new RegExp(asset.replaceAll(".", "\\.")),
    );
  }
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

test("requires a machine-readable cache decision for parser and catalog changes", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(verifyJob, /--canonical-corpus release\/canonical-corpus\.json/);
  assert.match(verifyJob, /--catalog-schema/);
  assert.match(verifyJob, /--output "\$RUNNER_TEMP\/release-evidence-validation\.json"/);
  assert.match(verifyJob, /--release-evidence-validation/);
  assert.doesNotMatch(workflow, /parserSchemaVersion\s*==\s*45/);
});

test("runs every included JVM and app unit suite in CI", () => {
  const unitTestStep = continuousIntegrationWorkflow.slice(
    continuousIntegrationWorkflow.indexOf("      - name: Run Kotlin tests"),
    continuousIntegrationWorkflow.indexOf("      - name: Test web UI"),
  );

  assert.match(
    unitTestStep,
    /gradlew\.bat verifySecureBuildDependencies test :app:testDebugUnitTest --stacktrace/,
  );
  assert.doesNotMatch(unitTestStep, /:parser-core:test/);
});

test("runs Android deployment safety checks in CI and before release signing", () => {
  const command = /pwsh -File tools\/android\/Test-DualDexAndroidTools\.ps1/;

  assert.match(continuousIntegrationWorkflow, command);
  assert.match(workflow.slice(0, workflow.indexOf("  sign-and-publish:")), command);
});

test("publishes independently gated compatibility and UI-conformance evidence", () => {
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
    "dualdex-gen1-gen3-table-coverage.json",
    "dualdex-gen1-gen3-table-coverage.md",
    "dualdex-party-analysis-compatibility.json",
    "dualdex-party-analysis-audit.md",
    "dualdex-area-guide-compatibility.json",
    "dualdex-area-guide-audit.md",
    "dualdex-progress-timeline-compatibility.json",
    "dualdex-progress-timeline-audit.md",
    "dualdex-specimens-compatibility.json",
    "dualdex-specimens-audit.md",
    "dualdex-damage-forecast-compatibility.json",
    "dualdex-damage-forecast-audit.md",
    "dualdex-challenge-expansion-compatibility.json",
    "dualdex-challenge-expansion-audit.md",
    "dualdex-ui-conformance-audit.md",
    "dualdex-ui-conformance-route-matrix.json",
    "dualdex-ui-conformance-font-matrix.json",
    "dualdex-ui-conformance-font-matrix.md",
    "dualdex-ui-conformance-computed-styles.json",
    "dualdex-ui-conformance-screenshots.json",
    "dualdex-ui-conformance-summary.json",
    "dualdex-ui-conformance-summary.md",
    "dualdex-qa-hardening-convergence.md",
    "dualdex-storage-guide-load-hardening.md",
    "dualdex-save-synchronized-knowledge-checkpoints.md",
    "dualdex-stage-07-corpus-evidence.json",
    "dualdex-stage-07-corpus-evidence.md",
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
  assert.match(workflow, /\.v11Rc20ArchiveContainerIdentity == true/);
  assert.match(workflow, /\.v11Rc21WideAbilityDomainFailClosed == true/);
  assert.match(workflow, /\.v11Rc22GranularLoadingProgress == true/);
  assert.match(workflow, /\.v11Rc22SaveScopedKnowledgeIntegrity == true/);
  assert.match(workflow, /\.v11Rc22OrganicPositiveStatus == true/);
  assert.match(workflow, /\.v11Rc22AdaptiveClockContrast == true/);
  assert.match(workflow, /\.v11Rc23Gen1CompiledRelationships == true/);
  assert.match(workflow, /\.v11Rc23Gen1CompiledTypeChart == true/);
  assert.match(workflow, /\.v11Rc23Gen1OptionalFailureIsolation == true/);
  assert.match(workflow, /\.v11Rc24PokedexCaughtPortraitBadge == true/);
  assert.match(workflow, /\.v11Rc24InteractivePartyRoster == true/);
  assert.match(workflow, /\.v11Rc24CohesiveTrainerCard == true/);
  assert.match(workflow, /\.v11Rc24PartyExperienceProgress == true/);
  assert.match(workflow, /\.v11Rc25Gen1CompiledMoveDomain == true/);
  assert.match(workflow, /\.v11Rc25Gen1CompiledMachineMoves == true/);
  assert.match(workflow, /\.v11Rc25Gen1ExpandedMachineDomain == true/);
  assert.match(workflow, /\.v11Rc25Gen1CompiledDescriptions == true/);
  assert.match(workflow, /\.v11Rc25Gen1RecordSpriteBanks == true/);
  assert.match(workflow, /\.v11Rc28RomDerivedNatureCatalog == true/);
  assert.match(workflow, /\.v11Rc30SharpLocalRaster == true/);
  assert.match(workflow, /\.v11Rc30PlayerCenteredRecenter == true/);
  assert.match(workflow, /\.v11Rc30DiscoverySafeLocalLoading == true/);
  assert.match(workflow, /\.v11Rc30RomDerivedMapAvatar == true/);
  assert.match(workflow, /\.v11Rc31ThemeConsolidation == true/);
  assert.match(workflow, /\.v11Rc31NatureResolutionSupport == true/);
  assert.match(workflow, /\.v11Rc31GlobalLocalMapRendering == true/);
  assert.match(workflow, /\.v11Rc32PersistentLocalMapDiscovery == true/);
  assert.match(workflow, /\.v11Rc33Gen3LocalMapPois == true/);
  assert.match(workflow, /\.v11Rc33PoiEventFlagTracking == true/);
  assert.match(workflow, /\.v11Rc36ConditionalPoiNames == true/);
  assert.match(workflow, /\.v11Rc36PoiZoomDecluttering == true/);
  assert.match(workflow, /\.v11Rc40LiveViewPriority == true/);
  assert.match(workflow, /\.v11Rc40PlayerFollow == true/);
  assert.match(workflow, /\.v11Rc68PartyAnalysis == true/);
  assert.match(workflow, /\.aggregate\.provenAbilityModifiers\.covered == 30/);
  assert.match(workflow, /\.v11Rc69AreaGuide == true/);
  assert.match(workflow, /\.aggregate\.areaNames\.covered == 3973/);
  assert.match(workflow, /\.aggregate\.poiContent\.total == 25003/);
  assert.match(workflow, /\.v11Rc70TrainerProgressTimeline == true/);
  assert.match(workflow, /\.v11Rc71StorageGuideLoadHardening == true/);
  assert.match(workflow, /\.v11Rc72PokedexSpecimens == true/);
  assert.match(workflow, /\.v11Rc76PortableChallengeExpansion == true/);
  assert.match(workflow, /\.v11Stage7CrossFeatureUiConformance == true/);
  assert.match(workflow, /\.aggregate\.currentTotalFields\.covered == 40/);
  assert.match(workflow, /\.aggregate\.observableEventFamilies\.total == 126/);
  assert.match(workflow, /\.aggregate\.baselineApplicableTemplates\.covered == 66/);
  assert.match(workflow, /\.aggregate\.applicableFields\.covered == 148/);
  assert.match(workflow, /\.aggregate\.applicableSources\.total == 84/);
  assert.match(workflow, /\.reference\.descriptionsClassified\.covered == 883/);
  assert.match(workflow, /\.aggregate\.allApplicableTemplates\.covered == 110/);
  assert.match(workflow, /\.aggregate\.allFullyObservableTemplates\.covered == 104/);
  assert.match(workflow, /\.aggregate\.allValidatedTemplates\.covered == 104/);
  assert.match(workflow, /\.v11SaveSynchronizedKnowledgeCheckpoints == true/);
  assert.match(workflow, /\.gen1CompiledEvolutionCoveragePercent == 96\.62/);
  assert.match(workflow, /\.gen1CompiledLearnsetCoveragePercent == 93\.56/);
  assert.match(workflow, /\.gen1CompiledTypeChartCoveragePercent == 95\.79/);
  assert.match(workflow, /\.gen1MoveCatalogCoveragePercent == 90\.24/);
  assert.match(workflow, /\.gen1MoveDetailsCoveragePercent == 90\.42/);
  assert.match(workflow, /\.gen1MachineMoveCoveragePercent == 86\.32/);
  assert.match(workflow, /\.gen1SpriteCoveragePercent == 88\.31/);
  assert.match(workflow, /\.gen1PokedexDescriptionCoveragePercent == 89\.47/);
  assert.match(workflow, /\.gen1Gen3CoverageUniqueRoms == 331/);
  assert.match(workflow, /\.gen1Gen3CoverageTables == 23/);
  assert.match(workflow, /\.gen1Gen3CoverageParserErrors == 0/);
  assert.match(workflow, /release\/RELEASE_NOTES_\$\{RELEASE_TAG#v\}\.md/);
  assert.match(workflow, /test -s "\$release_notes"/);
  assert.match(workflow, /\.v11Rc4HotfixBoundedRomIndex == true/);
  assert.match(workflow, /has\(\"debugApkSha256\"\) \| not/);
});

test("builds only the unsigned release APK before protected signing", () => {
  assert.match(workflow, /:app:assembleRelease/);
  assert.doesNotMatch(workflow, /assembleDebug/);
  assert.doesNotMatch(workflow, /app-debug\.apk/);
  assert.doesNotMatch(readFileSync(join(repositoryRoot, "release", "v1-ready.json"), "utf8"), /debugApkSha256/);
});

test("binds stable release metadata to candidate provenance and an allowlisted source diff", () => {
  const metadataStep = workflow.slice(
    workflow.indexOf("      - name: Derive and validate release identity"),
    workflow.indexOf("      - name: Refuse an existing release"),
  );

  assert.match(metadataStep, /sourceCandidateTag/);
  assert.match(metadataStep, /git rev-parse.*\^\{commit\}/s);
  assert.match(metadataStep, /git rev-parse.*\^\{tree\}/s);
  assert.match(metadataStep, /git diff --name-only/);
  assert.match(metadataStep, /--candidate-promotion/);
  assert.match(metadataStep, /--candidate-source-commit/);
  assert.match(metadataStep, /--candidate-source-tree/);
  assert.match(metadataStep, /releases\/tags\/\$sourceCandidateTag/);
  assert.match(metadataStep, /releases\/assets\/\$candidate_provenance_asset_id/);
  assert.match(metadataStep, /candidate_provenance_api_digest/);
  assert.match(metadataStep, /--candidate-provenance-sha256/);
  assert.match(metadataStep, /--candidate-apk-sha256/);
  assert.match(metadataStep, /--changed-paths/);
});

test("stable validation reuses the candidate comparison range and RCs skip final authorization", () => {
  const evidenceStep = workflow.slice(
    workflow.indexOf("      - name: Validate source-bound compatibility evidence"),
    workflow.indexOf("      - name: Validate published compatibility documentation"),
  );
  const metadataStep = workflow.slice(
    workflow.indexOf("      - name: Derive and validate release identity"),
    workflow.indexOf("      - name: Refuse an existing release"),
  );

  assert.match(evidenceStep, /sourceCandidateTag/);
  assert.match(evidenceStep, /decision_range_end/);
  assert.match(evidenceStep, /--comparison-ref "\$decision_range_base"/);
  assert.match(evidenceStep, /git diff --name-only "\$decision_range_base\.\.\$decision_range_end"/);
  assert.match(metadataStep, /if \[\[ "\$RELEASE_TAG" != \*-rc\.\* \]\]; then/);
  assert.match(metadataStep, /test -s release\/v1-final-authorization\.json/);
});

test("audits exact policy for both protected environments without promotion signing secrets", () => {
  const verifyJob = workflow.slice(
    workflow.indexOf("  verify-and-build:"),
    workflow.indexOf("  sign-and-publish:"),
  );

  assert.match(verifyJob, /environments\/release-promotion/);
  assert.match(verifyJob, /release-promotion\/deployment-branch-policies/);
  assert.match(verifyJob, /release-promotion\/secrets/);
  assert.match(verifyJob, /--promotion-environment/);
  assert.match(verifyJob, /--promotion-environment-policies/);
  assert.match(verifyJob, /--promotion-signing-secret-count/);
  assert.match(verifyJob, /--default-branch/);
});

test("promotion rechecks protected environment governance immediately before publication", () => {
  const publication = promotionWorkflow.indexOf("gh api --method PATCH");
  const policyCheck = promotionWorkflow.indexOf("verify-repository-policy.mjs");

  assert.match(promotionWorkflow, /repos\/\$GITHUB_REPOSITORY\/environments\/release-signing/);
  assert.match(promotionWorkflow, /repos\/\$GITHUB_REPOSITORY\/environments\/release-promotion/);
  assert.match(promotionWorkflow, /release-promotion\/deployment-branch-policies/);
  assert.match(promotionWorkflow, /release-promotion\/secrets/);
  assert.notEqual(policyCheck, -1);
  assert.ok(policyCheck < publication, "current environment policy must pass before draft publication");
});

test("promotion records and rechecks the complete immutable public asset set", () => {
  assert.match(promotionWorkflow, /initial-release-assets\.json/);
  assert.match(promotionWorkflow, /current-release-assets\.json/);
  assert.match(promotionWorkflow, /--release-assets/);
  assert.match(promotionWorkflow, /--assets-directory/);
  assert.match(promotionWorkflow, /validate-candidate-promotion\.mjs[\s\S]*--asset-set-only/);
  assert.doesNotMatch(promotionWorkflow, /initial_apk_asset_id|current_apk_asset_id/);
});

test("privacy-scans every assembled public asset including Stage 7 and closure evidence", () => {
  const signingJob = workflow.slice(workflow.indexOf("  sign-and-publish:"));
  const scanIndex = signingJob.indexOf("validate-public-release-assets.mjs");
  const publishIndex = signingJob.indexOf("gh release create");

  assert.notEqual(scanIndex, -1);
  assert.ok(scanIndex < publishIndex, "public asset privacy validation must precede publication");
  assert.match(signingJob, /--directory "\$ASSETS"/);
  for (const asset of [
    "dualdex-stage-07-corpus-execution.json",
    "dualdex-stage-07-closure.json",
    "dualdex-stage-08-closure.json",
    "canonical-corpus.json",
  ]) {
    assert.match(signingJob, new RegExp(asset.replaceAll(".", "\\.")));
  }
});

test("pins every CI and release action to an immutable commit", () => {
  for (const [name, source] of [
    ["CI", continuousIntegrationWorkflow],
    ["release", workflow],
    ["promotion", promotionWorkflow],
    ["packaged Android", packagedAndroidWorkflow],
  ]) {
    const actionReferences = [...source.matchAll(/uses:\s*([^\s#]+)/g)].map(
      (match) => match[1],
    );
    assert.ok(actionReferences.length > 0);
    for (const reference of actionReferences) {
      if (reference.startsWith("./")) continue;
      assert.match(reference, /@[a-f0-9]{40}$/, `${name} floating action reference: ${reference}`);
    }
  }
});
