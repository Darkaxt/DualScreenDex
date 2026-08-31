import assert from 'node:assert/strict';
import test from 'node:test';

import {
  assertEvidenceBudget,
  assertQaRuntimeIdentity,
  assertRuntimeAuthority,
  assertScrollAtEnd,
  assertSelectorMatchBudget,
  assertThorGeometry,
  assertTouchTargetBounds,
  assertTouchTargetLayout,
  assertVisibleTextAudit,
  effectiveContrastRatio,
  inspectPrivacySummary,
  isVisualStateStable,
  parseCommandLine,
  selectDebugLoopbackTarget,
  validateScenario,
} from './thor-webview-cdp.mjs';

const authority = {
  catalogReady: true,
  gameAccessReady: true,
  catalogHash: 'different-projection-hash',
  settings: { knowledgeMode: 'DISCOVERED' },
  retroArch: {
    resolution: 'ACTIVE',
    activeSource: 'Modern Emerald (v3.5).gba',
    contentCrc32: '8C7DBECA',
    contentSha256: '21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895',
    sessionEpoch: 7,
    indexedRoms: 1,
  },
};

const expectedAuthority = {
  source: 'Modern Emerald (v3.5).gba',
  sha256: '21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895',
  crc32: '8C7DBECA',
  knowledgeMode: 'DISCOVERED',
  resolution: 'ACTIVE',
  indexedRoms: 1,
};

test('requires the CDP URL, output directory, and scenario inputs', () => {
  assert.deepEqual(parseCommandLine([
    '--cdp-url', 'http://127.0.0.1:9222',
    '--output', 'artifacts/thor-stage-1',
    '--scenario', 'tools/qa/scenarios/thor-ui-stage-1.json',
  ]), {
    cdpUrl: 'http://127.0.0.1:9222',
    output: 'artifacts/thor-stage-1',
    scenario: 'tools/qa/scenarios/thor-ui-stage-1.json',
  });
  assert.throws(() => parseCommandLine(['--cdp-url', 'http://127.0.0.1:9222']), /--output is required/);
});

test('accepts only the dedicated debug package and bounded capture names', () => {
  assert.doesNotThrow(() => validateScenario({
    name: 'stage-1',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [
      {
        name: 'trainer-card',
        steps: [{
          kind: 'touch',
          selector: '.trainer-destination-action[aria-label="Card"]',
          waitFor: '.trainer-card-shell',
        }],
        waitFor: '.trainer-screen',
        measurements: ['.trainer-screen'],
      },
      {
        name: 'settings-danger',
        steps: [{
          kind: 'swipe',
          selector: '.settings-content',
          direction: 'up',
          repeat: 3,
          waitFor: '.danger-action',
        }],
        waitFor: '.danger-action',
        measurements: ['.danger-action', '.settings-content'],
        textAudit: true,
        scrollAtEnd: ['.settings-content'],
      },
      {
        name: 'keyboard-path',
        steps: [{
          kind: 'key',
          key: 'Tab',
          repeat: 2,
          shift: false,
          waitFor: '[role="dialog"]',
        }],
        waitFor: '[role="dialog"]',
        measurements: ['[role="dialog"]'],
        active: [{ selector: '[role="dialog"] button', focused: true }],
      },
      {
        name: 'specimen-expansion',
        steps: [{
          kind: 'mock-specimen-expansion',
          waitFor: '.specimens-screen',
        }],
        waitFor: '.specimens-screen',
        measurements: ['.specimens-screen'],
      },
      {
        name: 'error-feedback',
        steps: [{
          kind: 'mock-action-failure',
          waitFor: '.pokedex-screen',
        }],
        waitFor: '.pokedex-screen',
        measurements: ['.pokedex-screen'],
      },
    ],
  }));
  assert.throws(() => validateScenario({
    name: 'production',
    debugPackage: 'com.darkaxt.dualdex',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [],
  }), /debug QA package/);
  assert.throws(() => validateScenario({
    name: 'unsafe',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [{ name: '../outside', waitFor: '.screen', measurements: [] }],
  }), /capture name/);
  assert.throws(() => validateScenario({
    name: 'missing-postcondition',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [{
      name: 'trainer-card',
      steps: [{ kind: 'action', value: { type: 'SCREEN', screen: 'TRAINER' } }],
      waitFor: '.trainer-screen',
      measurements: ['.trainer-screen'],
    }],
  }), /postcondition/);
  assert.throws(() => validateScenario({
    name: 'synthetic-click',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [{
      name: 'trainer-card',
      steps: [{ kind: 'click', selector: '.trainer-destination-action' }],
      waitFor: '.trainer-screen',
      measurements: ['.trainer-screen'],
    }],
  }), /mock-specimen-expansion/);
  assert.throws(() => validateScenario({
    name: 'invalid-text-audit',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [{
      name: 'capture',
      waitFor: '.screen',
      measurements: ['.screen'],
      textAudit: 'yes',
    }],
  }), /textAudit/);
  assert.throws(() => validateScenario({
    name: 'unmeasured-scroll-end',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: [{
      name: 'capture',
      waitFor: '.screen',
      measurements: ['.screen'],
      scrollAtEnd: ['.settings-content'],
    }],
  }), /scrollAtEnd/);
});

