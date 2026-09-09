# Stage 4 — Canonical logical catalog digest

Date: 2026-09-09

## Scope

Task 426 supplies Task 425's logical reopen-parity primitive: public `CatalogLogicalDigest.sha256` and `writeCanonical` over the **actual persisted catalog DTOs**. The existing `CatalogSectionCodec` section switch is shared through a typed visitor; ordinary storage encoding remains unchanged. Parser/cache revision **64**, SQL schema **2**, codec versions **1**, CorpusReport **14** and receipt schema **1** are unchanged by this checkpoint.

Digest format **1** binds ROM SHA/CRC, family/platform, parser/storage schemas and every section in the dynamic storage plan. It excludes SQLite/source metadata, timestamps, paths, gzip bytes and fields not represented by persistence DTOs. This is logical payload evidence, not semantic correctness, whole-input uniqueness or a replacement for catalog equality.

Canonical output has explicit nulls, Unicode scalar-sorted object keys, exact UTF-8 strings without Unicode normalization, and versioned plain finite-decimal formatting with negative zero normalized to zero. It is **not** a general RFC 8785 or Python float-format implementation. The independently pinned normalized Unicode vector agrees with `official_matrix.canonical`; that limited agreement does not establish universal number-format equivalence.

Only unordered persisted domains are normalized: four Set fields (`encounters.windows`, runtime `areaBaseIds`, world-location `baseAreaIds`, theme `assetClasses`) and two overlay map-derived lists (`localizedCapabilities`, `worldLocationNames`). Other sequence order remains significant. This inventory must be reviewed when storage models change.

Raw and canonical sections are each bounded to **32 MiB**, total canonical output to **128 MiB**, and nesting to **64**. Work is section-at-a-time; a bounded JSON input is not a byte-exact heap limit. The streaming API leaves the supplied output open. On failure callers must discard partial output; no successful digest is returned.

## Verification

The isolated first RED attempt exposed invalid fabricated metatile geometry rather than the missing feature. After fixing only the fixture, genuine RED executed ten tests and failed all ten on missing facade/canonicalizer assertions. The unchanged ten tests then passed in 49.140 seconds, with zero failures/errors/skips and all 802 source pins unchanged.

Tests cover independent canonical bytes, Unicode/nonfinite/depth rejection, identity/schema/section bindings, map/set insertion-order invariance, numeric/localized/contextual-disposition mutations, meaningful sequence order, immutability, exact output bounds and **real synthetic SQLite persist-close-reopen parity** independent of source metadata.

Independent read-only review found no verified correctness defect. It checked all 23 listed source/artifact hashes, reversed the reader patch to the RED source pin, confirmed the normalization inventory against the persisted DTO graph, inspected real JDBC close/reopen test paths and independently reproduced the 68-byte normalized Unicode vector. It did not claim fresh JVM execution or final original-control acceptance.

After exact-byte integration into parent `5e3d394581d3ac578cec0575b015b18e80ad1647`, one fresh owned offline Windows Job ran the ten digest tests plus five explicitly inspected existing synthetic storage regressions. The latter cover write-inflate bounds, persisted language-manifest validation, immutable manifest restoration, dynamic overlay reopen and populated production-section round trips.

| Boundary | Result |
|---|---:|
| Actual JUnit methods | **15 passed, 0 failures/errors/skips** |
| Classes | **2: digest 10, existing storage 5** |
| Elapsed | **249.811 seconds** |
| Gradle | **exit 0, eight tasks executed, no timeout** |
| Parent source pins independently rehashed | **807 unchanged** |
| Actual XML files independently rehashed | **2** |
| Original selectors / reads | **none / 0** |

The gate used private outputs/cache directories, two workers, one test fork and a 900-second owned-process bound. Parent HEAD and sources stayed frozen. The coordinator inspected the complete log and actual XML, reconstructed the exact 15-method inventory and reverified the before/after source snapshots. Existing compiler warnings were not changed.

| Retained evidence | SHA-256 |
|---|---|
| Reviewed three-file patch | `e5c1c617016b529475801857f097199f5c55167bd7b9810fa7da31363a349f1a` |
| Parent gate result | `b24f959dd02a7f165f607e6a76992068026df2808546ebd2b9fd7da7b666bba7` |
| Parent digest XML | `295a4a8a758f9ec6ea9950c0e7c0b2bddae8ad09f3d1d61b71364f2cfcfe1ede` |
| Parent storage XML | `1978d859788559f5e81b6d46a88de9cf095b4ebd25d141c4bfab313898301bc3` |
| Coordinator verification | `54e32f6a7c8ef3a414414003e0e97ee430acdac2a0a1244847ebb85c135a40c6` |

Private paths, database payloads and raw input data are not published.

## Remaining mandatory work

Task 426's facade implementation closes here. Task 425 remains **OPEN** for CLI/app integration, packaged provenance, same-image header/compiled-structural observations, measured projection/type evidence and canonical API-envelope/report/oracle binding. The [cache-only API bridge](stage-04-cache-only-api-checkpoint.md) and [codec exporter](stage-04-codec-evidence-checkpoint.md) remain tooling, not final stable-source captures. Task 427 owns the next same-image header observation subcomponent; its unpublished report-version proposal does not change this checkpoint's version claims.

Native runtime pinning and GBA title/applicability obligations remain open. No existing consumed reservation is reopened. All **43 cells / 44 controls / 308 checks / 660 capability dispositions** and the final eligible corpus remain mandatory. **Zero final stable-source controls are accepted. Stage 4 remains OPEN; Stages 5-6 remain blocked.** No original acquisition, corpus run, device/emulator/ADB action, APK, signing or release occurred for this checkpoint.
