/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.snapandswap.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.Transformation;

import com.snapandswap.R;

public class FocusRenderer extends OverlayRenderer
        implements FocusIndicator {

    private static final String TAG = "CAM Pie";

    // Sometimes continuous autofocus starts and stops several times quickly.
    // These states are used to make sure the animation is run for at least some
    // time.
    private volatile int mState;
    private ScaleAnimation mAnimation = new ScaleAnimation();
    private static final int STATE_IDLE = 0;
    private static final int STATE_FOCUSING = 1;
    private static final int STATE_FINISHING = 2;

    private Runnable mDisappear = new Disappear();
    private AnimationListener mEndAction = new EndAction();
    private static final int SCALING_UP_TIME = 600;
    private static final int SCALING_DOWN_TIME = 100;
    private static final int DISAPPEAR_TIMEOUT = 100;
    private static final int DIAL_HORIZONTAL = 157;

    // geometry
    private Point mCenter;
    private int mRadius;

    // touch handling
    private Paint mFocusPaint;
    private int mSuccessColor;
    private int mFailColor;
    private int mCircleSize;
    private int mFocusX;
    private int mFocusY;
    private int mCenterX;
    private int mCenterY;

    private int mDialAngle;
    private RectF mCircle;
    private RectF mDial;
    private Point mPoint1;
    private Point mPoint2;
    private int mStartAnimationAngle;
    private boolean mFocused;
    private int mInnerOffset;
    private int mOuterStroke;
    private int mInnerStroke;
    private boolean mBlockFocus;

    private volatile boolean mFocusCancelled;

    public FocusRenderer(Context context) {
        init(context);
    }

    private void init(Context ctx) {
        setVisible(false);

        Resources res = ctx.getResources();
        mRadius = (int) res.getDimensionPixelSize(R.dimen.pie_radius_start);
        mCircleSize = mRadius - res.getDimensionPixelSize(R.dimen.focus_radius_offset);
        mCenter = new Point(0,0);
        mFocusPaint = new Paint();
        mFocusPaint.setAntiAlias(true);
        mFocusPaint.setColor(Color.WHITE);
        mFocusPaint.setStyle(Paint.Style.STROKE);
        mSuccessColor = Color.GREEN;
        mFailColor = Color.RED;
        mCircle = new RectF();
        mDial = new RectF();
        mPoint1 = new Point();
        mPoint2 = new Point();
        mInnerOffset = res.getDimensionPixelSize(R.dimen.focus_inner_offset);
        mOuterStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
        mInnerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
        mState = STATE_IDLE;
        mBlockFocus = false;

    }

    public void setCenter(int x, int y) {
        mCenter.x = x;
        mCenter.y = y;
        // when using the pie menu, align the focus ring
        alignFocus(x, y);
    }



    @Override
    public void onDraw(Canvas canvas) {
        int state = canvas.save();
        drawFocus(canvas);
        if (mState == STATE_FINISHING) {
            canvas.restoreToCount(state);
            return;
        }

        canvas.restoreToCount(state);
    }


    @Override
    public boolean handlesTouch() {
        return true;
    }

    // focus specific code

    public void setBlockFocus(boolean blocked) {
        mBlockFocus = blocked;
        if (blocked) {
            clear();
        }
    }

    public void setFocus(int x, int y) {
        mFocusX = x;
        mFocusY = y;
        setCircle(mFocusX, mFocusY);
    }

    public void alignFocus(int x, int y) {
        mOverlay.removeCallbacks(mDisappear);
        mAnimation.cancel();
        mAnimation.reset();
        mFocusX = x;
        mFocusY = y;
        mDialAngle = DIAL_HORIZONTAL;
        setCircle(x, y);
        mFocused = false;
    }

    public int getSize() {
        return 2 * mCircleSize;
    }

    private int getRandomRange() {
        return (int)(-60 + 120 * Math.random());
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
        mFocusX = mCenterX;
        mFocusY = mCenterY;
        setCircle(mFocusX, mFocusY);
        if (isVisible()) {
            setCenter(mCenterX, mCenterY);
        }
    }

    private void setCircle(int cx, int cy) {
        mCircle.set(cx - mCircleSize, cy - mCircleSize,
                cx + mCircleSize, cy + mCircleSize);
        mDial.set(cx - mCircleSize + mInnerOffset, cy - mCircleSize + mInnerOffset,
                cx + mCircleSize - mInnerOffset, cy + mCircleSize - mInnerOffset);
    }

    public void drawFocus(Canvas canvas) {
        if (mBlockFocus) return;
        mFocusPaint.setStrokeWidth(mOuterStroke);
        canvas.drawCircle((float) mFocusX, (float) mFocusY, (float) mCircleSize, mFocusPaint);

        int color = mFocusPaint.getColor();

        if (mState == STATE_FINISHING) {
            mFocusPaint.setColor(mFocused ? mSuccessColor : mFailColor);
        }

        mFocusPaint.setStrokeWidth(mInnerStroke);
        drawLine(canvas, mDialAngle, mFocusPaint);
        drawLine(canvas, mDialAngle + 45, mFocusPaint);
        drawLine(canvas, mDialAngle + 180, mFocusPaint);
        drawLine(canvas, mDialAngle + 225, mFocusPaint);
        canvas.save();
        // rotate the arc instead of its offset to better use framework's shape caching
        canvas.rotate(mDialAngle, mFocusX, mFocusY);
        canvas.drawArc(mDial, 0, 45, false, mFocusPaint);
        canvas.drawArc(mDial, 180, 45, false, mFocusPaint);
        canvas.restore();
        mFocusPaint.setColor(color);
    }

    private void drawLine(Canvas canvas, int angle, Paint p) {
        convertCart(angle, mCircleSize - mInnerOffset, mPoint1);
        convertCart(angle, mCircleSize - mInnerOffset + mInnerOffset / 3, mPoint2);
        canvas.drawLine(mPoint1.x + mFocusX, mPoint1.y + mFocusY,
                mPoint2.x + mFocusX, mPoint2.y + mFocusY, p);
    }

    private static void convertCart(int angle, int radius, Point out) {
        double a = 2 * Math.PI * (angle % 360) / 360;
        out.x = (int) (radius * Math.cos(a) + 0.5);
        out.y = (int) (radius * Math.sin(a) + 0.5);
    }

    @Override
    public void showStart() {
        cancelFocus();
        mStartAnimationAngle = 67;
        int range = getRandomRange();
        startAnimation(SCALING_UP_TIME,
                false, mStartAnimationAngle, mStartAnimationAngle + range);
        mState = STATE_FOCUSING;
    }

    @Override
    public void showSuccess(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = true;
        }
    }

    @Override
    public void showFail(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = false;
        }
    }

    private void cancelFocus() {
        mFocusCancelled = true;
        mOverlay.removeCallbacks(mDisappear);
        if (mAnimation != null) {
            mAnimation.cancel();
        }
        mFocusCancelled = false;
        mFocused = false;
        mState = STATE_IDLE;
    }

    @Override
    public void clear() {
        cancelFocus();
        mOverlay.post(mDisappear);
    }

    private void startAnimation(long duration, boolean timeout,
            float toScale) {
        startAnimation(duration, timeout, mDialAngle,
                toScale);
    }

    private void startAnimation(long duration, boolean timeout,
            float fromScale, float toScale) {
        setVisible(true);
        mAnimation.reset();
        mAnimation.setDuration(duration);
        mAnimation.setScale(fromScale, toScale);
        mAnimation.setAnimationListener(timeout ? mEndAction : null);
        mOverlay.startAnimation(mAnimation);
        update();
    }

    private class EndAction implements AnimationListener {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Keep the focus indicator for some time.
            if (!mFocusCancelled) {
                mOverlay.postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private class Disappear implements Runnable {
        @Override
        public void run() {
            setVisible(false);
            mFocusX = mCenterX;
            mFocusY = mCenterY;
            mState = STATE_IDLE;
            setCircle(mFocusX, mFocusY);
            mFocused = false;
        }
    }

    private class ScaleAnimation extends Animation {
        private float mFrom = 1f;
        private float mTo = 1f;

        public ScaleAnimation() {
            setFillAfter(true);
        }

        public void setScale(float from, float to) {
            mFrom = from;
            mTo = to;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mDialAngle = (int)(mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }
}
