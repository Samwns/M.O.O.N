#!/usr/bin/env bash
# M.O.O.N — empacota uma pasta de plugin em .moonpack e atualiza o index.json.
#
#   tools/pack.sh <pasta-do-plugin> [opções]
#
#   --versao-motor V   gera só o pacote dessa versão exata do motor (repetível).
#                      Sem a opção: um pacote para cada item de "versoes_motor"
#                      do plugin.json da pasta (ou o "versao_motor" único).
#   --out DIR          saída (padrão: <plugins>/dist). Recebe os .moonpack e o index.json.
#   --base-url URL     prefixo das URLs no index.json (ex.: a pasta de downloads da
#                      release: https://github.com/USUARIO/REPO/releases/download/TAG).
#                      Sem a opção a URL é só o nome do arquivo (relativa ao index.json,
#                      útil para um repositório local: file:///sdcard/.../index.json).
#   --arch A           arm64 (padrão se tipo native/dex) ou any (padrão se webview).
#   --versao V         sobrescreve "versao" (versão do pacote) do plugin.json.
#
# Pasta do plugin (código-fonte), arquivo plugin.json:
#   nome, motor, versao, min_moon, tipo (webview|native|dex)
#   versoes_motor   lista de versões exatas do motor (uma por pacote)   [ou versao_motor]
#   entrada         arquivo de entrada (webview: o shim .js; native: o .so; dex: o .dex)
#   fontes          (opcional) lista de arquivos .js concatenados, em ordem, para formar `entrada`
#   incluir         (opcional) arquivos/pastas extras copiados para o pacote (native/dex)
#   (qualquer outro campo é copiado para o plugin.json final)
# O id do pacote é sempre <motor>-<versao_motor>.
#
# Precisa de: bash, jq, zip, sha256sum.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
die() { echo "pack.sh: $*" >&2; exit 1; }
for t in jq zip sha256sum; do command -v "$t" >/dev/null || die "falta o comando $t"; done

SRC="${1:-}"; [ -n "$SRC" ] && [ -d "$SRC" ] || die "uso: pack.sh <pasta-do-plugin> [--versao-motor V] [--out DIR] [--base-url URL]"
shift
SRC="$(cd "$SRC" && pwd)"
OUT="${OUT:-$ROOT/dist}"; BASE="${BASE_URL:-}"; BASE="${BASE%/}"; ARCH=""; VERSOES=(); PKGVER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --versao-motor) VERSOES+=("$2"); shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --base-url) BASE="${2%/}"; shift 2 ;;
    --arch) ARCH="$2"; shift 2 ;;
    --versao) PKGVER="$2"; shift 2 ;;
    *) die "opção desconhecida: $1" ;;
  esac
done
mkdir -p "$OUT"; OUT="$(cd "$OUT" && pwd)"
SJ="$SRC/plugin.json"; [ -f "$SJ" ] || die "falta $SJ"

motor="$(jq -r '.motor // empty' "$SJ")"; [ -n "$motor" ] || die "plugin.json sem \"motor\""
nome="$(jq -r '.nome // empty' "$SJ")"; [ -n "$nome" ] || die "plugin.json sem \"nome\""
tipo="$(jq -r '.tipo // empty' "$SJ")"
case "$tipo" in webview|native|dex) ;; *) die "tipo inválido: '$tipo' (webview|native|dex)" ;; esac
versao="${PKGVER:-$(jq -r '.versao // "1.0.0"' "$SJ")}"
entrada="$(jq -r '.entrada // empty' "$SJ")"; [ -n "$entrada" ] || die "plugin.json sem \"entrada\""
if [ ${#VERSOES[@]} -eq 0 ]; then
  mapfile -t VERSOES < <(jq -r '(.versoes_motor // [.versao_motor])[] | select(. != null)' "$SJ")
fi
[ ${#VERSOES[@]} -gt 0 ] || die "nenhuma versão do motor (versoes_motor / --versao-motor)"
[ -n "$ARCH" ] || { [ "$tipo" = webview ] && ARCH=any || ARCH=arm64; }

INDEX="$OUT/index.json"
[ -f "$INDEX" ] || echo '{"versao":1,"nome":"","plugins":[]}' > "$INDEX"

for vm in "${VERSOES[@]}"; do
  id="$motor-$vm"
  stage="$(mktemp -d)"
  trap 'rm -rf "$stage"' EXIT
  # entrada: concatenação de "fontes" ou o arquivo existente
  if jq -e '.fontes' "$SJ" >/dev/null 2>&1; then
    : > "$stage/$entrada"
    while IFS= read -r f; do
      [ -f "$SRC/$f" ] || die "fonte não encontrada: $f"
      { echo "/* --- $(basename "$f") --- */"; cat "$SRC/$f"; echo; } >> "$stage/$entrada"
    done < <(jq -r '.fontes[]' "$SJ")
  else
    [ -f "$SRC/$entrada" ] || die "entrada não encontrada: $entrada"
    mkdir -p "$stage/$(dirname "$entrada")"; cp "$SRC/$entrada" "$stage/$entrada"
  fi
  # arquivos extras
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    [ -e "$SRC/$f" ] || die "arquivo a incluir não existe: $f"
    mkdir -p "$stage/$(dirname "$f")"; cp -r "$SRC/$f" "$stage/$(dirname "$f")/"
  done < <(jq -r '(.incluir // [])[]' "$SJ")
  # sha256 de cada arquivo
  arqs="$(cd "$stage" && find . -type f ! -name plugin.json | sed 's|^\./||' | sort | while IFS= read -r f; do
            printf '%s\t%s\n' "$f" "$(sha256sum "$f" | cut -d' ' -f1)"; done |
          jq -Rn '[inputs | split("\t") | {(.[0]): .[1]}] | add // {}')"
  jq --arg id "$id" --arg vm "$vm" --arg versao "$versao" --arg nome "$nome $vm" --arg arch "$ARCH" --argjson arqs "$arqs" '
      del(.versoes_motor, .fontes, .incluir)
      | . + {id:$id, nome:$nome, versao_motor:$vm, versao:$versao, arquitetura:$arch, arquivos:$arqs}' "$SJ" > "$stage/plugin.json"
  pack="$id-$ARCH.moonpack"
  rm -f "$OUT/$pack"
  (cd "$stage" && zip -X -9 -q -r "$OUT/$pack" plugin.json $(ls -A | grep -vx plugin.json))
  sum="$(sha256sum "$OUT/$pack" | cut -d' ' -f1)"; size="$(stat -c %s "$OUT/$pack")"
  url="$pack"; [ -n "$BASE" ] && url="$BASE/$pack"
  tmp="$(mktemp)"
  jq --slurpfile pj "$stage/plugin.json" --arg url "$url" --arg arq "$pack" --arg sum "$sum" --argjson size "$size" '
      ($pj[0]) as $p
      | .plugins = ([.plugins[] | select(.id != $p.id)] + [{
          id:$p.id, nome:$p.nome, motor:$p.motor, versao_motor:$p.versao_motor, versao:$p.versao,
          min_moon:($p.min_moon // "0.0.0"), tipo:$p.tipo, arquitetura:$p.arquitetura,
          arquivo:$arq, url:$url, sha256:$sum, tamanho:$size }] | sort_by(.id))' "$INDEX" > "$tmp"
  mv "$tmp" "$INDEX"
  echo "ok  $pack  ($size bytes, sha256 ${sum:0:12}…)"
  rm -rf "$stage"; trap - EXIT
done
echo "index: $INDEX"
