# Stage 4 native name geometry and authority checkpoint

Status: **verified bounded native name geometry and language authority; not Stage 4 closure**.

## Scope and authority

This bounded Task 381 / `LNG-B001` checkpoint connects native compiled name-table geometry to exact-codec language corroboration. It does not infer geometry from language, titles, filenames, hashes, or region bytes.

- GB source stride, repeated-add indexing, destination copy length, appended destination terminator, and banking sequence must agree. Japanese Gen I Red/Blue-lineage inline banking and Yellow's validated banking helper distinguish their codec trials.
- Gen II compiled name consumers and referenced far-pointer pairs support both five-byte Japanese and ten-byte Korean fixed species records at ordinary numeric-domain counts. Missing native consumers cannot fall back to a guessed ten-byte pointer layout.
- GBA complete script-buffer consumers prove six-byte species and eight-byte move indexing. The species/move root relationship and bounded complete rows establish the recovered table geometry. Ruby/Sapphire lineage roots or the later published name-pointer pair must independently agree with the selected roots; the shared arithmetic alone cannot select a dialect.
- Native corroboration requires separately selected species and move tables, exact codec applicability, script agreement, and distinct lexical evidence. At least three distinct species names, six distinct move names, and 18 distinct matched native trigrams are required, with at least 0.35 coverage and 0.08 competing-language margin. Kana normalization is lexical-only; catalog spelling is unchanged.
- Lexical sampling is bounded to 128 records per table. Variable tables still traverse every declared record to reject malformed tails. Records are decoded as complete tokens, including Korean multibyte units. GB traversal cannot cross the originating 16 KiB bank. Valid full-width GB glyph records may omit an in-row terminator; controls, substitutions, invalid units, and truncated tokens cannot use that exception.
- Conflicting independently corroborated codec/table authorities remain `AMBIGUOUS`, without a default or duplicate Japanese projection. A preferred/header native codec cannot regain authority when no native candidate resolved.
- Published numeric roots survive conflicting name roots. Native text failure does not supply type semantics or another language's text.

Parser schema revision 49, catalog schema 2, CLI report schema 14, and codec IDs/versions remain unchanged. The only codec-table extension adds the source-proven Gen II Japanese gender glyphs `0xEF = ♂` and `0xF5 = ♀`.

## Source and exact-control evidence

Public source oracles:

