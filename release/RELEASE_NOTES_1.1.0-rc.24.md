# DualDex 1.1.0-rc.24

RC24 refines the Pokédex, Party, and Trainer Card presentation without changing navigation, parser behavior, or live-data authority.

## Pokédex and Party

- Caught Poké Balls now sit on the lower-right corner of Pokémon portraits and appear only for affirmative caught state.
- Organic mode no longer repeats eye or negative Poké Ball status marks that add no information beyond the portrait treatment.
- Party is presented as six stable positions in a two-column by three-row roster.
- Each occupied Party card shows its live identity, level, HP, status, proportional HP bar, and a thinner blue experience-progress line when that progress is available.
- Selecting a Party member opens the existing nature, ability, held item, experience, stats, moves, type, status, and Pokédex details in a focused card; closing it returns to the roster.

## Trainer Card

- Trainer identity, public ID, money, play time, Pokédex totals, card stars, avatar, and all eight badge positions now share one cohesive Trainer Card composition.
- Missing optional artwork and data retain their existing truthful fallbacks.

## Delivery

- RC24 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
