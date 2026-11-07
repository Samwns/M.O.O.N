package moon.host;

/*
 * Ajudantes comuns dos adaptadores de motor do M.O.O.N (Ren'Py, Godot).
 * Este Java vai SÓ dentro dos pacotes .moonpack (adaptador.dex); o APK do
 * M.O.O.N continua sem código Java (hasCode=false). O M.O.O.N (OFS) carrega o
 * dex com DexClassLoader e chama moon.host.Entrada.iniciar(Activity, Bundle).
 *
 * O motor roda dentro da própria NativeActivity do M.O.O.N, num processo
 * aberto só para ele ("modo motor"). Sair do jogo = reabrir o M.O.O.N sem
 * extras (modo biblioteca) num processo novo.
 */

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

public final class Moon {
    public static final String TAG = "moon-host";

    /** Recebe o ciclo de vida da NativeActivity hospedeira. */
    public interface Ciclo {
        void aoIniciar();
        void aoRetomar();
        void aoPausar();
        void aoParar();
        void aoDestruir();
        void aoMudarConfig(Configuration c);
        void aoPoucaMemoria();
    }

    private Moon() {}

    /** Liga o ciclo de vida da activity (via Application, API 14+) ao motor. */
    public static void registrarCiclo(final Activity a, final Ciclo c) {
        Application app = a.getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity x, Bundle b) {}
            public void onActivityStarted(Activity x) { if (x == a) c.aoIniciar(); }
            public void onActivityResumed(Activity x) { if (x == a) c.aoRetomar(); }
            public void onActivityPaused(Activity x) { if (x == a) c.aoPausar(); }
            public void onActivityStopped(Activity x) { if (x == a) c.aoParar(); }
            public void onActivitySaveInstanceState(Activity x, Bundle b) {}
            public void onActivityDestroyed(Activity x) { if (x == a) c.aoDestruir(); }
        });
        app.registerComponentCallbacks(new ComponentCallbacks2() {
            public void onConfigurationChanged(Configuration cfg) { c.aoMudarConfig(cfg); }
            public void onLowMemory() { c.aoPoucaMemoria(); }
            public void onTrimMemory(int level) {}
        });
    }

    /** Intent da própria activity, sem extras (= biblioteca), em task limpa. */
    public static Intent intentBiblioteca(Activity a) {
        Intent i = new Intent();
        i.setClassName(a.getPackageName(), a.getClass().getName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return i;
    }

    private static boolean pedido = false;

    /** Pede ao sistema para reabrir o M.O.O.N em modo biblioteca. O processo
     *  atual deve morrer logo depois (System.exit do motor ou matarProcesso). */
    public static synchronized void pedirBiblioteca(Activity a) {
        if (pedido) return;
        pedido = true;
        try {
            a.startActivity(intentBiblioteca(a));
            Log.i(TAG, "biblioteca pedida");
        } catch (Throwable t) {
            Log.e(TAG, "nao foi possivel reabrir a biblioteca: " + t);
        }
    }

    /** Volta à biblioteca e encerra este processo (estado do motor é descartado). */
    public static void voltarBiblioteca(Activity a) {
        pedirBiblioteca(a);
        matarProcesso();
    }

    /** Separador dos argumentos extras do motor no extra "moon.args" (um único String). */
    public static final String SEP = "\u001f";

    /**
     * Reabre o MESMO jogo num processo novo (modo motor), com argumentos de linha de
     * comando extras (ex.: OS.create_instance / reinício do Godot). Reaproveita todos os
     * extras moon.* da intent atual; o processo atual é encerrado.
     */
    public static void reabrir(Activity a, String[] extras) {
        try {
            android.content.Intent it = new android.content.Intent(a.getIntent());
            it.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            StringBuilder sb = new StringBuilder();
            if (extras != null) for (String x : extras) { if (sb.length() > 0) sb.append(SEP); sb.append(x); }
            it.putExtra("moon.args", sb.toString());
            Log.i(TAG, "reabrir: " + it.getStringExtra("moon.jogo") + " args=" + sb);
            a.startActivity(it);
        } catch (Throwable t) {
            Log.e(TAG, "reabrir falhou: " + t);
            pedirBiblioteca(a);
        }
        matarProcesso();
    }

    /** Argumentos extras (moon.args/"args") em lista; vazio se não houver. */
    public static java.util.List<String> argsExtras(String s) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        if (s != null && !s.isEmpty()) for (String x : s.split(SEP, -1)) if (!x.isEmpty()) l.add(x);
        return l;
    }

    /** Tela cheia imersiva (barras escondidas; um gesto da borda mostra por um instante). */
    public static void imersivo(Activity a) {
        Window w = a.getWindow();
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = 1;   // SHORT_EDGES
            w.setAttributes(lp);
        }
        w.getDecorView().setSystemUiVisibility(256 | 512 | 1024 | 2 | 4 | 4096);
    }

    // textos do diálogo de saída (a UI pode mandar traduzidos nos args: txt_sair_msg, txt_sair, txt_continuar)
    public static String txtSairMsg = "Sair do jogo e voltar à biblioteca do M.O.O.N?\nO progresso não salvo será perdido.";
    public static String txtSair = "Sair";
    public static String txtContinuar = "Continuar";

    public static void textos(Bundle args) {
        String v;
        if ((v = args.getString("txt_sair_msg")) != null && !v.isEmpty()) txtSairMsg = v;
        if ((v = args.getString("txt_sair")) != null && !v.isEmpty()) txtSair = v;
        if ((v = args.getString("txt_continuar")) != null && !v.isEmpty()) txtContinuar = v;
    }

    /** Diálogo (tema escuro do sistema) que confirma a volta à biblioteca. */
    public static void confirmarSaida(final Activity a) {
        a.runOnUiThread(new Runnable() {
            public void run() {
                android.app.AlertDialog d = new android.app.AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(txtSairMsg)
                    .setPositiveButton(txtSair, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { voltarBiblioteca(a); }
                    })
                    .setNegativeButton(txtContinuar, null)
                    .create();
                mostrar(a, d);
            }
        });
    }

    /** Mostra um diálogo sem tirar o jogo do modo imersivo (barras do sistema continuam escondidas). */
    public static void mostrar(Activity a, android.app.Dialog d) {
        Window w = d.getWindow();
        int nf = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (w != null) w.setFlags(nf, nf);
        d.show();
        if (w != null) {
            w.getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility());
            w.clearFlags(nf);
        }
    }

    public static void matarProcesso() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /** Embrulha o Window.Callback (a própria activity) para o motor ver teclas e foco. */
    public static void embrulharJanela(Activity a, JanelaCallback.Alvo alvo) {
        Window w = a.getWindow();
        w.setCallback(new JanelaCallback(a, w.getCallback(), alvo));
    }
}
