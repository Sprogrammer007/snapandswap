package com.snapandswap;

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.snapandswap.ui.FocusIndicator;
import com.snapandswap.ui.FocusRenderer;

import java.util.ArrayList;
import java.util.List;


public class FocusOverlayManager {

    private static final String TAG = "CAM_FocusManager";

    private static final int RESET_TOUCH_FOCUS = 0;
    private static final int RESET_TOUCH_FOCUS_DELAY = 3000;

    private int mState = STATE_IDLE;
    private static final int STATE_IDLE = 0; // Focus is not active.
    private static final int STATE_FOCUSING = 1; // Focus is in progress.
    // Focus is in progress and the camera should take a picture after focus finishes.
    private static final int STATE_FOCUSING_SNAP_ON_FINISH = 2;
    private static final int STATE_SUCCESS = 3; // Focus finishes and succeeds.
    private static final int STATE_FAIL = 4; // Focus finishes and fails.
	

    private String mFocusMode;
    private String mOverrideFocusMode;
    private Parameters mParameters;
    private String[] mDefaultFocusModes;

    private FocusRenderer mFocusRender;

    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mLockAeAwbNeeded;
    private boolean mAeAwbLock;
    private boolean mInitialized;

	private UserPreferences mPreferences;
    private Matrix mMatrix;

    private int mPreviewWidth; // The width of the preview frame layout.
    private int mPreviewHeight; // The height of the preview frame layout.
    private int mDisplayOrientation;
    private List<Object> mFocusArea; // focus area in driver format
    private List<Object> mMeteringArea; // metering area in driver format

    private Handler mHandler;
    Listener mListener;


    public interface Listener {
        public void autoFocus();
        public void cancelAutoFocus();
        public boolean capture();
        public void setFocusParameters();
    }

