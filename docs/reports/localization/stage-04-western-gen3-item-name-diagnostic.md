# Stage 4 Western GBA item-name diagnostic

Status: **fifteen-control diagnostic verified; all 1,660 requested names remain unavailable and semantic acceptance remains open**. Task 397 is the diagnostic slice of Task 390 / `LNG-B002`. Production remains `2fbe3be5481ec082684a8d42637ad716bde217ee`, parser revision **60**, storage schema **2**, codec versions **1**. This checkpoint changes app tests and documentation only.

## Scope and authority boundaries

The separate `westernOfficialGen3DiagnosticFifteenControls` selector covers the original official English, French, German, Italian and Spanish controls for Ruby/Sapphire, Emerald and FireRed/LeafGreen. The original 35-control manifest SHA-256 is `ee408ad4a5d51da8656ff336ff7c139d92fc24b9d201f6817c47ce3ef194a749`; inventory SHA-256 is `70c2ac3c87c871cb9b2d92d798977afb0f26db8e2f9ba9f8380184a098834837`. Exact language/family/hash/code bindings and 16,777,216-byte input sizes are checked before input access. Hashes identify exact evidence, not semantic authority.

The adapter requires the exact diagnostic opt-in, validates both metadata pins and their complete control inventory, then reserves fresh output directories before reading originals. Each input is read once with a size bound. The ordinary analysis context supplies its own session and callbacks; the original item producer is invoked once during normal materialization. There is no second session, rediscovery, automatic retry or raw-ROM retention.

Immediately after original analysis and before materialization, the test adapter observes the already-initialized session resolver's original route map. Inspection must not initialize the lazy delegate or invoke a resolver method. The selected layout's immutable authority is bound by object identity, not structural equality or counts; missing or nonunique bindings fail closed. The frozen route snapshot must remain unchanged afterward. All five route dispositions stay distinct: NotEvaluated, NotInvoked, and Invoked with Absent, Ambiguous or Nominated state. Only an original NotInvoked route can permit later compiled-only discovery; a published nomination is not name proof.

The current requested domain is the complete union of capture-ball and item-POI IDs, including zero and unsigned 16-bit values. Complete producer fields and unavailable reasons are retained. Sparse overlays contain only AVAILABLE, nonblank names; every requested ID remains in the capability denominator. No Gen I/II reference ABI, Japanese count, table stride or name width supplies Western authority.

Checks cover current numeric bindings, all fifteen capability dispositions, full exposed language/projection/capability API metadata, whole-catalog SQLite write/close/reopen equality, all eighteen persisted sections, integrity and schema checks, and cache-only runtime startup with a throwing/counting parser callback. Every check has a unique key and earlier failures persist. Missing names alone are diagnostic observations, not integrity failures or waivers. API `Ball <id>` fallback labels are presentation only and never count as available or accepted ROM-native text.

## Preparation evidence

Repository scope is two new app-test files plus one separate selector in the existing real-control class. The JDBC factory, historical adapters, Gen I semantics and all production sources remain unchanged.

| Attempt | Actual execution | Result | Elapsed |
|---|---|---|---:|
| 1 | Zero JUnit methods | Compilation preparation failure: three synthetic `Files.readString` / `Files.writeString` calls are unavailable on the app compile API surface | 69.98 s |
| 2 | 22 fabricated methods | 22/22 passed; subsequent source inspection found an untested array-manifest/object-reader integration mismatch | 87.41 s |
| 3 | 23 fabricated methods | 23/23 passed after bounded strict envelope decoding and positive/malformed metadata coverage | 76.74 s |

The final gate includes uninitialized-no-init and identity-only route tests, immutable snapshots, once-only throwing producers, zero/u16 domains, missing/unavailable/extra-name accounting, all exposed API metadata/capability mutations, and mixed/all-unavailable fixtures through actual SQLite and cache-only runtime with zero reparses. The initial compilation failure is not a behavioral RED; attempt 2 is not misrepresented as covering the later decoding fix. No successful unchanged gate was rerun.

Independent preparation reconciliation verified the three-file patch and selector-only delta, all method/command/XML inventories, source stability, both synthetic databases and **884 retained pins** without rerunning tests or reading originals. Receipt SHA-256: `e54e84f0a9211e88aef1c13cb5a6c974eb279e41973b676c8dfef954e140b147`.

The coordinator subsequently identified and closed **Task 398**, a separate test-suite integration defect: the synthetic temp helper required an environment variable that ordinary test runs do not set. With all `DUALDEX_*` variables absent, the exact SQLite/cache-only fixture reproduced one expected failure at the helper in **50.57 seconds**. The helper alone now follows the neighboring optional-root convention: null/blank uses JVM temp, while a configured root is created before the unique child. The existing **23/23 fabricated methods then passed** without those variables in **77.30 seconds**, zero failures/errors/skips, including both real SQLite/cache-only fixtures with zero reparses.

