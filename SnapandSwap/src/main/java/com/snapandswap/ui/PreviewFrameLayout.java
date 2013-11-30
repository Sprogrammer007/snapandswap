package com.snapandswap.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * A layout which handles the preview aspect ratio.
 */
public class PreviewFrameLayout extends RelativeLayout implements LayoutChangeNotifier {

    @SuppressWarnings("unused")
	private static final String TAG = "CAM_preview";

    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged(int width, int height);
    }

    private double mAspectRatio;
    private View mBorder;
    private OnSizeChangedListener mListener;
    private LayoutChangeHelper mLayoutChangeHelper;

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAspectRatio(4.0 / 3.0);
        mLayoutChangeHelper = new LayoutChangeHelper(this);
    }

    @Override
    protected void onFinishInflate() {
      
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }

    public void showBorder(boolean enabled) {
        mBorder.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    public void fadeOutBorder() {

    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);

        // Get the padding of the border background.
        int hPadding = getPaddingLeft() + getPaddingRight();
        int vPadding = getPaddingTop() + getPaddingBottom();

        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;

        // Ask children to follow the new preview dimension.
        super.onMeasure(MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY));
    }

    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mListener != null) mListener.onSizeChanged(w, h);
    }

    @Override
    public void setOnLayoutChangeListener(
            LayoutChangeNotifier.Listener listener) {
        mLayoutChangeHelper.setOnLayoutChangeListener(listener);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mLayoutChangeHelper.onLayout(changed, l, t, r, b);
    }
}
