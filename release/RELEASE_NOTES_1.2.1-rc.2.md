# DualDex 1.2.1-rc.2

This candidate validates the compiled Gen I/II catalog improvement batch before stable 1.2.1. It is initially a private draft; no physical-device or local-emulator acceptance is claimed.

## Improvements

- Broader compiled Gen I core detection, including bank-local species indexes, verified far-copy/restart helpers and source-backed helper-bank consumers.
- Broader classic and compact Gen II core detection, with canonical species/stat joins, native type domains, stored move categories and bounded plain-name text authority.
- Corrected Gen II navigation and sprite-bank boundaries. Unproven optional maps, relationships, text and sprites remain unavailable rather than being invented.
- Parser cache revision 83 rejects stale revision-82 catalogs and rebuilds them before activation. SQLite storage schema remains version 2.
- Updated compatibility documentation to all 342 distinct current-library ROM identities, evaluated by one source-bound executable.

## Candidate gate correction

RC1's ordinary Windows CI exhausted its test heap while constructing a synthetic oversized-section string, before persistence was invoked. RC2 reuses a small string across enough diagnostics to exceed the same real 128 MiB section ceiling. The test additionally requires the exact section-limit exception, not an allocation failure. No production code, parser execution, cache schema or corpus inputs change; the verified full342 evidence remains source-bound to its original executable. RC1's immutable tag and signed draft assets are not replaced.

## Verified corpus results

- 342 inputs: 300 selected catalogs, 2 ambiguous families and 40 without a mainline-family match.
- All 300 selected catalogs persisted and reopened exactly with unchanged version-1 logical digests.
- Zero parser, catalog, persistence or decoded-reference errors; no regressions against the retained current-library evidence.
- 285 resolved language manifests; 15 explicitly unknown; no proven multilingual build.

Family selection does not promise support for every optional feature. Consult the current compatibility matrix for each scanned build. No ROMs, private memory captures or signing material are included.

## Acceptance requirements

The exact-source GitHub release workflow must pass the required hosted packaged-Android gate before protected production signing. Passive-catalog acceptance additionally requires five exact real-ROM cache-reopen, runtime/API and browser controls, successful source-bound CI, pinned certificate verification and the complete immutable public-asset set. The candidate is not signed locally and must not be substituted or rebuilt during candidate promotion.
