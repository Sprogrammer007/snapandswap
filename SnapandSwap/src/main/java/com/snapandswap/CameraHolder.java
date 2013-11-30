package com.snapandswap;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.snapandswap.CameraManager.CameraProxy;

import java.io.IOException;

public class CameraHolder {

	private CameraProxy mCamera;
	private boolean mCameraOpened;  // true if camera is opened
	private final int mNumberOfCameras;
	private static final int KEEP_CAMERA_TIMEOUT = 3000; // 3 seconds
	private long mKeepBeforeTime; 
	private int mCameraId = -1;  // current camera id
	private int mBackCameraId = -1;
	private int mFrontCameraId = -1;
	private final CameraInfo[] mInfo;
	private Camera.Parameters mParameters;
	private final Handler mHandler;

	private CameraHolder(){
		HandlerThread ht = new HandlerThread("CameraHolder");
		ht.start();
		mHandler = new MyHandler(ht.getLooper());
		mNumberOfCameras = Camera.getNumberOfCameras();
		mInfo = new CameraInfo[mNumberOfCameras];
		for (int i = 0; i < mNumberOfCameras; i++) {
			mInfo[i] = new CameraInfo();
			Camera.getCameraInfo(i, mInfo[i]);
			if (mBackCameraId == -1 && mInfo[i].facing == CameraInfo.CAMERA_FACING_BACK) {
				mBackCameraId = i;
			} else if (mFrontCameraId == -1 && mInfo[i].facing == CameraInfo.CAMERA_FACING_FRONT) {
				mFrontCameraId = i;
			}
		}
	}

	private static CameraHolder mHolder;
	public synchronized static CameraHolder getInstances() {
		if (mHolder == null) {
			mHolder = new CameraHolder();
		}
		return mHolder;
	}

	private static final int RELEASE_CAMERA = 1;
	@SuppressLint("HandlerLeak")
	private class MyHandler extends Handler {
		MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case RELEASE_CAMERA:
				synchronized (CameraHolder.this) {
					// In 'CameraHolder.open', the 'RELEASE_CAMERA' message
					// will be removed if it is found in the queue. However,
					// there is a chance that this message has been handled
					// before being removed. So, we need to add a check
					// here:
		
					if (!mCameraOpened) release();
				}
				break;
			}
		}
	}
	
	public synchronized CameraProxy open(int cameraId) 
			throws CameraHardwareException {
		if (mCamera != null && mCameraId != cameraId) {
			mCamera.release();
			mCamera = null;
			mCameraId = -1;
		}

		Assert(!mCameraOpened);
		if (mCamera == null) {
			try {
				Log.v(CameraActivity.TAG, "open camera " + cameraId);
				mCamera = CameraManager.getInstances().openCamera(cameraId);
				mCameraId = cameraId;
			} catch (RuntimeException e) {
				Log.e(CameraActivity.TAG, "fail to connect Camera", e);
				throw new CameraHardwareException(e);
			}
			mParameters = mCamera.getParameters();
		} else {
			try {
				mCamera.reconnect();
			} catch (IOException e) {
				Log.e(CameraActivity.TAG, "reconnect failed.");
				throw new CameraHardwareException(e);
			}
			mCamera.setParameters(mParameters);
		}
		mCameraOpened = true;
		mHandler.removeMessages(RELEASE_CAMERA);
		mKeepBeforeTime = 0;
		return mCamera;
	}

	public synchronized void release() {
		if (mCamera == null) return;

		long now = System.currentTimeMillis();
		if (now < mKeepBeforeTime) {
			if (mCameraOpened) {
				mCameraOpened = false;
				mCamera.stopPreview();
			}
			mHandler.sendEmptyMessageDelayed(RELEASE_CAMERA,
					mKeepBeforeTime - now);
			return;
		}
		mCameraOpened = false;
		mCamera.release();
		// We must set this to null because it has a reference to Camera.
		// Camera has references to the listeners.
		mCamera = null;
		mParameters = null;
		mCameraId = -1;
	}

	

	public static void Assert(boolean cond) {
		if (!cond) {
			throw new AssertionError();
		}
	}
	
	public void keep() {
		keep(KEEP_CAMERA_TIMEOUT);
	}
	
	public synchronized void keep(int time) {
		// We allow mCameraOpened in either state for the convenience of the
		// calling activity. The activity may not have a chance to call open()
		// before the user switches to another activity.
		mKeepBeforeTime = System.currentTimeMillis() + time;
	}

	public CameraInfo[] getCameraInfo() {
		return mInfo;
	}

	public int getNumberOfCameras() {
		return mNumberOfCameras;
	}

	public int getCurrentCameraId() {
		return mCameraId;
	}

	public int getBackCameraId() {
		return mBackCameraId;
	}

	public int getFrontCameraId() {
		return mFrontCameraId;
	}

	public CameraProxy getCameraProxy() {
		if (mCamera != null) {
			return mCamera;
		}
		return null;
	}
}
