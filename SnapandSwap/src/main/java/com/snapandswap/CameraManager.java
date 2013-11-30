package com.snapandswap;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

import static com.snapandswap.Util.Assert;

public class CameraManager {

    private final static String TAG = "CameraManager";

	private static CameraManager mCameraManager = new CameraManager();

	 // Thread progress signals
    private ConditionVariable mSig = new ConditionVariable();
    
    private static final int RELEASE = 1;
    private static final int RECONNECT = 2;
    private static final int UNLOCK = 3;
    private static final int LOCK = 4;
    private static final int SET_PREVIEW_TEXTURE_ASYNC = 5;
    private static final int START_PREVIEW_ASYNC = 6;
    private static final int STOP_PREVIEW = 7;
    private static final int AUTO_FOCUS = 10;
    private static final int CANCEL_AUTO_FOCUS = 11;
    private static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 12;
    private static final int SET_DISPLAY_ORIENTATION = 13;
    private static final int SET_ZOOM_CHANGE_LISTENER = 14;
    private static final int SET_PARAMETERS = 19;
    private static final int GET_PARAMETERS = 20;
    private static final int WAIT_FOR_IDLE = 22;
    private static final int SET_PREVIEW_DISPLAY_ASYNC = 23;
    private static final int ENABLE_SHUTTER_SOUND = 25;

	public static Parameters mParameters = null;
	
	private Handler mCameraHandler;
	private CameraProxy mCameraProxy;
	private Camera mCamera;

	public IOException mReconnectException;

	private CameraManager() {
		HandlerThread hThread = new HandlerThread("Camera Handler Thread");
		hThread.start();
		mCameraHandler = new MyCameraHandler(hThread.getLooper());
	}

	public static CameraManager getInstances() {
		return mCameraManager;
	}

	@SuppressLint("HandlerLeak")
	private class MyCameraHandler extends Handler {
		MyCameraHandler(Looper looper) {
			super(looper);
		} 
		
		@TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN_MR1)
		private void enableShutterSound(boolean enable) {
            try {
                mCamera.enableShutterSound(enable);
            } catch (NoSuchMethodError ex) {
                Log.e(TAG, "" + ex);
            }

		}
		
		@Override
		public void handleMessage(final Message msg) {
			try {
				switch (msg.what) {
				case RELEASE:
					mCamera.release();
					mCamera = null;
					break;

				case RECONNECT:
					mReconnectException = null;
					try {
						mCamera.reconnect();
					} catch (IOException ex) {
						mReconnectException = ex;
					}
					break;
					
				case SET_PREVIEW_TEXTURE_ASYNC:
					try {
						mCamera.setPreviewTexture((SurfaceTexture) msg.obj);
					} catch(IOException e) {
						throw new RuntimeException(e);
					}
					return;  

				case SET_PREVIEW_DISPLAY_ASYNC:
					try {
						Log.i(CameraActivity.TAG, "third " + (SurfaceHolder) msg.obj);
						mCamera.setPreviewDisplay((SurfaceHolder) msg.obj);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return;
				case SET_PARAMETERS:
					mCamera.setParameters((Parameters) msg.obj);
					break;

				case GET_PARAMETERS:
					mParameters = mCamera.getParameters();
					break;

				case START_PREVIEW_ASYNC:
					mCamera.startPreview();
					return;

				case AUTO_FOCUS:
					mCamera.autoFocus((AutoFocusCallback) msg.obj);
					break;
					
				case CANCEL_AUTO_FOCUS:
					mCamera.cancelAutoFocus();
					break;

				case SET_DISPLAY_ORIENTATION:
					mCamera.setDisplayOrientation(msg.arg1);
                    break;
				case ENABLE_SHUTTER_SOUND:
					enableShutterSound((msg.arg1 == 1) ? true : false);
					break;

				case WAIT_FOR_IDLE:
					// do nothing
					break;
				default:
					break;
				}
			} catch (RuntimeException e) {
				if (msg.what != RELEASE && mCamera != null) {
					try {
						mCamera.release();
					} catch (Exception ex) {
						Log.e(CameraActivity.TAG, "Fail to release the camera.");
					}
					mCamera = null;
					mCameraProxy = null;
				}
				throw e;
			}
			mSig.open();
		}
	}

