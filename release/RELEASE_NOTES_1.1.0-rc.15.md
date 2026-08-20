# DualDex 1.1.0-rc.15

RC15 completes the approved DualDex 1.1 player-state and display-continuity plan while preserving
the passive, read-only companion boundary.

## Display continuity and recovery

- Handheld and External display targets now react to Android display add, change, removal, resume,
  and preference-change events through one deterministic policy.
- Auto mode never relocates the activity. Handheld and External move only when the eligible target
  is unique; ambiguous display sets remain where they are.
- Removing the active display triggers a safe re-evaluation on resume. A failed relaunch remembers
  the attempted target so it cannot loop, and a successful relaunch preserves the current local
  companion route.
- The implementation keeps one activity and one WebView. It adds no `Presentation`, service,
  cancellation timeout, manifest component, or game-input channel.

## Completed 1.1 player experience

- Trainer Card and Party continue to consume the normalized live-over-save state introduced in the
  earlier 1.1 candidates, including ROM-derived avatars/badges and catalog-resolved party details.
- The exact official Emerald vertical now freezes parser, SQLite reopen, sanitized SaveBlock/party
  decoding, runtime/API projection, and all ten normalized Trainer artwork PNGs in one regression.
- A raw enemy move present in live battle memory remains absent from the companion until the
  observation ledger proves it, preserving Organic-mode privacy.
- Exact official Red, Crystal, and FireRed controls retain their Pokédex, map, save-knowledge, and
  battle catalog features while correctly withholding an unsupported Emerald player descriptor.

## Retained RC14 fixes

- ZIP and 7z remain equal strict single-ROM containers.
- Large ROMs and SQLite catalogs keep the bounded streaming path that fixed the Pokemon Unbound
  loading crash.

## Verification

- Fresh affected Gradle gate: 51 tasks executed across save-core, battle-memory, parser-core,
  catalog-store, companion-core, and the Android app; build successful.
- Web gate: 21 files / 120 tests passed, followed by a successful production Vite build.
- Release metadata and workflow safety tests pass before the protected signing job.
- The signed APK is built and signed only by the protected GitHub release workflow. This task does
  not install or launch it on a device; user acceptance remains separate.
