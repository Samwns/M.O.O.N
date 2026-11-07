package moon.host;

/*
 * Overlay do modo de controle (MOON.md 8.4), por cima da view do motor.
 *   - botão ⋮ no canto superior direito -> menu (voltar à biblioteca, trocar modo,
 *     esconder ⋮). Sem "teclado virtual": a view do motor não tem InputConnection
 *     (testado no Godot: o teclado abre mas as teclas não chegam); os motores já
 *     abrem o teclado sozinhos nos campos de texto (SDL_StartTextInput / LineEdit);
 *   - modo "gamepad": D-pad + 4 botões em losango que injetam KeyEvents de teclado
 *     pelo Window.Callback da janela (mesmo caminho de uma tecla física):
 *       setas -> DPAD_* (SDL/Godot: setas), confirmar -> ENTER, cancelar -> ESCAPE,
 *       extra -> MENU (Ren'Py: game_menu), 4º -> PAGE_UP (Ren'Py: rollback);
 *   - modo "novel": só o ⋮; o toque vai direto ao motor.
 * O overlay fica no DecorView (o setContentView do motor não o remove). Áreas sem
 * botão não consomem o toque, que segue para o motor.
 * A troca de modo é gravada em <saves>/moon_controle.json (o M.O.O.N lê em
 * host.controle_salvo).
 */

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;

public final class Controle {
    private Controle() {}

    static Activity act;
    static String modo = "novel";
    static String estilo = "teclado";
    static String saves = "";
    static String lang = "pt";
    static FrameLayout raiz;
    static Pad pad;
    static Botao menu;
    static boolean menuOculto = false;

    // ---- textos (pt/en/es) ----
    static String t(String k) {
        boolean en = lang.startsWith("en"), es = lang.startsWith("es");
        switch (k) {
            case "voltar": return en ? "Back to library" : es ? "Volver a la biblioteca" : "Voltar à biblioteca";
            case "p_gamepad": return en ? "Switch to gamepad mode" : es ? "Cambiar a modo gamepad" : "Trocar para modo gamepad";
            case "p_novel": return en ? "Switch to novel mode" : es ? "Cambiar a modo novela" : "Trocar para modo novel";
            case "esconder": return en ? "Hide the menu button (tap the corner to show it)" : es ? "Ocultar el botón del menú (toca la esquina para verlo)" : "Esconder o botão do menu (toque no canto para mostrar)";
        }
        return k;
    }

    /** Monta o overlay. Chamar na thread da UI depois de a janela existir. */
    public static void instalar(final Activity a, Bundle args) {
        act = a;
        String v;
        if ((v = args.getString("controle")) != null && !v.isEmpty()) modo = v;
        if ((v = args.getString("pad_estilo")) != null && !v.isEmpty()) estilo = v;
        if ((v = args.getString("lang")) != null && !v.isEmpty()) lang = v;
        if ((v = args.getString("saves")) != null) saves = v;
        a.runOnUiThread(new Runnable() { public void run() { montar(a); } });
    }

    static float dp(float x) { return x * act.getResources().getDisplayMetrics().density; }

