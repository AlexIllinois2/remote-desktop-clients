package com.iiordanov.bVNC.input;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.undatech.opaque.InputCarriable;
import com.undatech.opaque.input.RemoteKeyboard;
import com.undatech.remoteClientUi.R;

/**
 * Semi-transparent round keys that press DPAD_UP / DPAD_DOWN on the remote.
 * The group can be dragged anywhere on the canvas. Showing and hiding it is done from
 * the toolbar, which also carries the rotate button, so there is no side-edge hide strip.
 */
public class FloatingScrollKeys {
    private final static int REPEAT_START_DELAY = 400;
    private final static int REPEAT_INTERVAL = 120;
    // Initial spot: near the right side but clearly away from the edge.
    private final static int SIDE_MARGIN_DP = 20;

    private final FrameLayout container;
    private final InputCarriable remoteInput;
    private final Handler handler;
    private final int touchSlop;
    private final float sideMargin;

    private boolean placedInitially;
    private float freeX, freeY;

    public FloatingScrollKeys(FrameLayout container, InputCarriable remoteInput, Handler handler) {
        this.container = container;
        this.remoteInput = remoteInput;
        this.handler = handler;
        this.touchSlop = ViewConfiguration.get(container.getContext()).getScaledTouchSlop();
        this.sideMargin = SIDE_MARGIN_DP * container.getResources().getDisplayMetrics().density;

        bindTarget(container.findViewById(R.id.scrollKeyUp), () -> pressKey(KeyEvent.KEYCODE_DPAD_UP), true);
        bindTarget(container.findViewById(R.id.scrollKeyDown), () -> pressKey(KeyEvent.KEYCODE_DPAD_DOWN), true);
        // Touches on the gaps still drag the group.
        bindTarget(container, null, false);

        container.post(this::placeInitially);

        View parent = (View) container.getParent();
        final int[] lastSize = {parent.getWidth(), parent.getHeight()};
        parent.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            placeInitially();
            int width = right - left;
            int height = bottom - top;
            if (width == lastSize[0] && height == lastSize[1])
                return;
            // A rotation changes both dimensions, so keep the group at the same point of the
            // area it can travel over instead of clamping an absolute position into the new one.
            if (lastSize[0] > 0 && lastSize[1] > 0 && container.getWidth() > 0) {
                freeX = travelRatio(freeX, lastSize[0], container.getWidth()) * (width - container.getWidth());
                freeY = travelRatio(freeY, lastSize[1], container.getHeight()) * (height - container.getHeight());
            }
            lastSize[0] = width;
            lastSize[1] = height;
            clampIntoView();
            animateTo(freeX, freeY);
        });
    }

    private static float travelRatio(float position, int parentSize, int containerSize) {
        float travel = parentSize - containerSize;
        return travel <= 0 ? 0 : position / travel;
    }

    /**
     * Positions the group near the right side, vertically centred. Called both from a posted
     * message and the first layout callback, since the parent has no size yet at setup time.
     */
    private void placeInitially() {
        View parent = (View) container.getParent();
        if (placedInitially || parent.getWidth() == 0 || container.getWidth() == 0)
            return;
        placedInitially = true;
        moveTo(parent.getWidth() - container.getWidth() - sideMargin,
                (parent.getHeight() - container.getHeight()) / 2f);
        freeX = container.getX();
        freeY = container.getY();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindTarget(View target, Runnable press, boolean repeatable) {
        target.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY, lastRawX, lastRawY;
            private boolean dragging, repeated;

            private final Runnable repeatTask = new Runnable() {
                @Override
                public void run() {
                    repeated = true;
                    press.run();
                    handler.postDelayed(this, REPEAT_INTERVAL);
                }
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = lastRawX = event.getRawX();
                        downRawY = lastRawY = event.getRawY();
                        dragging = false;
                        repeated = false;
                        if (repeatable)
                            handler.postDelayed(repeatTask, REPEAT_START_DELAY);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastRawX;
                        float dy = event.getRawY() - lastRawY;
                        lastRawX = event.getRawX();
                        lastRawY = event.getRawY();
                        if (!dragging && traveledMoreThanSlop()) {
                            dragging = true;
                            handler.removeCallbacks(repeatTask);
                        }
                        if (dragging)
                            moveTo(container.getX() + dx, container.getY() + dy);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(repeatTask);
                        if (dragging) {
                            finishDrag();
                        } else if (press != null && !repeated) {
                            press.run();
                        }
                        return true;
                }
                return false;
            }

            private boolean traveledMoreThanSlop() {
                return Math.abs(lastRawX - downRawX) + Math.abs(lastRawY - downRawY) > touchSlop;
            }
        });
    }

    private void pressKey(int keyCode) {
        RemoteKeyboard keyboard = remoteInput.getKeyboard();
        if (keyboard == null)
            return;
        long now = SystemClock.uptimeMillis();
        keyboard.processLocalKeyEvent(keyCode,
                new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0), 0);
        keyboard.processLocalKeyEvent(keyCode,
                new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0), 0);
    }

    /** A drop simply leaves the group where it was put. */
    private void finishDrag() {
        freeX = container.getX();
        freeY = container.getY();
    }

    private void moveTo(float x, float y) {
        View parent = (View) container.getParent();
        float maxX = Math.max(0, parent.getWidth() - container.getWidth());
        float maxY = Math.max(0, parent.getHeight() - container.getHeight());
        container.setX(Math.max(0, Math.min(x, maxX)));
        container.setY(Math.max(0, Math.min(y, maxY)));
    }

    private void clampIntoView() {
        View parent = (View) container.getParent();
        float maxX = Math.max(0, parent.getWidth() - container.getWidth());
        float maxY = Math.max(0, parent.getHeight() - container.getHeight());
        freeX = Math.max(0, Math.min(freeX, maxX));
        freeY = Math.max(0, Math.min(freeY, maxY));
    }

    private void animateTo(float x, float y) {
        container.animate().x(x).y(y).setDuration(150).start();
    }
}
