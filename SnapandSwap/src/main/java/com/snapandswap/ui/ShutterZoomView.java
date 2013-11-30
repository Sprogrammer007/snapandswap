package com.snapandswap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class ShutterZoomView extends RotatableImageView implements View.OnLongClickListener{
	@SuppressWarnings("unused")
	private final String TAG = "ShutterZoomView";
	
	private final static int LONG_PRESS_TIME = 500;
	private final static double RAD = 57.2957795;
	public final static int FRONT = 3;
	public final static int FRONT_RIGHT = 4;
	public final static int RIGHT = 5;
	public final static int RIGHT_BOTTOM = 6;
	public final static int BOTTOM = 7;
	public final static int BOTTOM_LEFT = 8;
	public final static int LEFT = 1;
	public final static int LEFT_FRONT = 2;

	private static int xPosition = 0; // Touch x position
	private static int yPosition = 0; // Touch y position
	private static double mCenterX = 0; // Center view x position
	private static double mCenterY = 0; // Center view y position
	private static int mRadius;
	private static int mLastAngle = 0;
	private static int mLastPower = 0;
    private final int mTapTimeout;
    private Paint mCircle;

    private static OnShutterZoom mListener;
	private ShutterButton mButton;
    private MotionEvent mDown;

    @Override
    public boolean onLongClick(View v) {
        return false;
    }

    public interface OnShutterZoom {
        void onShutterButtonClick();
        void onShutterButtonReleased();
        void onShutterButtonMoved(int angle, int power, int direction);
    }

    
    public ShutterZoomView(Context context) {
    	this(context, null);
    }

    public ShutterZoomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCircle.setColor(Color.WHITE);
        mCircle.setStyle(Paint.Style.STROKE);
        mCircle.setStrokeWidth(4);
        mTapTimeout = ViewConfiguration.getTapTimeout() + 500;
    }
    
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// setting the measured values to resize the view to a certain width and
		// height
		int d = Math.min(measure(widthMeasureSpec), measure(heightMeasureSpec));

		setMeasuredDimension(d, d);

		// before measure, get the center of view
		xPosition = (int) getWidth() / 2;
		yPosition = (int) getWidth() / 2;

		if(mButton != null) {
			setButtonPos();
		}
		mRadius = (int) (d / 2 * 0.6);
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r,
			int b) {
		super.onLayout(changed, l, t, r, b);
		mCenterX = (r - l) / 2;
		mCenterY = (b - t) / 2;
	}

	private synchronized void setButtonPos() {
		int w = mButton.getMeasuredWidth()/2;
		int h = mButton.getMeasuredHeight()/2;
		mButton.setLeft(xPosition - (w));
		mButton.setTop(yPosition - (h));
		mButton.setRight(xPosition + (w));
		mButton.setBottom(yPosition + (h));
	}

	private int measure(int measureSpec) {
		int result = 0;

		// Decode the measurement specifications.
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.UNSPECIFIED) {
			// Return a default size of 200 if no bounds are specified.
			result = 200;
		} else {
			// As you want to fill the available space
			// always return the full available bounds.
			result = specSize;
		}
		return result;
	}
	
	public void setButton(ShutterButton button) {
		  mButton = button;  
	}



	@Override
	protected void onDraw(Canvas canvas) {
		canvas.save();
		canvas.drawCircle((int) mCenterX, (int) mCenterY, mRadius,
				mCircle);
		canvas.restore();
	}

    public void setOnShutterZoomListener(OnShutterZoom listener) {
        mListener = listener;
    }
    
    @Override
    public boolean performClick() {
        boolean result = super.performClick();
        if (mListener != null) {
            mListener.onShutterButtonClick();
        }
        return result;
    }
    
    private int getAngle() {
		if (xPosition > mCenterX) {
			if (yPosition < mCenterY) {
				return mLastAngle = (int) (Math.atan((yPosition - mCenterY)
						/ (xPosition - mCenterX))
						* RAD + 90);
			} else if (yPosition > mCenterY) {
				return mLastAngle = (int) (Math.atan((yPosition - mCenterY)
						/ (xPosition - mCenterX)) * RAD) + 90;
			} else {
				return mLastAngle = 90;
			}
		} else if (xPosition < mCenterX) {
			if (yPosition < mCenterY) {
				return mLastAngle = (int) (Math.atan((yPosition - mCenterY)
						/ (xPosition - mCenterX))
						* RAD - 90);
			} else if (yPosition > mCenterY) {
				return mLastAngle = (int) (Math.atan((yPosition - mCenterY)
						/ (xPosition - mCenterX)) * RAD) - 90;
			} else {
				return mLastAngle = -90;
			}
		} else {
			if (yPosition <= mCenterY) {
				return mLastAngle = 0;
			} else {
				if (mLastAngle < 0) {
					return mLastAngle = -180;
				} else {
					return mLastAngle = 180;
				}
			}
		}
	}

	private int getPower() {
		return (int) (100 * Math.sqrt((xPosition - mCenterX)
				* (xPosition - mCenterX) + (yPosition - mCenterY)
				* (yPosition - mCenterY)) / mRadius);
	}

	private int getDirection() {
		if (mLastPower == 0 && mLastAngle == 0) {
			return 0;
		}
		int a = 0;
		if (mLastAngle <= 0) {
			a = (mLastAngle * -1) + 90;
		} else if (mLastAngle > 0) {
			if (mLastAngle <= 90) {
				a = 90 - mLastAngle;
			} else {
				a = 360 - (mLastAngle - 90);
			}
		}

		int direction = (int) (((a + 22) / 45) + 1);

		if (direction > 8) {
			direction = 1;
		}
		return direction;
	}
	

	@Override
	public boolean onTouchEvent(MotionEvent event) {
        int l = mButton.getLeft();
		int t = mButton.getTop();
		int r = l + mButton.getWidth();
		int b = t + mButton.getHeight();
		Rect loc = new Rect(l, t, r, b);

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: {
            if (loc.contains((int) event.getX(),(int) event.getY())) {
                mDown = MotionEvent.obtain(event);
                mButton.setPressed(true);
            }
            break;
        }
		case MotionEvent.ACTION_MOVE: {
            if (loc.contains((int) event.getX(),(int) event.getY())
            && (event.getEventTime()  - mDown.getEventTime() > mTapTimeout)) {
                mButton.setPressed(true);
                xPosition = (int) event.getX();
                yPosition = (int) event.getY();
                double abs = Math.sqrt((xPosition - mCenterX) * (xPosition - mCenterX)
                        + (yPosition - mCenterY) * (yPosition - mCenterY));
                if (abs > mRadius) {
                    xPosition = (int) ((xPosition - mCenterX) * mRadius / abs + mCenterX);
                    yPosition = (int) ((yPosition - mCenterY) * mRadius / abs + mCenterY);
                }

                setButtonPos();
                if (mListener != null) {
                    mListener.onShutterButtonMoved(getAngle(), getPower(),
                            getDirection());
                }
			} else {
				xPosition = (int) mCenterX;
				yPosition = (int) mCenterY;
				setButtonPos();
				mButton.setPressed(false);
				return true;
			}
			break;
        }
		case MotionEvent.ACTION_UP: {
			xPosition = (int) mCenterX;
			yPosition = (int) mCenterY;
			setButtonPos();
			if (loc.contains((int) event.getX(),(int) event.getY())
                && (event.getEventTime()  - mDown.getEventTime()) < mTapTimeout) {
                performClick();
			}
			mButton.setPressed(false);

            if (mListener != null) {
                mListener.onShutterButtonReleased();
            }
			break;
        }
		default:
			break;
		}
		return true;
	}
}


