#!/usr/bin/env bash
# Lista as versões do Ren'Py com RAPT (Android) publicado em https://www.renpy.org/dl/ (só leitura).
# Uso: ./list_versions.sh [saida]     (padrão: versions.txt ao lado deste script)
# Inclui 8.x e 7.4+ (versões numéricas x.y.z; ignora pré-lançamentos e "rc/pre").
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/versions.txt}"
BASE="https://www.renpy.org/dl"
tmp="$(mktemp)"
curl -fsSL "$BASE/" | grep -oE 'href="[0-9]+\.[0-9]+(\.[0-9]+)*/"' | sed -E 's/href="//; s|/"||' | sort -uV |
while IFS= read -r v; do
  maj="${v%%.*}"; rest="${v#*.}"; min="${rest%%.*}"
  if [ "$maj" = 8 ] || { [ "$maj" = 7 ] && [ "$min" -ge 4 ]; }; then
    if curl -fsSL "$BASE/$v/" | grep -q "renpy-$v-rapt\.zip\""; then echo "$v"; fi
  fi
done > "$tmp"
mv "$tmp" "$OUT"
echo "$(wc -l < "$OUT") versões em $OUT" >&2
