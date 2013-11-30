package com.snapandswap.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.snapandswap.R;


public class ShutterButtonLayout extends RelativeLayout implements
        ShutterZoomView.OnShutterZoom,
        ShutterButton.OnShutterButtonListener, Rotatable{

	private ShutterButton mButton;
	private ShutterZoomView mZoom;
	private OnShutterButtonChangeListener mListener;
	
	public ShutterButtonLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}


    public interface OnShutterButtonChangeListener {
    	void onShutterButtonFocus(boolean pressed);
        void onShutterButtonReleased();
        void onShutterButtonClick();
        void onShutterButtonMoved(int angle, int power, int direction);
    }
    
    public void setOnShutterButtonChangeListener(OnShutterButtonChangeListener listener) {
        mListener = listener;
    }
    

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		mButton = (ShutterButton) findViewById(R.id.sbutton);
		mZoom = (ShutterZoomView) findViewById(R.id.shutterzoom);
		mZoom.setButton(mButton);
		mButton.setOnShutterButtonListener(this);
		mZoom.setOnShutterZoomListener(this);
	}


	@Override
	public void onShutterButtonFocus(boolean pressed) {
		if(mListener != null) {
			mListener.onShutterButtonFocus(pressed);
		}
	}

	@Override
	public void onShutterButtonClick() {
		if(mListener != null) {
			mListener.onShutterButtonClick();
		}
	}

    @Override
    public void onShutterButtonReleased() {
        if(mListener != null) {
            mListener.onShutterButtonReleased();
        }
    }

    @Override
	public void onShutterButtonMoved(int angle, int power, int direction) {
        if(mListener != null) {
            mListener.onShutterButtonMoved(angle, power, direction);
        }
    }


	@Override
	public void setOrientation(int orientation, boolean animation) {
		if (mButton != null) {
			mButton.setOrientation(orientation, animation);
		}
	}

    public boolean isPressed() {
        return mButton.isPressed();
    }
}