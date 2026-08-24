# Source-backed Gen III Compatibility Design

**Status:** Approved for implementation from the characterized Stage 2 source/ROM matrix.

**Goal:** Add one generic integrated Gen III `NatureInfo` ABI that resolves Battle Theater's exact source-backed release and an independent Dreamstone compiled sibling while preserving every existing Nature and map behavior.

**Primary evidence:** `docs/reports/2026-08-24-source-backed-gen3-family-audit.md`

## Scope

Checkpoint 1 changes only Nature resolution, focused controls, parser-cache invalidation, and compatibility evidence. It does not change family routing, map navigation, Local/Atlas precedence, scene topology, discovery/fog, POIs, APIs, UI contracts, or runtime memory decoding.

The existing separate Nature ABI remains authoritative for official Emerald/FireRed, Modern Emerald, Classic, Unbound, Odyssey, and any other ROM that proves it. The new integrated ABI is additive and uses the existing `NatureCatalog`/`NatureRecord` model, catalog persistence, API, and UI unchanged.

## Authority rules

- Public source is a structural oracle. Raw compiled ROM bytes and their compiled consumers are production authority.
- Production code must not contain project names, ROM filenames, hashes, source paths, fixed table roots, per-ROM profiles, or hack allowlists.
- A source tag or engine-family label never makes a candidate eligible.
- All scans remain bounded by the existing reference index and function-analysis limits.
- Zero eligible integrated candidates is not an error; the existing ABI decides the result.
- More than one eligible root or more than one simultaneously proven ABI returns `NatureResolution.Ambiguous`.
- A failed Nature resolver suppresses only Nature data. Maps, scenes, Local-first navigation, Atlas fallback, other catalog modules, and app startup remain available.

## Integrated ABI

A candidate root is eligible only when all table and consumer requirements pass.

### Table proof

The ROM table must be four-byte aligned and contain exactly 25 consecutive 20-byte records:

| Record byte | Meaning used by DualDex |
|---:|---|
| 0..3 | GBA pointer to a terminated ROM-native Nature name |
| 4 | raised stat ID |
| 5 | lowered stat ID |
| 6..19 | structurally bounded but not interpreted by this checkpoint |

Every name must pass the existing GBA text rules, be nonblank, and all 25 names must be distinct. The stat fields must equal the canonical 5×5 matrix in ROM-native order:

- raised IDs: five `1`s, five `2`s, five `3`s, five `4`s, five `5`s;
- lowered IDs: `1,2,3,4,5` repeated five times.

Equal IDs produce a neutral five-zero modifier row. Otherwise `(raisedId - 1)` becomes `+1` and `(loweredId - 1)` becomes `-1`. Percentages are 110 and 90. Integrated records do not prove flavor affinity, so `flavorModifiers` and `flavorTableOffset` remain null. `nameTableOffset` and `statTableOffset` both identify the integrated root because both fields belong to one ABI.

### Compiled-consumer proof

At least one complete Thumb function containing a compiled reference to the candidate root must prove all of:

1. 20-byte record indexing, including the observed equivalent of `((id << 2) + id) << 2` or a direct multiply by 20;
2. unsigned byte loads from offsets 4 and 5 of one record base;
3. comparisons covering field equality and selection against the requested stat;
4. multiplication and division shapes retaining the 110, 90, and 100 factors.

The proof is tied to the nominated instruction site for that root. Table shape without compiled consumption is insufficient.

## Resolver composition

`Gen3NatureResolver.resolve` will compute the integrated candidates and the existing separate-table resolution from the same `RomAnalysisSession`:

- one integrated candidate plus an unavailable separate ABI resolves the integrated catalog;
- no integrated candidate returns the existing separate result unchanged;
- one integrated candidate plus a resolved or ambiguous separate ABI is ambiguous;
- multiple integrated candidates are ambiguous, including any proven separate candidate;
- reference-index overflow remains `BudgetExceeded` before either ABI is accepted.

The current separate-table implementation and reduced FireRed 11/10 and 9/10 consumer proof are not weakened.

## Files and implementation sequence

1. Modify `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/Gen3NatureResolver.kt`:
   - isolate the current separate-table path;
   - decode and validate integrated candidates;
   - add exact Thumb consumer predicates;
   - compose the two ABI outcomes fail-closed.
2. Extend `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/Gen3NatureResolverLiveRomTest.kt` with:
   - exact Battle Theater 2.3.0 and Dreamstone ROM controls;
   - 25 unique names;
   - canonical neutral/raised/lowered rows;
   - 110/90 percentages;
   - null flavor evidence;
   - integrated root assertions kept only in tests.
3. Bump `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt` from 36 to 37 so existing cached catalogs rebuild once and can publish newly resolved Nature data.
4. Update the Stage 2 audit and release notes only after focused validation establishes actual outcomes.

## Validation gates

Validation is chunked rather than repeated after every edit:

1. Run the focused Nature resolver live-ROM class once after the resolver and tests form one logical chunk. It must pass Battle Theater, Dreamstone, official Emerald/FireRed, Modern, Classic, Unbound, and Odyssey.
2. Run catalog schema/persistence tests affected by schema 37.
3. Build the parser CLI once and scan the eight-ROM characterized matrix with one job. Require zero parser errors and zero decoded cross-reference errors. Battle Theater must resolve 23/23; Dreamstone must add Nature data; accepted capabilities in all eight controls must not regress.
4. Audit this checkpoint against this document. Any gap receives a stable blocker or deferral ID with target, safe fallback, and concrete acceptance condition.
5. Fetch `fork/master`, inspect divergence and overlapping paths, rebase or merge deliberately, rerun only overlap-relevant tests, then commit and push the tested checkpoint to the feature branch and `fork/master` without force.
6. Publish the next signed prerelease only through the protected GitHub release workflow. Verify tag-to-commit, checksums, package/version, pinned signer certificate, v3 signature, and provenance. Do not perform ad hoc ADB or emulator validation.

## Blocking and deferred work

Checkpoint 1 is blocked only if either primary compiled control cannot be proven generically, an existing Nature control regresses, candidate ambiguity is accepted rather than rejected, or an unrelated module fails because Nature resolution failed.

`G3-CELIA-001`, `G3-DREAM-001`, `G3-ELITE-001`, `G3-GSC-001`, `G3-POKESCAPE-001`, `G3-TOURMALINE-001`, and `G3-VOYAGER-001` remain tracked deferrals in the family audit. Their safe fallbacks and acceptance conditions are unchanged. Dreamstone's Nature gain is in scope; its other gaps remain under `G3-DREAM-001`.
