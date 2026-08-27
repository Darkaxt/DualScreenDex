# DualDex Project-Wide QA Hardening Specification

**Status:** Audit synthesis; implementation is not authorized by this document

**Primary baseline:** `7766bd3c` (`fork/master` when the audit began)

**RC71 integrated baseline:** `723fb4e7` (`v1.1.0-rc.71`, current `fork/master`)

**Scope:** Android setup and guide loading, parser/catalog persistence, companion web/server/simulator, RetroArch/save/battle/mapper runtime, CLI tools, CI, and release governance

## 1. Purpose

This specification consolidates the project-wide QA reports into one RC71-aware backlog. It defines observable failures, required behavior, scoped remediation, and acceptance criteria. It does not prescribe implementation details beyond the boundaries needed to prevent one module failure from crashing or corrupting the rest of DualDex.

The triggering player report is consistent with real product defects rather than simple setup misuse:

- Before RC71, ROM indexing state could overwrite the truth that Android All Files Access was granted.
- RC71 separates permission truth from indexing state and contains automatic parser/source failures.
- A separate state-delivery defect remains: native `RetroArchView` and `SaveRamView` updates do not advance the version polled by the WebView. Corrected RC71 state can therefore remain invisible until an unrelated gateway action changes the version.
- Manual guide-loading memory failure and no-catalog asynchronous failure presentation also remain incomplete after RC71.

## 2. Evidence and confidence

The reports used read-only static data-flow analysis. No new build, APK launch, emulator run, device run, or destructive failure injection was performed during this audit. “Confirmed” below means that the wrong behavior follows from an inspected code path; it does not mean that every path was reproduced on hardware.

Existing RC71 evidence reports:

- `docs/superpowers/specs/2026-08-27-storage-and-guide-load-hardening-design.md`
- `docs/reports/2026-08-27-storage-and-guide-load-hardening.md`
- `release/RELEASE_NOTES_1.1.0-rc.71.md`

RC71 reports 29 web test files / 215 tests, Android unit/lint/release gates, catalog tests, and 18 release-policy tests. Its own report states that no APK was installed or launched. The audit first compared the hardening implementation at `f5954d76`; smart-sync then confirmed its release packaging and tag at `723fb4e7`, without changing the audited production paths.

## 3. RC71 work that must be retained

The following behavior is already designed and implemented on the RC71 branch and must not be duplicated or regressed:

1. Android All Files Access truth is independent from ROM-index state.
2. Direct indexing retains the last valid direct index on refresh failure and can retain an eligible SAF fallback.
3. Player-facing storage and guide errors are sanitized while debug stage/class remains observable.
4. A ROM switch withholds the previous catalog, and incomplete parser checkpoints do not become active.
5. ROM-source and parser boundaries contain ordinary `Exception` and `OutOfMemoryError` without catching all `Throwable`.
6. A failed exact guide source is latched so the heartbeat does not reopen it repeatedly.
7. A failed automatic guide load has an explicit retry route, and loading ownership is released on success or failure.

The requirements below begin where RC71 stops or where a broader project invariant is still missing.

## 4. Global invariants

### INV-01 — Fail closed by module

Failure to resolve, parse, load, render, poll, or persist an optional feature must disable or mark that feature unavailable. It must not crash the APK, publish invented empty data as authoritative, retain stale live authority, or discard the last independently valid state.

### INV-02 — Fresh identity gates live authority

Live memory, SaveRAM, mapper captures, checkpoints, and catalog publication must be bound to a fresh immutable content identity plus a monotonic session epoch. Basename-only or stale status is discovery evidence, not authorization.

### INV-03 — User data outlives parser caches

Parser cache invalidation, corruption cleanup, inactive-cache cleanup, and parser-schema migration must not delete the last valid SaveRAM snapshot, knowledge journal, or other user-derived recovery state.

### INV-04 — Every untrusted workload is bounded

Socket reads, archive traversal, decompression, ROM/save reads, cache chunks, inflated JSON, UDP drains, and optional binary decoders require explicit byte/count/time or operation limits before allocation or unbounded looping.

### INV-05 — API and UI failures are structured and recoverable

Android and desktop servers must return one JSON error envelope for API failures. The browser must defensively parse it, surface a stable player-safe message, retain technical detail only in debug evidence, and provide bounded reconnect/retry behavior.

### INV-06 — Release claims bind to executable evidence

A parser/catalog-affecting change requires an explicit cache-schema decision and current compatibility evidence. A candidate must not become public under a policy that says device validation is still required.

## 5. Severity and delivery policy

- **P0 — release blocker:** credible crash, corruption/data loss, corrected state not reaching the user, stale parser output shipped as current, or a release gate that permits untested public candidates.
- **P1 — next hardening batch:** high-impact wrong authority, unrecoverable workflow, resource exhaustion, or broad test escape.
- **P2 — scheduled quality work:** recoverability, performance, navigation, parity, and diagnostic defects with bounded impact.
- **P3 — documentation or defense in depth:** useful hardening not yet tied to a confirmed mainstream failure.

