#!/usr/bin/env bash
# Lista as versões estáveis do Godot 4.x (tags "*-stable" de github.com/godotengine/godot, só leitura).
# Uso: ./list_versions.sh [saida]     (padrão: versions.txt ao lado deste script)
# A versão é a tag sem "-stable" (4.7.2, 4.4, 4.3...), a mesma usada por build_engine.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/versions.txt}"
tmp="$(mktemp)"
git ls-remote --tags --refs https://github.com/godotengine/godot 'refs/tags/4.*-stable' |
  sed -E 's|.*refs/tags/||; s/-stable$//' | grep -E '^4\.[0-9]+(\.[0-9]+)*$' | sort -uV > "$tmp"
mv "$tmp" "$OUT"
echo "$(wc -l < "$OUT") versões em $OUT" >&2
