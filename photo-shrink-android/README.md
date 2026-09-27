# Photo Shrink (Android)

Resize a photo to any resolution and compress it under a KB limit at the highest quality that fits.
Same algorithm as `../photo_shrink.py`.

Install: `release/PhotoShrink.apk` (Android 5.0+). Allow "Install unknown apps" when prompted.

## Build

```bash
sudo apt-get install android-sdk-platform-23 aapt apksigner zipalign dalvik-exchange openjdk-17-jdk
./build.sh            # -> build/PhotoShrink.apk
```

The signing key is created in `keystore/release.jks` on first build (git-ignored). Keep it:
updates must be signed with the same key.
