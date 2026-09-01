# DualDex 1.1.0-rc.85

RC85 is the signed release candidate for the completed Thor header consistency pass. It supersedes the unpublished RC84 tag and preserves the full lower-display remediation while making every applicable root destination identify itself with the established active red treatment.

## Active destination headers

- Mark Pokédex, Party, Local Map, and Settings as the current root destination with the same semantic selected palette used by Trainer Card and Progress.
- Keep current destinations noninteractive and expose them with `aria-current="page"`; navigation and utility actions remain unselected.
- Render the selected Party Poké Ball conventionally with a red upper half and white lower half.
- Preserve the existing Trainer Card/Progress switching behavior and authoritative in-game clock placement.

## Thor presentation

- Retain readable semantic shortcut colors over ROM-derived header themes.
- Keep Party names and HP values within compact card bounds without redundant species labels.
- Refresh the affected README screenshots from the packaged Android WebView at the exact Thor app-area viewport.
- Preserve the release/debug isolation boundary: no ROM, QA marker, simulator control, or production signing material is included in public assets.

## Measured validation

- Focused active-destination suite: 6 files and 100 tests passed.
- Companion TypeScript and production Vite build passed.
- Packaged Thor WebView scenarios passed for Pokédex, Party overview/detail, Settings, Local Map, and Trainer Card.
- Refreshed public screenshots remain `1241×1027`; all 48 public image assets passed binary-safe privacy validation.
- Pull request CI, CodeQL, public Chromium acceptance, and packaged Android managed-device acceptance passed; the previously timed-out asynchronous storage projection completed under the hardened condition-based deadline.

## Delivery

- This candidate uses Android version code `1010085`.
- The candidate is built and signed only through the protected GitHub Actions environment; production signing material is never exposed to the repository or local workspace.
- DualDex remains read-only and sends no game commands or emulator-memory writes.
