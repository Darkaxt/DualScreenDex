# Stage 4 name-consumer checkpoint

Status: **verified bounded name consumers; not Stage 4 closure**.

## Scope and authority

- The generic variable-name sequence matcher requires caller-established record starts. It follows complete terminated tokens, rejects invalid hidden prefixes, preserves nearest/lower-offset selection with overflow-safe arithmetic, and checks cancellation. Matching text or proximity never proves the supplied root.
- Existing Gen II English relocation remains a separate, explicitly named `gbEnglish` discovery heuristic. Its caller requires that exact codec singleton, canonical move-data corroboration, and full relocated-table validation. Multibyte codecs cannot enter that raw-byte discovery path.
- Ability-name row zero requires an actual terminator token; padding begins at `consumedBytes`, not the first terminator-valued byte. Unterminated full-width names require invalid-free token consumption. Japanese/Korean names reuse script-shape plausibility without changing Western case behavior or sparse sentinel semantics.
- Malformed active ability names retain independently referenced numeric identities while their name capability remains unavailable. Name resolution checks cancellation before early outcomes and each direct/general proposal.
- Gen II landmark decoding separates exact-codec glyph/substitution ownership from source-backed display-flow controls. Native script plausibility remains a text-shape check, not final ROM-language authority.

The production ability-name path already supplies the manifest's exact codec. This checkpoint does not change language authority, codec identities/versions, parser/catalog schemas, or shared description decoding.

## Landmark source boundary

The Korean landmark display-flow fixture is based on the pinned [Korean text consumer](https://github.com/Narishma-gb/pokegold-kr/blob/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4/home/text.asm) and [charmap](https://github.com/Narishma-gb/pokegold-kr/blob/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4/constants/charmap.asm).

Korean DONE is `5E`, not Western `57`; its static line controls use their own dispatch. DONE ends display but does not eliminate the requirement for a later complete terminator token within the supplied byte window. A terminator-valued Korean trail cannot satisfy that requirement. Runtime WRAM name substitutions and scrolling controls are not fabricated as fixed landmark literals. An initial synthetic test incorrectly treated runtime names as static substitutions; it was corrected from source before the supplemental behavioral gate.

## Regression evidence

- Four locator regressions first reproduced raw trail splitting, suffix promotion, rejection/misidentification of supplied starts, and invalid prefixes hidden by decoded-text equality.
- Four ability-name regressions reproduced false sentinel termination, padding starting inside a token, non-native name acceptance, and truncated controls accepted as complete-width names.
- Two ability-name resolver regressions reproduced ignored cancellation before empty/unsupported-exact outcomes and between proposals.
- The initial combined ability/landmark red gate reported 54 tests with 15 failures: four ability decoder, two ability resolver, and nine landmark failures.
- A fresh supplemental five-class run, after the ability/locator fixes and source-corrected landmark fixtures, reported 137 tests with 11 failures, all in the still-unfixed landmark decoder. All 112 ability-name and locator tests passed; no errors or skips occurred.

A scoped independent read-only review found no additional concrete defect in the ability-name changes. The coordinator inspected the locator implementation and its regression diff. These focused results do not constitute full-parser, official-matrix, persistence/API, or corpus acceptance.

## Fresh combined verification

After all executable changes were stable, the coordinator inspected the landmark implementation and ran:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test \
  --tests 'com.enrpau.dualscreendex.parser.text.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.validate.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityLegacyParityTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2LandmarkNameCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2WorldMapResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2CompiledNamePairResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.FamilyParsersAbilityResolutionTest' \
  --tests 'com.enrpau.dualscreendex.parser.family.FamilyProbeCoordinatorTest' \
  --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL; 316 cases across 21 classes: 314 passed, 0 failures, 0 errors, 2 skipped**. Compilation and tests executed freshly. All 25 landmark decoder tests passed. The skipped external controls require `DUALDEX_CLOUD_WHITE_2_ROM` and `DUALDEX_POKEGOLD_ROM`; neither was supplied. In particular, this run does not claim live world-map resolver acceptance. Existing compiler/Gradle warnings remain. `git diff --check` passed.

## Mandatory remaining work

`LNG-B002` remains open. Task 379 must thread cancellation through the Gen II world-map caller and preserve independently validated numeric landmark bindings when localized names fail. Native end-to-end localized validation remains required. Decoder-only tests do not close those caller contracts.

`LNG-B001` still requires native language corroboration and unmarked Japanese Gen I structural trials. `LNG-D005` still requires native compiled type semantics. `LNG-B003` still requires all exact official controls, the complete sanitized 43-cell matrix, persistence/reopen/API acceptance, one final current-corpus run after Stage 4 executable changes are final, and public Stage 4 closure.

No required official cell is deferred. Stage 5 remains blocked. No ROM payload, private memory, corpus run, release, signing, emulator, physical-device, or ADB action is part of this checkpoint.
