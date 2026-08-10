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

The GitHub environment is the release-signing authority. A contextual current-user recovery copy exists outside the repository for emergency administration, but it is not a release prerequisite and does not introduce another user-managed passphrase. Never commit a keystore, properties file, or credential export.
