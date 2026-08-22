export const MIN_MAP_SCALE = 1;
export const MAX_MAP_SCALE = 4;
export const MAX_SCENE_MAP_SCALE = 128;

export interface MapViewport {
  scale: number;
  panX: number;
  panY: number;
}

export interface MapPoint { x: number; y: number }
export interface MapRect extends MapPoint { width: number; height: number }

export function shouldGlideCamera(previous: MapPoint | null, next: MapPoint, gridWidth: number, gridHeight: number): boolean {
  if (!previous || gridWidth <= 0 || gridHeight <= 0) return false;
  const maximumContinuousDistance = Math.max(2, Math.min(gridWidth, gridHeight) / 4);
  return Math.hypot(next.x - previous.x, next.y - previous.y) <= maximumContinuousDistance;
}

/**
 * A critically damped camera follower. New coordinate samples only move the
 * target, so the camera keeps its velocity instead of restarting an animation
 * for every poll.
 */
export class AcceleratedMapFollower {
  position: MapPoint;
  velocity: MapPoint = { x: 0, y: 0 };
  private target: MapPoint;
  private smoothingPercent: number;

  constructor(initial: MapPoint, smoothingPercent = 25) {
    this.position = { ...initial };
    this.target = { ...initial };
    this.smoothingPercent = clampPercent(smoothingPercent);
  }

  get settled() {
    return Math.hypot(this.target.x - this.position.x, this.target.y - this.position.y) < 0.15
      && Math.hypot(this.velocity.x, this.velocity.y) < 0.15;
  }

  setSmoothingPercent(value: number) {
    this.smoothingPercent = clampPercent(value);
  }

  setTarget(target: MapPoint) {
    this.target = { ...target };
    if (this.smoothingPercent === 0) this.reset(target);
  }

  reset(position: MapPoint) {
    this.position = { ...position };
    this.target = { ...position };
    this.velocity = { x: 0, y: 0 };
  }

  step(deltaMs: number): MapPoint {
    if (this.settled) {
      this.reset(this.target);
      return { ...this.position };
    }
    if (!Number.isFinite(deltaMs) || deltaMs <= 0) return { ...this.position };

    // 25% takes roughly one polling interval to settle. Higher settings keep
    // more latency in exchange for a softer, uninterrupted follow.
    const responseSeconds = 0.18 + (this.smoothingPercent / 100) * 2.8;
    const omega = 4.6 / responseSeconds;
    const seconds = deltaMs / 1000;
    const x = criticallyDampedAxis(this.position.x, this.velocity.x, this.target.x, omega, seconds);
    const y = criticallyDampedAxis(this.position.y, this.velocity.y, this.target.y, omega, seconds);
    this.position = { x: x.position, y: y.position };
    this.velocity = { x: x.velocity, y: y.velocity };
    if (this.settled) this.reset(this.target);
    return { ...this.position };
  }
}

function criticallyDampedAxis(position: number, velocity: number, target: number, omega: number, seconds: number) {
  const offset = position - target;
  const coefficient = velocity + omega * offset;
  const decay = Math.exp(-omega * seconds);
  return {
    position: target + (offset + coefficient * seconds) * decay,
    velocity: (velocity - omega * coefficient * seconds) * decay,
  };
}

function clampPercent(value: number) {
  if (!Number.isFinite(value)) return 25;
  return Math.min(100, Math.max(0, value));
}

export function clampScale(value: number, maximumScale = MAX_MAP_SCALE): number {
  if (!Number.isFinite(value)) return MIN_MAP_SCALE;
  return Math.min(maximumScale, Math.max(MIN_MAP_SCALE, value));
}

export function containFit(intrinsicWidth: number, intrinsicHeight: number, availableWidth: number, availableHeight: number) {
  const dimensions = [intrinsicWidth, intrinsicHeight, availableWidth, availableHeight];
  if (dimensions.some(value => !Number.isFinite(value) || value <= 0)) {
    throw new RangeError('contain-fit dimensions must be positive finite numbers');
  }
  const scale = Math.min(availableWidth / intrinsicWidth, availableHeight / intrinsicHeight);
  return { width: intrinsicWidth * scale, height: intrinsicHeight * scale, scale };
}

