# Thor UI Usability Remediation Design

Date: 2026-08-30

Status: approved specification

Evidence: `docs/testing/thor-critical-ui-usability-audit-2026-08-30.md`

## Goal

Remove the usability defects proven by the exact packaged-APK Thor audit while preserving the routes and compositions that already work.

The correction is intentionally staged:

1. restore access, readability, and reliable map interaction;
2. improve compact navigation, density, accessibility, and recovery;
3. close bounded polish issues and prove integrated conformance.

This is not a visual rebrand. The ROM-derived presentation, production data authority, and current application architecture remain unless this specification explicitly changes one UI contract.

## Authority and precedence

The exact audit is the defect authority for this work:

- physical window `1240×1080`;
- Android app area `1240×1025`;
- packaged WebView `538×445` CSS px;
- DPR `2.3062500953674316`;
- density `369 dpi`;
- font scale `0.95`;
- exact Modern Emerald `(v3.5).gba` in `DISCOVERED` mode;
- production WebView, parser/catalog cache, state server, decoder, and publication paths;
- debug-only sanitized memory transport.

The final evidence used the emulator-console multidisplay window, not Android's quarter-size overlay-display thumbnail.

This specification tightens and supersedes the affected contrast, compact-density, modal, accessibility, and verification clauses in `docs/superpowers/specs/2026-08-21-cross-page-ui-conformance-design.md`. Its passing page compositions remain authoritative where this specification does not say otherwise.

## Scope

### In scope

- Trainer Card/Progress destination reachability;
- contrast-safe semantic action colors for ROM-derived themes;
- Local, Atlas, habitat, and map-detail touch targets;
- POI clustering and dense-location selection;
- compact Area Guide scrolling and exit identity;
- functional Comfortable and Compact Pokédex density;
- compact Settings information architecture and touch sizing;
- shared Party/Specimen modal behavior;
- tabs, selected states, headings, and route focus;
- recoverable empty, failure, and background-feedback states;
- the bounded Battle, map-header, Party Analysis, and typography polish listed here;
- deterministic browser and packaged-APK verification at exact Thor geometry.

### Out of scope

- parser, catalog, SaveRAM, live-memory, battle-calculation, rarity, or map-raster behavior;
- changes to knowledge/discovery policy or revealing undiscovered labels;
- new map data, POIs, encounter data, or destination identities;
- Android display, overlay, docking, signing, or release architecture;
- controller or D-pad navigation inside the companion; controller focus remains with the game;
- redesigning the passing Pokédex detail, Battle Entry/Rarity, Party board, Trainer Card body, or Specimens card;
- publishing an RC, stable release, tag, or signed artifact;
- parser-corpus execution for UI-only changes.

## Product invariants

### `UI-INV-01` — Exact compact containment

At `538×445`, every ordinary screen and fixed header must satisfy `scrollWidth <= clientWidth`. Intentional map-plane transforms and clipped decorative pseudo-elements do not count as semantic overflow, but controls and semantic content do.

### `UI-INV-02` — Reachable touch interaction

Every visible touch action has an effective target of at least `44×44 CSS px`. No two simultaneously actionable targets may overlap. A smaller visual glyph is allowed inside a compliant wrapper.

### `UI-INV-03` — One primary vertical gesture owner

A compact route or drawer has one primary vertical touch-scrolling surface. A contained child scroller may exist only when the parent cannot also require vertical continuation through later content. The Area Guide may not require users to discover a narrow outer-scroll gutter around an inner list.

### `UI-INV-04` — Contrast-safe semantics

ROM-derived colors remain presentation inputs, not assumed semantic foreground/background pairs. Normal text must reach `4.5:1`; large text and non-text control boundaries must reach `3:1`. High Contrast remains an alternative, not a prerequisite for reading setup actions.

### `UI-INV-05` — Truthful discovery

