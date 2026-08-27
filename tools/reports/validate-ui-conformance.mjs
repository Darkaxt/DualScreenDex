#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const REQUIRED_CHECK_ARRAYS = [
  "missingAccessibleNames",
  "missingNonColorStatusCues",
  "undersizedTouchTargets",
  "contrastFailures",
  "clippedText",
  "diagnosticLeaks",
];

export async function buildUiConformanceReports({ manifest, rows, date = null, artifactRoot = null }) {
  validateManifest(manifest);
  if (rows.length !== manifest.expectedMatrixRows) {
    throw new Error(`expected ${manifest.expectedMatrixRows} rows, got ${rows.length}`);
  }

  const routeById = new Map(manifest.routes.map(route => [route.id, route]));
  const expectedKeys = new Set(manifest.routes.flatMap(route => manifest.themes.flatMap(theme =>
    manifest.fontScales.map(fontScale => matrixKey(route.id, theme.id, fontScale)))));
  const seen = new Set();
  const screenshotRows = [];

  for (const row of rows) {
    const key = matrixKey(row.routeId, row.themeId, row.fontScale);
    if (seen.has(key)) throw new Error(`duplicate matrix row ${key}`);
    if (!expectedKeys.has(key)) throw new Error(`unexpected matrix row ${key}`);
    seen.add(key);

    const route = routeById.get(row.routeId);
    if (row.routeFamily !== route.family || row.state !== route.state || row.pattern !== route.pattern || row.expectedScrollOwner !== route.scrollOwner) {
      throw new Error(`route contract mismatch for ${key}`);
    }
    validateRow(row, route, key);
    const screenshotPath = path.resolve(row.screenshot?.path ?? "");
    const screenshotBytes = await readFile(screenshotPath);
    const actualSha256 = createHash("sha256").update(screenshotBytes).digest("hex");
    if (actualSha256 !== row.screenshot.sha256) throw new Error(`screenshot hash mismatch for ${key}`);
    screenshotRows.push({ routeId: row.routeId, themeId: row.themeId, fontScale: row.fontScale, path: normalizedPath(artifactRoot ? path.relative(artifactRoot, screenshotPath) : screenshotPath), sha256: actualSha256 });
  }

  if (seen.size !== expectedKeys.size) {
    const missing = [...expectedKeys].filter(key => !seen.has(key));
    throw new Error(`missing matrix rows: ${missing.join(", ")}`);
  }

  const ordered = [...rows].sort(compareRows);
  const fontRows = ordered.map(row => ({ routeId: row.routeId, routeFamily: row.routeFamily, state: row.state, themeId: row.themeId, fontScale: row.fontScale, ...row.text }));
  const routeFonts = manifest.routes.map(route => {
    const values = fontRows.filter(row => row.routeId === route.id);
    return {
      routeId: route.id,
      family: route.family,
      state: route.state,
      rows: values.length,
      minimumPx: round(Math.min(...values.map(row => row.minimumPx))),
      maximumPx: round(Math.max(...values.map(row => row.maximumPx))),
      averagePx: round(values.reduce((sum, row) => sum + row.averagePx, 0) / values.length),
    };
  });
  const styleRows = ordered.map(row => ({ routeId: row.routeId, routeFamily: row.routeFamily, state: row.state, themeId: row.themeId, fontScale: row.fontScale, pattern: row.pattern, computedStyles: row.computedStyles }));

  return {
    summary: {
      schemaVersion: 1,
      date,
      viewport: manifest.viewport,
      routeCount: manifest.routes.length,
      themeCount: manifest.themes.length,
      fontScaleCount: manifest.fontScales.length,
      matrixRows: ordered.length,
      gates: { typography: true, contrast: true, overflow: true, scrollOwnership: true, accessibleNames: true, touchTargets: true, focus: true, diagnosticBoundary: true, screenshots: true },
      errors: [],
    },
    font: { schemaVersion: 1, date, viewport: manifest.viewport, rows: fontRows, routes: routeFonts, errors: [] },
    styles: { schemaVersion: 1, date, viewport: manifest.viewport, rows: styleRows, errors: [] },
    screenshots: { schemaVersion: 1, date, viewport: manifest.viewport, rows: screenshotRows.sort(compareRows), errors: [] },
  };
}

function validateManifest(manifest) {
  const expected = manifest.routes.length * manifest.themes.length * manifest.fontScales.length;
  if (manifest.expectedMatrixRows !== expected) throw new Error(`manifest expects ${manifest.expectedMatrixRows} rows but cartesian product is ${expected}`);
  for (const [name, values] of [["route", manifest.routes], ["theme", manifest.themes]]) {
    const ids = values.map(value => value.id);
    if (new Set(ids).size !== ids.length) throw new Error(`duplicate ${name} id`);
  }
  if (new Set(manifest.fontScales).size !== manifest.fontScales.length) throw new Error("duplicate font scale");
}

