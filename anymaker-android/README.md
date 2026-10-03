# Anymaker (Android)

Photo, scan and PDF tools on the phone. Version 1.0.0 ships 17 tools:
Resize & Compress, Batch, Crop & Rotate, Convert, Remove Background, Passport Photo, Scan Document,
Scan Text, Scan QR, Create QR, Signature, Images to PDF, Edit PDF, Merge PDF, Split PDF, Compress PDF and Lock PDF.
The design and the roadmap for the other tools are in `../docs/superpowers/specs/2026-10-03-photo-shrink-2-design.md`.

- Kotlin, Jetpack Compose, Material 3; minSdk 24, targetSdk 36
- Package `com.vx.anymaker`
- No runtime permissions, no analytics, no ads; ML features run on device through Google Play services

## Build
CI builds everything: push to GitHub and download the artifacts from the **Anymaker Android** workflow run.

Locally (needs JDK 21 and the Android SDK with platform 37):

```bash
./gradlew testDebugUnitTest lintDebug   # tests (Robolectric) and lint
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
scripts/make-keystore.sh                # once: creates the upload key + keystore.properties
./gradlew bundleRelease                 # app/build/outputs/bundle/release/app-release.aab
```

## Signing without GitHub secrets
Every push also publishes `anymaker-release-unsigned.aab`, `anymaker-debug.apk` and `mapping.txt` to the
`anymaker-builds` branch. Sign the bundle offline with the upload key:

```bash
git fetch origin anymaker-builds && git show FETCH_HEAD:anymaker-release-unsigned.aab > app.aab
jarsigner -keystore anymaker-upload.jks -sigalg SHA256withRSA -digestalg SHA-256 app.aab upload
```

## Release signing
The release build is signed when `keystore.properties` exists or these environment variables are set:
`ANYMAKER_KEYSTORE` (path), `ANYMAKER_KEYSTORE_PASS`, `ANYMAKER_KEY_ALIAS`, optional `ANYMAKER_KEY_PASS`.
In GitHub Actions they come from the secrets `ANYMAKER_KEYSTORE_BASE64`, `ANYMAKER_KEYSTORE_PASS` and `ANYMAKER_KEY_ALIAS`.

## Layout
| Path | Purpose |
|---|---|
| `app/src/main/java/com/vx/anymaker/core/image` | `Shrinker` (compression engine), import, save, share |
| `app/src/main/java/com/vx/anymaker/core/data` | Settings in DataStore |
| `app/src/main/java/com/vx/anymaker/feature/*` | Screens: home, resize, files, settings |
| `app/src/main/java/com/vx/anymaker/ui/theme` | Colors, type, shapes |
| `store/` | Play Store icon and feature graphic (`tools/make_store_assets.py`) |
| `PLAY_STORE.md` | Listing text and publishing checklist |
| `PRIVACY.md` | Privacy policy |
