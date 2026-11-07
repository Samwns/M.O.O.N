#!/usr/bin/env bash
# Gera o APK do plugin Ren'Py do M.O.O.N (sem Gradle): javac + d8 + aapt2 + zipalign + apksigner.
# Uso: ./build.sh [caminho/renpy-8.4.1-rapt.zip]
#   O Java do SDL2/Ren'Py (idêntico em 8.3.x/8.4.x/8.5.x) é extraído do RAPT oficial a cada
#   build; só o PythonSDLActivity adaptado (java/) e o main.py (assets/) são nossos.
# Saída: build/moon-renpy-plugin.apk
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-$HOME/Arquivos/sdk_android}"
BT="${BUILD_TOOLS:-$SDK/build-tools/36.1.0}"
JAR="$SDK/platforms/android-34/android.jar"
RAPT_ZIP="${1:-${RAPT_ZIP:-$HERE/build/renpy-8.4.1-rapt.zip}}"
RAPT_URL="https://www.renpy.org/dl/8.4.1/renpy-8.4.1-rapt.zip"
RAPT_SHA="d6bedea5e5f823516cefd09e461c6ee18af3e4ba210075fd008875014f7a436e"
B="$HERE/build"; mkdir -p "$B"

if [ ! -f "$RAPT_ZIP" ]; then
  curl -L -o "$RAPT_ZIP" "$RAPT_URL"
fi
if [ "$(basename "$RAPT_ZIP")" = "renpy-8.4.1-rapt.zip" ]; then
  echo "$RAPT_SHA  $RAPT_ZIP" | sha256sum -c - >/dev/null || { echo "sha256 do RAPT diverge"; exit 1; }
fi

# 1. Java de terceiros (nenhum script do zip é executado, só extração)
J="$B/java"; rm -rf "$J" "$B/classes" "$B/dex"; mkdir -p "$J" "$B/classes" "$B/dex"
P=rapt/prototype/renpyandroid/src/main/java
unzip -q -o "$RAPT_ZIP" "$P/org/libsdl/*" "$P/org/jnius/*" "$P/org/renpy/*" -d "$B/rapt_x"
cp -r "$B/rapt_x/$P/org" "$J/"
rm -rf "$B/rapt_x"
# fora: jtar/AssetExtract (private.mp3), Play Core, FileProvider (androidx), .class pré-compilados
rm -f "$J/org/renpy/android/"{AssetExtract,RenPyFileProvider}.java
find "$J" -name '*.class' -delete
cp "$HERE/java/org/renpy/android/PythonSDLActivity.java" "$J/org/renpy/android/"

# 2. javac + d8
# (caminhos relativos: o diretório do projeto pode ter espaços)
( cd "$J" && javac -nowarn -source 8 -target 8 -encoding UTF-8 -cp "$JAR" -d "$B/classes" $(find org -name '*.java') ) 2>&1 | grep -v '^Note:\|warning' || true
( cd "$B/classes" && "$BT/d8" --min-api 24 --lib "$JAR" --output "$B/dex" $(find org -name '*.class') )

# 3. APK: manifesto + assets (aapt2) + dex
"$BT/aapt2" link --manifest "$HERE/AndroidManifest.xml" -I "$JAR" -A "$HERE/assets" \
  --min-sdk-version 24 --target-sdk-version 34 -o "$B/unsigned0.apk"
cp "$B/unsigned0.apk" "$B/unsigned1.apk"
( cd "$B/dex" && zip -q -j "$B/unsigned1.apk" classes.dex )
"$BT/zipalign" -f -p 4 "$B/unsigned1.apk" "$B/aligned.apk"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$B/moon-renpy-plugin.apk" "$B/aligned.apk"
rm -f "$B/unsigned0.apk" "$B/unsigned1.apk" "$B/aligned.apk"
ls -l "$B/moon-renpy-plugin.apk" "$B/dex/classes.dex"
