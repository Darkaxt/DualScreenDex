#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const DEBUG_PACKAGE = 'com.darkaxt.dualdex.debug';
const SANITIZED_QA_TRANSPORT = 'SANITIZED_RAW_MEMORY';
const MAX_CAPTURES = 16;
const MAX_TOTAL_MEASUREMENTS = 256;
const MAX_SELECTOR_MATCHES = 64;
const MAX_SCREENSHOT_BYTES = 4 * 1024 * 1024;
const MAX_TOTAL_SCREENSHOT_BYTES = 16 * 1024 * 1024;
const MAX_TOTAL_MEASURED_ELEMENTS = 1024;
const MAX_CAPTURE_REPORT_BYTES = 1024 * 1024;
const MAX_FINAL_REPORT_BYTES = 4 * 1024 * 1024;
const VISUAL_STABILITY_TOLERANCE = 0.01;
const TOUCH_TARGET_TOLERANCE = 0.01;
const EVIDENCE_ROUNDING_TOLERANCE = 0.001;
const THOR_GEOMETRY = Object.freeze({
  innerWidth: 538,
  innerHeight: 445,
  devicePixelRatio: 2.3062500953674316,
});
const SAFE_ACTIONS = new Set([
  'BACK',
  'OPEN_AREA_POKEDEX',
  'OPEN_SPECIES',
  'PROGRESS_SECTION',
  'SCREEN',
  'SETTINGS',
  'TAB',
  'TRAINER_DESTINATION',
]);
const ACTIVE_ATTRIBUTES = new Set(['aria-current', 'aria-pressed', 'aria-selected', 'data-active']);
const SAFE_KEYS = new Map([
  ['Tab', { code: 'Tab', virtualKeyCode: 9 }],
  ['Enter', { code: 'Enter', virtualKeyCode: 13 }],
  ['Escape', { code: 'Escape', virtualKeyCode: 27 }],
  [' ', { code: 'Space', virtualKeyCode: 32 }],
  ['End', { code: 'End', virtualKeyCode: 35 }],
  ['Home', { code: 'Home', virtualKeyCode: 36 }],
  ['ArrowLeft', { code: 'ArrowLeft', virtualKeyCode: 37 }],
  ['ArrowUp', { code: 'ArrowUp', virtualKeyCode: 38 }],
  ['ArrowRight', { code: 'ArrowRight', virtualKeyCode: 39 }],
  ['ArrowDown', { code: 'ArrowDown', virtualKeyCode: 40 }],
]);
const SAFE_NAME = /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/;
const SHA256 = /^[0-9a-f]{64}$/i;
const CRC32 = /^[0-9a-f]{8}$/i;

export function parseCommandLine(args) {
  const values = {};
  const known = new Map([
    ['--cdp-url', 'cdpUrl'],
    ['--output', 'output'],
    ['--scenario', 'scenario'],
  ]);
  for (let index = 0; index < args.length; index += 2) {
    const flag = args[index];
    const key = known.get(flag);
    if (!key) throw new TypeError(`unknown argument: ${flag ?? '<missing>'}`);
    const value = args[index + 1];
    if (!value || value.startsWith('--')) throw new TypeError(`${flag} requires a value`);
    if (values[key]) throw new TypeError(`${flag} may be provided only once`);
    values[key] = value;
  }
  if (!values.cdpUrl) throw new TypeError('--cdp-url is required');
  if (!values.output) throw new TypeError('--output is required');
  if (!values.scenario) throw new TypeError('--scenario is required');
  assertLoopbackUrl(values.cdpUrl, 'CDP URL', new Set(['http:', 'https:']));
  return values;
}

export function assertEvidenceBudget(usage) {
  requireRecord(usage, 'evidence usage');
  const limits = [
    ['screenshotBytes', MAX_SCREENSHOT_BYTES, 'screenshot'],
    ['totalScreenshotBytes', MAX_TOTAL_SCREENSHOT_BYTES, 'aggregate screenshots'],
    ['totalMeasuredElements', MAX_TOTAL_MEASURED_ELEMENTS, 'measured element count'],
    ['captureReportBytes', MAX_CAPTURE_REPORT_BYTES, 'capture report'],
    ['finalReportBytes', MAX_FINAL_REPORT_BYTES, 'final report'],
  ];
  for (const [key, limit, label] of limits) {
    const value = usage[key] ?? 0;
    if (!Number.isSafeInteger(value) || value < 0) throw new TypeError(`${label} usage must be a non-negative safe integer`);
    if (value > limit) throw new Error(`${label} exceeds the ${limit}-byte budget`);
  }
  return usage;
}

export function assertSelectorMatchBudget(matchCount, selector) {
  if (!Number.isSafeInteger(matchCount) || matchCount < 0) throw new TypeError('selector match count must be a non-negative safe integer');
  if (matchCount > MAX_SELECTOR_MATCHES) {
    throw new Error(`measurement selector ${selector} matches ${matchCount} elements; maximum is ${MAX_SELECTOR_MATCHES}`);
  }
  return matchCount;
}

