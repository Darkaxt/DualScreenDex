# Cross-page UI Conformance Design

Date: 2026-08-21
Status: approved correction

## Goal

Make the production companion feel like one ROM-themed application on every page, bring Party and Trainer Card to the visual density and hierarchy of the supplied `pokeemerald-dualscreen` reference, reduce the Pokédex caught badge by 20%, and remove diagnostic information from every normal user-facing surface.

This is a presentation correction. It does not change parser data, knowledge policy, navigation destinations, map pixels, fog rules, battle calculations, Party ownership, or Trainer data.

## Evidence and root cause

The installed RC24 Party capture at `D:\Temp\dualdex-rc25-ui-audit\party-installed-rc24.png` proves that the previous implementation met only structural checklist items: two columns, three rows, sprite, name, level, HP, and EXP. It did not reproduce the reference's compact game-menu composition, information density, framing, or selected-state hierarchy. Empty cards dominated most of the screen.

The existing ROM-theme browser gate also passed while Atlas, Battle, Party, Trainer Card, Pokémon AREA, and inactive controls retained hard-coded legacy green. The gate asserted root CSS variables but explicitly excluded Map and Battle from its primary surface check. The defect is therefore both incomplete styling and incomplete verification.

## Production information boundary

Normal application pages must not display diagnostic or provenance information. The following are forbidden outside the explicitly labeled Debug section in Settings:

- ROM filenames or archive member paths;
- family names used as parser identities;
- CRC32, SHA, offsets, schema versions, capability codes, parser phases, or internal resolution labels;
- redundant policy/runtime subtitles such as `EMERALD · ORGANIC`, `LIVE · OWNED POKÉMON`, or `LIVE · READ ONLY`.

The global ROM identity strip is removed from the normal shell. The Pokédex, Battle, Party, Trainer Card, Settings, Setup, Move, and Ability headers use their destination title without redundant diagnostic kickers. A Pokédex number remains valid game content. Loading copy may describe user-facing work such as “Preparing maps”; it may not expose enum names or parser terminology.

Capability Report, Memory Mapper, and the Settings Debug group remain diagnostic surfaces and may show technical data. Their technical status does not exempt them from the theme contrast contract: identity rails and primary actions must use the derived foreground paired with their actual background.

## ROM-theme surface contract

The persisted theme tokens remain the only source for non-semantic `GAME` chrome:

- `field` and `fieldPattern`: page substrate and low-contrast texture;
- `header` and `headerShadow`: page headers and strong identity rails;
- `menu` and `menuShadow`: toolbars, inactive controls, recessed regions, and depth;
- `panel`: cards, dialogs, detail sheets, and information rows;
- `border`: non-semantic borders and focus framing;
- `text` and `textShadow`: content text and readable depth;
- `accent` and `accentText`: selected tabs, selected cards, primary controls, and active rails.

Every normal route must consume these tokens for its outer background, header, controls, panels, and cards. No normal page may select a legacy forest color simply because its selector predates the theme.

Semantic colors remain independent:

- HP green/yellow/red and EXP blue;
- status-condition colors;
- Pokémon type colors;
- Atlas cyan revealed-location square, player marker, black fog, map raster, and brown raster boundary;
- error and destructive-action red;
- source sprites, badges, maps, and Trainer artwork.

Dark, Light, and High Contrast remain fixed accessibility alternatives and are not generated from the ROM tokens.

## Page conformance matrix

The browser audit must visit and inspect all normal user routes:

1. loading and welcome;
2. Pokédex browse and every filter state;
3. Pokédex detail ENTRY, STATS, MOVES, AREA, and MORE;
4. Move Detail and Ability Detail;
5. Battle ENTRY, ATTACK, RARITY, and MOVES;
6. Party roster and its selected-member dialog;
7. Trainer Card;
8. local map, Atlas, and Pokémon AREA map;
9. Settings and Setup.

Capability Report and Memory Mapper are audited for legibility and containment, but their diagnostic content is allowed because they are reached only through Settings → Debug.

For each normal route, the audit checks the computed background, panel, border, inactive control, active control, and text colors—not merely the variables on the root element. The same route set is checked for viewport overflow at the production 4:3 size.

## Party composition

The persistent Party view remains a two-column by three-row board at the production 4:3 viewport. It must feel like a game party menu rather than six generic forms.

Each occupied slot has:

- a compact framed sprite region occupying no more than roughly one fifth of card width;
- one strong identity row with nickname/name, optional sex, and level;
- species name as secondary text only when it differs from the nickname;
- numeric HP near the bars;
- a blue EXP line directly above the HP bar at half its height;
- the semantic HP bar;
- status only when present;
- a strong ROM-accent selected state with paired border/shadow depth.

The slot face uses ROM-derived accent/panel/menu colors. The layout may evoke the reference's blue cards for a ROM whose accent/field is blue, but it must not hardcode Emerald blue for every ROM.

Empty positions preserve party order but become quiet placeholders: no repeated `OPEN SLOT` diagnostic copy and no large text blocks. Their accessible labels remain explicit.

Selecting an occupied card continues to open the existing detail dialog. The dialog retains nature, ability, held item, stats, moves, type/status artwork, EXP, and Pokédex navigation, but all non-semantic chrome follows the same theme contract.

### Measured reference correction

The lossless reference crop and the current 1024×768 production capture are retained under `D:\Temp\dualdex-rc25-ui-audit\reference-features`. Their measured comparison supersedes the earlier qualitative “compact” assessment:

