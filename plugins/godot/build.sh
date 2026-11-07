#!/usr/bin/env bash
# Gera o APK do plugin Godot do M.O.O.N a partir do template oficial (sem Gradle, sem Java nosso).
# Uso: ./build.sh [versao]   (padrao 4.7.2)   ->  saida: out/moon-godot-plugin-<versao>.apk
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
VER="${1:-4.7.2}"; TAG="$VER.stable"
GODOT="${GODOT:-/usr/bin/godot}"
SDK="${ANDROID_SDK:-$HOME/Arquivos/sdk_android}"
KS="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
TPL="$HOME/.local/share/godot/export_templates/$TAG"
OUT="$AQUI/out"; mkdir -p "$OUT"

# 1) template oficial (dado, baixado uma vez; so as partes Android sao extraidas)
if [ ! -f "$TPL/android_debug.apk" ]; then
  TMP="$(mktemp -d)"; mkdir -p "$TPL"
  curl -fL -o "$TMP/t.tpz" "https://github.com/godotengine/godot-builds/releases/download/$VER-stable/Godot_v$VER-stable_export_templates.tpz"
  unzip -qoj "$TMP/t.tpz" templates/android_debug.apk templates/android_release.apk templates/version.txt -d "$TPL"
  rm -rf "$TMP"
fi

# 2) config do editor isolada (nao mexe em ~/.config/godot)
CFG="$(mktemp -d)"; mkdir -p "$CFG/godot"
cat > "$CFG/godot/editor_settings-4.tres" <<EOF
[gd_resource type="EditorSettings" format=3]
[resource]
export/android/android_sdk_path = "$SDK"
export/android/java_sdk_path = "$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export/android/debug_keystore = "$KS"
export/android/debug_keystore_user = "androiddebugkey"
export/android/debug_keystore_pass = "android"
EOF

# 2b) motor proprio (build_engine.sh): MOTOR_SO=/caminho/libgodot_android.so troca o .so arm64 do template
PROJ="$AQUI/project"
if [ -n "${MOTOR_SO:-}" ]; then
  PROJ="$(mktemp -d)"; cp -r "$AQUI/project/." "$PROJ/"
  cp "$TPL/android_debug.apk" "$PROJ/../template_proprio.apk"
  TP="$(dirname "$PROJ")/template_proprio.apk"
  W="$(mktemp -d)"; mkdir -p "$W/lib/arm64-v8a"; cp "$MOTOR_SO" "$W/lib/arm64-v8a/libgodot_android.so"
  (cd "$W" && zip -q -0 "$TP" lib/arm64-v8a/libgodot_android.so)
  sed -i "s|^custom_template/debug=.*|custom_template/debug=\"$TP\"|" "$PROJ/export_presets.cfg"
fi

# 3) exporta (Godot troca pacote/nome/permissoes no manifesto e assina)
XDG_CONFIG_HOME="$CFG" "$GODOT" --headless --path "$PROJ" --import >/dev/null 2>&1 || true
XDG_CONFIG_HOME="$CFG" "$GODOT" --headless --path "$PROJ" --export-debug Android "$OUT/moon-godot-plugin-$VER.apk"
rm -rf "$CFG"
ls -l "$OUT/moon-godot-plugin-$VER.apk"