    CameraProxy openCamera(int cameraId) {
        // Cannot open camera in mCameraHandler, otherwise all camera events
        // will be routed to mCameraHandler looper, which in turn will call
        // event handler like Camera.onFaceDetection, which in turn will modify
        // UI and cause exception like this:
        // CalledFromWrongThreadException: Only the original thread that created
        // a view hierarchy can touch its views.
        mCamera = Camera.open(cameraId);
        if (mCamera != null) {
            mCameraProxy = new CameraProxy();
            Log.i(CameraActivity.TAG, "camera open success");
            return mCameraProxy;
        } else {
            return null;
        }
    }
	
	public class CameraProxy {

		private CameraProxy() {
			Assert(mCamera != null);
		}
		
		public Camera getCamera() {
			return mCamera;
		}
		
		public void release() {
			mSig.close();
			mCameraHandler.sendEmptyMessage(RELEASE);
			mSig.block();
		}
		
		public void reconnect() throws IOException{
			mSig.close();
			mCameraHandler.sendEmptyMessage(RECONNECT);
			mSig.block();
			if (mReconnectException != null) {
                throw mReconnectException;
            }
		}
		
        public void unlock() {
            mSig.close();
            mCameraHandler.sendEmptyMessage(UNLOCK);
            mSig.block();
        }

        public void lock() {
            mSig.close();
            mCameraHandler.sendEmptyMessage(LOCK);
            mSig.block();
        }
        
        public void stopPreview() {
        	mSig.close();
            mCameraHandler.sendEmptyMessage(STOP_PREVIEW);
            mSig.block();
        }
        
        public void takePicture(final ShutterCallback shutter, final PictureCallback raw,
                final PictureCallback postview, final PictureCallback jpeg) {
            mSig.close();
            mCameraHandler.post(new Runnable() {
				@Override
				public void run() {
					mCamera.takePicture(shutter, raw, postview, jpeg);
					mSig.open();
				}
            });
            mSig.block();
        }
        
        public void setPreviewDisplayAsync(final SurfaceHolder surfaceHolder) {
        	mCameraHandler.obtainMessage(SET_PREVIEW_DISPLAY_ASYNC, surfaceHolder).sendToTarget();
        }
        
        public void setPreviewTextureAsync(final SurfaceTexture surfaceTexture) {
        	 mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC, surfaceTexture).sendToTarget();
        }
        
    	public void startPreviewAsync() {
			mCameraHandler.sendEmptyMessage(START_PREVIEW_ASYNC);
	
		}
        
        public void setDisplayOrientation(int degrees) {
            mSig.close();
            mCameraHandler.obtainMessage(SET_DISPLAY_ORIENTATION, degrees, 0).sendToTarget();
            mSig.block();
        }

        public void setZoomChangeListener(OnZoomChangeListener listener) {
            mSig.close();
            mCameraHandler.obtainMessage(SET_ZOOM_CHANGE_LISTENER, listener).sendToTarget();
            mSig.block();
        }
        
        public void setParameters(Parameters params) {
        	mSig.close();
        	mCameraHandler.obtainMessage(SET_PARAMETERS, params).sendToTarget();
        	mSig.block();
        }
        
        public Parameters getParameters() {
        	mSig.close();
        	mCameraHandler.sendEmptyMessage(GET_PARAMETERS);
        	mSig.block();
        	Parameters parameters = mParameters;
        	mParameters = null;
        	return parameters;
        }
        
        public void autoFocus(AutoFocusCallback cb) {
            mSig.close();
            mCameraHandler.obtainMessage(AUTO_FOCUS, cb).sendToTarget();
            mSig.block();
        }
        
        public void cancelAutoFocus() {
            mSig.close();
            mCameraHandler.sendEmptyMessage(CANCEL_AUTO_FOCUS);
            mSig.block();
        }

        @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
        public void setAutoFocusMoveCallback(Camera.AutoFocusMoveCallback cb) {
            mSig.close();
            mCameraHandler.obtainMessage(SET_AUTO_FOCUS_MOVE_CALLBACK, cb).sendToTarget();
            mSig.block();
        }
        
        public void enableShutterSound(boolean enable) {
        	mSig.close();
        	mCameraHandler.obtainMessage(
                    ENABLE_SHUTTER_SOUND, (enable ? 1 : 0), 0).sendToTarget();
        	mSig.block();

        }
        
        public void waitForIdle() {
            mSig.close();
            mCameraHandler.sendEmptyMessage(WAIT_FOR_IDLE);
            mSig.block();
        }

		
	}
}
