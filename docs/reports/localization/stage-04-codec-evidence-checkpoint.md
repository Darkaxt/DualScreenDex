# Stage 4 — Source-bound codec evidence exporter

Date: 2026-09-09

## Scope

Task 424 / Task 373 G2 implements a deterministic test-only exporter for all **21 exact official codec identities**. It executes **354 independently declared vectors** and emits identity-bound `vectorSetSha256`, `vectorCount` and `matchedCount` observations for the official matrix. It does not change production decoders, introduce dependencies, select a ROM language or accept any official control. Parser/cache revision **64**, SQL schema **2** and codec versions **1** are unchanged.

The literal oracle includes input bytes, byte limits, token limits, expected text, token kinds/content and every integer counter. It covers malformed/truncated/control sequences and cancellation. Expected values are not derived from observed decoder output. Exact registry singleton, language, version and charset identity are checked; a failing vector produces no passing data summary. Every capture retains `acceptance=false`.

The build copies 27 selected source inputs into test resources. Evidence hashes those actual embedded bytes and 51 selected runtime classfiles separately. These are explicit source/class snapshots, **not complete toolchain provenance or final per-ROM capture proof**. Packaged parser-cli provenance, original-session structural checks, cache/API captures and independently reviewed applicability remain separate matrix obligations.

## Implementation

- `parser-core/build.gradle.kts`: bounded declared test-resource source snapshot task.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/CodecGoldenVectors.kt`: independently declared manifests.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/CodecGoldenEvidence.kt`: exact identity execution, canonical encoding and source/class evidence.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/CodecGoldenEvidenceTest.kt`: exporter contract and rejection tests.

The exporter test can write to a caller-supplied **test JVM** `codecGolden.output` property using exclusive creation. Its file is canonical UTF-8 JSON **plus one LF**; vector manifest digests hash canonical objects without that framing byte. A typical local contract selection is `:parser-core:test --tests '*CodecGoldenEvidenceTest'`; a final capture runner must separately pin its source/runtime and output path.

## Verification

The retained isolated RED progression includes 45 methods with two new assertion failures, then 52 methods with nine exporter assertion failures and 43 existing passes. Two additional evidence tests have GREEN evidence only. A background GREEN launch was interrupted with tool exit 143 before tests; its outcome was retained and the owned process was confirmed stopped. Subsequent unchanged synchronous GREEN runs each passed 54 methods; that interruption is not a behavioral RED or an original-input retry.

A bounded independent review verified the exact four-file delta, literal expectation consistency, actual source/class bytes, canonical manifests and retained JUnit inventory, and found no concrete correctness defect.

After exact-hash parent integration, one fresh owned offline Windows Job ran the same **54 methods in five classes** with two workers and a 900-second watchdog:

| Boundary | Result |
|---|---:|
| JUnit methods | **54 passed, 0 failures/errors/skips** |
| Exact codecs | **21** |
| Golden vectors | **354/354 matched** |
| Elapsed | **227.013 seconds** |
| Gradle outcome | **exit 0; six tasks executed** |
| Parent source pins independently rehashed | **808 unchanged** |
| Actual JUnit XML files independently rehashed | **5** |
| Original selectors / reads | **none / 0** |

The parent result, actual XML and complete log were inspected. Independent coordinator verification recomputed every vector manifest hash/count and the complete canonical object; output equals the reviewed isolated artifact. One coordinator check initially assumed the artifact lacked a terminal LF; a diagnostic identified the framing distinction, and the corrected verification passed without rerunning tests or changing evidence. A separate diagnostic default-codepage decoding error was corrected by reading UTF-8 JSON bytes.

Retained private evidence is identified by digest, not by publishing local paths or payloads:

| Artifact | SHA-256 |
|---|---|
| Parent gate result | `9d3655425b3ec756f88af787b2e6c2406a9b527ed3c1e52b4ef0727cba302ba7` |
| Coordinator verification | `6866607fb78c3b93942f9b5a2d88a64fb76b0c4fa096963c49c64a91f906b6fa` |
| Evidence file, 313,126 bytes including LF | `977c5d0030f20563a239e0d6db1990c3c20dac3d805e3606527908de1b5baf55` |
| Canonical object, 313,125 bytes without LF | `3c53478fb392f780d214e0609f747387fff0d62f6692ad3dc64dc542e4256067` |

## Remaining mandatory work

This checkpoint completes the **exporter implementation**, not G2 final stable-source execution or `LNG-B003`. The current **43-cell / 44-control** matrix still requires all **308 checks and 660 capability dispositions**, complete original-session/report provenance, independent oracles, persistence/reopen and measured API evidence. **Zero final stable-source controls are accepted.**

Task 425 owns normalized boundary/API capture; Task 426 owns its canonical logical catalog digest subcomponent. Tasks 417/419 retain GBA title authority/applicability, and Task 420 retains original Japanese Crystal declared-sign acceptance. Native acceptance preparation is unfrozen and blocked on complete runtime pinning; no new original execution decision follows from this checkpoint. Historical scoped item/contextual-map acceptance is not relabeled as final matrix evidence.

Stage 4 remains **OPEN**; Stages 5–6 remain blocked. The final eligible corpus waits for stable executable changes. No ROM acquisition, corpus run, device/emulator/ADB work, APK, signing, release or raw-ROM publication occurred for this checkpoint.
