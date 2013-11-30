package com.snapandswap;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CameraProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Toast;

import com.snapandswap.CameraManager.CameraProxy;
import com.snapandswap.ui.AbstractSettingPopup;
import com.snapandswap.ui.CameraPreview;
import com.snapandswap.ui.FocusRenderer;
import com.snapandswap.ui.MenuManagerLayout;
import com.snapandswap.ui.PreviewFrameLayout;
import com.snapandswap.ui.RenderOverlay;
import com.snapandswap.ui.ShutterButtonLayout;
import com.snapandswap.ui.ShutterZoomView;
import com.snapandswap.ui.ZoomRenderer;

import java.util.List;

@SuppressLint("NewApi")
public class CameraActivity extends Activity {

    final public static String TAG = "CustomCamera";

    private static CameraProxy mCamera;
    private Parameters mParameters;
    private Parameters mInitialParams;
    private static CameraListenerHelper mListener;
    private OrientationManager mOrientationManger;


    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private static final int CHECK_DISPLAY_ROTATION = 1;
    private static final int START_PREVIEW_DONE = 2;
    private static final int OPEN_CAMERA_FAIL = 3;
    private static final int CAMERA_DISABLED = 4;
    private static final int CLEAR_SCREEN_DELAY = 5;
    private static final int SETUP_PREVIEW = 6;
    private static final int CAMERA_OPEN_DONE = 7;
    private static final int PLAY_FOCUS_COMPLETE = 8;

    // This is the timeout to keep the camera in onPause for the first time
    // after screen on if the activity is started from secure lock screen.
    private static final int KEEP_CAMERA_TIMEOUT = 1000; // ms
    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private CameraPreview mSurfaceView;
    private TextureView mSurfaceTextureView;
    private MenuManagerLayout mMenu;
    private PopUpManager mPopup;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    private RenderOverlay mOverlay;
    private FocusRenderer mFocusRenderer;
    private ShutterButtonLayout mShutter;
    private SurfaceHolder mHolder;

    private int curPicsCount = 0;
    private int jpgRotation;
    private int mCameraId;
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    public int mLastRawOrientation;
    private int mRotation = -1;
    private int mDisplayOrientation = -1;

    public long mAutoFocusTime;
    private long mOnResumeTime;
    private long mFocusStartTime;

    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final JpegPictureCallback mJpegPictureCallback =
            new JpegPictureCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new AutoFocusMoveCallBack()
                    : null;
    private ShutterCallBackHelper mShutterHelper =
            new ShutterCallBackHelper();

    private boolean mPaused;
    private boolean mOpenCameraFail;
    private boolean mCameraDisabled;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mContinousFocusSupported;
    private boolean mSnapshotOnIdle = false;
    private boolean zoomChanged = false;
    private boolean mFocusSoundEnable = true;

    private CameraSettings mSettings;
    //Camera state managements
    private static final int PREVIEW_STOPPED = 0;
    private static final int IDLE = 1;  // preview is active
    // Focus is in progress. The exact focus state is in Focus.java.
    private static final int FOCUSING = 2;
    private static final int SNAPSHOT_IN_PROGRESS = 3;
    private int mCameraState = PREVIEW_STOPPED;

    private String mSceneMode;

    private final Handler mHandler = new MainHandler();
    CameraStartUpThread mCameraStartUpThread;
    ConditionVariable mStartPreviewPrerequisiteReady = new ConditionVariable();

    private PreviewFrameLayout mPreviewFrameLayout;
    private SurfaceTexture mSurfaceTexture;
    private PreferenceGroup mPrefGroup;
    private UserPreferences mPreferences;
    private FocusOverlayManager mFocusManager;

    final private static Object mLock = new Object();
    private SoundClips.Player mSoundPlayer;

    private Runnable mDoSnapRunnable = new Runnable() {
        @Override
        public void run() {
            if(mListener != null) {
                mListener.onShutterButtonClick();
            }
        }
    };



    private class CameraStartUpThread extends Thread {
        private volatile boolean mCancelled;

        public void cancel() {
            mCancelled = true;
        }