- the reference grid occupies about 92% of the page width and 91% of its content height; the current grid occupies about 84% and 64%;
- the reference cards are approximately 209×92 pixels (2.27:1); the current cards are approximately 423×140 pixels (3.02:1);
- the reference portrait is unboxed and occupies 52% of row height; the current portrait ratio is similar, but its separate square frame and undersized row make it visually detached;
- the reference stacks level below the name and reserves the far edge for sex; the current heading compresses name, sex, and level onto one line;
- the reference HP label, bar, and numeric value form one bottom-anchored information block; the current bars float below a separate metadata row;
- the reference board dominates the usable page; the current board leaves a large unused band below it;
- the reference uses a bottom tab bar while DualScreenDex uses its established global top header. That architectural difference is retained, but it does not justify the card and board proportion mismatch;
- DualScreenDex intentionally retains the requested blue EXP line at half the HP-bar height and the selectable detail dialog.

At 1024×768, the corrected board must occupy roughly 90–94% of content width and 86–90% of content height. Occupied cards must target a 2.25–2.45:1 aspect ratio, portraits must remain approximately 52% of row height, identity must use two vertical lines, and the EXP/HP block must be bottom-aligned. Empty slots keep identical geometry but remain quiet.

## Shared Pokédex action icon

Atlas, Battle, and Pokémon AREA use one `DexIcon` component. It is a crisp, recognizable monochrome Pokédex outline whose shell, hinge, screen, lens, and details use `currentColor`; it does not carry fixed red, cream, cyan, or green fills into unrelated ROM themes.

The action follows the active route theme. In Battle it is a transparent header glyph using the header foreground, not a separate white or legacy olive `#0d3026` square. Atlas keeps the established action position and accessible “Open Pokédex” label.

Atlas uses the same ROM-accent bottom divider as the standard page header, followed by a crisp neutral-black separation into the map stage. It must not reuse the colored elevated-header blur because bright ROM headers otherwise bloom over the black raster and fog surface.

## Battle rarity composition

The Rarity tab repeats the same five-star meter at the top of its content card so the score remains the dominant visual cue after the identity rail scrolls out of attention. The meter preserves fractional fills and its accessible rating label.

The card uses a rarity-responsive, theme-derived shader: low readings remain restrained, medium readings add a controlled accent wash, and high readings gain stronger radial light and border depth. The treatment uses `color-mix()` with ROM theme tokens rather than hard-coded Emerald colors. Title and assessment remain readable above the shader, and an unavailable reading renders without invented stars.

## Pokédex browse density

Sparse result sets must still use the 4:3 canvas intentionally. Browse rows become portrait-led roster cards rather than 66px text strips:

- normal density uses an approximately 88–96px row with a 72–80px portrait;
- Pokédex number becomes supporting text above or beside the larger species name;
- known type chips occupy the trailing metadata region, using catalog type colors;
- static metadata remains hidden when the active knowledge policy has not unlocked it;
- day/night encounter marks and affirmative caught state retain their existing semantics;
- compact density remains smaller but preserves the same hierarchy.

This correction applies to GAME, Dark, Light, and High Contrast themes. It must not introduce route-specific debug labels or fill space with invented data.

## Trainer Card composition

Trainer Card remains one cohesive card, but its shell, strip, rows, portrait frame, and badge tray use ROM-derived tokens rather than fixed green. The layout keeps the reference hierarchy: title/ID strip, identity and avatar, compact aligned facts, then badges. No existing Trainer field is removed or invented.

## Nature detail

Party Detail turns a recognized Nature into a link to a themed Nature Detail page. The page derives the canonical 25 Gen III natures from their 5×5 raised/lowered-stat order and shows the resulting 110%/90% non-HP stat multipliers plus flavor preference. The five diagonal natures are explicitly neutral. Gen I/II party members and unknown or custom Nature names remain plain text; the UI does not invent a canonical effect for an unrecognized value.

## Pokédex caught badge

The caught badge stays at the portrait's lower-right edge and remains affirmative-only. Its outer diameter, contained artwork, and fallback mark are each reduced to 80% of RC24: 27px → 22px, 23px → 18px, and 19px → 15px after integer rounding. It must remain readable without covering the species portrait.

## Accessibility and responsive behavior

- All Party slots remain real buttons and retain slot-aware accessible names.
- Empty Party slots remain disabled and non-interactive.
- Focus, selected state, and dialog close controls meet the current keyboard contract.
- Text remains readable against derived surfaces; theme contrast corrections remain authoritative.
- On the 1024×768 six-inch target, every visible text-bearing element computes to at least 11.2px and every captured layout averages at least 12px. Auxiliary labels use the shared 11.25px tier, ordinary labels and body copy use larger semantic tiers, and titles/value highlights retain their deliberate hierarchy instead of being flattened to one size.
- Dense Settings, Setup, and Debug content scrolls when necessary rather than shrinking below the physical-size floor.
- The 2×3 board does not collapse to one column at supported secondary-display sizes.
- No normal page gains horizontal or document-level vertical overflow.

## Verification boundary

Completion requires:

- RED then GREEN component assertions for the caught-badge size, Party composition, title-only headers, and removal of the ROM status strip;
- computed-style browser assertions across the full page/tab matrix;
- production 4:3 screenshots for every matrix entry;
- a generated minimum/maximum/average font-size matrix for every captured layout, with browser assertions enforcing the physical-size floor and average;
- symmetric Party-board inset assertions so a correctly sized board cannot drift below the viewport;
- explicit contrast assertions for Capability Report and Memory Mapper identity/action surfaces;
- direct visual comparison of Party and Trainer Card against the supplied reference, with the installed RC24 capture retained as the negative baseline;
- full companion-web tests and production build;
- no device installation or gameplay mutation during automated verification.

The task is not complete if root variables are correct but any normal page still renders legacy chrome, if Party remains a sparse generic grid, or if diagnostic content survives outside Settings → Debug.
