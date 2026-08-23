package com.limelight.binding.input;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.DigitalButton;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared editor session that allows moving, resizing, adding and removing
 * on-screen controller elements and virtual keyboard keys. Used both by the
 * standalone OnScreenControlsActivity and by the in-stream editor overlay.
 */
public class OscEditorSession implements VirtualControllerElement.OnElementTapListener {

    private final Activity activity;
    private final FrameLayout canvas;
    private final VirtualController virtualController;
    private final VirtualKeyManager virtualKeyManager;
    private final Runnable onDoneCallback;

    private View toolbar;
    private Button modeButton;
    private boolean attached = false;
    private boolean previousKeysVisible = true;

    public OscEditorSession(Activity activity,
                            FrameLayout canvas,
                            VirtualController virtualController,
                            VirtualKeyManager virtualKeyManager,
                            Runnable onDoneCallback) {
        this.activity = activity;
        this.canvas = canvas;
        this.virtualController = virtualController;
        this.virtualKeyManager = virtualKeyManager;
        this.onDoneCallback = onDoneCallback;
    }

    public boolean isAttached() {
        return attached;
    }

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;

        virtualController.setControllerMode(VirtualController.ControllerMode.MoveButtons);
        virtualController.setOnElementTapListener(this);
        previousKeysVisible = virtualKeyManager.isVisible();
        virtualKeyManager.setVisibility(true);
        virtualKeyManager.setEditMode(true);

        toolbar = LayoutInflater.from(activity).inflate(R.layout.view_osc_toolbar, canvas, false);

        ImageButton addButton = toolbar.findViewById(R.id.osc_toolbar_add);
        modeButton = toolbar.findViewById(R.id.osc_toolbar_mode);
        Button resetButton = toolbar.findViewById(R.id.osc_toolbar_reset);
        ImageButton settingsButton = toolbar.findViewById(R.id.osc_toolbar_settings);
        ImageButton doneButton = toolbar.findViewById(R.id.osc_toolbar_done);

        addButton.setOnClickListener(v -> showAddDialog());

        settingsButton.setOnClickListener(v -> showOpacityDialog());

        modeButton.setOnClickListener(v -> {
            VirtualController.ControllerMode newMode =
                    virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons ?
                            VirtualController.ControllerMode.MoveButtons :
                            VirtualController.ControllerMode.ResizeButtons;
            virtualController.setControllerMode(newMode);
            updateModeButtonText();
        });

