# PdfBox-Android: JPEG 2000 support is an optional extra library that Anymaker does not ship.
-dontwarn com.gemalto.jp2.**

# PdfBox looks up fonts, filters and encodings by name at run time; keep it whole so
# rarely used PDF features don't break only in the release build.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
