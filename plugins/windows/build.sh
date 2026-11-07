#!/usr/bin/env bash
# Gera o APK do plugin Windows do M.O.O.N (prototipo), sem Gradle.
#   ./build.sh            APK completo (com assets/rootfs.tar.gz, ~130 MB)
#   NO_ROOTFS=1 ./build.sh  APK so com codigo (o rootfs ja deve estar em files/rootfs)
# Saida: build/moon-plugin-windows.apk
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-$HOME/Arquivos/sdk_android}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
JAR="$SDK/platforms/android-34/android.jar"
# Binarios de terceiros (Winlator 10: rootfs glibc + Wine 10.10, Box64 0.4.0).
# Hoje vem do launcher legado; antes de apagar o legado, copiar estes 3 arquivos para third_party/.
TPG="${TPG_ASSETS:-$HERE/../../Laucher Tarja Preta Games/tpglator-core/src/main/assets}"
[ -d "$HERE/third_party" ] && TPG="$HERE/third_party"
OUT="$HERE/build"; STAGE="$OUT/stage"
rm -rf "$OUT/classes" "$OUT/apk"; mkdir -p "$OUT/classes" "$OUT/apk/assets"

if [ "${NO_ROOTFS:-0}" != 1 ]; then
  if [ ! -f "$OUT/rootfs.tar.gz" ]; then
    echo "== rootfs: extraindo (dados, nada e executado no PC)"
    rm -rf "$STAGE"; mkdir -p "$STAGE"
    zstd -dc "$TPG/rootfs.tzst" | tar -x -C "$STAGE"
    zstd -dc "$TPG/box64/box64-0.4.0.tzst" | tar -x -C "$STAGE"
    cp "$TPG/box64/default.box64rc" "$STAGE/etc/config.box64rc"
    mkdir -p "$STAGE/home/xuser" "$STAGE/tmp"
    zstd -dc "$TPG/container_pattern.tzst" | tar -x -C "$STAGE/home/xuser"
    python3 -I "$HERE/tools/patch_prefix.py" "$STAGE" /proc/self/fd/9
    tar -C "$STAGE" -czf "$OUT/rootfs.tar.gz" .
    rm -rf "$STAGE"
  fi
  cp "$OUT/rootfs.tar.gz" "$OUT/apk/assets/"
fi

echo "== java"
find "$HERE/src" -name '*.java' | sed 's/.*/"&"/' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt"
mapfile -t CLASSES < <(find "$OUT/classes" -name '*.class')
"$BT/d8" --release --min-api 26 --lib "$JAR" --output "$OUT/apk" "${CLASSES[@]}"

echo "== apk"
"$BT/aapt2" link -o "$OUT/unsigned.apk" --manifest "$HERE/AndroidManifest.xml" -I "$JAR" \
  -A "$OUT/apk/assets" -0 gz
(cd "$OUT/apk" && zip -q -0 "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "${KEYSTORE:-$HOME/.android/debug.keystore}" --ks-pass pass:android \
  --out "$OUT/moon-plugin-windows.apk" "$OUT/aligned.apk"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk"
ls -la "$OUT/moon-plugin-windows.apk"
