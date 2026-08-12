# DualDex Loaded-ROM Capability Report Design

| Field | Value |
| --- | --- |
| Status | Approved product direction |
| Date | 2026-08-11 |
| Target | Next GitHub-signed release candidate |
| Placement | Existing Settings Debug section |

## Outcome

DualDex exposes the parser evidence for the currently loaded ROM inside the production APK. The existing Debug section gains one read-only `CAPABILITY REPORT` row beside `CAPTURE MEMORY REPORT`. Selecting it opens a dedicated page; it does not expand Settings in place and it does not add shortcuts to the Pokédex or Battle screens.

The report explains partial and unfinished ROM hacks without turning missing records into a whole-feature failure. A feature with credible structure and 70% usable records remains available as partial coverage. Invalid or absent records are omitted from the catalog, the exact observed coverage is shown, and the corpus workflow can flag the result for manual review.

The capability page is independent of the Memory Mapper switch and export consent. It never starts memory capture and requires no new Android permission.

## Settings contract

The bottom Settings group is labeled `DEBUG` and contains two separate actions:

- `CAPABILITY REPORT` opens the loaded-ROM report page;
- `CAPTURE MEMORY REPORT` opens the existing optional memory-evidence page.

The capability action is disabled with `NO ROM LOADED` when no catalog is active. While lazy parsing is in progress, it remains available for the last published evidence and shows the same loading phase and percentage as the companion header. Completed evidence replaces the page contents without closing the page.

## Dedicated page

The page uses the existing compact handheld shell and ordinary back-header pattern. Its first card identifies the active ROM by display name, platform, selected engine family, CRC32, abbreviated SHA-256, active ruleset, and whether the ruleset was assumed.

The main list follows `RomCapability` order. Every row contains:

- a human-readable feature name;
- one status: `AVAILABLE`, `PARTIAL`, `NOT FOUND`, `AMBIGUOUS`, or `N/A`;
- observed coverage when known, such as `1300 / 1301 records` and `99.9%`;
- a short materialized result when useful, such as `5,297 learnset entries`; and
- an affordance that opens that capability's details on the same page.

Rows remain compact by default. Expanded details show confidence, valid and total records, ROM offset in hexadecimal, record and element sizes, structural reasons, and parser diagnostics relevant to that capability. Missing values render as `N/F`; fields that do not apply render as `N/A`. The page never invents a count from a dash or silently presents partial evidence as complete.

Status is data-driven rather than inferred from display prose:

- `AVAILABLE`: structurally selected and complete for the reported table;
- `PARTIAL`: structurally selected and usable, but coverage is incomplete or the result is marked for manual corpus review;
- `AMBIGUOUS`: two or more credible layouts remain without a structural winner;
- `NOT FOUND`: no credible layout or usable evidence was found;
- `N/A`: the feature does not exist for that engine generation.

## Data contract

DualDex reuses the existing local `GET /api/diagnostics` endpoint and `DiagnosticView`; no network-facing service is added. The diagnostic capability DTO is extended with explicit valid-record, total-record, element-size, and review-status fields so the UI never parses reason strings to determine state.

Parser and catalog evidence remain the source of truth. The runtime publishes immutable diagnostic snapshots for the active catalog. Lazy-parser progress may replace the snapshot atomically, but it may not combine evidence from two ROM hashes.

The existing lightweight `Catalog.capabilities` map remains sufficient for ordinary screens. The detailed page fetches diagnostics only when opened or when the active catalog/progress marker changes.

## Copy report

`COPY REPORT` copies a stable JSON diagnostic document containing ROM identity, parser family, ruleset evidence, capability evidence, and parser diagnostics. It excludes ROM bytes, sprites, save contents, memory dumps, paths outside the user-facing ROM name, and knowledge history. Copy success or failure appears as an inline status on the page.

## Error and compatibility rules

- Partial records never crash page rendering or catalog materialization.
- A malformed capability entry is shown as unresolved while other feature rows remain available.
- If the active ROM changes while the page is open, the page atomically switches to the new ROM identity and evidence.
- If diagnostics cannot be loaded, the page keeps the ROM identity and shows a retry action; it does not redirect to the Memory Mapper.
- Existing stored catalogs without the new optional evidence fields reopen successfully and show the best status available from their current capability data.
- The Debug capability row and page are production features; WebView debug mode is not required.

## Verification contract

Automated tests cover:

- Settings placement beside the memory report and dedicated-page navigation;
- disabled no-ROM behavior;
- available, 70% partial, ambiguous, not-found, and not-applicable rows;
- explicit `N/F` versus `N/A` rendering;
- valid/total coverage and hexadecimal layout details;
- malformed-record omission without page or catalog failure;
- lazy-progress refresh without cross-ROM evidence;
- stable copy output and exclusion of ROM, save, memory, and private-path data;
- cached-catalog compatibility; and
- the existing memory-report flow remaining independent and unchanged.

