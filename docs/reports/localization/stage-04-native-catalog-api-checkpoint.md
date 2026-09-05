# Stage 4 native catalog and API checkpoint

Status: **nine exact native controls pass bounded catalog/cache/API acceptance; not Stage 4 closure**.

## Scope

Tasks 382–384 connect the previously published native language authority to species descriptions, native GBA abilities, and applicable map labels. Seven Japanese family controls and independently pinned Korean Gold and Silver execute separate JUnit cases. Together they cover eight native language-family cells. SHA-256 values fence external test inputs only; production resolution does not use identities, filenames, titles, hashes, or fixed ROM offsets.

`WorldMapCatalogApiRealControlTest.nativeOfficial*` uses the production catalog parser, actual SQLite write/close/reopen, and cache-only `ProductionCompanionRuntime` bootstrap. No ROM payloads or raw cache artifacts are included in the repository.

## Corrected boundaries

### Native species-description layouts

- Gen I Japanese descriptions have three metadata bytes and inline prose, rather than the Western far-pointer layout.
- Gen II Japanese consumers prove displaced pointer segments; Korean consumers prove 128 entries per bank. Three-byte metadata preserves the first Korean multibyte prose token.
- Native GB prose decoding is bounded by record, pointer-table, and bank authority. Contextual DEXEND handling does not weaken the global codec.
- Native GBA descriptions use consumer-proven 28-byte records, a contextual zero-terminated category, dimensions at +6/+8, and page pointer at +12. Inherited and discovered layouts require the compiled stride proof.
- Scalar description metadata survives exact snapshots and existing catalog serialization. No SQLite production redesign was needed.

### Separate description-row authority

The first post-layout acceptance run exposed a real join defect: public `dexNumber` intentionally used regional numbering, but the catalog treated it as the description row. Bulbasaur received Girafarig prose and dimensions while Treecko received Bulbasaur prose. The expected native fragment was correct; this was not a decoding or persistence defect.

Public regional mapping A remains unchanged. A separate immutable mapping B is tied to the selected description table through a compiled mapping-wrapper return consumed by its dimensions accessor. Composition evidence constrains eligible maps but cannot alone establish the role. The whole description record, including dimensions, and semantic coverage use the same row authority. There is no internal-species-ID arithmetic workaround.

Missing or conflicting role evidence withholds B without borrowing regional numbering. Description-only binding budget exhaustion preserves independently established A; overall species-discovery budgets still apply. Session cancellation reaches both semantic-domain and coverage binding. Equivalent mapping roots retain their consumer evidence, while distinct semantic maps remain ambiguous. The proven wrapper's push/pop/return frame must agree.

### Native GBA abilities

Strict native dash-only sentinel recognition preserves exact codec tokens and padding rules. Complete shift-based consumers prove name indexing; separately compiled inline-description arithmetic proves its stride and bounds the adjacent name table. Architecture-defined header data cannot masquerade as executable text consumers. Mixed shift/multiply consumers cannot bypass missing or conflicting extent proof.

Inline descriptions are not interpreted as pointer arrays. Two legitimate five-glyph descriptions had failed an eight-character prose minimum, disabling the bounded table and names. The compiled-inline path now accepts those short descriptions while retaining termination, script, invalid-token, control, and zero-padding checks. Existing Western pointer-description paths remain intact.

### Native GBA map labels

Ruby's loader contained literal data whose halfword resembled a conditional branch. Proven LDR-loaded words are excluded from branch interpretation only when a forward jump skips the complete word; genuine opposing branches remain distinct.

FireRed/LeafGreen's separate section-name pointer table uses instruction-proven bias, range, and root. The same bounded lookup supplies World and Local labels. Missing, malformed, unavailable, or ambiguous names do not replace independently proven numeric geometry. A referenced word without jump-over proof cannot hide an executed index overwrite.

## Regression and review dispositions

Behavioral failures were observed before the corresponding fixes. The initial native gate failed all nine controls. Two initial test-oracle errors were corrected separately: regional Dex number 1 was not a National Bulbasaur selector, and a Gen I Local town label was not a World encounter-point label.

