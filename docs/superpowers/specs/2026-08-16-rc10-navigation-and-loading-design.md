# RC10 Navigation and Loading Design

## Goal

Keep Atlas navigation predictable, remove idle-only guidance from active loading, and make the upper toolbar accurately reflect the running game.

## Live game clock and title alignment

When the parser can prove the running game's live clock structure, the upper toolbar displays its validated `HH:MM` value in a position that is geometrically centered in the panel rather than centered only between the surrounding buttons. The clock is read from game memory and updates through the existing live-state stream. Android wall time and the trainer-card play-time counter are not substitutes. Missing, ambiguous, or invalid clock data produces no clock text.

The Pokédex identity block is left-aligned in its available header column. Its placement does not depend on how many right-side actions are available, and the centered clock does not push the title to the right.

## Pokédex navigation

The Pokédex icon in the Atlas header is a global navigation action, not an Area-filter action. It is always enabled while Atlas is open. Activating it closes Atlas and opens the Pokédex through the normal `SCREEN=POKEDEX` action. It does not change `filter`, `selectedAreaId`, or any other Pokédex browsing state, so the last-opened Pokédex tab remains selected.

Map-marker selection remains local to Atlas. The existing Pokémon Area Map may continue using its explicitly area-scoped navigation contract; this change applies only to the Atlas header shortcut.

## Atlas markers

Atlas locations use compact cyan squares, matching the region-map location language and avoiding any implication of capture or encounter state. The current Atlas location uses the same square with the existing pulse; the selected location retains the lime focus outline. The Local Map live-player marker remains a separate circular position indicator.

## Loading presentation

The sentence `Choose a Pokémon game to begin.` is idle guidance. It appears only when no catalog is loaded and loading is inactive. During determinate or indeterminate loading, the module label and progress bar replace that sentence and the setup actions. DualDex branding remains visible.

## Verification

- Atlas tests prove the Pokédex action is enabled for a location without encounter data and calls the global navigation callback.
- Atlas tests prove ordinary and current locations use the cyan-square marker class while the Local Map player marker remains distinct.
- Application-shell tests prove global Pokédex navigation sends `SCREEN=POKEDEX` without changing the filter payload.
- Loading tests prove idle guidance is present only while idle and absent during both loading modes.
- A real Modern Emerald ROM control proves the live-clock address and byte layout from compiled source roles without ROM-name, SHA, symbol, or absolute-offset selection.
- Runtime tests prove valid game hours/minutes are published, invalid or unavailable values are withheld, and the web header renders the clock only when published.
- Header tests prove the clock occupies the geometric center and the Pokédex title remains left-aligned.
- The production web test suite and build must pass before RC10 release preparation.
