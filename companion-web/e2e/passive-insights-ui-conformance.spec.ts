import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

interface RouteControl {
  id: string;
  family: 'baseline' | 'party-analysis' | 'area-guide' | 'trainer-progress' | 'specimens' | 'damage-forecast' | 'challenges';
  state: string;
  pattern: 'grid' | 'paper' | 'map';
  scrollOwner: string | null;
  baseline: string;
}

interface ConformanceManifest {
  schemaVersion: number;
  viewport: { width: number; height: number };
  fontScales: number[];
  themes: { id: string; kind: 'GAME' | 'FIXED'; control: string }[];
  routes: RouteControl[];
  expectedMatrixRows: number;
}

const manifestPath = join(process.cwd(), '..', 'docs', 'reports', 'passive-insights-progress', 'ui-conformance-route-matrix.json');

test('freezes the complete Stage 7 route, theme, and font-scale contract', () => {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as ConformanceManifest;
  const routeIds = new Set(manifest.routes.map(route => route.id));

  expect(manifest.schemaVersion).toBe(1);
  expect(manifest.viewport).toEqual({ width: 1024, height: 768 });
  expect(manifest.fontScales).toEqual([0.85, 1, 1.35]);
  expect(manifest.themes.map(theme => theme.id)).toEqual([
    'game-gen1', 'game-gen2', 'game-gen3', 'game-modern-emerald', 'game-unbound', 'game-odyssey',
    'light', 'dark', 'high-contrast',
  ]);
  expect(routeIds).toEqual(new Set([
    'baseline-pokedex', 'baseline-party', 'baseline-trainer-card', 'baseline-atlas', 'baseline-battle',
    'party-analysis-summary', 'party-analysis-comparison', 'party-analysis-linked-detail',
    'area-guide-collapsed', 'area-guide-populated', 'area-guide-empty',
    'progress-metrics', 'progress-challenges', 'progress-challenges-empty', 'progress-timeline', 'progress-timeline-empty',
    'specimens-loading', 'specimens-unavailable', 'specimens-empty', 'specimens-single', 'specimens-multiple', 'specimens-detail',
    'damage-exact', 'damage-bounded', 'damage-withheld', 'damage-unavailable',
    'challenge-expansion-list', 'challenge-expansion-detail',
  ]));
  expect(manifest.expectedMatrixRows).toBe(manifest.routes.length * manifest.themes.length * manifest.fontScales.length);
  expect(manifest.routes.every(route => route.baseline.length > 0)).toBe(true);
  expect(manifest.routes.filter(route => route.scrollOwner != null).every(route => route.scrollOwner!.startsWith('.'))).toBe(true);
});