function validateRow(row, route, key) {
  if (!Number.isFinite(row.text?.count) || row.text.count <= 0) throw new Error(`no visible copy for ${key}`);
  if (!Number.isFinite(row.text.minimumPx) || row.text.minimumPx < 11.2) throw new Error(`font minimum failed for ${key}`);
  if (!Number.isFinite(row.text.averagePx) || row.text.averagePx < 12) throw new Error(`font average failed for ${key}`);
  if (!Number.isFinite(row.text.maximumPx) || row.text.maximumPx < row.text.minimumPx) throw new Error(`font range invalid for ${key}`);
  if (row.checks?.bodyOverflow) throw new Error(`body overflow for ${key}`);
  for (const check of REQUIRED_CHECK_ARRAYS) {
    if (!Array.isArray(row.checks?.[check])) throw new Error(`missing ${check} check for ${key}`);
    if (row.checks[check].length) throw new Error(`${check} failed for ${key}`);
  }
  if (row.checks?.expectedScrollOwnerVisible !== true) throw new Error(`expected scroll owner is missing for ${key}`);
  if (row.checks?.focusVisible !== true) throw new Error(`focus is not visible for ${key}`);
  if (!Array.isArray(row.checks?.activeScrollOwners) || row.checks.activeScrollOwners.length > 1) throw new Error(`scroll ownership failed for ${key}`);
  if (row.checks.activeScrollOwners.length === 1 && route.scrollOwner && !row.checks.activeScrollOwners[0].split(".").includes(route.scrollOwner.slice(1))) {
    throw new Error(`unexpected scroll owner for ${key}`);
  }
  if (!row.screenshot?.path || !/^[a-f0-9]{64}$/i.test(row.screenshot.sha256 ?? "")) throw new Error(`invalid screenshot evidence for ${key}`);
}

export async function writeUiConformanceReports({ reports, outDir }) {
  await mkdir(outDir, { recursive: true });
  await Promise.all([
    writeJson(path.join(outDir, "ui-conformance-summary.json"), reports.summary),
    writeJson(path.join(outDir, "ui-conformance-font-matrix.json"), reports.font),
    writeJson(path.join(outDir, "ui-conformance-computed-styles.json"), reports.styles),
    writeJson(path.join(outDir, "ui-conformance-screenshots.json"), reports.screenshots),
    writeFile(path.join(outDir, "ui-conformance-font-matrix.md"), fontMarkdown(reports), "utf8"),
    writeFile(path.join(outDir, "ui-conformance-summary.md"), summaryMarkdown(reports), "utf8"),
  ]);
}

function fontMarkdown(reports) {
  const lines = [
    "# Passive Insights UI Font Matrix",
    "",
    `Validated ${reports.summary.matrixRows} rendered rows at ${reports.summary.viewport.width}x${reports.summary.viewport.height}.`,
    "",
    "| Route | Family | State | Rows | Minimum px | Maximum px | Average px |",
    "|---|---|---|---:|---:|---:|---:|",
    ...reports.font.routes.map(row => `| ${row.routeId} | ${row.family} | ${row.state} | ${row.rows} | ${row.minimumPx.toFixed(2)} | ${row.maximumPx.toFixed(2)} | ${row.averagePx.toFixed(2)} |`),
    "",
    "Every rendered row satisfies the 11.2 px minimum and 12 px average gates. Per-theme and per-scale values are retained in `ui-conformance-font-matrix.json`.",
    "",
  ];
  return lines.join("\n");
}

function summaryMarkdown(reports) {
  return [
    "# Passive Insights UI Conformance Audit",
    "",
    `- Routes: ${reports.summary.routeCount}`,
    `- Themes: ${reports.summary.themeCount}`,
    `- Font scales: ${reports.summary.fontScaleCount}`,
    `- Rendered matrix rows: ${reports.summary.matrixRows}`,
    "- Typography, theme styles, contrast, overflow, scroll ownership, accessible names, touch targets, focus, diagnostic boundaries, screenshots: PASS",
    "- Errors: 0",
    "",
    "The screenshot manifest contains a SHA-256 for every row. Large PNG evidence remains outside the repository at the paths recorded in `ui-conformance-screenshots.json`.",
    "",
  ].join("\n");
}

function matrixKey(routeId, themeId, fontScale) { return `${routeId}/${themeId}/${fontScale}`; }
function normalizedPath(value) { return value.replaceAll("\\", "/"); }
function round(value) { return Math.round(value * 100) / 100; }
function compareRows(left, right) { return matrixKey(left.routeId, left.themeId, left.fontScale).localeCompare(matrixKey(right.routeId, right.themeId, right.fontScale)); }
async function writeJson(file, value) { await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, "utf8"); }

function parseArguments(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const name = argv[index];
    const value = argv[index + 1];
    if (!name?.startsWith("--") || !value) throw new Error(`invalid argument ${name ?? ""}`);
    options[name.slice(2)] = value;
  }
  for (const required of ["manifest", "rows", "out", "date"]) if (!options[required]) throw new Error(`--${required} is required`);
  return options;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  const rowFiles = (await readdir(options.rows, { withFileTypes: true })).filter(entry => entry.isFile() && entry.name.endsWith(".json")).sort((left, right) => left.name.localeCompare(right.name));
  const rows = (await Promise.all(rowFiles.map(async entry => JSON.parse(await readFile(path.join(options.rows, entry.name), "utf8"))))).flat();
  const reports = await buildUiConformanceReports({ manifest, rows, date: options.date, artifactRoot: path.dirname(path.resolve(options.rows)) });
  await writeUiConformanceReports({ reports, outDir: options.out });
  console.log(JSON.stringify(reports.summary));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
