package com.snapandswap;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.FloatMath;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.OrientationEventListener;
import android.view.Surface;

import com.snapandswap.CameraManager.CameraProxy;

import java.util.List;

@SuppressLint({ "Recycle", "FloatMath" })
public class Util {
	public static final int ORIENTATION_HYSTERESIS = 5;
	
	public static final String TRUE = "true";
    public static final String FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture";
    private static final String AUTO_EXPOSURE_LOCK_SUPPORTED = "auto-exposure-lock-supported";
    private static final String AUTO_WHITE_BALANCE_LOCK_SUPPORTED = "auto-whitebalance-lock-supported";
    
    public static final String SCENE_MODE_HDR = "hdr";
    
    public static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }
    
	public static int getDisplayRotation(Activity activity) {
		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		switch (rotation) {
		case Surface.ROTATION_0: return 0;
		case Surface.ROTATION_90: return 90;
		case Surface.ROTATION_180: return 180;
		case Surface.ROTATION_270: return 270;
		}
		return 0;
	}

	public static int getDisplayOrientation(int degrees, int cameraId) {
		// See android.hardware.Camera.setDisplayOrientation for
		// documentation.
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		} else {  // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}
	
	public static int getCWOrientation(Activity activity, int degrees) {
		int result  = (getDisplayRotation(activity) + degrees + 360) % 360;
		return result;		
	}

	public static int getCameraOrientation(int cameraId) {
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		return info.orientation;
	}

	public static int getJpegRotation(int cameraId, int orientation) {
		// See android.hardware.Camera.Parameters.setRotation for
		// documentation.
		int rotation = 0;
		if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
			CameraInfo info = CameraHolder.getInstances().getCameraInfo()[cameraId];
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				rotation = (info.orientation - orientation + 360) % 360;
			} else {  // back-facing camera
				rotation = (info.orientation + orientation) % 360;
			}
		}
		return rotation;
	}

	public static int roundOrientation(int orientation, int orientationHistory) {
		boolean changeOrientation = false;
		if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
			changeOrientation = true;
		} else {
			int dist = Math.abs(orientation - orientationHistory);
			dist = Math.min( dist, 360 - dist );
			changeOrientation = ( dist >= 45 + ORIENTATION_HYSTERESIS );
		}
		if (changeOrientation) {
			return ((orientation + 45) / 90 * 90) % 360;
		}
		return orientationHistory;
	}

	public static void Assert(boolean cond) {
		if (!cond) {
			throw new AssertionError();
		}
	}
	
	@TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void throwIfCameraDisabled(Activity activity) throws CameraDisabledException {
		// Check if device policy has disabled the camera.
		if (ApiHelper.HAS_GET_CAMERA_DISABLED) {
			DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(
					Context.DEVICE_POLICY_SERVICE);
			if (dpm.getCameraDisabled(null)) {
				throw new CameraDisabledException();
			}
		}
	}

	public static CameraProxy openCamera(Activity activity, int cameraId)
			throws CameraHardwareException, CameraDisabledException {
		throwIfCameraDisabled(activity);
		try {
			return CameraHolder.getInstances().open(cameraId);
		} catch (CameraHardwareException e) {
			e.printStackTrace();
		}
		return null;
	}
	
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void showErrorAndFinish(final Activity activity, int msgId) {
        DialogInterface.OnClickListener buttonListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                activity.finish();
            }
        };
        TypedValue out = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.alertDialogIcon, out, true);
        new AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(R.string.camera_error_title)
                .setMessage(msgId)
                .setNeutralButton(R.string.dialog_ok, buttonListener)
                .setIcon(out.resourceId)
                .show();
    }

	public static boolean isAutoExposureLockSupported(Parameters params) {
		return TRUE.equals(params.get(AUTO_EXPOSURE_LOCK_SUPPORTED));
	}

	public static boolean isAutoWhiteBalanceLockSupported(Parameters params) {
		return TRUE.equals(params.get(AUTO_WHITE_BALANCE_LOCK_SUPPORTED));
	}

	public static boolean isAutoFocusSupported(Parameters parameters) {
		List<String> mParamters = parameters.getSupportedFocusModes();
		return (mParamters != null) && mParamters.contains(Parameters.FOCUS_MODE_AUTO);
	}

	@TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
	public static boolean isMeteringAreaSupported(Parameters parameters) {
		if (ApiHelper.HAS_CAMERA_METERING_AREA) {
			return parameters.getMaxNumMeteringAreas() > 0;
		}
		return false;
	}

	@TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
	public static boolean isFocusAreaSupported(Parameters parameters) {
		if (ApiHelper.HAS_CAMERA_METERING_AREA) {
			return parameters.getMaxNumFocusAreas() > 0;
		}
		return false;
	}
	
    public static MotionEvent transformEvent(MotionEvent e, Matrix m) {
        // We try to use the new transform method if possible because it uses
        // less memory.
        if (ApiHelper.HAS_MOTION_EVENT_TRANSFORM) {
            return transformEventNew(e, m);
        } else {
            return transformEventOld(e, m);
        }
    }
    
    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static MotionEvent transformEventNew(MotionEvent e, Matrix m) {
        MotionEvent newEvent = MotionEvent.obtain(e);
        newEvent.transform(m);
        return newEvent;
    }

    // This is copied from Input.cpp in the android framework.
    private static MotionEvent transformEventOld(MotionEvent e, Matrix m) {
        long downTime = e.getDownTime();
        long eventTime = e.getEventTime();
        int action = e.getAction();
        int pointerCount = e.getPointerCount();
        int[] pointerIds = getPointerIds(e);
        PointerCoords[] pointerCoords = getPointerCoords(e);
        int metaState = e.getMetaState();
        float xPrecision = e.getXPrecision();
        float yPrecision = e.getYPrecision();
        int deviceId = e.getDeviceId();
        int edgeFlags = e.getEdgeFlags();
        int source = e.getSource();
        int flags = e.getFlags();

        // Copy the x and y coordinates into an array, map them, and copy back.
        float[] xy = new float[pointerCoords.length * 2];
        for (int i = 0; i < pointerCount;i++) {
            xy[2 * i] = pointerCoords[i].x;
            xy[2 * i + 1] = pointerCoords[i].y;
        }
        m.mapPoints(xy);
        for (int i = 0; i < pointerCount;i++) {
            pointerCoords[i].x = xy[2 * i];
            pointerCoords[i].y = xy[2 * i + 1];
            pointerCoords[i].orientation = transformAngle(
                m, pointerCoords[i].orientation);
        }

        @SuppressWarnings("deprecation")
		MotionEvent n = MotionEvent.obtain(downTime, eventTime, action,
                pointerCount, pointerIds, pointerCoords, metaState, xPrecision,
                yPrecision, deviceId, edgeFlags, source, flags);

        return n;
    }
    
    private static int[] getPointerIds(MotionEvent e) {
        int n = e.getPointerCount();
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = e.getPointerId(i);
        }
        return r;
    }
    
    private static PointerCoords[] getPointerCoords(MotionEvent e) {
        int n = e.getPointerCount();
        PointerCoords[] r = new PointerCoords[n];
        for (int i = 0; i < n; i++) {
            r[i] = new PointerCoords();
            e.getPointerCoords(i, r[i]);
        }
        return r;
    }

    private static float transformAngle(Matrix m, float angleRadians) {
        // Construct and transform a vector oriented at the specified clockwise
        // angle from vertical.  Coordinate system: down is increasing Y, right is
        // increasing X.
        float[] v = new float[2];
        v[0] = FloatMath.sin(angleRadians);
        v[1] = -FloatMath.cos(angleRadians);
        m.mapVectors(v);

        // Derive the transformed vector's clockwise angle from vertical.
        float result = (float) Math.atan2(v[0], -v[1]);
        if (result < -Math.PI / 2) {
            result += Math.PI;
        } else if (result > Math.PI / 2) {
            result -= Math.PI;
        }
        return result;
    }
    
    public static Size getOptimalPreviewSize(Activity currentActivity,
            List<Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of preview surface. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size.
        Point point = getDefaultDisplaySize(currentActivity, new Point());
        int targetHeight = Math.min(point.x, point.y);
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w(CameraActivity.TAG, "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private static Point getDefaultDisplaySize(Activity activity, Point size) {
        Display d = activity.getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.HONEYCOMB_MR2) {
            d.getSize(size);
        } else {
            size.set(d.getWidth(), d.getHeight());
        }
        return size;
    }
    
    public static <T> T checkNotNull(T object) {
        if (object == null) throw new NullPointerException();
        return object;
    }
    
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a == null ? false : a.equals(b));
    }

    public static boolean isCameraHdrSupported(Parameters params) {
        List<String> supported = params.getSupportedSceneModes();
        return (supported != null) && supported.contains(SCENE_MODE_HDR);
    }

    public static void prepareMatrix(Matrix matrix, int displayOrientation,
                                     int viewWidth, int viewHeight) {
        matrix.setScale(1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }


    public static void rectFToRect(RectF rectF, Rect rect) {
        rect.left = Math.round(rectF.left);
        rect.top = Math.round(rectF.top);
        rect.right = Math.round(rectF.right);
        rect.bottom = Math.round(rectF.bottom);
    }
}
