package moon.host;

/*
 * Base da SDLActivity no M.O.O.N: NÃO é uma Activity. É um ContextWrapper da
 * NativeActivity hospedeira que oferece os métodos de Activity que o Java do
 * SDL2/Ren'Py usa, repassando-os à activity real. O build troca
 * "SDLActivity extends Activity" por "SDLActivity extends moon.host.Atividade".
 *
 * Os métodos de ciclo de vida (onCreate, onResume...) aqui são vazios: o SDL
 * os sobrescreve e chama super.*; quem os dispara é a Entrada, a partir dos
 * callbacks da Application e do Window.Callback embrulhado.
 */

import android.app.Activity;
import android.app.Application;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Atividade extends ContextWrapper {
    /** Activity hospedeira e argumentos (definidos pela Entrada antes do construtor). */
    public static Activity alvo;
    public static Bundle args;

    protected final Activity mMoonActivity;
    JanelaCallback mMoonJanela;

    public Atividade() {
        super(alvo);
        mMoonActivity = alvo;
    }

    public Activity getAtividade() { return mMoonActivity; }

    // chamados pela Entrada (os on* são protected, como na Activity)
    public void moonJanela(JanelaCallback j) { mMoonJanela = j; }
    public void moonOnCreate(Bundle b) { onCreate(b); }
    public void moonOnStart() { onStart(); }
    public void moonOnResume() { onResume(); }
    public void moonOnPause() { onPause(); }
    public void moonOnStop() { onStop(); }
    public void moonOnDestroy() { onDestroy(); }

    public static String arg(String k) {
        String v = args == null ? null : args.getString(k);
        return v == null ? "" : v;
    }

    // ---- ciclo de vida: vazios (o SDL sobrescreve) ----
    protected void onCreate(Bundle b) {}
    protected void onStart() {}
    protected void onRestart() {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
    protected void onNewIntent(Intent i) {}
    protected void onActivityResult(int req, int res, Intent data) {}
    public void onWindowFocusChanged(boolean f) {}
    public void onLowMemory() {}
    public void onTrimMemory(int level) {}
    public void onConfigurationChanged(Configuration c) {}
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {}
    public void onMultiWindowModeChanged(boolean m) {}
    public void onMultiWindowModeChanged(boolean m, Configuration c) {}

    /** Voltar sem dono: manda o M.O.O.N para segundo plano (o jogo continua). */
    public void onBackPressed() {
        mMoonActivity.moveTaskToBack(true);
    }

    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mMoonJanela != null) return mMoonJanela.teclaOriginal(e);
        return mMoonActivity.dispatchKeyEvent(e);
    }

    // ---- Activity -> activity real ----
    public Window getWindow() { return mMoonActivity.getWindow(); }
    public WindowManager getWindowManager() { return mMoonActivity.getWindowManager(); }
    public void setContentView(View v) { mMoonActivity.setContentView(v); }
    public void addContentView(View v, android.view.ViewGroup.LayoutParams p) { mMoonActivity.addContentView(v, p); }
    public View findViewById(int id) { return mMoonActivity.findViewById(id); }
    public final void runOnUiThread(Runnable r) { mMoonActivity.runOnUiThread(r); }
    public Intent getIntent() { return mMoonActivity.getIntent(); }
    public void setIntent(Intent i) { }
    public final Application getApplication() { return mMoonActivity.getApplication(); }
    public boolean isFinishing() { return mMoonActivity.isFinishing(); }
    public boolean isDestroyed() { return mMoonActivity.isDestroyed(); }
    public void setRequestedOrientation(int o) { mMoonActivity.setRequestedOrientation(o); }
    public int getRequestedOrientation() { return mMoonActivity.getRequestedOrientation(); }
    public boolean isInMultiWindowMode() { return mMoonActivity.isInMultiWindowMode(); }
    public boolean isInPictureInPictureMode() { return mMoonActivity.isInPictureInPictureMode(); }
    public void setTitle(CharSequence t) { mMoonActivity.setTitle(t); }
    public View getCurrentFocus() { return mMoonActivity.getCurrentFocus(); }
    public final void requestPermissions(String[] p, int code) { mMoonActivity.requestPermissions(p, code); }
    public void startActivityForResult(Intent i, int code) { mMoonActivity.startActivityForResult(i, code); }
    public void setResult(int code) { }

    /** Sair do jogo: reabre a biblioteca do M.O.O.N (o motor encerra o processo em seguida). */
    public void finish() { Moon.pedirBiblioteca(mMoonActivity); }
    public void finishAndRemoveTask() { Moon.pedirBiblioteca(mMoonActivity); }
    public void finishAffinity() { Moon.pedirBiblioteca(mMoonActivity); }
    public boolean moveTaskToBack(boolean b) { return mMoonActivity.moveTaskToBack(b); }
}
