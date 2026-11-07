#!/usr/bin/env bash
# Gera godot-<versão>-arm64.moonpack (motor DENTRO do M.O.O.N, sem APK).
# Uso: ./build.sh [versão]      (padrão 4.7.2)
# Conteúdo:
#   adaptador.dex          ../adaptador (GodotHost + Context com recursos do template)
#   godot-classes*.dex     classes*.dex do template oficial android_debug.apk da MESMA versão
#                          (godot-lib + Kotlin + androidx; Java do motor, de terceiros)
#   godot-res.apk          AndroidManifest.xml + resources.arsc + res/ do mesmo template (R.* do godot-lib)
#   libgodot_android.so    motor próprio (../build_engine.sh, disable_path_overrides=no) + libc++_shared.so
# Saída: Moon/plugins/dist/ (+ index.json), via ../../tools/pack.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
V="${1:-4.7.2}"
TPL="${TEMPLATE_APK:-$HOME/.local/share/godot/export_templates/$V.stable/android_debug.apk}"
ENG="$HERE/../out/engine/godot-$V-arm64"
[ -f "$TPL" ] || { echo "falta o template $TPL (rode ../build.sh $V uma vez)"; exit 1; }
[ -f "$ENG/libgodot_android.debug.so" ] || "$HERE/../build_engine.sh" "$V" debug
DEX="$HERE/../adaptador/build/adaptador.dex"
[ -f "$DEX" ] || "$HERE/../adaptador/build.sh"
S="$HERE/build/$V"; rm -rf "$S"; mkdir -p "$S/x"
unzip -q -o "$TPL" 'classes*.dex' AndroidManifest.xml resources.arsc 'res/*' -d "$S/x"
CP=()
for d in $(cd "$S/x" && ls classes*.dex | sort -V); do
  n="godot-$d"; cp "$S/x/$d" "$S/$n"; CP+=("\"$n\"")
done
( cd "$S/x" && zip -q -0 -r "$S/godot-res.apk" AndroidManifest.xml resources.arsc res )
rm -rf "$S/x"
cp "$ENG/libgodot_android.debug.so" "$S/libgodot_android.so"
cp "$ENG/libc++_shared.so" "$S/"
# libc++_shared do build_engine vem com símbolos (9 MB): strip do NDK (~1,3 MB)
STRIP="${LLVM_STRIP:-$(ls -d "${ANDROID_HOME:-$HOME/Arquivos/sdk_android}"/ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip 2>/dev/null | tail -1)}"
[ -x "$STRIP" ] && "$STRIP" --strip-unneeded "$S/libc++_shared.so"
cp "$DEX" "$S/adaptador.dex"
INC="$(cd "$S" && ls | grep -vx adaptador.dex | sed 's/.*/"&"/' | paste -sd,)"
cat > "$S/plugin.json" <<J
{
  "nome": "Godot",
  "motor": "godot",
  "versao_motor": "$V",
  "versao": "1.0.2",
  "min_moon": "0.3.0",
  "tipo": "dex",
  "entrada": "adaptador.dex",
  "classe": "moon.host.Entrada",
  "classpath": [$(IFS=,; echo "${CP[*]}")],
  "incluir": [$INC]
}
J
"$HERE/../../tools/pack.sh" "$S" ${OUT:+--out "$OUT"}
