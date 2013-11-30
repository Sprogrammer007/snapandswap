package com.snapandswap.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

public class CameraPreview extends SurfaceView{

	@SuppressWarnings("unused")
	private Context mContext;
	@SuppressWarnings("deprecation")
	public CameraPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		setZOrderMediaOverlay(true);
		getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}	
	
	public void shrink() {
		setLayoutSize(1);
	}

	public void expand() {
		setLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT);
	}

	private void setLayoutSize(int size) {
		ViewGroup.LayoutParams p = getLayoutParams();
		if (p.width != size || p.height != size) {
			p.width = size;
			p.height = size;
			setLayoutParams(p);
		}
	}
}
