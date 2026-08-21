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
