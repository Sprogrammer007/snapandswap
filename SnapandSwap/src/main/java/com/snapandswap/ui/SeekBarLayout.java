package com.snapandswap.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.snapandswap.R;

import java.util.ArrayList;
import java.util.List;

public class SeekBarLayout extends RelativeLayout implements VerticalSeekBar.OnSeekBarChangeListener{
	
	public interface OnSeekBarChange {
		public void onProgressChanged(VerticalSeekBar seekBar, int progress,
				boolean fromUser);
		public void onStartTrackingTouch(VerticalSeekBar seekBar);
		public void onStopTrackingTouch(VerticalSeekBar seekBar); 
	}
	
	protected VerticalSeekBar mSeekBar;
	protected RotatableImageView mIcon;
	protected RotatableImageView mIcon2;
	private List<OnSeekBarChange> mListeners;
	
	public SeekBarLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		mListeners = new ArrayList<OnSeekBarChange>(10);
	}
	
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		
		mSeekBar = (VerticalSeekBar) findViewById(R.id.seekBar);
		mSeekBar.setProgressDrawable(null);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.setThumbOffset(3);
        mSeekBar.setMax(8);
        mSeekBar.setProgress(4);
		mIcon = (RotatableImageView) findViewById(R.id.zoomIconTop);
		mIcon2 = (RotatableImageView) findViewById(R.id.zoomIconBot);
	}
	
	public void setOrientation(int orientation) {
		mIcon.setOrientation(orientation, true);
		mIcon2.setOrientation(orientation, true);
	}

    public void resetSeekBar() {
        mSeekBar.setProgress(4);
    }

	public void setOnSeekBarChange(OnSeekBarChange listener) {
		mListeners.add(listener);
	}

	@Override
	public void onProgressChanged(VerticalSeekBar seekBar, int progress,
			boolean fromUser) {
		if (mListeners == null) return;
		for (OnSeekBarChange listener : mListeners) {
			listener.onProgressChanged(seekBar, progress, fromUser);
		}
	}

	@Override
	public void onStartTrackingTouch(VerticalSeekBar seekBar) {
		if (mListeners == null) return;
		for (OnSeekBarChange listener : mListeners) {
			listener.onStartTrackingTouch(seekBar);
		}	
	}

	@Override
	public void onStopTrackingTouch(VerticalSeekBar seekBar) {
		if (mListeners == null) return;
		for (OnSeekBarChange listener : mListeners) {
			listener.onStopTrackingTouch(seekBar);
		}
	}
}
