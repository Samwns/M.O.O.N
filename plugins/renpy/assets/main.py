# main.py do plugin Ren'Py do M.O.O.N (executado pelo librenpython via ANDROID_PRIVATE/main.py).
# Só entrega a execução ao script de entrada do próprio jogo (<Nome>.py), que usa o
# renpy/ e a stdlib do jogo. Variáveis (definidas em PythonSDLActivity.preparePython):
# MOON_GAME_DIR, MOON_GAME_ID, MOON_VERSION, MOON_SCRIPT, MOON_TEST, ANDROID_PUBLIC.
import os
import sys
import time

import androidembed


class _Log(object):
    def __init__(self, tag):
        self.tag = tag
        self.buf = ""

    def write(self, s):
        self.buf += s
        while "\n" in self.buf:
            line, self.buf = self.buf.split("\n", 1)
            if line.strip():
                androidembed.log(self.tag + line)

    def flush(self):
        pass

    def isatty(self):
        return False


sys.stdout = _Log("")
sys.stderr = _Log("ERR ")
sys.dont_write_bytecode = True  # não suja a pasta do jogo com __pycache__

game = os.environ["MOON_GAME_DIR"]
print("moon: ola moon (python %s, jogo %s, runtime %s)" % (
    sys.version.split()[0], os.environ["MOON_GAME_ID"], os.environ["MOON_VERSION"]))

if os.environ.get("MOON_TEST") == "1":
    # mesmo caminho de saída do Ren'Py (renpy/bootstrap.py): remove a task e encerra o processo
    print("moon: modo teste, saindo")
    from jnius import autoclass
    import android
    android.activity.finishAndRemoveTask()
    autoclass("java.lang.System").exit(0)

script = os.environ.get("MOON_SCRIPT") or ""
if not script:
    cands = sorted(f for f in os.listdir(game)
                   if f.endswith(".py") and os.path.isfile(os.path.join(game, f)))
    if not cands:
        raise SystemExit("nenhum <Nome>.py em " + game)
    script = cands[0]
script = os.path.join(game, script)

# marcador de execução (o M.O.O.N compara com traceback.txt para detectar erro)
try:
    with open(os.path.join(os.environ["ANDROID_PUBLIC"], "moon_run.json"), "w") as f:
        f.write('{"started": %d, "script": "%s", "version": "%s"}\n' % (
            time.time(), script, os.environ["MOON_VERSION"]))
except Exception as e:
    print("moon: sem marcador:", e)

os.chdir(game)
sys.argv = [script]
import runpy
runpy.run_path(script, run_name="__main__")
