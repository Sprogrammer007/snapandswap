package com.snapandswap;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.FloatMath;
import android.util.Log;

import java.util.List;

public class CameraSettings {
	private static final int NOT_FOUND = -1;

	public static final String KEY_VERSION = "pref_version_key";
	public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
	public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
	public static final String KEY_IMAGE_QUALITY = "pref_camera_imagequality_key";
	public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
	public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
	public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
	public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
	public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
	public static final String KEY_CAMERA_ID = "pref_camera_id_key";
	public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
	public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN = "pref_camera_first_use_hint_shown_key";
	public static final String KEY_CAMERA_SHUTTER_SOUND = "pref_camera_shutter_sound";
	public static final String SHUTTER_SOUND_ON = "on";
    public static final String KEY_CAMERA_FOCUS_SOUND = "pref_camera_focus_sound";
	public static final String FOCUS_SOUND_ON = "on";


	private final Context mContext;
    private final Parameters mParameters;
    private final CameraInfo[] mCameraInfo;
    private final int mCameraId;

    public CameraSettings(Activity activity, Parameters parameters,
                          int cameraId, CameraInfo[] cameraInfo) {
        mContext = activity;
        mParameters = parameters;
        mCameraId = cameraId;
        mCameraInfo = cameraInfo;
    }
    
    public PreferenceGroup getPreferenceGroup(int preferenceRes) {
        PreferenceInflater inflater = new PreferenceInflater(mContext);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(preferenceRes);
        if (mParameters != null) initPreference(group);
        return group;
    }
    
    private void initPreference(PreferenceGroup group) {

        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        IconListPreference exposure =
                (IconListPreference) group.findPreference(KEY_EXPOSURE);
        IconListPreference cameraIdPref =
                (IconListPreference) group.findPreference(KEY_CAMERA_ID);
        ListPreference cameraHdr = group.findPreference(KEY_CAMERA_HDR);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here

//        if (pictureSize != null) {
//            filterUnsupportedOptions(group, pictureSize, sizeListToStringList(
//                    mParameters.getSupportedPictureSizes()));
//            filterSimilarPictureSize(group, pictureSize);
//        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(group,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (sceneMode != null) {
            filterUnsupportedOptions(group,
                    sceneMode, mParameters.getSupportedSceneModes());
        }
        if (flashMode != null) {
            filterUnsupportedOptions(group,
                    flashMode, mParameters.getSupportedFlashModes());
        }
        if (focusMode != null) {
            if (!Util.isFocusAreaSupported(mParameters)) {
                filterUnsupportedOptions(group,
                        focusMode, mParameters.getSupportedFocusModes());
            } else {
                // Remove the focus mode if we can use tap-to-focus.
                removePreference(group, focusMode.getKey());
            }
        }
    
        if (exposure != null) buildExposureCompensation(group, exposure);
        if (cameraIdPref != null) buildCameraId(group, cameraIdPref);

//        if (timeLapseInterval != null) {
//            if (ApiHelper.HAS_TIME_LAPSE_RECORDING) {
//                resetIfInvalid(timeLapseInterval);
//            } else {
//                removePreference(group, timeLapseInterval.getKey());
//            }
//        }
       
        if (cameraHdr != null && (!ApiHelper.HAS_CAMERA_HDR
                    || !Util.isCameraHdrSupported(mParameters))) {
            removePreference(group, cameraHdr.getKey());
        }
    }
    
    private void filterUnsupportedOptions(PreferenceGroup group,
            ListPreference pref, List<String> supported) {

        // Remove the preference if the parameter is not supported or there is
        // only one options for the settings.
        if (supported == null || supported.size() <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        resetIfInvalid(pref);
    }

    private void resetIfInvalid(ListPreference pref) {
        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == NOT_FOUND) {
            pref.setValueIndex(0);
        }
    }
    
    private void buildExposureCompensation(
            PreferenceGroup group, IconListPreference exposure) {
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (max == 0 && min == 0) {
            removePreference(group, exposure.getKey());
            return;
        }
        float step = mParameters.getExposureCompensationStep();

        // show only integer values for exposure compensation
        int maxValue = (int) FloatMath.floor(max * step);
        int minValue = (int) FloatMath.ceil(min * step);
        CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
        CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
        int[] icons = new int[maxValue - minValue + 1];
//        TypedArray iconIds = mContext.getResources().obtainTypedArray(
//                R.array.pref_camera_exposure_icons);
        for (int i = minValue; i <= maxValue; ++i) {
            entryValues[maxValue - i] = Integer.toString(Math.round(i / step));
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            entries[maxValue - i] = builder.append(i).toString();
//            icons[maxValue - i] = iconIds.getResourceId(3 + i, 0);
        }
        exposure.setUseSingleIcon(true);
        exposure.setEntries(entries);
        exposure.setEntryValues(entryValues);
        exposure.setLargeIconIds(icons);
    }
    
    private void buildCameraId(
            PreferenceGroup group, IconListPreference preference) {
        int numOfCameras = mCameraInfo.length;
        if (numOfCameras < 2) {
            removePreference(group, preference.getKey());
            return;
        }

        CharSequence[] entryValues = new CharSequence[numOfCameras];
        for (int i = 0; i < numOfCameras; ++i) {
            entryValues[i] = "" + i;
        }
        preference.setEntryValues(entryValues);
    }
    
    private static boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }
    
    public static void initialCameraPictureSize(
            Context context, Parameters parameters) {
        // When launching the camera app first time, we will set the picture
        // size to the first one in the list defined in "arrays.xml" and is also
        // supported by the driver.
        List<Size> supported = parameters.getSupportedPictureSizes();
        if (supported == null) return;
        for (String candidate : context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues)) {
            if (setCameraPictureSize(candidate, supported, parameters)) {
                SharedPreferences.Editor editor = UserPreferences.get(context).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.apply();
                return;
            }
        }
        Log.e(CameraActivity.TAG, "No supported picture size found");
    }

    public static void removePreferenceFromScreen(
            PreferenceGroup group, String key) {
        removePreference(group, key);
    }

    public static boolean setCameraPictureSize(
            String candidate, List<Size> supported, Parameters parameters) {
        int index = candidate.indexOf('x');
        if (index == NOT_FOUND) return false;
        int width = Integer.parseInt(candidate.substring(0, index));
        int height = Integer.parseInt(candidate.substring(index + 1));
        for (Size size : supported) {
            if (size.width == width && size.height == height) {
                parameters.setPictureSize(width, height);
                return true;
            }
        }
        return false;
    }
}
