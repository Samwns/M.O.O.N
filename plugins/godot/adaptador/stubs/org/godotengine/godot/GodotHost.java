package org.godotengine.godot;
// STUB só para compilar o adaptador (não vai para o dex): a classe real vem do classes*.dex do template oficial.
import android.app.Activity;
public interface GodotHost {
	Activity getActivity();
	Godot getGodot();
	default void onGodotSetupCompleted() {}
	default void onGodotMainLoopStarted() {}
	default void onGodotForceQuit(Godot instance) {}
	default boolean onGodotForceQuit(int godotInstanceId) { return false; }
	default void onGodotRestartRequested(Godot instance) {}
	default int onNewGodotInstanceRequested(String[] args) { return 0; }
}
