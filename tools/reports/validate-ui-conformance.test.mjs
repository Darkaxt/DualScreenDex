import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";

import { buildUiConformanceReports } from "./validate-ui-conformance.mjs";

const manifest = {
  schemaVersion: 1,
  viewport: { width: 1024, height: 768 },
  routes: [{ id: "party", family: "party", state: "populated", pattern: "grid", baseline: "party.png", scrollOwner: ".party-content" }],
  themes: [{ id: "light", source: "fixed" }, { id: "dark", source: "fixed" }],
  fontScales: [0.85, 1],
  expectedMatrixRows: 4,
};

test("validates the complete cartesian matrix and emits durable report models", async () => {
  const root = await mkdtemp("D:/Temp/dualdex-ui-conformance-test-");
  try {
    const screenshot = path.join(root, "party.png");
    await writeFile(screenshot, "pixels");
    const sha256 = createHash("sha256").update("pixels").digest("hex");
    const rows = manifest.themes.flatMap(theme => manifest.fontScales.map(fontScale => row({ themeId: theme.id, fontScale, screenshot, sha256 })));

    const reports = await buildUiConformanceReports({ manifest, rows, artifactRoot: root });

    assert.equal(reports.summary.matrixRows, 4);
    assert.equal(reports.summary.routeCount, 1);
    assert.deepEqual(reports.summary.errors, []);
    assert.equal(reports.font.rows.length, 4);
    assert.equal(reports.font.routes[0].minimumPx, 11.2);
    assert.equal(reports.styles.rows.length, 4);
    assert.equal(reports.screenshots.rows[0].sha256, sha256);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("fails closed for missing, duplicate, failed, or tampered rows", async () => {
  const root = await mkdtemp("D:/Temp/dualdex-ui-conformance-test-");
  try {
    const screenshot = path.join(root, "party.png");
    await mkdir(path.dirname(screenshot), { recursive: true });
    await writeFile(screenshot, "pixels");
    const sha256 = createHash("sha256").update("pixels").digest("hex");
    const rows = manifest.themes.flatMap(theme => manifest.fontScales.map(fontScale => row({ themeId: theme.id, fontScale, screenshot, sha256 })));

    await assert.rejects(() => buildUiConformanceReports({ manifest, rows: rows.slice(1) }), /expected 4 rows, got 3/);
    await assert.rejects(() => buildUiConformanceReports({ manifest, rows: [rows[0], rows[0], rows[2], rows[3]] }), /duplicate matrix row/);
    await assert.rejects(() => buildUiConformanceReports({ manifest, rows: rows.map((item, index) => index ? item : { ...item, checks: { ...item.checks, focusVisible: false } }) }), /focus is not visible/);
    await assert.rejects(() => buildUiConformanceReports({ manifest, rows: rows.map((item, index) => index ? item : { ...item, screenshot: { ...item.screenshot, sha256: "0".repeat(64) } }) }), /screenshot hash mismatch/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

function row({ themeId, fontScale, screenshot, sha256 }) {
  return {
    routeId: "party", routeFamily: "party", state: "populated", themeId, fontScale, pattern: "grid", expectedScrollOwner: ".party-content",
    text: { count: 4, minimumPx: 11.2, maximumPx: 24, averagePx: 14, smallest: ["11.2px small:label"] },
    computedStyles: { page: { color: "rgb(0, 0, 0)", backgroundColor: "rgb(255, 255, 255)" } },
    checks: { bodyOverflow: false, missingAccessibleNames: [], missingNonColorStatusCues: [], undersizedTouchTargets: [], contrastFailures: [], clippedText: [], diagnosticLeaks: [], activeScrollOwners: [".party-content"], expectedScrollOwnerVisible: true, focusVisible: true },
    screenshot: { path: screenshot, sha256 },
  };
}