        resetButton.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle(R.string.osc_reset_title)
                .setMessage(R.string.osc_reset_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> resetToDefaults())
                .setNegativeButton(R.string.no, null)
                .show());

        doneButton.setOnClickListener(v -> {
            detach();
            if (onDoneCallback != null) {
                onDoneCallback.run();
            }
        });

        updateModeButtonText();

        int margin = (int) (24 * activity.getResources().getDisplayMetrics().density + 0.5f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = margin;

        canvas.addView(toolbar, params);
    }

    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;

        virtualController.saveProfile();
        virtualController.setOnElementTapListener(null);
        virtualController.setControllerMode(VirtualController.ControllerMode.Active);
        virtualKeyManager.setEditMode(false);
        virtualKeyManager.setVisibility(previousKeysVisible);

        if (toolbar != null) {
            canvas.removeView(toolbar);
            toolbar = null;
            modeButton = null;
        }
    }

    private void updateModeButtonText() {
        if (modeButton == null) {
            return;
        }
        boolean resize =
                virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
        modeButton.setText(resize ?
                R.string.osc_toolbar_resize_mode : R.string.osc_toolbar_move_mode);
    }

    private void showOpacityDialog() {
        final int initialOpacity = virtualController.getOpacity();

        final TextView preview = new TextView(activity);

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(100);
        seekBar.setProgress(initialOpacity);
        preview.setText(activity.getString(R.string.osc_edit_opacity_preview, initialOpacity));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                // Live preview on the actual overlay
                virtualController.setOpacity(progress);
                preview.setText(activity.getString(R.string.osc_edit_opacity_preview, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(preview);
        container.addView(seekBar);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.osc_edit_opacity_title)
                .setView(container)
                .setPositiveButton(R.string.button_done, (dialog, which) ->
                        PreferenceConfiguration.setOscOpacity(activity, seekBar.getProgress()))
                .setNegativeButton(R.string.button_cancel, (dialog, which) ->
                        virtualController.setOpacity(initialOpacity))
                .show();
    }

    private void showAddDialog() {
        List<String> options = new ArrayList<>();
        final List<Integer> missingElementIds = new ArrayList<>();

        for (int eid : VirtualControllerConfigurationLoader.ALL_ELEMENTS) {
            if (findElement(eid) == null) {
                missingElementIds.add(eid);
                options.add(VirtualControllerConfigurationLoader.getElementName(activity, eid));
            }
        }

        final int keyboardOptionIndex = options.size();
        options.add(activity.getString(R.string.osc_add_virtual_key));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.osc_add_element_title)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which == keyboardOptionIndex) {
                        virtualKeyManager.addVirtualKey();
                    } else if (which < missingElementIds.size()) {
                        addGamepadElement(missingElementIds.get(which));
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private VirtualControllerElement findElement(int eid) {
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element.getElementId() == eid) {
                return element;
            }
        }
        return null;
    }

    private void addGamepadElement(int eid) {
        VirtualControllerElement element =
                VirtualControllerConfigurationLoader.createElement(eid, virtualController, activity);
        if (element == null) {
            return;
        }

        int[] geometry = VirtualControllerConfigurationLoader.getDefaultGeometry(activity, eid);
        virtualController.addElement(element, geometry[0], geometry[1], geometry[2], geometry[3]);

        // Newly created elements use default colors; match the current overlay opacity
        element.setOpacity(virtualController.getOpacity());
        element.invalidate();

        virtualController.saveProfile();
    }

    @Override
    public void onElementTapped(final VirtualControllerElement element) {
        final String name =
                VirtualControllerConfigurationLoader.getElementName(activity, element.getElementId());
        final boolean renameable = VirtualControllerConfigurationLoader.isRenameable(element);

        List<String> options = new ArrayList<>();
        if (renameable) {
            options.add(activity.getString(R.string.osc_edit_rename));
        }
        options.add(activity.getString(R.string.osc_edit_delete));

        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (renameable && which == 0) {
                        showGamepadRenameDialog((DigitalButton) element);
                    } else {
                        deleteGamepadElement(element);
                    }
                })
                .setNeutralButton(R.string.button_cancel, null)
                .show();
    }

    private void showGamepadRenameDialog(final DigitalButton button) {
        final EditText input = new EditText(activity);
        input.setText(button.getText());
        input.setSelection(input.getText().length());

        FrameLayout container = new FrameLayout(activity);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.osc_edit_rename_title)
                .setView(container)
                .setPositiveButton(R.string.button_done, (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        button.setText(text);
                        virtualController.saveProfile();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void deleteGamepadElement(VirtualControllerElement element) {
        String name =
                VirtualControllerConfigurationLoader.getElementName(activity, element.getElementId());

        VirtualControllerConfigurationLoader.markElementDeleted(activity, element.getElementId());
        virtualController.removeElement(element);

        Toast.makeText(activity, name, Toast.LENGTH_SHORT).show();
    }

    private void resetToDefaults() {
        VirtualController.ControllerMode previousMode = virtualController.getControllerMode();

        VirtualControllerConfigurationLoader.clearPreferences(activity);

        for (VirtualControllerElement element : new ArrayList<>(virtualController.getElements())) {
            virtualController.removeElement(element);
        }
        virtualController.refreshLayout();
        virtualController.setControllerMode(previousMode);
        updateModeButtonText();

        virtualKeyManager.removeAllKeys();
    }
}