Clustering, exit grouping, fallback actions, and accessible labels must preserve the active knowledge policy. Presentation may distinguish unknown entries by ordinal or relative position but must not reveal hidden names, types, items, services, or destinations.

### `UI-INV-06` — Fail closed without route loss

A failed optional UI module disables or replaces only that module with a bounded terminal/retry state. It must not crash the APK, remove unrelated navigation, publish invented data, or leave a permanent loading state.

### `UI-INV-07` — Passing views remain passing

The following accepted compositions remain regression gates:

- compact Pokédex detail: fixed app header plus shared identity/tabs/content scroller;
- one-column Pokédex browse in Auto/Comfortable density;
- Battle Entry and non-scrolling Rarity;
- two-column by three-row Party board;
- Trainer Card body and badge tray;
- Specimens nickname/species/source/level/rarity containment.

### `UI-INV-08` — Production authority remains unchanged

UI work consumes existing state and actions. It does not create a second source of game, map, Trainer, Party, battle, or knowledge truth.

## Stage A requirements — access, contrast, and map interaction

### `UI-NAV-01` — Trainer destinations

The Trainer header must show Card and Progress simultaneously at the exact compact viewport.

- Each destination has a distinct visible icon and accessible name.
- The selected destination is programmatically exposed.
- Neither control relies on horizontal scrolling or clipped overflow.
- Selecting either destination by touch changes only Trainer content.
- Title truncation is permitted before a destination is hidden.

Acceptance:

- Card and Progress bounds remain within x=`0..538`;
- each effective target is at least `44×44`;
- Trainer screen, header, header actions, and destination switcher close at `scrollWidth <= clientWidth`;
- direct touch tests prove both transitions.

### `UI-THEME-01` — Semantic color pairs

The Web UI must derive semantic foreground/background pairs independently from raw ROM token roles.

At minimum, expose contrast-safe pairs for:

- primary action;
- secondary action;
- selected control;
- neutral panel/surface;
- destructive action;
- status/error action;
- focus indicator and non-text boundary.

The derivation chooses a readable foreground for the actual resolved surface. It may use a theme-provided foreground only when it passes; otherwise it chooses a bounded dark or light neutral. Settings, Setup, Capability actions, dialogs, tabs, and map panels consume semantic pairs instead of assuming `--forest-*` is dark.

The existing raw theme tokens remain available for non-semantic surfaces, artwork, shadows, and borders.

### `UI-THEME-02` — Theme verification

Automated contrast checks must cover:

- the exact Modern Emerald tokens from the audit;
- bundled Game-theme fixtures;
- Dark, Light, and High Contrast;
- synthetic near-black, near-white, saturated, and low-separation token sets.

Acceptance:

- Settings and Setup action labels meet `4.5:1` under exact Modern Emerald;
- action/control boundaries meet `3:1` against adjacent surfaces;
- no normal theme requires High Contrast to expose an action label;
- semantic error and destructive colors remain independent from ROM decoration.

### `UI-MAP-01` — Effective map targets

Local POIs, Atlas locations, habitat markers, map detail/close controls, zoom/recenter controls, and any cluster control use at least `44×44` effective targets.

- Inner marker artwork may remain `11–26 px` where visual precision matters.
- Hit wrappers remain centered on the authoritative map coordinate.
- Panning, pinch/zoom, selection, discovery/fog, and recenter behavior remain unchanged.
- Player/avatar markers remain non-interactive unless already actionable.

### `UI-MAP-02` — Dense POI clustering

POIs whose effective hit regions would overlap must be represented by one cluster target at the current zoom.

Activating a cluster opens a compact, viewport-bounded member chooser. It may be a nonmodal anchored popover or a modal sheet, but it must not block map recovery or render outside the visible WebView:

- every member row is at least `44 px` high;
- the chooser preserves source order or a deterministic map-position order;
- selecting a member invokes the existing POI selection behavior;
- unknown members use policy-safe category/ordinal or relative-position copy;
- the chooser has an explicit close action;
- a modal implementation must satisfy `UI-MODAL-01` from its first stage rather than waiting for the later modal refactor;
- clusters split when zoom/spacing makes every member independently actionable.