test('asserts packaged visible-text floors and scroll-end reachability', () => {
  const text = { count: 4, minimumPx: 11.2, averagePx: 12.5, smallest: ['11.2px .eyebrow'] };
  assert.deepEqual(assertVisibleTextAudit(text), text);
  assert.throws(
    () => assertVisibleTextAudit({ ...text, minimumPx: 11.18 }),
    /minimum visible text/,
  );
  assert.throws(
    () => assertVisibleTextAudit({ ...text, averagePx: 11.99 }),
    /average visible text/,
  );

  const atEnd = { top: 119, maximumTop: 120 };
  assert.deepEqual(assertScrollAtEnd(atEnd, '.settings-content'), atEnd);
  assert.throws(
    () => assertScrollAtEnd({ top: 80, maximumTop: 120 }, '.settings-content'),
    /did not reach its end/,
  );
});

test('selects one loopback DualDex page and rejects remote or ambiguous targets', () => {
  const target = selectDebugLoopbackTarget([
    { type: 'other', url: 'http://127.0.0.1:36900/', title: 'Other' },
    { type: 'page', url: 'http://127.0.0.1:36900/', title: 'DualDex' },
  ]);
  assert.equal(target.title, 'DualDex');
  assert.throws(() => selectDebugLoopbackTarget([
    { type: 'page', url: 'https://example.com/', title: 'DualDex' },
  ]), /loopback/);
  assert.throws(() => selectDebugLoopbackTarget([
    { type: 'page', url: 'http://127.0.0.1:36900/', title: 'DualDex' },
    { type: 'page', url: 'http://localhost:36900/', title: 'DualDex' },
  ]), /exactly one/);
});

test('requires runtime-attested debug package and sanitized scenario', () => {
  const expected = {
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
  };
  const identity = {
    applicationId: 'com.darkaxt.dualdex.debug',
    transport: 'SANITIZED_RAW_MEMORY',
    scenarioId: 'modern-normal',
  };

  assert.deepEqual(assertQaRuntimeIdentity(identity, expected), identity);
  assert.throws(
    () => assertQaRuntimeIdentity({ ...identity, applicationId: 'com.darkaxt.dualdex' }, expected),
    /applicationId/,
  );
  assert.throws(
    () => assertQaRuntimeIdentity({ ...identity, transport: 'UDP' }, expected),
    /transport/,
  );
  assert.throws(
    () => assertQaRuntimeIdentity({ ...identity, scenarioId: 'other' }, expected),
    /scenarioId/,
  );
});

test('rejects wrong ROM, knowledge, readiness, resolution, or session authority', () => {
  assert.equal(assertRuntimeAuthority(authority, expectedAuthority), 7);
  assert.equal(assertRuntimeAuthority(authority, expectedAuthority, 7), 7);
  assert.throws(() => assertRuntimeAuthority({
    ...authority,
    retroArch: { ...authority.retroArch, contentSha256: 'wrong' },
  }, expectedAuthority), /sha256/);
  assert.throws(() => assertRuntimeAuthority({ ...authority, settings: { knowledgeMode: 'ORGANIC' } }, expectedAuthority), /knowledgeMode/);
  assert.throws(() => assertRuntimeAuthority({ ...authority, catalogReady: false }, expectedAuthority), /catalogReady/);
  assert.throws(() => assertRuntimeAuthority({ ...authority, retroArch: { ...authority.retroArch, resolution: 'FAILED' } }, expectedAuthority), /resolution/);
  assert.throws(() => assertRuntimeAuthority({
    ...authority,
    retroArch: { ...authority.retroArch, sessionEpoch: 8 },
  }, expectedAuthority, 7), /sessionEpoch/);
});

test('composites transparent semantic backgrounds before checking contrast', () => {
  assert.equal(effectiveContrastRatio('rgb(255, 255, 255)', [
    'rgb(0, 0, 0)',
    'rgba(255, 255, 255, 0)',
  ]), 21);
  assert.equal(effectiveContrastRatio('color(srgb 1 1 1)', [
    'rgb(0, 0, 0)',
    'color(srgb 1 1 1 / 0.5)',
  ]), 3.977);
  assert.throws(() => effectiveContrastRatio('not-a-color', ['rgb(0, 0, 0)']), /color/);
});

