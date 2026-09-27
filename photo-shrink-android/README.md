# Photo Shrink (Android)

Resize a photo to any resolution and compress it under a KB limit at the highest quality that fits.
Same algorithm as `../photo_shrink.py`.

- **Lock settings:** tick once (e.g. 1000×1000 · 60 KB); every selected or shared photo converts
  automatically with those settings until you untick.
- Presets, exact size, strict resolution, JPG/WEBP, Save to Gallery, Share.
- No permissions, no internet, targetSdk 36, minSdk 21.

| File | Use |
|---|---|
| `release/PhotoShrink.aab` | Upload to Google Play |
| `release/PhotoShrink.apk` | Install directly on a phone |
| `store/` | Play Store icon and feature graphic |
| `PLAY_STORE.md` | Listing text and publishing checklist |
| `PRIVACY.md` | Privacy policy |

## Build

```bash
sudo apt-get install android-sdk-platform-23 apksigner zipalign dalvik-exchange openjdk-17-jdk zip unzip
python3 tools/make_assets.py   # regenerate logo + store graphics (needs Pillow)
./build.sh                     # -> build/PhotoShrink.aab + build/PhotoShrink.apk
```

The upload key is created in `keystore/` on first build and is git-ignored.
Back it up: every Play update must be signed with it.