        @Override
        public void run() {
            try {
                // We need to check whether the activity is paused before long
                // operations to ensure that onPause() can be done ASAP.
                if (mCancelled) return;
                mCamera = Util.openCamera(CameraActivity.this, mCameraId);
                mParameters = mCamera.getParameters();
                // Wait until all the initialization needed by startPreview are
                // done
                mStartPreviewPrerequisiteReady.block();

                initializeCapabilities();
                if (mFocusManager == null) initializeFocusManager();
                if (mCancelled) return;
                updateCameraParametersPreference();
                mHandler.sendEmptyMessage(CAMERA_OPEN_DONE);
                if (mCancelled) return;
                startPreview();
                mHandler.sendEmptyMessage(START_PREVIEW_DONE);
                mOnResumeTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessage(CHECK_DISPLAY_ROTATION);
            } catch (CameraHardwareException e) {
                mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
            } catch (CameraDisabledException e) {
                mHandler.sendEmptyMessage(CAMERA_DISABLED);
            }
        }
    }


    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    @SuppressLint("HandlerLeak")
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SETUP_PREVIEW: {
                    setupPreview();
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Set the display orientation if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if (Util.getDisplayRotation(CameraActivity.this) != mRotation) {
                        setDisplayOrientation();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }
                case CLEAR_SCREEN_DELAY:
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;

                case START_PREVIEW_DONE: {
                    setCameraState(IDLE);
                    mCameraStartUpThread = null;
                    if (!ApiHelper.HAS_SURFACE_TEXTURE) {
                        // This may happen if surfaceCreated has arrived.
                        mCamera.setPreviewDisplayAsync(mHolder);
                    }
                    break;
                }

                case CAMERA_OPEN_DONE: {
                    initializeAfterCameraOpen();
                    break;
                }

                case OPEN_CAMERA_FAIL: {
                    mCameraStartUpThread = null;
                    mOpenCameraFail = true;
                    Util.showErrorAndFinish(CameraActivity.this,
                            R.string.cannot_connect_camera);
                    break;
                }

                case CAMERA_DISABLED: {
                    mCameraStartUpThread = null;
                    mCameraDisabled = true;
                    Util.showErrorAndFinish(CameraActivity.this,
                            R.string.camera_disabled);
                    break;
                }

                case PLAY_FOCUS_COMPLETE: {
                    if (mSoundPlayer != null) {
                        mSoundPlayer.play(SoundClips.FOCUS_COMPLETE);
                    }
                    break;
                }

            }
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mCameraId = CameraHolder.getInstances().getBackCameraId();

        mPreferences = new UserPreferences(this);
        mListener = new CameraListenerHelper();
        mSettings = new CameraSettings(CameraActivity.this, mParameters, mCameraId,
                CameraHolder.getInstances().getCameraInfo());
        mPreferences.setLocalId(this, mCameraId);


        mCameraStartUpThread = new CameraStartUpThread();
        mCameraStartUpThread.start();

        //setup OrientationManger to listen to orientation changes
        mOrientationManger = new OrientationManager(this);
        mOrientationManger.setOrientationChangeListener(mListener);

