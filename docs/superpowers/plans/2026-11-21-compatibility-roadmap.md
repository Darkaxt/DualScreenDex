# DualDex compatibility roadmap — revised 2026-11-21

**Status: IN_PROGRESS.** This replaces the active-execution sections of
[2026-10-07 all-ROM plan](2026-10-07-all-rom-compatibility-completion-plan.md) and the closed
[2026-10-16 corpus/issue33/release plan](2026-10-16-corpus-issue33-release-plan.md). Both remain as history.
The parent goal is unchanged: verified 100% compatibility for every retained library identity
([spec ALL-01–08](../specs/2026-10-07-all-rom-compatibility-completion-design.md)).

## Starting point (verified 2026-11-21)

- Public stable **v1.2.4** (2026-10-18). Assets, the 67 checksums, provenance and the pinned signer `c5a02cec…2fba` were re-verified by a read-only QA pass. Fork is 0 commits behind upstream.
- Corpus baseline (`docs/reports/corpus/2026-10-18-full342-evidence.json`, parser 139, plus the parser 140 Unbound follow-up):
  - 342 identities: **301 selected / 2 ambiguous / 39 unmatched / 0 errors**.
  - Data compatibility: **0 complete / 332 partial / 10 unresolved**.
  - Localization: 286 resolved / 15 unknown.
- Pokescape (95.83%) is **parked** by user decision. ALL-03/ALL-09 no longer name it as the current target.
- `docs/rom-compatibility-matrix.md` dates from 2026-09-14 (333-ROM corpus) and is stale.

## Working model (fitted to the current Claude Code agent)

Safeguards that still apply:
- Fail-closed parsing.
- No invented or guessed data and no title/hash/fixed-address profiles.
- No ROM redistribution.
- Originals must hash unchanged before and after each run.
- Protected GitHub signing.
- Release verification from the downloaded artifacts.

The procedural overhead those safeguards accumulated is replaced as follows:

| Old practice | Replacement |
| --- | --- |
| Separately reviewed one-use "MAIN admissions"/reservations for every original read | Read originals freely inside the local corpus. Record per batch: commit, CLI jar digest, input list, original hashes before/after, report path. |
| Serial single-ROM leaves with a publication + compact proof + signed cleanup ticket per leaf | **Coherent batches**: one generic parser contract that moves several ROMs, then one commit/push, one qualification, one cleanup. |
| Full 342 corpus before any release | **Affected inputs + named controls** for each batch (Oct 18 user correction). The full corpus runs only for a release batch that changes shared parser/catalog code paths, at most once per release. |
| Test-first ceremony before understanding the data | User's original method (2026-08-13): decode with a dirty isolated POC against the real ROM and public source first. Then integrate one generic rule with focused positive/relocated/malformed tests. |
| No agents at all | Parser decoding/implementation stays inline in the main session (user's 2026-08-13 directive). Read-only QA/review agents with distinct scopes are used at batch close and before releases. They never spawn agents. |
| Ledger prose with dense counts | A short ledger table per batch (below) plus a report under `docs/reports/corpus/`. Every deferral gets an ID, target and acceptance condition. |

Builds go through the installed Gradle gate (`gradle-build-gate`), one build at a time.

Release cadence: a new RC/stable only for a meaningful batch or one major fix (memory `dualdex-batch-release-candidates`). On release, comment on and close the exact linked issues.

No live ADB/device gates for parser-only work (memory `dualdex-no-ad-hoc-live-adb-gates`). Hosted packaged acceptance plus passive-catalog controls are the release route.

## Batches, in order

| ID | Batch | Scope | Done when |
| --- | --- | --- | --- |
| B0 | Maintenance and QA fixes | (a) bump `vite` to ≥7.3.7 in `companion-web` (Dependabot #60–62); close stale PR #32 or supersede it with a coordinated vitest upgrade. (b) `RetroArchSetupCoordinator` cancel→complete hand-off in try/finally at `:332`, `:571`, `:673`, `:1107` (QA finding: a throw between `requestCancellation` and `complete` can hang the waiter), plus a throwing-coordinator test. (c) Log, rather than silently drop, theme render failures (`CatalogParser.kt:1062`). (d) Reply to issue #33 with the README path correction for Android 11+ (`Android/data/...`) and upstream RetroArch#19568; close it if the user agrees. (e) Fix the relative "qualification details" link in the release-notes template. (f) Reconcile the `LNG-B002` open/closed conflict (FireRed tasks 388/389). | npm audit clean; host tests green through the gate; issue reply posted; changes pushed. |
| B1 | PR #35 salvage | Cherry-pick, with tests, the valuable fixes from ItsRetroPup's PR #35 that master lacks: (1) the ROM-library scanner quota (`DirectRomLibraryIndexer` still uses `StorageTraversalPolicy.DEFAULT`, 20,000 nodes); (2) the OOM from theme assets holding every map raster (overlaps QA finding: theme selection renders every tiled map, `RomThemeMaterializer.selectAssets`); (3) the parse-time CPU fixes. Drop fix 3 (already shipped in 1.2.2). Parser-touching parts need affected-input qualification. | Each fix has a test; affected controls unchanged; PR #35 closed with credit and links to the integrating commits. |
| R1 | Corpus refresh (user request 2026-11-21) | B1 changes shared parser/storage paths (Emerald clock resolver, hot-path perf, Base64 section encoding, cache revision 141), so run the full 342-identity corpus **once** with the B0+B1 CLI jar. Record commit, jar digest, original hashes before/after and a report under `docs/reports/corpus/`. Compare against the parser 139/140 baseline; any regression blocks B2. This becomes the baseline for B2/B3 and is reused by B4 unless later batches touch shared paths again. Update `release/compatibility-evidence.json` (still revision 140). | Report committed; no unexplained regressions vs. baseline; originals unchanged. |
| B2 | Gen II relationships | `LIB26-G2-RELATIONSHIPS-REMAINING`: evolutions/learnsets NOT_FOUND for Ambrosia, Anniversary Crystal, Crystal Kaizo, Mystic Crystal, Gold/Silver 97 Reforged, Polished Crystal and Crystal Inheritance. First re-check each against the R1 baseline (later Gen II core fixes may have closed some). Then implement one generic compiled-consumer contract. | Affected ROMs gain both datasets with valid references; Gen II controls unchanged. |
| B3 | Unmatched/ambiguous triage | Cluster the 39 unmatched and 2 ambiguous rows by engine/header family. Give every row an ID (`ROW-<identity>`) with target batch or explicit "unsupported engine" reason. Implement the largest shared family-detection gap. | Every row has an ID; the largest cluster selects and parses. |
| B4 | Release batch v1.2.5 | After B0–B2 (B3 optional). Reuse the R1 corpus run; re-run the full corpus only if B2/B3 changed shared parser paths, otherwise affected + controls. B2 changed shared Gen II paths and schema (142), qualified on all 19 Gen II rows plus Gen I controls; decide full vs. affected afresh, and move the evidence JSON and cache decision to 142. Includes DEF-DEPENDABOT-63. Signed RC → protected promotion → stable; downloaded verification; close linked issues. | Public release verified; matrix doc regenerated from the release corpus evidence. |
| B5+ | Next quick wins | In order of verified multi-ROM impact: `LIB26-MAPS` (Heart and Soul, SoulGold, Tourmaline), `LIB26-G3-SIBLINGS` and ROWE domains, `LIB26-G2-LINEAGE/SIBLINGS/OPTIONAL`, `LIB26-G1-OPTIONAL/FORMS`, official-control runs (`LIB26-G2/G3-OFFICIAL-CONTROLS`). Re-rank after each release. | Per-batch ledger row closed. |

## Tracked deferrals (previously untracked)

| ID | Item | Target | Acceptance condition |
| --- | --- | --- | --- |
| DEF-LC-SMOL | Liquid Crystal: four unsupported SMOL map failures and an invalid event-pointer diagnostic (Unbound follow-up report) | B5+ map batch | Maps decode or are recorded as a proven unsupported engine feature. |
| DEF-ME-HIDDEN11 | Modern Emerald: 11 hidden-only challenge areas without proved collection flags | B5+ | Compiled-consumer proof of the hidden-item flag base, or a permanent documented exclusion. |
| DEF-ROWS-41 | 39 unmatched + 2 ambiguous corpus rows | B3 | Each row has an ID and a target. |
| DEF-POKESCAPE | Pokescape remaining POI text/mechanics (95.83%) | Parked by user | Resumes only on explicit user request. |
| DEF-G3-LIGHTING-LIVE | Gen III seamless-map live visual check (memory tasks #123/#130, RC29) | Downstream live observation | The user reports the visuals on a current release, or the item is closed as superseded. |
| DEF-G2-AMBROSIA-MOVE-DOMAIN | Ambrosia: learnsets use moves up to 254; its Metronome has no exclusion loop, so no compiled move-count authority (B2) | B2 (reopened; source-backed) | A compiled consumer proves the 254-move domain; then evolutions and learnsets resolve. |
| DEF-G2-ANNIVERSARY-SPECIES-252 | Anniversary Crystal: species 252 (PHANCERO) exists, but the parser's species domain is 251, so the compiled pre-evolution walker never matches (B2) | B2 (reopened; source-backed) | The species domain resolves to 252 from a compiled consumer; then relationships resolve. |
| DEF-G2-POLISHED-ENGINE | Polished Crystal and Crystal Inheritance: relationship consumers differ from the pokecrystal-shaped contract (B2) | B2 (reopened; source-backed) | A separate compiled contract for the Polished-engine relationship stream. |
| DEF-DEPENDABOT-63 | Dependabot #63: guava ≤33.7.1 (high), transitive through the Gradle plugin classpath | B4 | A verified build-classpath constraint to ≥33.7.2; the alert closes. |
| DEF-MATRIX-STALE | `docs/rom-compatibility-matrix.md` predates the 342 library | B4 | Regenerated from release evidence. |

## Storage (D:/Temp, ~23 GB)

- Keep:
  - the active checkout;
  - `dualdex-full-corpus-release-2026-10-16` (the 139 baseline catalogs);
  - `dualdex-unbound-followup-2026-10-18`;
  - `dualdex-release-retained`;
  - both official-control directories.
- Clean through `transactional-cleanup` after a final check:
  - the seven empty `dualdex-catalog-clear*` folders;
  - `dualdex-pokescape-map-text-remainder-2026-10-09` (daemon logs);
  - `dualdex-expansion-controls`;
  - `dualdex-localization-corpus` extraction staging/cache;
  - `dualdex-pokescape-teaching-sources-2026-10-12`, after archiving its small JSON proofs to G:.
- Verify before touching: `dualdex-localization-corpus-source` (hard-linked archive view, possibly the live library input).

## Batch ledger

| Batch | Status | Commit | Evidence |
| --- | --- | --- | --- |
| B0 | Done | f0fc4a7c, test fixture fix | Issue #33 answered and closed; v1.2.4 notes link fixed; PR #32 closed as stale; npm audit 0, companion-web 365/365, release tools 107/107. |
| B1 | Done | a23be727, f27e2736, 32f65b9e, 98028c12, 90cc4c67, 25bc99f8 (from PR #35) | Skipped: 104c3479 (shipped in 1.2.2), 2ee4e886 (superseded by lazy theme selection), 06b80bb7 (cache bump replaced by 141), 17d8089d (merge). |
| R1 | Done | 160861fe | [R1 report](../../reports/corpus/2026-11-21-r1-corpus-refresh.md): 342 inputs, 0 errors, originals unchanged; 0 regressions, 8 LOCAL_MAP gains (mean 79.875%→79.911%); 2 h 52 m vs 4 h 05 m. Evidence JSON switch deferred to B4. Host tests before R1: app 693, parser-core 2658, catalog-store 130, 0 failed. |
| B2 | In progress (reopened: 4 of 8 ROMs; acceptance unmet) | ea77e7e7, 3b823905, 10cd23f3 | [B2 report](../../reports/corpus/2026-11-21-b2-gen2-relationships.md): 22 affected inputs, 0 errors, originals unchanged; Crystal Kaizo, Gold/Silver 97 Reforged and Mystic Crystal gain EVOLUTIONS and LEARNSETS; 0 regressions, controls unchanged. Ambrosia, Anniversary Crystal, Polished Crystal and Crystal Inheritance deferred (DEF-G2-*). Host tests: parser-core 2667, catalog-store 130, app 693, 0 failed. Parser schema 142. |
| B3 | Pending (after B2) | — | — |
| B4 | Pending | — | — |