    private class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESET_TOUCH_FOCUS: {
                    cancelAutoFocus();
                    break;
                }
            }
        }
    }

    public FocusOverlayManager(UserPreferences preferences, String[] defaultFocusModes,
                               Parameters parameters, Listener listener, Looper looper) {
        mHandler = new MainHandler(looper);
        mMatrix = new Matrix();
        mPreferences = preferences;
        mDefaultFocusModes = defaultFocusModes;
        setParameters(parameters);
        mListener = listener;
    }

    public void setParameters(Parameters parameters) {
        // parameters can only be null when onConfigurationChanged is called
        // before camera is open. We will just return in this case, because
        // parameters will be set again later with the right parameters after
        // camera is open.
        if (parameters == null) return;
        mParameters = parameters;
        mFocusAreaSupported = Util.isFocusAreaSupported(parameters);
        mMeteringAreaSupported = Util.isMeteringAreaSupported(parameters);
        mLockAeAwbNeeded = (Util.isAutoExposureLockSupported(mParameters) ||
                Util.isAutoWhiteBalanceLockSupported(mParameters));
    }

    public void setPreviewSize(int previewWidth, int previewHeight) {
        if (mPreviewWidth != previewWidth || mPreviewHeight != previewHeight) {
            mPreviewWidth = previewWidth;
            mPreviewHeight = previewHeight;
            setMatrix();
        }
    }

    public void setFocusRenderer(FocusRenderer renderer) {
        mFocusRender = renderer;
        mInitialized = (mMatrix != null);
    }

    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        setMatrix();
    }


    private void lockAeAwbIfNeeded() {
        if (mLockAeAwbNeeded && !mAeAwbLock) {
            mAeAwbLock = true;
            mListener.setFocusParameters();
        }
    }

    private void unlockAeAwbIfNeeded() {
        if (mLockAeAwbNeeded && mAeAwbLock && (mState != STATE_FOCUSING_SNAP_ON_FINISH)) {
            mAeAwbLock = false;
            mListener.setFocusParameters();
        }
    }

    private void setMatrix() {
        if (mPreviewWidth != 0 && mPreviewHeight != 0) {
            Matrix matrix = new Matrix();
            Util.prepareMatrix(matrix, mDisplayOrientation,
                    mPreviewWidth, mPreviewHeight);
            // In face detection, the matrix converts the driver coordinates to UI
            // coordinates. In tap focus, the inverted matrix converts the UI
            // coordinates to driver coordinates.
            matrix.invert(mMatrix);
            mInitialized = (mFocusRender != null);
        }
    }

    public void onShutterDown() {
        if (!mInitialized) return;

        boolean autoFocusCalled = false;
        if (needAutoFocusCall()) {
            // Do not focus if touch focus has been triggered.
            if (mState != STATE_SUCCESS && mState != STATE_FAIL) {
                autoFocus();
                autoFocusCalled = true;
            }
        }

        if (!autoFocusCalled) lockAeAwbIfNeeded();
    }

    public void onShutterUp() {
        if (!mInitialized) return;

        if (needAutoFocusCall()) {
            // User releases half-pressed focus key.
            if (mState == STATE_FOCUSING || mState == STATE_SUCCESS
                    || mState == STATE_FAIL) {
                cancelAutoFocus();
            }
        }

        // Unlock AE and AWB after cancelAutoFocus. Camera API does not
        // guarantee setParameters can be called during autofocus.
        unlockAeAwbIfNeeded();
    }

    public void doSnap() {
        if (!mInitialized) return;

        // If the user has half-pressed the shutter and focus is completed, we
        // can take the photo right away. If the focus mode is infinity, we can
        // also take the photo.
        if (!needAutoFocusCall() || (mState == STATE_SUCCESS || mState == STATE_FAIL)) {
            capture();
        } else if (mState == STATE_FOCUSING) {
            // Half pressing the shutter (i.e. the focus button event) will
            // already have requested AF for us, so just request capture on
            // focus here.
            mState = STATE_FOCUSING_SNAP_ON_FINISH;
        } else if (mState == STATE_IDLE) {
            // We didn't do focus. This can happen if the user press focus key
            // while the snapshot is still in progress. The user probably wants
            // the next snapshot as soon as possible, so we just do a snapshot
            // without focusing again.
            capture();
        }
    }

    public void onAutoFocus(boolean focused, boolean shutterButtonPressed) {
        Log.i(TAG, "onAutoFocus");
        if (mState == STATE_FOCUSING_SNAP_ON_FINISH) {
            // Take the picture no matter focus succeeds or fails. No need
            // to play the AF sound if we're about to play the shutter
            // sound.
            if (focused) {
                mState = STATE_SUCCESS;
            } else {
                mState = STATE_FAIL;
            }
            updateFocusUI();
            capture();
        } else if (mState == STATE_FOCUSING) {
            // This happens when (1) user is half-pressing the focus key or
            // (2) touch focus is triggered. Play the focus tone. Do not
            // take the picture now.
            if (focused) {
                mState = STATE_SUCCESS;
            } else {
                mState = STATE_FAIL;
            }
            updateFocusUI();
            // If this is triggered by touch focus, cancel focus after a
            // while.
            if (mFocusArea != null) {
                mHandler.sendEmptyMessageDelayed(RESET_TOUCH_FOCUS, RESET_TOUCH_FOCUS_DELAY);
            }
            if (shutterButtonPressed) {
                // Lock AE & AWB so users can half-press shutter and recompose.
                lockAeAwbIfNeeded();
            }
        } else if (mState == STATE_IDLE) {
            // User has released the focus key before focus completes.
            // Do nothing.
        }
    }

    public void onAutoFocusMoving(boolean moving) {
        if (!mInitialized) return;


        // Ignore if we have requested autofocus. This method only handles
        // continuous autofocus.
        if (mState != STATE_IDLE) return;

        if (moving) {
            mFocusRender.showStart();
        } else {
            mFocusRender.showSuccess(true);
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void initializeFocusAreas(int focusWidth, int focusHeight,
                                      int x, int y, int previewWidth, int previewHeight) {
        if (mFocusArea == null) {
            mFocusArea = new ArrayList<Object>();
            mFocusArea.add(new Camera.Area(new Rect(), 1));
        }

        // Convert the coordinates to driver format.
        calculateTapArea(focusWidth, focusHeight, 1f, x, y, previewWidth, previewHeight,
                ((Camera.Area) mFocusArea.get(0)).rect);
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void initializeMeteringAreas(int focusWidth, int focusHeight,
                                         int x, int y, int previewWidth, int previewHeight) {
        if (mMeteringArea == null) {
            mMeteringArea = new ArrayList<Object>();
            mMeteringArea.add(new Camera.Area(new Rect(), 1));
        }

        // Convert the coordinates to driver format.
        // AE area is bigger because exposure is sensitive and
        // easy to over- or underexposure if area is too small.
        calculateTapArea(focusWidth, focusHeight, 1.5f, x, y, previewWidth, previewHeight,
                ((Camera.Area) mMeteringArea.get(0)).rect);
    }

    public void onSingleTapUp(int x, int y) {
        if (!mInitialized || mState == STATE_FOCUSING_SNAP_ON_FINISH) return;
        Log.i(TAG, "onSingleTapUp");
        // Let users be able to cancel previous touch focus.
        if ((mFocusArea != null) && (mState == STATE_FOCUSING ||
                mState == STATE_SUCCESS || mState == STATE_FAIL)) {
            cancelAutoFocus();
        }
        // Initialize variables.
        int focusWidth = mFocusRender.getSize();
        int focusHeight = mFocusRender.getSize();
        if (focusWidth == 0 || mFocusRender.getWidth() == 0
                || mFocusRender.getHeight() == 0) return;
        int previewWidth = mPreviewWidth;
        int previewHeight = mPreviewHeight;
        // Initialize mFocusArea.
        if (mFocusAreaSupported) {
            initializeFocusAreas(
                    focusWidth, focusHeight, x, y, previewWidth, previewHeight);
        }
        // Initialize mMeteringArea.
        if (mMeteringAreaSupported) {
            initializeMeteringAreas(
                    focusWidth, focusHeight, x, y, previewWidth, previewHeight);
        }

        // Use margin to set the focus indicator to the touched area.
        mFocusRender.setFocus(x, y);



        // Set the focus area and metering area.
        mListener.setFocusParameters();
        if (mFocusAreaSupported) {
            autoFocus();
        } else {  // Just show the indicator in all other cases.
            updateFocusUI();
            // Reset the metering area in 3 seconds.
            mHandler.removeMessages(RESET_TOUCH_FOCUS);
            mHandler.sendEmptyMessageDelayed(RESET_TOUCH_FOCUS, RESET_TOUCH_FOCUS_DELAY);
        }
    }

    public void onPreviewStarted() {
        mState = STATE_IDLE;
    }

    public void onPreviewStopped() {
        // If auto focus was in progress, it would have been stopped.
        mState = STATE_IDLE;
        resetTouchFocus();
        updateFocusUI();
    }

    public void onCameraReleased() {
        onPreviewStopped();
    }

    private void autoFocus() {
        Log.v(TAG, "Start autofocus.");
        mListener.autoFocus();
        mState = STATE_FOCUSING;
        // Pause the face view because the driver will keep sending face
        // callbacks after the focus completes.
        updateFocusUI();
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    private void cancelAutoFocus() {
        Log.v(TAG, "Cancel autofocus.");

        // Reset the tap area before calling mListener.cancelAutofocus.
        // Otherwise, focus mode stays at auto and the tap area passed to the
        // driver is not reset.
        resetTouchFocus();
        mListener.cancelAutoFocus();

        mState = STATE_IDLE;
        updateFocusUI();
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    private void capture() {
        if (mListener.capture()) {
            mState = STATE_IDLE;
            mHandler.removeMessages(RESET_TOUCH_FOCUS);
        }
    }

    public String getFocusMode() {
        if (mOverrideFocusMode != null) return mOverrideFocusMode;
        List<String> supportedFocusModes = mParameters.getSupportedFocusModes();

        if (mFocusAreaSupported && mFocusArea != null) {
            Log.i(TAG, "mFocusAreaSupported" + mFocusAreaSupported + " mFocus Area " + mFocusArea);
            // Always use autofocus in tap-to-focus.
            mFocusMode = Parameters.FOCUS_MODE_AUTO;
        } else {
            // The default is continuous autofocus.
            mFocusMode = mPreferences.getString(
                    CameraSettings.KEY_FOCUS_MODE, null);

            // Try to find a supported focus mode from the default list.
            if (mFocusMode == null) {
                Log.i(TAG, "focus mode is null");
                for (int i = 0; i < mDefaultFocusModes.length; i++) {
                    String mode = mDefaultFocusModes[i];
                    if (Util.isSupported(mode, supportedFocusModes)) {
                        mFocusMode = mode;
                        break;
                    }
                }
            }


        }
        if (!Util.isSupported(mFocusMode, supportedFocusModes)) {
            // For some reasons, the driver does not support the current
            // focus mode. Fall back to auto.
            if (Util.isSupported(Parameters.FOCUS_MODE_AUTO,
                    mParameters.getSupportedFocusModes())) {
                mFocusMode = Parameters.FOCUS_MODE_AUTO;
            } else {
                mFocusMode = mParameters.getFocusMode();
            }
        }
        Log.i(TAG, "" + mFocusMode);
        return mFocusMode;
    }

    public List getFocusAreas() {
        return mFocusArea;
    }

    public List getMeteringAreas() {
        return mMeteringArea;
    }

    public void updateFocusUI() {
        if (!mInitialized) return;
        FocusIndicator focusIndicator = mFocusRender;

        if (mState == STATE_IDLE) {
            if (mFocusArea == null) {
                focusIndicator.clear();
            } else {
                // Users touch on the preview and the indicator represents the
                // metering area. Either focus area is not supported or
                // autoFocus call is not required.
                focusIndicator.showStart();
            }
        } else if (mState == STATE_FOCUSING || mState == STATE_FOCUSING_SNAP_ON_FINISH) {
            focusIndicator.showStart();
        } else {
            if (Util.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusMode)) {
                // TODO: check HAL behavior and decide if this can be removed.
                focusIndicator.showSuccess(false);
            } else if (mState == STATE_SUCCESS) {
                focusIndicator.showSuccess(false);
            } else if (mState == STATE_FAIL) {
                focusIndicator.showFail(false);
            }
        }
    }

    public void resetTouchFocus() {
        if (!mInitialized) return;
        // Put focus indicator to the center. clear reset position
        mFocusRender.clear();
        mFocusArea = null;
        mMeteringArea = null;
    }

    private void calculateTapArea(int focusWidth, int focusHeight, float areaMultiple,
                                  int x, int y, int previewWidth, int previewHeight, Rect rect) {
        int areaWidth = (int) (focusWidth * areaMultiple);
        int areaHeight = (int) (focusHeight * areaMultiple);
        int left = Util.clamp(x - areaWidth / 2, 0, previewWidth - areaWidth);
        int top = Util.clamp(y - areaHeight / 2, 0, previewHeight - areaHeight);

        RectF rectF = new RectF(left, top, left + areaWidth, top + areaHeight);
        mMatrix.mapRect(rectF);
        Util.rectFToRect(rectF, rect);
    }


    /* package */ int getFocusState() {
        return mState;
    }

    public boolean isFocusCompleted() {
        return mState == STATE_SUCCESS || mState == STATE_FAIL;
    }

    public boolean isFocusingSnapOnFinish() {
        return mState == STATE_FOCUSING_SNAP_ON_FINISH;
    }

    public void removeMessages() {
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    public void overrideFocusMode(String focusMode) {
        mOverrideFocusMode = focusMode;
    }

    public void setAeAwbLock(boolean lock) {
        mAeAwbLock = lock;
    }

    public boolean getAeAwbLock() {
        return mAeAwbLock;
    }

    private boolean needAutoFocusCall() {
        String focusMode = getFocusMode();
        return !(focusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                || focusMode.equals(Parameters.FOCUS_MODE_FIXED)
                || focusMode.equals(Parameters.FOCUS_MODE_EDOF));
    }

}