export function validateScenario(scenario) {
  requireRecord(scenario, 'scenario');
  requireSafeName(scenario.name, 'scenario name');
  if (scenario.debugPackage !== DEBUG_PACKAGE) {
    throw new TypeError(`scenario must target the debug QA package ${DEBUG_PACKAGE}`);
  }
  requireSafeName(scenario.qaScenarioId, 'QA scenario id');
  validateAuthority(scenario.authority);
  if (!Array.isArray(scenario.captures) || scenario.captures.length === 0 || scenario.captures.length > MAX_CAPTURES) {
    throw new TypeError(`scenario captures must contain between 1 and at most ${MAX_CAPTURES} entries`);
  }
  const names = new Set();
  let totalMeasurements = 0;
  for (const capture of scenario.captures) {
    requireRecord(capture, 'capture');
    requireSafeName(capture.name, 'capture name');
    if (names.has(capture.name)) throw new TypeError(`duplicate capture name: ${capture.name}`);
    names.add(capture.name);
    requireSelector(capture.waitFor, `${capture.name}.waitFor`);
    if (!Array.isArray(capture.measurements) || capture.measurements.length === 0 || capture.measurements.length > 64) {
      throw new TypeError(`${capture.name}.measurements must contain between 1 and 64 selectors`);
    }
    totalMeasurements += capture.measurements.length;
    capture.measurements.forEach((selector, index) => requireSelector(selector, `${capture.name}.measurements[${index}]`));
    if (capture.touchTargets !== undefined) {
      if (!Array.isArray(capture.touchTargets) || capture.touchTargets.length === 0 || capture.touchTargets.length > 32) {
        throw new TypeError(`${capture.name}.touchTargets must contain between 1 and 32 selectors`);
      }
      capture.touchTargets.forEach((selector, index) => {
        requireSelector(selector, `${capture.name}.touchTargets[${index}]`);
        if (!capture.measurements.includes(selector)) {
          throw new TypeError(`${capture.name}.touchTargets[${index}] must also be measured`);
        }
      });
    }
    validateSteps(capture.steps ?? [], capture.name);
    validateActiveAssertions(capture.active ?? [], capture.name);
    validateContrastAssertions(capture.contrasts ?? [], capture.name);
    if (capture.allowHorizontalOverflow !== undefined) {
      if (!Array.isArray(capture.allowHorizontalOverflow)) throw new TypeError(`${capture.name}.allowHorizontalOverflow must be an array`);
      capture.allowHorizontalOverflow.forEach((selector, index) => requireSelector(selector, `${capture.name}.allowHorizontalOverflow[${index}]`));
    }
  }
  if (totalMeasurements > MAX_TOTAL_MEASUREMENTS) {
    throw new TypeError(`scenario total measurement selectors must not exceed ${MAX_TOTAL_MEASUREMENTS}`);
  }
  return scenario;
}

export function selectDebugLoopbackTarget(targets) {
  if (!Array.isArray(targets)) throw new TypeError('CDP target list must be an array');
  const pages = targets.filter(target => {
    if (target?.type !== 'page' || target.title !== 'DualDex' || typeof target.url !== 'string') return false;
    try {
      assertLoopbackUrl(target.url, 'target URL', new Set(['http:', 'https:']));
      return true;
    } catch {
      return false;
    }
  });
  if (pages.length === 0) throw new Error('expected one loopback DualDex page target');
  if (pages.length !== 1) throw new Error(`expected exactly one loopback DualDex page target, received ${pages.length}`);
  return pages[0];
}

export function assertQaRuntimeIdentity(identity, scenario) {
  requireRecord(identity, 'QA runtime identity');
  requireRecord(scenario, 'scenario');
  assertEqualAuthority('applicationId', identity.applicationId, scenario.debugPackage, String);
  assertEqualAuthority('transport', identity.transport, SANITIZED_QA_TRANSPORT, String);
  assertEqualAuthority('scenarioId', identity.scenarioId, scenario.qaScenarioId, String);
  return identity;
}

export function assertRuntimeAuthority(state, expected, expectedSessionEpoch = null) {
  requireRecord(state, 'runtime state');
  validateAuthority(expected);
  if (state.catalogReady !== true) throw new Error('runtime authority catalogReady must be true');
  if (state.gameAccessReady !== true) throw new Error('runtime authority gameAccessReady must be true');
  requireRecord(state.settings, 'runtime settings');
  requireRecord(state.retroArch, 'runtime RetroArch state');
  assertEqualAuthority('sha256', state.retroArch.contentSha256, expected.sha256, value => String(value).toLowerCase());
  assertEqualAuthority('source', state.retroArch.activeSource, expected.source, String);
  assertEqualAuthority('crc32', state.retroArch.contentCrc32, expected.crc32, value => String(value).toUpperCase());
  assertEqualAuthority('knowledgeMode', state.settings.knowledgeMode, expected.knowledgeMode, String);
  assertEqualAuthority('resolution', state.retroArch.resolution, expected.resolution, String);
  assertEqualAuthority('indexedRoms', state.retroArch.indexedRoms, expected.indexedRoms, Number);
  const sessionEpoch = state.retroArch.sessionEpoch;
  if (!Number.isSafeInteger(sessionEpoch) || sessionEpoch < 0) {
    throw new Error('runtime authority sessionEpoch must be a non-negative safe integer');
  }
  if (expectedSessionEpoch !== null) {
    assertEqualAuthority('sessionEpoch', sessionEpoch, expectedSessionEpoch, Number);
  }
  return sessionEpoch;
}

export function assertThorGeometry(geometry) {
  requireRecord(geometry, 'WebView geometry');
  assertGeometryValue('innerWidth', geometry.innerWidth, THOR_GEOMETRY.innerWidth, 0);
  assertGeometryValue('innerHeight', geometry.innerHeight, THOR_GEOMETRY.innerHeight, 0);
  assertGeometryValue('devicePixelRatio', geometry.devicePixelRatio, THOR_GEOMETRY.devicePixelRatio, 1e-6);
  assertGeometryValue('visualViewportWidth', geometry.visualViewportWidth, THOR_GEOMETRY.innerWidth, 0.5);
  assertGeometryValue('visualViewportHeight', geometry.visualViewportHeight, THOR_GEOMETRY.innerHeight, 0.5);
  return geometry;
}

export function assertTouchTargetBounds(bounds, viewport) {
  requireRecord(bounds, 'touch bounds');
  requireRecord(viewport, 'touch viewport');
  for (const [label, value] of Object.entries({ ...bounds, ...Object.fromEntries(Object.entries(viewport).map(([key, entry]) => [`viewport.${key}`, entry])) })) {
    if (typeof value !== 'number' || !Number.isFinite(value)) throw new TypeError(`${label} must be finite`);
  }
  if (bounds.width + TOUCH_TARGET_TOLERANCE < 44 || bounds.height + TOUCH_TARGET_TOLERANCE < 44) throw new Error('Touch target is smaller than 44x44');
  if (bounds.x + EVIDENCE_ROUNDING_TOLERANCE < viewport.x
    || bounds.y + EVIDENCE_ROUNDING_TOLERANCE < viewport.y
    || bounds.x + bounds.width > viewport.x + viewport.width + EVIDENCE_ROUNDING_TOLERANCE
    || bounds.y + bounds.height > viewport.y + viewport.height + EVIDENCE_ROUNDING_TOLERANCE) {
    throw new Error('Touch target leaves the viewport');
  }
  return bounds;
}