test('bounds aggregate capture and measurement evidence', () => {
  assert.doesNotThrow(() => assertEvidenceBudget({
    captureReportBytes: 1024,
    finalReportBytes: 2048,
    screenshotBytes: 4096,
    totalScreenshotBytes: 8192,
    totalMeasuredElements: 8,
  }));
  assert.throws(() => assertEvidenceBudget({ totalMeasuredElements: 1025 }), /measured element/);
  assert.throws(() => assertEvidenceBudget({ captureReportBytes: 1024 * 1024 + 1 }), /capture report/);
  assert.throws(() => assertEvidenceBudget({ finalReportBytes: 4 * 1024 * 1024 + 1 }), /final report/);

  const capture = index => ({
    name: `capture-${index}`,
    waitFor: '.screen',
    measurements: Array.from({ length: 17 }, (_, measurement) => `.item-${index}-${measurement}`),
  });
  assert.throws(() => validateScenario({
    name: 'too-many-captures',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: Array.from({ length: 17 }, (_, index) => capture(index)),
  }), /at most 16/);
  assert.throws(() => validateScenario({
    name: 'too-many-measurements',
    debugPackage: 'com.darkaxt.dualdex.debug',
    qaScenarioId: 'modern-normal',
    authority: expectedAuthority,
    captures: Array.from({ length: 16 }, (_, index) => capture(index)),
  }), /total measurement/);
});

test('rejects selectors whose actual match count exceeds the evidence cap', () => {
  assert.equal(assertSelectorMatchBudget(64, '.entry'), 64);
  assert.throws(() => assertSelectorMatchBudget(65, '.entry'), /matches 65 elements/);
});

test('requires capture geometry to settle after ancestor animation', () => {
  const settled = {
    bounds: { x: 0, y: 62, width: 538.103, height: 383.313 },
    runningAnimations: 0,
  };
  assert.equal(isVisualStateStable(settled, settled), true);
  assert.equal(isVisualStateStable(
    { ...settled, bounds: { ...settled.bounds, y: 67 } },
    settled,
  ), false);
  assert.equal(isVisualStateStable(settled, { ...settled, runningAnimations: 1 }), false);
  assert.equal(isVisualStateStable(null, settled), false);
});

test('rejects undersized, clipped, or overlapping captured actions', () => {
  const viewport = { x: 0, y: 0, width: 538.103, height: 445.312 };
  const targets = [
    { selector: '.card', index: 0, bounds: { x: 442.107, y: 0, width: 47.995, height: 59.397 } },
    { selector: '.progress', index: 0, bounds: { x: 490.102, y: 0, width: 47.995, height: 59.397 } },
  ];
  assert.deepEqual(assertTouchTargetLayout(targets, viewport), targets);
  assert.throws(
    () => assertTouchTargetLayout([{ ...targets[0], bounds: { ...targets[0].bounds, height: 43.98 } }], viewport),
    /smaller than 44x44/,
  );
  assert.throws(
    () => assertTouchTargetLayout([targets[0], { ...targets[1], bounds: { ...targets[1].bounds, x: 489 } }], viewport),
    /overlap/,
  );
});

test('uses the fractional visual viewport for touch containment', () => {
  const bounds = { x: 490.102, y: 0, width: 47.995, height: 59.397 };
  const viewport = { x: 0, y: 0, width: 538.103, height: 445.312 };
  assert.deepEqual(assertTouchTargetBounds(bounds, viewport), bounds);
  assert.doesNotThrow(() => assertTouchTargetBounds(
    { x: 492.107, y: 0, width: 45.996, height: 59.397 },
    { ...viewport, width: 538.1029663085938 },
  ));
  assert.throws(() => assertTouchTargetBounds(bounds, { ...viewport, width: 538 }), /leaves the viewport/);
  assert.doesNotThrow(() => assertTouchTargetBounds({ ...bounds, width: 43.997 }, viewport));
  assert.throws(() => assertTouchTargetBounds({ ...bounds, width: 43.98 }, viewport), /smaller than 44x44/);
});

test('accepts only exact Thor WebView geometry within fractional viewport tolerance', () => {
  const geometry = {
    innerWidth: 538,
    innerHeight: 445,
    devicePixelRatio: 2.3062500953674316,
    visualViewportWidth: 538.1029663085938,
    visualViewportHeight: 445.3116455078125,
  };
  assert.deepEqual(assertThorGeometry(geometry), geometry);
  assert.throws(() => assertThorGeometry({ ...geometry, innerWidth: 539 }), /innerWidth/);
  assert.throws(() => assertThorGeometry({ ...geometry, devicePixelRatio: 2 }), /devicePixelRatio/);
});

test('privacy checks report only categories and selectors, never captured values', () => {
  assert.deepEqual(inspectPrivacySummary({
    passwordInputs: [],
    populatedTextInputs: [],
    pathLikeText: [],
    emailLikeText: [],
  }), { safe: true, categories: [] });

  const result = inspectPrivacySummary({
    passwordInputs: ['input#secret'],
    populatedTextInputs: ['input#trainer-name'],
    pathLikeText: ['C:/Users/example/private/file.sav'],
    emailLikeText: ['person@example.com'],
  });
  assert.equal(result.safe, false);
  assert.deepEqual(result.categories, ['email-like-text', 'password-input', 'path-like-text', 'populated-text-input']);
  assert.doesNotMatch(JSON.stringify(result), /private|person@example|trainer-name|secret/);
});
