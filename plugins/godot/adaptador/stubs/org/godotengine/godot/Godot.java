package org.godotengine.godot;
// STUB só para compilar (Godot.kt 4.7.2); não vai para o dex.
import android.content.Context;
import android.content.res.Configuration;
import android.widget.FrameLayout;
import java.util.List;
import java.util.Set;
import org.godotengine.godot.plugin.GodotPlugin;
public class Godot {
	public static Godot getInstance(Context c) { return null; }
	public boolean initEngine(GodotHost h, List<String> args, Set<GodotPlugin> plugins) { return false; }
	public FrameLayout onInitRenderView(GodotHost h, FrameLayout f) { return null; }
	public void onStart(GodotHost h) {}
	public void onResume(GodotHost h) {}
	public void onPause(GodotHost h) {}
	public void onStop(GodotHost h) {}
	public void onDestroy(GodotHost h) {}
	public void onBackPressed() {}
	public void onConfigurationChanged(Configuration c) {}
}
