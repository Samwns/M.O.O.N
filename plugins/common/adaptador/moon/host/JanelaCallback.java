package moon.host;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/** Window.Callback que repassa tudo à activity original e deixa o motor ver
 *  teclas (dispatchKeyEvent) e foco da janela (a NativeActivity não os entrega ao Java). */
public final class JanelaCallback implements Window.Callback {
    public interface Alvo {
        /** null = comportamento normal da activity. */
        Boolean teclaEvento(KeyEvent e);
        void focoMudou(boolean temFoco);
    }

    private final Window.Callback o;
    private final Alvo alvo;
    private final android.app.Activity act;
    private boolean longo = false;

    public JanelaCallback(android.app.Activity act, Window.Callback original, Alvo alvo) {
        this.act = act;
        this.o = original;
        this.alvo = alvo;
    }

    /** dispatchKeyEvent da activity original (o motor chama como "super"). */
    public boolean teclaOriginal(KeyEvent e) { return o.dispatchKeyEvent(e); }

    public boolean dispatchKeyEvent(KeyEvent e) {
        // voltar pressionado (longo): sair do jogo para a biblioteca, com confirmação
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.isLongPress()) {
                if (!longo) { longo = true; Moon.confirmarSaida(act); }
                return true;
            }
            if (longo) {
                if (e.getAction() == KeyEvent.ACTION_UP) longo = false;
                return true;
            }
        }
        Boolean r = alvo.teclaEvento(e);
        if (r != null) return r;
        return o.dispatchKeyEvent(e);
    }
    public void onWindowFocusChanged(boolean f) {
        o.onWindowFocusChanged(f);
        alvo.focoMudou(f);
    }
    public boolean dispatchKeyShortcutEvent(KeyEvent e) { return o.dispatchKeyShortcutEvent(e); }
    public boolean dispatchTouchEvent(MotionEvent e) { return o.dispatchTouchEvent(e); }
    public boolean dispatchTrackballEvent(MotionEvent e) { return o.dispatchTrackballEvent(e); }
    public boolean dispatchGenericMotionEvent(MotionEvent e) { return o.dispatchGenericMotionEvent(e); }
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent e) { return o.dispatchPopulateAccessibilityEvent(e); }
    public View onCreatePanelView(int f) { return o.onCreatePanelView(f); }
    public boolean onCreatePanelMenu(int f, Menu m) { return o.onCreatePanelMenu(f, m); }
    public boolean onPreparePanel(int f, View v, Menu m) { return o.onPreparePanel(f, v, m); }
    public boolean onMenuOpened(int f, Menu m) { return o.onMenuOpened(f, m); }
    public boolean onMenuItemSelected(int f, MenuItem i) { return o.onMenuItemSelected(f, i); }
    public void onWindowAttributesChanged(WindowManager.LayoutParams p) { o.onWindowAttributesChanged(p); }
    public void onContentChanged() { o.onContentChanged(); }
    public void onAttachedToWindow() { o.onAttachedToWindow(); }
    public void onDetachedFromWindow() { o.onDetachedFromWindow(); }
    public void onPanelClosed(int f, Menu m) { o.onPanelClosed(f, m); }
    public boolean onSearchRequested() { return o.onSearchRequested(); }
    public boolean onSearchRequested(SearchEvent e) { return o.onSearchRequested(e); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback c) { return o.onWindowStartingActionMode(c); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback c, int t) { return o.onWindowStartingActionMode(c, t); }
    public void onActionModeStarted(ActionMode m) { o.onActionModeStarted(m); }
    public void onActionModeFinished(ActionMode m) { o.onActionModeFinished(m); }
    public void onPointerCaptureChanged(boolean c) { o.onPointerCaptureChanged(c); }
}
