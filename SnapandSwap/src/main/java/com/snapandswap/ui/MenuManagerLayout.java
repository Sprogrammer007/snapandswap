package com.snapandswap.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;

import com.snapandswap.CameraSettings;
import com.snapandswap.IconListPreference;
import com.snapandswap.PopUpManager;
import com.snapandswap.PreferenceGroup;
import com.snapandswap.R;

public class MenuManagerLayout extends RelativeLayout implements
			OnClickListener, Rotatable{
	private static final String TAG = "MenuLayout";
	
	private RotatableImageView mButton1;
	private RotatableImageView mButton2;
	private RotatableImageView mButton3;
	private RotatableImageView mButton4;
	private RotatableImageView mButton5;
	
	private PreferenceGroup mPrefGroup;
	private PopUpManager mPopup;

    public MenuManagerLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		//Initialize all settings views
		mButton1 =(RotatableImageView) findViewById(R.id.resolution);
		mButton2 = (RotatableImageView) findViewById(R.id.flashlight);
		mButton3  = (RotatableImageView) findViewById(R.id.exposure);
		mButton4 =(RotatableImageView) findViewById(R.id.imgquality);
		mButton5 =(RotatableImageView) findViewById(R.id.settings);

		mButton3.setImageResource(R.drawable.camera_mode_exposure);
		mButton5.setImageResource(R.drawable.settingsbtn);

        mButton1.setOnClickListener(this);
        mButton2.setOnClickListener(this);
        mButton3.setOnClickListener(this);
        mButton4.setOnClickListener(this);
        mButton5.setOnClickListener(this);
	}

    public RotatableImageView findButtonByID() {
        return mButton3;
    }
	
	public void setPreferenceGroup (PreferenceGroup pref) {
		mPrefGroup = pref;
	}
	
	public void addPopup(PopUpManager popup) {
		mPopup = popup;
	}
	
	public void updateImageButtonDrawable(String[] keys, boolean seletable) {
		if (mPrefGroup == null) return;

		IconListPreference pref;
		int icons[];
		int index;
		for (int i = 0; i < keys.length; i++) {
            if ( mPrefGroup.findPreference(keys[i]) instanceof  IconListPreference) {
			pref = (IconListPreference) mPrefGroup.findPreference(keys[i]);
            } else {
                continue;
            }
			icons = pref.getIconIds();
			index = pref.findIndexOfValue(pref.getValue());
			if (keys[i].equals(CameraSettings.KEY_PICTURE_SIZE)) {
				if (index != -1) {
					mButton1.setImageResource(icons[index]);
				} else {
					Log.e(TAG, "Invalid preference value.");
					pref.print();
				}
			} else if (keys[i].equals(CameraSettings.KEY_FLASH_MODE)) {
				if (index != -1) {
					mButton2.setImageResource(icons[index]);
				} else {
					Log.e(TAG, "Invalid preference value.");
					pref.print();
				}
			} else if (keys[i].equals(CameraSettings.KEY_IMAGE_QUALITY)) {
				if (index != -1) {
					mButton4.setImageResource(icons[index]);
				} else {
					Log.e(TAG, "Invalid preference value.");
					pref.print();
				}
			}
		}
        updateSeletable(seletable);
	}

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateSeletable(boolean seletable) {
        if (!seletable) {
            mButton2.setAlpha(0.6f);
            mButton2.setEnabled(false);
            mButton3.setAlpha(0.6f);
            mButton3.setEnabled(false);
        } else {
            mButton2.setAlpha(1.0f);
            mButton2.setEnabled(true);
            mButton3.setAlpha(1.0f);
            mButton3.setEnabled(true);
        }
    }


    @Override
	public void onClick(View v) {
		if (mPopup == null) return;
		switch (v.getId()) {
		case R.id.resolution:
			mPopup.dismissCurrentPopup();
			mPopup.initSinglePref(
                    mPrefGroup.findPreference(CameraSettings.KEY_PICTURE_SIZE));
			mPopup.show(v);
			break;

		case R.id.flashlight:
			mPopup.dismissCurrentPopup();
			mPopup.initSinglePref(
                    mPrefGroup.findPreference(CameraSettings.KEY_FLASH_MODE));
			mPopup.show(v);
			break;
		case R.id.imgquality:
			mPopup.dismissCurrentPopup();
			mPopup.initSinglePref(
                    mPrefGroup.findPreference(CameraSettings.KEY_IMAGE_QUALITY));
			mPopup.show(v);
			break;
		case R.id.exposure:
			mPopup.dismissCurrentPopup();
            mPopup.initSinglePref(
                    mPrefGroup.findPreference(CameraSettings.KEY_EXPOSURE));
			mPopup.show(v);
			break;

        case R.id.settings:
            mPopup.dismissCurrentPopup();
            mPopup.initMultiPref(mPrefGroup);
            mPopup.show(v);
            break;

            default:
			break;
		}
	}

	@Override
	public void setOrientation(int orientation, boolean animation) {
		mButton1.setOrientation(orientation, animation);
		mButton2.setOrientation(orientation, animation);
		mButton3.setOrientation(orientation, animation);
		mButton4.setOrientation(orientation, animation);
		mButton5.setOrientation(orientation, animation);
	}

}
