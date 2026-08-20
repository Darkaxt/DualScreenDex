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

Capability Report, Memory Mapper, and the Settings Debug group remain diagnostic surfaces and may show technical data.

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

## Trainer Card composition

Trainer Card remains one cohesive card, but its shell, strip, rows, portrait frame, and badge tray use ROM-derived tokens rather than fixed green. The layout keeps the reference hierarchy: title/ID strip, identity and avatar, compact aligned facts, then badges. No existing Trainer field is removed or invented.

## Pokédex caught badge

The caught badge stays at the portrait's lower-right edge and remains affirmative-only. Its outer diameter, contained artwork, and fallback mark are each reduced to 80% of RC24: 27px → 22px, 23px → 18px, and 19px → 15px after integer rounding. It must remain readable without covering the species portrait.

## Accessibility and responsive behavior

- All Party slots remain real buttons and retain slot-aware accessible names.
- Empty Party slots remain disabled and non-interactive.
- Focus, selected state, and dialog close controls meet the current keyboard contract.
- Text remains readable against derived surfaces; theme contrast corrections remain authoritative.
- The 2×3 board does not collapse to one column at supported secondary-display sizes.
- No normal page gains horizontal or document-level vertical overflow.

## Verification boundary

Completion requires:

- RED then GREEN component assertions for the caught-badge size, Party composition, title-only headers, and removal of the ROM status strip;
- computed-style browser assertions across the full page/tab matrix;
- production 4:3 screenshots for every matrix entry;
- direct visual comparison of Party and Trainer Card against the supplied reference, with the installed RC24 capture retained as the negative baseline;
- full companion-web tests and production build;
- no device installation or gameplay mutation during automated verification.

The task is not complete if root variables are correct but any normal page still renders legacy chrome, if Party remains a sparse generic grid, or if diagnostic content survives outside Settings → Debug.

