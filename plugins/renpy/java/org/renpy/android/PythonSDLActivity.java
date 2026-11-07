package org.renpy.android;

/*
 * Adaptação MÍNIMA do PythonSDLActivity do RAPT oficial do Ren'Py (MIT) para o
 * plugin do M.O.O.N. Original: rapt/prototype/renpyandroid/.../PythonSDLActivity.java
 *
 * Mantido igual (API que o Python/jnius do Ren'Py usa): mActivity, mFrameLayout,
 * mVbox, addView/removeView, setOrientationBis, hidePresplash, onStop/armOnStop/
 * finishOnStop, openUrl, vibrate, getDPI, setWakeLock, mActivityResult*.
 *
 * Removido:
 *   - Play Asset Delivery / Play Core / gms.tasks (mAssetPackManager, onStateUpdate...)
 *   - Constants (gerado pelo build do RAPT), createStore/IAP
 *   - unpackData/AssetExtract/jtar (private.mp3: aqui o motor vem da pasta do jogo)
 *   - openEditor (usava androidx FileProvider; o plugin não edita arquivos)
 *
 * Acrescentado (única lógica nossa, ~80 linhas, marcada com "MOON"):
 *   - loadLibraries()/getMainSharedObject(): carrega o librenpython.so do runtime
 *     escolhido pelo Intent (files/runtime/<versão>/), importando o zip se preciso.
 *   - preparePython(): em vez de descompactar o private.mp3, monta ANDROID_PRIVATE
 *     (stdlib copiada do lib/python3.x do jogo + main.py) e define as variáveis
 *     de ambiente (saves por jogo em /sdcard/MOON/saves/<jogo>).
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

    // ---- MOON: Intent -> runtime/jogo -------------------------------------
    // Extras (ver README): moon.game_dir, moon.game_id, moon.version,
    // moon.runtime_zip (opcional), moon.script (opcional), moon.test (opcional).
    static final String TAG = "moon-renpy";
    static final String SAVES_ROOT = "/sdcard/MOON/saves";
    static final String DEFAULT_RUNTIME_DIR = "/sdcard/MOON/plugins/renpy";

    String xGameDir, xGameId, xVersion, xRuntimeZip, xScript;
    boolean xTest;

    void readExtras() {
        Intent i = getIntent();
        xGameDir = i.getStringExtra("moon.game_dir");
        xVersion = i.getStringExtra("moon.version");
        xGameId = i.getStringExtra("moon.game_id");
        xRuntimeZip = i.getStringExtra("moon.runtime_zip");
        xScript = i.getStringExtra("moon.script");
        xTest = i.getBooleanExtra("moon.test", false) || "1".equals(i.getStringExtra("moon.test"));
        if (xGameId == null && xGameDir != null) {
            xGameId = new File(xGameDir).getName();
        }
        if (xGameId != null) {
            xGameId = xGameId.replaceAll("[^A-Za-z0-9._-]", "_");
        }
    }

    File runtimeSo() {
        return new File(getFilesDir(), "runtime/" + xVersion + "/librenpython.so");
    }

    /** Importa o pacote renpy-runtime-<v>-arm64.zip para files/runtime/<v>/. */
    void importRuntime() throws IOException {
        File so = runtimeSo();
        if (so.exists()) return;
        File zip = new File(xRuntimeZip != null ? xRuntimeZip
                : DEFAULT_RUNTIME_DIR + "/renpy-runtime-" + xVersion + "-arm64.zip");
        if (!zip.exists()) {
            throw new IOException("runtime " + xVersion + " ausente: " + zip);
        }
        File dir = so.getParentFile();
        dir.mkdirs();
        ZipFile zf = new ZipFile(zip);
        try {
            for (String name : new String[] { "librenpython.so", "runtime.json" }) {
                ZipEntry e = zf.getEntry(name);
                if (e == null) throw new IOException(zip + " sem " + name);
                File out = new File(dir, name + ".tmp");
                copy(zf.getInputStream(e), new FileOutputStream(out));
                out.renameTo(new File(dir, name));
            }
        } finally {
            zf.close();
        }
        so.setReadOnly();   // Android 14+: código carregado dinamicamente deve ser somente leitura
        Log.i(TAG, "runtime " + xVersion + " importado de " + zip + " (" + so.length() + " bytes)");
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
        try {
            if (xVersion == null) throw new IOException("extra moon.version ausente");
            importRuntime();
            Log.i(TAG, "System.load " + runtimeSo());
            System.load(runtimeSo().getAbsolutePath());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(e.getMessage());
        }
    }

    @Override
    protected String getMainSharedObject() {
        // SDL faz dlopen(caminho) na thread do jogo; o .so já está carregado pelo System.load.
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
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.addView(view);

        mVbox = new LinearLayout(this);
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
                Toast.makeText(PythonSDLActivity.this, msg, Toast.LENGTH_LONG).show();
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

            // main.py (assets do plugin), sempre sobrescrito
            File mainPy = new File(private_, "main.py");
            copy(getAssets().open("main.py"), new FileOutputStream(mainPy));
            new File(private_, ".nomedia").createNewFile();

            File pub = new File(SAVES_ROOT, xGameId);
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

        String apkFilePath;
        try {
            ApplicationInfo appInfo = getApplication().getPackageManager().getApplicationInfo(getPackageName(), 0);
            apkFilePath = appInfo.sourceDir;
        } catch (NameNotFoundException e) {
            apkFilePath = "";
        }
        nativeSetEnv("ANDROID_APK", apkFilePath);

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

    @Override
    protected void onNewIntent(Intent intent) {
        Log.v("python", "onNewIntent()");
        setIntent(intent);
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

    public PowerManager.WakeLock wakeLock = null;

    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
            wakeLock.setReferenceCounted(false);
        }
        if (active) wakeLock.acquire(); else wakeLock.release();
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