Do not publish a separate RC for each item. Group a meaningful set of P0/P1 corrections, then promote one candidate after its complete acceptance matrix passes.

## 6. Android setup and guide lifecycle

### AND-01 — Version all externally produced runtime state (**P0**)

**Evidence:** `ProductionCompanionRuntime.updateRetroArch()` and `updateSaveRam()` only replace side fields, while `StateView.version` remains the gateway snapshot version. `/api/state?sinceVersion=N` returns `204` when that unchanged version is supplied. The browser only accepts a strictly newer version.

**Failure:** Returning from Android Settings can update native storage/index state without updating Setup in the WebView. The same defect hides direct-index completion/failure, SaveRAM status, failed-guide state, and the retry affordance until an unrelated action occurs.

**Requirement:** State delivery must use one monotonic revision that advances for gateway actions and runtime-owned `RetroArchView`/`SaveRamView` changes. State construction and listener notification must observe one coherent snapshot.

**Acceptance:** With `sinceVersion=N`, publishing each of `GRANTED/INDEXING`, direct-index failure, SaveRAM transition, and guide failure returns HTTP 200 with a revision greater than N. A Preact/loopback integration test must show the Setup transition without an extra tap.

### AND-02 — Contain and present every manual/asynchronous guide failure (**P0**)

**Evidence:** Manual `/api/load` materializes `RomSourceLoader.load(...)` before RC71’s runtime parser boundary, while the request handler contains `Exception` but not `OutOfMemoryError`. After an asynchronous no-catalog failure, `App.tsx` renders `Welcome` using only local request `error`; it does not render `state.error`. RC71’s retry link is only on Setup.

**Failure:** A manual source-load OOM can escape the loopback worker/process. An automatic parse that fails after the HTTP load response can return the user to “Choose a Pokémon game” without a reason or reachable retry.

**Requirement:** Put manual source materialization behind the same narrowly scoped `Exception` plus `OutOfMemoryError` policy as automatic loading. Surface sanitized terminal `state.error` and retry/reselect from the no-catalog screen, or deliberately route the failed state to Setup.

**Acceptance:** Inject ordinary failure and OOM at manual source and asynchronous parser boundaries. The process remains alive, loading ends, no partial catalog is active, the normal UI shows only safe guidance, and retry is reachable without navigation knowledge. Paths, hashes, source names, parser stages, and exception messages must not appear in normal UI.

### AND-03 — Quarantine SAF indexes whose persisted grant is absent (**P1**)

**Failure:** A persisted SAF index remains eligible after its URI permission is revoked. A matching RetroArch title can resolve to an inaccessible document; RC71 prevents the heartbeat loop but retains a retry that cannot succeed.

**Requirement:** Stored SAF entries may remain on disk for future recovery but must not participate in session resolution or direct-index fallback unless the exact persisted tree grant is currently valid.

**Acceptance:** Index through SAF, revoke the grant, and simulate matching content. No source open occurs, retry is not offered, ROM access is missing, and folder selection remains available. Restoring the grant can make the stored index eligible again.

### AND-04 — Make overlay picker actions perform the advertised action (**P2**)

**Failure:** Overlay “select folder” routes only foreground `MainActivity`; the player must locate and tap the action a second time.

**Requirement:** Send a one-shot picker request to the activity, consume it exactly once after activity-result registration, and retain picker ownership in the activity.

**Acceptance:** Each overlay SAF route foregrounds the activity and opens the intended `OpenDocumentTree` once.

### AND-05 — Provide an explicit safe game rescan (**P2**)

**Failure:** A cached direct index is not refreshed when a ROM is later copied or replaced; discovery may remain stale until permission or app storage state changes.

**Requirement:** Add a player-visible rescan or a bounded freshness policy. Keep the last valid index active until a new index commits successfully.

**Acceptance:** Add/replace a ROM after initial indexing, trigger rescan, and observe the new committed index. Inject scan failure and retain the prior index.

### AND-06 — Harden All Files Settings launch (**P3**)

**Requirement:** Resolve the package-specific Settings intent before launching; fall back to global settings or explicit SAF guidance when unavailable. Never let `ActivityNotFoundException` terminate the flow.

### AND-07 — State the protected-path limitation accurately (**P3**)

All Files Access intentionally excludes protected `Android/data` and `Android/obb` discovery. Setup documentation must state that ROM/save content needs public shared storage or the supported folder picker; it must not imply universal access to app-private RetroArch paths.

### AND-08 — Isolate guide presentation projection failures (**P1**)

**Failure:** Area-guide projection runs synchronously during uncached state construction. A malformed or unexpectedly large projection can throw or exhaust memory while serving ordinary state, taking the whole companion path down even though the underlying catalog is usable.