    static void montar(Activity a) {
        if (raiz != null) return;
        try {
            raiz = new FrameLayout(a);
            raiz.setClickable(false);
            raiz.setFocusable(false);
            pad = new Pad(a);
            raiz.addView(pad, new FrameLayout.LayoutParams(-1, -1));
            menu = new Botao(a);
            int s = (int) dp(40);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.RIGHT);
            lp.topMargin = (int) dp(6);
            lp.rightMargin = (int) dp(6);
            raiz.addView(menu, lp);
            ((ViewGroup) a.getWindow().getDecorView()).addView(raiz, new FrameLayout.LayoutParams(-1, -1));
            aplicarModo();
            Log.i(Moon.TAG, "controle: overlay modo=" + modo + " estilo=" + estilo);
        } catch (Throwable t) {
            Log.e(Moon.TAG, "controle: overlay falhou: " + t);
        }
    }

    static void aplicarModo() {
        pad.setVisibility("gamepad".equals(modo) ? View.VISIBLE : View.GONE);
        if (raiz != null) raiz.bringToFront();
    }

    static void salvar() {
        if (saves == null || saves.isEmpty()) return;
        try {
            File d = new File(saves);
            d.mkdirs();
            FileOutputStream o = new FileOutputStream(new File(d, "moon_controle.json"));
            o.write(("{\"modo\": \"" + modo + "\", \"estilo\": \"" + estilo + "\"}\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) {
            Log.e(Moon.TAG, "controle: salvar: " + t);
        }
    }

    static void abrirMenu() {
        final boolean gp = "gamepad".equals(modo);
        CharSequence[] itens = { t("voltar"), t(gp ? "p_novel" : "p_gamepad"), t("esconder") };
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(itens, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Log.i(Moon.TAG, "controle: menu item " + w);
                    switch (w) {
                        case 0: Moon.confirmarSaida(act); break;
                        case 1: modo = gp ? "novel" : "gamepad"; salvar(); aplicarModo(); break;
                        case 2: menuOculto = true; menu.invalidate(); break;
                    }
                }
            })
            .create();
        Moon.mostrar(act, d);
    }

    /** Injeta uma tecla (fonte teclado) pelo Window.Callback: chega ao motor como uma tecla física. */
    static void tecla(int code, boolean down, int rep) {
        long t = SystemClock.uptimeMillis();
        KeyEvent e = new KeyEvent(t, t, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code, rep, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
            InputDevice.SOURCE_KEYBOARD);
        try {
            act.getWindow().getCallback().dispatchKeyEvent(e);
        } catch (Throwable x) {
            Log.e(Moon.TAG, "controle: tecla " + code + ": " + x);
        }
    }

    // ---- botão ⋮ ----
    static final class Botao extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Botao(Context c) { super(c); setFocusable(false); }
        @Override protected void onDraw(Canvas c) {
            if (menuOculto) return;
            float w = getWidth(), h = getHeight();
            p.setColor(0x66000000);
            c.drawCircle(w / 2, h / 2, w / 2, p);
            p.setColor(0xDDFFFFFF);
            float r = w / 14;
            for (int i = -1; i <= 1; i++) c.drawCircle(w / 2, h / 2 + i * w / 4.5f, r, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (menuOculto) { menuOculto = false; invalidate(); }
                else abrirMenu();
            }
            return true;
        }
    }

    // ---- D-pad + losango ----
    static final class Pad extends View {
        // papéis: 0..3 setas (cima, baixo, esq, dir), 4 confirmar, 5 cancelar, 6 extra, 7 quarto
        static final int[] TECLAS = { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PAGE_UP };
        final RectF[] area = new RectF[8];
        final int[] dono = new int[8];        // pointerId que segura o botão (-1 = solto)
        final int[] rep = new int[8];
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Handler h = new Handler(Looper.getMainLooper());

        Pad(Context c) {
            super(c);
            setFocusable(false);
            for (int i = 0; i < 8; i++) { area[i] = new RectF(); dono[i] = -1; }
        }

        // posição no losango (0 baixo, 1 direita, 2 esquerda, 3 topo) de cada papel, por estilo
        int[] posicoes() {
            // nintendo: confirmar = A (direita), cancelar = B (baixo); demais: confirmar embaixo
            if ("nintendo".equals(estilo)) return new int[] { 1, 0, 3, 2 };
            return new int[] { 0, 1, 3, 2 };
        }

        String rotulo(int papel) {
            String[] r;
            if ("xbox".equals(estilo)) r = new String[] { "A", "B", "Y", "X" };
            else if ("ps".equals(estilo)) r = new String[] { "✕", "○", "△", "□" };
            else if ("nintendo".equals(estilo)) r = new String[] { "A", "B", "X", "Y" };
            else r = new String[] { "↵", "Esc", "☰", "↶" };
            return r[papel - 4];
        }

        int cor(int papel) {
            if ("xbox".equals(estilo)) return new int[] { 0xFF3DB24A, 0xFFD8403A, 0xFFE8B21E, 0xFF2F7FD8 }[papel - 4];
            if ("ps".equals(estilo)) return new int[] { 0xFF6E9BE0, 0xFFE0606A, 0xFF48C4A0, 0xFFD58AC8 }[papel - 4];
            return 0xFFFFFFFF;
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            float b = dp(54), m = dp(22);
            // D-pad: canto inferior esquerdo
            float cx = m + b * 1.5f, cy = hh - m - b * 1.5f;
            area[0].set(cx - b / 2, cy - b * 1.5f, cx + b / 2, cy - b / 2);
            area[1].set(cx - b / 2, cy + b / 2, cx + b / 2, cy + b * 1.5f);
            area[2].set(cx - b * 1.5f, cy - b / 2, cx - b / 2, cy + b / 2);
            area[3].set(cx + b / 2, cy - b / 2, cx + b * 1.5f, cy + b / 2);
            // losango: canto inferior direito
            float lx = w - m - b * 1.5f, ly = cy;
            float[][] pos = { { lx, ly + b }, { lx + b, ly }, { lx - b, ly }, { lx, ly - b } };
            int[] ps = posicoes();
            for (int papel = 4; papel < 8; papel++) {
                float[] q = pos[ps[papel - 4]];
                area[papel].set(q[0] - b / 2, q[1] - b / 2, q[0] + b / 2, q[1] + b / 2);
            }
        }

        @Override protected void onDraw(Canvas c) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);   // estilo pode ter mudado
            for (int i = 0; i < 8; i++) {
                RectF r = area[i];
                boolean on = dono[i] >= 0;
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? 0x88FFFFFF : 0x44000000);
                if (i < 4) c.drawRoundRect(r, dp(8), dp(8), p);
                else c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(0x99FFFFFF);
                if (i < 4) c.drawRoundRect(r, dp(8), dp(8), p);
                else c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, p);
                p.setStyle(Paint.Style.FILL);
                if (i < 4) {
                    seta(c, r, i);
                } else {
                    String s = rotulo(i);
                    p.setColor((cor(i) & 0x00FFFFFF) | 0xDD000000);
                    p.setTextSize(dp(s.length() > 1 ? 15 : 22));
                    p.setTextAlign(Paint.Align.CENTER);
                    Paint.FontMetrics fm = p.getFontMetrics();
                    c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, p);
                }
            }
        }

        void seta(Canvas c, RectF r, int dir) {
            float x = r.centerX(), y = r.centerY(), s = r.width() / 5;
            android.graphics.Path t = new android.graphics.Path();
            if (dir == 0) { t.moveTo(x, y - s); t.lineTo(x - s, y + s / 2); t.lineTo(x + s, y + s / 2); }
            if (dir == 1) { t.moveTo(x, y + s); t.lineTo(x - s, y - s / 2); t.lineTo(x + s, y - s / 2); }
            if (dir == 2) { t.moveTo(x - s, y); t.lineTo(x + s / 2, y - s); t.lineTo(x + s / 2, y + s); }
            if (dir == 3) { t.moveTo(x + s, y); t.lineTo(x - s / 2, y - s); t.lineTo(x - s / 2, y + s); }
            t.close();
            p.setColor(0xDDFFFFFF);
            c.drawPath(t, p);
        }

        int achar(float x, float y) {
            float f = dp(6);
            for (int i = 0; i < 8; i++) {
                RectF r = area[i];
                if (x >= r.left - f && x <= r.right + f && y >= r.top - f && y <= r.bottom + f) return i;
            }
            return -1;
        }

        void soltar(int i) {
            if (dono[i] < 0) return;
            dono[i] = -1;
            h.removeCallbacksAndMessages(Integer.valueOf(i));
            tecla(TECLAS[i], false, 0);
        }

        void apertar(final int i, int pid) {
            if (dono[i] >= 0) return;
            dono[i] = pid;
            rep[i] = 0;
            tecla(TECLAS[i], true, 0);
            if (i < 4) repetir(i, 400);
        }

        void repetir(final int i, long ms) {
            h.postAtTime(new Runnable() {
                public void run() {
                    if (dono[i] < 0) return;
                    rep[i]++;
                    tecla(TECLAS[i], true, rep[i]);
                    repetir(i, 90);
                }
            }, Integer.valueOf(i), SystemClock.uptimeMillis() + ms);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            int idx = e.getActionIndex();
            int pid = e.getPointerId(idx);
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                int b = achar(e.getX(idx), e.getY(idx));
                if (b < 0) return a != MotionEvent.ACTION_DOWN;   // fora dos botões: o toque vai ao motor
                apertar(b, pid);
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE) {
                // dedo deslizando no D-pad troca de direção
                for (int k = 0; k < e.getPointerCount(); k++) {
                    int id = e.getPointerId(k);
                    int b = achar(e.getX(k), e.getY(k));
                    for (int i = 0; i < 4; i++) if (dono[i] == id && b != i && b >= 0 && b < 4) { soltar(i); apertar(b, id); invalidate(); }
                }
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
                for (int i = 0; i < 8; i++) if (dono[i] == pid || a == MotionEvent.ACTION_CANCEL || a == MotionEvent.ACTION_UP) soltar(i);
                invalidate();
                return true;
            }
            return true;
        }
    }
}