Acceptance uses the nine Oldale POIs from the audit. Every POI remains individually selectable and no actionable target overlaps another.

### `UI-GUIDE-01` — Compact Area Guide scrolling

The drawer header and close control remain fixed. The compact guide body becomes the single vertical gesture owner from Overview through Objectives.

Implementations may use outer-scroll virtualization, expandable sections, or another bounded technique, but may not place a `280 px` contained list inside a parent that must continue to later sections.

Acceptance:

- a continuous swipe path reaches every populated section;
- no populated state requires swiping a narrow gutter;
- rendering remains bounded for large encounter/point sets;
- closing and reopening starts at the documented default position rather than a stale inner-list offset.

### `UI-GUIDE-02` — Distinguishable connected areas

Connected-area actions must be unique and meaningful.

- Repeated exits with the same destination identity are grouped into one action with an exit count.
- Distinct destinations with equal display names gain policy-safe differentiation such as direction or relative map position.
- Keys are unique; duplicate DOM keys are forbidden.
- Selecting a group retains the existing destination navigation result and does not invent topology.

The exact Oldale state may not show four indistinguishable `Oldale Town` buttons.

## Stage B requirements — compact workflow and accessibility

### `UI-DENS-01` — Functional Pokédex density

Pokédex row geometry is one shared value consumed by layout and virtualization.

- Auto and Comfortable retain the accepted `94 px` row at exact Thor geometry.
- Compact uses a `68 px` row.
- Compact preserves number, species identity, knowledge-safe types/status, and a readable portrait.
- Changing density recalculates virtual offsets without gaps, overlaps, stale scroll range, or row jumps beyond the nearest valid anchor.

Acceptance:

- exact browse viewport remains `538×254`;
- Comfortable exposes approximately `2.7` rows as today;
- Compact exposes at least `3.7` rows;
- long-list scrolling remains geometrically correct near the start, middle, and end.

### `UI-SET-01` — Compact Settings navigation

At compact width, Settings becomes a category index plus one category page rather than one `2085 px` document.

The index contains seven labelled destinations:

1. General — Game and Preferences;
2. Connection — RetroArch and Save Data;
3. Display — Display Mode, Presentation, and Companion Display;
4. Information — Information Policy, map details, move list, and battle tabs;
5. Accessibility — Readability and contrast;
6. Behavior;
7. Advanced — compatibility, reports, diagnostics, and maintenance.

Opening a category replaces the index with that category's existing controls. Back from a category returns to the index; back from the index exits Settings. A direct route may open a specific category/control for recovery actions.

Noncompact presentation may retain the existing full-page layout if all requirements still pass.

### `UI-SET-02` — Settings target size and hierarchy

- Every action, category row, select, display-mode choice, and toggle row has at least a `44 px` effective height.
- Routine, primary, read-only diagnostic, and maintenance/destructive actions use distinct semantic styles.
- `REMOVE UNUSED GAME DATA` cannot look identical to report/export actions.
- Focus indication surrounds the visible control, including visually hidden checkbox inputs.
- Category and control labels remain readable at supported font scales.

### `UI-MODAL-01` — Shared dialog primitive

Party detail, Specimen detail, POI cluster chooser, and future modal sheets use one shared modal contract:

- mount against the visible screen host, not inside a scrolling list;
- labelled `dialog` semantics and `aria-modal=true`;
- explicit visible close control of at least `44×44`;
- Escape closes when the WebView owns keyboard focus;
- Tab and Shift+Tab remain within the dialog;
- background content is inert to focus and assistive traversal;
- close restores focus to the triggering control;
- backdrop activation may close, but the backdrop is not the only close mechanism;
- long content scrolls inside the dialog while the close control remains reachable.

This keyboard behavior does not add controller/D-pad navigation.

