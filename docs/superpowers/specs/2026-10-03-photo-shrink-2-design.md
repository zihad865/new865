# Photo Shrink 2.0: ডিজাইন স্পেক

তারিখ: 2026-10-03 · অবস্থা: রিভিউর অপেক্ষায় · ভিত্তি: `photo-shrink-android` 1.2

## ১. লক্ষ্য

Photo Shrink-কে একটা অফলাইন "ফটো + স্ক্যান + PDF" অল-ইন-ওয়ান টুলে পরিণত করা, যেটা সারা বিশ্বের ইউজারদের পাসপোর্ট, ভিসা, চাকরি, ভর্তি আর অফিসের কাগজপত্রের কাজ এক অ্যাপে সারে, আর অ্যাড ও Pro কেনা থেকে আয় করে।

**সফলতার মাপকাঠি**
- প্রতিটা টুল ইন্টারনেট ছাড়া কাজ করে। ইন্টারনেট লাগে শুধু অ্যাড আর একবারের মডেল ডাউনলোডে।
- ৫০টা ছবির batch মাঝারি ফোনে ২ মিনিটের মধ্যে শেষ হয়, অ্যাপ background-এ গেলেও চলে।
- KB লিমিট কখনো ভাঙে না। আউটপুট সবসময় লিমিটের নিচে, নইলে স্পষ্ট এরর।
- Play Console-এ crash-free ইউজার ≥ ৯৯.৫%।

## ২. ব্যবহারকারীর চাওয়া (কথোপকথন থেকে)

| চাওয়া | কোথায় |
|---|---|
| টেক্সট স্ক্যান (OCR) | SP2 |
| ছবি রিসাইজ / কম্প্রেস | SP1 |
| PDF বানানো আর যেকোনো PDF এডিট | SP3 |
| একসাথে অনেক ছবি (batch) | SP1 |
| ব্যাকগ্রাউন্ড রিমুভ | SP4 |
| QR কোড স্ক্যানার | SP2 |
| সুন্দর UI | SP0 (ডিজাইন সিস্টেম), সব SP-তে প্রয়োগ |
| আয় | SP5 |

**ধরে নেওয়া (ইউজার এখনো নিশ্চিত করেননি):**
1. আয়ের মডেল: অ্যাড + এককালীন Pro কেনা।
2. Package `com.vx.photoshrink` আর `keystore/upload.jks` একই থাকবে, যাতে পুরোনো ইনস্টলে আপডেট যায়।
3. সব সাব-প্রজেক্ট করা হবে।
4. টার্গেট ইউজার গ্লোবাল (ইউজার নিশ্চিত করেছেন)। ডিফল্ট ভাষা ইংরেজি, ফোনের ভাষা অনুযায়ী অনুবাদ।

## ৩. পদ্ধতি বাছাই

| পদ্ধতি | সুবিধা | অসুবিধা | সিদ্ধান্ত |
|---|---|---|---|
| **A. Kotlin + Jetpack Compose (native)** | ML Kit, PdfBox, WorkManager সরাসরি; ছোট অ্যাপ; দ্রুত | UI নতুন করে লিখতে হবে | **বাছাই** |
| B. React Native / Expo | একই কোডে iOS | ML Kit, PDF, Tesseract সবই native module লাগবে; বড় অ্যাপ | বাদ |
| C. এখনকার Gradle-ছাড়া Java বিল্ড | কোনো মাইগ্রেশন নেই | কোনো লাইব্রেরি যোগ করা যায় না, ফিচারগুলো অসম্ভব | বাদ |

`Shrinker.java` (যাচাই-করা অ্যালগরিদম) হুবহু রাখা হবে; Kotlin থেকে সরাসরি ডাকা যায়।

## ৪. আর্কিটেকচার

