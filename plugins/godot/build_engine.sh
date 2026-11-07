#!/usr/bin/env bash
# Compila o motor Godot 4.x oficial p/ Android arm64 com disable_path_overrides=no (permite --main-pack externo).
# Uso: ./build_engine.sh [versao] [debug|release]   (padrao 4.7.2 debug)  -> out/engine/godot-<versao>-arm64/ (+ .tar.gz)
# CI: ./build_engine.sh 4.7.2 debug   (NDK escolhido pelo detect.py da propria tag; instale-o antes)
# Requer: scons (pip install --user scons), git, NDK da versao pedida pelo Godot (platform/android/detect.py:get_ndk_version).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
VER="${1:-4.7.2}"; ALVO="${2:-debug}"
SRC="${GODOT_SRC:-$HOME/Arquivos/build/godot-$VER}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Arquivos/sdk_android}"
[ -d "$SRC" ] || git clone -q --depth 1 --branch "$VER-stable" https://github.com/godotengine/godot "$SRC"
cd "$SRC"
NDK="$(python3 -c "import re;print(re.search(r'return \"([0-9.]+)\"',open('platform/android/detect.py').read().split('def get_ndk_version')[1]).group(1))")"
echo "NDK exigido: $NDK"
[ -d "$ANDROID_HOME/ndk/$NDK" ] || { command -v sdkmanager >/dev/null && yes | sdkmanager "ndk;$NDK" >/dev/null; }
[ -d "$ANDROID_HOME/ndk/$NDK" ] || { echo "instale o NDK $NDK em $ANDROID_HOME/ndk"; exit 1; }
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/$NDK"
# a opcao so existe a partir do 4.6 (antes nao ha restricao de --main-pack); passa so se o SConstruct a conhece
PO=""; grep -q '"disable_path_overrides"' SConstruct && PO="disable_path_overrides=no"
time scons platform=android target=template_$ALVO arch=arm64 $PO -j"$(nproc)"
PKG="$AQUI/out/engine/godot-$VER-arm64"; mkdir -p "$PKG"
SO="$(ls platform/android/java/lib/libs/$ALVO/arm64-v8a/libgodot_android.so 2>/dev/null | head -1)"
[ -n "$SO" ] || { ls bin; echo ".so nao encontrado"; exit 1; }
cp "$SO" "$PKG/libgodot_android.$ALVO.so"
# libc++_shared.so do NDK usado (o host precisa dela junto do .so)
cp "$ANDROID_NDK_ROOT"/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so "$PKG/"
printf 'godot=%s\ntarget=template_%s\narch=arm64\nndk=%s\nflags=%s\n' "$VER" "$ALVO" "$NDK" "$PO" > "$PKG/VERSION.txt"
(cd "$AQUI/out/engine" && tar czf "godot-$VER-arm64.tar.gz" "godot-$VER-arm64")
ls -l "$PKG"
echo "Use: MOTOR_SO=$PKG/libgodot_android.$ALVO.so ./build.sh $VER"
