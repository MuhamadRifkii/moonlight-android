package com.limelight.binding.input.special_keys;

import static com.limelight.binding.input.KeyboardTranslator.KEY_NAMES;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SpecialKeyDialogManager {

    private final Context context;
    private final SpecialKeyManager specialKeyManager;
    private final KeyboardTranslator keyboardTranslator;
    private AlertDialog currentListDialog;
    private CustomSpecialKeyDataChangeListener dataChangeListener;
    private ItemTouchHelper itemTouchHelper;

    public interface OnKeysSelectedListener {
        void onKeysSelected(short[] selectedKeys);
    }

    public SpecialKeyDialogManager(Context context, SpecialKeyManager specialKeyManager, CustomSpecialKeyDataChangeListener listener) {
        this.context = context;
        this.specialKeyManager = specialKeyManager;
        this.keyboardTranslator = new KeyboardTranslator();
        this.dataChangeListener = listener;
    }

    public void showManageCustomKeysDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_key_management_list, null);
        builder.setView(dialogView);
        builder.setTitle(context.getString(R.string.game_menu_manage_custom_keys));

        RecyclerView recyclerView = dialogView.findViewById(R.id.custom_keys_recycler_view);
        Button addCustomKeyButton = dialogView.findViewById(R.id.add_custom_key_button);

        List<SpecialKeyManager.CustomKey> customKeys = specialKeyManager.getCustomKeys();
        // set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        CustomKeysAdapter.StartDragListener startDragListener = viewHolder -> itemTouchHelper.startDrag(viewHolder);

        // Create a holder for the adapter so callbacks can access it
        final CustomKeysAdapter[] adapterHolder = new CustomKeysAdapter[1];

        CustomKeysAdapter adapter = new CustomKeysAdapter(context, recyclerView, customKeys,
                new CustomKeysAdapter.Callbacks() {

                    @Override
                    public void onClick(SpecialKeyManager.CustomKey key, int position) {
                        showEditDeleteCustomKeyDialog(key, adapterHolder[0]);
                    }

                    @Override
                    public void onListReordered(List<SpecialKeyManager.CustomKey> newList) {
                        specialKeyManager.saveCustomKeys(newList);
                        if (dataChangeListener != null) dataChangeListener.onCustomKeyDataChanged();
                    }
                },
                startDragListener
        );

        // Assign it to the holder after initialization
        adapterHolder[0] = adapter;
        recyclerView.setAdapter(adapter);

        addCustomKeyButton.setOnClickListener(v -> showAddEditCustomKeyDialog(null, adapterHolder[0]));

        builder.setNegativeButton(R.string.button_done, (dialog, which) -> {
            dialog.dismiss();
            if (dataChangeListener != null) dataChangeListener.onCustomKeyDataChanged();
        });

        builder.setNeutralButton(R.string.button_reset, (dialog, which) -> {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_confirm_reset_title)
                    .setMessage(R.string.dialog_confirm_reset_message)
                    .setPositiveButton(R.string.button_reset_all, (confirmDialog, confirmWhich) -> {
                        specialKeyManager.resetToDefaults();
                        adapter.updateList(specialKeyManager.getCustomKeys());
                        if (dataChangeListener != null) {
                            dataChangeListener.onCustomKeyDataChanged();
                        }
                        Toast.makeText(context, R.string.toast_custom_keys_reset, Toast.LENGTH_SHORT).show();
                        confirmDialog.dismiss();
                    })
                    .setNegativeButton(R.string.button_cancel, (confirmDialog, confirmWhich) -> confirmDialog.cancel())
                    .show();
        });

        currentListDialog = builder.create();

        // Create ItemTouchHelper (must be after adapter/recyclerView set)
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // no-op
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // false so drag only starts when user touches handle
                return false;
            }
        });

        // attach to recycler
        itemTouchHelper.attachToRecyclerView(recyclerView);

        currentListDialog.show();
    }

    private void showAddEditCustomKeyDialog(final SpecialKeyManager.CustomKey customKeyToEdit, CustomKeysAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final boolean isEditMode = (customKeyToEdit != null);

        builder.setTitle(isEditMode ? R.string.dialog_edit_custom_key_title : R.string.dialog_add_custom_key_title);

        LinearLayout parentLayout = new LinearLayout(context);
        parentLayout.setOrientation(LinearLayout.VERTICAL);
        parentLayout.setPadding(45, 30, 45, 0);

        EditText labelInput = new EditText(context);
        labelInput.setHint(R.string.hint_enter_label);
        if (isEditMode) {
            labelInput.setText(customKeyToEdit.label);
        }
        parentLayout.addView(labelInput);

        TextView selectedKeysDisplayTextView = new TextView(context);
        List<Short> currentSelectedKeys = new ArrayList<>();
        if (isEditMode) {
            for (short key : customKeyToEdit.keys) {
                currentSelectedKeys.add(key);
            }
        }
        selectedKeysDisplayTextView.setText(context.getString(R.string.text_selected_keys_format, keysToString(convertListToShortArray(currentSelectedKeys))));
        selectedKeysDisplayTextView.setPadding(10, 16, 10, 16);
        parentLayout.addView(selectedKeysDisplayTextView);

        Button selectKeysButton = new Button(context);
        selectKeysButton.setText(R.string.button_select_keys);
        parentLayout.addView(selectKeysButton);

        builder.setView(parentLayout);

        OnKeysSelectedListener keySelectionListener = selectedKeys -> {
            currentSelectedKeys.clear();
            for (short key : selectedKeys) {
                currentSelectedKeys.add(key);
            }
            selectedKeysDisplayTextView.setText(context.getString(R.string.text_selected_keys_format, keysToString(convertListToShortArray(currentSelectedKeys))));
        };

        selectKeysButton.setOnClickListener(v -> {
            showKeyboardSelectionDialog(keySelectionListener, convertListToShortArray(currentSelectedKeys));
        });

        builder.setPositiveButton(isEditMode ? R.string.button_save : R.string.button_add, null);
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());

        AlertDialog labelDialog = builder.create();
        labelDialog.show();

        Window window = labelDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(layoutParams);
        }

        Button upsertButton = labelDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        upsertButton.setOnClickListener(v -> {
            String newLabel = labelInput.getText().toString().trim();

            if (newLabel.isEmpty()) {
                Toast.makeText(context, R.string.toast_label_cannot_be_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentSelectedKeys.isEmpty()) {
                Toast.makeText(context, R.string.toast_no_keys_selected, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEditMode) {
                specialKeyManager.removeCustomKey(customKeyToEdit); // Remove old entry
                specialKeyManager.addCustomKey(new SpecialKeyManager.CustomKey(newLabel, convertListToShortArray(currentSelectedKeys)));
                Toast.makeText(context, R.string.toast_custom_key_updated, Toast.LENGTH_SHORT).show();
            } else {
                specialKeyManager.addCustomKey(new SpecialKeyManager.CustomKey(newLabel, convertListToShortArray(currentSelectedKeys)));
                Toast.makeText(context, R.string.toast_custom_key_added, Toast.LENGTH_SHORT).show();
            }

            adapter.updateList(specialKeyManager.getCustomKeys());
            if (dataChangeListener != null) {
                dataChangeListener.onCustomKeyDataChanged();
            }
            labelDialog.dismiss();
        });
    }

    public void showKeyboardSelectionDialog(final OnKeysSelectedListener listener, short[] initialSelectedKeys) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_select_keys_title);

        LinearLayout keyboardDialogLayout = new LinearLayout(context);
        keyboardDialogLayout.setOrientation(LinearLayout.VERTICAL);
        keyboardDialogLayout.setPadding(45, 30, 45, 0);

        TextView selectedKeysDisplayTextView = new TextView(context);
        selectedKeysDisplayTextView.setText(context.getString(R.string.text_selected_keys_format, keysToString(initialSelectedKeys)));
        selectedKeysDisplayTextView.setPadding(0, 0, 0, 16);
        keyboardDialogLayout.addView(selectedKeysDisplayTextView);

        View keyboardLayoutView = LayoutInflater.from(context).inflate(R.layout.layout_axixi_keyboard, null);

        int keyboardHeightDp = 150;
        int keyboardHeightPx = (int) (keyboardHeightDp * context.getResources().getDisplayMetrics().density + 0.5f);

        LinearLayout.LayoutParams keyboardLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                keyboardHeightPx
        );
        keyboardDialogLayout.addView(keyboardLayoutView, keyboardLayoutParams);

        builder.setView(keyboardDialogLayout);

        List<Short> selectedKeysInKeyboardDialog = new ArrayList<>();
        if (initialSelectedKeys != null) {
            for (short key : initialSelectedKeys) {
                selectedKeysInKeyboardDialog.add(key);
            }
        }

        recursivelyHighlightSelectedKeys(keyboardLayoutView, selectedKeysInKeyboardDialog);

        recursivelySetClickListener(keyboardLayoutView, (keyButton) -> {
            try {
                String tag = (String) keyButton.getTag();
                if (tag == null || Objects.equals("hide", tag)) {
                    return;
                }

                int androidKeyCode = Integer.parseInt(tag);
                short gfeKeyCode = keyboardTranslator.translate(androidKeyCode, -1);

                if (gfeKeyCode != 0) {
                    if (selectedKeysInKeyboardDialog.contains(gfeKeyCode)) {
                        selectedKeysInKeyboardDialog.remove(Short.valueOf(gfeKeyCode));
                        keyButton.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                    } else {
                        selectedKeysInKeyboardDialog.add(gfeKeyCode);
                        keyButton.setBackgroundColor(context.getResources().getColor(android.R.color.holo_blue_light));
                    }
                    selectedKeysDisplayTextView.setText(context.getString(R.string.text_selected_keys_format, keysToString(convertListToShortArray(selectedKeysInKeyboardDialog))));
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_key_not_translatable_format, keyButton.getText().toString()), Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(context, context.getString(R.string.toast_invalid_key_code_tag_format, keyButton.getTag()), Toast.LENGTH_SHORT).show();
            }
        }, selectedKeysInKeyboardDialog);

        builder.setPositiveButton(R.string.button_done, null);
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());

        AlertDialog keyboardDialog = builder.create();
        keyboardDialog.show();

        Window window = keyboardDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM;
            window.setAttributes(layoutParams);
        }

        Button doneButton = keyboardDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        doneButton.setOnClickListener(v -> {
            listener.onKeysSelected(convertListToShortArray(selectedKeysInKeyboardDialog));
            keyboardDialog.dismiss();
        });
    }

    private void showEditDeleteCustomKeyDialog(final SpecialKeyManager.CustomKey customKey, CustomKeysAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_edit_delete_custom_key_title);
        builder.setMessage(context.getString(R.string.dialog_edit_delete_custom_key_message, customKey.label));

        builder.setPositiveButton(R.string.button_edit, (dialog, which) -> {
            // Call the consolidated method for editing
            showAddEditCustomKeyDialog(customKey, adapter);
        });
        builder.setNegativeButton(R.string.button_delete, (dialog, which) -> {
            specialKeyManager.removeCustomKey(customKey);
            adapter.updateList(specialKeyManager.getCustomKeys());
            if (dataChangeListener != null) {
                dataChangeListener.onCustomKeyDataChanged();
            }
            Toast.makeText(context, R.string.toast_custom_key_deleted, Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private short[] convertListToShortArray(List<Short> list) {
        short[] array = new short[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private String keysToString(short[] keys) {
        StringBuilder sb = new StringBuilder();
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                short baseKeyCode = (short) (keys[i] & 0x7FFF);

                String keyName = KEY_NAMES.get(baseKeyCode);
                if (keyName != null) {
                    sb.append(keyName);
                } else {
                    sb.append(String.format("0x%02X", keys[i]));
                }
                if (i < keys.length - 1) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    private interface KeyButtonClickListener {
        void onClick(TextView keyButton);
    }

    private void recursivelySetClickListener(View view, KeyButtonClickListener listener, List<Short> selectedKeys) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                recursivelySetClickListener(viewGroup.getChildAt(i), listener, selectedKeys);
            }
        } else if (view instanceof TextView) {
            TextView keyButton = (TextView) view;
            keyButton.setOnClickListener(v -> listener.onClick(keyButton));
        }
    }

    private void recursivelyHighlightSelectedKeys(View view, List<Short> selectedKeys) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                recursivelyHighlightSelectedKeys(viewGroup.getChildAt(i), selectedKeys);
            }
        } else if (view instanceof TextView) {
            TextView keyButton = (TextView) view;
            String tag = (String) keyButton.getTag();
            if (tag != null && !Objects.equals("hide", tag)) {
                try {
                    int androidKeyCode = Integer.parseInt(tag);
                    short gfeKeyCode = keyboardTranslator.translate(androidKeyCode, -1);
                    if (selectedKeys.contains(gfeKeyCode)) {
                        // Using a deprecated method, consider ContextCompat.getColor(context, android.R.color.holo_blue_light) for API 23+
                        keyButton.setBackgroundColor(context.getResources().getColor(android.R.color.holo_blue_light));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}