### `UI-MODAL-02` — Scrolled Specimens stability

Opening a specimen from any list scroll position must display the dialog in the current viewport and preserve list position when closed.

Acceptance requires a synthetic multi-card collection long enough to scroll. Open the first, middle, and last visible cards after scrolling; each dialog is fully reachable and returns focus to the same card.

### `UI-A11Y-01` — Tab contract

Pokédex Detail and Battle tab sets follow one shared tab pattern:

- one tab stop for the active tab;
- Left/Right and, where rows wrap, Up/Down navigation;
- Home/End support;
- `aria-selected`, `aria-controls`, and labelled `tabpanel` association;
- disabled tabs remain announced and are skipped by activation;
- touch behavior and compact wrapping remain unchanged.

### `UI-A11Y-02` — Selected-state contract

Pokédex filters, battle targets, map selections, display mode, Trainer destinations, Progress tabs, and other exclusive controls expose state through the appropriate `aria-pressed`, `aria-current`, radio, or tab semantics. A visual class alone is insufficient.

### `UI-A11Y-03` — Route headings and focus

- Every route has exactly one programmatic `<h1>`; the existing visual title style may remain unchanged.
- Forward route navigation focuses the new heading or documented initial control.
- Back navigation restores focus to the trigger when it still exists.
- Live state refreshes do not steal focus.
- Map may not create a nested `main` landmark inside the application `main`.

### `UI-REC-01` — Actionable terminal states

Each terminal or recoverable state uses `message + reason + next action`:

- Move list not selected: direct action to Settings → Information → Move List;
- recoverable specimen load failure: Retry;
- unavailable habitat: Atlas/Area Guide action only when supported, otherwise explicit terminal copy;
- capability/setup failure: bounded Retry or reselect action;
- unsupported optional modules: remain disabled without blocking the rest of the route.

Retries are bounded and preserve the active catalog/session identity.

### `UI-REC-02` — Non-obscuring global feedback

Global loading and error feedback may not cover or intercept header destinations, map utilities, the Pokédex search dock, or modal close controls.

- Passive status is pointer-transparent.
- Actionable errors provide dismiss/retry without creating a full-width invisible hit layer.
- Persistent status occupies a reserved content/status region rather than overlapping controls.
- Alerts are announced once per state transition, not on every live poll.

## Stage C requirements — bounded polish

### `UI-POLISH-01` — Battle Attack fit

At exact authority and default font scale, the audited single-target Attack card fits the `538×203` content region without the `19 px` micro-scroll. Increased font scales may scroll vertically rather than clip.

Battle Entry and Rarity geometry must remain unchanged.

### `UI-POLISH-02` — Map-header collision resistance

The location title/current-state block reserves the centered clock region and trailing actions.

- Long names truncate before the clock rather than rendering beneath it.
- The clock remains centered.
- Header actions remain fully reachable.
- Exact tests include the longest available area names and synthetic overlength copy.

### `UI-POLISH-03` — Nearby-move card geometry

Party Analysis nearby-move cards use a dedicated two-content-column layout rather than inheriting the three-column evolution layout. Long member and move names remain readable or intentionally ellipsized with accessible full labels.

### `UI-POLISH-04` — Physical text floor

At the exact Thor geometry and normal font scale:

- visible text-bearing elements remain at least `11.2 px`;
- a route's average visible text size remains at least `12 px`;
- no implementation satisfies fit by reducing type below the floor.

At `85%`, `100%`, and `135%` application font settings, required controls and content remain reachable; larger text may increase vertical scrolling but not horizontal clipping.

## Architecture boundaries

### Theme derivation

Contrast derivation belongs in a pure companion-web utility invoked by `applicationThemeStyle`. It emits semantic CSS custom properties from the existing catalog theme without modifying catalog data. CSS components consume semantic variables according to control role.

### Map interaction

