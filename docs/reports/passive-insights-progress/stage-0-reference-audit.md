# Stage 0 reference corpus and semantic vocabulary audit

## Outcome

Stage 0 is complete with 0 blockers and 0 errors. It changes no APK, runtime, UI, release metadata, or tag.

The authenticated corpus contains 1,003 base achievements across the 11 exact official English Generation I–III controls. The fail-closed classifier now resolves 1,003 / 1,003 references (100.00%). The final 120 descriptions were recovered through an exact-ID, source-description-fingerprinted semantic registry containing no source prose, trigger expressions, addresses, or offsets. Semantic classification does not imply runtime support: exact runtime equivalents for those recovered objectives remain 0 / 120 (`NOT_FOUND`) until their full fact, temporal, or game-adapter contracts exist.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | ---: | --- |
| Section 4.1 exact official source set | Extractor freezes 11 unique game IDs and manifest identity/count metadata | Exact-ID test plus duplicate-ID assertion | Gen I 259 / 259, Gen II 260 / 260, Gen III 484 / 484 | 1,003 / 1,003 extracted (100.00%) | SATISFIED |
| Section 4.2 bounded authenticated research fields | Exact permitted top-level/achievement field sets; cache reuse rejects additional fields; derived JSON contains hashes rather than source prose | Sanitization, forbidden-field, malformed-payload, secret-leak, and cache-resume tests | 11 / 11 payloads validate; 0 forbidden fields; 0 trigger expressions; 0 credentials | 11 / 11 payloads (100.00%) | SATISFIED |
| Section 4.3 fail-closed semantic classification | Deterministic family rules plus an exact-ID, description-fingerprinted semantic recovery registry | High-signal family, stale-fingerprint, orphan, duplicate, closed-schema, and deterministic-output tests | 1,003 classified; 0 unclassified | 1,003 / 1,003 (100.00%) | SATISFIED |
| Section 5.2 semantic fact declarations | Every classified record declares stable required facts and capability roles; no address/offset/parser-family key is emitted | Classification shape and closed-schema tests | 1,003 / 1,003 classified records declare required facts | 1,003 / 1,003 (100.00%) | SATISFIED |
| Section 5.3 event and temporal declarations | Every classified record declares required events and one closed temporal scope | Classification shape and closed-schema tests | 1,003 / 1,003 records declare events and temporal scope | 1,003 / 1,003 (100.00%) | SATISFIED |
| Section 5.3 live transition evaluation | Assigned to `SnapshotTransitionEvaluator` implementation in Stage 3; Stage 0 intentionally changes no runtime | Not applicable to research-only Stage 0 | No runtime tuple is consumed by Stage 0 | 0 / 0 applicable Stage 0 checks | DEFERRED |
| Section 5.4 data-defined challenge vocabulary | Records declare independent template key/title/description, family, semantic inputs, temporal scope, operators, visibility, tier, outcome, reason, and source hashes/IDs | Determinism and no-source-prose tests | 1,003 / 1,003 classified records satisfy the commit-safe record schema | 1,003 / 1,003 (100.00%) | SATISFIED |
| Section 5.4 executable applicability/completion predicates | Runtime evaluator and instantiated challenge definitions are assigned to Stage 3, then expanded in Stage 6 | Not applicable to research-only Stage 0 | No runtime claims made | 0 / 0 applicable Stage 0 checks | DEFERRED |
| Section 11.1 closed portable predicate vocabulary | Schema defines the required 21 operators; classified records select only operators from that closed set | Exact operator-enum and unknown-operator rejection tests | 1,003 / 1,003 classified records use only the closed vocabulary | 1,003 / 1,003 (100.00%) | SATISFIED |
| Section 11.2 portability tiers | Every record declares Tier 1–3; no recovered semantic is hidden behind Tier 4 | Tier bounds, recovery-path/tier consistency, and summary tests | Tier 1: 449; Tier 2: 470; Tier 3: 84; Tier 4: 0 | 1,003 / 1,003 tiered (100.00%) | SATISFIED |
| Section 15.2 classified / extracted | Numeric report separates classified and unclassified references | Report-rendering test | 1,003 / 1,003 | 100.00% | SATISFIED |
| Section 15.2 expressible / classified | A record is expressible only when it has a known template key and Tier 1–3 semantic binding | Summary and schema tests | 1,003 / 1,003 | 100.00% | SATISFIED |
| Section 15.2 applicable / total per ROM | Requires runtime capability binding against each official/hack ROM tuple in Stages 3 and 6 | Not applicable to research-only Stage 0 | No per-ROM applicability claim made | 0 / 0 applicable Stage 0 checks | DEFERRED |
| Section 15.2 fully observable / applicable | Requires live fact/event observability in Stage 3 | Not applicable to research-only Stage 0 | No observability claim made | 0 / 0 applicable Stage 0 checks | DEFERRED |
| Section 15.2 validated / fully observable | Requires real ROM/save/memory tuple validation in Stages 3 and 6 | Not applicable to research-only Stage 0 | No runtime validation claim made | 0 / 0 applicable Stage 0 checks | DEFERRED |

## Numeric classification evidence

| Generation | Extracted | Classified | Percentage |
| ---: | ---: | ---: | ---: |
| I | 259 | 259 | 100.00% |
| II | 260 | 260 | 100.00% |
| III | 484 | 484 | 100.00% |
| **Total** | **1,003** | **1,003** | **100.00%** |

| Portability tier | Records | Percentage of extracted |
| ---: | ---: | ---: |
| 1 | 449 | 44.77% |
| 2 | 470 | 46.86% |
| 3 | 84 | 8.37% |
| 4 | 0 | 0.00% |

## Verification

- Focused semantic-classifier tests: 12 / 12 passed.
- Authenticated controls represented: 11 / 11.
- Manifest payload fingerprints matching the sanitized research cache: 11 / 11.
- Sanitized payloads with the exact permitted field sets: 11 / 11.
- Derived records containing exact source `title` or `description` properties: 0 / 1,003.
- Recovered semantic records with exact runtime equivalents: 0 / 120 (`NOT_FOUND`, not `NOT_APPLICABLE`).
- Imported trigger bytecode, trigger expressions, addresses, or offsets: 0.
- Repository `.env` files or embedded API credentials: 0.

## Blockers and errors

- Blockers: 0.
- Errors: 0.
