# DualDex 1.1.0-rc.20

RC20 fixes automatic companion matching when RetroArch launches a renamed ZIP or 7z container whose filename differs from the ROM member inside it. It retains RC19's dynamic Gen II and Gen III Local maps and the completed v1.1 companion features.

## Renamed archive matching

- RetroArch content without a reported CRC can now match either the indexed ROM member basename or its archive-container basename.
- Filename normalization strips only supported ROM/archive extensions, so version names such as `v3.5` are not mistaken for file extensions.
- Conflicting indexed hashes remain ambiguous; the resolved source must still pass the existing full SHA-256 verification before its catalog opens.
- The exact observed Modern Emerald pair, outer `Pokemon Modern Emerald (v3.5).7z` and inner `Modern Emerald (v3.5).gba`, is covered by the resolver regression.

## Compatibility documentation

- A 331-unique-ROM Gen I–III report publishes numeric percentages for 23 table types, including world and local maps.
- `NOT_APPLICABLE` generation/table combinations remain outside the denominator.
- The report discloses one unresolved Gen III parser error and records zero selected catalogs that failed persistence.
- Desktop corpus and single-ROM memory peaks are documented to guide the existing bounded Android loading work.

## Verification and delivery

- The complete RetroArch session resolver suite passed from clean tasks.
- The Android integration compilation passed after the production web bundle was rebuilt from locked dependencies.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
