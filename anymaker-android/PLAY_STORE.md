# Google Play release checklist: Anymaker 0.1.0

## Files
| What | Where |
|---|---|
| App bundle (upload this) | GitHub Actions → workflow **Anymaker Android** → artifact `anymaker-release` → `app-release.aab` |
| Test APK (install on a phone) | artifact `anymaker-release` → `app-release.apk` (or `anymaker-debug-apk`) |
| R8 mapping (upload with the bundle) | artifact `anymaker-release` → `mapping.txt` |
| Hi-res icon 512 × 512 | `store/icon-512.png` |
| Feature graphic 1024 × 500 | `store/feature-graphic-1024x500.png` |
| Phone screenshots (2–8) | Install the APK and take them on a phone |
| Privacy policy URL | Publish `PRIVACY.md` (for example with GitHub Pages) and paste the link |

Regenerate the graphics with `python3 tools/make_store_assets.py` (needs Pillow).

## Store listing (this version only ships Resize & Compress)
List only what the build does. Add PDF, scan and other features to the title and description when those versions ship.

**App name (≤30):** `Anymaker: Photo Resizer & KB`

**Short description (≤80):**
`Resize photos to any pixel size and compress under any KB with the best quality.`

**Full description:**
```
Anymaker resizes any photo to the exact pixels you need and compresses it under your KB limit, keeping the highest quality that fits.

• Any size: 1000×1000, 600×600 passport, 300×80 signature, or your own
• Any limit: 100 KB, 50 KB, 20 KB, whatever the form or website asks for
• Best quality: finds the highest quality that fits under your limit and shows a quality grade
• Never reduce pixels: keep the exact resolution you asked for
• Lock settings: set once and every photo you choose or share converts automatically
• JPG or WEBP
• Save to your gallery or share to any app
• Light and dark mode
• Works offline. No account, no upload, no permissions.

Perfect for job and university applications, visa and passport forms, and any website with an upload limit.
```

**Category:** Photography · **Tags:** photo resizer, image compressor, KB

## Play Console answers
- **Data safety:** No data collected. No data shared.
- **Ads:** No, this app does not contain ads.
- **Content rating:** complete the questionnaire; no objectionable content (Everyone / PEGI 3).
- **Target audience:** 18+ (keeps the app outside the Families policy).
- **App access:** all functionality is available without login.
- **Countries:** all countries and regions.
- **Play App Signing:** keep it on (default). The key from `scripts/make-keystore.sh` or the `ANYMAKER_KEYSTORE_BASE64` secret is your **upload key**.

## New personal developer accounts
Accounts created after November 2023 must run a **closed test with at least 12 testers for 14 days** before production access. Upload the AAB to *Testing › Closed testing* first.

## Next version
Bump `versionCode` and `versionName` in `app/build.gradle.kts`, push, download the new AAB from Actions, and upload. Always sign with the same upload key.
