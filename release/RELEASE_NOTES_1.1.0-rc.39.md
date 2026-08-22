# DualDex 1.1.0-rc.39

RC39 restores live map tracking and every other live-state refresh blocked by an Android runtime incompatibility introduced in RC38.

## Android live-state correction

- RC38 formatted unresolved `{PLAYER}'s House` labels with a regular expression accepted by the desktop JVM but rejected by Android's regular-expression engine.
- On Android, `/api/bootstrap` consequently returned a pattern-syntax error instead of the current companion state.
- Without fresh bootstrap state, the Atlas could not receive updated player coordinates even though the RetroArch process and mapper remained active.
- RC39 replaces the regular expression with literal, case-insensitive placeholder substitution. No regular-expression compilation occurs in this path.

## Preserved behavior

- An available live trainer name still produces the named house label.
- An unavailable name still produces `Your House` without exposing a placeholder or inventing an identity.
- Parsed map geometry, player-position addresses, fog, discovery, POIs, and label placement are unchanged from RC38.

## Verification

- The failure was reproduced on the installed RC38 APK: `/api/bootstrap` returned `Syntax error in regexp pattern` at the possessive player token while RetroArch remained active.
- A focused regression requires literal named and unnamed player substitutions.
- The complete API projection suite remains green.
- The protected release workflow runs the complete module, lint, web, package-identity, signing, checksum, and provenance gates.

## Delivery

- RC39 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
