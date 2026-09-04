# Stage 4 shared text-validation checkpoint

Status: **verified shared checks; Stage 4 remains open**.

## Implemented scope

- Western description word/case checks retain their existing behavior.
- Japanese and Korean shared plausibility checks use native script content rather than Latin lowercase or whitespace word boundaries. Japanese recognizes Han, Hiragana, and Katakana; Korean recognizes Hangul. Native letters must constitute at least half of alphanumeric content, permitting abbreviations and digits without accepting Latin-only or digit-padded text. Incompatible letter scripts are rejected.
- Native description minimum length counts alphanumeric characters, not punctuation or whitespace padding. These are plausibility heuristics, never language or mechanical authority.
- Full-width Japanese/Korean names can precede terminated names during fixed-name count inference.
- `TableValidators.variableNames` decodes bounded complete tokens and advances by `consumedBytes`. A Korean trail byte equal to the terminator no longer splits a token. Invalid terminated records retain their boundaries; an unterminated record stops traversal and makes the table incompatible, even if its valid-prefix ratio exceeds the usual threshold.
- Invalid variable-name byte windows/counts fail closed before reaching decoder preconditions.

No resolver discovery, language authority, codec identity/version, catalog schema, UI setting, or runtime-language selection changes are included.

## Verification

Initial focused regressions: 10 tests executed, 7 failed. A subsequent lost-boundary test also failed before the traversal correction. Review identified single-native-letter digit padding; its added regression failed before the native-content ratio included digits.

Fresh final gate:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test \
  --tests 'com.enrpau.dualscreendex.parser.text.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.validate.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.catalog.MoveDescriptionMaterializerTest' \
  --tests 'com.enrpau.dualscreendex.parser.catalog.AbilityDescriptionMaterializerTest' \
  --rerun-tasks
```

Result: **BUILD SUCCESSFUL; 195 tests passed, 0 failures, 0 errors, 0 skipped**, across 13 classes. Existing compiler/Gradle warnings remain. `git diff --check` passed.

## Required Stage 4 work

- `LNG-B001`: native script corroboration in `RomLanguageAuthority` and structural Japanese Gen I trials; header hints remain insufficient.
- `LNG-B002`: remaining token-safe locator/description/ability/location consumers and exact-codec propagation. In particular, `locateVariableNameSequenceNear` still uses raw byte boundaries. Removing its previous-byte filter alone would permit suffix matches and is not a fix.
- `LNG-D005`: compiled Japanese/Korean type semantics, without presumed numeric order.
- `LNG-B003`: all required official controls through parse/materialize/persist/reopen/API, sanitized 43-cell matrix, final current-corpus gate, and public Stage 4 closure.

These are mandatory blockers, not deferred official cells. Stage 5 remains blocked. No corpus run, release, ROM payload publication, emulator, device, or ADB action was performed.