**Requirement:** Bound retained projection work and isolate expected `Exception`/`OutOfMemoryError` at the presentation boundary. Disable only the affected guide projection, retain the catalog and other pages, and publish sanitized availability plus bounded debug stage/class.

**Acceptance:** Inject projection exception, oversized retained input, and OOM. `/api/state` remains available, unrelated guide pages work, the area guide is marked unavailable rather than empty/available, and a later valid projection can recover.

## 7. Parser, catalog, archive, and persistence

### CAT-01 — Separate recovery snapshots from disposable parser databases (**P0**)

**Evidence:** `save_snapshot` is stored in the same SHA-named SQLite database deleted by corrupt-cache handling and inactive-cache cleanup. Schema migration drops `save_snapshot` together with catalog tables, despite a cleanup comment claiming snapshots are outside that namespace.

**Failure:** Cache cleanup, one corrupt catalog section, or a parser/schema migration can silently delete the last valid SaveRAM recovery snapshot.

**Requirement:** Move snapshots to a separate database/namespace or make invalidation and migration operate only on catalog data. Catalog and snapshot migrations must be independent.

**Acceptance:** Persist catalog plus snapshot for SHA A, then independently clear A as inactive, corrupt a catalog section, and open a pre-migration fixture. The catalog may rebuild, but `SaveSnapshotStore.read(A)` returns the original snapshot in every case.

### CAT-02 — Invalidate stale catalogs for parser-output changes (**P0**)

**Evidence:** The aligned hybrid-move decoder shipped at `7766bd3c`, but `CatalogSchema.parserSchemaVersion` remains 42. A schema-42 cache is accepted and published without reparsing, while RC71 release notes advertise the new decoder.

**Failure:** Upgrading users can continue seeing pre-fix move data indefinitely from an old on-device catalog.

**Requirement:** Bump the parser cache schema for the hybrid move-output change. Every parser-output-affecting review must explicitly choose and test “schema bump required” or justify “output invariant.”

**Acceptance:** Seed a schema-42 catalog with old hybrid output. The upgraded app rejects it, reparses, stores the new schema, and exposes corrected move details.

### CAT-03 — Make checkpoint persistence best effort, not guide authority (**P1**)

**Failure:** A synchronous cache write from a parser progress callback can throw through parsing. Disk-full, locking, or I/O failure then suppresses an otherwise usable in-memory catalog.

**Requirement:** Isolate persistence failure, keep incomplete cache state unavailable, record a bounded diagnostic, continue parsing, and publish the final valid in-memory catalog. Parsing/validation failure remains fatal to that guide.

**Acceptance:** A repository that throws on one or every checkpoint write still yields `catalogReady=true` and a usable in-memory catalog; restart is a cache miss, never a false hit.

### CAT-04 — Serialize writers per canonical database (**P1**)

**Failure:** `CatalogCache` and `SaveSnapshotStore` synchronize separate instances that can concurrently write the same SQLite file. Lock exceptions can fail guide loading or SaveRAM polling.

**Requirement:** Use one per-canonical-database coordinator, encode outside write transactions where practical, and apply bounded retry/backoff only to transient lock results.

**Acceptance:** A deliberately blocked checkpoint write and concurrent snapshot write serialize or retry successfully without publishing catalog or save failure.

### CAT-05 — Cancel superseded parser work (**P1**)

**Failure:** A stale ROM A parse keeps the single parser worker occupied after ROM B is selected. Generation checks suppress A’s publication but do not free CPU, memory, or B’s queue position.

**Requirement:** Retain the submitted future and propagate a cancellation token through bounded parser checkpoints and expensive scans. Cancellation must stop persistence and publication for stale work.

**Acceptance:** Block A in a resolver, request B, and prove B starts without allowing A to complete. A performs no later persistence writes.

### CAT-06 — Use one retained-byte identity policy for index and load (**P1**)

**Failure:** Direct indexing hashes every byte, while the normal GBA loader strips an allowed unaddressable trailer. A valid trailed GBA indexes but then always fails indexed-versus-loaded SHA verification.

**Requirement:** Share the loader’s retained-byte/addressability policy across direct, raw, ZIP, and 7z inspection.

**Acceptance:** Index and load a valid GBA with a permitted 1 KiB trailer; SHA/CRC agree and activation succeeds.

### CAT-07 — Verify requested SHA against stored catalog identity (**P1**)

**Failure:** A valid ROM B catalog copied or corrupted into A’s SHA-named file can be returned for A because the embedded `catalog.romSha256` is not checked against the request.

**Requirement:** Reject a complete cache entry unless embedded and requested SHA-256 values match; defend again before runtime publication.

**Acceptance:** Put B’s valid database at A’s cache path. Lookup A is a miss/rejection and runtime parses A rather than publishing B.

