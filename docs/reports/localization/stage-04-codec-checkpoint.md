# Stage 4 codec foundation checkpoint

Status: **verified codec foundation checkpoint; not Stage 4 closure**.

## Scope

The codec foundation adds exact versioned Japanese and Korean text candidates to the existing token decoder and language registry:

| Candidate | Codec identity | Version |
|---|---|---:|
| Japanese Gen I Red/Blue lineage | `gb-gen1-ja-red-blue` | 1 |
| Japanese Gen I Yellow | `gb-gen1-ja-yellow` | 1 |
| Japanese Gen II | `gb-gen2-ja` | 1 |
| Japanese Gen III Ruby/Sapphire | `gba-gen3-ja-ruby-sapphire` | 1 |
| Japanese Gen III Emerald/FireRed/LeafGreen | `gba-gen3-ja-emerald-frlg` | 1 |
| Korean Gen II Gold/Silver | `gb-gen2-ko` | 1 |

Family selection supplies a **probe dialect**, not final language or mechanical authority. Header product codes and GB destination metadata remain candidate hints. Exact codecs are subsequently subject to structural and text corroboration. No manual content-language override or UI-language coupling is introduced.

Gen III dialects distinguish Ruby/Sapphire arrow bytes from later prefixed controls. Extended controls consume bounded parameter widths, including the parameter to `FC 11`. Ruby/Sapphire rejects the later `FC 17` and `FC 18` controls. Korean lead/trail tokens consume two bytes without confusing a trail byte with the bare terminator; unmapped or truncated tokens remain invalid. The generated Hangul tables contain character mappings only, not ROM content.

## Source references

- [Japanese Red/Blue source charmap](https://github.com/luckytyphlosion/pokered-jp/blob/258d1a89ec49a2a0ccfbdd232ac0e5d96d00899a/charmap.asm).
- [Japanese Yellow source charmap](https://github.com/Narishma-gb/pokeyellow-jp/blob/f282e72ae26232790fdb780aa5a5db7ec8ebf572/constants/charmap.asm) and [static text expansions](https://github.com/Narishma-gb/pokeyellow-jp/blob/f282e72ae26232790fdb780aa5a5db7ec8ebf572/home/text.asm#L232-L245).
- [Gen II Japanese charmap declarations](https://github.com/scr-trees/pokegold_jpcrystalvc/blob/f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm). This checkout builds English Gold/Silver; its disabled dictionary expansions are not evidence of the native Japanese consumer.
- [Ruby/Sapphire text consumer](https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/src/text.c) and [charmap](https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/charmap.txt).
- [Emerald text consumer](https://github.com/pret/pokeemerald/blob/5eff78649e7170a877b961ef0b3da13b81a16038/src/text.c) and [charmap](https://github.com/pret/pokeemerald/blob/5eff78649e7170a877b961ef0b3da13b81a16038/charmap.txt).
- [FireRed/LeafGreen text consumer](https://github.com/pret/pokefirered/blob/c75f352304d529f6ba92d4f74b9cf8b5c3810788/src/text.c).
- [Korean character tables at the pinned source revision](https://github.com/Narishma-gb/pokegold-kr/tree/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4/constants/charmap).

Charmap aliases and renderer semantics must be distinguished during ratification. Source disagreements are not evidence that all official controls have passed.

## Mapping review resolution

- Yellow overrides punctuation bytes `74` and `75` with U+00B7 and U+22EF, and dictionary token `56` with two U+22EF characters. Red/Blue retains its own U+30FB/U+2026 mappings.
- Both Gen III Japanese dialects decode `AF` as U+00B7. Focused vectors preserve the exact Unicode character rather than visually similar punctuation.
- Japanese Gen II dictionary token `37` expands to `ここは` followed by a space. The charmap comment alone omitted that space, while the retained English consumer disabled the expansion entirely. Neither was sufficient evidence to change the Japanese codec. Read-only inspection of both native controls below verified the token dispatch, substitution pointer, trailing-space character, and terminating boundary. The existing `ここは ` mapping and concatenation regression remain correct.

| Native Japanese control | SHA-256 | Token dispatch | Handler | Expansion address |
|---|---|---|---|---|
| Gold Rev 1 | `27a07a1d3faf9c6a0b1b60d5e88ee3a4159a751a47b4c46ab09f1202d52bac3e` | `0x0F8B` | `0x10D4` | `0x1183` |
| Crystal | `136ada06cb68656b7de475fa4b278d37dbeff8f5257e7dfdf7f4a4aec19a90f3` | `0x10A6` | `0x11FF` | `0x12C6` |

These offsets identify validation evidence only; production decoding contains no per-ROM offsets or hash selectors. No ROM payload is included in this report.

## Verification scope

The focused checkpoint command is:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test \
  --tests 'com.enrpau.dualscreendex.parser.text.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.language.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.detect.RomHeaderTest' \
  --tests 'com.enrpau.dualscreendex.parser.family.FamilyProbeCoordinatorTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.FamilyParsersAbilityResolutionTest' \
  --rerun-tasks
```

Result: **BUILD SUCCESSFUL**, fresh compilation and execution (`--rerun-tasks`), **105 passed, 0 failures/errors, 1 skipped** across 11 test classes. The skipped pre-existing Cloud White 2 live-ROM regression requires `DUALDEX_CLOUD_WHITE_2_ROM`; it was not part of the codec gate. All 65 text/language/header tests executed and passed. The remaining 40 passes cover family integration. Existing compiler and Gradle deprecation warnings remain.

The punctuation regressions first failed in three assertions before the Yellow/Gen III mapping fixes; all 12 Japanese codec tests then passed. The native Gen II pointer check initially assumed the handler began with the pointer load; inspection showed its preceding `push de`. The corrected evidence check verified both complete handlers and exact control digests without changing production decoding.

The Western registry test now checks the Western subset rather than asserting that the complete registry excludes Japanese/Korean candidates. All exact Western codec identity assertions remain intact.

Pre-commit smart-sync compared parent `d8414bbd84cf0f91876125802d377fe4ba7dcab8` with fetched `fork/master` at `2fef59df95f6deb0ae861aad42cff52eff739a86`: no master-only commits and no integration changes required. The remote feature branch matched the parent.

## Required work before Stage 4 closure

This checkpoint does not enable or claim end-to-end Japanese/Korean catalog support. Mandatory remaining Stage 4 work is:

1. Script-aware table plausibility and language corroboration, including unmarked Gen I structural trials.
2. Token-driven variable-length name traversal; raw byte terminator searches are not safe for Korean lead/trail sequences.
3. Localized names, descriptions, types, and applicable location materialization with capability-local failure isolation.
4. Exact Japanese family controls and separate Korean Gold/Silver controls through parse, materialization, persistence, reopen, and API overlays.
5. The complete sanitized 43-cell official-language matrix and one final source-bound current-corpus run after executable changes are final.
6. Stage closure audit and publication of `stage-04-closure.md` and the localization ledger.

No required official cell is deferred. Stage 5 remains blocked until public Stage 4 closure. No corpus, APK release, emulator, or physical-device validation is part of this codec checkpoint.
