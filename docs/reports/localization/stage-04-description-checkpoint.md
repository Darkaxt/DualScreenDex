# Stage 4 description-consumer checkpoint

Status: **verified bounded description consumers; not Stage 4 closure**.

## Implemented scope

- Pokédex category/page decoding and category-root discovery follow complete codec tokens within their byte windows. A Gen III control parameter equal to `FF` no longer terminates text prematurely.
- Referenced page boundaries remain hard bounds: truncated control parameters cannot borrow bytes from the next page. Off-by-one recovery is bounded by both the next reference and the existing 512-byte description limit.
- Pokédex decoding checks cancellation at entry, during pointer/row traversal, and within text tokens. Description discovery checks cancellation before early outcomes and during scans/proposals; cancellation is not converted into an unavailable or malformed result.
- The typed ability-description decoder requires an explicit `PokemonTextCodec`, and its resolver requires an explicit decoder. It no longer silently constructs an English decoder. Native prose uses the shared script-aware checks; Western word/length behavior is retained.
- Ability description decoding retains its 192-byte bound, propagates cancellation unchanged, and isolates malformed rows without cutting off later valid prose. Invalid-only bytes are malformed rather than missing prose; genuine blank/dash placeholders remain missing prose.

The existing production ability catalog path already obtains the exact codec from `layout.defaultTextCodec()`. This checkpoint corrects the separate typed dataset; it does not add production wiring or derive language authority from family/header hints. No official Korean ability fixture is fabricated: official Korean Gen II has no abilities.

## Verification

Red regressions reproduced control-parameter truncation and ignored cancellation in Pokédex decoding, raw-byte category discovery and early resolver outcomes, and recovery beyond the description byte budget. One test initially omitted the codec's deliberate space replacement for a control; the expected presentation was corrected without changing codec semantics.

Ability regressions reproduced native prose rejection, control-parameter collisions, ignored cancellation, and invalid-only text being classified as missing prose. Initial one-row fixture extent setup errors were corrected before treating behavioral failures as evidence.

The coordinated six-class description gate passed 62 tests. The fresh combined checkpoint gate was then run independently:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test \
  --tests 'com.enrpau.dualscreendex.parser.text.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.validate.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.catalog.MoveDescriptionMaterializerTest' \
  --tests 'com.enrpau.dualscreendex.parser.catalog.AbilityDescriptionMaterializerTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityLegacyParityTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionResolverCompatibilityTest' \
  --rerun-tasks
```

Result: **BUILD SUCCESSFUL; 257 passed, 0 failures, 0 errors, 0 skipped**, across 19 test classes. Fresh compilation and test execution completed. Existing compiler/Gradle warnings remain. `git diff --check` passed. A scoped read-only review of the Pokédex changes found no additional defects; ability changes and regressions were inspected before the independent final gate.

## Mandatory remaining work

`LNG-B002` remains open for the variable-name locator's boundary authority, ability-name and location consumers, remaining localized validation/propagation, and official end-to-end acceptance. This checkpoint does not prove complete native-language descriptions across official ROMs.

`LNG-B001` still blocks native language corroboration and Japanese Gen I structural trials. `LNG-D005` still blocks native compiled type semantics. `LNG-B003` still requires all official exact controls, the sanitized 43-cell matrix, the single final current-corpus run after executable changes are final, and public Stage 4 closure.

No required official cell is deferred. Stage 5 remains blocked. No corpus, ROM payload publication, schema change, release, emulator, physical-device, or ADB action is part of this checkpoint.
