#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
sdk_tools="$sdk_root/build-tools/${BUILD_TOOLS_VERSION:-35.0.1}"
sdk_jar="$sdk_root/platforms/android-${ANDROID_API:-35}/android.jar"
rm -rf build/classes build/dex build/gen
mkdir -p build/classes build/dex build/gen
"$sdk_tools/aapt2" compile --dir res -o build/resources.zip
"$sdk_tools/aapt2" link -o build/unsigned.apk -I "$sdk_jar" --manifest AndroidManifest.xml --java build/gen build/resources.zip
javac -source 8 -target 8 -classpath "$sdk_jar" -d build/classes src/dev/sid/motokeyprobe/*.java build/gen/dev/sid/motokeyprobe/R.java
"$sdk_tools/d8" --lib "$sdk_jar" --output build/dex build/classes/dev/sid/motokeyprobe/*.class
jar uf build/unsigned.apk -C build/dex classes.dex
"$sdk_tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/debug.jks ]; then
  keytool -genkeypair -keystore build/debug.jks -storepass android -keypass android -alias androiddebugkey -dname 'CN=Local Key Probe' -keyalg RSA -validity 30 -noprompt
fi
"$sdk_tools/apksigner" sign --ks build/debug.jks --ks-pass pass:android --out build/probe.apk build/aligned.apk
"$sdk_tools/apksigner" verify build/probe.apk
