#!/usr/bin/env bash
# Adaptador Godot do M.O.O.N (adaptador.dex), sem Gradle: javac + d8.
# Uso: ./build.sh            Saída: build/adaptador.dex
# Compila contra stubs/ (assinaturas do godot-lib 4.x: Godot, GodotHost, GodotPlugin),
# que NÃO entram no dex: as classes reais vêm dos classes*.dex do template oficial
# (ver ../pack/build.sh), no mesmo DexClassLoader.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-$HOME/Arquivos/sdk_android}"
BT="${BUILD_TOOLS:-$SDK/build-tools/36.1.0}"
JAR="$SDK/platforms/android-34/android.jar"
B="$HERE/build"; rm -rf "$B"; mkdir -p "$B/src/moon/host" "$B/classes" "$B/stubc" "$B/dex"
cp "$HERE/java/moon/host/"*.java "$HERE/../../common/adaptador/moon/host/"*.java "$B/src/moon/host/"
( cd "$HERE/stubs" && javac -nowarn -source 8 -target 8 -encoding UTF-8 -cp "$JAR" -d "$B/stubc" $(find org -name '*.java') ) 2>&1 | grep -v '^warning\|^1 warning\|bootstrap' || true
( cd "$B/src" && javac -nowarn -source 8 -target 8 -encoding UTF-8 -cp "$JAR:$B/stubc" -d "$B/classes" $(find moon -name '*.java') ) 2>&1 | grep -v '^warning\|^1 warning\|bootstrap' || true
( cd "$B/classes" && "$BT/d8" --min-api 26 --lib "$JAR" --classpath "$B/stubc" --output "$B/dex" $(find moon -name '*.class') )
mv "$B/dex/classes.dex" "$B/adaptador.dex"
ls -l "$B/adaptador.dex"