export function assertTouchTargetLayout(targets, viewport) {
  if (!Array.isArray(targets) || targets.length === 0) throw new TypeError('touch target layout must contain at least one target');
  for (const target of targets) {
    requireRecord(target, 'touch target');
    if (typeof target.selector !== 'string' || !Number.isSafeInteger(target.index) || target.index < 0) {
      throw new TypeError('touch target identity is invalid');
    }
    try {
      assertTouchTargetBounds(target.bounds, viewport);
    } catch (error) {
      throw new Error(`${target.selector}[${target.index}] ${JSON.stringify(target.bounds)}: ${error.message}`);
    }
  }
  for (let firstIndex = 0; firstIndex < targets.length; firstIndex += 1) {
    const first = targets[firstIndex];
    for (let secondIndex = firstIndex + 1; secondIndex < targets.length; secondIndex += 1) {
      const second = targets[secondIndex];
      const overlapWidth = Math.min(first.bounds.x + first.bounds.width, second.bounds.x + second.bounds.width)
        - Math.max(first.bounds.x, second.bounds.x);
      const overlapHeight = Math.min(first.bounds.y + first.bounds.height, second.bounds.y + second.bounds.height)
        - Math.max(first.bounds.y, second.bounds.y);
      if (overlapWidth > VISUAL_STABILITY_TOLERANCE && overlapHeight > VISUAL_STABILITY_TOLERANCE) {
        throw new Error(`Touch targets overlap: ${first.selector}[${first.index}] and ${second.selector}[${second.index}]`);
      }
    }
  }
  return targets;
}

export function isVisualStateStable(previous, current) {
  if (previous === null || current === null) return false;
  requireRecord(previous, 'previous visual state');
  requireRecord(current, 'current visual state');
  requireRecord(previous.bounds, 'previous visual bounds');
  requireRecord(current.bounds, 'current visual bounds');
  if (!Number.isSafeInteger(current.runningAnimations) || current.runningAnimations < 0) {
    throw new TypeError('running animation count must be a non-negative safe integer');
  }
  if (current.runningAnimations !== 0) return false;
  return ['x', 'y', 'width', 'height'].every(key => {
    const before = previous.bounds[key];
    const after = current.bounds[key];
    if (!Number.isFinite(before) || !Number.isFinite(after)) throw new TypeError(`visual bound ${key} must be finite`);
    return Math.abs(before - after) <= VISUAL_STABILITY_TOLERANCE;
  });
}

export function effectiveContrastRatio(foreground, backgroundLayers) {
  const background = resolveBackgroundColor(backgroundLayers);
  const effectiveForeground = compositeColor(parseCssColor(foreground), background);
  const first = relativeLuminance(effectiveForeground);
  const second = relativeLuminance(background);
  return Math.round(((Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)) * 1000) / 1000;
}

export function inspectPrivacySummary(scan) {
  requireRecord(scan, 'privacy scan');
  const categories = [];
  if (hasEntries(scan.emailLikeText)) categories.push('email-like-text');
  if (hasEntries(scan.passwordInputs)) categories.push('password-input');
  if (hasEntries(scan.pathLikeText)) categories.push('path-like-text');
  if (hasEntries(scan.populatedTextInputs)) categories.push('populated-text-input');
  return { safe: categories.length === 0, categories };
}

async function run() {
  const options = parseCommandLine(process.argv.slice(2));
  const scenarioPath = resolve(options.scenario);
  const scenario = validateScenario(JSON.parse(await readFile(scenarioPath, 'utf8')));
  const targets = await fetchTargets(options.cdpUrl);
  const target = selectDebugLoopbackTarget(targets);
  if (!target.webSocketDebuggerUrl) throw new Error('DualDex target does not expose a WebSocket debugger URL');
  assertLoopbackUrl(target.webSocketDebuggerUrl, 'debugger WebSocket URL', new Set(['ws:', 'wss:']));

  const session = await CdpSession.connect(target.webSocketDebuggerUrl);
  try {
    await session.send('Runtime.enable');
    await session.send('Page.enable');
    const identity = await evaluate(session, async () => ({
      title: document.title,
      origin: location.origin,
      url: location.href,
    }));
    if (identity.title !== 'DualDex') throw new Error(`unexpected page title: ${identity.title}`);
    assertLoopbackUrl(identity.url, 'attached page URL', new Set(['http:', 'https:']));

    const geometry = assertThorGeometry(await readGeometry(session));
    const qaRuntimeIdentity = assertQaRuntimeIdentity(await readQaRuntimeIdentity(session), scenario);
    const initialState = await readRuntimeState(session);
    const sessionEpoch = assertRuntimeAuthority(initialState, scenario.authority);
    const outputDirectory = await prepareEvidenceOutput(options.output);

    const captures = [];
    let totalScreenshotBytes = 0;
    let totalMeasuredElements = 0;
    for (const capture of scenario.captures) {
      const steps = await runSteps(session, capture.steps ?? [], identity.origin);
      await waitForCapture(session, capture);
      const captureGeometry = assertThorGeometry(await readGeometry(session));
      assertQaRuntimeIdentity(await readQaRuntimeIdentity(session), scenario);
      assertRuntimeAuthority(await readRuntimeState(session), scenario.authority, sessionEpoch);

      const privacy = inspectPrivacySummary(await scanPrivacy(session));
      if (!privacy.safe) throw new Error(`${capture.name} failed privacy checks: ${privacy.categories.join(', ')}`);

      const measurements = await measureSelectors(session, capture.measurements);
      totalMeasuredElements += measurements.reduce(
        (count, measurement) => count + measurement.elements.length,
        0,
      );
      assertEvidenceBudget({ totalMeasuredElements });
      assertMeasuredContainment(capture, measurements);
      const touchTargets = assertMeasuredTouchTargets(capture, measurements, captureGeometry);
      const active = await assertActiveState(session, capture.active ?? []);
      const contrasts = await assertContrasts(session, capture.contrasts ?? []);
      const screenshot = await session.send('Page.captureScreenshot', {
        format: 'png',
        fromSurface: true,
        captureBeyondViewport: false,
      });
      if (!screenshot?.data) throw new Error(`${capture.name} did not return screenshot data`);
      const screenshotBytes = Buffer.from(screenshot.data, 'base64');
      totalScreenshotBytes += screenshotBytes.length;
      assertEvidenceBudget({
        screenshotBytes: screenshotBytes.length,
        totalScreenshotBytes,
        totalMeasuredElements,
      });
      const evidence = {
        name: capture.name,
        waitFor: capture.waitFor,
        privacy,
        steps,
        measurements,
        touchTargets,
        active,
        contrasts,
        screenshotBytes: screenshotBytes.length,
      };
      await writeCaptureEvidence(outputDirectory, evidence, screenshotBytes);
      captures.push(evidence);
    }

    await writeFinalEvidence(outputDirectory, {
      scenario: scenario.name,
      debugPackage: scenario.debugPackage,
      qaRuntimeIdentity,
      authority: { ...scenario.authority, sessionEpoch },
      target: { title: target.title, origin: identity.origin },
      geometry,
      captures,
      totalScreenshotBytes,
    });
  } finally {
    session.close();
  }
}