After the first implementation batch, six controls passed; the three Japanese GBA controls still failed description fragments and ability text. Their persisted outputs and compiled consumers established the mapping and short-prose causes above. All nine map-label boundaries already passed that intermediate run.

Bounded independent reviews identified four concrete defects, each subsequently reproduced and corrected:

1. Referenced words could hide executed index overwrites.
2. Mixed shift/multiply consumers bypassed independent name extents.
3. Mapping wrappers with incompatible saved-register frames appeared to return to the description caller.
4. Content deduplication discarded equivalent-root binding consumers.

Owner self-audits additionally exercised budget isolation, actual semantic cancellation, and divergent-map fallback. The coordinator inspected the final wrapper/equivalent-root corrections and ran the fresh combined gate below; no additional full review round was performed after those two corrections.

## Independent coordinator verification

The final combined gate used `--rerun-tasks` across mapping, semantic coverage, native and Western description/name/ability compatibility, map resolvers, authority, cancellation, and snapshot persistence tests.

- **460 cases across 29 classes: 459 passed, one skipped, zero failures/errors.**
- The single existing optional skip was `FamilyProbeCoordinatorTest.semanticPartialPromotionPreservesValidatedCloudWhiteTwoDescriptionAuthority`, whose external control was not configured.
- Gradle completed in **1m26s**, with all 11 actionable tasks executed and fresh compilation.

The subsequent strengthened native gate used:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.darkaxt.dualdex.web.WorldMapCatalogApiRealControlTest.nativeOfficial*' \
  --rerun-tasks --no-parallel --console=plain
```

The external native-control and private temporary-output directories were supplied through environment variables. Result: **9/9 passed, zero failures/errors/skips**; JUnit execution time **206.203 seconds**, Gradle **5m11s**, all 40 actionable tasks executed. Source hashes matched the retained combined-gate snapshot exactly. Existing compiler and Gradle deprecation warnings remain.

Publication hygiene subsequently normalized `NativeDescriptionAbiTest.kt` from CRLF to LF. Byte comparison against the tested/staged version verified that only line endings changed; the gates were not repeated for that normalization. Production source remained unchanged.

Every control passed exact native codec/single-overlay authority, independent sampled species/move/description/type/map labels, shared-text isolation, all 15 capability inventory checks, actual SQLite whole-catalog equality/section inventory/integrity, and cache-only API startup with `ROM_DEFAULT` and zero parser invocations. GBA controls additionally passed independently decoded Bulbasaur dimensions 7/69 and exact ability 65 name/prose in both catalog and API.

| Controls | Description coverage | Ability name/prose coverage | Joined gate |
|---|---:|---:|---|
| Japanese Red/Blue lineage and Yellow | 151/151 each | Not applicable | Pass |
| Japanese Gold/Silver and Crystal | 251/251 each | Not applicable | Pass |
| Korean Gold and Silver, separately executed | 251/251 each | Not applicable | Pass |
| Japanese Ruby/Sapphire, Emerald, FireRed/LeafGreen | 386/411 each | 77/77 each | Pass |

Coverage ratios are observed diagnostics, not new production constants. The native GBA 386/411 description denominator remains separate from the corrected join: this checkpoint does not claim every counted slot has a description. Independent samples plus whole-catalog parity are not a complete field-by-field semantic oracle.

## Remaining Stage 4 gates

- `LNG-B001`–`LNG-B003`: ratify the complete current 43-cell official matrix, including all 35 Western controls and nine native exact inputs; finish applicable coverage/denominator audit without waiving required content.
- `LNG-D005`: native official forecast acceptance remains explicitly `NOT_RUN`; the sampled catalog/reopen type-semantic gate does not substitute for it.
- Run the final source-bound current corpus only after all Stage 4 executable changes are final, audit the ledger, and publish `stage-04-closure.md`.

No required official cell is deferred. Stage 5 stays blocked. No full corpus, deprecated closure inputs, device/emulator/ADB, signing, release APK, ROM publication, or cleanup action is part of this checkpoint.
