# DualDex 1.2.1

## Improvements

- Broader compiled Gen I core detection, including bank-local species indexes, verified far-copy/restart helpers and source-backed helper-bank consumers.
- Broader classic and compact Gen II core detection, with canonical species/stat joins, native type domains, stored move categories and bounded plain-name text authority.
- Corrected Gen II navigation and sprite-bank boundaries. Unproven optional maps, relationships, text and sprites remain unavailable rather than being invented.
- Parser cache revision 83 rejects stale revision-82 catalogs and rebuilds them before activation. The SQLite storage schema remains version 2.
- Updated compatibility documentation to the current library's 342 distinct ROM identities, all evaluated by one source-bound executable.

## Verified corpus results

- 342 inputs: 300 selected catalogs, 2 ambiguous families and 40 without a mainline-family match.
- All 300 selected catalogs persisted and reopened exactly, preserving their version-1 logical digest.
- Zero parser, catalog, persistence or decoded cross-reference errors; no regressions against the retained current-library evidence.
- 285 resolved ROM-language manifests; 15 explicitly unknown; no proven multilingual build.

Family detection does not promise that every optional feature is supported. Consult the current compatibility matrix for each scanned build. No ROMs, private memory captures or signing material are included.

## Release acceptance

The signed artifact must pass the exact-source GitHub release pipeline, including the reusable hosted packaged-Android acceptance gate, production package/version checks, pinned signing certificate and immutable public-asset validation. Passive-catalog promotion additionally requires five real-ROM cache-reopen, runtime/API and browser controls. No physical-device or local-emulator validation is claimed.
