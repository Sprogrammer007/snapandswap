package com.snapandswap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

public class RenderOverlay extends FrameLayout {

    @SuppressWarnings("unused")
	private static final String TAG = "CAM_Overlay";

    interface Renderer {

        public boolean handlesTouch();
        public boolean onTouchEvent(MotionEvent evt);
        public void setOverlay(RenderOverlay overlay);
        public void layout(int left, int top, int right, int bottom);
        public void draw(Canvas canvas);

    }

    private RenderView mRenderView;
    private List<Renderer> mClients;

    // reverse list of touch clients
    private List<Renderer> mTouchClients;
    private int[] mPosition = new int[2];

    public RenderOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderView = new RenderView(context);
        addView(mRenderView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mClients = new ArrayList<Renderer>(10);
        mTouchClients = new ArrayList<Renderer>(10);
        setWillNotDraw(false);
    }

    public void addRenderer(Renderer renderer) {
        mClients.add(renderer);
        renderer.setOverlay(this);
        if (renderer.handlesTouch()) {
            mTouchClients.add(0, renderer);
        }
    }

    public void addRenderer(int pos, Renderer renderer) {
        mClients.add(pos, renderer);
        renderer.setOverlay(this);
    }

    public void remove(Renderer renderer) {
        mClients.remove(renderer);
        renderer.setOverlay(null);
    }

    public int getClientSize() {
        return mClients.size();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        return false;
    }

    public boolean directDispatchTouch(MotionEvent m, Renderer target) {
        mRenderView.setTouchTarget(target);
        boolean res = super.dispatchTouchEvent(m);
        mRenderView.setTouchTarget(null);
        return res;
    }

    private void adjustPosition() {
        getLocationInWindow(mPosition);
    }

    public int getWindowPositionX() {
        return mPosition[0];
    }

    public int getWindowPositionY() {
        return mPosition[1];
    }

    public void update() {
        mRenderView.invalidate();
    }

    private class RenderView extends View {

        private Renderer mTouchTarget;

        public RenderView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void setTouchTarget(Renderer target) {
            mTouchTarget = target;
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            if (mTouchTarget != null) {
                return mTouchTarget.onTouchEvent(evt);
            }
            if (mTouchClients != null) {
                boolean res = false;
                for (Renderer client : mTouchClients) {
                    res |= client.onTouchEvent(evt);
                }
                return res;
            }
            return false;
        }

        @Override
        public void layout(int left, int top, int right, int bottom) {
            adjustPosition();
            super.layout(left,  top, right, bottom);
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.layout(left, top, right, bottom);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (mClients == null) return;
            boolean redraw = false;
            for (Renderer renderer : mClients) {
                renderer.draw(canvas);
                redraw = redraw || ((OverlayRenderer) renderer).isVisible();
            }
            if (redraw) {
                invalidate();
            }
        }
    }

}