function validateAuthority(authority) {
  requireRecord(authority, 'scenario authority');
  if (typeof authority.source !== 'string' || authority.source.length === 0 || authority.source.length > 160) throw new TypeError('authority source is required');
  if (!SHA256.test(authority.sha256)) throw new TypeError('authority sha256 must contain 64 hexadecimal characters');
  if (!CRC32.test(authority.crc32)) throw new TypeError('authority crc32 must contain 8 hexadecimal characters');
  if (!['DISCOVERED', 'ORGANIC', 'HIDDEN'].includes(authority.knowledgeMode)) throw new TypeError('authority knowledgeMode is invalid');
  if (authority.resolution !== 'ACTIVE') throw new TypeError('authority resolution must be ACTIVE');
  if (!Number.isInteger(authority.indexedRoms) || authority.indexedRoms < 1) throw new TypeError('authority indexedRoms must be a positive integer');
}

function validateSteps(steps, captureName) {
  if (!Array.isArray(steps) || steps.length > 16) throw new TypeError(`${captureName}.steps must contain at most 16 entries`);
  for (const [index, step] of steps.entries()) {
    const label = `${captureName}.steps[${index}]`;
    requireRecord(step, label);
    if (step.kind === 'action') {
      requireRecord(step.value, `${label}.value`);
      if (!SAFE_ACTIONS.has(step.value.type)) throw new TypeError(`${captureName} uses an unsafe action type`);
      for (const value of Object.values(step.value)) {
        if (!['string', 'number', 'boolean'].includes(typeof value) && value !== null) throw new TypeError(`${captureName} action values must be JSON scalars`);
      }
    } else if (step.kind === 'touch') {
      requireSelector(step.selector, `${label}.selector`);
    } else if (step.kind === 'swipe') {
      requireSelector(step.selector, `${label}.selector`);
      if (!['up', 'down'].includes(step.direction)) throw new TypeError(`${label}.direction must be up or down`);
      if (!Number.isInteger(step.repeat) || step.repeat < 1 || step.repeat > 8) throw new TypeError(`${label}.repeat must be between 1 and 8`);
    } else if (step.kind === 'key') {
      if (!SAFE_KEYS.has(step.key)) throw new TypeError(`${label}.key is not allowed`);
      if (step.repeat !== undefined && (!Number.isInteger(step.repeat) || step.repeat < 1 || step.repeat > 16)) {
        throw new TypeError(`${label}.repeat must be between 1 and 16`);
      }
      if (step.shift !== undefined && typeof step.shift !== 'boolean') throw new TypeError(`${label}.shift must be boolean`);
    } else {
      throw new TypeError(`${label}.kind must be action, touch, swipe, or key`);
    }
    requireSelector(step.waitFor, `${label}.postcondition waitFor`);
  }
}

function validateActiveAssertions(assertions, captureName) {
  if (!Array.isArray(assertions) || assertions.length > 32) throw new TypeError(`${captureName}.active must contain at most 32 entries`);
  for (const [index, assertion] of assertions.entries()) {
    requireRecord(assertion, `${captureName}.active[${index}]`);
    requireSelector(assertion.selector, `${captureName}.active[${index}].selector`);
    const hasAttribute = assertion.attribute !== undefined || assertion.equals !== undefined;
    const hasText = assertion.textIncludes !== undefined;
    const hasFocus = assertion.focused !== undefined;
    if (!hasAttribute && !hasText && !hasFocus) throw new TypeError(`${captureName}.active[${index}] must assert an attribute, text, or focus`);
    if (hasAttribute) {
      if (!ACTIVE_ATTRIBUTES.has(assertion.attribute) || typeof assertion.equals !== 'string') throw new TypeError(`${captureName}.active[${index}] has an invalid active attribute assertion`);
    }
    if (hasText && (typeof assertion.textIncludes !== 'string' || assertion.textIncludes.length === 0 || assertion.textIncludes.length > 120)) {
      throw new TypeError(`${captureName}.active[${index}].textIncludes is invalid`);
    }
    if (hasFocus && typeof assertion.focused !== 'boolean') throw new TypeError(`${captureName}.active[${index}].focused must be boolean`);
  }
}

function validateContrastAssertions(assertions, captureName) {
  if (!Array.isArray(assertions) || assertions.length > 32) throw new TypeError(`${captureName}.contrasts must contain at most 32 entries`);
  for (const [index, assertion] of assertions.entries()) {
    requireRecord(assertion, `${captureName}.contrasts[${index}]`);
    requireSelector(assertion.selector, `${captureName}.contrasts[${index}].selector`);
    if (typeof assertion.minimum !== 'number' || assertion.minimum < 1 || assertion.minimum > 21) throw new TypeError(`${captureName}.contrasts[${index}].minimum is invalid`);
  }
}

function requireRecord(value, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
}

function requireSafeName(value, label) {
  if (typeof value !== 'string' || !SAFE_NAME.test(value)) throw new TypeError(`${label} must be a bounded kebab-case name`);
}

function requireSelector(value, label) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 512 || value.includes('\0')) throw new TypeError(`${label} must be a bounded selector`);
}

function hasEntries(value) {
  return Array.isArray(value) && value.length > 0;
}