- [Japanese Gen I move-name lexical source](https://github.com/luckytyphlosion/pokered-jp/blob/258d1a89ec49a2a0ccfbdd232ac0e5d96d00899a/text/move_names.asm).
- [Korean Gen II move-name lexical source](https://github.com/Narishma-gb/pokegold-kr/blob/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4/data/moves/names.asm).
- [Japanese Gen II glyph oracle](https://github.com/scr-trees/pokegold_jpcrystalvc/blob/f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm).

Compiled geometry was inspected in nine SHA-256-pinned external controls. Hashes fence laboratory inputs only; production resolution does not use them. Public tests construct synthetic consumers/tables and do not bundle ROM payloads.

| Exact control | Expected codec | Observed species geometry | Observed move geometry |
|---|---|---|---|
| Japanese Green Rev 1 | `gb-gen1-ja-red-blue` | 190 × 5 bytes | 165 variable records |
| Japanese Yellow Rev 3 | `gb-gen1-ja-yellow` | 190 × 5 bytes | 165 variable records |
| Japanese Gold Rev 1 | `gb-gen2-ja` | 251 × 5 bytes | 251 variable records |
| Japanese Crystal | `gb-gen2-ja` | 251 × 5 bytes | 251 variable records |
| Japanese Ruby Rev 1 | `gba-gen3-ja-ruby-sapphire` | 412 × 6 bytes | 355 × 8 bytes |
| Japanese Emerald | `gba-gen3-ja-emerald-frlg` | 412 × 6 bytes | 355 × 8 bytes |
| Japanese FireRed Rev 1 | `gba-gen3-ja-emerald-frlg` | 412 × 6 bytes | 355 × 8 bytes |
| Korean Gold | `gb-gen2-ko` | 251 × 10 bytes | 251 variable records |
| Korean Silver | `gb-gen2-ko` | 251 × 10 bytes | 251 variable records |

Nine exact controls cover eight native language-family matrix cells; Korean Gold and Silver are independently executed controls within one cell. Counts above are observed diagnostics, not new production constants. The opt-in test asserts each input hash, selected family, resolved exact codec, and native record widths through the public `ParserOrchestrator.analyze` entrypoint. It does not assert complete catalog coverage.

## Root cause, regressions, and review

Initial synthetic geometry and authority gates exposed unsupported native widths and Western-only corroboration. The first exact parser-entrypoint run executed nine controls: seven passed; Japanese Gold remained language-unknown and Japanese Crystal failed family selection. Valid full-width Nidoran names ended in the two missing Gen II gender glyphs. Their pinned charmap mappings supplied the narrow correction; record validation was not weakened.

An independent pre-fix read-only review found two further defects:

1. A wrong-family native consumer could regain dialect authority through the preferred/header-codec fallback.
2. Native variable-name sampling/counting could consume the following physical GB bank.

Both review findings and the glyph omission were reproduced as fresh assertion failures before correction. Regressions exercise the actual identity/core path with a contradictory Red title over a Yellow consumer, a move sequence crossing a 16 KiB bank boundary, and full-width gender-bearing Gen II names. The coordinator inspected the subsequent fixes and tests; the original reviewer did not re-review them.

The implementation owner reported **88 synthetic/regression tests across 12 suites, zero failures**, followed by **nine exact controls executed with zero failures/errors/skips**. The latter run replaced the owner's synthetic XML/HTML reports; the 88-case result is owner-reported rather than separately retained durable output. No production changes occurred between those two gates.

## Coordinator verification

The independent coordinator combined gate completed with `--rerun-tasks` and the opt-in external native-control directory. It ran `text.*Test`, `validate.*Test`, `RomLanguageAuthorityTest`, `NativeRomLanguageAuthorityTest`, `NativeNameLayoutIntegrationTest`, `FamilyProbeCoordinatorTest`, `OfficialLanguageResolverTest`, `Gen2CompiledNamePairResolverTest`, `Gen3CompiledNameGeometryTest`, `NativeCompiledNameGeometryTest`, `GbaPublishedHeaderResolverTest`, `NativeOfficialLanguageLiveRomTest`, `CompiledTypeNameResolverTest`, `RecordMaterializersTest`, and `CatalogParserTest`.

Result: **374 cases across 24 classes: 373 passed, one external-control skip, zero failures/errors**. The skip requires `DUALDEX_CLOUD_WHITE_2_ROM`, which was not supplied. All nine exact native controls executed and passed; their diagnostics agree with the geometry table above. Gradle reported **BUILD SUCCESSFUL in 2m25s**, with all five actionable tasks executed, including fresh source/test compilation. Existing compiler and Gradle deprecation warnings remain. XML evidence was retained locally before the supplemental run.

The supplemental fresh gate passed **18/18 cases across three classes, with zero failures/errors/skips**: `Gen1CompiledTableResolverTest` (14), `ParserOrchestratorLanguageAuthorityTest` (one), and `PublishedUnifiedFallbackIsolationTest` (three). It used `--rerun-tasks`; Gradle reported **BUILD SUCCESSFUL in 59s**, with all five actionable tasks executed. Together the independent gates cover **392 cases: 391 passed, one external-control skip, zero failures/errors**. `git diff --check` passed.

No official persistence/API acceptance is implied by parser-entrypoint or synthetic materializer success.

## Mandatory remaining work

`LNG-B001` now has bounded native candidate/geometry/corroboration evidence; the official Western/native combined matrix still requires ratification. `LNG-B002` requires full official native names, descriptions, applicable locations, and independent numeric-survival acceptance. `LNG-D005` remains open for native decoded type semantics through required official materialization/cache/forecast boundaries. `LNG-B003` requires the complete sanitized 43-cell matrix, parse/materialize/persist/close/reopen/API-overlay acceptance, one final source-bound current-corpus run after Stage 4 executable changes are final, ledger audit, and published `stage-04-closure.md`.

No required official cell is deferred. Stage 4 remains open and Stage 5 stays blocked. No full corpus, deprecated closure inputs, ROM publication, private memory, signing, release, installed APK, emulator, physical-device, ADB, or cleanup action is included.
