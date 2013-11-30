package com.snapandswap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.PopupWindow.OnDismissListener;

import com.snapandswap.ui.AbstractSettingPopup;
import com.snapandswap.ui.MenuManagerLayout;
import com.snapandswap.ui.MultiSettingPopup;
import com.snapandswap.ui.PopupWindows;
import com.snapandswap.ui.SeekBarLayout;
import com.snapandswap.ui.SingleSettingPopup;
import com.snapandswap.ui.VerticalSeekBar;

public class PopUpManager extends PopupWindows implements
			 OnDismissListener {

    private final static int MAXROOTHEIGHT = 650;

    private final static int DO_NOT_ROATE = 1;
    private final static int DO_ROATE = 2;
    private int mState = DO_ROATE;

    private SingleSettingPopup mSingleLayout;
	private MultiSettingPopup mMultiLayout;
	private ViewGroup mArrowRoot;
	private ViewGroup mZoomRoot;
	
	private LayoutInflater mInflater;

	private CameraActivity.CameraListenerHelper mListener;
    private MyMultiSettingsListener mMultiListener;

    private int mOrientation;
	private int xPos;
	private int yPos;
	private int screenWidth;
	private int screenHeight;
	private int rootHeight;

	private Rect anchorRect;
	
	private ListPreference mPref = null;
	private Handler mHandler;	

    private final SeekBarChangeHelper sListener =
    new SeekBarChangeHelper();
	private SeekBarLayout sBLayout;
	private View mCurAnchor = null;
	private int mWhichWindow = -1;

    //field for popup2
    private int xPosW2;
    private int yPosW2;
    private View mAnchorW2;
    private AdapterView<?> mParent;
    private MenuManagerLayout mMenu;

    public PopUpManager(Context context) {
    	super(context);
		mHandler = new PopupHandler();
        mListener = CameraActivity.getCameraListener();
        mMultiListener = new MyMultiSettingsListener();
        init(context);
        setWindow2OnTouchListener(mMultiListener);
    }


    @SuppressLint("HandlerLeak")
	private class PopupHandler extends Handler {
	
		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
			case POPUP_DISMISS:
                isRotating = false;
				mWindow.dismiss();	
				break;
			case DISABLE_FOCUS:
                if (mWindow.isFocusable()) {
                    mWindow.setFocusable(false);
                    mWindow.update();
                }

                if (mWindow2.isFocusable()) {
                    mWindow2.setFocusable(false);
                    mWindow2.update();
                }
				break;

            case ROTATE_POPUP_ASYNC:
                mSingleLayout.setOrientation(mOrientation, false);
                mMultiLayout.setOrientation(mOrientation, false);
                measureXYPosition();
                if ((mSingleLayout.getMeasuredHeight() > 700)
                        || (mMultiLayout.getMeasuredHeight() > 700)) {
                    mWindow.setHeight(MAXROOTHEIGHT);
                    mWindow.showAtLocation(mCurAnchor, Gravity.NO_GRAVITY, xPos, yPos);
                } else {
                    mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
                    mWindow.showAtLocation(mCurAnchor, Gravity.NO_GRAVITY, xPos, yPos);
                }
                break;
            case ROTATE_POPUP2_ASYNC:
                measureXYWindow2(mParent, mAnchorW2);
                if ((mSingleLayout.getMeasuredHeight() > 700)
                        || (mMultiLayout.getMeasuredHeight() > 700)) {
                    mWindow2.setHeight(MAXROOTHEIGHT);
                    mWindow2.showAtLocation(mCurAnchor, Gravity.NO_GRAVITY, xPosW2, yPosW2);
                } else {
                    mWindow2.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
                    mWindow2.showAtLocation(mCurAnchor, Gravity.NO_GRAVITY, xPosW2, yPosW2);
                }
                break;
			default:
				break;
			}
		}
	}

    private void init(Context context) {
    	mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	
      	mRootViewSingle = (ViewGroup) mInflater.inflate(R.layout.setting_single, null);
        mRootViewSingle.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mRootViewMulti  = (ViewGroup) mInflater.inflate(R.layout.setting_multi, null);
        mRootViewMulti.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mZoomRoot = (ViewGroup) mInflater.inflate(R.layout.camerazoom, null);
		mZoomRoot.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		sBLayout = (SeekBarLayout) mZoomRoot.findViewById(R.id.zoomLayout);
		sBLayout.setOnSeekBarChange(sListener);

        mSingleLayout = (SingleSettingPopup) mRootViewSingle.findViewById(R.id.setting_single);
        mSingleLayout.setOnListPrefChangedListener(mListener);

        mMultiLayout = (MultiSettingPopup) mRootViewMulti.findViewById(R.id.setting_multi);
        mMultiLayout.setOnListPrefChangedListener(mMultiListener);
        
    	mArrowRoot = (ViewGroup) mInflater.inflate(R.layout.arrowpopup, null);
    	mArrowPop.setContentView(mArrowRoot);
    }

    private void setState(int state) {
        mState = state;
    }

    public void addMenu(MenuManagerLayout menu) {
        mMenu = menu;
    }
    
    public void initSinglePref(ListPreference pref){
    	if (mPref == pref) return;
    	mPref = pref;
      	mSingleLayout.initialize(pref);
    }

    public void initMultiPref(PreferenceGroup group) {
        if(mMultiLayout.initialize(group)) {
           mMultiLayout.afterinit();
        }
    }


	public void updateOrientation(int orientation) {
		if (mOrientation == orientation) return;
		mOrientation = orientation;
		rotatePopUpWindow();
	}

	private void rotatePopUpWindow() {
        if (!isShowing() || mState == DO_NOT_ROATE) return;
        isRotating = true;
        if (mWindow.isShowing() && !mWindow2.isShowing()) {
            mWindow.dismiss();
            mHandler.sendEmptyMessage(ROTATE_POPUP_ASYNC);
        } else if (mWindow2.isShowing()&& mWindow.isShowing()
                && mParent != null && mAnchorW2 != null) {
            mWindow2.dismiss();
            mWindow.dismiss();
            mHandler.sendEmptyMessage(ROTATE_POPUP_ASYNC);
            mHandler.sendEmptyMessageDelayed(ROTATE_POPUP2_ASYNC, 100);

        }
	}

	public void show (View anchor) {
    	if (mCurAnchor == anchor) {
			mCurAnchor = null;
			return;
		}

		if (mWindow.isShowing()) return;
		mHandler.removeMessages(POPUP_DISMISS);
		mCurAnchor = anchor;

		int[] location 	= new int[2];
		anchor.getLocationOnScreen(location);

		anchorRect 	= new Rect(location[0], location[1],
                location[0] + anchor.getWidth(),
                location[1] + anchor.getHeight());
        boolean toRight;
		switch (anchor.getId()) {
		case R.id.exposure:
			setBackgroundDrawable(mContext.getResources()
					.getDrawable(R.drawable.round_settings_background));
			setContentView(mZoomRoot);
			setWhichRootToMeasure(MEASURE_ZOOM);
            toRight = measureXYPosition();
            setState(DO_NOT_ROATE);
            mHandler.sendEmptyMessageDelayed(POPUP_DISMISS, DELAYED_DISMISS_TIMER);
			break;

        case R.id.settings:
			setBackgroundDrawable(null);
			setContentView(mRootViewMulti);
			setWhichRootToMeasure(MEASURE_LIST_MULTI);
            toRight = measureXYPosition();
            setState(DO_ROATE);
			break;

		default:
			if (mPref != null) {
				setBackgroundDrawable(mContext.getResources()
                        .getDrawable(R.drawable.settings_background));
				setContentView(mRootViewSingle);
				setWhichRootToMeasure(MEASURE_LIST_SINGLE);
                toRight = measureXYPosition();
                setState(DO_ROATE);
			} else {
				throw new NullPointerException("Preference List is not set");
			}
			break;
		}

		int whichArrow = (toRight) ? R.drawable.arrow_right : R.drawable.arrow_left; 
        setAnimationStyle();
		synchronized (mLock) {
			mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
			showArrow(anchor, whichArrow, xPos, anchorRect.centerY());
		}

		rotatePopUpWindow();
	}

	
	private void setWhichRootToMeasure(int whichroot) {
		mWhichWindow = whichroot;
	}

	@SuppressWarnings("deprecation")
	private boolean measureXYPosition() {

        switch (mWhichWindow) {
            case MEASURE_LIST_SINGLE:
                mRootViewSingle.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                rootHeight = mRootViewSingle.getMeasuredHeight();
                break;
            case  MEASURE_LIST_MULTI:
                mRootViewMulti.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                rootHeight = mRootViewMulti.getMeasuredHeight();
                break;
            case MEASURE_ZOOM:
                mZoomRoot.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                rootHeight = mZoomRoot.getMeasuredHeight();
                break;
            default:
                break;
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point screenSize = new Point();
            mWindowManager.getDefaultDisplay().getSize(screenSize);
            screenWidth = screenSize.x;
            screenHeight = screenSize.y;
        } else {
            screenWidth = mWindowManager.getDefaultDisplay().getWidth();
            screenHeight = mWindowManager.getDefaultDisplay().getHeight();
        }

        int dyLeft = anchorRect.left;
        int dyRight = screenHeight - anchorRect.right;

        rootHeight = Math.min(MAXROOTHEIGHT, rootHeight);
        int dyHeight = screenHeight - rootHeight;

        boolean toRight = (dyLeft > dyRight) ? true : false;

        //get xPos
        if (toRight) {
            xPos = anchorRect.left - 27;
        } else {
            xPos = anchorRect.right + 27;
        }


        //get yPos
        if (anchorRect.centerY() - (rootHeight / 2) < 0) {
            yPos = 15;
        } else if (anchorRect.centerY() + (rootHeight / 2) > screenHeight) {
            yPos = dyHeight - 15;
        } else {
            yPos = anchorRect.centerY() - (rootHeight / 2);
        }

        return toRight;
    }


    public void dismissCurrentPopup() {
        isRotating = false;
		if (mWindow.isShowing()) {
			mHandler.removeMessages(POPUP_DISMISS);
			mWindow.dismiss();
		}

        if (mWindow2.isShowing()) {
            mAnchorW2 = null;
            mWindow2.dismiss();
        }
	}

    public void updateMultiPref(ListPreference pref) {
        mMultiLayout.updatePrefSelected(pref);
    }

    public void setSeletable(boolean s) {
        mMultiLayout.setSeletable(s);
    }

	private void showArrow(View anchor, int whichArrow, int x, int y) {
		y -= 24;
		x = (whichArrow == R.drawable.arrow_left) ? x - 27 : x + 27;
		mArrowPop.setBackgroundDrawable(mContext.getResources().getDrawable(whichArrow));
		mArrowPop.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

    }

    public void resetAnchor() {
        mCurAnchor = null;
    }

	private void setAnimationStyle() {
	    mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Center);
	}
	
	
	public void setOnDismissListener(PopupWindows.OnDismissListener listener) {
		mDismissListener = listener;
	}

    public boolean isShowing() {
        return (mWindow.isShowing() || mWindow2.isShowing());
    }


	@Override
	public boolean onTouch(View v, MotionEvent event) {
        if (mWindow.isShowing()) {
            switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!mWindow.isFocusable()) {
                    mWindow.setFocusable(true);
                    mWindow.update();
                }
                break;
            case MotionEvent.ACTION_UP:
                mHandler.sendEmptyMessageDelayed(DISABLE_FOCUS, 500);
                break;
            }
        }
		return false;
	}


    private class MyMultiSettingsListener implements AbstractSettingPopup.Listener,
            View.OnTouchListener{

        @Override
        public void onListPrefChanged(AdapterView<?> parent, View view, int index,
                                   long id, ListPreference pref) {

            if (mAnchorW2 == view) {
                mAnchorW2 = null;
                mWindow2.dismiss();
                return;
            }

            if ( mWindow2.isShowing()) {
                mWindow2.dismiss();
            }

            if (pref.getKey().equals(CameraSettings.KEY_EXPOSURE)) {
                setState(DO_NOT_ROATE);
                isRotating = false;
                mWindow.dismiss();
                if (mWindow2.isShowing()) {
                    mWindow2.dismiss();
                }
                mPref = pref;
                initSinglePref(pref);
                show(mMenu.findButtonByID());
                return;
            }

            initSinglePref(pref);
            mParent = parent;
            mAnchorW2 = view;

            if (pref != null) {
                mWindow2.setBackgroundDrawable(mContext.getResources()
                                    .getDrawable(R.drawable.settings_background_solid));
                mWindow2.setContentView(mRootViewSingle);
            }
            mHandler.sendEmptyMessage(ROTATE_POPUP2_ASYNC);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mWindow2.isShowing()) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!mWindow2.isFocusable()) {
                            mWindow2.setFocusable(true);
                            mWindow2.update();
                        }

                        break;
                    case MotionEvent.ACTION_UP:
                        mHandler.sendEmptyMessageDelayed(DISABLE_FOCUS, 500);
                        break;
                }
            }
            return false;
        }
    }

    public void dismiss() {
        if (mWindow2.isShowing()) {
            mWindow2.dismiss();
        } else {
            isRotating = false;
            setState(DO_ROATE);
            mWindow.dismiss();
            mCurAnchor = null;
        }
    }

    public void dismissAll() {
        isRotating = false;
        if (mWindow.isShowing()){
            mCurAnchor = null;
            setState(DO_ROATE);
            mHandler.removeMessages(POPUP_DISMISS);
            mWindow.dismiss();
            mArrowPop.dismiss();
        }

        if (mWindow2.isShowing()) {
            mWindow2.dismiss();
        }
    }

    private void measureXYWindow2(AdapterView<?> parent, View view) {

        final AdapterView<?> p = parent;
        final View v = view;

        int[] location = new int[2];
        int[] plocation = new int[2];
        v.getLocationOnScreen(location);
        p.getLocationOnScreen(plocation);

        mRootViewSingle.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        int height = mRootViewSingle.getMeasuredHeight();
        int width =mRootViewSingle.getMeasuredWidth();

        int parentLeft = plocation[0];
        int parentTop = plocation[1];
        int h = v.getHeight();
        int w = v.getWidth();

        Rect r;
        switch (mOrientation) {
            case 0:
                r = new Rect(location[0], location[1],
                        location[0] + w,
                        location[1] + h);
                xPosW2 = r.centerX();
                yPosW2 = r.centerY() + (height/2);
                break;
            case 90:
                r = new Rect(location[0], location[1],
                        location[0] + h,
                        location[1] - w);
                if (r.centerX() - (width/2) < parentLeft) {
                    xPosW2 = parentLeft - 20;
                } else {

                    xPosW2 =  r.centerX() - (width/2);

                }
                yPosW2 = 15;
                break;
            case 180:
                r = new Rect(location[0], location[1],
                        location[0] - w,
                        location[1] - h);
                xPosW2 = r.centerX() + 20;
                if (r.centerY() + (height/2) > parentTop) {
                    yPosW2 = (parentTop + 10) - height;
                } else if (r.centerY() - (height/2) < 0) {
                    yPosW2 = 15;
                } else {
                    yPosW2 = r.centerY() - (height/2);
                }
                break;
            case 270:
                r = new Rect(location[0], location[1],
                        location[0] - h,
                        location[1] + w);
                yPosW2 = 15;
                if ((r.centerY() - (height/2)) < (parentLeft - parent.getHeight())) {
                    xPosW2 = (parentLeft - parent.getHeight()) + 5;
                } else {
                    xPosW2 = r.centerY() - (height/2);
                }
                break;
        }
    }

    private class SeekBarChangeHelper implements SeekBarLayout.OnSeekBarChange{


        @Override
        public void onProgressChanged(VerticalSeekBar seekBar, int progress,
                                      boolean fromUser) {
            mMultiLayout.updateExposure(progress);
            progress -= 4;
            ((CameraActivity) mContext).updateExposure(progress);

        }

        @Override

        public void onStartTrackingTouch(VerticalSeekBar seekBar) {
            mHandler.removeMessages(POPUP_DISMISS);
        }

        @Override
        public void onStopTrackingTouch(VerticalSeekBar seekBar) {
            mHandler.sendEmptyMessageDelayed(POPUP_DISMISS, DELAYED_DISMISS_TIMER);
        }

    }

    public void resetExposure() {
        sBLayout.resetSeekBar();
        mMultiLayout.updateAllSelected();
        mMultiLayout.updateExposure(4);
    }

}