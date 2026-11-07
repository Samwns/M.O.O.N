#!/usr/bin/env bash
# M.O.O.N — build
#
#   ./build.sh android            -> build/android/moon-debug.apk
#   ./build.sh android install    -> + adb install -r + am start (+ logcat da tag moon)
#   ./build.sh android release    -> build/android/moon-<versao>-release.apk (assinado com a keystore de release)
#   ./build.sh desktop            -> build/desktop/moon (X11), se src/main.ofs existir
#
# Variaveis: ANDROID_SDK (padrao ~/Arquivos/sdk_android), ANDROID_SERIAL (adb -s),
#            MOON_ENTRY (fonte do APK: .oal ou .ofs; padrao src/ui/app.oal;
#            src/fase0_android.ofs = teste do retangulo).
# Release (obrigatorias): MOON_KEYSTORE (caminho do .jks, fora do repo),
#            MOON_KEY_ALIAS, MOON_KS_PASS. Opcional: MOON_KEY_PASS (padrao = MOON_KS_PASS).
#            Gerar a keystore (uma vez, fora do repo):
#              keytool -genkeypair -v -keystore ~/moon-release.jks -alias moon \
#                -keyalg RSA -keysize 2048 -validity 10000
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OFS_REPO="${OFS_REPO:-$HERE/../../Obsidian-Fault-Script}"
OFS_REPO="$(cd "$OFS_REPO" && pwd)"
BUILD="$HERE/build"
TARGET="${1:-android}"
ACTION="${2:-}"

export OFS_STDLIB_PATH="$OFS_REPO/ofs/stdlib"
export OFS_STDLIB="$OFS_STDLIB_PATH"
export NO_COLOR=1 OFS_PROGRESS=plain

die() { echo "build.sh: $*" >&2; exit 1; }
step() { echo "==> $*"; }

# ---------------------------------------------------------------------------
# ofscc: compilado a partir do fonte do repo (tem OFSCC_TARGET). Nao toca no
# ofscc instalado; fica em build/tools/ofscc e e refeito quando o fonte muda.
# ---------------------------------------------------------------------------
ensure_ofscc() {
  OFSCC="$BUILD/tools/ofscc"
  local src="$OFS_REPO/ofs/ofscc"
  if [ -x "$OFSCC" ] && [ -z "$(find "$src" -name '*.ofs' -newer "$OFSCC" -print -quit)" ]; then
    return
  fi
  command -v ofs >/dev/null || die "precisa do 'ofs' instalado para compilar o ofscc do fonte"
  mkdir -p "$BUILD/tools"
  step "compilando ofscc do fonte ($src)"
  (cd "$OFS_REPO" && OFSCC_MAX_CPU_SECONDS=300 ofs build ofs/ofscc/ofscc.ofs -o "$OFSCC" >/dev/null)
}

# ofs_ir <entrada.ofs> <saida.ll> [triple]
ofs_ir() {
  OFSCC_TARGET="${3:-}" OFSCC_INPUT="$1" OFSCC_MODE=ir OFSCC_C_OUT="$2" "$OFSCC" >"$2.log" 2>&1 \
    || { cat "$2.log" >&2; die "ofscc falhou em $1"; }
  [ -s "$2" ] || { cat "$2.log" >&2; die "ofscc nao gerou $2"; }
}

