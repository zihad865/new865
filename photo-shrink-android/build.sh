#!/usr/bin/env bash
# Builds Photo Shrink without Gradle:
#   build/PhotoShrink.aab  - Android App Bundle for Google Play upload
#   build/PhotoShrink.apk  - signed APK for direct install / testing
#
# Needs: JDK 17+, zip/unzip, and from Ubuntu packages:
#   sudo apt-get install android-sdk-platform-23 apksigner zipalign dalvik-exchange
# bundletool (with its bundled aapt2) is downloaded to tools/ on first run.
#
# Signing (upload key): keystore/upload.jks, created on first run with a random
# password saved to keystore/upload-password.txt. Override with KEYSTORE,
# KEYSTORE_PASS, KEY_ALIAS. BACK UP THE KEYSTORE AND PASSWORD.
set -euo pipefail

cd "$(dirname "$0")"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
BUNDLETOOL_VERSION="1.18.1"
BUNDLETOOL_SHA256="675786493983787ffa11550bdb7c0715679a44e1643f3ff980a529e9c822595c"
BUNDLETOOL="${BUNDLETOOL:-tools/bundletool-all-${BUNDLETOOL_VERSION}.jar}"
KEYSTORE="${KEYSTORE:-keystore/upload.jks}"
KEY_ALIAS="${KEY_ALIAS:-upload}"
PASS_FILE="keystore/upload-password.txt"
OUT=build
NAME=PhotoShrink

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

for tool in javac jarsigner keytool dalvik-exchange zipalign apksigner zip unzip curl; do
    command -v "$tool" >/dev/null || die "missing tool: $tool"
done
[[ -f "$ANDROID_JAR" ]] || die "android.jar not found at $ANDROID_JAR"

if [[ ! -f "$BUNDLETOOL" ]]; then
    log "Downloading bundletool $BUNDLETOOL_VERSION"
    mkdir -p "$(dirname "$BUNDLETOOL")"
    curl -fsSL -o "$BUNDLETOOL.part" \
        "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar"
    mv "$BUNDLETOOL.part" "$BUNDLETOOL"
fi

# bundletool and the aapt2 inside it run with access to the signing key: pin the release.
actual_sha="$(sha256sum "$BUNDLETOOL" | cut -d' ' -f1)"
if [[ "$actual_sha" != "$BUNDLETOOL_SHA256" ]]; then
    die "bundletool checksum mismatch: expected $BUNDLETOOL_SHA256, got $actual_sha (delete $BUNDLETOOL to re-download)"
fi

# Re-extract aapt2 from the verified jar on every build unless AAPT2 points elsewhere.
if [[ -z "${AAPT2:-}" ]]; then
    AAPT2=tools/aapt2
    case "$(uname -s)" in
        Linux) plat=linux/aapt2 ;;
        Darwin) plat=macos/aapt2 ;;
        *) die "unsupported OS for bundled aapt2; set AAPT2=/path/to/aapt2" ;;
    esac
    unzip -o -q -j "$BUNDLETOOL" "$plat" -d tools
    chmod +x "$AAPT2"
fi

if [[ -z "${KEYSTORE_PASS:-}" ]]; then
    if [[ -f "$PASS_FILE" ]]; then
        KEYSTORE_PASS="$(<"$PASS_FILE")"
    elif [[ ! -f "$KEYSTORE" ]]; then
        mkdir -p keystore
        KEYSTORE_PASS="$(head -c 18 /dev/urandom | base64 | tr -d '/+=')"
        printf '%s' "$KEYSTORE_PASS" > "$PASS_FILE"
        chmod 600 "$PASS_FILE"
    else
        die "KEYSTORE_PASS not set and $PASS_FILE missing"
    fi
fi
# Passed to signing tools through the environment, never on the command line (visible in ps).
export KEYSTORE_PASS

if [[ ! -f "$KEYSTORE" ]]; then
    log "Creating upload key $KEYSTORE"
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -storetype PKCS12 -keystore "$KEYSTORE" \
        -storepass:env KEYSTORE_PASS -keypass:env KEYSTORE_PASS -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Photo Shrink Upload" >/dev/null 2>&1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/module"

log "Compiling resources"
"$AAPT2" compile --dir res -o "$OUT/compiled.zip"

log "Linking resources (binary for APK, proto for AAB)"
"$AAPT2" link -o "$OUT/res.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --java "$OUT/gen" --auto-add-overlay "$OUT/compiled.zip"
"$AAPT2" link --proto-format -o "$OUT/res-proto.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --auto-add-overlay "$OUT/compiled.zip"

log "Compiling Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')

log "Converting to DEX"
dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"

log "Building APK"
cp "$OUT/res.apk" "$OUT/unaligned.apk"
zip -q -j "$OUT/unaligned.apk" "$OUT/classes.dex"
zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASS --ks-key-alias "$KEY_ALIAS" \
    --out "$OUT/$NAME.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/$NAME.apk"

log "Building AAB"
unzip -q "$OUT/res-proto.apk" -d "$OUT/module"
mkdir -p "$OUT/module/manifest" "$OUT/module/dex"
mv "$OUT/module/AndroidManifest.xml" "$OUT/module/manifest/"
cp "$OUT/classes.dex" "$OUT/module/dex/"
(cd "$OUT/module" && zip -q -r ../base.zip .)
java -jar "$BUNDLETOOL" build-bundle --modules="$OUT/base.zip" --output="$OUT/$NAME.aab"
jarsigner -keystore "$KEYSTORE" -storepass:env KEYSTORE_PASS -keypass:env KEYSTORE_PASS \
    -sigalg SHA256withRSA -digestalg SHA-256 "$OUT/$NAME.aab" "$KEY_ALIAS" >/dev/null
jarsigner -verify "$OUT/$NAME.aab" >/dev/null || die "AAB signature verification failed"
java -jar "$BUNDLETOOL" validate --bundle="$OUT/$NAME.aab" >/dev/null

log "Built $OUT/$NAME.aab ($(du -h "$OUT/$NAME.aab" | cut -f1)) and $OUT/$NAME.apk ($(du -h "$OUT/$NAME.apk" | cut -f1))"