Two earlier launcher attempts ran **zero JUnit methods** because their private init incorrectly inspected Gradle's mutable system-property view. Gradle stores `java.io.tmpdir` separately; the corrected init verifies effective JVM arguments. Both preparation failures remain distinct from the genuine RED. Blank/configured-root behavior matches the existing source convention; it was not claimed as an additional fresh execution gate.

Independent retained review verified the helper-only delta, exact RED/GREEN commands and XML, absent environment and effective private JVM temp, all other current sources, and unchanged Task 397 evidence. No test or original-control run was repeated. Review receipt SHA-256: `1d092827aa602fffb535a3aa1929167c578f4511350f34b7fe0013a7540ee418`; GREEN XML SHA-256: `5d79577594758a0bdd2973e6af6863f1e24f92dd21476c927be191d36f8ea063`. The actual diagnostic explicitly configured the variable, so its earlier frozen execution remains valid; the correction changes only synthetic fixture setup.

## Single current diagnostic

A separately recorded coordinator decision froze **793 executable-source files** and **888 evidence pins**. The sole fifteen-control selector ran **once**, with a 1,800-second watchdog and no retry: exit **0**, **1,255.43 seconds**, **40 tasks executed**, one aggregate JUnit method, zero failures/errors/skips. All frozen sources and evidence pins remained unchanged. Existing Kotlin warnings and Gradle deprecation notices remain in the complete log.

The following results apply independently to **each of the five Western languages**:

| Family | Available / requested names | Ball / item-POI API occurrences | Original route | Checks |
|---|---:|---:|---|---:|
| Ruby/Sapphire | 0/101 | 12 / 233 | NotInvoked | 597 |
| Emerald | 0/104 | 12 / 274 | InvokedNominated | 679 |
| FireRed/LeafGreen | 0/127 | 12 / 351 | InvokedNominated | 833 |

Totals: **0/1,660 observed available names**, **4,470 numeric item API occurrences**, **10,545 passing diagnostic checks**, **zero API reparses**. Each control performs one original read, one original analysis and one normal item-producer call. All fifteen original authorities are Unavailable with reason **`no original compiled item candidates`**. Every diagnostic receipt terminates `DIAGNOSTIC_CAPTURE_COMPLETE`, but retains `semanticAcceptance=false`, `acceptedNames=0` and `requiredSemanticCompletion=NOT_ACCEPTED`.

Independent retained-only reconciliation checked all current producer domains, sparse overlays, original route/authority snapshots, numeric/API occurrences, all fifteen capability DTOs and language metadata, and read-only SQLite integrity, foreign keys, schemas 60/2 and eighteen sections. It retained **1,159 content pins**, performed zero additional original reads and zero test reruns, and preserved all historical evidence.

Historical parser-49 summaries also observed 0/101, 0/104 and 0/127 per language. The new parser-60 domain was measured independently, not preset from those counts. Common language/capability deltas are retained for classification; equality of historical and current non-item catalog payloads is **not established**. Historical caches were not opened as current cache-only inputs.

| Retained artifact | SHA-256 |
|---|---|
| Coordinator decision | `9240f973d96588971a0b43024df7538b53e8b981c17a50a6309f5af541a6c6ad` |
| Execution receipt | `2d2ee638ffb86836cc496803fe2d3862dab19d9fc37c9d4215ae82deed88bc04` |
| Complete build log | `078c21605c9854896ee2f01bb8cbe51a8979e8aa6e0a70b5a23fdd3ee3668ec7` |
| Exact selector XML | `245a551c2ea42a8bb6592a6f193ec16461503d37351e5f360773d54d4faf6589` |
| Diagnostic batch | `de385a94ccc79dafc4a659ccc2fcbb1acbf80806b15b5c749cbe43392d07f2f4` |
| Independent result reconciliation | `be95a07b35f4cd04566901a136059b1a95601c639d603f3bf9fc9cae89a1273e` |

Original paths, captured metadata, catalogs, databases and API payloads remain private and outside public assets. No ROM is bundled or copied into production assets.

## Mandatory next boundary

The observed failure is at compiled item-candidate recognition, before item-name materialization can establish token outcomes. Source inspection has identified a potential 44-byte item-structure recognizer gap, but the diagnostic does **not** prove that layout or any particular instruction sequence on these inputs. The next Task 390 step is independently bound compiled-consumer/reference/token proof preparation using the original route and current requested domains. Existing native proof mechanics may be reused; their names, counts, roots and widths may not. Preparation must use the **selected original entry**: Emerald/FireRed retain incidental NotInvoked entries, and Ruby retains an incidental InvokedAbsent entry. Selecting another entry to obtain a more permissive route would discard the original authority binding.

All 1,660 required names remain open under `LNG-B002`. Resolution must then pass independent expected-name acceptance through materialization, actual SQLite close/reopen and cache-only API. Task 386's Local/region labels and reference-aware POI coverage, and Tasks 373–375's current **43-cell/44-control** matrix, fifteen capability dispositions, final current corpus after executable changes stabilize and published Stage 4 closure remain mandatory. No required cell is deferred; Stages 5–6 remain blocked. No corpus, device, signing or release-APK run is part of this checkpoint.
