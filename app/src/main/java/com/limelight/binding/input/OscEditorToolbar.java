package com.limelight.binding.input;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Toolbar container for the on-screen controls editor that can be dragged
 * anywhere within its parent once the gesture exceeds the touch slop.
 * Child button clicks are unaffected unless the user starts dragging.
 */
public class OscEditorToolbar extends LinearLayout {

    private float downRawX, downRawY;
    private float startX, startY;
    private boolean dragging = false;

    public OscEditorToolbar(Context context) {
        super(context);
    }

    public OscEditorToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OscEditorToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = getX();
                startY = getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) {
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                        // Begin dragging; children receive an automatic ACTION_CANCEL
                        dragging = true;
                        return true;
                    }
                }
                else {
                    return true;
                }
                break;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    moveToolbar(event.getRawX(), event.getRawY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void moveToolbar(float rawX, float rawY) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }

        float newX = startX + (rawX - downRawX);
        float newY = startY + (rawY - downRawY);

        newX = Math.max(0, Math.min(newX, parent.getWidth() - getWidth()));
        newY = Math.max(0, Math.min(newY, parent.getHeight() - getHeight()));

        setX(newX);
        setY(newY);
    }
}
