#!/usr/bin/env bash
# Gera renpy-runtime-<versão>-arm64.zip a partir do RAPT oficial da mesma versão.
# Uso: ./pack_runtime.sh <versão> [renpy-<versão>-rapt.zip]
# Conteúdo do pacote: librenpython.so (arm64-v8a), runtime.json, NOTICE.txt.
# Nenhum script do zip é executado; só extrai o .so. Saída: build/runtime/renpy-runtime-<v>-arm64.zip
set -euo pipefail
V="${1:?uso: pack_runtime.sh <versão> [rapt.zip]}"
HERE="$(cd "$(dirname "$0")" && pwd)"
B="$HERE/build"; OUT="$B/runtime"; mkdir -p "$OUT"
ZIP="${2:-$B/renpy-$V-rapt.zip}"
[ -f "$ZIP" ] || curl -L -o "$ZIP" "https://www.renpy.org/dl/$V/renpy-$V-rapt.zip"
# sha256 conhecidos (conferidos em 2026-11-06 contra o download oficial); outras versões: só registra
case "$V" in
  8.3.7) EXP=ea3a96dc59a276464fcec30eb5371e12178be563348b1e318a5f236c3511ef9f; PY=3.9;;
  8.4.1) EXP=d6bedea5e5f823516cefd09e461c6ee18af3e4ba210075fd008875014f7a436e; PY=3.12;;
  7.*) EXP=""; PY=2.7;;
  8.0.*|8.1.*|8.2.*|8.3.*) EXP=""; PY=3.9;;
  8.4.*|8.5.*|8.6.*) EXP=""; PY=3.12;;
  *) EXP=""; PY="?";;
esac
SHA=$(sha256sum "$ZIP" | cut -d' ' -f1)
if [ -n "$EXP" ] && [ "$SHA" != "$EXP" ]; then echo "sha256 do RAPT diverge ($SHA)"; exit 1; fi
W="$B/pack_$V"; rm -rf "$W"; mkdir -p "$W"
unzip -q -o -j "$ZIP" "rapt/prototype/renpyandroid/src/main/jniLibs/arm64-v8a/librenpython.so" -d "$W"
file "$W/librenpython.so" | grep -q 'ARM aarch64' || { echo "não é AArch64"; exit 1; }
SOSHA=$(sha256sum "$W/librenpython.so" | cut -d' ' -f1)
cat > "$W/runtime.json" <<JSON
{"renpy": "$V", "python": "$PY", "abi": "arm64-v8a", "so_sha256": "$SOSHA", "rapt_sha256": "$SHA",
 "source": "https://www.renpy.org/dl/$V/renpy-$V-rapt.zip"}
JSON
cat > "$W/NOTICE.txt" <<TXT
librenpython.so: binário oficial do RAPT do Ren'Py $V (renpy-build), sem modificações.
Ren'Py: MIT. Inclui, estaticamente: Python (PSF), SDL2 (zlib), FFmpeg e fribidi (LGPL),
OpenSSL (Apache-2), libaom/libwebp (BSD), HarfBuzz (MIT), FreeType (FTL), entre outros.
Fontes: https://github.com/renpy/renpy-build (commit registrado nas strings do .so) e
https://github.com/renpy/renpy. Redistribuído pelo plugin Ren'Py do M.O.O.N.
TXT
R="$OUT/renpy-runtime-$V-arm64.zip"; rm -f "$R"
( cd "$W" && zip -q -9 "$R" librenpython.so runtime.json NOTICE.txt )
ls -l "$W/librenpython.so" "$R"; cat "$W/runtime.json"
