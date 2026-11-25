#!/usr/bin/env bash
# Reproducible build pipeline: locally supplied .apkm -> patched BYOK APK.
#
# Requirements on PATH: java (JDK 17+), python3, keytool.
# Env: APKEDITOR_JAR, APKTOOL_JAR, ANDROID_JAR, D8, APKSIGNER
set -euo pipefail

APKM="$1"                      # locally supplied .apkm bundle (never committed)
BUILD_DIR="${2:-build-out}"
KS="${3:-$BUILD_DIR/poc.jks}"  # POC keystore (created if absent; never committed)

APKEDITOR_JAR="${APKEDITOR_JAR:-tools/APKEditor.jar}"
APKTOOL_JAR="${APKTOOL_JAR:-tools/apktool.jar}"
ANDROID_JAR="${ANDROID_JAR:-/opt/android-sdk/platforms/android-35/android.jar}"
D8="${D8:-/opt/android-sdk/build-tools/35.0.0/d8}"
APKSIGNER="${APKSIGNER:-/opt/android-sdk/build-tools/35.0.0/apksigner}"
KS_PASS="${KS_PASS:-byokpoc}"

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

echo "== 1. merge splits =="
java -jar "$APKEDITOR_JAR" m -i "$APKM" -o merged.apk

echo "== 2. decode =="
java -jar "$APKTOOL_JAR" d -f -o decoded merged.apk >/dev/null

echo "== 3. patch smali + manifest =="
python3 ../patcher/patch_smali.py decoded
python3 ../patcher/patch_manifest.py decoded/AndroidManifest.xml

echo "== 4. BYOK module -> classes5.dex =="
rm -rf out && mkdir -p out/classes out/dex
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
      -d out/classes $(find ../src -name '*.java')
"$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output out/dex \
      $(find out/classes -name '*.class')
mv out/dex/classes.dex out/dex/classes5.dex

echo "== 5. rebuild (extractNativeLibs=true: no zipalign needed on non-x86 hosts) =="
sed -i 's/android:extractNativeLibs="false"/android:extractNativeLibs="true"/' decoded/AndroidManifest.xml
java -jar "$APKTOOL_JAR" b decoded -o unsigned.apk >/dev/null

echo "== 6. append classes5.dex =="
python3 - "$BUILD_DIR" <<'PY'
import sys, zipfile
build = sys.argv[1]
with zipfile.ZipFile('unsigned.apk') as zin, zipfile.ZipFile('unsigned5.apk', 'w') as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zi = zipfile.ZipInfo('classes5.dex', date_time=(2026, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, open('out/dex/classes5.dex', 'rb').read())
PY

echo "== 7. keystore + sign =="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias byok -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=byok-poc, OU=POC"
fi
"$APKSIGNER" sign --ks "$KS" --ks-key-alias byok --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --min-sdk-version 26 --max-sdk-version 36 \
  --out Kimi-byok-poc.apk unsigned5.apk
"$APKSIGNER" verify --print-certs Kimi-byok-poc.apk | head -4

echo "== done: $BUILD_DIR/Kimi-byok-poc.apk =="