function parseCssColor(value) {
  if (typeof value !== 'string') throw new TypeError('CSS color must be a string');
  const rgb = value.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+))?\s*\)$/i);
  if (rgb) return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3]), rgb[4] === undefined ? 1 : Number(rgb[4])];
  const srgb = value.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)(?:\s*\/\s*([\d.]+))?\s*\)$/i);
  if (srgb) return [Number(srgb[1]) * 255, Number(srgb[2]) * 255, Number(srgb[3]) * 255, srgb[4] === undefined ? 1 : Number(srgb[4])];
  const hex = value.match(/^#([0-9a-f]{6})([0-9a-f]{2})?$/i);
  if (hex) {
    const packed = hex[1];
    return [
      Number.parseInt(packed.slice(0, 2), 16),
      Number.parseInt(packed.slice(2, 4), 16),
      Number.parseInt(packed.slice(4, 6), 16),
      hex[2] === undefined ? 1 : Number.parseInt(hex[2], 16) / 255,
    ];
  }
  throw new TypeError(`unsupported CSS color: ${value}`);
}

function resolveBackgroundColor(backgroundLayers) {
  if (!Array.isArray(backgroundLayers)) throw new TypeError('background layers must be an array');
  let background = [255, 255, 255, 1];
  for (const layer of backgroundLayers) background = compositeColor(parseCssColor(layer), background);
  return background;
}

function serializeCssColor(color) {
  const [red, green, blue, alpha] = color.map(value => Math.round(value * 1000) / 1000);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

function compositeColor(foreground, background) {
  const alpha = foreground[3] + background[3] * (1 - foreground[3]);
  if (alpha <= 0) return [0, 0, 0, 0];
  return [
    (foreground[0] * foreground[3] + background[0] * background[3] * (1 - foreground[3])) / alpha,
    (foreground[1] * foreground[3] + background[1] * background[3] * (1 - foreground[3])) / alpha,
    (foreground[2] * foreground[3] + background[2] * background[3] * (1 - foreground[3])) / alpha,
    alpha,
  ];
}

function relativeLuminance(color) {
  const channels = color.slice(0, 3).map(channel => {
    const normalized = channel / 255;
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function assertEqualAuthority(label, actual, expected, normalize) {
  if (normalize(actual) !== normalize(expected)) throw new Error(`runtime authority ${label} mismatch`);
}

function assertGeometryValue(label, actual, expected, tolerance) {
  if (typeof actual !== 'number' || !Number.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
    throw new Error(`Thor geometry ${label} expected ${expected}, received ${actual}`);
  }
}

function assertLoopbackUrl(value, label, protocols) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new TypeError(`${label} must be a valid URL`);
  }
  if (!protocols.has(url.protocol)) throw new TypeError(`${label} uses an unsupported protocol`);
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) throw new TypeError(`${label} must use loopback`);
  if (url.username || url.password) throw new TypeError(`${label} must not contain credentials`);
  return url;
}

async function fetchTargets(cdpUrl) {
  const base = new URL(cdpUrl.endsWith('/') ? cdpUrl : `${cdpUrl}/`);
  const response = await fetch(new URL('json/list', base));
  if (!response.ok) throw new Error(`CDP target discovery failed with HTTP ${response.status}`);
  return response.json();
}

async function readGeometry(session) {
  return evaluate(session, () => ({
    innerWidth,
    innerHeight,
    devicePixelRatio,
    visualViewportWidth: visualViewport?.width ?? null,
    visualViewportHeight: visualViewport?.height ?? null,
  }));
}

async function readQaRuntimeIdentity(session) {
  return evaluate(session, async () => {
    const response = await fetch('/api/qa/runtime-identity', { cache: 'no-store' });
    if (!response.ok) throw new Error(`QA runtime identity request failed with HTTP ${response.status}`);
    return response.json();
  });
}

async function readRuntimeState(session) {
  return evaluate(session, async () => {
    const response = await fetch('/api/state', { cache: 'no-store' });
    if (!response.ok) throw new Error(`State request failed with HTTP ${response.status}`);
    return response.json();
  });
}

async function runSteps(session, steps, expectedOrigin) {
  const results = [];
  for (const step of steps) {
    let result;
    if (step.kind === 'action') {
      await evaluate(session, async action => {
        const response = await fetch('/api/actions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(action),
        });
        if (!response.ok) throw new Error(`Action failed with HTTP ${response.status}`);
        return response.json();
      }, step.value);
      result = { kind: 'action', type: step.value.type };
    } else if (step.kind === 'touch') {
      const target = await inspectTouchTarget(session, step.selector, expectedOrigin);
      await dispatchTouch(session, target.center, target.center);
      result = { kind: 'touch', selector: step.selector, ...target };
    } else if (step.kind === 'key') {
      const repeat = step.repeat ?? 1;
      for (let index = 0; index < repeat; index += 1) await dispatchKey(session, step.key, step.shift === true);
      result = { kind: 'key', key: step.key, repeat, shift: step.shift === true };
    } else {
      result = {
        kind: 'swipe',
        selector: step.selector,
        direction: step.direction,
        gestures: await dispatchSwipeStep(session, step),
      };
    }
    await waitFor(session, async () => {
      const current = await evaluate(session, () => location.origin);
      return current === expectedOrigin;
    }, 'attached package origin');
    await waitForElementInViewport(session, step.waitFor);
    await waitForVisualStability(session, step.waitFor);
    results.push({ ...result, waitFor: step.waitFor });
  }
  return results;
}

async function dispatchKey(session, key, shift) {
  const descriptor = SAFE_KEYS.get(key);
  if (!descriptor) throw new TypeError('key is not allowed');
  const event = {
    key,
    code: descriptor.code,
    windowsVirtualKeyCode: descriptor.virtualKeyCode,
    nativeVirtualKeyCode: descriptor.virtualKeyCode,
    modifiers: shift ? 8 : 0,
  };
  await session.send('Input.dispatchKeyEvent', { type: 'rawKeyDown', ...event });
  await session.send('Input.dispatchKeyEvent', { type: 'keyUp', ...event });
}

async function dispatchTouch(session, start, end) {
  await session.send('Input.dispatchTouchEvent', {
    type: 'touchStart',
    touchPoints: [{ x: start.x, y: start.y, radiusX: 1, radiusY: 1, force: 1, id: 1 }],
  });
  if (start.x !== end.x || start.y !== end.y) {
    for (let index = 1; index <= 4; index += 1) {
      const progress = index / 4;
      await session.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{
          x: start.x + (end.x - start.x) * progress,
          y: start.y + (end.y - start.y) * progress,
          radiusX: 1,
          radiusY: 1,
          force: 1,
          id: 1,
        }],
      });
    }
  }
  await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
}

