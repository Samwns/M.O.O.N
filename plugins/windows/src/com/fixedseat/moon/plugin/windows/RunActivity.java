package com.fixedseat.moon.plugin.windows;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Prototipo do plugin Windows do M.O.O.N.
 *
 * Intent: action com.fixedseat.moon.plugin.windows.RUN
 *   exe   (String, obrigatorio) caminho Unix do .exe, ex. /sdcard/Download/X/X.exe
 *   args  (String) argumentos extras, ex. "-force-d3d11 -screen-width 1280"
 *   prefix(String) nome do prefixo Wine (padrao "default"); fica em files/prefixes/<nome>
 *   env   (String) variaveis extras "K=V;K2=V2" (WINEDEBUG, BOX64_*, DXVK_*)
 * Resultado: a activity termina quando o processo Windows sai;
 * setResult(RESULT_OK | RESULT_CANCELED) com extra "exit" = codigo de saida.
 *
 * O rootfs (glibc arm64 + Box64 + Wine x86_64 do Winlator 10) foi recompilado
 * para /data/data/com.winlator/files/rootfs. No build esse prefixo e trocado
 * por "/proc/self/fd/9" (mesmo comprimento) e todo processo herda o fd 9
 * aberto no rootfs. Assim o mesmo rootfs serve para qualquer nome de pacote.
 */
