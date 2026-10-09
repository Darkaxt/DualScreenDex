# Pokescape remaining native map text

**Status: IN_PROGRESS. Authorization: already_authorized.** Same-ROM required work under [the parent specification](2026-10-07-all-rom-compatibility-completion-design.md). This scheduling does not waive [native mechanics](2026-10-07-pokescape-native-ability-mechanics-design.md), advance to another ROM, or authorize a release before current-ROM acceptance.

Current accepted parser94 catalog: Local names270/484, encounter names201/202, POI text1326/2149; weighted coverage95.83%, mechanics0/298. Preserve the accepted item baseline `3d16d40eb1e0668a182533f94c8eed522b313a1831018afc58422326394634cc` and its independently verified historical ability-text reversal. The prior World Map leaf proves a guarded213-row compiled region-entry reader, not names outside that domain.

## Diagnosis and architecture

`Gen3MapLocationResolver.resolveNamesBySection` returns geometric entries immediately when detailed resolution succeeds. The compiled static-name fallback runs only when detailed resolution fails. This is a candidate omission, not proof that any of the214 missing Local names exists: invalid/out-of-domain sections, native blank output, and context-dependent names must be distinguished first. Public source is structural nomination only.

Recover names independently of geometry only from a proven bounded native name consumer, while preserving World Map geometry acceptance. Keep actual map identities and native section joins. Never invent a label, copy public-source names, use ROM identity/fixed-root routing, or reduce the denominator to hide an unresolved obligation. A native blank/context-dependent result needs its own consumer/applicability evidence, not an absent string alone. Required remaining POI and encounter text remain tracked until independently accepted.

## Requirements

| ID | Acceptance |
|---|---|
| MPT-01 | Diagnose retained evidence and current consumers first. Every new original/parser admission is separately MAIN-reviewed, pinned and exclusively consumed once; bounded registered outputs, no original copies or consumed retries. |
| MPT-02 | Prove actual compiled section/name/geometry domains and complete required map joins. Distinguish static, blank, contextual and outside-domain results; no inferred applicability from geometry or missing text alone. |
| MPT-03 | Implement only a proved generic contract with independent name/geometry authority, fail-closed conflicts, clobbers, malformed/truncated data, extent/work budgets and cancellation. Preserve dynamic-name isolation and existing generations/routes. |
| MPT-04 | Observe relocated functional RED/GREEN and negative/conflicting/cancellation controls through the installed serial Windows Gradle gate, two workers/no parallel and bounded heaps/test fork. Advance parser revision for changed production behavior. |
| MPT-05 | Exact-source one-target parser/native acceptance checks every newly published name/applicability/join, all structural/localized references, complete SQLite/reopen/canonical digest and exact accepted-item baseline reversal. Preserve all unrelated sections, both maps and341 other rows. |
| MPT-06 | Publish sanitized matrix/ledgers only for verified gains; retain/reverify compact proof and inspect/ticket/apply exact cleanup without the Gradle gate. No private paths/ROM identity/assets/native labels in public evidence. |
| MPT-07 | Finish all applicable Local/encounter/POI text and validation obligations, not merely a subset or rounded score. Mechanics and other parent deficits remain mandatory; no device/APK/signing/release action in this diagnostic leaf. |

**Completion:** all applicable requirements independently satisfied, zero blockers/required deferrals, exact-source acceptance and cleanup applied. A diagnosed native blank or out-of-domain result does not by itself authorize changing applicability or claiming whole-ROM completion.