### CAT-08 — Isolate malformed optional relationships and descriptions (**P1**)

**Failure:** Unbounded terminator scans, invalid pointers, or per-record expansion failures can abort the whole catalog. Skipped records can alternatively be published as authoritative empty `AVAILABLE` lists.

**Requirement:** Bound all scans, contain optional decoder failures per dataset/record, carry materialization evidence, mark failed records unavailable, and report partial capability rather than invented emptiness.

**Acceptance:** A fixture with one valid and one EOF/unterminated relationship record yields a usable catalog: valid record available, malformed record unavailable, capability partial, and no escaping exception.

### CAT-09 — Bound archive and raw-source work (**P1**)

**Failure:** Index/load can drain huge deflated non-ROM members or oversized raw files without aggregate extracted-byte, entry-count, or nonselected-drain limits.

**Requirement:** Enforce raw size, archive entry count, aggregate extracted bytes, and nonselected drain limits. Stop once a second ROM entry makes the archive ambiguous.

**Acceptance:** Reject a small ZIP containing a valid ROM plus an oversized deflated text member within the configured budget. Reject an oversized `.gb` during indexing without hashing to EOF.

### CAT-10 — Verify section digests and bound cache decoding (**P1**)

**Failure:** Stored section digests are not read or verified. Chunk count, encoded bytes, and gzip-inflated bytes are unbounded before JSON allocation.

**Requirement:** Stream and verify each section digest; cap chunks, encoded bytes, inflated bytes, and section count before deserialization.

**Acceptance:** Valid-gzip substitution with an old digest is rejected. Chunk-count, compressed-size, and inflate-limit fixtures reject deterministically without OOM.

### CAT-11 — Apply contract-specific GBA LZ77 limits before allocation (**P1**)

**Failure:** A reachable malformed asset can declare a 16 MiB output repeatedly even when the caller only needs a small frame, creating heavy allocation and OOM pressure.

**Requirement:** Decoder entry points accept a maximum decoded size, check before allocation, and use explicit per-contract bounds for sprites, balls, trainers, local maps, and world maps.

**Acceptance:** An over-limit but syntactically valid header is rejected before allocation, and the optional asset becomes unavailable without aborting catalog creation.

### CAT-12 — Quarantine only corrupt persisted snapshots (**P1**)

**Failure:** Invalid snapshot JSON throws before polling live candidates, so one corrupt retained row blocks recovery from a valid current save.

**Requirement:** Treat malformed retained snapshots as absent, quarantine/delete only that row, report bounded diagnostics, and continue candidate polling.

**Acceptance:** Malformed persisted JSON plus a valid candidate yields `MATCHED` and replaces the corrupt snapshot.

### CAT-13 — Finish failed asynchronous cache restore (**P2**)

**Failure:** Invalid SHA or repository exception in `restoreCatalogAsync` can escape the worker and leave `CACHE_REOPEN` active indefinitely.

**Requirement:** Contain the async body and always publish terminal failure when its generation is still current.

**Acceptance:** Invalid SHA and throwing repository both end loading without an uncaught worker exception.

### CAT-14 — Bound parser CLI archives, input enumeration, and jobs (**P2**)

**Failure:** CLI ZIP handling bypasses core archive limits, eagerly materializes all corpus inputs, and accepts an effectively unbounded `--jobs` value.

**Requirement:** Reuse core archive policy, stream through a bounded queue, and cap workers to a documented maximum while retaining ordered output.

**Acceptance:** Oversized archives reject before member parsing; an excessive jobs request cannot create excessive threads.

## 8. Companion web, Android/desktop servers, and simulator

### WEB-01 — Add socket deadlines to the Android loopback server (**P1**)

**Failure:** Four workers and four queued sockets have no request-read deadline. Four loopback clients that send partial requests can occupy all workers indefinitely and make the companion appear dead.

**Requirement:** Apply bounded read/write deadlines, request/header/body limits, and structured retryable errors. Closing the WebView/server must interrupt blocked work.

**Acceptance:** Hold four partial requests open; each times out within the bound and a later valid bootstrap succeeds.

### WEB-02 — Preserve navigation through refresh and display transfer (**P1**)

**Failure:** Detail/map/party routes live only in Preact component state. Android transfers the WebView URL, which remains `/`, so transfer/recreation returns to the root.

**Requirement:** Define a validated URL/hash route codec, synchronize browser history, reject malformed/stale catalog-bound routes, and restore only routes valid for the active catalog.

**Acceptance:** Open representative nested routes, transfer/recreate the display, and restore the same route/back behavior. A route bound to a different catalog safely falls back.

### WEB-03 — Version catalog media URLs (**P1**)

**Failure:** Map/sprite/trainer URLs are stable across catalog changes, but responses declare one-year immutable caching. A changed ROM can display media from the prior catalog.

