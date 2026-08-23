package com.limelight.binding.input.virtual_keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;

public class VirtualKeyView extends View {

    public interface OnKeyActionListener {
        void onKeyActionDown(VirtualKeyView view);
        void onKeyActionUp(VirtualKeyView view);
        void onMapRequested(VirtualKeyView view);
        void onDeleted(VirtualKeyView view);
        void onPositionChanged(VirtualKeyView view);
    }

    private String viewId;
    private short[] mappedKeys = new short[0];
    private String label = "UNMAPPED";
    private String customLabel = null;

    private boolean isEditMode = false;
    private Paint paint;
    private Paint textPaint;
    private OnKeyActionListener actionListener;

    private float dX, dY;
    private boolean isDragging = false;
    private boolean isPressed = false;
    private boolean wasScaling = false;

    private static final int DRAG_THRESHOLD = 10;
    private float startX, startY;

    private ScaleGestureDetector scaleDetector;
    private int scaleBaseSize = 0;

    public VirtualKeyView(Context context, String id, OnKeyActionListener listener) {
        super(context);
        this.viewId = id;
        this.actionListener = listener;

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(128, 50, 50, 50)); // Semi-transparent dark gray

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (editMode) {
            paint.setColor(Color.argb(180, 50, 150, 50)); // Green tint in edit mode
        } else {
            paint.setColor(Color.argb(128, 50, 50, 50));
        }
        invalidate();
    }

    public void setMappedKeys(short[] keys, String newLabel) {
        this.mappedKeys = keys;
        if (newLabel != null && !newLabel.isEmpty()) {
            this.customLabel = newLabel;
        }
        refreshLabel();
        invalidate();
    }

    public void setCustomLabel(String text) {
        this.customLabel = (text == null || text.trim().isEmpty()) ? null : text.trim();
        refreshLabel();
        invalidate();
    }

    private void refreshLabel() {
        if (customLabel != null && !customLabel.isEmpty()) {
            this.label = customLabel;
        } else if (mappedKeys == null || mappedKeys.length == 0) {
            this.label = "UNMAPPED";
        } else {
            this.label = generateLabel(mappedKeys);
        }
    }

    private String generateLabel(short[] keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            short baseKeyCode = (short) (keys[i] & 0x7FFF);
            String name = KeyboardTranslator.KEY_NAMES.get(baseKeyCode);
            if (name != null) {
                sb.append(name);
            } else {
                sb.append("KEY");
            }
            if (i < keys.length - 1) sb.append("+");
        }
        return sb.toString();
    }

    public short[] getMappedKeys() {
        return mappedKeys;
    }

    public String getLabel() {
        return label;
    }

    public String getViewId() {
        return viewId;
    }

    public int getElementSize() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        return params != null ? params.width : 0;
    }

    public void setElementSize(int newSize) {
        applySize(clampSize(newSize));
        notifyPositionChanged();
    }

    private int clampSize(int size) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int min = (int) (screenHeight * VirtualKeyManager.MIN_SIZE_FRACTION);
        int max = (int) (screenHeight * VirtualKeyManager.MAX_SIZE_FRACTION);
        return Math.max(min, Math.min(max, size));
    }

    private void applySize(int newSize) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params == null) {
            return;
        }

        int centerX = params.leftMargin + params.width / 2;
        int centerY = params.topMargin + params.height / 2;

        params.width = newSize;
        params.height = newSize;
        params.leftMargin = centerX - newSize / 2;
        params.topMargin = centerY - newSize / 2;

        View parent = (View) getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            params.leftMargin = Math.max(0, Math.min(params.leftMargin, parent.getWidth() - newSize));
            params.topMargin = Math.max(0, Math.min(params.topMargin, parent.getHeight() - newSize));
        }

        setLayoutParams(params);
    }

    private void notifyPositionChanged() {
        if (actionListener != null) {
            actionListener.onPositionChanged(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int radius = Math.min(getWidth(), getHeight()) / 2;

        if (isPressed && !isEditMode) {
            paint.setColor(Color.argb(180, 100, 100, 100)); // lighter when pressed
        } else if (!isEditMode) {
            paint.setColor(Color.argb(128, 50, 50, 50));
        }

        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);

        // Adjust text size based on button size
        textPaint.setTextSize(getHeight() * 0.25f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = (getHeight() / 2f) - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, getWidth() / 2f, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEditMode) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    isPressed = true;
                    invalidate();
                    if (actionListener != null) actionListener.onKeyActionDown(this);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isPressed = false;
                    invalidate();
                    if (actionListener != null) actionListener.onKeyActionUp(this);
                    return true;
            }
            return super.onTouchEvent(event);
        } else {
            // Edit Mode touch handling
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = getX() - event.getRawX();
                    dY = getY() - event.getRawY();
                    startX = event.getRawX();
                    startY = event.getRawY();
                    isDragging = false;
                    wasScaling = false;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // A second finger went down; pause dragging while scaling
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (scaleDetector.isInProgress()) {
                        wasScaling = true;
                        return true;
                    }

                    if (event.getPointerCount() == 1 &&
                            (Math.abs(event.getRawX() - startX) > DRAG_THRESHOLD ||
                             Math.abs(event.getRawY() - startY) > DRAG_THRESHOLD)) {
                        isDragging = true;
                    }

                    if (isDragging && event.getPointerCount() == 1) {
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        // Keep within bounds
                        View parent = (View) getParent();
                        if (parent != null) {
                            newX = Math.max(0, Math.min(newX, parent.getWidth() - getWidth()));
                            newY = Math.max(0, Math.min(newY, parent.getHeight() - getHeight()));
                        }

                        setX(newX);
                        setY(newY);

                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
                        params.leftMargin = (int) newX;
                        params.topMargin = (int) newY;
                        setLayoutParams(params);
                    }
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    // Stop dragging so the remaining finger doesn't cause a jump
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    wasScaling = false;
                    return true;

                case MotionEvent.ACTION_UP:
                    if (isDragging || wasScaling) {
                        notifyPositionChanged();
                    } else if (!wasScaling) {
                        showEditMenu();
                    }
                    isDragging = false;
                    wasScaling = false;
                    return true;
            }
        }
        return false;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            scaleBaseSize = getElementSize();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (scaleBaseSize > 0) {
                applySize(clampSize(Math.round(scaleBaseSize * detector.getScaleFactor())));
            }
            return true;
        }
    }

    private void showEditMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.osc_edit_virtual_key_title);

        CharSequence[] options = {
                getContext().getString(R.string.osc_edit_map_keys),
                getContext().getString(R.string.osc_edit_rename),
                getContext().getString(R.string.osc_edit_resize),
                getContext().getString(R.string.osc_edit_duplicate),
                getContext().getString(R.string.osc_edit_delete)
        };
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    if (actionListener != null) actionListener.onMapRequested(this);
                    break;
                case 1:
                case 2:
                    if (actionListener instanceof VirtualKeyManager) {
                        ((VirtualKeyManager) actionListener).onEditRequested(which == 1, this);
                    }
                    break;
                case 3:
                    if (actionListener instanceof VirtualKeyManager) {
                        ((VirtualKeyManager) actionListener).duplicateKey(this);
                    }
                    break;
                case 4:
                    if (actionListener != null) actionListener.onDeleted(this);
                    break;
            }
        });
        builder.show();
    }
}
