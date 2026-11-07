package org.renpy.android;

/*
 * PythonSDLActivity do ADAPTADOR (pacote renpy-<versão>-arm64.moonpack): o Ren'Py
 * roda dentro da NativeActivity do M.O.O.N. Derivado do ../../java/... do plugin APK
 * (que já é uma adaptação mínima do RAPT oficial, MIT). Diferenças:
 *   - SDLActivity estende moon.host.Atividade (ContextWrapper da NativeActivity), não Activity;
 *   - argumentos vêm do Bundle da Entrada (Atividade.arg), não do Intent;
 *   - librenpython.so e main.py vêm da cópia interna do pacote (plugin_dir);
 *   - saves em arg "saves" (/sdcard/MOON/saves/renpy/<jogo>);
 *   - setWakeLock usa FLAG_KEEP_SCREEN_ON (o M.O.O.N não pede WAKE_LOCK);
 *   - sair (finishAndRemoveTask + System.exit do renpy/bootstrap.py) reabre a biblioteca.
 */

import org.libsdl.app.SDLActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PythonSDLActivity extends SDLActivity {

    /** Python acessa a activity por aqui. */
    public static PythonSDLActivity mActivity = null;

    public FrameLayout mFrameLayout;
    public LinearLayout mVbox;
    public StoreInterface mStore = null;
    public ImageView mPresplash = null;

    // ---- MOON: argumentos da Entrada -> runtime/jogo -----------------------
    static final String TAG = "moon-renpy";

    String xGameDir, xGameId, xVersion, xScript, xSaves, xPluginDir;
    boolean xTest;

    void readExtras() {
        xGameDir = arg("jogo");
        xVersion = arg("versao");
        xGameId = arg("jogo_id");
        xScript = arg("script");
        xSaves = arg("saves");
        xPluginDir = arg("plugin_dir");
        xTest = "1".equals(arg("teste"));
        if (xGameId.length() == 0) xGameId = new File(xGameDir).getName();
        xGameId = xGameId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (xSaves.length() == 0) xSaves = "/sdcard/MOON/saves/renpy/" + xGameId;
    }

    File runtimeSo() {
        return new File(xPluginDir, "librenpython.so");
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        try {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
            out.close();
        }
    }

    static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            String[] names = src.list();
            if (names != null) for (String n : names) copyTree(new File(src, n), new File(dst, n));
        } else {
            copy(new FileInputStream(src), new FileOutputStream(dst));
        }
    }

    static void recursiveDelete(File f) {
        if (f.isDirectory()) {
            File[] l = f.listFiles();
            if (l != null) for (File r : l) recursiveDelete(r);
        }
        f.delete();
    }

    @Override
    public void loadLibraries() {
        readExtras();
        File so = runtimeSo();
        if (!so.exists()) throw new UnsatisfiedLinkError("runtime ausente: " + so);
        Log.i(TAG, "System.load " + so);
        System.load(so.getAbsolutePath());   // deste class loader: as native do SDL ligam aqui
    }

    @Override
    protected String getMainSharedObject() {
        return runtimeSo().getAbsolutePath();
    }
    // ---- fim do bloco MOON ------------------------------------------------

    public void addView(View view, int index) {
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(getAtividade());
        mFrameLayout.addView(view);

        mVbox = new LinearLayout(getAtividade());
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        super.setContentView(mVbox);
    }

    // Faz o SDL respeitar a orientação do manifesto.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        return;
    }

    public void toastError(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getAtividade(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public native void nativeSetEnv(String variable, String value);

    /** Chamado pelo SDL_main (thread nativa) antes de iniciar o Python. */
    public void preparePython() {
        Log.v("python", "Starting preparePython.");
        mActivity = this;

        try {
            File private_ = new File(getFilesDir(), "rt-" + xVersion);
            File game = new File(xGameDir);
            if (!game.isDirectory()) throw new IOException("jogo ausente: " + xGameDir);

            // stdlib: lib/python3.x do jogo -> ANDROID_PRIVATE/lib/ (config.home = ANDROID_PRIVATE)
            File[] libs = new File(game, "lib").listFiles();
            File pylib = null;
            if (libs != null) for (File l : libs) {
                if (l.isDirectory() && l.getName().matches("python3\\.\\d+")) pylib = l;
            }
            if (pylib == null) throw new IOException("lib/python3.x ausente em " + game);
            File dstLib = new File(private_, "lib/" + pylib.getName());
            File marker = new File(private_, "lib/.copied-" + pylib.getName());
            if (!marker.exists() || !dstLib.isDirectory()) {
                Log.i(TAG, "copiando stdlib " + pylib + " -> " + dstLib);
                recursiveDelete(new File(private_, "lib"));
                copyTree(pylib, dstLib);
                marker.createNewFile();
            }

            // main.py (do pacote), sempre sobrescrito
            File mainPy = new File(private_, "main.py");
            copy(new FileInputStream(new File(xPluginDir, "main.py")), new FileOutputStream(mainPy));
            new File(private_, ".nomedia").createNewFile();

            File pub = new File(xSaves);
            pub.mkdirs();

            nativeSetEnv("ANDROID_PRIVATE", private_.getAbsolutePath());
            nativeSetEnv("ANDROID_PUBLIC", pub.getAbsolutePath());
            nativeSetEnv("ANDROID_OLD_PUBLIC", new File(pub, "old").getAbsolutePath());
            nativeSetEnv("MOON_GAME_DIR", game.getAbsolutePath());
            nativeSetEnv("MOON_GAME_ID", xGameId);
            nativeSetEnv("MOON_VERSION", xVersion);
            nativeSetEnv("MOON_SCRIPT", xScript == null ? "" : xScript);
            nativeSetEnv("MOON_TEST", xTest ? "1" : "0");
        } catch (IOException e) {
            Log.e(TAG, "preparePython falhou: " + e);
            toastError("M.O.O.N Ren'Py: " + e.getMessage());
            finish();
            try { Thread.sleep(1500); } catch (InterruptedException ie) { /* pass */ }
            System.exit(1);   // sem env o Python não pode iniciar
            return;
        }

        nativeSetEnv("ANDROID_APK", getApplicationInfo().sourceDir);

        Log.v("python", "Finished preparePython.");
    }

    public void hidePresplash() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActivity.mPresplash != null) {
                    mActivity.mLayout.removeView(mActivity.mPresplash);
                    mActivity.mPresplash = null;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.v("python", "onDestroy()");
        super.onDestroy();
        if (mStore != null) {
            mStore.destroy();
        }
    }


    public boolean mStopDone = true;

    @Override
    public void onStop() {
        Log.v("python", "onStop() start.");
        super.onStop();
        long startTime = System.currentTimeMillis();
        synchronized (this) {
            while (true) {
                if (mStopDone) break;
                if (startTime + 8000 < System.currentTimeMillis()) break;  // backstop
                try {
                    this.wait(100);
                } catch (InterruptedException e) { /* pass */ }
            }
        }
        Log.v("python", "onStop() done.");
    }

    public void armOnStop() {
        Log.v("python", "armOnStop()");
        mStopDone = false;
    }

    public void finishOnStop() {
        Log.v("python", "finishOnStop()");
        synchronized (this) {
            mStopDone = true;
            this.notifyAll();
        }
    }

    // APIs públicas usadas pelo pacote Python `android` ------------------------

    public void openUrl(String url) {
        openURL(url);
    }

    public void vibrate(double s) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot((int) (1000 * s), VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate((int) (1000 * s));
            }
        }
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public void setWakeLock(final boolean active) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (active) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    public int mActivityResultRequestCode = -1;
    public int mActivityResultResultCode = -1;
    public Intent mActivityResultResultData = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (mStore != null && mStore.onActivityResult(requestCode, resultCode, resultData)) {
            return;
        }
        mActivityResultRequestCode = requestCode;
        mActivityResultResultCode = resultCode;
        mActivityResultResultData = resultData;
        super.onActivityResult(requestCode, resultCode, resultData);
    }
}