**Requirement:** Include catalog identity in media URLs or remove immutable caching and require revalidation. ETags alone do not repair an immutable URL already satisfied from cache.

**Acceptance:** Load catalog A then B with the same asset IDs/keys and different bytes. B displays B’s media without clearing WebView data.

### WEB-04 — Standardize API errors and reconnect polling (**P1**)

**Failure:** Android and desktop servers emit plain-text errors, while the browser unconditionally parses JSON for actions/uploads. Polling has no catch/backoff, so rejection becomes unhandled and offers no connection status.

**Requirement:** Return one JSON error envelope from both servers; parse defensively by status/content type; add cancellable polling with bounded exponential backoff and a reconnecting/failed UI state.

**Acceptance:** Exercise 400, 404, 500, malformed JSON, dropped connection, and server restart. No unhandled promise rejection occurs, the UI explains reconnection safely, and polling recovers without duplicate timers.

### WEB-05 — Bound and conflate desktop SSE state (**P2**)

**Failure:** A slow/nonreading SSE client retains an unbounded queue of serialized snapshots.

**Requirement:** Use a bounded latest-state slot or small conflating queue, write deadlines, and cleanup on disconnect.

**Acceptance:** A stalled client under many state updates remains within a fixed memory/queue bound and eventually receives the newest state or disconnects.

### WEB-06 — Return 404 for missing static assets (**P2**)

**Failure:** Missing JavaScript/CSS modules receive `index.html` with HTTP 200, producing module MIME/syntax failures and a blank app.

**Requirement:** SPA fallback applies only to extensionless HTML navigation; missing assets return 404 from Android and desktop servers.

**Acceptance:** Unknown route returns the SPA; unknown `.js`, `.css`, image, or map returns 404.

### WEB-07 — Publish sprite availability in the guide contract (**P2**)

**Failure:** The area guide always requests species sprites although the API can omit sprite availability, causing avoidable 404s and noisy broken-image behavior.

**Requirement:** Expose availability or provide a deterministic fallback before rendering a request.

### WEB-08 — Make simulator encounter keys unique (**P3**)

**Failure:** Repeated identical seeded encounters can reuse stable keys and confuse keyed rendering/replay analysis.

**Requirement:** Include a monotonic encounter ordinal or explicitly deduplicate replay events.

### WEB-09 — Maintain Android/desktop API parity tests (**P2**)

A shared matrix must cover response status, content type, error envelope, cache policy, asset 404 behavior, bootstrap/state semantics, and connection recovery for both server implementations.

## 9. RetroArch session, SaveRAM, battle memory, and mapper

### RUN-01 — Never authorize live state by basename alone (**P0**)

**Failure:** When RetroArch omits CRC, basename resolution can reuse a previously activated SHA/catalog and mark a different same-named ROM active. Live memory and SaveRAM can then be interpreted under the wrong ROM.

**Requirement:** Null-CRC basename matching is unverified discovery only. Fresh immutable identity is required before live readers or SaveRAM polling become active; manual catalog selection remains explicitly non-live until verified.

**Acceptance:** After catalog A is active, load different content with A’s basename and missing/different CRC. Resolution is not `ACTIVE`, live readers stop, and no SaveRAM poll starts.

### RUN-02 — Fence queued work with a monotonic session epoch (**P0**)

**Failure:** Save work queued for ROM A can execute after a switch to B, discover candidates from mutable B state under A’s parse context, persist the wrong association, or restore A’s journal before identity rejection. Work may also publish after coordinator close.

**Requirement:** Capture session epoch, ROM SHA, active source, and closed state at enqueue. Revalidate before discovery, reads, persistence, checkpoint/journal mutation, and publication. Restore a journal only after recovery identity is accepted.

**Acceptance:** Block A’s save read, switch to B or close the coordinator, then release A. No A persistence, checkpoint, journal, socket, or state mutation occurs afterward.

### RUN-03 — Hash before declaring SaveRAM unchanged (**P1**)

**Failure:** Same document ID, size, and mtime bypasses a content read. A provider can replace same-sized save bytes while preserving metadata, leaving stale recovery indefinitely.

**Requirement:** Metadata can prioritize polling but cannot prove content identity. Re-read and hash before publishing `UNCHANGED`, or label the retained state stale/unverified until a bounded fresh hash succeeds.

**Acceptance:** Replace bytes A with B under identical ID/size/mtime. Valid B becomes `CHANGED`; invalid B yields `STALE` while retaining A.

### RUN-04 — Make RetroArch config installation recoverable across retry/process death (**P1**)

**Failure:** Recovery data has no restore path, direct writes truncate in place, and retry can overwrite the only valid original with already-truncated bytes.

**Requirement:** Persist a transaction record with original/intended hashes, preserve the first valid backup until verified commit, explicitly resume or offer guarded restore, and use atomic replacement where supported.

