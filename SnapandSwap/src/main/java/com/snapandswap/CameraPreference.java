package com.snapandswap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/**
 * The base class of all Preferences used in Camera. The preferences can be
 * loaded from XML resource by <code>PreferenceInflater</code>.
 */
public abstract class CameraPreference {

    private final String mTitle;
    private SharedPreferences mSharedPreferences;
    private final Context mContext;

    static public interface OnPreferenceChangedListener {
        public void onSharedPreferenceChanged();
        public void onRestorePreferencesClicked();
        public void onOverriddenPreferencesClicked();
        public void onCameraPickerClicked(int cameraId);
    }

    public CameraPreference(Context context, AttributeSet attrs) {
        mContext = context;
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CameraPreference, 0, 0);
        mTitle = a.getString(R.styleable.CameraPreference_title);
        a.recycle();
    }

    public String getTitle() {
        return mTitle;
    }

    public SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null) {
            mSharedPreferences = UserPreferences.get(mContext);
        }
        return mSharedPreferences;
    }

    public abstract void reloadValue();
}
