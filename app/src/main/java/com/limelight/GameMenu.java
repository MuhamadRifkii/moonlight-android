package com.limelight;

import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.special_keys.CustomSpecialKeyDataChangeListener;
import com.limelight.binding.input.special_keys.SpecialKeyDialogManager;
import com.limelight.binding.input.special_keys.SpecialKeyManager;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

public class GameMenu implements CustomSpecialKeyDataChangeListener {

    private static final long TEST_GAME_FOCUS_DELAY = 10;
    private static final long KEY_UP_DELAY = 25;

    public static class MenuOption {
        public final String label;
        public final boolean withGameFocus;
        public final Runnable runnable;
        public final Runnable longPressRunnable; // new field

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable, null);
        }

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this(label, withGameFocus, runnable, null);
        }

        public MenuOption(String label, boolean withGameFocus, Runnable runnable, Runnable longPressRunnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
            this.longPressRunnable = longPressRunnable;
        }
    }

    private final Game game;
    private final NvConnection conn;
    private final GameInputDevice device;
    private final SpecialKeyManager specialKeyManager;
    private final SpecialKeyDialogManager specialKeyDialogManager;

    public GameMenu(Game game, NvConnection conn, GameInputDevice device) {
        this.game = game;
        this.conn = conn;
        this.device = device;
        this.specialKeyManager = new SpecialKeyManager(game);
        this.specialKeyDialogManager = new SpecialKeyDialogManager(game, specialKeyManager, this);

        showMenu();
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    private static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN:
                return KeyboardPacket.MODIFIER_META;

            default:
                return 0;
        }
    }

    private void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {
            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];
                modifier[0] &= (byte) ~getModifier(key);
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private void runWithGameFocus(Runnable runnable) {
        if (game.isFinishing()) {
            return;
        }
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showSidebarMenu(String title, MenuOption[] options, boolean isSubMenu) {
        game.runOnUiThread(() -> {
            View sidebar = game.findViewById(R.id.game_menu_sidebar);

//            View settingsButton = sidebar.findViewById(R.id.menu_settings_button);
//            if (settingsButton != null) {
//                settingsButton.setOnClickListener(v -> {
//                    game.openStreamSettings();
//                });
//            }

            TextView disconnect = sidebar.findViewById(R.id.disconnect_text);
            disconnect.setOnClickListener(v -> game.disconnect());

            ListView listView = sidebar.findViewById(R.id.menu_list);
            List<String> menuLabels = new ArrayList<>();
            if (isSubMenu) {
                menuLabels.add("← Back");
            }
            for (MenuOption option : options) {
                menuLabels.add(option.label);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(game, R.layout.game_menu_item, R.id.menu_item_text, menuLabels);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                if (isSubMenu && position == 0) {
                    showMenu();
                    return;
                }

                int realPosition = isSubMenu ? position - 1 : position;
                run(options[realPosition]);
            });

            // Handle long-press events
            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                int realPosition = isSubMenu ? position - 1 : position;
                if (realPosition >= 0 && realPosition < options.length) {
                    MenuOption opt = options[realPosition];
                    if (opt.longPressRunnable != null) {
                        opt.longPressRunnable.run();
                        return true;
                    }
                }
                return false;
            });

            sidebar.setVisibility(View.VISIBLE);
        });
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> specialKeyOptions = new ArrayList<>();

        List<SpecialKeyManager.CustomKey> allKeys = specialKeyManager.getCustomKeys();
        for (SpecialKeyManager.CustomKey customKey : allKeys) {
            specialKeyOptions.add(new MenuOption(customKey.label,
                    () -> sendKeys(customKey.keys)));
        }

        specialKeyOptions.add(new MenuOption(getString(R.string.game_menu_manage_custom_keys),
                specialKeyDialogManager::showManageCustomKeysDialog));

        showSidebarMenu(getString(R.string.game_menu_send_keys),
                specialKeyOptions.toArray(new MenuOption[0]), true);
    }

    private void showMenu() {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, game::toggleKeyboard));

        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }

        options.add(new MenuOption(getString(R.string.game_menu_send_keys), this::showSpecialKeysMenu));

        List<MenuOption> configOptions = new ArrayList<>();
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_performance_overlay), game::togglePerformanceOverlay));
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_controller), game::toggleVirtualController));
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keys), game::toggleVirtualKeys));
        configOptions.add(new MenuOption(
                getString(R.string.game_menu_toggle_floating_button),
                true,
                game::toggleFloatingButtonVisibility
        ));
        configOptions.add(new MenuOption(
                getString(R.string.game_menu_toggle_floating_menu_button),
                true,
                game::toggleMenuButtonVisibility
        ));
        configOptions.add(new MenuOption(getString(R.string.game_menu_configure_osc),
                game::toggleOscEditor));

        options.add(new MenuOption(getString(R.string.game_menu_config), () -> showSidebarMenu(getString(R.string.game_menu_config), configOptions.toArray(new MenuOption[0]), true)));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_trackpad), game::changeMouseMode));

        showSidebarMenu(getString(R.string.game_menu_config), options.toArray(new MenuOption[0]), false);
    }

    @Override
    public void onCustomKeyDataChanged() {
        showSpecialKeysMenu();
    }
}