**Acceptance:** Inject failure after every write/readback stage and process death. The original config remains recoverable and is never replaced by corrupt bytes.

### RUN-05 — Invalidate stale live authority on terminal memory failure (**P1**)

**Failure:** Read errors, malformed responses, or send exceptions close transport without clearing prior Trainer/Party values published as live.

**Requirement:** Terminal failure immediately suspends live authority, publishes a bounded unavailable reason, preserves only separately identity-gated recovery, and permits later valid samples to restore live authority.

**Acceptance:** Publish one live sample, then inject explicit error, malformed response, and send exception. Prior values are no longer `LIVE`; a later valid sample recovers.

### RUN-06 — Separate command-port liveness from fresh content status (**P1**)

**Failure:** Non-status replies reset missed heartbeats while stale `lastStatus` remains. The view can become `DISCONNECTED` and `ACTIVE` simultaneously and continue stale SaveRAM/battle work.

**Requirement:** Only a fresh valid `GET_STATUS` refreshes content authority. On expiry, withhold active-content fields and stop live operations.

**Acceptance:** Seed `PLAYING`, then send only config/unknown replies through the heartbeat limit. No active resolution, battle session, or stale content use remains.

### RUN-07 — Bind mapper captures to one strict identity (**P1**)

**Failure:** Content/core can change after mapper enable; captures continue under old descriptors and may persist bytes under the wrong identity.

**Requirement:** Freeze and revalidate connection, system, fresh content identity, and mapper epoch before start and commit. Abort and require re-enable on mismatch.

**Acceptance:** Change core/content during capture. No snapshot/export merges identities and the mapper reports an identity-change failure.

### RUN-08 — Eliminate idle 25 ms polling loops (**P2**)

**Failure:** Battle and mapper schedulers wake roughly 80 times per second in aggregate even when disconnected/disabled.

**Requirement:** Start one-shot/fast loops only for eligible active operations and cancel immediately on disable, disconnect, identity change, completion, or close.

**Acceptance:** A fake scheduler advanced for one idle minute observes zero polling callbacks; each operation starts and stops exactly one bounded loop.

### RUN-09 — Recreate broken command sockets with backoff (**P2**)

**Requirement:** Clear/close failed monitors, recreate with bounded backoff, and publish a distinct retrying state.

**Acceptance:** A permanently failing first monitor is replaced by a succeeding second monitor without app restart.

### RUN-10 — Bound UDP drain work (**P2**)

**Requirement:** Cap packets, bytes, or operations per heartbeat/capture tick; carry remaining work forward and record overflow diagnostics.

**Acceptance:** Ten thousand irrelevant packets cannot monopolize the coordinator and a matching packet is eventually handled within quotas.

### RUN-11 — Prevent delayed mapper replies crossing captures (**P2**)

**Requirement:** Use a fresh transport per capture or drain/quarantine residual replies and fail closed when response ownership is ambiguous.

**Acceptance:** A delayed valid reply from a cancelled capture cannot satisfy the next capture at the same address.

### RUN-12 — Bound SaveRAM reads before allocation (**P1**)

**Requirement:** Enforce supported save-size limits with bounded streams even when provider metadata is absent or false. Oversized/endless sources become unavailable/stale while retaining the last valid snapshot.

**Acceptance:** Multi-gigabyte and endless fake sources reject after the configured limit without OOM; valid supported saves still parse.

### RUN-13 — Do not expose mutable retained memory arrays (**P2**)

**Requirement:** Keep zero-copy ownership internal. Public snapshot/read APIs expose copies or controlled readers so external mutation cannot change retained hashes, diffs, or exports.

**Acceptance:** Mutating returned bytes cannot alter later snapshots, hashes, diffs, or exports.

### RUN-14 — Prevent save-cli output/input aliasing (**P0**)

**Failure:** `save-cli` accepts JSON/Markdown outputs that alias a ROM, SaveRAM, probe, or each other, then overwrites the input after analysis. A report can still claim the source was unchanged because hashing happened before the write.

**Requirement:** Before evaluation, reject normalized direct, relative, symlink, hard-link/existing `Files.isSameFile`, parent-alias, and output-to-output collisions.

**Acceptance:** Reject direct, relative, symlink/existing-file collisions and `--json x --markdown x`; verify every input hash remains unchanged.

## 10. CI, release, privacy, and governance

### REL-01 — Execute every JVM/app unit suite in PR CI (**P0**)

**Failure:** CI explicitly runs only parser-core, parser-cli, companion-core, companion-simulator, and companion-server tests, then assembles/lints the app. Catalog, save, RetroArch, mapper, battle, and app unit regressions can merge green.

**Requirement:** Add an aggregate that runs every included JVM module’s tests plus `:app:testDebugUnitTest`. Keep focused parser jobs only as parallel fast feedback.

**Acceptance:** JUnit results exist for every included tested module, and a deliberately selected failure in each currently omitted module fails CI.

