# DualDex signing identity

Production package: `com.darkaxt.dualdex`

Production signing occurs only in the protected GitHub Actions `release-signing` environment. Local builds have no release signing configuration. Emulator builds use the Android debug certificate and the separate package `com.darkaxt.dualdex.debug`.

The repository stores only the public certificate and its SHA-256 fingerprint:

- `dualdex-release-cert.pem`
- `dualdex-release-cert.sha256`

The encrypted keystore and credentials are held as GitHub environment secrets:

- `DUALDEX_RELEASE_KEYSTORE_B64`
- `DUALDEX_RELEASE_STORE_PASSWORD`
- `DUALDEX_RELEASE_KEY_ALIAS`
- `DUALDEX_RELEASE_KEY_PASSWORD`

The GitHub environment is the release-signing authority. There is no user recovery phrase and no local production-signing path. Never commit a keystore, properties file, or credential export.

The release workflow must be dispatched from a new `v1.*` source tag. It runs all tests and creates an unsigned APK before entering the protected environment, reconstructs the keystore only in that protected job, verifies the pinned fingerprint, signs and verifies the APK, and creates a new GitHub Release without replacing an existing one. RC releases remain draft prereleases until the downloaded artifact passes the dedicated AVD and physical Thor gates.