        initializePreviewSurface();
        mStartPreviewPrerequisiteReady.open();
        initialize();
    }

    private void initializePreviewSurface() {

        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);

        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            mSurfaceTextureView = (TextureView) findViewById(R.id.texture_frame);
            mSurfaceTextureView.setVisibility(View.VISIBLE);
            mSurfaceTextureView.setSurfaceTextureListener(mListener);
        } else {
            mSurfaceView = (CameraPreview) findViewById(R.id.preview_surface_view);
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceView.getHolder().addCallback(mListener);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void initialize() {

        // Initialize shutter button.
        mShutter = (ShutterButtonLayout) findViewById(R.id.shutter_button);
        mShutter.setOnShutterButtonChangeListener(mListener);
        mShutter.setVisibility(View.VISIBLE);

        mPrefGroup = mSettings.getPreferenceGroup(R.xml.camera_preferences);
        mPopup = new PopUpManager(this);

        mMenu = (MenuManagerLayout) findViewById(R.id.camera_menu);

        if (mMenu != null) {
            if (mPrefGroup != null) {
                mMenu.setPreferenceGroup(mPrefGroup);
            }
            if (mPopup != null) {
                mMenu.addPopup(mPopup);
                mPopup.addMenu(mMenu);
            }

        }

    }

    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        mOverlay = (RenderOverlay) findViewById(R.id.render_overlay);

        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {

            String[] defaultFocusModes = this.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            mFocusManager = new FocusOverlayManager(mPreferences, defaultFocusModes,
                    mInitialParams, mListener, this.getMainLooper());
        }
    }

    private void initializeAfterCameraOpen() {
        if (mFocusRenderer == null) {
            mFocusRenderer = new FocusRenderer(this);
        }

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(this);
        }
        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(this, mZoomRenderer, mPopup);
        }
        setPreviewFrameLayoutAspectRatio();
        mFocusManager.setPreviewSize(mPreviewFrameLayout.getWidth(),
                mPreviewFrameLayout.getHeight());
        initializeRenderOverlay();
        initializeZoom();

        String[] keys = getResources()
                .getStringArray(R.array.pref_camera_all_keys);
        updateMenuItems(null, keys);

        if (mSoundPlayer == null) mSoundPlayer = SoundClips.getPlayer(this);
    }

    void setPreviewFrameLayoutAspectRatio() {
        // Set the preview frame aspect ratio according to the picture size.
        Size size = mParameters.getPictureSize();
        mPreviewFrameLayout.setAspectRatio((double) size.width / size.height);
    }

    private void initializeZoom() {
        if ((mParameters == null) || !mParameters.isZoomSupported()
                || (mZoomRenderer == null)) return;
        mZoomMax = mParameters.getMaxZoom();
        mZoomRatios = mParameters.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer != null) {
            mZoomRenderer.setZoomMax(mZoomMax);
            mZoomRenderer.setZoom(mParameters.getZoom());
            mZoomRenderer.setZoomValue(mZoomRatios.get(mParameters.getZoom()));
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            // Not useful to change zoom value when the activity is paused.
            if (mPaused) return;
            mZoomValue = index;
            if (mParameters == null || mCamera == null) return;
            // Set zoom parameters asynchronously
            mParameters.setZoom(mZoomValue);
            mCamera.setParameters(mParameters);
            if (mZoomRenderer != null) {
                Parameters p = mCamera.getParameters();
                mZoomRenderer.setZoomValue(mZoomRatios.get(p.getZoom()));
            }
        }

        @Override
        public void onZoomStart() {
            if (mFocusRenderer != null) {
                mFocusRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mFocusRenderer != null) {
                mFocusRenderer.setBlockFocus(false);
            }
        }
    }

    private void initializeRenderOverlay() {

        if (mFocusRenderer != null) {
            mOverlay.addRenderer(mFocusRenderer);
            mFocusManager.setFocusRenderer(mFocusRenderer);
        }

        if (mZoomRenderer != null) {
            mOverlay.addRenderer(mZoomRenderer);
        }
        if (mGestures != null) {
            mGestures.clearTouchReceivers();
            mGestures.setRenderOverlay(mOverlay);
            mGestures.addTouchReceiver(mShutter);
            mGestures.addTouchReceiver(mMenu);
        }
        mOverlay.requestLayout();
    }

    private void initializeCapabilities() {
        mInitialParams = mCamera.getParameters();
        mFocusAreaSupported = Util.isFocusAreaSupported(mParameters);
        mMeteringAreaSupported = Util.isMeteringAreaSupported(mParameters);
        mAeLockSupported = Util.isAutoExposureLockSupported(mParameters);
        mAwbLockSupported = Util.isAutoWhiteBalanceLockSupported(mParameters);
        mContinousFocusSupported = mParameters.getSupportedFocusModes().contains(
                Util.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null && mOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
    }

    public boolean superdispatchTouchEvent(MotionEvent m) {
        return super.dispatchTouchEvent(m);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        mOrientationManger.pause();
        onPauseBeforeSuper();
        super.onPause();
        onPauseAfterSuper();

    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Resumed  ");
        mOrientationManger.resume();
        onResumeBeforeSuper();
        super.onResume();
        onResumeAfterSuper();
    }

    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    public void onResumeAfterSuper() {
        if (mOpenCameraFail || mCameraDisabled) return;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED && mCameraStartUpThread == null) {
            mCameraStartUpThread = new CameraStartUpThread();
            mCameraStartUpThread.start();
        }
        keepScreenOnAwhile();
    }

    public void onPauseBeforeSuper() {
        mPaused = true;
    }

    public void onPauseAfterSuper() {
        // Wait the camera start up thread to finish.
        waitCameraStartUpThread();

        // When camera is started from secure lock screen for the first time
        // after screen on, the activity gets onCreate->onResume->onPause->onResume.
        // To reduce the latency, keep the camera for a short time so it does
        // not need to be opened again.
        if (mCamera != null) {
            CameraHolder.getInstances().keep(KEEP_CAMERA_TIMEOUT);
        }
        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCamera != null && mCameraState != PREVIEW_STOPPED) {
            mCamera.cancelAutoFocus();
        }

        if (mPopup != null) {
            mPopup.dismissAll();
        }
        stopPreview();
        // Close the camera now because other activities may need to use it.
        closeCamera();

        resetScreenOn();
        // Remove the messages in the event queue.
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mHandler.removeMessages(START_PREVIEW_DONE);
        mHandler.removeMessages(OPEN_CAMERA_FAIL);
        mHandler.removeMessages(CAMERA_DISABLED);

        if (mFocusManager != null) mFocusManager.removeMessages();
        if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        } else {
        super.onBackPressed();
        }
    }

    void waitCameraStartUpThread() {
        try {
            if (mCameraStartUpThread != null) {
                mCameraStartUpThread.cancel();
                mCameraStartUpThread.join();
                mCameraStartUpThread = null;
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted());
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSurfaceTextureView.setVisibility(View.GONE);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
        mSurfaceTextureView.setVisibility(View.VISIBLE);
    }


    private void stopPreview() {
        if (mCamera != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCamera.stopPreview();
        }
        setCameraState(PREVIEW_STOPPED);
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) mFocusManager.onPreviewStopped();
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.setZoomChangeListener(null);
            CameraHolder.getInstances().release();
            Log.v(TAG, "camera released");    // release the camera for other applications
            mCamera = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }


    private void setDisplayOrientation() {
        mRotation = Util.getDisplayRotation(this);
        mDisplayOrientation = Util.getDisplayOrientation(mRotation, mCameraId);

        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
    }

    // Only called by UI thread.
    private void setupPreview() {
        mFocusManager.resetTouchFocus();
        startPreview();
        setCameraState(IDLE);
    }
    private void startPreview() {
        if (mCameraState != PREVIEW_STOPPED) stopPreview();

        setDisplayOrientation();
        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus to
            // resume it because it may have been paused by autoFocus call.
            if (Util.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode())) {
                mCamera.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }

        updateCameraParametersPreference();

        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            if (mSurfaceTexture == null) {
                Camera.Size size = mParameters.getPreviewSize();
                int width;
                int height;
                if (mDisplayOrientation % 180 == 0) {
                    width = size.width;
                    height = size.height;

                } else {
                    width = size.height;
                    height = size.width;
                }
                synchronized (mLock) {
                    mSurfaceTexture = mSurfaceTextureView.getSurfaceTexture();
                    mSurfaceTexture.setDefaultBufferSize(width, height);
                }
            }
            mCamera.setDisplayOrientation(mDisplayOrientation);
            mCamera.setPreviewTextureAsync(mSurfaceTexture);
        } else {
            mCamera.setDisplayOrientation(mDisplayOrientation);
            mCamera.setPreviewDisplayAsync(mHolder);
        }
        mCamera.startPreviewAsync();

        mFocusManager.onPreviewStarted();

        if (mSnapshotOnIdle) {
            mHandler.post(mDoSnapRunnable);
        }
    }

    private void updateUIOrientation(int orientation) {
        mPopup.updateOrientation(orientation);
        mShutter.setOrientation(orientation, true);
        mMenu.setOrientation(orientation, true);
    }

    private void setCameraState(int state) {
        mCameraState = state;
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            // Use the same area for focus and metering.
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }

    private void updateCameraParametersPreference() {
        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();


        //update PictureSize
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(this, mParameters);
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }
        Size size = mParameters.getPictureSize();

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = Util.getOptimalPreviewSize(this, sizes,
                (double) size.width / size.height);
        Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get latest values
            mCamera.setParameters(mParameters);
            mParameters = mCamera.getParameters();
        }

        // Since changing scene mode may change supported values, set scene mode
        // first. HDR is a scene mode. To promote it in UI, it is stored in a
        // separate preference.
        String hdr = mPreferences.getString(CameraSettings.KEY_CAMERA_HDR,
                this.getString(R.string.pref_camera_hdr_default));
        if (this.getString(R.string.setting_on_value).equals(hdr)) {
            mSceneMode = Util.SCENE_MODE_HDR;
        } else {
            mSceneMode = mPreferences.getString(
                    CameraSettings.KEY_SCENE_MODE,
                    this.getString(R.string.pref_camera_scenemode_default));
        }

        if (Util.isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCamera.setParameters(mParameters);
                mParameters = mCamera.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        // Set JPEG quality.
        int sJpegQuality = Integer.parseInt(mPreferences.getString(
                CameraSettings.KEY_IMAGE_QUALITY,
                this.getString(R.string.pref_camera_imagequality_default)));
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId, sJpegQuality);
        mParameters.setJpegQuality(jpegQuality);


        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {

            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    this.getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (Util.isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = this.getString(
                            R.string.pref_camera_flashmode_entry_off);
                }
            }

            // Set white balance parameter.
            String whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    this.getString(R.string.pref_camera_whitebalance_default));
            if (Util.isSupported(whiteBalance,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(whiteBalance);
            } else {
                whiteBalance = mParameters.getWhiteBalance();
                if (whiteBalance == null) {
                    whiteBalance = Parameters.WHITE_BALANCE_AUTO;
                }
            }

            // Set focus mode.
            mFocusManager.overrideFocusMode(null);
            mParameters.setFocusMode(mFocusManager.getFocusMode());
        } else {
            Log.i(TAG, "Not auto scene mode");
            // if user changes scene mode to anything other then auto
            // we set flash mode and white balance to default
            // Set flash mode.
            Log.i(TAG, "before " +  mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    "tessef"));

            SharedPreferences.Editor editor = UserPreferences.get(this).edit();
            String flashMode =Parameters.FLASH_MODE_OFF;
            mParameters.setFlashMode(flashMode);
            editor.putString(CameraSettings.KEY_FLASH_MODE, flashMode);
            editor.apply();

            Log.i(TAG, "after " +  mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    "tessef"));
            // Set white balance parameter.
            String whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            if (Util.isSupported(whiteBalance,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(whiteBalance);
            }
            editor.putString(CameraSettings.KEY_WHITE_BALANCE, whiteBalance);
            editor.apply();
            //resetExposure Value to default

            mPopup.resetExposure();
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
        }

        if (mContinousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }



        //set shuuter sound on/off
        String shutterSound = mPreferences.getString(
                CameraSettings.KEY_CAMERA_SHUTTER_SOUND,
                this.getString(R.string.pref_camera_shutter_sound_default));
        if (shutterSound.equals(CameraSettings.SHUTTER_SOUND_ON)) {
            mShutterHelper = new ShutterCallBackHelper();
            mCamera.enableShutterSound(true);
        } else {
            mShutterHelper = null;
            mCamera.enableShutterSound(true);
        }

        //set shuuter sound on/off
        String focusSound = mPreferences.getString(
                CameraSettings.KEY_CAMERA_FOCUS_SOUND,
                this.getString(R.string.pref_camera_focus_sound_default));
        if (focusSound.equals(CameraSettings.FOCUS_SOUND_ON)) {
            mFocusSoundEnable = true;
        } else {
            mFocusSoundEnable = false;
        }

        mCamera.setParameters(mParameters);
    }

    public void updateExposure(int value) {
        mParameters = mCamera.getParameters();
        mParameters.setExposureCompensation(value);
        mCamera.setParameters(mParameters);
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mParameters.getFocusMode().equals(Util.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mCamera.setAutoFocusMoveCallback(
                    (Camera.AutoFocusMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCamera.setAutoFocusMoveCallback(null);
        }
    }

    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCamera == null
//				|| !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }

        // Do not trigger touch focus if popup window is opened.
		if (removeTopLevelPopup()) return;

        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) return;
		mFocusManager.onSingleTapUp(x, y);
    }

    private boolean removeTopLevelPopup() {
        // Remove the top level popup or dialog box and return true if there's any
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
            return true;
        } else {
            return false;
        }
    }

    public static CameraListenerHelper getCameraListener() {
        if (mListener != null) {
            return mListener;
        } else {
            throw new NullPointerException("No Listener");
        }
    }

    /* Camera Listener Helper
     * This Inner-Class handles all camera call backs and listener
     */
    public class CameraListenerHelper implements
            SurfaceHolder.Callback,SurfaceTextureListener,
            ShutterButtonLayout.OnShutterButtonChangeListener,
            OrientationManager.Listener, FocusOverlayManager.Listener,
            AbstractSettingPopup.Listener {


        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "surfaceCreated: " + holder);
            mHolder = holder;
            if (mCamera == null || mCameraStartUpThread != null) return;
            mCamera.setPreviewDisplayAsync(mHolder);

            if (mCameraState == PREVIEW_STOPPED) {
                setupPreview();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mHolder = null;
            CameraActivity.this.stopPreview();
        }

        @Override
        public void onShutterButtonFocus(boolean pressed) {
            if (mPaused || (mCameraState == SNAPSHOT_IN_PROGRESS)
                    || (mCameraState == PREVIEW_STOPPED)) return;

            if (zoomChanged) {
                mZoomRenderer.onZoomEnd();
                zoomChanged = false;
            }

        }

        @Override
        public void onShutterButtonReleased() {
            mFocusManager.onShutterUp();
        }

        @Override
        public void onShutterButtonClick() {
            if (mPaused || (mCameraState == SNAPSHOT_IN_PROGRESS)
            ||(mCameraState == PREVIEW_STOPPED)) return;

            if (removeTopLevelPopup()) return;

            mFocusManager.onShutterDown();
            Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

            // If the user wants to do a snapshot while the previous one is still
            // in progress, remember the fact and do it after we finish the previous
            // one and re-start the preview. Snapshot in progress also includes the
            // state that autofocus is focusing and a picture will be taken when
            // focus callback arrives.
            if ((mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS)) {
                mSnapshotOnIdle = true;
                return;
            }

            mSnapshotOnIdle = false;
            mFocusManager.doSnap();
        }

        @Override
        public void onOrientationChange(int orientation) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
            mLastRawOrientation = orientation;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            int cwOrientation = Util.getCWOrientation(CameraActivity.this, mOrientation);
            updateUIOrientation(cwOrientation);
            if (mZoomRenderer != null) mZoomRenderer.setOrientation(cwOrientation);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface,
                                              int width, int height) {
            Log.d(CameraActivity.TAG, "surface created");
            if (mSurfaceTexture == null) {
                mSurfaceTexture = surface;
            }

            if (mCamera == null || mCameraStartUpThread != null) return;;
            mCamera.setPreviewTextureAsync(surface);

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(CameraActivity.TAG, "surface destroyed");
            CameraActivity.this.stopPreview();

            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {

        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }

        @Override
        public void onListPrefChanged(AdapterView<?> parent, View view,
                                      int index, long id,ListPreference pref) {
            String[] key = new String[]{pref.getKey()};
            updateCameraParametersPreference();

             if (mMenu != null) {
                updateMenuItems(pref, key);
            }
            mPopup.dismiss();
        }

        @Override
        public void onShutterButtonMoved(int angle, int power, int direction) {
            if (mParameters == null || mCamera == null) return;
            cancelAutoFocus();
            float c = mZoomRenderer.getCircleSize();
            power = power / 4;
            int change = Math.round(power);
            if (direction == ShutterZoomView.FRONT
                    || direction == ShutterZoomView.FRONT_RIGHT
                    || direction == ShutterZoomView.LEFT_FRONT) {
                c = c + change;
                mZoomRenderer.onScale(c);
                zoomChanged = true;
            } else if (direction == ShutterZoomView.BOTTOM
                    || direction == ShutterZoomView.BOTTOM_LEFT
                    || direction == ShutterZoomView.RIGHT_BOTTOM){
                c = c - change;
                mZoomRenderer.onScale(c);
                zoomChanged = true;
            } else {

            }
        }

        @Override
        public void autoFocus() {
            mFocusStartTime = System.currentTimeMillis();
            if (mParameters.getFocusMode().equals(Util.FOCUS_MODE_CONTINUOUS_PICTURE)){
               mCamera.cancelAutoFocus();
            } else {
                mCamera.autoFocus(mAutoFocusCallback);
            }

            setCameraState(FOCUSING);
        }

        @Override
        public void cancelAutoFocus() {
            mCamera.cancelAutoFocus();
            setCameraState(IDLE);
            updateCameraParametersPreference();
        }

        @Override
        public boolean capture() {
            if (mCamera == null || mCameraState == SNAPSHOT_IN_PROGRESS) {
                return false;
            }

            if (curPicsCount <= 4) {
                jpgRotation = Util.getJpegRotation(CameraHolder.getInstances().getCurrentCameraId(), mOrientation);
                mParameters.setRotation(jpgRotation);
                mCamera.setParameters(mParameters);
                mCamera.takePicture(mShutterHelper,
                        null, null,
                        mJpegPictureCallback);
                curPicsCount++;
                mFocusManager.updateFocusUI();
            } else {
                Toast.makeText(getApplicationContext(), "You Only Allow Up to 5 Pictures", Toast.LENGTH_LONG).show();
            }
            setCameraState(SNAPSHOT_IN_PROGRESS);
            return true;
        }

        @Override
        public void setFocusParameters() {
            updateCameraParametersPreference();
        }
    }

    private void updateMenuItems(ListPreference pref, String[] key) {
        final boolean c = (Parameters.SCENE_MODE_AUTO.equals(mSceneMode));
        for (int i = 0; i < key.length; i++) {
            if (key[i].equals(CameraSettings.KEY_SCENE_MODE)) {
                mPopup.setSeletable(!c);
            } else {
                continue;
            }
        }
        mPopup.updateMultiPref(pref);
        mMenu.updateImageButtonDrawable(key, c);
    }

    private final class AutoFocusCallback implements Camera.AutoFocusCallback{

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (mPaused) return;
            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            setCameraState(IDLE);
            mFocusManager.onAutoFocus(success, mShutter.isPressed());

            if (mCameraState != SNAPSHOT_IN_PROGRESS && mFocusSoundEnable) {
                mHandler.sendEmptyMessageDelayed(PLAY_FOCUS_COMPLETE, 350);
            }
        }
    }

    private final class AutoFocusMoveCallBack implements Camera.AutoFocusMoveCallback {

        @Override
        public void onAutoFocusMoving(boolean moving, Camera camera) {
            Log.i(TAG, "focus moving");
            mFocusManager.onAutoFocusMoving(moving);
        }
    }

    private final class JpegPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (mPaused)  return;
            if (mShutterHelper == null) {
                mSoundPlayer.vibrate(100);
            }
            StorageUtil.createNewJpeg(CameraActivity.this, data);
            mFocusManager.updateFocusUI();

            if (ApiHelper.CAN_START_PREVIEW_IN_JPEG_CALLBACK) {
                setupPreview();
            } else {
                // Camera HAL of some devices have a bug. Starting preview
                // immediately after taking a picture will fail. Wait some
                // time before starting the preview.
                mHandler.sendEmptyMessageDelayed(SETUP_PREVIEW, 300);
            }
        }
    }

    private final class ShutterCallBackHelper implements Camera.ShutterCallback {

        @Override
        public void onShutter() {
        }
    }
}