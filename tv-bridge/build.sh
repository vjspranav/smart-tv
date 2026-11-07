#!/bin/sh
# Builds tv-bridge.apk without Gradle. Needs JDK 17 + Android build-tools 34 + platform 34.
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; BT="$SDK/build-tools/34.0.0"; JAR="$SDK/platforms/android-34/android.jar"
rm -rf build && mkdir -p build/classes build/dex
"$JAVA_HOME/bin/javac" --release 8 -classpath "$JAR" -d build/classes src/com/livingroom/bridge/*.java 2>&1 | grep -v "bootstrap\|source value 8\|target value 8\|To suppress\|^[0-9] warning" || true
"$BT/d8" --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$JAR" build/res.zip
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f bridge.keystore ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore bridge.keystore -storepass bridge123 -keypass bridge123 \
  -alias bridge -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Living Room Remote Bridge" >/dev/null 2>&1
PATH="$JAVA_HOME/bin:$PATH" "$BT/apksigner" sign --ks bridge.keystore --ks-pass pass:bridge123 --out tv-bridge.apk build/aligned.apk
PATH="$JAVA_HOME/bin:$PATH" "$BT/apksigner" verify tv-bridge.apk && ls -l tv-bridge.apk