# Modo release: falha cedo, com mensagem clara, se faltar env ou keystore.
check_release_env() {
  local v missing=()
  for v in MOON_KEYSTORE MOON_KEY_ALIAS MOON_KS_PASS; do
    [ -n "${!v:-}" ] || missing+=("$v")
  done
  [ ${#missing[@]} -eq 0 ] || die "modo release: faltam variaveis: ${missing[*]} (ver cabecalho do build.sh)"
  [ -f "$MOON_KEYSTORE" ] || die "keystore nao encontrada: $MOON_KEYSTORE"
  # Keystore nunca dentro dos repositorios (Moon ou OFS).
  local ks repo
  ks="$(realpath "$MOON_KEYSTORE")"
  for repo in "$(cd "$HERE/.." && pwd)" "$OFS_REPO"; do
    case "$ks" in "$repo"/*) die "a keystore nao pode ficar dentro do repositorio ($repo)" ;; esac
  done
}

# Reassina o APK gerado pelo OFS (debug) com a keystore de release.
sign_release() {
  local SDK="${ANDROID_SDK:-${ANDROID_HOME:-$HOME/Arquivos/sdk_android}}"
  local VERSION APKSIGNER IN OUT
  VERSION="$(sed -n 's/^[[:space:]]*version[[:space:]]*"\([^"]*\)".*/\1/p' "$HERE/src/ui/app.oal" | head -1)"
  [ -n "$VERSION" ] || die "nao achei 'version' em src/ui/app.oal"
  APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
  [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ] || die "apksigner nao encontrado em $SDK/build-tools"
  IN="$BUILD/android/moon-debug.apk"
  OUT="$BUILD/android/moon-$VERSION-release.apk"
  [ -f "$IN" ] || die "APK nao gerado: $IN"
  export MOON_KEY_PASS="${MOON_KEY_PASS:-$MOON_KS_PASS}"
  step "assinando release (alias $MOON_KEY_ALIAS, versao $VERSION)"
  rm -f "$OUT"
  # env: evita senha na linha de comando (e no historico/ps).
  "$APKSIGNER" sign --ks "$MOON_KEYSTORE" --ks-key-alias "$MOON_KEY_ALIAS" \
    --ks-pass env:MOON_KS_PASS --key-pass env:MOON_KEY_PASS --out "$OUT" "$IN"
  "$APKSIGNER" verify --print-certs "$OUT" | head -2
  echo "APK release: $OUT"
}

build_android() {
  # A logica de APK mora na CLI do OFS: `ofs build --target android`.
  # Usa o wrapper do repo (tem o alvo android e compila ofscc/oalc do fonte).
  export ANDROID_SDK="${ANDROID_SDK:-${ANDROID_HOME:-$HOME/Arquivos/sdk_android}}"
  local ENTRY="${MOON_ENTRY:-$HERE/src/ui/app.oal}"
  [ "$ACTION" = "release" ] && check_release_env
  local args=(build --target android "$ENTRY" -o "$BUILD/android/moon-debug.apk")
  case "$ENTRY" in
    *.ofs) args+=(--manifest "$HERE/android/AndroidManifest.xml") ;;
  esac
  if [ "$ACTION" = "install" ]; then
    args+=(--install)
    [ -n "${ANDROID_SERIAL:-}" ] && args+=(--serial "$ANDROID_SERIAL")
  fi
  mkdir -p "$BUILD/android"
  OFS_ANDROID_BUILD="$BUILD/android" bash "$OFS_REPO/src/packaging/linux/ofs" "${args[@]}"
  if [ "$ACTION" = "release" ]; then
    sign_release
    return
  fi
  if [ "$ACTION" = "install" ]; then
    local SDK="${ANDROID_SDK:-${ANDROID_HOME:-$HOME/Arquivos/sdk_android}}"
    local ADBS=("$SDK/platform-tools/adb"); [ -n "${ANDROID_SERIAL:-}" ] && ADBS+=(-s "$ANDROID_SERIAL")
    sleep 2
    "${ADBS[@]}" logcat -d -s moon:I oal:I AndroidRuntime:E 'DEBUG:*' 'libc:*' | tail -20 || true
  fi
}

build_desktop() {
  [ -f "$HERE/src/main.ofs" ] || die "src/main.ofs ainda nao existe (Fase 1)"
  mkdir -p "$BUILD/desktop"
  ofs build "$HERE/src/main.ofs" -o "$BUILD/desktop/moon"
}

case "$TARGET" in
  android) build_android ;;
  desktop) build_desktop ;;
  *) die "alvo desconhecido: $TARGET (use android | desktop)" ;;
esac
