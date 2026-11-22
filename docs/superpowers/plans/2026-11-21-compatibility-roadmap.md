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

Release cadence (user, 2026-11-22): the work is raising corpus compatibility. Stages do not end in releases; publish only when accumulated gains are meaningful or one major fix justifies it (memory `dualdex-batch-release-candidates`). On release, comment on and close the exact linked issues.

No live ADB/device gates for parser-only work (memory `dualdex-no-ad-hoc-live-adb-gates`). Hosted packaged acceptance plus passive-catalog controls are the release route.

## Batches, in order

| ID | Batch | Scope | Done when |
| --- | --- | --- | --- |
| B0 | Maintenance and QA fixes | (a) bump `vite` to ≥7.3.7 in `companion-web` (Dependabot #60–62); close stale PR #32 or supersede it with a coordinated vitest upgrade. (b) `RetroArchSetupCoordinator` cancel→complete hand-off in try/finally at `:332`, `:571`, `:673`, `:1107` (QA finding: a throw between `requestCancellation` and `complete` can hang the waiter), plus a throwing-coordinator test. (c) Log, rather than silently drop, theme render failures (`CatalogParser.kt:1062`). (d) Reply to issue #33 with the README path correction for Android 11+ (`Android/data/...`) and upstream RetroArch#19568; close it if the user agrees. (e) Fix the relative "qualification details" link in the release-notes template. (f) Reconcile the `LNG-B002` open/closed conflict (FireRed tasks 388/389). | npm audit clean; host tests green through the gate; issue reply posted; changes pushed. |
| B1 | PR #35 salvage | Cherry-pick, with tests, the valuable fixes from ItsRetroPup's PR #35 that master lacks: (1) the ROM-library scanner quota (`DirectRomLibraryIndexer` still uses `StorageTraversalPolicy.DEFAULT`, 20,000 nodes); (2) the OOM from theme assets holding every map raster (overlaps QA finding: theme selection renders every tiled map, `RomThemeMaterializer.selectAssets`); (3) the parse-time CPU fixes. Drop fix 3 (already shipped in 1.2.2). Parser-touching parts need affected-input qualification. | Each fix has a test; affected controls unchanged; PR #35 closed with credit and links to the integrating commits. |
| R1 | Corpus refresh (user request 2026-11-21) | B1 changes shared parser/storage paths (Emerald clock resolver, hot-path perf, Base64 section encoding, cache revision 141), so run the full 342-identity corpus **once** with the B0+B1 CLI jar. Record commit, jar digest, original hashes before/after and a report under `docs/reports/corpus/`. Compare against the parser 139/140 baseline; any regression blocks B2. This becomes the baseline for B2/B3 and is reused by B4 unless later batches touch shared paths again. Update `release/compatibility-evidence.json` (still revision 140). | Report committed; no unexplained regressions vs. baseline; originals unchanged. |
| B2 | Gen II relationships | `LIB26-G2-RELATIONSHIPS-REMAINING`: evolutions/learnsets NOT_FOUND for Ambrosia, Anniversary Crystal, Crystal Kaizo, Mystic Crystal, Gold/Silver 97 Reforged, Polished Crystal and Crystal Inheritance. First re-check each against the R1 baseline (later Gen II core fixes may have closed some). Then implement one generic compiled-consumer contract. | Affected ROMs gain both datasets with valid references; Gen II controls unchanged. |
| B3 | Unmatched/ambiguous triage | Cluster the 39 unmatched and 2 ambiguous rows by engine/header family. Give every row an ID (`ROW-<identity>`) with target batch or explicit "unsupported engine" reason. Implement the largest shared family-detection gap. | Every row has an ID; the largest cluster selects and parses. |
| B4 | Release batch v1.2.5 | After B0–B2 (B3 optional). Reuse the R1 corpus run; re-run the full corpus only if B2/B3 changed shared parser paths, otherwise affected + controls. B2 changed shared Gen II paths and schema (143), qualified on all 19 Gen II rows plus Gen I controls; decide full vs. affected afresh, and move the evidence JSON and cache decision to 143. Includes DEF-DEPENDABOT-63. Signed RC → protected promotion → stable; downloaded verification; close linked issues. | Public release verified; matrix doc regenerated from the release corpus evidence. |
| B5+ | Next quick wins (GBA first) | The GBA ledger remainders above (C1, C4, expansion learnsets/dex domain, table extent, zero tail, move PP), then, in order of verified multi-ROM impact: `LIB26-MAPS` (Heart and Soul, SoulGold, Tourmaline), `LIB26-G3-SIBLINGS` and ROWE domains, `LIB26-G2-LINEAGE/SIBLINGS/OPTIONAL`, `LIB26-G1-OPTIONAL/FORMS`, official-control runs (`LIB26-G2/G3-OFFICIAL-CONTROLS`). Re-rank after each stage by measured corpus gain. | Per-batch ledger row closed. |

## Tracked deferrals (previously untracked)

| ID | Item | Target | Acceptance condition |
| --- | --- | --- | --- |
| DEF-LC-SMOL | Liquid Crystal: four unsupported SMOL map failures and an invalid event-pointer diagnostic (Unbound follow-up report) | B5+ map batch | Maps decode or are recorded as a proven unsupported engine feature. |
| DEF-ME-HIDDEN11 | Modern Emerald: 11 hidden-only challenge areas without proved collection flags | B5+ | Compiled-consumer proof of the hidden-item flag base, or a permanent documented exclusion. |
| DEF-ROWS-41 | 39 unmatched + 2 ambiguous corpus rows | Closed 2026-11-22 (B3) | Each row has an ID and a target ([B3 report](../../reports/corpus/2026-11-22-b3-unmatched-triage.md)). |
| LIB26-GBA-HEADER-1B8 | C1: 7 Emerald-derived hacks with a 0x1B8, ambiguous or zeroed-slot published header, then per-hack species ABIs | GBA focus (B3a) | 0x1B8 candidate done at 20f4b0af: Scorched Silver, Parallel Emerald and Team Rocket Edition select ([B3a report](../../reports/corpus/2026-11-22-b3a-gba-focus.md)). Still open: Scale x Fang (misaligned table start), Light and Shadow and WaveBlue (zeroed ability slots), Elite Redux (missing ball slots, innates ABI). |
| LIB26-GBA-FRLG-EM-TIE | C3: Ultra Violet and Lugias Ocean tie between the FireRed and Emerald probes | Closed 2026-11-22 (c4697b98) | GetMoney SaveBlock2 key-offset discriminator: both select as FireRed (99.50%, 91.15%); controls unchanged ([B3a report](../../reports/corpus/2026-11-22-b3a-gba-focus.md)). |
| LIB26-GBA-EXPANSION-ABI | C4: 9 pokeemerald-expansion rows with unsupported SpeciesInfo strides or stale pointers | GBA focus (B3a) | Each expansion version's layout is source-backed and selects. Partial at b3d2fec7: Pisces and Sovereign of the Skies select through proven species extents ([B3a report](../../reports/corpus/2026-11-22-b3a-gba-focus.md)). Still open: Too Many Types, Unova Emerald, Too Many Types 2, Recordkeepers, Voyager, Voyager Demo, Lazarus. Follow-ups LIB26-GBA-EXPANSION-LEARNSETS and -DEX-DOMAIN. |
| LIB26-GBA-EXPANSION-LEARNSETS | Pisces: expansion `LevelUpMove {u16 move; u16 level}` learnsets and evolutions not materialized; abilities missing on Pisces and Sovereign | GBA focus (B3a) | Source-backed expansion learnset/evolution/ability decoding materializes them, or a documented exclusion. |
| LIB26-GBA-EXPANSION-DEX-DOMAIN | Sovereign of the Skies: 997 of 1208 slots excluded from the Pokédex semantic domain by the zero-Dex rule | GBA focus (B3a) | Audit against the species-to-dex map; the domain matches the hack's national Dex. |
| LIB26-GBA-ZERO-TAIL | Elysium B: base-stat table ends in a 40-row zero tail | GBA focus (B3a) | A compiled count authority proves the extent, or it stays fail-closed. |
| LIB26-GBA-TABLE-EXTENT | Gaia, Recharged Emerald/Yellow and Noon: tables run into learnset data, or have no proven end | GBA focus (B3a) | A bounded extent proof selects them. |
| LIB26-GBA-MOVE-PP | Moon Galaxy: active move 506 has PP above the typed codec's 64 | GBA focus (B3a) | A source-backed PP domain for the hack's codec, or a documented exclusion. |
| LIB26-G12-CUSTOM | C8: 6 custom Gen I/II engines (Prism, Orange, Peridot, Brown, Fools Gold, BW3 Genesis) | After the GBA focus | Source-backed engine support per row, or recorded as unsupported. |
| DEF-POKESCAPE | Pokescape remaining POI text/mechanics (95.83%) | Parked by user | Resumes only on explicit user request. |
| DEF-G3-LIGHTING-LIVE | Gen III seamless-map live visual check (memory tasks #123/#130, RC29) | Downstream live observation | The user reports the visuals on a current release, or the item is closed as superseded. |
| DEF-G2-AMBROSIA-MOVE-DOMAIN | Ambrosia: no Metronome exclusion loop, so no compiled move-count authority (B2) | Closed 2026-11-22 (efcadaff) | Battle Tower bound fallback proves 254 moves; EVOLUTIONS and LEARNSETS available. |
| DEF-G2-ANNIVERSARY-SPECIES-252 | Anniversary Crystal: species 252 (PHANCERO) beyond the inherited 251 (B2) | Closed 2026-11-22 (efcadaff) | Pre-evolution walker bound proves 252 species; EVOLUTIONS and LEARNSETS available. |
| DEF-G2-POLISHED-ENGINE | Polished Crystal and Crystal Inheritance: compact-engine relationship streams (B2) | Closed 2026-11-22 (efcadaff, 7a0e10cb) | Compact relationship contract; both ROMs gain EVOLUTIONS and LEARNSETS. |
| DEF-G2-INHERITANCE-METHOD-VOCAB | Crystal Inheritance: method 3 is HOLDING, not trade; 12 species' evolutions refused (fail closed) | B5+ (`LIB26-G2-OPTIONAL`) | A parsed per-ROM method vocabulary drives the presentation; those species show their real conditions. |
| DEF-DEPENDABOT-63 | Dependabot #63: guava ≤33.7.1 (high), transitive through the Gradle plugin classpath | Closed 2026-11-22 (1e8777ee) | Resolution strategy forces 33.7.2 and `verifySecureBuildDependencies` enforces it; GitHub's static graph still read the plugin POMs, so the alert was dismissed as inaccurate with that evidence. |
| DEF-MATRIX-STALE | `docs/rom-compatibility-matrix.md` predates the 342 library | Closed 2026-11-22 (B4) | Regenerated from the B4 full342 run ([B4 report](../../reports/corpus/2026-11-22-b4-release-corpus.md)). |
| DEF-PACKAGED-INDEX-FAILURE-TIMING | v1.2.5-rc.1 release run attempt 1: `installedPackageDeliversWebLoopbackRecoveryAndCacheReopen` timed out (30 s) waiting for the failed-index projection; passed on rerun and on runs 45022647102 and 45025398564 | B5+ | Root cause found (hosted timing vs. a discovery race in `RetroArchSetupCoordinator.indexSharedStorage`) and either fixed with a deterministic test or bounded by evidence. |
| DEF-RELEASE-POLICY-AUDIT-AUTH | `release.yml` policy audit calls the GitHub API unauthenticated; attempt 1 of the RC run got 403 (anonymous runner rate limit) | Next release-tooling change | Authenticated reads with the workflow token, verified by a release run. |

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
| B2 | Done 2026-11-22 | ea77e7e7, 3b823905, 10cd23f3, efcadaff, 7a0e10cb | [B2 report](../../reports/corpus/2026-11-21-b2-gen2-relationships.md): 22 affected inputs, 0 errors, originals unchanged; all 8 target ROMs gain EVOLUTIONS and LEARNSETS (Crystal Inheritance EVOLUTIONS partial 242/254, DEF-G2-INHERITANCE-METHOD-VOCAB); 0 regressions, 14 controls unchanged. Host tests: parser-core 2672, catalog-store 130, app 693, 0 failed. Parser schema 143. |
| B3 | Done 2026-11-22 | 5f179dae, 4f523473, ff0a24cd | [B3 report](../../reports/corpus/2026-11-22-b3-unmatched-triage.md): all 41 rows have IDs and targets. C2 rule: Moon Galaxy and Sun Sky now SELECTED (37%→82% and 86%). Run 1 (82 inputs) found the Elysium B regression, fixed at ff0a24cd. Run 2 (8 inputs) is identical to R1 apart from the two gains. 82 originals unchanged. Parser schema 144. Next: the GBA focus (B3a). |
| B3a | Done 2026-11-22 | 20f4b0af, c4697b98, b3d2fec7 | [B3a report](../../reports/corpus/2026-11-22-b3a-gba-focus.md): C1 0x1B8 header (3 select), C3 FireRed/Emerald tie closed (2 select), C4 expansion extents (2 select); remainders ledgered above. |
| B4 | Done 2026-11-22 | 1e8777ee (evidence), b89c5258 (RC1), 6c3fbb4a (stable) | [v1.2.5](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.2.5) published and independently verified (68 assets, 67 checksums, pinned signer, 62 evidence assets identical to RC1). [B4 report](../../reports/corpus/2026-11-22-b4-release-corpus.md): one full342 run, 310/0/32, 0 errors, 18 up, 0 down, mean 81.513%. Five saved-catalog passive controls; matrix regenerated; Dependabot #63 dismissed as inaccurate (build classpath pinned and enforced). No open issues to close. |
