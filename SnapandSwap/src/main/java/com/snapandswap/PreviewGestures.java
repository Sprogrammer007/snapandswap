package com.snapandswap;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.snapandswap.ui.RenderOverlay;
import com.snapandswap.ui.ZoomRenderer;

import java.util.ArrayList;
import java.util.List;

public class PreviewGestures
        implements ScaleGestureDetector.OnScaleGestureListener {

    
	private static final String TAG = "CAM_gestures";

    private static final int MODE_NONE = 0;
    private static final int MODE_ZOOM = 1;
    private static final int MODE_MODULE = 2;
    private static final int MODE_ALL = 3;

    private CameraActivity mActivity;
    private RenderOverlay mOverlay;
    private ZoomRenderer mZoom;
    private MotionEvent mDown;
    private MotionEvent mCurrent;
    private ScaleGestureDetector mScale;
    private List<View> mReceivers;
    private int mMode;
    private int mSlop;
    private int mTapTimeout;
    private boolean mEnabled;
    private boolean mZoomOnly;
    private int mOrientation;
    private int[] mLocation;
    private PopUpManager mPopup;

    public PreviewGestures(CameraActivity ctx,
            ZoomRenderer zoom, PopUpManager popup) {
        mActivity = ctx;
        mZoom = zoom;
        mMode = MODE_ALL;
        mScale = new ScaleGestureDetector(ctx, this);
        mSlop = 12;
        mTapTimeout = ViewConfiguration.getTapTimeout();
        mEnabled = true;
        mLocation = new int[2];
        mPopup = popup;
    }

    public void setRenderOverlay(RenderOverlay overlay) {
        mOverlay = overlay;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void setZoomOnly(boolean zoom) {
        mZoomOnly = zoom;
    }

    public void addTouchReceiver(View v) {
        if (mReceivers == null) {
            mReceivers = new ArrayList<View>();
        }
        mReceivers.add(v);
    }

    public void clearTouchReceivers() {
        if (mReceivers != null) {
            mReceivers.clear();
        }
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return mActivity.superdispatchTouchEvent(m);
        }
        mCurrent = m;
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            if (checkReceivers(m)) {
                mMode = MODE_MODULE;
                return mActivity.superdispatchTouchEvent(m);
            } else {
                mMode = MODE_ALL;
                mDown = MotionEvent.obtain(m);
                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                }
                // make sure this is ok
                return mActivity.superdispatchTouchEvent(m);
            }
        } else if (mMode == MODE_NONE) {
            return false;
        } else if (mMode == MODE_ZOOM) {
            mScale.onTouchEvent(m);
            if (!mScale.isInProgress() && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                mMode = MODE_NONE;
                onScaleEnd(mScale);
            }
            return true;
        } else if (mMode == MODE_MODULE) {
            return mActivity.superdispatchTouchEvent(m);
        } else {
            // didn't receive down event previously;
            // assume module wasn't initialzed and ignore this event.
            if (mDown == null) {
                return true;
            }
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {

                if (mZoom != null) {
                    mScale.onTouchEvent(m);
                    onScaleBegin(mScale);
                }
            } else if ((mMode == MODE_ZOOM) && !mScale.isInProgress()
                    && MotionEvent.ACTION_POINTER_UP == m.getActionMasked()) {
                // user initiated and stopped zoom gesture without zooming
                mScale.onTouchEvent(m);
                onScaleEnd(mScale);
            }
            // not zoom or pie mode and no timeout yet
            if (mZoom != null) {
                boolean res = mScale.onTouchEvent(m);
                if (mScale.isInProgress()) {
                    cancelActivityTouchHandling(m);
                    return res;
                }
            }
            if (MotionEvent.ACTION_UP == m.getActionMasked()) {
                cancelActivityTouchHandling(m);
                // must have been tap
                if (m.getEventTime() - mDown.getEventTime() < mTapTimeout) {
                    mActivity.onSingleTapUp(null,
                            (int) mDown.getX() - mOverlay.getWindowPositionX(),
                            (int) mDown.getY() - mOverlay.getWindowPositionY());
                    return true;
                } else {
                    return mActivity.superdispatchTouchEvent(m);
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                if ((Math.abs(m.getX() - mDown.getX()) > mSlop)
                        || Math.abs(m.getY() - mDown.getY()) > mSlop) {
                    // moved too far and no timeout yet, no focus or pie
             
                    if (isSwipe(m, true)) {
                        mMode = MODE_MODULE;
                        return mActivity.superdispatchTouchEvent(m);
                    } else {
                        cancelActivityTouchHandling(m);
                        if (isSwipe(m , false)) {
                            mMode = MODE_NONE;
                        } 
                    }
                }
            }
            return false;
        }
    }

    private boolean checkReceivers(MotionEvent m) {
        if (mReceivers != null) {
            for (View receiver : mReceivers) {
                if (isInside(m, receiver)) {
                    return true;
                }
            }
        }
        return false;
    }

    // left tests for finger moving right to left
    private boolean isSwipe(MotionEvent m, boolean left) {
        float dx = 0;
        float dy = 0;
        switch (mOrientation) {
        case 0:
            dx = m.getX() - mDown.getX();
            dy = Math.abs(m.getY() - mDown.getY());
            break;
        case 90:
            dx = - (m.getY() - mDown.getY());
            dy = Math.abs(m.getX() - mDown.getX());
            break;
        case 180:
            dx = -(m.getX() - mDown.getX());
            dy = Math.abs(m.getY() - mDown.getY());
            break;
        case 270:
            dx = m.getY() - mDown.getY();
            dy = Math.abs(m.getX() - mDown.getX());
            break;
        }
        if (left) {
            return (dx < 0 && dy / -dx < 0.6f);
        } else {
            return (dx > 0 && dy / dx < 0.6f);
        }
    }

    private boolean isInside(MotionEvent evt, View v) {
        v.getLocationInWindow(mLocation);
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= mLocation[0] && evt.getX() < mLocation[0] + v.getWidth()
                && evt.getY() >= mLocation[1] && evt.getY() < mLocation[1] + v.getHeight());
    }

    public void cancelActivityTouchHandling(MotionEvent m) {
        mActivity.superdispatchTouchEvent(makeCancelEvent(m));
    }

    private MotionEvent makeCancelEvent(MotionEvent m) {
        MotionEvent c = MotionEvent.obtain(m);
        c.setAction(MotionEvent.ACTION_CANCEL);
        return c;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        return mZoom.onScale(detector);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (mMode != MODE_ZOOM) {
            mMode = MODE_ZOOM;
            cancelActivityTouchHandling(mCurrent);
        }
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return mZoom.onScaleBegin(detector);
        } else {
            return true;
        }
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mCurrent.getActionMasked() != MotionEvent.ACTION_MOVE) {
            mZoom.onScaleEnd(detector);
        }
    }
}
