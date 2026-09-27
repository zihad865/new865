# Google Play release checklist

## Files
| What | File |
|---|---|
| App bundle (upload this) | `release/PhotoShrink.aab` |
| App icon 512×512 | `store/icon-512.png` |
| Feature graphic 1024×500 | `store/feature-graphic-1024x500.png` |
| Phone screenshots (min 2) | Take them on your phone after installing `release/PhotoShrink.apk` |
| Privacy policy | Host `PRIVACY.md` publicly (e.g. GitHub Pages) and paste the URL |

## Store listing
**App name:** Photo Shrink – Resize & Compress KB

**Short description (≤80):**
Resize photos to any pixel size and compress under any KB with best quality.

**Full description:**
Photo Shrink resizes any photo to the exact pixels you need and compresses it under your KB limit,
keeping the highest quality that fits.

• Any resolution: 1000×1000, 300×300 passport, 300×80 signature, or your own
• Any size limit: 100 KB, 60 KB, 20 KB, 10 KB, whatever the form asks for
• Lock your settings: tick once and every photo converts to the same pixels & KB automatically
• Smart quality: finds the best quality under your limit, never bigger than you set
• Strict mode: keep exact pixels, never shrink the resolution
• JPG or WEBP output
• Save to Gallery or share straight to WhatsApp, email, or upload forms
• Works offline, no ads, no tracking, no permissions

Perfect for job applications, admission forms, passport and visa photos, and online uploads.

**Category:** Photography · **Tags:** photo resize, compress image, KB

## Play Console answers
- **Data safety:** No data collected, no data shared.
- **Ads:** No ads.
- **Content rating:** questionnaire → no objectionable content (rated Everyone / 3+).
- **Target audience:** 18+ (avoids Families policy requirements).
- **App access:** All functionality available without special access.
- **Play App Signing:** keep enabled (default). `keystore/upload.jks` is your **upload key**.

## New personal developer accounts
Accounts created after Nov 2023 must run a **closed test with at least 12 testers for 14 days**
before production access is granted. Upload the AAB to *Testing › Closed testing* first.

## Updating the app
Bump `android:versionCode` (and `versionName`) in `AndroidManifest.xml`, run `./build.sh`, upload the
new AAB. Always sign with the same upload key.
