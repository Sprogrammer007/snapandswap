package com.snapandswap.ui;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

abstract public class PopupWindows implements OnTouchListener,
        PopupWindow.OnDismissListener {

    protected static final String TAG = "PopupManager";

    protected OnDismissListener mDismissListener;

	protected static final int POPUP_DISMISS = 1;
	protected static final int ROTATE_POPUP_ASYNC = 2;
    protected static final int ROTATE_POPUP2_ASYNC = 3;
	protected static final int MEASURE_ZOOM = 4;   
	protected static final int MEASURE_LIST_SINGLE = 5;
    protected static final int MEASURE_LIST_MULTI = 7;
	protected static final int DISABLE_FOCUS = 6;
	protected static final int DELAYED_DISMISS_TIMER = 5000;
	
	protected Context mContext;
	protected PopupWindow mWindow;
	protected PopupWindow mArrowPop;
	protected PopupWindow mWindow2;

	protected ViewGroup mRootViewSingle;
	protected ViewGroup mRootViewMulti;
	protected WindowManager mWindowManager;
    protected boolean isRotating = false;

    protected final Object mLock = new Object();

	public PopupWindows(Context context) {
		mContext = context;
		mWindow = new PopupWindow(context);

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

		mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
		mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
		mWindow.setTouchable(true);
		mWindow.setTouchInterceptor(this);
		
		mArrowPop = new PopupWindow(context);
    	mArrowPop.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
    	mArrowPop.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    	mArrowPop.setTouchable(false);

        mWindow2 = new PopupWindow(context);
        mWindow2.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow2.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow2.setTouchable(true);

        setOnDismissListener(this);
    }


    public interface OnDismissListener {
        public abstract void onDismiss();
    }


    @SuppressWarnings("deprecation")
	public void setBackgroundDrawable(Drawable background) {
		if (background == null) {
			mWindow.setBackgroundDrawable(new BitmapDrawable());
		}else {
			mWindow.setBackgroundDrawable(background);
		}
	}

	public void setContentView(View root) {
		mWindow.setContentView(root);
	}

	public void setContentView(int layoutResID) {
		LayoutInflater inflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		setContentView(inflator.inflate(layoutResID, null));
	}

	public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
		mWindow.setOnDismissListener(listener);  
	}

    public void setWindow2OnTouchListener(View.OnTouchListener listener) {
        mWindow2.setTouchInterceptor(listener);
    }


    @Override
    public void onDismiss() {
        Log.i(TAG, "Popup Dismissed");

        synchronized (mLock) {
            if (mArrowPop.isShowing() && !isRotating) {
                mArrowPop.dismiss();
            }
        }

        if (mWindow.isShowing() && mDismissListener != null) {
            mDismissListener.onDismiss();
        }

    }
}