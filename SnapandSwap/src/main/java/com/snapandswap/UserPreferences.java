package com.snapandswap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class UserPreferences implements SharedPreferences, 
	OnSharedPreferenceChangeListener{

    private static final String TAG = "UserPreferences";
    private SharedPreferences mLocalPref;
	private SharedPreferences mGlobalPref;
	 private static WeakHashMap<Context, UserPreferences> sMap =
	            new WeakHashMap<Context, UserPreferences>();
	 
	public UserPreferences(Context context) {
		mGlobalPref = PreferenceManager.getDefaultSharedPreferences(context);
		mGlobalPref.registerOnSharedPreferenceChangeListener(this);
		synchronized (sMap) {
			sMap.put(context, this);
		}
	}
	  // Sets the camera id and reads its preferences. Each camera has its own
    // preferences.
    public void setLocalId(Context context, int cameraId) {
        String prefName = context.getPackageName() + "_preferences_" + cameraId;
        if (mLocalPref != null) {
        	mLocalPref.unregisterOnSharedPreferenceChangeListener(this);
        }
        mLocalPref = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        mLocalPref.registerOnSharedPreferenceChangeListener(this);
    }
    
	public static UserPreferences get(Context context) {
		synchronized (sMap) {
			return sMap.get(context);
		}
	}

	public SharedPreferences getGlobal() {
		return mGlobalPref;
	}

	public SharedPreferences getLocal() {
		return mLocalPref;
	}

	private static boolean isGlobal(String key) {
		return key.equals(CameraSettings.KEY_CAMERA_ID)
				|| key.equals(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN);
	}

	@Override
	public boolean contains(String key) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Editor edit() {
		return new MyEditor();
	}

	@Override
	public Map<String, ?> getAll() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getBoolean(String key, boolean defValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public float getFloat(String key, float defValue) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String key, int defValue) {
		if (isGlobal(key) || !mLocalPref.contains(key)) {
			return mGlobalPref.getInt(key, defValue);
		} else {
			return mLocalPref.getInt(key, defValue);
		}
	}

	@Override
	public long getLong(String key, long defValue) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getString(String key, String defValue) {
		if (isGlobal(key) || !mLocalPref.contains(key)) {
			return mGlobalPref.getString(key, defValue);
		} else {
			return mLocalPref.getString(key, defValue);
		}
	}

	@Override
	public Set<String> getStringSet(String arg0, Set<String> arg1) {
		// TODO Auto-generated method stub
		return null;
	}
	
    private class MyEditor implements Editor {
        private Editor mEditorGlobal;
        private Editor mEditorLocal;

        MyEditor() {
            mEditorGlobal = mGlobalPref.edit();
            mEditorLocal = mLocalPref.edit();
        }

        @Override
        public boolean commit() {
            boolean result1 = mEditorGlobal.commit();
            boolean result2 = mEditorLocal.commit();
            return result1 && result2;
        }

        @Override
        public void apply() {
            mEditorGlobal.apply();
            mEditorLocal.apply();
        }

        // Note: clear() and remove() affects both local and global preferences.
        @Override
        public Editor clear() {
            mEditorGlobal.clear();
            mEditorLocal.clear();
            return this;
        }

        @Override
        public Editor remove(String key) {
            mEditorGlobal.remove(key);
            mEditorLocal.remove(key);
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
//            if (isGlobal(key)) {
                mEditorGlobal.putString(key, value);
//            } else {
                mEditorLocal.putString(key, value);
//            }
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            if (isGlobal(key)) {
                mEditorGlobal.putInt(key, value);
            } else {
                mEditorLocal.putInt(key, value);
            }
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            if (isGlobal(key)) {
                mEditorGlobal.putLong(key, value);
            } else {
                mEditorLocal.putLong(key, value);
            }
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            if (isGlobal(key)) {
                mEditorGlobal.putFloat(key, value);
            } else {
                mEditorLocal.putFloat(key, value);
            }
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            if (isGlobal(key)) {
                mEditorGlobal.putBoolean(key, value);
            } else {
                mEditorLocal.putBoolean(key, value);
            }
            return this;
        }

        // This method is not used.
        @Override
        public Editor putStringSet(String key, Set<String> values) {
            throw new UnsupportedOperationException();
        }
    }

	@Override
	public void registerOnSharedPreferenceChangeListener(
			OnSharedPreferenceChangeListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unregisterOnSharedPreferenceChangeListener(
			OnSharedPreferenceChangeListener listener) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// TODO Auto-generated method stub
		
	}

}