- **ভাষা/UI:** Kotlin, Jetpack Compose, Material 3, single activity, Navigation Compose
- **SDK:** minSdk 24 (Android 7.0), targetSdk 36
- **বিল্ড:** Gradle (Kotlin DSL) + version catalog; `build.sh` অবসরে যাবে
- **ব্যাকগ্রাউন্ড কাজ:** WorkManager (batch, বড় PDF), progress notification
- **ডেটা:** Room (history), DataStore (settings, presets)
- **সেভ:** MediaStore → `Pictures/PhotoShrink`, `Documents/PhotoShrink`; API 24–28-এ `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28)

**প্যাকেজ বিন্যাস** (একটাই Gradle module, প্যাকেজ দিয়ে ভাগ):

```
com.vx.photoshrink
├── core/image     Shrinker.java (অপরিবর্তিত), ImageIo, Exif, Dpi, Formats
├── core/pdf       PdfOps (PdfBox), PdfRender (PdfRenderer)
├── core/ocr       OcrEngine ইন্টারফেস → MlKitOcr (৫ লিপি), TesseractOcr (বাকি ভাষা)
├── core/ml        Segmenter (ML Kit Subject Segmentation)
├── core/jobs      BatchWorker, JobRepository
├── core/data      Room DB (History), DataStore (Settings, Presets)
├── core/money     Ads (AdMob + UMP), Billing (Play Billing)
├── ui/theme       রং, টাইপোগ্রাফি, shape, কম্পোনেন্ট
└── feature/*      home, resize, batch, edit, scan, ocr, qr, pdf, bg, passport, files, settings
```

প্রতিটা `feature` শুধু `core`-এর ইন্টারফেস ডাকে; `core` কখনো `feature` জানে না।

## ৫. ফিচার তালিকা

### SP1: ফটো টুল
- রিসাইজ + KB কম্প্রেস (এখনকার অ্যালগরিদম), Lock Settings
- দেশভিত্তিক পাসপোর্ট/ভিসা preset: US ও India 2×2 in (600×600 px), UK/EU/Schengen 35×45 mm, Canada 50×70 mm, China 33×48 mm, Australia 35×45 mm; ফোনের দেশ অনুযায়ী আগে দেখায়
- সাধারণ preset: সই 300×80 · 60KB, ইমেইল অ্যাটাচমেন্ট (1600px · 500KB), সোশ্যাল (1080×1080), ওয়েবসাইট (WEBP 1200px · 200KB), সরকারি চাকরি/ভর্তি ফর্ম (দেশ অনুযায়ী); নিজের preset সেভ
- Batch: ১–৫০০ ছবি, `SEND_MULTIPLE`, এক সেটিংসে সব, ZIP বা গ্যালারিতে সেভ
- এডিট: crop (ফ্রি, 1:1, 3:4, 35×45mm), rotate, flip, brightness/contrast
- ফরম্যাট: আউটপুট JPG/PNG/WEBP; ইনপুট HEIC/HEIF (API 28+, নিচে স্পষ্ট বার্তা)
- DPI লেখা (72/150/300), EXIF ও লোকেশন মোছা
- Before/after স্লাইডার, ফাইল সাইজ ও quality grade

### SP2: স্ক্যান
- **ডকুমেন্ট স্ক্যানার:** ML Kit Document Scanner (কিনারা খোঁজা, crop, filter, বহু পাতা, সরাসরি PDF); ক্যামেরা permission লাগে না
- **টেক্সট স্ক্যান (OCR):** ল্যাটিন (ইংরেজি, স্প্যানিশ, পর্তুগিজ, ফরাসি, জার্মান, ইন্দোনেশিয়ান, তুর্কি, ভিয়েতনামি ইত্যাদি), চাইনিজ, দেবনাগরী (হিন্দি, মারাঠি, নেপালি), জাপানি, কোরিয়ান → ML Kit Text Recognition v2 (অন-ডিভাইস)। ML Kit যেগুলো পারে না (আরবি, বাংলা, রুশ, থাই, তামিল ইত্যাদি) → Tesseract4Android, প্রতিটা ভাষার ডেটা প্রথম ব্যবহারে ডাউনলোড। ফল: কপি, শেয়ার, `.txt`, searchable PDF
- **QR/বারকোড:** ML Kit Code Scanner দিয়ে স্ক্যান (ক্যামেরা permission লাগে না), গ্যালারির ছবি থেকেও; ZXing দিয়ে QR বানানো (লিংক, Wi-Fi, টেক্সট, ফোন); স্ক্যান history; লিংক খোলার আগে পুরো URL দেখানো

### SP3: PDF
- ছবি → PDF (পাতার সাইজ A4/Letter/ছবির মাপ, margin, KB লিমিট)
- PDF দেখা (PdfRenderer), পাতার thumbnail
- পাতা: merge, split, reorder, rotate, delete, extract
- যোগ করা: টেক্সট, ছবি, সই (আঙুলে আঁকা বা ছবি থেকে), highlight, whiteout, তারিখ
- PDF ফর্ম পূরণ (AcroForm)
- PDF কম্প্রেস (ভেতরের ছবি আবার এনকোড), PDF → ছবি
- পাসওয়ার্ড দেওয়া ও খোলা (পাসওয়ার্ড জানা থাকলে)
- **সীমা:** PDF-এর আগের লেখা Word-এর মতো বদলানো যাবে না; whiteout + নতুন টেক্সট দিয়ে সংশোধন হবে। স্টোর লিস্টিংয়েও এভাবেই বলা হবে।

### SP4: AI ফটো
- ব্যাকগ্রাউন্ড রিমুভ: ML Kit Subject Segmentation (অন-ডিভাইস, ~৫MB মডেল Play services নামায়); স্বচ্ছ PNG, রং, নিজের ছবি; হাতে brush দিয়ে ঠিক করা
- পাসপোর্ট ফটো মেকার: ব্যাকগ্রাউন্ড সাদা/নীল, মাপ, 4×6 প্রিন্ট শিট
- সিগনেচার ক্লিনার: কাগজের সই থেকে সাদা অংশ সরিয়ে স্বচ্ছ PNG

### SP5: Files, আয়, ভাষা
- Files ট্যাব: সব আউটপুটের history, খোঁজা, আবার শেয়ার, মুছে ফেলা
- বহুভাষী UI: ডিফল্ট ইংরেজি; লঞ্চে স্প্যানিশ, পর্তুগিজ (ব্রাজিল), হিন্দি, ইন্দোনেশিয়ান, আরবি (RTL), রুশ, তুর্কি, ভিয়েতনামি, ফরাসি, বাংলা; ফোনের ভাষা অনুযায়ী, Settings থেকে বদলানো যায় (Android 13+ per-app language)
- দাম: Play Console-এর দেশভিত্তিক মূল্য (local pricing)
- AdMob: Files তালিকায় native ad; ফলাফল স্ক্রিনে banner; interstitial শুধু কাজ শেষে, প্রতি ৩টা কাজে সর্বোচ্চ ১টা, আগেরটার পর অন্তত ৬০ সেকেন্ড; কোনো বোতামের গায়ে অ্যাড নয়
- UMP consent: EEA/UK-এ GDPR আর US রাজ্যের প্রাইভেসি আইন (CCPA ইত্যাদি) মেনে; Settings-এ "Privacy options"; অ্যাড ছাড়া অ্যাপ পুরোপুরি চলে
- Pro (এককালীন): অ্যাড নেই, batch সীমা নেই (ফ্রি: ১০টা), PDF সই ও পাসওয়ার্ড, 4×6 প্রিন্ট শিট
- Rewarded ad দেখে একবার Pro কাজ
- অ্যাপের ভেতরে রিভিউ চাওয়া (৩টা সফল কাজের পর, একবার)

### SP6: রিলিজ
- নাম: "Photo Shrink: PDF & Scanner" (২৭ অক্ষর)
- ইংরেজি listing + উপরের ১০টা ভাষায় অনুবাদ করা listing ও স্ক্রিনশট, feature graphic
- সব দেশে প্রকাশ (Play Console-এর সব দেশ ও অঞ্চল)
- Data safety: অ্যাড ID ও ডিভাইস তথ্য (AdMob), ছবি ডিভাইসেই থাকে
- `PRIVACY.md` আপডেট, closed test (১২ জন, ১৪ দিন), তারপর production

## ৬. UI ডিজাইন

- **নিচের ট্যাব:** Tools · Files · Settings
- **Tools হোম:** উপরে সার্চ বার আর "সাম্প্রতিক preset" chip; নিচে তিন সেকশনের grid: ফটো, স্ক্যান, PDF। প্রতিটা টাইলে আইকন + এক লাইনের কাজ
- **প্রতিটা টুলের ধাপ:** ফাইল বাছাই → সেটিংস (bottom sheet) → প্রসেস (progress) → ফলাফল
- **কমন ফলাফল স্ক্রিন:** preview, আগের/পরের সাইজ, Save · Share · আরেকটা টুলে পাঠানো (যেমন "PDF বানাও")
- **থিম:** Material You dynamic color (Android 12+), নিচে ব্র্যান্ড রং; light/dark; ইংরেজি Plus Jakarta Sans (অ্যাপে বান্ডল করা), অন্য লিপিতে সিস্টেমের Noto ফন্ট; আরবির জন্য পুরো RTL লেআউট
- **অ্যাক্সেসিবিলিটি:** 48dp টাচ টার্গেট, TalkBack লেবেল, ফন্ট ২০০% পর্যন্ত বড় করলেও লেআউট ভাঙবে না
- **গতি:** কাজ শুরুর < ১০০ms-এ সাড়া, ভারী কাজ সবসময় background thread-এ

## ৭. ডেটা প্রবাহ

```
Picker / Share intent ─▶ Uri তালিকা ─▶ feature ViewModel
                                          │
                     ছোট কাজ (১টা ফাইল) ──┼── বড় কাজ (batch / বড় PDF)
                              │                     │
                        core suspend fn       WorkManager BatchWorker
                              │                     │ (progress → notification + UI)
                              ▼                     ▼
                       আউটপুট cache ─▶ MediaStore সেভ ─▶ Room History
```

## ৮. এরর হ্যান্ডলিং

| অবস্থা | আচরণ |
|---|---|
| বড় ছবিতে OutOfMemory | ছোট decode থেকে আবার চেষ্টা (এখনকার নিয়ম), ৪ বারের পর স্পষ্ট বার্তা |
| KB লিমিটে কোনোভাবেই না আঁটে | ফাইল সেভ না করে কারণ দেখানো, ছোট রেজোলিউশন প্রস্তাব |
| Batch-এ কিছু ফাইল ব্যর্থ | বাকিগুলো চলবে; শেষে "৪৮টা সফল, ২টা ব্যর্থ" + কারণ |
| পাসওয়ার্ড-দেওয়া PDF | পাসওয়ার্ড চাওয়া; ভুল হলে আবার চাওয়া |
| নষ্ট PDF বা ছবি | "ফাইলটা খোলা যাচ্ছে না" + ফাইলের নাম |
| Play services নেই (যেমন Huawei) | স্ক্যানার / ব্যাকগ্রাউন্ড রিমুভ টাইল নিষ্ক্রিয়, কারণসহ; বাকি সব চলে |
| মডেল ডাউনলোড ব্যর্থ | "ইন্টারনেটে একবার যুক্ত হন" + Retry |
| স্টোরেজ ভরা | সেভের আগে জায়গা যাচাই, স্পষ্ট বার্তা |

## ৯. টেস্টিং

- **JVM unit:** preset মাপ, `Shrinker.targetSize`, ফাইলনাম, ZIP, অ্যাড-ফ্রিকোয়েন্সি নিয়ম, Pro সীমা
- **Robolectric:** ViewModel ও DataStore
- **Instrumented (emulator):** KB লিমিট golden test (১০টা নমুনা ছবি × ৫টা লিমিট), PDF merge/split/encrypt, OCR নমুনা (ইংরেজি, হিন্দি, আরবি, বাংলা), RTL স্ক্রিনশট টেস্ট (আরবি), batch ৫০ ছবি
- **Compose UI test:** হোম → রিসাইজ → সেভ; batch progress; ভাষা বদল
- প্রতিটা SP শেষে `./gradlew test lint` সবুজ

## ১০. ঝুঁকি

| ঝুঁকি | ব্যবস্থা |
|---|---|
| এই cloud container-এ `dl.google.com` ব্লক (Google Maven) | environment-এ `dl.google.com` ও `maven.google.com` allow করা, অথবা নিজের PC-তে বিল্ড |
| PdfBox-Android-এর শেষ রিলিজ 2.0.27.0 (জানুয়ারি 2023) | `core/pdf` ইন্টারফেসের পেছনে রাখা, যাতে পরে বদলানো যায়; ব্যাপক PDF টেস্ট |
| Tesseract-ভিত্তিক ভাষায় (আরবি, বাংলা ইত্যাদি) OCR-এর নির্ভুলতা মাঝারি | ছবি আগে grayscale + threshold; ফল এডিটযোগ্য রাখা |
| Tesseract native lib অ্যাপ বড় করে | App Bundle ABI split; ভাষার ডেটা চাহিদামতো ডাউনলোড |
| Play অ্যাড নীতি | উপরের অ্যাড নিয়ম কোডে test দিয়ে বাঁধা |

## ১১. সাব-প্রজেক্ট ক্রম

প্রতিটার জন্য আলাদা implementation plan (`docs/superpowers/plans/`), আলাদা রিলিজ।

| SP | নাম | নির্ভরতা | রিলিজ |
|---|---|---|---|
| SP0 | ভিত্তি: Gradle, Compose, ডিজাইন সিস্টেম, নেভিগেশন | — | 2.0.0-alpha (internal) |
| SP1 | ফটো টুল + batch | SP0 | 2.0.0 (প্রথম পাবলিক আপডেট) |
| SP2 | স্ক্যান, OCR, QR | SP0 | 2.1.0 |
| SP3 | PDF | SP0, SP2 (searchable PDF) | 2.2.0 |
| SP4 | AI ফটো | SP1 | 2.3.0 |
| SP5 | Files, আয়, বহুভাষা | SP1 | 2.0.0 (SP1-এর সাথে একসাথে) |
| SP6 | রিলিজ ও ASO | প্রতিটা রিলিজে | — |

সুপারিশকৃত বাস্তব ক্রম: **SP0 → SP1 → SP5 → SP2 → SP3 → SP4**, যাতে প্রথম পাবলিক আপডেট থেকেই আয় শুরু হয়।
