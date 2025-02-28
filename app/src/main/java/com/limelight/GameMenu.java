
package com.limelight;

import android.app.AlertDialog;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

public class GameMenu {

    private static final long TEST_GAME_FOCUS_DELAY = 10;
    private static final long KEY_UP_DELAY = 25;

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final NvConnection conn;
    private final GameInputDevice device;

    public GameMenu(Game game, NvConnection conn, GameInputDevice device) {
        this.game = game;
        this.conn = conn;
        this.device = device;

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

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
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
            if (sidebar == null) {
                Log.e("GameMenu", "Sidebar view is null. Ensure it's in the activity layout.");
                return;
            }

            TextView disconnectText = sidebar.findViewById(R.id.disconnect_text);
            if (disconnectText != null) {
                disconnectText.setOnClickListener(v -> game.disconnect());
            } else {
                Log.e("GameMenu", "Disconnect button not found.");
            }

            ListView listView = sidebar.findViewById(R.id.menu_list);
            if (listView == null) {
                Log.e("GameMenu", "ListView not found in sidebar.");
                return;
            }
            List<String> menuLabels = new ArrayList<>();
            if (isSubMenu) {
                menuLabels.add("← Back"); // Add a "Back" option for submenus
            }
            for (MenuOption option : options) {
                menuLabels.add(option.label);
            }

            // Use custom layout for ListView items
            ArrayAdapter<String> adapter = new ArrayAdapter<>(game, R.layout.game_menu_item, R.id.menu_item_text, menuLabels);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                if (isSubMenu && position == 0) {
                    // If "Back" is clicked, go back to main menu
                    showMenu();
                    return;
                }

                int realPosition = isSubMenu ? position - 1 : position;
                run(options[realPosition]);
            });

            sidebar.setVisibility(View.VISIBLE);
        });
    }



    private void showSpecialKeysMenu() {
        showSidebarMenu(getString(R.string.game_menu_send_keys), new MenuOption[]{
                new MenuOption(getString(R.string.game_menu_send_keys_esc),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})),
                new MenuOption(getString(R.string.game_menu_send_keys_f11),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})),
                new MenuOption(getString(R.string.game_menu_send_keys_f11_alt),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_MENU, KeyboardTranslator.VK_RETURN})),
                new MenuOption(getString(R.string.game_menu_send_keys_win),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})),
                new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})),
                new MenuOption(getString(R.string.game_menu_send_keys_insert),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_INSERT})),
                new MenuOption(getString(R.string.game_menu_send_move_window),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_RIGHT}))
        }, true); // true to indicate this is a submenu
    }


    private void showMenu() {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, () -> game.toggleKeyboard()));

        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }

        options.add(new MenuOption(getString(R.string.game_menu_toggle_performance_overlay), () -> game.togglePerformanceOverlay()));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> showSpecialKeysMenu()));
//        options.add(new MenuOption(getString(R.string.game_menu_disconnect), () -> game.disconnect()));

        showSidebarMenu("Game Menu", options.toArray(new MenuOption[0]), false); // Pass 'false' since this is not a submenu
    }

}
