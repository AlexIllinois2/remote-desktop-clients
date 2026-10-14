package com.iiordanov.bVNC.input;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.undatech.opaque.InputCarriable;
import com.undatech.opaque.input.RemoteKeyboard;
import com.undatech.remoteClientUi.R;

/**
 * Two semi-transparent round keys that press DPAD_UP / DPAD_DOWN on the remote. The pair can be
 * dropped anywhere on the canvas; only parking it flush against a side edge collapses it into a
 * rounded bar, which a tap or a drag pulls back out.
 */
public class FloatingScrollKeys {
    private final static int REPEAT_START_DELAY = 400;
    private final static int REPEAT_INTERVAL = 120;
    private final static int ANIMATION_DURATION = 150;
    // How close to the side a drop has to be to count as deliberately parking it there.
    private final static int DOCK_THRESHOLD_DP = 10;
    private final static int BAR_STRIP_DP = 18;
    // Initial spot: near the right side but clearly away from the edge.
    private final static int SIDE_MARGIN_DP = 20;

    private final FrameLayout container;
    private final View circles;
    private final View bar;
    private final InputCarriable remoteInput;
    private final Handler handler;
    private final int touchSlop;
    private final float dockThreshold;
    private final float stripWidth;
    private final float sideMargin;

    private boolean docked;
    private boolean dockedLeft;
    private boolean placedInitially;
    private float freeX, freeY;

    public FloatingScrollKeys(FrameLayout container, InputCarriable remoteInput, Handler handler) {
        this.container = container;
        this.remoteInput = remoteInput;
        this.handler = handler;
        this.circles = container.findViewById(R.id.scrollKeyCircles);
        this.bar = container.findViewById(R.id.scrollKeyBar);
        this.touchSlop = ViewConfiguration.get(container.getContext()).getScaledTouchSlop();
        float density = container.getResources().getDisplayMetrics().density;
        this.dockThreshold = DOCK_THRESHOLD_DP * density;
        this.stripWidth = BAR_STRIP_DP * density;
        this.sideMargin = SIDE_MARGIN_DP * density;

        bindTarget(container.findViewById(R.id.scrollKeyUp), KeyEvent.KEYCODE_DPAD_UP);
        bindTarget(container.findViewById(R.id.scrollKeyDown), KeyEvent.KEYCODE_DPAD_DOWN);
        // Touches on the gaps, and on the docked bar, still drag the pair.
        bindTarget(container, -1);

        container.post(this::placeInitially);

        View parent = (View) container.getParent();
        final int[] lastWidth = {parent.getWidth()};
        parent.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            placeInitially();
            if (right - left == lastWidth[0])
                return;
            lastWidth[0] = right - left;
            clampIntoView();
            animateTo(docked ? dockedX() : freeX, freeY);
        });
    }

    /**
     * Positions the pair near the right side, vertically centred. Called both from a posted
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
    private void bindTarget(View target, int keyCode) {
        target.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY, lastRawX, lastRawY;
            private boolean dragging, repeated;

            private final Runnable repeatTask = new Runnable() {
                @Override
                public void run() {
                    repeated = true;
                    pressKey(keyCode);
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
                        if (!docked && keyCode >= 0)
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
                            if (docked)
                                expandInPlace();
                        }
                        if (dragging)
                            moveTo(container.getX() + dx, container.getY() + dy);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(repeatTask);
                        if (dragging) {
                            finishDrag();
                        } else if (docked) {
                            restore();
                        } else if (keyCode >= 0 && !repeated) {
                            pressKey(keyCode);
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

    /** A drop away from the sides leaves the pair where it was put; a flush drop parks it. */
    private void finishDrag() {
        float leftGap = container.getX();
        float rightGap = parentWidth() - (container.getX() + container.getWidth());
        freeX = container.getX();
        freeY = container.getY();
        if (leftGap <= dockThreshold) {
            dock(true);
        } else if (rightGap <= dockThreshold) {
            dock(false);
        }
    }

    private void dock(boolean left) {
        dockedLeft = left;
        docked = true;
        freeX = left ? 0 : parentWidth() - container.getWidth();
        // Docking right leaves the pair's left edge on screen and vice versa, so the bar has to
        // sit on whichever side stays visible.
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        lp.gravity = (left ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        bar.setLayoutParams(lp);
        circles.setVisibility(View.INVISIBLE);
        bar.setVisibility(View.VISIBLE);
        animateTo(dockedX(), freeY);
    }

    /** Slides fully out without moving the pair, so a drag can continue from the finger. */
    private void expandInPlace() {
        docked = false;
        freeX = container.getX();
        freeY = container.getY();
        circles.setVisibility(View.VISIBLE);
        bar.setVisibility(View.GONE);
        container.setX(dockedLeft ? 0 : parentWidth() - container.getWidth());
    }

    private void restore() {
        docked = false;
        circles.setVisibility(View.VISIBLE);
        bar.setVisibility(View.GONE);
        animateTo(freeX, freeY);
    }

    private float dockedX() {
        return dockedLeft ? -container.getWidth() + stripWidth : parentWidth() - stripWidth;
    }

    private float parentWidth() {
        return ((View) container.getParent()).getWidth();
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
        container.animate().x(x).y(y).setDuration(ANIMATION_DURATION).start();
    }
}
