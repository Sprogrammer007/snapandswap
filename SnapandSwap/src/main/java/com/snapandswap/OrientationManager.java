package com.snapandswap;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.provider.Settings;
import android.util.Log;
import android.view.OrientationEventListener;
import android.widget.Toast;

public class OrientationManager{

	private Activity mActivity;
	private boolean mOrientationLocked;
	private OrientationManager.Listener mListener;
	private MyOrientationEventListener mOrientationListener;
	// This is true if "Settings -> Display -> Rotation Lock" is checked. We
	// don't allow the orientation to be unlocked if the value is true.
	@SuppressWarnings("unused")
	private boolean mRotationLockedSetting = false;

	public interface Listener{
		public void onOrientationChange(int orientation);
	}

	public OrientationManager(Activity activity) {
		mActivity = activity;
		mOrientationListener = new MyOrientationEventListener(activity);
	}

	public void resume() {
		ContentResolver resolver = mActivity.getContentResolver();
		mRotationLockedSetting = Settings.System.getInt(
				resolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 1;
		if (mOrientationListener.canDetectOrientation()){
			mOrientationListener.enable();
		}else{
			Toast.makeText(mActivity, "Can't DetectOrientation", Toast.LENGTH_LONG).show();
		}
	}

	public void pause() {
		mOrientationListener.disable();
	}

	 // Lock the framework orientation to the current device orientation
    public void lockOrientation() {
        if (mOrientationLocked) return;
        mOrientationLocked = true;
        mActivity.setRequestedOrientation(calculateCurrentScreenOrientation());
    }

    // Unlock the framework orientation, so it can change when the device
    // rotates.
    public void unlockOrientation() {
        if (!mOrientationLocked) return;
        mOrientationLocked = false;
        Log.d(CameraActivity.TAG, "unlock orientation");
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private int calculateCurrentScreenOrientation() {
        int displayRotation = Util.getDisplayRotation(mActivity);
        // Display rotation >= 180 means we need to use the REVERSE landscape/portrait
        boolean standard = displayRotation < 180;
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else {
            if (displayRotation == 90 || displayRotation == 270) {
                // If displayRotation = 90 or 270 then we are on a landscape
                // device. On landscape devices, portrait is a 90 degree
                // clockwise rotation from landscape, so we need
                // to flip which portrait we pick as display rotation is counter clockwise
                standard = !standard;
            }
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        }
    }
    
    public void setOrientationChangeListener(OrientationManager.Listener listener) {
    	mListener = listener;
    }
    
    /*	My inner orientation listener class that OrientationEventListener
     * 	and forward the information from the OrientationEventListener to
     * 	my main activity.
     */
    
    private class MyOrientationEventListener
    extends OrientationEventListener {
    	public MyOrientationEventListener(Context context) {
    		super(context);
    	}

    	@Override
    	public void onOrientationChanged(int orientation) {
    		if (mListener == null) return;
			mListener.onOrientationChange(orientation);
    	}
    }
}