async function dispatchSwipeStep(session, step) {
  const gestures = [];
  for (let index = 0; index < step.repeat; index += 1) {
    const target = await inspectSwipeTarget(session, step.selector, step.direction);
    await dispatchTouch(session, target.start, target.end);
    await waitFor(session, async () => {
      const scrollTop = await evaluate(session, selector => {
        const element = document.querySelector(selector);
        return element instanceof HTMLElement ? element.scrollTop : null;
      }, step.selector);
      return typeof scrollTop === 'number' && Math.abs(scrollTop - target.scrollTop) > 1;
    }, `${step.selector} scroll`, 3_000);
    const finalScrollTop = await evaluate(session, selector => document.querySelector(selector)?.scrollTop ?? null, step.selector);
    gestures.push({ ...target, finalScrollTop });
    if (await elementIntersectsViewport(session, step.waitFor)) break;
  }
  return gestures;
}

async function inspectSwipeTarget(session, selector, direction) {
  return evaluate(session, ({ targetSelector, swipeDirection }) => {
    const matches = [...document.querySelectorAll(targetSelector)];
    if (matches.length !== 1 || !(matches[0] instanceof HTMLElement)) {
      throw new Error(`Swipe selector must match exactly one HTML element: ${targetSelector}`);
    }
    const element = matches[0];
    const bounds = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    const top = Math.max(0, bounds.top) + 24;
    const bottom = Math.min(innerHeight, bounds.bottom) - 24;
    if (bottom - top < 80 || !['auto', 'scroll'].includes(style.overflowY) || element.scrollHeight <= element.clientHeight + 1) {
      throw new Error(`Swipe target is not a visible vertical scroll owner: ${targetSelector}`);
    }
    const x = Math.min(innerWidth - 24, Math.max(24, bounds.left + bounds.width / 2));
    const start = { x, y: swipeDirection === 'up' ? bottom : top };
    const end = { x, y: swipeDirection === 'up' ? top : bottom };
    const hit = document.elementFromPoint(start.x, start.y);
    if (!(hit instanceof Element) || (hit !== element && !element.contains(hit))) {
      throw new Error(`Swipe start is occluded: ${targetSelector}`);
    }
    const round = value => Math.round(value * 1000) / 1000;
    return {
      start: { x: round(start.x), y: round(start.y) },
      end: { x: round(end.x), y: round(end.y) },
      scrollTop: round(element.scrollTop),
      maximumScrollTop: element.scrollHeight - element.clientHeight,
      hitTag: hit.tagName.toLowerCase(),
    };
  }, { targetSelector: selector, swipeDirection: direction });
}

async function inspectTouchTarget(session, selector, expectedOrigin) {
  const target = await evaluate(session, ({ targetSelector, origin }) => {
    const matches = [...document.querySelectorAll(targetSelector)];
    if (matches.length !== 1 || !(matches[0] instanceof HTMLElement)) {
      throw new Error(`Touch selector must match exactly one HTML element: ${targetSelector}`);
    }
    const element = matches[0];
    const bounds = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0 || style.pointerEvents === 'none') {
      throw new Error(`Touch target is not interactive: ${targetSelector}`);
    }
    if ('disabled' in element && element.disabled) throw new Error(`Touch target is disabled: ${targetSelector}`);
    if (element instanceof HTMLAnchorElement) {
      const destination = new URL(element.href, location.href);
      if (!['dualdex:', 'http:', 'https:'].includes(destination.protocol)) throw new Error('Touch target uses an unsupported protocol');
      if (['http:', 'https:'].includes(destination.protocol) && destination.origin !== origin) {
        throw new Error('Touch target leaves the attached package origin');
      }
    }
    const center = { x: bounds.left + bounds.width / 2, y: bounds.top + bounds.height / 2 };
    const hit = document.elementFromPoint(center.x, center.y);
    if (!(hit instanceof Element) || (hit !== element && !element.contains(hit))) {
      throw new Error(`Touch target center is occluded: ${targetSelector}`);
    }
    const round = value => Math.round(value * 1000) / 1000;
    return {
      bounds: {
        x: round(bounds.x),
        y: round(bounds.y),
        width: round(bounds.width),
        height: round(bounds.height),
      },
      viewport: {
        x: round(visualViewport?.offsetLeft ?? 0),
        y: round(visualViewport?.offsetTop ?? 0),
        width: round(visualViewport?.width ?? innerWidth),
        height: round(visualViewport?.height ?? innerHeight),
      },
      center: { x: round(center.x), y: round(center.y) },
      hitTag: hit.tagName.toLowerCase(),
    };
  }, { targetSelector: selector, origin: expectedOrigin });
  assertTouchTargetBounds(target.bounds, target.viewport);
  return target;
}

async function elementIntersectsViewport(session, selector) {
  return evaluate(session, requestedSelector => {
    const element = document.querySelector(requestedSelector);
    if (!(element instanceof HTMLElement)) return false;
    const bounds = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    return bounds.width > 0
      && bounds.height > 0
      && bounds.bottom > 0
      && bounds.right > 0
      && bounds.top < innerHeight
      && bounds.left < innerWidth
      && style.display !== 'none'
      && style.visibility !== 'hidden'
      && Number(style.opacity) !== 0;
  }, selector);
}

async function waitForElementInViewport(session, selector) {
  await waitFor(session, () => elementIntersectsViewport(session, selector), selector);
}

async function waitForCapture(session, capture) {
  await waitForElementInViewport(session, capture.waitFor);
  if ((capture.active ?? []).length > 0) {
    await waitFor(session, async () => {
      try {
        await assertActiveState(session, capture.active);
        return true;
      } catch {
        return false;
      }
    }, `${capture.name} active state`);
  }
  await waitForVisualStability(session, capture.waitFor);
}

async function waitForVisualStability(session, selector) {
  let previous = null;
  let consecutiveStableSamples = 0;
  await waitFor(session, async () => {
    const current = await readElementVisualState(session, selector);
    if (isVisualStateStable(previous, current)) {
      consecutiveStableSamples += 1;
    } else {
      consecutiveStableSamples = 0;
    }
    previous = current;
    return consecutiveStableSamples >= 2;
  }, `${selector} visual stability`);
}