### REL-02 — Reconcile candidate draft and validation policy (**P0**)

**Evidence:** Signing documentation says RCs remain draft until dedicated AVD and physical Thor gates pass. Release metadata hard-codes `draft=false`, so the workflow creates a public prerelease before those post-release gates.

**Requirement:** Enforce one policy. Recommended: create candidate releases as drafts, then use a protected promotion step bound to the exact signed APK SHA-256 and recorded required validation mode.

**Acceptance:** Candidate workflow output remains nonpublic until the promotion record names the exact artifact hash and all required gates/authorized substitutions.

### REL-03 — Add reusable packaged Android/WebView acceptance (**P0**)

**Failure:** RC71 can pass unit/browser/build gates without launching its APK. Broken bundled assets, loopback routes, WebView bootstrap, or native-to-web state delivery remain untested.

**Requirement:** Add a reusable emulator job, not ad hoc ADB gestures, that installs the debug APK and validates bundled WebView bootstrap, loopback API, native state revision, storage/index transitions, guide failure/retry, asset 404 behavior, and cache reopen.

**Acceptance:** Publish JUnit/XML and bounded screenshots. Intentionally breaking a bundled asset, loopback route, state revision, or retry route fails the job.

### REL-04 — Make a nonprivate Chromium suite portable and mandatory (**P1**)

**Failure:** Workflows run Vitest but not Playwright. Existing Playwright configuration invokes `npm.cmd`, contains Windows paths, and depends on private fixture environment variables.

**Requirement:** Add a platform-neutral CI profile using installed Chromium and generated/sanitized public fixtures. Keep private evidence capture in an explicit manual job.

**Acceptance:** CI and release run a nonprivate Chromium suite on Ubuntu with no host drive, external ROM, or private environment dependency.

### REL-05 — Bind compatibility evidence to release source and scope (**P1**)

**Failure:** Release checks validate historic fixed values without binding evidence to `GITHUB_SHA`, generator schema, or corpus input digest. Parser-affecting changes can reuse stale compatibility reports.

**Requirement:** Add a release-evidence manifest containing source commit, generator schema, corpus/input digest, and applicability scope. Parser/catalog changes require fresh evidence; Android-only changes may reuse evidence only through an explicit attested scope decision.

**Acceptance:** Parser/catalog changes without refreshed or valid attested evidence fail release. Android-only changes pass only with a recorded nonparser scope.

### REL-06 — Remove reversible player-state diagnostics (**P1**)

**Failure:** `ResolvedStateTrace` uses `hashCode()` fingerprints for low-entropy numeric values such as money, playtime, location, and flags; these are often directly reversible. Raw cache exception messages, full hashes, and stacks can expose filesystem/database detail.

**Requirement:** Export availability, source class, bounded counts, and coarse failure class only. Remove deterministic value encodings for player state and suppress paths/full hashes/raw messages from normal Logcat. Version the diagnostic contract.

**Acceptance:** Known trainer, money, coordinates, flags, and path-bearing failures do not appear raw or deterministically encoded in persisted/exported diagnostics or logs.

### REL-07 — Declare direct Gradle dependencies for direct imports (**P2**)

**Failure:** The app imports parser-core/save-core types through accidental transitive `api` exposure, so intermediary API narrowing can break unrelated consumers.

**Requirement:** Declare direct app dependencies that match imports; narrow intermediary exposure where public signatures permit.

**Acceptance:** An architecture test maps direct project imports to direct dependencies; changing an unrelated intermediary from `api` to `implementation` does not break app compilation.

### REL-08 — Record minimal prior process-exit classification (**P2**)

**Requirement:** On next launch, use an injectable platform facade to record a bounded local-only previous exit category, timestamp bucket, and process-memory bucket. Do not record stack, path, ROM identity, save data, or transmit it.

**Acceptance:** Injected crash/ANR/low-memory exits produce one minimal exportable event each with none of the prohibited data.

### REL-09 — Make source-tag/environment protection auditable (**P2**)

**Requirement:** Verify protected tag/environment policy through an auditable nonsecret check or signed tags with a pinned public identity, and record it in provenance. External GitHub configuration must not be assumed silently.

**Acceptance:** An unprotected/mismatched source tag is rejected before unsigned handoff, and signing cannot proceed without configured environment authorization.

### REL-10 — Replace the stale “current” requirement index (**P2**)

**Failure:** `docs/v1-requirement-matrix.md` calls itself current while describing RC9/v1.0 blockers that conflict with v1.1/RC70/RC71 evidence.

**Requirement:** Preserve the RC9 matrix as immutable historical evidence and add one concise current-readiness index linking the active release marker, current corpus evidence, current RC notes, and historical records.

**Acceptance:** No nonarchived current document claims RC9/v1.0 is the active pending release; all reviewer entry points identify the same active version/tag/readiness source.

