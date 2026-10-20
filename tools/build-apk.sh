#!/bin/sh
# Builds an APK without Gradle (same toolchain as tv-remote/tv-bridge/build.sh).
# Usage: tools/build-apk.sh <appdir>   → <appdir>/build/$NAME.apk
# <appdir>/app.conf sets NAME, SRC, KEYSTORE, KEYALIAS, KEYPASS.
set -e
APP="$(cd "${1:?usage: build-apk.sh <appdir>}" && pwd)"
. "$APP/app.conf"
export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; BT="$SDK/build-tools/34.0.0"; JAR="$SDK/platforms/android-34/android.jar"
cd "$APP"  # relative paths below: the project path contains spaces
B=build
rm -rf "$B" && mkdir -p "$B/classes" "$B/dex"

"$JAVA_HOME/bin/javac" -Xlint:-options --release 8 -classpath "$JAR" -d "$B/classes" $(find "$SRC" -name '*.java')
"$BT/d8" --min-api 26 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
"$BT/aapt2" compile --dir res -o "$B/res.zip"
ASSETS=""; [ -d assets ] && ASSETS="-A assets"
"$BT/aapt2" link -o "$B/unsigned.apk" --manifest AndroidManifest.xml -I "$JAR" $ASSETS "$B/res.zip"
(cd "$B/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f "$KEYSTORE" ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -storepass "$KEYPASS" -keypass "$KEYPASS" \
  -alias "$KEYALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=$NAME" >/dev/null 2>&1
PATH="$JAVA_HOME/bin:$PATH" "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --out "$B/$NAME.apk" "$B/aligned.apk"
echo "$APP/$B/$NAME.apk"
