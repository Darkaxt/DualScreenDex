# DualDex ROM-Scoped Settings Design

## Goal

Persist current and future APK customization without letting one ROM's choices leak into another ROM, while keeping device-owned behavior global and never modifying game SaveRAM.

## Storage model

`SettingsRepository` owns one versioned JSON document in the existing Android `dualdex-runtime` `SharedPreferences`. Schema 2 contains:

- a complete `globalDefaults` settings record;
- sparse `romOverrides` keyed by lowercase ROM SHA-256;
- no catalog paths, filenames, or SaveRAM bytes.

The document lives outside `files/catalogs`, so clearing or rebuilding catalog SQLite files cannot remove preferences. `SharedPreferences.commit()` remains the atomic persistence boundary.

Every setting has a global default. The effective settings for a loaded ROM are `globalDefaults` overlaid with that ROM's sparse values.

## Setting scopes

ROM-overridable settings:

- knowledge mode;
- attack, rarity, and move helpers;
- font scale, density, contrast, and theme;
- auto-open behavior;
- level-up ruleset mode (`AUTO` or an exact ruleset ID);
- docked/overlay display mode.

Global-only device settings:

- physical display target;
- overlay hardware scale;
- Thor top-screen focus ownership.

This split makes most customization ROM-specific while preventing a ROM profile from unexpectedly moving the APK between physical displays or taking system focus ownership.

## Runtime flow

1. APK startup reads global defaults and the last catalog SHA, then resolves the last ROM's effective settings.
2. When a catalog is loaded or reopened, `ProductionCompanionRuntime` requests settings for that catalog's SHA before publishing the ready state.
3. A settings action persists against the active catalog SHA. Global-only fields update `globalDefaults`; ROM-overridable fields update only that SHA's sparse override.
4. With no active catalog, changes update global defaults.
5. A stale manual ruleset ID remains stored but is applied fail-closed as unresolved; it is not silently replaced or selected by ROM order. If the table returns after reparsing, the user's choice returns.
6. `AUTO` continues to use the only validated table or a fingerprint-bound SaveRAM detection. It never writes to the `.srm`.

## Migration

Schema-1 settings remain readable. Existing values become schema-2 global defaults. If the legacy ruleset is manual and the last catalog SHA is valid, only that ruleset value moves into the last ROM's override and the global default becomes `AUTO`. No other legacy field is guessed to belong to a particular ROM.

Malformed hashes or individual override fields are ignored independently. The rest of the settings document remains usable.

## Boundaries

- ROM identity is the 64-character SHA-256, never filename or CRC.
- Catalog cache deletion does not delete settings.
- Save snapshot storage remains per-ROM but separate; it contains detected state, not the manual preference.
- The original SaveRAM is always read-only.
- The repository accepts at most 4096 ROM override records and rejects an attempt to exceed that bound without dropping existing records.

## Verification

Acceptance requires RED-first tests proving:

- two ROM hashes retain different rulesets and other overrides;
- global-only device fields are shared;
- sparse overrides inherit later global-default changes;
- schema-1 manual ruleset migrates only to the last ROM;
- cache recreation does not affect settings;
- runtime catalog switches publish each ROM's effective settings;
- stale manual IDs fail closed without being erased;
- a checksum-valid real Modern Emerald save still selects the detected table in `AUTO`, while manual overrides remain ROM-local;
- the real `.srm` hash is unchanged.
