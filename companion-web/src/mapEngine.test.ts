import { describe, expect, it } from 'vitest';
import { anchoredZoom, centerMapPoint, containFit, edgesAreBlack, focusMapRect, GestureTracker, maximumScaleForMarker } from './mapEngine';

describe('world map viewport engine', () => {
  it('contain-fits the intrinsic raster without stretching it', () => {
    const emerald = containFit(224, 120, 960, 620);
    expect(emerald.width).toBe(960);
    expect(emerald.height).toBeCloseTo(514.2857142857143, 10);
    expect(emerald.width / emerald.height).toBeCloseTo(224 / 120, 10);
    const fireRed = containFit(176, 120, 960, 620);
    expect(fireRed.width).toBeCloseTo(909.3333333333334, 10);
    expect(fireRed.height).toBe(620);
    expect(fireRed.width / fireRed.height).toBeCloseTo(176 / 120, 10);
  });

  it('keeps the map point under the zoom anchor fixed', () => {
    const viewport = { scale: 1, panX: 14, panY: -8 };
    const center = { x: 500, y: 350 };
    const anchor = { x: 620, y: 420 };
    const mapBefore = mapAt(viewport, anchor, center);

    const zoomed = anchoredZoom(viewport, 1.75, anchor, center);

    expect(mapAt(zoomed, anchor, center).x).toBeCloseTo(mapBefore.x, 8);
    expect(mapAt(zoomed, anchor, center).y).toBeCloseTo(mapBefore.y, 8);
  });

  it('restores previous Local-map detail inside a larger scene', () => {
    const focused = focusMapRect(
      1000,
      500,
      { x: 0, y: 0, width: 100, height: 100 },
      1000,
      500,
    );

    expect(focused.fit).toEqual({ width: 1000, height: 500, scale: 1 });
    expect(focused.viewport).toEqual({ scale: 5, panX: 2250, panY: 1000 });
    expect(focused.maximumScale).toBe(20);
    expect(anchoredZoom(focused.viewport, 12, { x: 500, y: 250 }, { x: 500, y: 250 }, focused.maximumScale).scale)
      .toBe(12);

    const gesture = new GestureTracker(focused.viewport, focused.maximumScale);
    gesture.setViewport({ ...focused.viewport, scale: 16 });
    expect(gesture.viewport.scale).toBe(16);
  });

  it('centers a live player point without changing the current zoom', () => {
    const centered = centerMapPoint(
      { scale: 7.5, panX: 31, panY: -18 },
      { x: 0, y: 0, width: 704, height: 320 },
      { width: 1240, height: 563.6363636364 },
      { x: 344, y: 120 },
    );

    expect(centered.scale).toBe(7.5);
    expect(centered.panX).toBeCloseTo(105.6818181818, 8);
    expect(centered.panY).toBeCloseTo(528.4090909091, 8);
    expect(centered.panX + ((344 / 704) - 0.5) * 1240 * centered.scale).toBeCloseTo(0, 8);
    expect(centered.panY + ((120 / 320) - 0.5) * 563.6363636364 * centered.scale).toBeCloseTo(0, 8);
  });

  it('caps local zoom when a map tile reaches the intrinsic trainer-marker size', () => {
    expect(maximumScaleForMarker(0.08, 16, 64)).toBe(50);
    expect(0.08 * maximumScaleForMarker(0.08, 16, 64) * 16).toBe(64);
  });

  it('pans with one pointer and clears a canceled gesture', () => {
    const gesture = new GestureTracker();
    gesture.setCenter(500, 350);
    gesture.down(1, 240, 200);

    expect(gesture.move(1, 302, 241)).toEqual({ scale: 1, panX: 62, panY: 41 });
    expect(gesture.cancel(1).select).toBe(false);
    expect(gesture.activeCount).toBe(0);
  });

  it('preserves the moving midpoint through pinch-out and pinch-in', () => {
    const gesture = new GestureTracker({ scale: 1.25, panX: 18, panY: -12 });
    gesture.setCenter(500, 350);
    gesture.down(1, 390, 330);
    gesture.down(2, 610, 410);
    const startMidpoint = { x: 500, y: 370 };
    const anchoredMap = mapAt(gesture.viewport, startMidpoint, { x: 500, y: 350 });

    gesture.move(1, 340, 290);
    const pinchOut = gesture.move(2, 680, 450);
    const outMidpoint = { x: 510, y: 370 };
    expect(pinchOut.scale).toBeGreaterThan(1.25);
    expect(mapAt(pinchOut, outMidpoint, { x: 500, y: 350 }).x).toBeCloseTo(anchoredMap.x, 8);
    expect(mapAt(pinchOut, outMidpoint, { x: 500, y: 350 }).y).toBeCloseTo(anchoredMap.y, 8);

    gesture.move(1, 430, 330);
    const pinchIn = gesture.move(2, 590, 410);
    expect(pinchIn.scale).toBeLessThan(pinchOut.scale);
    expect(gesture.up(1).select).toBe(false);
    expect(gesture.up(2).select).toBe(false);
  });

  it('requires every outer fog pixel to be opaque black', () => {
    const pixels = new Uint8ClampedArray(5 * 4 * 4).fill(19);
    for (let y = 0; y < 4; y += 1) for (let x = 0; x < 5; x += 1) {
      if (x === 0 || y === 0 || x === 4 || y === 3) pixels.set([0, 0, 0, 255], (y * 5 + x) * 4);
    }
    expect(edgesAreBlack(pixels, 5, 4)).toBe(true);
    pixels[(2 * 5 + 4) * 4 + 2] = 1;
    expect(edgesAreBlack(pixels, 5, 4)).toBe(false);
  });
});

function mapAt(
  viewport: { scale: number; panX: number; panY: number },
  point: { x: number; y: number },
  center: { x: number; y: number },
) {
  return {
    x: (point.x - center.x - viewport.panX) / viewport.scale,
    y: (point.y - center.y - viewport.panY) / viewport.scale,
  };
}
