# Stage 4 native description-slot checkpoint

Status: **description-only applicability independently verified: 201 passed, six existing opt-in skips, zero failures/errors**. This closes Task 385's 75 disputed slot instances, not Stage 4 or FireRed move-prose acceptance.

## Authority and scope

The Japanese Ruby, Emerald and FireRed controls each contain 25 species slots whose separately proven description-row mapping B points outside the description table. Public regional mapping A is not a description selector. Previously these slots inflated description coverage to 386/411 despite having no applicable description record.

The existing compiled mapping-wrapper/accessor binding establishes B's role. New bounded, relocatable compiled neighbor consumers establish the description-table endpoint: complete palette-copy consumers for Ruby/Emerald and a background-template consumer with bound child calls for FireRed. Zero bytes, plausible text, numeric ordering, source labels, ROM identity and arbitrary calls do not establish that endpoint. Unknown or conflicting evidence cannot justify exclusions.

Independent pinned-source correlation covers all 411 positive species mapping entries and all 387 description dimension pairs, including the sentinel. All 75 disputed instances map to rows 387–411 beyond the proven 387-row extent. Source labels are explanatory oracle data, not production routing.

An immutable B-derived exclusion set affects only description-record applicability and description coverage. All **412 species records**, public A values/statuses, base stats, types and sprite fields remain intact. Each control now reports **386/386 applicable descriptions**. The 25 out-of-domain records have description, height and weight marked `NOT_APPLICABLE`; missing or malformed in-domain prose remains applicable and unavailable, not excluded.

## Projection and persistence

The existing serialized shared `description` field now preserves applicability without retaining prose. Genuine `NOT_APPLICABLE` stays explicit; applicable text isolated into the language overlay leaves a value-null `NOT_FOUND` placeholder. Extraction, overlay key validation and expected coverage use this explicit applicability, never paired dimension statuses or reason strings.

Parser revision **50 → 51** invalidates caches with the former indiscriminate description-placeholder semantics. Storage schema remains **2**. Localized prose stays in its own projection; UI locale does not trigger parsing.

## Verification

The owner gate passed 159 synthetic/cache cases with six existing opt-in skips, then all three exact native slot controls. A subsequent bounded independent read-only code review found no actionable issues.

The coordinator's fresh combined gate ran the exact frozen eleven-file source with `--rerun-tasks --no-parallel`:

| Suite | Cases | Passed | Skipped |
|---|---:|---:|---:|
| `DescriptionSpeciesIndexTest` | 25 | 25 | 0 |
| `SpeciesIndexResolverTest` | 34 | 34 | 0 |
| `CatalogLanguageOverlayTest` | 13 | 13 | 0 |
| `MoveDescriptionMaterializerTest` | 40 | 40 | 0 |
| `CatalogParserTest` | 27 | 27 | 0 |
| `CatalogStoreTest` | 53 | 47 | 6 |
| `DamageForecastAssemblerTest` | 12 | 12 | 0 |
| Three exact native description-slot controls | 3 | 3 | 0 |
| **Total** | **207** | **201** | **6** |

Zero failures/errors. Gradle completed in **4 minutes 25 seconds**, with 45 tasks executed. All eleven source hashes remained unchanged. The six skips are existing opt-in cache cases for Unbound, Modern Emerald, official Gen III trainer sprites, Odyssey, and official Gen I/II Local maps; none is a native slot acceptance control. Existing unrelated compiler and Gradle deprecation warnings remain.

All three native controls verify complete record/public-A preservation, independently expected species samples, description-only semantic/overlay/API coverage, and actual SQLite close/reopen. Ruby/Emerald retain **354/354 move descriptions**. FireRed explicitly reports **negative 0/354 move prose, overall positive BLOCKED**; its successful species-slot gate is not full native acceptance.

Retained red evidence includes the corrected initial two-failure log, background-consumer red XML and corrected stale-revision-50 red XML. The initial two-failure XML was overwritten by a later owner run and is not claimed as retained. The owner's post-green CRLF-to-LF test-file normalization is covered by the coordinator gate on the exact frozen LF bytes. Source snapshots, complete patch including both new helpers, raw logs/XML, receipts and SQLite caches remain private; no ROM payloads enter public assets.

## Remaining Stage 4 work

- Task 388 / `LNG-B002`: prove actual FireRed move prose and pass required positive parse/reopen/API acceptance.
- Task 389 / `LNG-B002`: implement the proven Korean direct-sign dialect and headline boundaries; verify both exact controls through parse/reopen/API.
- Tasks 386 and 373–375: remaining capability producers/applicability, current 43-cell/44-control matrix, final current corpus after executable changes stabilize, ledger audit and stage closure.

Task 385's description-slot accounting is complete; its evidence still feeds final matrix ratification. Stage 4 remains open and Stage 5 blocked. No full corpus, device/emulator/ADB testing, signing, release APK or cleanup is part of this checkpoint.
