# DualDex 1.1.0-rc.48

RC48 hardens performance and memory ownership throughout the companion while preserving RC47's parser schema and user-facing behavior. It is intended for live validation on the AYN Thor before the remaining device-memory measurements are closed.

## Faster unchanged-state paths

- Complete exact-ROM catalogs continue to bypass parsing without a parser-schema migration.
- Repeated ROM indexing reuses stable source identity, size, and modification metadata instead of reopening unchanged ROM contents.
- Repeated save polling avoids source reads, parsing, snapshots, persistence, and association lookups when the save has not changed.
- The companion reuses catalog projection contexts and returns an empty unchanged-state response instead of repeatedly serializing and transferring identical bodies.
- Live-memory polling avoids redundant decoding and publication work when the sampled state is unchanged.

## Bounded memory and I/O

- ROM ingestion, request uploads, and SQLite catalog sections are streamed in bounded chunks rather than copied wholesale into memory.
- Local-map raster residency is capped and reusable; superseded render assets are released.
- Mapper history retains at most 32 snapshots and 16 MiB of raw data, with append-only persistence compacted only when eviction is required.
- The loopback server admits at most eight simultaneous connections across four workers and rejects excess work cleanly.
- Only one companion WebView surface remains active; hidden or replaced surfaces pause and release their runtime resources.

## Large-list responsiveness

- Pokédex browsing virtualizes the 900-species control set and mounts no more than 60 result rows at once.
- Search, tab counts, scrolling, and sprite loading remain lazy and responsive without retaining the complete rendered list.

## Compatibility and validation

- The automated release gate covers official Gen I-III ROM controls plus Modern Emerald 3.5, Pokémon Unbound 2.1.1.1, and Pokémon Odyssey 4.1.1.
- Catalog persistence/reopen, local-map controls, runtime ABI controls, web presentation, Android tests, lint, dependency verification, and release-policy checks remain in the release workflow.
- Host-side stress controls found no out-of-memory termination during the bounded parser and catalog workloads.
- Android PSS, Java/native heap, GC, CPU, and renderer measurements remain part of the live RC48 validation rather than being represented as completed host-side evidence.

## Delivery

- RC48 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010048`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
