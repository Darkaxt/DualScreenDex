# Stage 4 FireRed move-prose checkpoint

Status: **corrected implementation independently verified: 240 passed, seven existing opt-in skips, zero failures/errors**. This closes Task 388's mandatory Japanese FireRed move-prose slice, not complete Stage 4 acceptance.

## Compiled authority

Japanese FireRed uses direct **60-byte** move-description records, while the previously accepted Ruby/Emerald consumers use **56-byte** records. The materializer now selects a bound biased root **and proven stride** before decoding; it neither globally changes the stride nor selects an ABI by title, filename, hash, language identity or family label.

The relocated summary consumer reads a shared u16 move-ID field and computes its 60-byte offset. A call-free nonzero branch of the numeric consumer uses the same state declaration and field to index the selected 12-byte numeric move table. Equality with that selected root is mandatory; matching a retained cache's numeric values is corroboration, not production authority. The summary's text wrapper places the declared prose pointer in `TextPrinterTemplate.currentChar`, and the bound printer consumes that template. Pinned public `pret/pokefirered` source is a structural oracle, not a production profile. Compiled arithmetic establishes the native record geometry rather than the source's Western pointer-array declaration.

The initial investigation used one declared 256-KiB code window, found four candidates within a 32-candidate cap, and did not extend its depth-two chain or perform a whole-ROM discovery scan. All 354 native records terminate internally with zero-only padding. Five independently decoded sample digests cover move IDs 1, 11, 72, 253 and 354.

Missing-site recovery retains both supported prose and numeric nominations while reconciling **all** original literal counts in one bounded scan. Complete role proof still follows nomination. Competing root/stride witnesses conflict before readability, including a 56/60 disagreement at the same root and an unreadable recovered competitor. Duplicate identical witnesses remain valid. Incomplete counts, overflow, exhausted budgets and cancellation cannot retry an unproven pointer/learnset fallback.

## Reproduced corrections

- The initial relocated 60-byte positive failed before implementation. A real revision-52 SQLite acceptance test also failed before the parser revision changed. Parser revision is now **53**, with storage schema unchanged at **2**.
- A malformed-token mutation exposed the existing native direct decoder's tolerance of an invalid extended control: decoding continued to a later terminator and the 85% validity threshold admitted the damaged row. Native 56/60-byte direct records now require zero invalid units. This is local to that decoder; unrelated codecs, Western pointer and embedded readers are unchanged. Missing/malformed in-domain prose remains applicable and unavailable, never another language's text or an applicability exemption.
- An exact-last-row failure was separately traced to the fixture truncating its unrelated palette pointer. The fixture was corrected independently before the invalid-token production change; it is not reported as a parser defect.
- One bounded independent review found that equal pre-template font-call targets did not prove preservation of the already-stored text pointer. A returning callee that writes zero to caller `sp[0]` reproduced the defect from a passing positive: two cases, one intended assertion failure, zero errors/skips. The corrected fixture assembles the complete relocated leaf. Production binds the exact caller-selected attributes 2/3, dispatcher and jump entries, both complete read-only paths and balanced return. A single 256-byte adjacent read contained the 232-byte leaf; the relevant proof uses 78 bytes within a 226-byte extent, with no descendants or graphics traversal.

Six added callee regression methods cover the pointer overwrite, relevant instructions/selectors/branches/return, dispatch/literal relationships, independent leaf/table relocation, unreachable-versus-reachable switch arms and missing/truncated return. The coordinator inspected the exact corrective diff against the reviewed snapshot and verified all 46 retained correction-artifact hashes plus red/final XML.

This is bounded static compiled text-role authority, not arbitrary live graphics/font execution validation. Unexamined rendering descendants supply no preservation or return evidence.

## Verification

Final owner gates ran after the callee correction on five frozen source files:

| Gate | Passed | Skipped |
|---|---:|---:|
| Move-description materializer | 62 | 0 |
| Catalog caller regressions | 27 | 0 |
| Scoped persistence regressions | 17 | 0 |
| Full Japanese Ruby/Emerald/FireRed native controls | 3 | 0 |
| Separate Japanese species-slot controls | 3 | 0 |
| **Total** | **112** | **0** |

Zero failures/errors. All three Japanese GBA controls report **AVAILABLE 354/354**, with the five independent digests checked through parsing/materialization, actual SQLite write/close/read/close and API projection. Whole-catalog/projection parity, integrity, `ROM_DEFAULT`, cache-only bootstrap and zero reparses remain required. Six retained owner databases were separately reopened read-only and checked for parser/storage 53/2, complete 354-ID prose inventories, five matching digests each, successful integrity and zero foreign-key violations. Species-slot applicability remains 386/386 with public regional data and all 412 species records preserved.

The coordinator's final fresh combined gate ran **247 cases: 240 passed, seven existing opt-in skips, zero failures/errors**, in **14 minutes 59 seconds**, with 45 Gradle tasks executed using `--rerun-tasks --no-parallel`. All five frozen source hashes remained unchanged. It includes all owner parser/native/slot gates, the full persistence suite, species-description/overlay regressions, Gen II map/cancellation/scene regressions, both exact Korean declared-sign controls and 12 forecast cases. All three Japanese GBA controls independently report 354/354 prose through parse/reopen/API; all three slot controls preserve 386/386 species-description applicability and all 412 records. Both Korean sign controls remain positive.

The seven skips are six existing cache opt-ins (Unbound, Modern Emerald, official Gen III trainer sprites, Odyssey and official Gen I/II Local maps), plus `requiredDynamicSpecialLandmarkFailsClosed`, which requires `DUALDEX_POKEGOLD_ROM`. None of the eight selected native/slot acceptance methods was skipped. Existing compiler and Gradle deprecation warnings remain. The earlier owner 106/106 and coordinator 241-case gate (234 passed, seven existing opt-in skips, zero failures/errors) remain explicitly **pre-correction** evidence, not substitutes for final corrected acceptance.

Raw exact-control windows, private input receipts, source snapshots, red/green logs/XML, complete patches and SQLite caches remain outside public assets. No ROM payloads are published.

## Remaining Stage 4 work

The previous FireRed `NOT_FOUND 0/354`/`requiredPositive=BLOCKED` state is superseded by the corrected, independently verified positive controls. Synthetic missing/invalid/conflicting-authority negatives remain mandatory; the contradictory exact-control negative was replaced rather than retained alongside positive acceptance.

Task 386 / `LNG-B002` still owns item-name production, remaining Local/region-label authority/applicability and reference-aware POI coverage. Tasks 373–375 / `LNG-B003` still require all 15 capability dispositions across the current 43-cell/44-control official matrix, final current corpus after executable changes stabilize, ledger audit and published Stage 4 closure. Required official cells are not deferred. Stage 4 remains open and Stage 5 blocked. No corpus, devices/emulators/ADB, signing, release APK or cleanup is part of this checkpoint.