Clustering is a presentation projection over the existing POI list and viewport transform. Authoritative POI keys, coordinates, categories, states, and selection actions remain unchanged. The projection must be deterministic for the same POIs, viewport, and zoom.

### Settings navigation

Compact category selection is client UI state. Existing persisted setting values and Android deep links remain authoritative. Direct recovery navigation may add a client route/category target but must not duplicate setting storage.

### Modal ownership

The shared dialog primitive owns overlay placement and focus behavior. Party and Specimens continue to own their selected individual and route state. Closing a dialog invokes the existing page callback.

### Failure isolation

Contrast fallback, clustering failure, or an optional action failure must retain a usable conservative presentation:

- contrast derivation falls back to fixed black/white foreground selection;
- clustering failure retains one bounded aggregate target rather than overlapping members;
- unavailable fallbacks are omitted rather than rendered as dead controls;
- modal failure closes the overlay without changing underlying Party/Specimen data.

## Verification contract

### `UI-VAL-01` — Red/green focused tests

Every requirement receives a failing regression before implementation and a passing focused test afterward. Existing component and layout tests are extended rather than replaced.

Primary test surfaces include:

- `companion-web/src/App.production.test.tsx`;
- `companion-web/src/components.test.tsx`;
- `companion-web/src/layoutStyles.test.ts`;
- relevant page tests under `companion-web/src/pages`;
- `companion-web/e2e/ui-space-regressions.spec.ts`;
- `companion-web/e2e/map-presentation.spec.ts`;
- `companion-web/e2e/rom-derived-theme.spec.ts`.

### `UI-VAL-02` — Reusable packaged-WebView runner

Retain a host-side QA runner that attaches to the debug packaged WebView through CDP and:

- asserts `538×445` and the accepted DPR before capture;
- reads `/api/state` and rejects wrong ROM/knowledge/session authority;
- navigates only the debug QA package;
- records element bounds, scroll owners, overflow, contrast, active state, and screenshots;
- rejects a retained wrong tab instead of mislabelling its capture;
- accepts the CDP URL and output directory as inputs;
- contains no ROM, private path, credential, signing material, or personal raw-memory string.

The exact external ROM remains test input and is never added to the repository or APK.

### `UI-VAL-03` — Focused stage gates

A stage runs only tests related to its changed UI plus the production web build. The full parser corpus is prohibited unless parser, catalog, build-wrapper, or corpus-execution code changes independently.

### `UI-VAL-04` — Exact APK gates

- Stage A captures Trainer, Settings, Setup, Local Map, and Area Guide through the actual APK.
- Stage B captures Browse densities, Settings categories, Party/Specimen dialogs, and recovery states through the actual APK.
- Stage C performs the final compact matrix, including preserved views.

Browser evidence may establish red/green geometry, but cannot by itself close an APK requirement.

### `UI-VAL-05` — Preserved-view matrix

Final APK evidence must include:

- Pokédex Browse Auto/Comfortable and Compact;
- Pokédex Detail Entry and one long tab;
- Battle Entry, Attack, and Rarity;
- Party overview and detail;
- Trainer Card and Progress;
- Local Map, dense POI chooser, and Area Guide;
- Settings index, one category, Setup, and one recovery route;
- Specimens list and a scrolled-list detail dialog.

### `UI-VAL-06` — Stage evidence and closure

Each stage records:

- synchronized base and stage commit;
- requirement IDs;
- red/green tests;
- focused commands/results;
- APK screenshots/measurements required for that stage;
- preserved-view regressions;
- blockers and tracked referrals.

A requirement assigned to the current stage cannot be silently referred to a later stage. Newly discovered work needs an ID, target stage, dependency, and measurable acceptance condition.

## Release boundary

Each safe stage is smart-synced with `fork/master`, committed, and pushed to `fork/feat/retroarch-free-ui-qa`. No stage authorizes an RC or stable release. Candidate publication remains a separate decision and stable still requires the existing signed-candidate lower-display confirmation gate.
