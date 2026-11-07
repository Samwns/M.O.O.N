package moon.host;

/*
 * Entrada do adaptador Godot (pacote godot-<versão>-arm64.moonpack).
 * O Godot roda dentro da NativeActivity do M.O.O.N (modo motor), sem
 * GodotActivity/GodotFragment: esta classe implementa GodotHost e usa a classe
 * Godot direto (initEngine -> onInitRenderView -> ciclo de vida).
 *
 * Classes do motor: classes*.dex do template oficial (godot-lib + Kotlin/androidx),
 * no mesmo DexClassLoader (plugin.json "classpath"). Motor nativo:
 * libgodot_android.so próprio (disable_path_overrides=no, aceita --main-pack externo).
 *
 * Recursos (R.* do godot-lib): o dex do template aponta para IDs do resources.arsc
 * do template, que o M.O.O.N não tem. O pacote leva godot-res.apk (manifesto +
 * resources.arsc + res/ do template) e o Context do Godot (Contexto) devolve esses
 * Resources (PackageManager.getResourcesForApplication, API pública).
 * user:// = Contexto.getFilesDir() = pasta de saves do jogo.
 */

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotHost;

public final class Entrada implements GodotHost {
    /** Context do motor: activity real, mas com os Resources do template e user:// nos saves. */
    static final class Contexto extends ContextWrapper {
        final Resources res;
        final File dados;
        Contexto(Context base, Resources res, File dados) {
            super(base);
            this.res = res;
            this.dados = dados;
        }
        @Override public Resources getResources() { return res != null ? res : super.getResources(); }
        @Override public AssetManager getAssets() { return res != null ? res.getAssets() : super.getAssets(); }
        @Override public Context getApplicationContext() { return this; }   // Godot.getInstance usa applicationContext
        @Override public File getFilesDir() { dados.mkdirs(); return dados; }
    }

    final Activity a;
    Godot godot;
    /** true durante o initEngine: o GodotIO usa getActivity() antes do Context para o
     *  user:// (getFilesDir) e o motor guarda o caminho em cache no Main::setup. Com
     *  null ali, ele usa o Contexto -> user:// = pasta de saves. */
    boolean semActivity = true;

    Entrada(Activity a) { this.a = a; }

    public Activity getActivity() { return semActivity ? null : a; }
    public Godot getGodot() { return godot; }

    @Override public void onGodotForceQuit(Godot instance) {
        Log.i(Moon.TAG, "godot: sair -> biblioteca");
        Moon.voltarBiblioteca(a);
    }
    @Override public boolean onGodotForceQuit(int id) {
        Moon.voltarBiblioteca(a);
        return true;
    }
    /** Argumentos extras desta execução (moon.args), mantidos num reinício. */
    List<String> extras = new ArrayList<>();

    // set_restart_on_exit / reinício do motor: reabre o MESMO jogo num processo novo
    // (o Java não recebe os argumentos de set_restart_on_exit; mantém os desta execução).
    @Override public void onGodotRestartRequested(Godot instance) {
        Log.i(Moon.TAG, "godot: reinicio pedido -> reabrindo o mesmo jogo");
        Moon.reabrir(a, extras.toArray(new String[0]));
    }
    // OS.create_instance(args): em modo motor só há um processo; reabre o mesmo jogo com os novos argumentos.
    @Override public int onNewGodotInstanceRequested(String[] args) {
        Log.i(Moon.TAG, "godot: nova instancia pedida -> reabrindo o mesmo jogo com " + java.util.Arrays.toString(args));
        Moon.reabrir(a, args);
        return 1;
    }

    static Resources recursos(Activity a, String apk) {
        try {
            // ApplicationInfo do próprio app apontando para o apk de recursos
            // (getPackageArchiveInfo recusa um apk com hasCode e sem dex).
            PackageManager pm = a.getPackageManager();
            ApplicationInfo ai = new ApplicationInfo(a.getApplicationInfo());
            ai.sourceDir = apk;
            ai.publicSourceDir = apk;
            ai.splitSourceDirs = null;
            ai.splitPublicSourceDirs = null;
            ai.sharedLibraryFiles = null;
            Resources r = pm.getResourcesForApplication(ai);
            Log.i(Moon.TAG, "godot: recursos de " + apk + " " + r);
            return r;
        } catch (Throwable t) {
            Log.e(Moon.TAG, "godot: recursos do template: " + t);
            return null;
        }
    }

    /** Procura o .pck: o próprio caminho; numa pasta, <pasta>.pck ou o maior .pck. */
    static String acharPck(String jogo) {
        File f = new File(jogo);
        if (f.isFile()) return f.getAbsolutePath();
        File[] l = f.listFiles();
        File achado = null;
        if (l != null) for (File x : l) {
            if (!x.isFile() || !x.getName().toLowerCase().endsWith(".pck")) continue;
            if (x.getName().equalsIgnoreCase(f.getName() + ".pck")) return x.getAbsolutePath();
            if (achado == null || x.length() > achado.length()) achado = x;
        }
        return achado != null ? achado.getAbsolutePath() : jogo;
    }

    public static void iniciar(final Activity a, Bundle args) {
        Log.i(Moon.TAG, "godot: iniciar " + args);
        Moon.textos(args);
        String dir = args.getString("plugin_dir");
        String saves = args.getString("saves");
        if (saves == null || saves.isEmpty()) saves = "/sdcard/MOON/saves/godot/" + args.getString("jogo_id");
        String pck = acharPck(args.getString("jogo"));

        a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Moon.imersivo(a);

        final Entrada host = new Entrada(a);
        Contexto ctx = new Contexto(a, recursos(a, dir + "/godot-res.apk"), new File(saves));
        host.godot = Godot.getInstance(ctx);

        List<String> cl = new ArrayList<>();
        cl.add("--main-pack");
        cl.add(pck);
        host.extras = Moon.argsExtras(args.getString("args"));
        cl.addAll(host.extras);
        Log.i(Moon.TAG, "godot: --main-pack " + pck + " user:// " + saves);
        boolean ok = host.godot.initEngine(host, cl, Collections.emptySet());
        host.semActivity = false;
        if (!ok) {
            Log.e(Moon.TAG, "godot: initEngine falhou");
            Moon.voltarBiblioteca(a);
            return;
        }
        FrameLayout f = host.godot.onInitRenderView(host, new FrameLayout(a));
        a.setContentView(f);
        Controle.instalar(a, args);

        Moon.registrarCiclo(a, new Moon.Ciclo() {
            public void aoIniciar() { host.godot.onStart(host); }
            public void aoRetomar() { host.godot.onResume(host); }
            public void aoPausar() { host.godot.onPause(host); }
            public void aoParar() { host.godot.onStop(host); }
            public void aoDestruir() { host.godot.onDestroy(host); }
            public void aoMudarConfig(Configuration c) { host.godot.onConfigurationChanged(c); }
            public void aoPoucaMemoria() {}
        });
        Moon.embrulharJanela(a, new JanelaCallback.Alvo() {
            public Boolean teclaEvento(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) host.godot.onBackPressed();
                    return true;
                }
                return null;
            }
            public void focoMudou(boolean f) { if (f) Moon.imersivo(a); }
        });
    }
}
