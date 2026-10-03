# Google Play release checklist: Anymaker 1.0.0

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

## Store listing

**App name (≤30):** `Anymaker: PDF, Photo & Scan`

**Short description (≤80):**
`Resize photos to any KB, scan documents and text, edit PDFs, make QR codes.`

**Full description:**
```
Anymaker puts the photo, scan and PDF tools you need for forms, work and school in one app. Everything runs on your phone.

PHOTO
• Resize & Compress: any pixel size, any KB limit, the best quality that fits
• Batch: convert up to 100 photos at once with the same settings
• Crop & Rotate: free or fixed shapes (1:1, 4:3, 16:9…), rotate and flip
• Convert: JPG, PNG and WEBP; opens HEIC photos
• Remove Background: transparent PNG or a new color
• Passport Photo: common sizes (US 2×2 in, UK/EU/Schengen 35×45 mm, Canada, China and more) with a white or blue background and a 4×6 in print sheet

SCAN
• Scan Document: automatic edges, many pages into one PDF
• Scan Text: copy text from photos (Latin, Devanagari, Chinese, Japanese, Korean)
• Scan QR: QR codes and barcodes from the camera or a photo; see the full link before opening
• Create QR: links, text, Wi-Fi, phone and email
• Signature: draw it or clean up a photo of your signature into a transparent PNG

PDF
• Images to PDF: A4, Letter or photo size
• Edit PDF: add text, highlights, whiteout and images such as your signature
• Merge, Split and Compress PDF
• Lock PDF: add or remove a password

Files tab: everything you make in one place, ready to save or share.
No account. No ads. Your files are never uploaded.
```

**Category:** Productivity · **Tags:** PDF, scanner, photo resizer

## Play Console answers
- **Data safety:** The app itself collects and shares no data. Google Play services (ML Kit) may collect diagnostics; follow Google's ML Kit Data safety guidance (https://developers.google.com/ml-kit/android-data-disclosure) when filling the form.
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