public class RunActivity extends Activity {
    static final String TAG = "MoonWin";
    private TextView out;
    private volatile Process proc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        out = new TextView(this);
        out.setTextSize(11);
        out.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(out);
        setContentView(sv);
        final Intent it = getIntent();
        new Thread(new Runnable() { public void run() { runGame(it); } }, "moon-win").start();
    }

    @Override protected void onDestroy() {
        Process p = proc;
        if (p != null) p.destroy();
        super.onDestroy();
    }

    private void log(final String s) {
        Log.i(TAG, s);
        runOnUiThread(new Runnable() { public void run() { out.append(s + "\n"); } });
    }

    private void runGame(Intent it) {
        int rc = -1;
        try {
            File root = new File(getFilesDir(), "rootfs");
            if (!new File(root, ".moon_rootfs_ok").exists()) installRootfs(root);
            String exe = it.getStringExtra("exe");
            if (exe == null) { log("sem extra 'exe'; nada a rodar"); return; }
            String args = it.getStringExtra("args");
            String prefixName = it.getStringExtra("prefix");
            if (prefixName == null || !prefixName.matches("[A-Za-z0-9_.-]+")) prefixName = "default";
            File prefix = new File(getFilesDir(), "prefixes/" + prefixName);
            if (!new File(prefix, "system.reg").exists()) makePrefix(root, prefix);

            String r = root.getAbsolutePath();
            File exeF = new File(exe);
            StringBuilder sh = new StringBuilder();
            sh.append("cd ").append(q(exeF.getParent())).append(" || exit 90\n");
            sh.append("exec env LD_LIBRARY_PATH=").append(r).append("/usr/lib ")
              .append(r).append("/usr/local/bin/box64 ").append(r).append("/opt/wine/bin/wine ")
              .append(q(exe));
            if (args != null && !args.trim().isEmpty()) sh.append(' ').append(args.trim());
            sh.append(" 9<").append(r).append('\n');

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", sh.toString());
            java.util.Map<String, String> e = pb.environment();
            e.put("HOME", r + "/home/xuser");
            e.put("USER", "xuser");
            e.put("TMPDIR", r + "/tmp");
            e.put("DISPLAY", ":0");
            e.put("PATH", r + "/opt/wine/bin:" + r + "/usr/local/bin:" + r + "/usr/bin:/system/bin");
            e.put("BOX64_LD_LIBRARY_PATH", r + "/lib/x86_64-linux-gnu");
            e.put("BOX64_RCFILE", r + "/etc/config.box64rc");
            e.put("BOX64_NOBANNER", "1");
            e.put("BOX64_DYNAREC", "1");
            e.put("WINEPREFIX", prefix.getAbsolutePath());
            e.put("WINEDEBUG", "-all");
            e.put("WINEDLLOVERRIDES", "winemenubuilder.exe=d;mscoree=d;mshtml=d");
            String extra = it.getStringExtra("env");
            if (extra != null) for (String kv : extra.split(";")) {
                int i = kv.indexOf('=');
                if (i > 0) e.put(kv.substring(0, i).trim(), kv.substring(i + 1));
            }
            pb.redirectErrorStream(true);
            log("exe=" + exe + " args=" + args + " prefix=" + prefixName);
            long t0 = System.currentTimeMillis();
            proc = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            // saida completa em files/last_run.log; no logcat/tela so as linhas que nao sao trace
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new File(getFilesDir(), "last_run.log"))) {
                int n = 0;
                for (String line; (line = br.readLine()) != null; ) {
                    pw.println(line);
                    if (!line.contains(":trace:") || ++n < 200) log(line);
                }
            }
            rc = proc.waitFor();
            File nb = new File(prefix, ".moon_needs_wineboot");
            if (nb.exists()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(new File(prefix, ".update-timestamp"))) { fw.write("disable\n"); }
                nb.delete();
            }
            log("EXIT=" + rc + " em " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable t) {
            log("erro: " + Log.getStackTraceString(t));
        } finally {
            Intent res = new Intent().putExtra("exit", rc);
            setResult(rc == 0 ? RESULT_OK : RESULT_CANCELED, res);
            if (it.getBooleanExtra("finish", true) && it.getStringExtra("exe") != null) finish();
        }
    }

    /** Extrai assets/rootfs.tar.gz (ja com o prefixo trocado) com o tar do sistema. */
    private void installRootfs(File root) throws Exception {
        log("instalando rootfs (primeira execucao)...");
        File tgz = new File(getCacheDir(), "rootfs.tar.gz");
        try (InputStream in = getAssets().open("rootfs.tar.gz");
             OutputStream os = new FileOutputStream(tgz)) {
            byte[] buf = new byte[1 << 20];
            for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
        }
        root.mkdirs();
        sh("cd " + q(root.getAbsolutePath()) + " && /system/bin/tar -xzf " + q(tgz.getAbsolutePath())
           + " && chmod 755 usr/local/bin/box64");
        tgz.delete();
        new File(root, ".moon_rootfs_ok").createNewFile();
        log("rootfs ok");
    }

    /**
     * Copia o prefixo-modelo (container_pattern do Winlator), liga as DLLs builtin do Wine
     * em system32/syswow64 por symlink (hardlink e negado pelo SELinux) e liga D: ao
     * armazenamento compartilhado. O "wineboot -u" roda na primeira execucao do Wine;
     * depois o .update-timestamp vira "disable" (mesmo truque do Winlator), senao o Wine
     * refaz o update a cada partida (~5 s).
     */
    private void makePrefix(File root, File prefix) throws Exception {
        log("criando prefixo " + prefix);
        prefix.getParentFile().mkdirs();
        String p = q(prefix.getAbsolutePath());
        String w = q(root.getAbsolutePath() + "/opt/wine/lib/wine");
        sh("cp -a " + q(root.getAbsolutePath() + "/home/xuser/.wine") + " " + p
           + " && cd " + p + "/drive_c/windows"
           + " && for f in " + w + "/x86_64-windows/*; do [ -e system32/${f##*/} ] || ln -s \"$f\" system32/; done"
           + " && for f in " + w + "/i386-windows/*; do [ -e syswow64/${f##*/} ] || ln -s \"$f\" syswow64/; done"
           + " && cd " + p + " && rm -f dosdevices/d: && ln -s /storage/emulated/0 dosdevices/d:"
           + " && echo 0 > .update-timestamp");
        new File(prefix, ".moon_needs_wineboot").createNewFile();
    }

    private void sh(String cmd) throws Exception {
        Process p = new ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        for (String line; (line = br.readLine()) != null; ) log(line);
        if (p.waitFor() != 0) throw new RuntimeException("falhou: " + cmd);
    }

    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
}
