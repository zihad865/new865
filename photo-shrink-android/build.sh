#!/usr/bin/env bash
# Builds build/PhotoShrink.apk without Gradle, using the Ubuntu Android SDK packages:
#   sudo apt-get install android-sdk-platform-23 aapt apksigner zipalign dalvik-exchange openjdk-17-jdk
# Signing key: keystore/release.jks (created on first run). Override with
# KEYSTORE, KEYSTORE_PASS, KEY_ALIAS environment variables.
set -euo pipefail

cd "$(dirname "$0")"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
KEYSTORE="${KEYSTORE:-keystore/release.jks}"
KEYSTORE_PASS="${KEYSTORE_PASS:-photoshrink}"
KEY_ALIAS="${KEY_ALIAS:-photoshrink}"
OUT=build
APK="$OUT/PhotoShrink.apk"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

for tool in aapt javac dalvik-exchange zipalign apksigner keytool; do
    command -v "$tool" >/dev/null || die "missing tool: $tool"
done
[[ -f "$ANDROID_JAR" ]] || die "android.jar not found at $ANDROID_JAR"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

log "Generating R.java"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

log "Compiling Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')

log "Converting to DEX"
dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"

log "Packaging"
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/unaligned.apk"
(cd "$OUT" && aapt add unaligned.apk classes.dex >/dev/null)
zipalign -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

if [[ ! -f "$KEYSTORE" ]]; then
    log "Creating signing key $KEYSTORE"
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Photo Shrink" >/dev/null 2>&1
fi

log "Signing"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" \
    --out "$APK" "$OUT/aligned.apk"
apksigner verify "$APK"

log "Built $APK ($(du -h "$APK" | cut -f1))"
