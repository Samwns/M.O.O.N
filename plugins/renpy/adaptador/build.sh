#!/usr/bin/env bash
# Adaptador Ren'Py do M.O.O.N (adaptador.dex), sem Gradle: javac + d8.
# Uso: ./build.sh [renpy-8.4.1-rapt.zip]      Saída: build/adaptador.dex
# Env: RAPT_VERSION (padrão 8.4.1), ADAPTADOR_BUILD (pasta de saída), ANDROID_SDK, BUILD_TOOLS.
#
# Java de terceiros: org.libsdl.app.* / org.jnius.* / org.renpy.android.* do RAPT
# oficial (sha256 conferido; idêntico em 8.3.x/8.4.x), extraído a cada build.
# Patch automático da SDLActivity (não é mais Activity):
#   "extends Activity"      -> "extends moon.host.Atividade" (ContextWrapper da NativeActivity)
#   new X(this) / SDL.setContext(this) dentro da SDLActivity -> getAtividade() (Context = activity real)
# Nosso: java/moon/host/{Atividade,Entrada}.java, java/org/renpy/android/PythonSDLActivity.java
#        ../../common/adaptador/moon/host/{Moon,JanelaCallback,Controle}.java
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-$HOME/Arquivos/sdk_android}"
BT="${BUILD_TOOLS:-$SDK/build-tools/36.1.0}"
JAR="$SDK/platforms/android-34/android.jar"
RV="${RAPT_VERSION:-8.4.1}"      # versão do RAPT de onde sai o Java (7.x usa o seu próprio)
RAPT_ZIP="${1:-${RAPT_ZIP:-$HERE/../build/renpy-$RV-rapt.zip}}"
RAPT_URL="https://www.renpy.org/dl/$RV/renpy-$RV-rapt.zip"
RAPT_SHA=""; [ "$RV" = 8.4.1 ] && RAPT_SHA="d6bedea5e5f823516cefd09e461c6ee18af3e4ba210075fd008875014f7a436e"
B="${ADAPTADOR_BUILD:-$HERE/build}"; mkdir -p "$B" "$(dirname "$RAPT_ZIP")"

[ -f "$RAPT_ZIP" ] || curl -fL -o "$RAPT_ZIP" "$RAPT_URL"
[ -z "$RAPT_SHA" ] || echo "$RAPT_SHA  $RAPT_ZIP" | sha256sum -c - >/dev/null || { echo "sha256 do RAPT diverge"; exit 1; }

J="$B/java"; rm -rf "$J" "$B/classes" "$B/dex"; mkdir -p "$J" "$B/classes" "$B/dex"
P=rapt/prototype/renpyandroid/src/main/java
unzip -q -o "$RAPT_ZIP" "$P/org/libsdl/*" "$P/org/jnius/*" "$P/org/renpy/*" -d "$B/rapt_x"
cp -r "$B/rapt_x/$P/org" "$J/"
rm -rf "$B/rapt_x"
rm -f "$J/org/renpy/android/"{AssetExtract,RenPyFileProvider}.java
find "$J" -name '*.class' -delete

# patch da SDLActivity (só o corpo da classe SDLActivity, antes de "class SDLMain")
#   getContext() -> mSingleton (o C chama metodos do PythonSDLActivity nele)
python3 - "$J/org/libsdl/app/SDLActivity.java" <<'EOF'
import re, sys
p = sys.argv[1]; s = open(p).read()
cut = s.index("\nclass SDLMain")
head, tail = s[:cut], s[cut:]
assert "public class SDLActivity extends Activity implements" in head
head = head.replace("public class SDLActivity extends Activity implements",
                    "public class SDLActivity extends moon.host.Atividade implements", 1)
head = head.replace("SDL.setContext(this);", "SDL.setContext(getAtividade());", 1)
head, n = re.subn(r"new ([A-Za-z.]+)\(this\)", r"new \1(getAtividade())", head)
assert n >= 5, n
# o C (SDL_AndroidGetActivity -> SDLActivity.getContext()) chama preparePython etc. nesse objeto:
# getContext() devolve o proprio SDLActivity; casts para Activity usam a activity real.
a = "    public static Context getContext() {\n        return SDL.getContext();\n    }"
assert a in head
head = head.replace(a, "    public static Context getContext() {\n        return mSingleton != null ? mSingleton : SDL.getContext();\n    }")
head = head.replace("(Activity)getContext()", "mSingleton.getAtividade()")
open(p, "w").write("// PATCH M.O.O.N: SDLActivity hospedada (ver adaptador/build.sh)\n" + head + tail)
EOF

mkdir -p "$J/moon/host"
cp "$HERE/java/moon/host/"*.java "$HERE/../../common/adaptador/moon/host/"*.java "$J/moon/host/"
cp "$HERE/java/org/renpy/android/PythonSDLActivity.java" "$J/org/renpy/android/"

( cd "$J" && javac -nowarn -source 8 -target 8 -encoding UTF-8 -cp "$JAR" -d "$B/classes" $(find org moon -name '*.java') ) 2>&1 | grep -v '^Note:\|^warning\|^1 warning\|bootstrap class path' || true
( cd "$B/classes" && "$BT/d8" --min-api 26 --lib "$JAR" --output "$B/dex" $(find org moon -name '*.class') )
mv "$B/dex/classes.dex" "$B/adaptador.dex"
ls -l "$B/adaptador.dex"
