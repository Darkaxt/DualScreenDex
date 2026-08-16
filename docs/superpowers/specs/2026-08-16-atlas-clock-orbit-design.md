# Atlas Clock Orbit Design

## Goal

Simplify the Atlas controls and make validated in-game time readable at a glance without inventing time state for unsupported ROMs.

## Atlas marker behavior

- Remove the top-left marker visibility button and its local UI state.
- Cyan Atlas nodes remain part of the standard Atlas view.
- `Organic` and `Hidden` continue showing only revealed locations through the existing fog/reveal policy.
- `Discovered` continues showing every parsed location.
- The multi-region chooser remains available only when a ROM exposes more than one world-map region.
- The `A`/`L` local-map switch remains separate and appears only when the current physical area has a rendered local map.
- Selecting an Atlas node identifies that map point in the existing header. It must not claim or open a local raster for a different, non-current area.

## Clock presentation

- Increase the existing centered clock typography by 20 percent in both shared application headers and the Atlas header.
- Add a compact elliptical orbit directly below the clock using the approved clean Option B treatment.
- Exactly one celestial icon is rendered at a time. There is no overlap or cross-fade:
  - morning and daytime render the sun;
  - nighttime renders the moon.
- The active icon moves continuously from the left horizon to the right horizon over its own active interval.
- At a validated phase boundary the outgoing icon disappears at the right horizon and the incoming icon appears at the left horizon.
- The orbit is decorative and non-interactive. It must not consume a toolbar action slot.

## Source authority and fail-closed behavior

- Android wall time is never used.
- A celestial icon is shown only when the live clock and its day/night boundaries are both derived from validated ROM/runtime evidence.
- The existing Modern Emerald clock resolver proves the source-defined night predicate (`00:00–05:59` and `21:00–23:59`), yielding sun from `06:00–20:59` and moon from `21:00–05:59`.
- The validated schedule travels through normalized runtime metadata and companion/API state. The web UI receives a normalized phase plus progress; it does not infer ROM-family rules.
- If a ROM supplies a valid clock but no validated schedule, the enlarged numeric clock remains visible and the orbit is omitted.
- If no valid game clock exists, neither clock nor orbit is shown, preserving current fail-closed behavior.

## Data flow

1. The parser stores validated day and night boundary hours beside the normalized live-clock address.
2. The Android runtime combines those boundaries with the decoded in-game hour/minute.
3. Companion state publishes normalized `DAY` or `NIGHT` phase and a bounded `0..1` progress value.
4. The shared web clock component renders the time and, when phase evidence is present, one sun or moon at that progress along the arc.

## Verification

- Exact Modern Emerald parser/runtime controls must prove the `06:00` day and `21:00` night boundaries from the existing decoded predicate evidence.
- Runtime tests cover `05:59`, `06:00`, `20:59`, and `21:00`, plus clock-without-schedule fail-closed behavior.
- Web tests assert a 20-percent typography increase, exactly one icon, correct sun/moon selection, bounded orbit position, and no icon without phase evidence.
- Atlas tests assert that the marker-toggle control is absent and policy-eligible markers remain rendered.
- Existing pan, pinch, fog, region chooser, local-map switch, Pokédex, and Settings behavior remain unchanged.
