package moon.host;

/*
 * Entrada do adaptador Ren'Py (pacote renpy-<versão>-arm64.moonpack).
 * Chamada pelo M.O.O.N (OFS, via JNI) no onCreate da NativeActivity em modo
 * motor, depois de Window.takeSurface(null)/takeInputQueue(null).
 *
 * args (Bundle de Strings): motor, versao, jogo (pasta), jogo_id, saves
 * (pasta de saves do jogo), plugin_dir (cópia interna do pacote: .so, main.py),
 * script (opcional, <Nome>.py).
 */

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import org.renpy.android.PythonSDLActivity;

public final class Entrada {
    private Entrada() {}

    public static void iniciar(Activity a, Bundle args) {
        Log.i(Moon.TAG, "renpy: iniciar " + args);
        Atividade.alvo = a;
        Atividade.args = args;
        Moon.textos(args);
        a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        final Activity act = a;
        Moon.imersivo(a);
        final PythonSDLActivity sdl = new PythonSDLActivity();

        Moon.registrarCiclo(a, new Moon.Ciclo() {
            public void aoIniciar() { sdl.moonOnStart(); }
            public void aoRetomar() { sdl.moonOnResume(); }
            public void aoPausar() { sdl.moonOnPause(); }
            public void aoParar() { sdl.moonOnStop(); }
            public void aoDestruir() { sdl.moonOnDestroy(); }
            public void aoMudarConfig(Configuration c) { sdl.onConfigurationChanged(c); }
            public void aoPoucaMemoria() { sdl.onLowMemory(); }
        });
        JanelaCallback.Alvo alvo = new JanelaCallback.Alvo() {
            public Boolean teclaEvento(KeyEvent e) { return sdl.dispatchKeyEvent(e); }
            public void focoMudou(boolean f) {
                if (f) Moon.imersivo(act);
                sdl.onWindowFocusChanged(f);
            }
        };
        android.view.Window w = a.getWindow();
        JanelaCallback jc = new JanelaCallback(a, w.getCallback(), alvo);
        sdl.moonJanela(jc);
        w.setCallback(jc);

        sdl.moonOnCreate(null);
        Controle.instalar(a, args);
    }
}