export function focusMapRect(
  intrinsicWidth: number,
  intrinsicHeight: number,
  focus: MapRect,
  availableWidth: number,
  availableHeight: number,
) {
  const fit = containFit(intrinsicWidth, intrinsicHeight, availableWidth, availableHeight);
  const focusFit = containFit(focus.width, focus.height, availableWidth, availableHeight);
  const scale = clampScale(focusFit.scale / fit.scale, MAX_SCENE_MAP_SCALE);
  const focusOffsetX = ((focus.x + focus.width / 2) / intrinsicWidth - 0.5) * fit.width;
  const focusOffsetY = ((focus.y + focus.height / 2) / intrinsicHeight - 0.5) * fit.height;
  return {
    fit,
    viewport: {
      scale,
      panX: -focusOffsetX * scale,
      panY: -focusOffsetY * scale,
    },
    maximumScale: clampScale(scale * MAX_MAP_SCALE, MAX_SCENE_MAP_SCALE),
  };
}

export function anchoredZoom(
  viewport: MapViewport,
  requestedScale: number,
  anchor: MapPoint,
  center: MapPoint,
  maximumScale = MAX_MAP_SCALE,
): MapViewport {
  const scale = clampScale(requestedScale, maximumScale);
  const ratio = scale / viewport.scale;
  return {
    scale,
    panX: anchor.x - center.x - (anchor.x - center.x - viewport.panX) * ratio,
    panY: anchor.y - center.y - (anchor.y - center.y - viewport.panY) * ratio,
  };
}

export function centerMapPoint(
  viewport: MapViewport,
  intrinsic: MapRect,
  fit: { width: number; height: number },
  point: MapPoint,
): MapViewport {
  if (intrinsic.width <= 0 || intrinsic.height <= 0) return { ...viewport };
  return {
    scale: viewport.scale,
    panX: -((point.x / intrinsic.width) - 0.5) * fit.width * viewport.scale,
    panY: -((point.y / intrinsic.height) - 0.5) * fit.height * viewport.scale,
  };
}

export function maximumScaleForMarker(fitScale: number, tilePixels: number, markerPixels: number): number {
  const values = [fitScale, tilePixels, markerPixels];
  if (values.some(value => !Number.isFinite(value) || value <= 0)) {
    throw new RangeError('marker scale dimensions must be positive finite numbers');
  }
  return clampScale(markerPixels / tilePixels / fitScale, MAX_SCENE_MAP_SCALE);
}

type GestureMode = 'idle' | 'pan' | 'pinch' | 'pinch-tail';

export class GestureTracker {
  viewport: MapViewport;
  readonly pointers = new Map<number, MapPoint>();
  private center: MapPoint = { x: 0, y: 0 };
  private mode: GestureMode = 'idle';
  private moved = false;
  private suppressSelection = false;
  private panStart: (MapPoint & { viewport: MapViewport }) | null = null;
  private pinchStart: ({ distance: number; scale: number; mapX: number; mapY: number }) | null = null;
  private maximumScale: number;

  constructor(
    viewport: MapViewport = { scale: 1, panX: 0, panY: 0 },
    maximumScale = MAX_MAP_SCALE,
  ) {
    this.maximumScale = maximumScale;
    this.viewport = { ...viewport, scale: clampScale(viewport.scale, maximumScale) };
  }

  get activeCount() { return this.pointers.size; }

  setCenter(x: number, y: number) { this.center = { x, y }; }

  setMaximumScale(maximumScale: number) {
    this.maximumScale = Math.max(MIN_MAP_SCALE, maximumScale);
    this.viewport = { ...this.viewport, scale: clampScale(this.viewport.scale, this.maximumScale) };
    return { ...this.viewport };
  }

  setViewport(viewport: MapViewport) {
    this.viewport = { ...viewport, scale: clampScale(viewport.scale, this.maximumScale) };
  }

