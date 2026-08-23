# Performance and Memory Hardening — Stage 1 Validation

## Scope

Stage 1 stops continuous allocation and rendering churn. It implements R1–R4 without changing catalog output, save interpretation, battle selection, Organic knowledge, navigation, or map discovery. Peak-heap and long-lived-residency work remains assigned to the later stages below.

## Requirement matrix

| Requirement | Status | Observed evidence | Expected threshold | Next action / owner |
| --- | --- | --- | --- | --- |
| R1 — Context reuse | PASS | `reusesCatalogDerivedContextsUntilTheirInputsChange`: two unchanged reads produced 1 unique save-context identity and 1 unique battle-context identity. Applying a trainer produced a second battle context while retaining the same save context; replacing the catalog produced a second save context and third battle context. | One object graph per unchanged catalog/runtime input; atomic replacement after a relevant input change. | Retain as a regression control in all later stages. |
| R2 — Unchanged state | PASS | Server returned HTTP 204, `Content-Length: 0`, no content type, and 0 body bytes at the current version. An older version returned HTTP 200/current JSON. The client made 0 JSON calls and 0 state callbacks for 204; an equal-version injected heartbeat left the rendered GAME theme unchanged. | Zero JSON body bytes and zero client replacement/commit for unchanged versions. | Retain server, gateway, and App tests in all later gates. |
| R3 — Live polling | PASS | Discovery delays were 1, 7, and clamped 20 ms (1,000, 143, and 50 scheduled cycles/s respectively). Cached and ineligible delays were 25 ms (40 scheduled cycles/s). One receive buffer served 100 empty UDP polls. The byte cursor decoded a 1,024-byte reply directly into its destination and handled mixed whitespace/hex case, wrong addresses, `ERROR`, invalid nibbles, and short payloads. Source contains no reply `String`, regex split, or second address parse. | Steady scheduled cadence at most 40 cycles/s; one receive-buffer construction per transport; one byte-level reply pass; 1,024 bytes accepted within the packet contract. | Retain polling, allocation-count, and parser-case tests in all later gates. |
| R4 — Hot-path evidence | PASS | Deterministic tests record 1/1 unchanged context identities, 0 unchanged response bytes, 0 unchanged JSON/update callbacks, discovery and steady cycle rates, 1 allocation per 100 empty polls, 2 queued packets drained in one cycle in the late-duplicate case (an 80 packet/s test workload at the 25 ms cadence), and 1,024 decoded bytes. No production page or component was changed to expose these measurements. | Every named hot-path metric has repeatable automated evidence and remains outside normal UI. | Extend the same evidence format when Stages 2 and 3 add bounded resources. |
| R5 — Request and archive bounds | DEFERRED | Current 256 MiB aggregate request-body allocation and archive-copy risk remains. | Streaming input; 64 MiB compressed cap; 32 MiB extracted cap; at most one complete extracted array. | Stage 2 ingestion workstream. |
| R6 — Map rendering | DEFERRED | Uncached raster expansion and duplicate concurrent renders remain. | 32 MiB encoded LRU, render coalescing, and rendering outside the runtime lock. | Stage 2 map-render workstream. |
| R7 — Map residency | DEFERRED | Every revealed placement can still remain mounted and timed URLs can invalidate all placements. | Current/adjacent placement mounting and at most 32 MiB decoded-pixel residency. | Stage 2 browser-map workstream. |
| R8 — Catalog sections | DEFERRED | Chunk/list/assembled-array overlap and unchanged checkpoint rewrites remain. | Incremental section assembly/decode and changed-section-only writes. | Stage 2 catalog-store workstream. |
| R9 — Peak evidence | DEFERRED | Stage 2 bounds do not exist yet, so high-water evidence cannot pass. | Request/archive, map render/residency, and catalog-section byte metrics. | Stage 2 validation owner. |
| R10 — Pokédex rows | DEFERRED | Large catalogs can still mount every filtered row and sprite. | At most 60 mounted rows without behavior loss. | Stage 3 web-residency workstream. |
| R11 — Change-first background work | DEFERRED | ROM/save background paths can still hash, open, construct, and deserialize before proving a change. | Unchanged inputs perform none of the listed expensive operations. | Stage 3 background-work workstream. |
| R12 — Mapper bound | DEFERRED | Capture history and rewrite/base64 growth remain unbounded. | At most 32 snapshots and 16 MiB raw; append until compaction is required. | Stage 3 mapper workstream. |
| R13 — WebView ownership | DEFERRED | Activity and overlay can still retain two active WebViews/catalog graphs. | One actively polling visible WebView; hidden/superseded surfaces pause and release. | Stage 3 Android-lifecycle workstream. |
| R14 — Loopback ownership | DEFERRED | Cached worker pool and active socket growth remain unbounded. | Four workers and eight simultaneous connections with bounded rejection and complete shutdown. | Stage 3 server-ownership workstream. |
| R15 — Long-session evidence | DEFERRED | Bounded Stage 3 structures and authorized device profiling do not exist yet. | Automated lifecycle bounds plus Java/native/PSS, GC, CPU, WebView, and renderer evidence by ROM/phase. | Stage 3 validation owner; device portions require separate user authorization. |

No requirement is unassigned and Stage 1 has no `BLOCKER`.

## Automated gate

| Command | Result |
| --- | --- |
| `npm test -- --run` in `companion-web` | PASS — 26 files, 181 tests, 0 failures. |
| `npm run build` in `companion-web` | PASS — TypeScript and Vite production build. |
| `gradlew verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease -PdualdexVersionName=1.1.0-rc.48 -PdualdexVersionCode=1010048` | PASS — 1,469 tests, 0 failures, 0 errors; lint 0 errors (51 warnings); release APK assembled. |
| `node --test tools/release/*.test.mjs` | PASS — 18 tests, 0 failures. |

The unsigned Stage 1 gate artifact was `app-release-unsigned.apk`, 17,304,019 bytes, SHA-256 `2D9A393992B956EF61964B0A63D85F96E00081D745537DAD5EA57496FECE0432`. It is build evidence only: it was not signed, installed, or published.

## Specification cross-check

Every R1–R15 requirement in the design is represented exactly once above. Validation clarified R3 so that “40 polls/s” means scheduled memory-read cycles; already-arrived packet draining remains permitted and evidenced separately. No requirement was omitted. Stage 2 may start because R1–R4 pass and every remaining risk has an explicit target stage and owner.
