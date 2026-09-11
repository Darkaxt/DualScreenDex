# Stage 4 POI applicability checkpoint

This checkpoint narrows Task 386's `POI_TEXT` audit without issuing a final oracle or making incomplete matrix cells appear complete. Parser schema is **75**; SQL schema remains **2** and codec versions remain **1**.

## Independent event semantics

Two public source snapshots establish event-kind meaning independently of DualDex output:

- `pret/pokecrystal` commit `f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53`: `constants/script_constants.asm` declares Gen II background kinds 5/6 as `BGEVENT_IFSET`/`BGEVENT_IFNOTSET` and kind 8 as `BGEVENT_COPY`. `engine/overworld/events.asm` dispatches kinds 5/6 through a flag check before loading and calling another script. Kind 8 copies hidden-event data and returns without calling a text script. The same source has exactly five conditional background declarations: one flag-gated poster and four flag-gated locked-door events.
- `pret/pokeemerald` commit `9a83a2bbe8e097e62c00f1dbd56849766775d7b6`: `include/constants/event_bg.h` declares kinds 0–4 as facing-sensitive script events, kind 7 as hidden item and kind 8 as secret base. `src/field_control_avatar.c` routes kind 8 through runtime secret-base selection and a shared entrance script; the map event carries a secret-base ID, not a ROM-native text pointer.

DualDex now preserves these positive structural distinctions in `LocalMapPoiTextObligation`:

- `CONTEXTUAL_TEXT` means runtime state controls whether another script applies, so no unconditional static headline is authoritative.
- `NO_TEXT` means the compiled event has no ROM-native text operand.
- `UNRESOLVED` remains distinct. Missing output, an unknown kind, an unrecognized script or failed decoding cannot become an applicability exclusion.

Gen II kinds 5/6 publish `CONTEXTUAL_TEXT`; Gen II kind 8 and Gen III secret-base kind 8 publish `NO_TEXT`. Unknown event kinds still fail closed as `UNRESOLVED`.

## Coverage contract

The `POI_TEXT` denominator remains every retained POI. `CONTEXTUAL_TEXT` and `NO_TEXT` rows intentionally remain uncovered and may receive final `NOT_APPLICABLE` proofs per record. They do not disappear from accounting, cannot accept a fabricated `poiTexts` value and cannot borrow item or destination labels. This checkpoint therefore does not increase any capability count or convert `PARTIAL` to `AVAILABLE`.

A UTF-8-safe, read-only reconciliation of the retained parser-63 44-control caches identified the missing-label classes by the persisted structural obligation. Those caches are diagnostic only: they predate parser 75 and cannot supply current acceptance or semantic authority. They showed the recurring families of missing direct/gendered text, destination joins and previously unresolved event kinds. Public source then independently resolved only the two event-kind classes above. Direct-sign decoding and destination applicability remain open rather than being inferred from aggregate counts.

## Verification

TDD began with compile failures for the absent `CONTEXTUAL_TEXT` and `NO_TEXT` contracts. Focused producer/model tests then passed for:

- Gen II conditional, copy, direct, item and destination obligations;
- Gen III secret-base exclusion while retaining direct/gendered and destination obligations;
- overlay extraction/projection with unchanged all-POI denominators;
- parser-74 cache rejection and current parser-75 rewrite/reopen;
- final matrix API observation handling without text backfill.

Fresh final verification executed **58/58 tests** with zero failures, errors or skips: 29 parser/model cases, one current-cache revision case, 23 matrix API observation cases and five exact native controls.

Five exact current native controls passed parse, materialization, actual SQLite close/reopen, full-catalog equality, API projection and zero-reparse boundaries:

- Japanese Gold/Silver;
- Japanese Crystal;
- Korean Gold;
- Korean Silver;
- Japanese Emerald.

The four Gen II controls each retain exactly five `CONTEXTUAL_TEXT` rows and zero `NO_TEXT`/`UNRESOLVED` rows. Japanese Emerald retains exactly 75 `NO_TEXT` secret-base rows and zero `CONTEXTUAL_TEXT`/`UNRESOLVED` rows. Their `POI_TEXT` states remain truthful partials: Japanese Gold/Silver **1,251/1,951**, Japanese Crystal **1,583/2,100**, Korean Gold and Silver **1,484/1,949** each, and Japanese Emerald **1,731/2,136**.

## Remaining Task 386 work

This checkpoint does not ratify the other missing POI rows. The remaining audit must independently classify and, where required, correct:

- static direct and gender-conditioned sign scripts that still fail decoding;
- service scripts whose structural role is known but whose ROM-native text applicability is not yet bound;
- warps with absent, contextual, ambiguous or unavailable destination labels;
- any unknown event kind or competing structural evidence.

No final 44-control oracle, matrix, corpus, Android/device, signing or release work is included. Stage 4 remains open and Stages 5–6 remain blocked.
