#!/usr/bin/env bash
# M.O.O.N — ferramentas da Fase 1 (nucleo sem janela, desktop Linux).
#
#   ./tools.sh build                 -> bin/moon-import, bin/moon-smoke, bin/moon-selftest
#   ./tools.sh import <jogo> <saida> [opcoes do moon-import]
#   ./tools.sh test <jogo> [<ref>]   -> importa scripts, roda smoke + selftest;
#                                       com <ref> (saida do import_cli.gd do Godot)
#                                       compara os JSON byte a byte
#
# Compilador: OFS_BIN, senao ../../Obsidian-Fault-Script/ofs/dist/ofs, senao `ofs` no PATH.
# (O build.sh ao lado e o da Fase 0 / Android.)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -z "${OFS_BIN:-}" ]; then
  if [ -x "$HERE/../../Obsidian-Fault-Script/ofs/dist/ofs" ]; then
    OFS_BIN="$HERE/../../Obsidian-Fault-Script/ofs/dist/ofs"
  else
    OFS_BIN="$(command -v ofs || true)"
  fi
fi
[ -n "$OFS_BIN" ] || { echo "tools.sh: compilador OFS nao encontrado (defina OFS_BIN)" >&2; exit 1; }
export NO_COLOR=1 OFS_PROGRESS=0

build_one() {
  local src="$1" out="$2"
  echo "==> $out"
  (cd "$HERE" && "$OFS_BIN" build "$src" -o "$out" 2>&1 | grep -Ev '^\s*$|typeck trace| ok ' || true)
  [ -x "$HERE/$out" ] || { echo "tools.sh: falhou $src" >&2; exit 1; }
}

cmd="${1:-build}"
case "$cmd" in
  build)
    mkdir -p "$HERE/bin"
    build_one src/tools/moon_import.ofs bin/moon-import
    build_one src/tools/moon_smoke.ofs bin/moon-smoke
    build_one src/tools/moon_selftest.ofs bin/moon-selftest
    ;;
  import)
    shift
    src="$1"; dst="$2"; shift 2
    "$HERE/bin/moon-import" --src "$src" --dst "$dst" --data "$HERE/data" "$@"
    ;;
  test)
    game="${2:?uso: ./tools.sh test <pasta do jogo> [<saida do Godot>]}"
    ref="${3:-}"
    work="$(mktemp -d "${TMPDIR:-/tmp}/moon-test-XXXXXX")"
    "$HERE/bin/moon-import" --src "$game" --dst "$work/game" --data "$HERE/data" --no-images --audio skip | tail -1
    if [ -n "$ref" ]; then
      for d in scripts scripts_jp; do
        n=0; bad=0
        for f in "$ref/$d"/*.json; do
          n=$((n + 1))
          cmp -s "$f" "$work/game/$d/$(basename "$f")" || { bad=$((bad + 1)); echo "DIFF $d/$(basename "$f")"; }
        done
        echo "$d: $n arquivos, $bad diferentes do Godot"
      done
    fi
    "$HERE/bin/moon-smoke" --game "$work/game" --steps 60 | tail -4
    "$HERE/bin/moon-selftest" --game "$work/game" --work "$work/self" \
      --patch "$HERE/../batch/vn_player_godot/patches_examples/patch_ptbr_exemplo.json" | tail -1
    rm -rf "$work"
    ;;
  *)
    echo "uso: ./tools.sh build | import <jogo> <saida> [...] | test <jogo> [<ref Godot>]" >&2
    exit 2
    ;;
esac
