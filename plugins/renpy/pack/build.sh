#!/usr/bin/env bash
# Gera renpy-<versão>-arm64.moonpack (motor DENTRO do M.O.O.N, sem APK).
# Uso: ./build.sh <versão> [rapt.zip]      ex.: ./build.sh 8.4.1
# Conteúdo: adaptador.dex (../adaptador), librenpython.so + runtime.json + NOTICE.txt
# (do runtime ../build/runtime/renpy-runtime-<v>-arm64.zip, gerado por ../pack_runtime.sh),
# main.py (../assets/main.py) e plugin.json (com sha256 de cada arquivo, via ../../tools/pack.sh).
# Saída: Moon/plugins/dist/ (+ index.json).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
V="${1:?informe a versao do motor, ex.: 8.4.1}"
RT="$HERE/../build/runtime/renpy-runtime-$V-arm64.zip"
[ -f "$RT" ] || "$HERE/../pack_runtime.sh" "$V" ${2:+"$2"}
DEX="$HERE/../adaptador/build/adaptador.dex"
[ -f "$DEX" ] || "$HERE/../adaptador/build.sh" ${RAPT_841:+"$RAPT_841"}
S="$HERE/build/$V"; rm -rf "$S"; mkdir -p "$S"
unzip -q -o "$RT" librenpython.so runtime.json NOTICE.txt -d "$S"
cp "$DEX" "$S/adaptador.dex"
cp "$HERE/../assets/main.py" "$S/main.py"
cat > "$S/plugin.json" <<J
{
  "nome": "Ren'Py",
  "motor": "renpy",
  "versao_motor": "$V",
  "versao": "1.0.1",
  "min_moon": "0.3.0",
  "tipo": "dex",
  "entrada": "adaptador.dex",
  "classe": "moon.host.Entrada",
  "incluir": ["librenpython.so", "runtime.json", "NOTICE.txt", "main.py"]
}
J
"$HERE/../../tools/pack.sh" "$S" ${OUT:+--out "$OUT"}
