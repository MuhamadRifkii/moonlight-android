/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

import com.limelight.R;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class VirtualControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "OSC";

    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static DigitalPad createDigitalPad(
            final VirtualController controller,
            final Context context) {

        DigitalPad digitalPad = new DigitalPad(controller, context);
        digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    inputContext.inputMap |= ControllerPacket.UP_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                }

                controller.sendControllerInputContext();
            }
        });

        return digitalPad;
    }

    private static DigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

        return button;
    }

    private static DigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setUseRoundedRect(true);
        return button;
    }

    private static DigitalButton createRightTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        RightTrigger button = new RightTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setUseRoundedRect(true);
        return button;
    }

    private static VirtualControllerElement createLeftStick(
            final VirtualController controller,
            final Context context,
            final PreferenceConfiguration config) {
        if (config.enableFreeAnalogStick) {
            return new LeftAnalogStickFree(controller, context);
        }
        return new LeftAnalogStick(controller, context);
    }

    private static VirtualControllerElement createRightStick(
            final VirtualController controller,
            final Context context,
            final PreferenceConfiguration config) {
        if (config.enableFreeAnalogStick) {
            return new RightAnalogStickFree(controller, context);
        }
        return new RightAnalogStick(controller, context);
    }

    private static final int TRIGGER_L_BASE_X = 14;
    private static final int TRIGGER_R_BASE_X = 100;
    private static final int TRIGGER_Y_TOP = 4;
    private static final int TRIGGER_Y_BOTTOM = 14;
    private static final int TRIGGER_WIDTH = 14;
    private static final int TRIGGER_HEIGHT = 8;

    // Face buttons are defined based on the Y button (button number 9)
    private static final int BUTTON_BASE_X = 104;
    private static final int BUTTON_BASE_Y = 32;
    private static final int BUTTON_SIZE = 10;

    private static final int DPAD_BASE_X = 13;
    private static final int DPAD_BASE_Y = 28;
    private static final int DPAD_SIZE = 22;

    private static final int ANALOG_L_BASE_X = 32;
    private static final int ANALOG_L_BASE_Y = 54;
    private static final int ANALOG_R_BASE_X = 82;
    private static final int ANALOG_R_BASE_Y = 54;
    private static final int ANALOG_SIZE = 18;

    private static final int L3_BASE_X = 54;
    private static final int R3_BASE_X = 72;
    private static final int L3_R3_BASE_Y = 62;
    private static final int L3_R3_SIZE = 10;

    private static final int BACK_X = 70;
    private static final int START_X = 54;
    private static final int START_BACK_Y = 66;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 6;

    /**
     * Returns a human-readable name for the given element ID.
     */
    public static String getElementName(Context context, int eid) {
        switch (eid) {
            case VirtualControllerElement.EID_DPAD:
                return context.getString(R.string.osc_element_name_dpad);
            case VirtualControllerElement.EID_LT:
                return "LT";
            case VirtualControllerElement.EID_RT:
                return "RT";
            case VirtualControllerElement.EID_LB:
                return "LB";
            case VirtualControllerElement.EID_RB:
                return "RB";
            case VirtualControllerElement.EID_A:
                return "A";
            case VirtualControllerElement.EID_B:
                return "B";
            case VirtualControllerElement.EID_X:
                return "X";
            case VirtualControllerElement.EID_Y:
                return "Y";
            case VirtualControllerElement.EID_BACK:
                return context.getString(R.string.osc_element_name_back);
            case VirtualControllerElement.EID_START:
                return context.getString(R.string.osc_element_name_start);
            case VirtualControllerElement.EID_LS:
                return context.getString(R.string.osc_element_name_ls);
            case VirtualControllerElement.EID_RS:
                return context.getString(R.string.osc_element_name_rs);
            case VirtualControllerElement.EID_LSB:
                return "L3";
            case VirtualControllerElement.EID_RSB:
                return "R3";
            default:
                return "?";
        }
    }

    /**
     * Creates an unattached element of the given type. Returns null for unknown IDs.
     */
    public static VirtualControllerElement createElement(
            final int eid,
            final VirtualController controller,
            final Context context) {
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        switch (eid) {
            case VirtualControllerElement.EID_DPAD:
                return createDigitalPad(controller, context);
            case VirtualControllerElement.EID_LT:
                return createLeftTrigger(1, "LT", -1, controller, context);
            case VirtualControllerElement.EID_RT:
                return createRightTrigger(1, "RT", -1, controller, context);
            case VirtualControllerElement.EID_LB: {
                DigitalButton button = createDigitalButton(
                        eid, ControllerPacket.LB_FLAG, 0, 1, "LB", -1, controller, context);
                button.setUseRoundedRect(true);
                return button;
            }
            case VirtualControllerElement.EID_RB: {
                DigitalButton button = createDigitalButton(
                        eid, ControllerPacket.RB_FLAG, 0, 1, "RB", -1, controller, context);
                button.setUseRoundedRect(true);
                return button;
            }
            case VirtualControllerElement.EID_A:
                return createDigitalButton(
                        eid,
                        !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG,
                        0, 1,
                        !config.flipFaceButtons ? "A" : "B",
                        -1, controller, context);
            case VirtualControllerElement.EID_B:
                return createDigitalButton(
                        eid,
                        config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG,
                        0, 1,
                        config.flipFaceButtons ? "A" : "B",
                        -1, controller, context);
            case VirtualControllerElement.EID_X:
                return createDigitalButton(
                        eid,
                        !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG,
                        0, 1,
                        !config.flipFaceButtons ? "X" : "Y",
                        -1, controller, context);
            case VirtualControllerElement.EID_Y:
                return createDigitalButton(
                        eid,
                        config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG,
                        0, 1,
                        config.flipFaceButtons ? "X" : "Y",
                        -1, controller, context);
            case VirtualControllerElement.EID_BACK: {
                DigitalButton button = createDigitalButton(
                        eid, ControllerPacket.BACK_FLAG, 0, 2, "BACK", -1, controller, context);
                button.setUseRoundedRect(true);
                return button;
            }
            case VirtualControllerElement.EID_START: {
                DigitalButton button = createDigitalButton(
                        eid, ControllerPacket.PLAY_FLAG, 0, 3, "START", -1, controller, context);
                button.setUseRoundedRect(true);
                return button;
            }
            case VirtualControllerElement.EID_LS:
                return createLeftStick(controller, context, config);
            case VirtualControllerElement.EID_RS:
                return createRightStick(controller, context, config);
            case VirtualControllerElement.EID_LSB:
                return createDigitalButton(
                        eid, ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", -1, controller, context);
            case VirtualControllerElement.EID_RSB:
                return createDigitalButton(
                        eid, ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", -1, controller, context);
            default:
                return null;
        }
    }

    /**
     * Returns the default geometry for the given element ID as {x, y, width, height}.
     */
    public static int[] getDefaultGeometry(Context context, int eid) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;

        switch (eid) {
            case VirtualControllerElement.EID_DPAD:
                return new int[]{
                        screenScale(DPAD_BASE_X, height),
                        screenScale(DPAD_BASE_Y, height),
                        screenScale(DPAD_SIZE, height),
                        screenScale(DPAD_SIZE, height)
                };
            case VirtualControllerElement.EID_LT:
                return new int[]{
                        screenScale(TRIGGER_L_BASE_X, height),
                        screenScale(TRIGGER_Y_TOP, height),
                        screenScale(TRIGGER_WIDTH, height),
                        screenScale(TRIGGER_HEIGHT, height)
                };
            case VirtualControllerElement.EID_RT:
                return new int[]{
                        screenScale(TRIGGER_R_BASE_X, height) + rightDisplacement,
                        screenScale(TRIGGER_Y_TOP, height),
                        screenScale(TRIGGER_WIDTH, height),
                        screenScale(TRIGGER_HEIGHT, height)
                };
            case VirtualControllerElement.EID_LB:
                return new int[]{
                        screenScale(TRIGGER_L_BASE_X, height),
                        screenScale(TRIGGER_Y_BOTTOM, height),
                        screenScale(TRIGGER_WIDTH, height),
                        screenScale(TRIGGER_HEIGHT, height)
                };
            case VirtualControllerElement.EID_RB:
                return new int[]{
                        screenScale(TRIGGER_R_BASE_X, height) + rightDisplacement,
                        screenScale(TRIGGER_Y_BOTTOM, height),
                        screenScale(TRIGGER_WIDTH, height),
                        screenScale(TRIGGER_HEIGHT, height)
                };
            case VirtualControllerElement.EID_A:
                return new int[]{
                        screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                        screenScale(BUTTON_BASE_Y + 2 * BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height)
                };
            case VirtualControllerElement.EID_B:
                return new int[]{
                        screenScale(BUTTON_BASE_X + BUTTON_SIZE, height) + rightDisplacement,
                        screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height)
                };
            case VirtualControllerElement.EID_X:
                return new int[]{
                        screenScale(BUTTON_BASE_X - BUTTON_SIZE, height) + rightDisplacement,
                        screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height)
                };
            case VirtualControllerElement.EID_Y:
                return new int[]{
                        screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                        screenScale(BUTTON_BASE_Y, height),
                        screenScale(BUTTON_SIZE, height),
                        screenScale(BUTTON_SIZE, height)
                };
            case VirtualControllerElement.EID_BACK:
                return new int[]{
                        screenScale(BACK_X, height),
                        screenScale(START_BACK_Y, height),
                        screenScale(START_BACK_WIDTH, height),
                        screenScale(START_BACK_HEIGHT, height)
                };
            case VirtualControllerElement.EID_START:
                return new int[]{
                        screenScale(START_X, height) + rightDisplacement,
                        screenScale(START_BACK_Y, height),
                        screenScale(START_BACK_WIDTH, height),
                        screenScale(START_BACK_HEIGHT, height)
                };
            case VirtualControllerElement.EID_LS:
                return new int[]{
                        screenScale(ANALOG_L_BASE_X, height),
                        screenScale(ANALOG_L_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                };
            case VirtualControllerElement.EID_RS:
                return new int[]{
                        screenScale(ANALOG_R_BASE_X, height) + rightDisplacement,
                        screenScale(ANALOG_R_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                };
            case VirtualControllerElement.EID_LSB:
                return new int[]{
                        screenScale(L3_BASE_X, height),
                        screenScale(L3_R3_BASE_Y, height),
                        screenScale(L3_R3_SIZE, height),
                        screenScale(L3_R3_SIZE, height)
                };
            case VirtualControllerElement.EID_RSB:
                return new int[]{
                        screenScale(R3_BASE_X, height) + rightDisplacement,
                        screenScale(L3_R3_BASE_Y, height),
                        screenScale(L3_R3_SIZE, height),
                        screenScale(L3_R3_SIZE, height)
                };
            default:
                return new int[]{0, 0, 0, 0};
        }
    }

    // Elements included in the default layout, in creation order.
    // L3/R3 depend on the separateL3R3 preference and analog stick types
    // depend on enableFreeAnalogStick, so they're handled separately below.
    private static final int[] DEFAULT_ELEMENTS = {
            VirtualControllerElement.EID_DPAD,
            VirtualControllerElement.EID_A,
            VirtualControllerElement.EID_B,
            VirtualControllerElement.EID_X,
            VirtualControllerElement.EID_Y,
            VirtualControllerElement.EID_LT,
            VirtualControllerElement.EID_LB,
            VirtualControllerElement.EID_RT,
            VirtualControllerElement.EID_RB,
            VirtualControllerElement.EID_LS,
            VirtualControllerElement.EID_RS,
            VirtualControllerElement.EID_BACK,
            VirtualControllerElement.EID_START,
    };

    // All possible element types that can exist in a layout.
    public static final int[] ALL_ELEMENTS = {
            VirtualControllerElement.EID_DPAD,
            VirtualControllerElement.EID_A,
            VirtualControllerElement.EID_B,
            VirtualControllerElement.EID_X,
            VirtualControllerElement.EID_Y,
            VirtualControllerElement.EID_LT,
            VirtualControllerElement.EID_LB,
            VirtualControllerElement.EID_RT,
            VirtualControllerElement.EID_RB,
            VirtualControllerElement.EID_LS,
            VirtualControllerElement.EID_RS,
            VirtualControllerElement.EID_BACK,
            VirtualControllerElement.EID_START,
            VirtualControllerElement.EID_LSB,
            VirtualControllerElement.EID_RSB,
    };

    private static void addElementWithDefaults(
            final VirtualController controller,
            final int eid,
            final Context context) {
        VirtualControllerElement element = createElement(eid, controller, context);
        if (element == null) {
            return;
        }

        int[] geometry = getDefaultGeometry(context, eid);
        controller.addElement(element, geometry[0], geometry[1], geometry[2], geometry[3]);
    }

    public static void createDefaultLayout(final VirtualController controller, final Context context) {

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        for (int eid : DEFAULT_ELEMENTS) {
            addElementWithDefaults(controller, eid, context);
        }

        if (config.separateL3R3) {
            addElementWithDefaults(controller, VirtualControllerElement.EID_LSB, context);
            addElementWithDefaults(controller, VirtualControllerElement.EID_RSB, context);
        }

        controller.setOpacity(config.oscOpacity);
    }

    public static boolean isRenameable(VirtualControllerElement element) {
        return element instanceof DigitalButton;
    }

    public static void markElementDeleted(final Context context, final int eid) {
        SharedPreferences.Editor prefEditor =
                context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE).edit();
        try {
            prefEditor.putString("" + eid, new JSONObject().put("DELETED", true).toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefEditor.apply();
    }

    public static void saveProfile(final VirtualController controller, final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE).edit();

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualController controller, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE);

        List<VirtualControllerElement> removedElements = new ArrayList<>();

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    JSONObject configuration = new JSONObject(jsonConfig);
                    if (configuration.optBoolean("DELETED", false)) {
                        removedElements.add(element);
                        continue;
                    }
                    element.loadConfiguration(configuration);
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }

        for (VirtualControllerElement element : removedElements) {
            controller.removeElement(element);
        }
    }

    public static void clearPreferences(final Context context) {
        context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE).edit().clear().apply();
    }
}
