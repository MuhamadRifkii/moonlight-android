package com.limelight.binding.input.virtual_keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.special_keys.SpecialKeyDialogManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualKeyManager implements VirtualKeyView.OnKeyActionListener {

    public interface KeySender {
        void sendKeys(short[] keys, boolean isDown);
    }

    private static final String PREF_NAME = "VirtualKeyboardPrefs";
    private static final String PREF_KEYS_JSON = "KeysJson";

    public static final float MIN_SIZE_FRACTION = 0.05f;
    public static final float MAX_SIZE_FRACTION = 0.35f;

    private final Context context;
    private final FrameLayout layout;
    private final KeySender keySender;
    private final List<VirtualKeyView> keys = new ArrayList<>();
    private boolean isEditMode = false;
    private boolean isVisible = true;

    public VirtualKeyManager(Context context, FrameLayout layout, KeySender keySender) {
        this.context = context;
        this.layout = layout;
        this.keySender = keySender;
        loadKeys();
    }

    public void addVirtualKey() {
        String id = UUID.randomUUID().toString();
        VirtualKeyView keyView = new VirtualKeyView(context, id, this);

        int size = getDefaultSize();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = layout.getWidth() / 2 - size / 2;
        params.topMargin = layout.getHeight() / 2 - size / 2;

        layout.addView(keyView, params);
        keys.add(keyView);

        if (!isEditMode) {
            setEditMode(true);
        } else {
            keyView.setEditMode(true);
        }

        saveKeys();
    }

    public void duplicateKey(VirtualKeyView source) {
        String id = UUID.randomUUID().toString();
        VirtualKeyView keyView = new VirtualKeyView(context, id, this);
        keyView.setMappedKeys(source.getMappedKeys(), source.getLabel());

        FrameLayout.LayoutParams srcParams = (FrameLayout.LayoutParams) source.getLayoutParams();
        int offset = Math.max(20, srcParams.width / 5);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(srcParams.width, srcParams.height);
        params.leftMargin = Math.min(srcParams.leftMargin + offset,
                Math.max(0, layout.getWidth() - srcParams.width));
        params.topMargin = Math.min(srcParams.topMargin + offset,
                Math.max(0, layout.getHeight() - srcParams.height));

        layout.addView(keyView, params);
        keys.add(keyView);

        if (!isEditMode) {
            setEditMode(true);
        } else {
            keyView.setEditMode(true);
        }

        saveKeys();
    }

    public int getDefaultSize() {
        return (int) (context.getResources().getDisplayMetrics().heightPixels * 0.15f);
    }

    public int getMinSize() {
        return (int) (context.getResources().getDisplayMetrics().heightPixels * MIN_SIZE_FRACTION);
    }

    public int getMaxSize() {
        return (int) (context.getResources().getDisplayMetrics().heightPixels * MAX_SIZE_FRACTION);
    }

    public void removeAllKeys() {
        for (VirtualKeyView keyView : new ArrayList<>(keys)) {
            layout.removeView(keyView);
        }
        keys.clear();
        saveKeys();
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        for (VirtualKeyView keyView : keys) {
            keyView.setEditMode(editMode);
        }
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisibility(boolean visible) {
        this.isVisible = visible;
        for (VirtualKeyView keyView : keys) {
            keyView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    @Override
    public void onKeyActionDown(VirtualKeyView view) {
        if (keySender != null && view.getMappedKeys().length > 0) {
            keySender.sendKeys(view.getMappedKeys(), true);
        }
    }

    @Override
    public void onKeyActionUp(VirtualKeyView view) {
        if (keySender != null && view.getMappedKeys().length > 0) {
            keySender.sendKeys(view.getMappedKeys(), false);
        }
    }

    @Override
    public void onMapRequested(VirtualKeyView view) {
        SpecialKeyDialogManager dialogManager = new SpecialKeyDialogManager(context, null, null);
        dialogManager.showKeyboardSelectionDialog(
                selectedKeys -> {
                    view.setMappedKeys(selectedKeys, null);
                    saveKeys();
                },
                view.getMappedKeys());
    }

    public void onEditRequested(final boolean rename, final VirtualKeyView view) {
        if (rename) {
            showRenameDialog(view);
        } else {
            showResizeDialog(view);
        }
    }

    private void showRenameDialog(final VirtualKeyView view) {
        final EditText input = new EditText(context);
        input.setText("UNMAPPED".equals(view.getLabel()) ? "" : view.getLabel());
        input.setSelection(input.getText().length());

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle(R.string.osc_edit_rename_title)
                .setView(container)
                .setPositiveButton(R.string.button_done, (dialog, which) -> {
                    view.setCustomLabel(input.getText().toString());
                    saveKeys();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void showResizeDialog(final VirtualKeyView view) {
        final int min = getMinSize();
        final int max = getMaxSize();
        final int current = Math.max(min, Math.min(max, view.getElementSize()));

        final TextView preview = new TextView(context);

        final SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(100);
        seekBar.setProgress(Math.round((current - min) * 100f / (max - min)));
        preview.setText(context.getString(R.string.osc_edit_resize_preview, current));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                preview.setText(context.getString(R.string.osc_edit_resize_preview,
                        min + (max - min) * progress / 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(preview);
        container.addView(seekBar);

        new AlertDialog.Builder(context)
                .setTitle(R.string.osc_edit_resize_title)
                .setView(container)
                .setPositiveButton(R.string.button_done, (dialog, which) -> {
                    int size = min + (max - min) * seekBar.getProgress() / 100;
                    view.setElementSize(size);
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    public void onDeleted(VirtualKeyView view) {
        layout.removeView(view);
        keys.remove(view);
        saveKeys();
    }

    @Override
    public void onPositionChanged(VirtualKeyView view) {
        saveKeys();
    }

    private void saveKeys() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();

        for (VirtualKeyView keyView : keys) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", keyView.getViewId());

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) keyView.getLayoutParams();
                obj.put("x", params.leftMargin);
                obj.put("y", params.topMargin);
                obj.put("width", params.width);
                obj.put("height", params.height);

                JSONArray keysArray = new JSONArray();
                for (short k : keyView.getMappedKeys()) {
                    keysArray.put(k);
                }
                obj.put("keys", keysArray);

                obj.put("label", keyView.getLabel());

                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefs.edit().putString(PREF_KEYS_JSON, jsonArray.toString()).apply();
    }

    private void loadKeys() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(PREF_KEYS_JSON, null);
        if (jsonStr == null) return;

        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String id = obj.getString("id");

                VirtualKeyView keyView = new VirtualKeyView(context, id, this);

                int x = obj.getInt("x");
                int y = obj.getInt("y");
                int width = obj.getInt("width");
                int height = obj.getInt("height");

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
                params.leftMargin = x;
                params.topMargin = y;

                JSONArray keysArray = obj.getJSONArray("keys");
                short[] mappedKeys = new short[keysArray.length()];
                for (int j = 0; j < keysArray.length(); j++) {
                    mappedKeys[j] = (short) keysArray.getInt(j);
                }

                String label = obj.has("label") ? obj.getString("label") : null;
                keyView.setMappedKeys(mappedKeys, "UNMAPPED".equals(label) ? null : label);

                keyView.setVisibility(isVisible ? android.view.View.VISIBLE : android.view.View.GONE);

                layout.addView(keyView, params);
                keys.add(keyView);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
