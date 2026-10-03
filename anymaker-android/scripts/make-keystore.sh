#!/usr/bin/env bash
# Creates the Play upload key for Anymaker and a git-ignored keystore.properties
# that the release build reads. Prints the base64 value for the GitHub secret.
#
# Usage: scripts/make-keystore.sh [path/to/upload.jks]
# BACK UP THE KEYSTORE AND PASSWORD: every Play update must be signed with this key.
set -euo pipefail

cd "$(dirname "$0")/.."
KEYSTORE="${1:-keystore/anymaker-upload.jks}"
ALIAS="${ANYMAKER_KEY_ALIAS:-upload}"

command -v keytool >/dev/null || { echo "ERROR: keytool not found (install a JDK)" >&2; exit 1; }
if [[ -f "$KEYSTORE" ]]; then
    echo "ERROR: $KEYSTORE already exists; refusing to overwrite an upload key" >&2
    exit 1
fi

mkdir -p "$(dirname "$KEYSTORE")"
KEYSTORE_PASS="${ANYMAKER_KEYSTORE_PASS:-$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 28)}"
export KEYSTORE_PASS

keytool -genkeypair -storetype PKCS12 -keystore "$KEYSTORE" \
    -storepass:env KEYSTORE_PASS -keypass:env KEYSTORE_PASS -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Anymaker Upload"

umask 077
cat > keystore.properties <<PROPS
storeFile=$KEYSTORE
storePassword=$KEYSTORE_PASS
keyAlias=$ALIAS
keyPassword=$KEYSTORE_PASS
PROPS

echo
echo "Created $KEYSTORE and keystore.properties (both git-ignored)."
echo "For GitHub Actions add these repository secrets:"
echo "  ANYMAKER_KEYSTORE_BASE64 = output of: base64 -w0 $KEYSTORE"
echo "  ANYMAKER_KEYSTORE_PASS   = the storePassword in keystore.properties"
echo "  ANYMAKER_KEY_ALIAS       = $ALIAS"
echo "Back up the keystore and password somewhere safe now."