async function readElementVisualState(session, selector) {
  return evaluate(session, requestedSelector => {
    const element = document.querySelector(requestedSelector);
    if (!(element instanceof HTMLElement)) return null;
    const bounds = element.getBoundingClientRect();
    let runningAnimations = 0;
    for (let current = element; current; current = current.parentElement) {
      runningAnimations += current.getAnimations().filter(animation => animation.playState === 'running').length;
    }
    return {
      bounds: {
        x: bounds.x,
        y: bounds.y,
        width: bounds.width,
        height: bounds.height,
      },
      runningAnimations,
    };
  }, selector);
}

async function waitFor(session, predicate, label, timeoutMillis = 15_000) {
  const deadline = Date.now() + timeoutMillis;
  let lastError;
  while (Date.now() < deadline) {
    try {
      if (await predicate()) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolvePromise => setTimeout(resolvePromise, 100));
  }
  throw new Error(`timed out waiting for ${label}${lastError ? `: ${lastError.message}` : ''}`);
}

async function scanPrivacy(session) {
  return evaluate(session, () => {
    const isVisible = element => {
      const bounds = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return bounds.width > 0
        && bounds.height > 0
        && bounds.bottom > 0
        && bounds.right > 0
        && bounds.top < innerHeight
        && bounds.left < innerWidth
        && style.display !== 'none'
        && style.visibility !== 'hidden'
        && Number(style.opacity) !== 0;
    };
    const inputs = [...document.querySelectorAll('input, textarea')].filter(isVisible);
    const visibleText = [...document.querySelectorAll('body *')]
      .filter(element => element.children.length === 0 && isVisible(element))
      .map(element => element.textContent ?? '')
      .join('\n');
    return {
      passwordInputs: inputs.filter(input => input instanceof HTMLInputElement && input.type === 'password').map(() => 'present'),
      populatedTextInputs: inputs.filter(input => {
        if (!(input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement)) return false;
        if (['button', 'checkbox', 'file', 'hidden', 'radio', 'range', 'reset', 'submit'].includes(input.type)) return false;
        return input.value.trim().length > 0;
      }).map(() => 'present'),
      pathLikeText: /(?:\b[A-Za-z]:[\\/]|(?:^|\s)\/(?:Users|home)\/)/m.test(visibleText) ? ['present'] : [],
      emailLikeText: /\b[^\s@]+@[^\s@]+\.[^\s@]+\b/.test(visibleText) ? ['present'] : [],
    };
  });
}

async function measureSelectors(session, selectors) {
  const result = await evaluate(session, requested => {
    const round = value => Math.round(value * 1000) / 1000;
    const backgroundLayers = element => {
      const layers = [];
      let current = element;
      while (current instanceof Element) {
        layers.unshift(getComputedStyle(current).backgroundColor);
        current = current.parentElement;
      }
      return layers;
    };
    return requested.map(selector => {
      const matches = [...document.querySelectorAll(selector)];
      return {
        selector,
        matchCount: matches.length,
        elements: matches.length > 64 ? [] : matches.map((element, index) => {
        const bounds = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        const scrollsX = element.scrollWidth > element.clientWidth + 1;
        const scrollsY = element.scrollHeight > element.clientHeight + 1;
        return {
          index,
          tag: element.tagName.toLowerCase(),
          role: element.getAttribute('role'),
          visible: bounds.width > 0
            && bounds.height > 0
            && bounds.bottom > 0
            && bounds.right > 0
            && bounds.top < innerHeight
            && bounds.left < innerWidth
            && style.display !== 'none'
            && style.visibility !== 'hidden'
            && Number(style.opacity) !== 0,
          bounds: { x: round(bounds.x), y: round(bounds.y), width: round(bounds.width), height: round(bounds.height) },
          client: { width: element.clientWidth, height: element.clientHeight },
          scroll: { width: element.scrollWidth, height: element.scrollHeight },
          overflow: { x: style.overflowX, y: style.overflowY, scrollsX, scrollsY },
          scrollOwner: {
            x: scrollsX && ['auto', 'scroll'].includes(style.overflowX),
            y: scrollsY && ['auto', 'scroll'].includes(style.overflowY),
          },
          colors: {
            foreground: style.color,
            background: style.backgroundColor,
            backgroundLayers: backgroundLayers(element),
            borderTop: style.borderTopColor,
          },
          active: {
            ariaCurrent: element.getAttribute('aria-current'),
            ariaPressed: element.getAttribute('aria-pressed'),
            ariaSelected: element.getAttribute('aria-selected'),
            checked: 'checked' in element ? Boolean(element.checked) : null,
            dataActive: element.getAttribute('data-active'),
          },
        };
      }),
      };
    });
  }, selectors);
  for (const measurement of result) {
    assertSelectorMatchBudget(measurement.matchCount, measurement.selector);
    if (measurement.matchCount === 0) throw new Error(`measurement selector did not match: ${measurement.selector}`);
    for (const element of measurement.elements) {
      if (!element.visible) throw new Error(`measurement target is outside the captured viewport: ${measurement.selector}[${element.index}]`);
      element.colors.effectiveBackground = serializeCssColor(resolveBackgroundColor(element.colors.backgroundLayers));
      element.colors.textContrast = effectiveContrastRatio(element.colors.foreground, element.colors.backgroundLayers);
      element.colors.borderContrast = effectiveContrastRatio(element.colors.borderTop, element.colors.backgroundLayers);
    }
  }
  return result;
}

function assertMeasuredContainment(capture, measurements) {
  const allowed = new Set(capture.allowHorizontalOverflow ?? []);
  for (const measurement of measurements) {
    if (allowed.has(measurement.selector)) continue;
    for (const element of measurement.elements) {
      if (element.overflow.scrollsX) {
        throw new Error(`${capture.name} horizontal overflow at ${measurement.selector}[${element.index}]: ${element.scroll.width}/${element.client.width}`);
      }
    }
  }
}

function assertMeasuredTouchTargets(capture, measurements, geometry) {
  const selectors = capture.touchTargets ?? [];
  if (selectors.length === 0) return [];
  const bySelector = new Map(measurements.map(measurement => [measurement.selector, measurement]));
  const targets = selectors.flatMap(selector => {
    const measurement = bySelector.get(selector);
    if (!measurement) throw new Error(`touch target selector was not measured: ${selector}`);
    return measurement.elements.map(element => ({
      selector,
      index: element.index,
      bounds: element.bounds,
    }));
  });
  return assertTouchTargetLayout(targets, {
    x: 0,
    y: 0,
    width: geometry.visualViewportWidth,
    height: geometry.visualViewportHeight,
  });
}

