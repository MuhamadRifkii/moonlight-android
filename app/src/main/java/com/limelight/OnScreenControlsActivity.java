package com.limelight;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.limelight.binding.input.OscEditorSession;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyManager;

/**
 * Standalone editor for on-screen controls. Used outside of streaming
 * (e.g. from the app view context menu). While streaming, editing happens
 * in-place over the stream instead.
 */
public class OnScreenControlsActivity extends Activity {

    private FrameLayout canvas;
    private VirtualController virtualController;
    private VirtualKeyManager virtualKeyManager;
    private OscEditorSession editorSession;

    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            OnScreenControlsActivity.this.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    };

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSC layouts use absolute pixel coordinates captured from a full-screen,
        // landscape surface, so this editor must match that space exactly.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Full-screen, laid out over the entire display like the stream itself
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Allow elements to be placed under notches, matching in-stream behavior
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        setContentView(R.layout.activity_configure_virtual_controller);

        canvas = findViewById(R.id.configure_virtual_controller_frameLayout);

        virtualController = new VirtualController(null, canvas, this);
        virtualController.refreshLayout();

        virtualKeyManager = new VirtualKeyManager(this, canvas, null);

        editorSession = new OscEditorSession(this, canvas, virtualController, virtualKeyManager,
                this::finish);
        editorSession.attach();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            hideSystemUi.run();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Covers non-Done exits (e.g. back press); detach is a no-op if already detached
        if (editorSession != null) {
            editorSession.detach();
        }
    }
}