  down(pointerId: number, x: number, y: number): MapViewport {
    if (this.pointers.size === 0) {
      this.moved = false;
      this.suppressSelection = false;
    }
    this.pointers.set(pointerId, { x, y });
    if (this.pointers.size === 1) {
      this.mode = 'pan';
      this.panStart = { x, y, viewport: { ...this.viewport } };
    } else if (this.pointers.size === 2) {
      this.beginPinch();
    } else {
      this.suppressSelection = true;
    }
    return { ...this.viewport };
  }

  move(pointerId: number, x: number, y: number): MapViewport {
    if (!this.pointers.has(pointerId)) return { ...this.viewport };
    this.pointers.set(pointerId, { x, y });
    if (this.mode === 'pinch' && this.pointers.size >= 2 && this.pinchStart) {
      const [first, second] = [...this.pointers.values()];
      const midpoint = middle(first, second);
      const scale = clampScale(this.pinchStart.scale * span(first, second) / this.pinchStart.distance, this.maximumScale);
      this.viewport = {
        scale,
        panX: midpoint.x - this.center.x - this.pinchStart.mapX * scale,
        panY: midpoint.y - this.center.y - this.pinchStart.mapY * scale,
      };
      this.moved = true;
      return { ...this.viewport };
    }
    if (this.mode === 'pan' && this.pointers.size === 1 && this.panStart) {
      const dx = x - this.panStart.x;
      const dy = y - this.panStart.y;
      if (Math.hypot(dx, dy) > 4) this.moved = true;
      this.viewport = {
        ...this.panStart.viewport,
        panX: this.panStart.viewport.panX + dx,
        panY: this.panStart.viewport.panY + dy,
      };
    }
    return { ...this.viewport };
  }

  up(pointerId: number) {
    if (!this.pointers.has(pointerId)) return { select: false, viewport: { ...this.viewport } };
    const wasSuppressed = this.suppressSelection || this.mode === 'pinch' || this.mode === 'pinch-tail';
    this.pointers.delete(pointerId);
    const select = !wasSuppressed && !this.moved && this.pointers.size === 0;
    this.continueOrFinish();
    return { select, viewport: { ...this.viewport } };
  }

  cancel(pointerId: number) {
    if (this.pointers.has(pointerId)) this.pointers.delete(pointerId);
    this.suppressSelection = true;
    this.continueOrFinish();
    return { select: false, viewport: { ...this.viewport } };
  }

  private beginPinch() {
    const [first, second] = [...this.pointers.values()];
    const midpoint = middle(first, second);
    this.mode = 'pinch';
    this.suppressSelection = true;
    this.pinchStart = {
      distance: Math.max(1, span(first, second)),
      scale: this.viewport.scale,
      mapX: (midpoint.x - this.center.x - this.viewport.panX) / this.viewport.scale,
      mapY: (midpoint.y - this.center.y - this.viewport.panY) / this.viewport.scale,
    };
  }

  private continueOrFinish() {
    if (this.pointers.size >= 2) {
      this.beginPinch();
      return;
    }
    if (this.pointers.size === 1) {
      if (this.suppressSelection) {
        this.mode = 'pinch-tail';
      } else {
        const point = [...this.pointers.values()][0];
        this.mode = 'pan';
        this.panStart = { ...point, viewport: { ...this.viewport } };
      }
      return;
    }
    this.mode = 'idle';
    this.panStart = null;
    this.pinchStart = null;
    this.moved = false;
    this.suppressSelection = false;
  }
}

export function edgesAreBlack(pixels: Uint8ClampedArray, width: number, height: number): boolean {
  if (pixels.length !== width * height * 4 || width < 1 || height < 1) return false;
  const blackAt = (x: number, y: number) => {
    const offset = (y * width + x) * 4;
    return pixels[offset] === 0 && pixels[offset + 1] === 0 && pixels[offset + 2] === 0 && pixels[offset + 3] === 255;
  };
  for (let x = 0; x < width; x += 1) if (!blackAt(x, 0) || !blackAt(x, height - 1)) return false;
  for (let y = 0; y < height; y += 1) if (!blackAt(0, y) || !blackAt(width - 1, y)) return false;
  return true;
}

function middle(first: MapPoint, second: MapPoint): MapPoint {
  return { x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 };
}

function span(first: MapPoint, second: MapPoint): number {
  return Math.hypot(first.x - second.x, first.y - second.y);
}
