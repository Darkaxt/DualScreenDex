# DualDex 1.1.0-rc.70

RC70 adds ROM-save-scoped Trainer Progress, offline Challenges, a save-synchronized Timeline, and knowledge-safe Atlas Objectives without adding another memory reader or poller.

## Trainer Progress

- Switch between the established Trainer Card and a new Progress destination under the same first-Pokémon license policy.
- Review current Game totals separately from the journey DualDex has actually observed; tracked values never pretend to reconstruct earlier play history.
- Track battles, wild and Trainer encounters, captures, evolutions, visited areas, discovered POIs, Party changes, changed saves, and completed challenges.
- Remember the selected Trainer destination and Metrics, Challenges, or Timeline section independently for the exact ROM-save identity.

## Offline Challenges and Atlas Objectives

- Start with six independently worded Tier 1 Challenges covering collection, evolution, exploration, POI discovery, and battles.
- Evaluate only templates whose normalized semantic inputs are available for the active ROM; missing inputs hide only dependent Challenges.
- Preserve the first proven completion time and matching save reference.
- Show incomplete, applicable, Organic-safe Exploration Challenges in the current Area Guide Objectives section.
- No account, network connection, RetroAchievements runtime, ROM-name profile, stock offset, or gameplay write is used.

## Save Timeline and continuity

- Freeze meaningful observed deltas only when the validated game save changes.
- Reuse the existing atomic sidecar/app-private checkpoint path; `INITIAL`, `UNCHANGED`, malformed, mismatched, and recovery-only observations write no Timeline entry.
- Restore history only when ROM SHA, save identity, and save-file envelope match exactly.
- Bound each playthrough Timeline to 512 deterministic entries while preferentially retaining first and milestone saves.

## Compatibility and performance evidence

- The exact report covers all 11 official English Gen I–III ROMs plus Modern Emerald, Pokémon Unbound, and Pokémon Odyssey.
- All eight Gen III controls expose 5/5 current Progress totals, 9/9 event families, and 6/6 baseline templates.
- The six official Gen I/II controls currently expose 0/5 proven live Progress totals, 6/9 event families, and 3/6 baseline templates; missing fields remain explicitly numeric rather than receiving a stock layout.
- Across all controls, 66/66 applicable templates are fully observable and validated. The source research classifies 883/1,003 official achievement descriptions.
- Existing Debug-only minute/load metrics now include semantic/challenge CPU, event count, Timeline entries, and retained journal items. Ordinary pages expose none of this diagnostic information.

## Validation and delivery

- The affected JVM/Android suites report 1,798 tests with zero failures and zero errors, including the real 14-ROM controls.
- All 212 companion browser tests pass across 29 files, and the production web bundle builds successfully.
- RC70 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010070`.
- DualDex remains read-only. No device or emulator was used during implementation or publication.