## 11. Required cross-module test matrix

The implementation plan must map every requirement to at least one deterministic automated test and use failure injection where the fault is otherwise timing- or device-dependent.

| Layer | Required coverage |
| --- | --- |
| Pure JVM | Cache/snapshot migration and isolation; identity/epoch gates; bounded streams/archives/decompression; session freshness; CLI collision; parser cancellation |
| Preact/Vitest | Terminal state-error presentation; retry/reselect; reconnect/backoff; route codec/history; sprite availability |
| Android/desktop server integration | State revision; JSON error parity; socket deadlines; static 404; catalog-versioned assets; SSE bounds |
| Android unit | Storage policy plus out-of-band state publication; SAF eligibility; overlay picker dispatch; config transaction recovery; coordinator close fencing |
| Packaged emulator | Bundled WebView bootstrap; Settings-return projection with injectable permission facade; index/guide failure UI; loopback and asset behavior; catalog reopen |
| Browser E2E | Public-fixture navigation, display/reload restoration, media cache versioning, connection loss/recovery, responsive/touch behavior |
| Release policy | All-module test invocation; parser-schema decision; evidence manifest binding; draft candidate promotion; exact artifact hash |

No requirement should depend on slow one-off ADB gestures. Emulator coverage must be a reusable bench; physical-device validation remains a release-policy gate only where that policy explicitly requires it.

## 12. Recommended implementation sequence

### Batch A — RC71 trust and release blockers

1. AND-01 native/runtime state revision.
2. AND-02 manual/asynchronous guide failure containment and visible retry.
3. CAT-02 parser-cache schema bump and schema-decision gate.
4. CAT-01 snapshot durability boundary.
5. RUN-01/RUN-02 live identity and session epoch fencing.
6. RUN-14 save-cli collision guard.
7. REL-01 full PR unit matrix.
8. REL-02/REL-03 draft promotion plus packaged emulator acceptance.

### Batch B — persistence and untrusted-input resilience

1. CAT-03 through CAT-12.
2. RUN-03, RUN-04, RUN-05, RUN-06, and RUN-12.
3. AND-03 SAF eligibility and AND-08 guide-projection isolation.
4. REL-05 evidence binding and REL-06 diagnostic privacy.

### Batch C — companion transport and navigation

1. WEB-01 through WEB-06.
2. WEB-09 server parity matrix.
3. REL-04 portable Chromium gate.

### Batch D — performance, recovery, and governance

1. Remaining RUN/AND/WEB P2-P3 requirements.
2. REL-07 through REL-10.
3. Current-readiness documentation cleanup.

Each batch must be smart-synced with `fork/master`, verified as one integrated checkpoint, and pushed without discarding unrelated branch work. Publish a new RC only after a meaningful batch or major fix, not for isolated small corrections.

## 13. Completion criteria

This QA hardening stage is complete only when:

1. All P0 requirements have merged tests and the full cross-module release gate passes.
2. Android native state changes are observable in the live WebView without unrelated actions.
3. Every guide-load failure terminates visibly and recoverably without partial catalog publication or process-level escape at expected boundaries.
4. Catalog invalidation cannot delete SaveRAM recovery state.
5. Live memory/save/mapper authority cannot cross content identity or session epochs.
6. Parser-output changes cannot remain hidden behind compatible stale caches.
7. Untrusted I/O/decompression/network paths have deterministic limits.
8. Android and desktop APIs share structured failure behavior.
9. A packaged emulator exercises the actual bundled WebView/loopback/native integration.
10. Candidate publication behavior matches the documented validation policy and is bound to the exact signed artifact.

## 14. Audit traceability

Primary evidence locations:

- Android setup/state: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`, `setup/RetroArchSetupCoordinator.kt`, `storage/SharedStorageGateway.kt`, `web/ProductionCompanionRuntime.kt`, `web/AndroidLoopbackServer.kt`
- Guide UI/API: `companion-web/src/App.tsx`, `gateway.ts`, `pages/SetupPage.tsx`
- Catalog/parser: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/`, `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/`
- Save/session/runtime: `app/src/main/java/com/darkaxt/dualdex/save/`, `battle/`, `mapper/`, `setup/`; `retroarch-session/`; `memory-mapper-lab/`; `save-cli/`
- Companion servers/simulator: `companion-server/`, `companion-simulator/`, Android loopback server, packaged `companion-web`
- CI/release: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `tools/release/`, `signing/README.md`, `release/POST_RELEASE_CHECKLIST.md`, `docs/v1-requirement-matrix.md`

The strongest cross-cutting correction is to make **revision, content identity, session epoch, and persistence authority explicit**. RC71 improves local error handling, but these four contracts are what prevent corrected native state from being hidden, stale work from crossing sessions, parser caches from masking fixes, and disposable data from deleting durable recovery state.