async function assertActiveState(session, assertions) {
  const results = [];
  for (const assertion of assertions) {
    const result = await evaluate(session, expected => {
      const element = document.querySelector(expected.selector);
      if (!(element instanceof HTMLElement)) return { matched: false, reason: 'missing' };
      const attributeValue = expected.attribute ? element.getAttribute(expected.attribute) : null;
      const textMatched = expected.textIncludes ? (element.textContent ?? '').includes(expected.textIncludes) : true;
      const attributeMatched = expected.attribute ? attributeValue === expected.equals : true;
      const focusMatched = expected.focused === undefined || (document.activeElement === element) === expected.focused;
      const bounds = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      const visible = bounds.width > 0 && bounds.height > 0 && bounds.bottom > 0 && bounds.right > 0
        && bounds.top < innerHeight && bounds.left < innerWidth && style.display !== 'none'
        && style.visibility !== 'hidden' && Number(style.opacity) !== 0;
      return {
        matched: attributeMatched && textMatched && focusMatched && visible,
        attributeValue,
        textMatched,
        focusMatched,
        visible,
      };
    }, assertion);
    if (!result.matched) throw new Error(`active state mismatch for ${assertion.selector}`);
    results.push({
      selector: assertion.selector,
      attribute: assertion.attribute ?? null,
      equals: assertion.equals ?? null,
      textIncludes: assertion.textIncludes ?? null,
      focused: assertion.focused ?? null,
    });
  }
  return results;
}

async function assertContrasts(session, assertions) {
  const results = [];
  for (const assertion of assertions) {
    const colors = await evaluate(session, selector => {
      const element = document.querySelector(selector);
      if (!(element instanceof HTMLElement)) throw new Error(`Contrast target not found: ${selector}`);
      const bounds = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      if (bounds.width <= 0 || bounds.height <= 0 || bounds.bottom <= 0 || bounds.right <= 0
        || bounds.top >= innerHeight || bounds.left >= innerWidth || style.display === 'none'
        || style.visibility === 'hidden' || Number(style.opacity) === 0) {
        throw new Error(`Contrast target is outside the captured viewport: ${selector}`);
      }
      const layers = [];
      let current = element;
      while (current instanceof Element) {
        layers.unshift(getComputedStyle(current).backgroundColor);
        current = current.parentElement;
      }
      return { foreground: getComputedStyle(element).color, backgroundLayers: layers };
    }, assertion.selector);
    const ratio = effectiveContrastRatio(colors.foreground, colors.backgroundLayers);
    if (ratio < assertion.minimum) throw new Error(`contrast ${ratio} is below ${assertion.minimum} for ${assertion.selector}`);
    results.push({
      selector: assertion.selector,
      minimum: assertion.minimum,
      ratio,
      effectiveBackground: serializeCssColor(resolveBackgroundColor(colors.backgroundLayers)),
    });
  }
  return results;
}

async function evaluate(session, operation, argument) {
  const expression = `(${operation.toString()})(${argument === undefined ? '' : JSON.stringify(argument)})`;
  const response = await session.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
    userGesture: true,
  });
  if (response.exceptionDetails) {
    const detail = response.exceptionDetails.exception?.description ?? response.exceptionDetails.text ?? 'Runtime evaluation failed';
    throw new Error(detail);
  }
  return response.result?.value;
}

async function prepareEvidenceOutput(output) {
  const outputDirectory = resolve(output);
  await mkdir(outputDirectory, { recursive: true });
  return outputDirectory;
}

async function writeCaptureEvidence(outputDirectory, capture, screenshot) {
  const serialized = `${JSON.stringify(capture, null, 2)}\n`;
  assertEvidenceBudget({ captureReportBytes: Buffer.byteLength(serialized) });
  await writeFile(join(outputDirectory, `${capture.name}.png`), screenshot, { flag: 'wx' });
  await writeFile(join(outputDirectory, `${capture.name}.json`), serialized, { flag: 'wx' });
}

async function writeFinalEvidence(outputDirectory, report) {
  const finalReport = {
    schemaVersion: 1,
    createdAt: new Date().toISOString(),
    ...report,
  };
  const serialized = `${JSON.stringify(finalReport, null, 2)}\n`;
  assertEvidenceBudget({ finalReportBytes: Buffer.byteLength(serialized) });
  await writeFile(join(outputDirectory, 'report.json'), serialized, { flag: 'wx' });
}

class CdpSession {
  static async connect(url) {
    if (typeof WebSocket !== 'function') throw new Error('Node.js 24 or newer is required for the global WebSocket client');
    const socket = new WebSocket(url);
    await new Promise((resolvePromise, reject) => {
      const timer = setTimeout(() => reject(new Error('timed out connecting to CDP')), 10_000);
      socket.addEventListener('open', () => {
        clearTimeout(timer);
        resolvePromise();
      }, { once: true });
      socket.addEventListener('error', () => {
        clearTimeout(timer);
        reject(new Error('failed to connect to CDP'));
      }, { once: true });
    });
    return new CdpSession(socket);
  }

  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    socket.addEventListener('message', event => this.#receive(event.data));
    socket.addEventListener('close', () => this.#failPending(new Error('CDP connection closed')));
    socket.addEventListener('error', () => this.#failPending(new Error('CDP connection failed')));
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolvePromise, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP ${method} timed out`));
      }, 15_000);
      this.pending.set(id, { resolve: resolvePromise, reject, timer, method });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  close() {
    this.socket.close();
  }

  #receive(data) {
    let message;
    try {
      message = JSON.parse(typeof data === 'string' ? data : Buffer.from(data).toString('utf8'));
    } catch {
      this.#failPending(new Error('CDP returned malformed JSON'));
      return;
    }
    if (!message.id) return;
    const pending = this.pending.get(message.id);
    if (!pending) return;
    this.pending.delete(message.id);
    clearTimeout(pending.timer);
    if (message.error) pending.reject(new Error(`CDP ${pending.method} failed: ${message.error.message}`));
    else pending.resolve(message.result);
  }

  #failPending(error) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.pending.clear();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  run().catch(error => {
    process.stderr.write(`${error.stack ?? error.message}\n`);
    process.exitCode = 1;
  });
}
