# Stage 4 — complete retained item candidate cohort

Date: 2026-09-09. Task 428 / Task 390 / `LNG-B002`–`LNG-B003`; Stage 4 remains open.

A new immutable, private item-only candidate handoff now contains **44 exact controls and 3,688 referenced plaintext values**. Main reviewed the existing 43-control handoff and the separate Japanese FireRed recovery directly and serially, without agents. This closes the missing FireRed **candidate-source** row, not final item or matrix acceptance.

## Verification performed

- The fabricated binder suite ran **17/17 tests**, zero failures/errors/skips. Main inspected the actual log; these are identity/domain/value/slice tests, not official-control execution.
- All **16** pinned predecessor artifacts matched their byte lengths and hashes. Direct stored-artifact verification checked **102** external source-pin entries: 101 match their expected pins; one intentionally rejected historical mismatch remains rejected and supplies no candidate values.
- All **3,604** predecessor source segments matched both the original retained side-artifact bytes and their bundled slices. All **3,561** candidate values matched their source pointers and retained display policy, with exact requested domains, control identities and all 44 inventory rows reconciled.
- FireRed's compiled proof matches the original frozen handoff pin. Its seven named source dependencies match their hashes and lengths. The **127** unique referenced IDs include zero and exclude dynamic ID 175; the complete requested domain is preserved. Every referenced row is publishable, has zero invalid/control units and matches its retained UTF-8 plaintext hash. The **40** retained code-window self-hashes and **six** frozen sample digests were checked.
- The new handoff preserves the full old source bundle as an unchanged prefix and all **43** previous control rows except their new proof-artifact references. The earlier **39** candidate fragments also remain identical apart from proof references. The new bundle has **3,732** source segments; appended FireRed segments are exact original JSON byte slices, not reserialized substitutes.
- New files were written exclusively in a new private directory, then read back and hash/byte-verified. All 44 candidate domains and 3,688 values were reconciled. Candidates still omit final API pointers and dispositions, propose no exclusions, and fail the final binding guard with `EVIDENCE_BINDING`.

## Provenance limits

The historical materializer incorporates the then-current validator/assembler source hashes. Those contract files changed after the old candidate was produced. Main therefore verified the stored artifacts directly instead of regenerating them, rewriting their pins or treating current-contract drift as evidence corruption. The successor preserves and explicitly labels those historical contract references; they are not current executable bindings or linguistic oracles.

FireRed expectations use retained `records[].text` verbatim. The retained generator source shows independent charmap decoding before its comparison to the historical overlay, but this review did not re-establish that generator file's historical execution identity. Complete raw item records are not retained, so **no 127-record raw redecode is claimed**. Code-window hashes and sample agreement prove retained consistency, not whole-original uniqueness or a new execution. Other historical proof and normalization limits also remain unchanged.

`FIELD_ACCEPTANCE` remains a **proposed, unissued** proof kind: `reviewStatus=UNREVIEWED_NOT_ISSUED`, `candidateAcceptance=false`, `acceptance=false`, and `binding=null`. Candidate-source review is not independent final linguistic ratification. No cache or API observation supplied expected plaintext.

## Retained evidence identifiers

Raw source slices, original paths and plaintext manifests remain private; only receipt identifiers are published.

| Artifact | SHA-256 |
| --- | --- |
| 17-test log | `77390345c471e67508862f9b6c8f00ad7eb2ee9b8e885ecc301cf430cda39afa` |
| Main retained verifier | `87ee5d783ba86a76d1d52d07592b7aaab8eb8cfeddc33d7e81da9c73622c87e5` |
| Main retained review result | `2fe94370e851a33ee63fd82e455dc86a7279f989a6095b1e82e9c6ad3ada9396` |
| Successor builder/verifier | `a0795fbc065811e5993197949628d21c8870aa2c72e4b69f1a3b2762a3ff50da` |
| Successor verification receipt | `e24b33ae0e5bc74c5ae506e2e64642248bcd45172e1cc773ba4d1b690e58de6d` |
| 44-control candidate manifest | `d762d1832cde542fe11be18e01ac4910fa4afc4033da5ec36d0c64195777b44b` |
| Candidate proofs | `cfa4fb13f0de61d710e65e9e1d2057b2b620bc8e940fa2fdfe756830e80d71bc` |
| Source bindings | `693225c10cc14eb25899b2b4a395a95255c7a9622485052735e0d5a52e2b5c46` |
| Source-slice bundle | `f7ace158ecbd7608f26d3a10ed9d8ef85bfff09c78350a4ccf9b5a4b5c04d777` |

## Remaining mandatory scope

Task 428 / Task 390 retains final independent `FIELD_ACCEPTANCE` issuance and normalization ratification; Task 425 / Task 430 retains current-domain/API pointer parity and the final five-field source/report/receipt/generator binding. Acceptance requires complete current referenced domains, not historical count agreement. Missing or future requested IDs cannot be silently excluded. The candidate manifest is not directly eligible for G6.

No production/parser/catalog/cache code or version changed. Parser/storage/codecs remain **64/2/1**, report/generator **16**, receipt envelope **1** and logical digest **1**. No original ROM reads/stat/acquisition, cache reads/stat, corpus execution, Android/device/emulator/ADB work, APK, signing, release or PR occurred. Historical artifacts remain immutable.

Zero final stable-source controls are accepted. All fifteen capability dispositions, the current 43-cell/44-control matrix, one final eligible corpus after executable stabilization and published Stage 4 closure remain mandatory. Stages 5–6 remain sequentially blocked.
