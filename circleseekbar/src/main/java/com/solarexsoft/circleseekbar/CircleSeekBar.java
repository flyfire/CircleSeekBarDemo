package com.solarexsoft.circleseekbar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class CircleSeekBar extends View {
    private static final String TAG = CircleSeekBar.class.getSimpleName();
    public static int INVALID_VALUE = -1;
    public static final int MAX = 100;
    public static final int MIN = 0;

    private static final int ANGLE_OFFSET = -90;// start from 12 o'clock
    private int mPoints = MIN; // current points value
    private int mMin = MIN; // min value of progress value
    private int mMax = MAX; // max value seekarc can be set to
    private int mStep = 10; // increment/decrement value for each movement of the progress
    private Drawable mIndicatorIcon; // the drawable for the seek arc thumbnail

    private int mProgressWidth = 12;
    private int mArcWidth = 12;
    private boolean mClockWise = true;
    private boolean mEnabled = true;

    private int mUpdateTimes = 0;
    private float mPreviousProgress = -1;
    private float mCurrentProgress = 0;

    private boolean isMax = false; // determine whether reach max of point
    private boolean isMin = false; // determine whether reach min of point

    private int mArcRadius = 0;
    private RectF mArcRect = new RectF();
    private Paint mArcPaint;

    private float mProgressSweep = 0;
    private Paint mProgressPaint;

    private float mTextSize = 72;
    private Paint mTextPaint;
    private Rect mTextRect = new Rect();

    private int mTranslateX;
    private int mTranslateY;

    // (x,y) coordinator of indicator icon
    private int mIndicatorIconX;
    private int mIndicatorIconY;

    private double mTouchAngle; // current touch angle of arc

    public interface OnSeekPointsChangeListener {
        void onSeekPointsChanged(CircleSeekBar circleSeekBar, int points, boolean fromUser);

        void onStartTrackingTouch(CircleSeekBar circleSeekBar);

        void onStopTrackingTouch(CircleSeekBar circleSeekBar);
    }

    private OnSeekPointsChangeListener mOnSeekPointsChangeListener;

    public CircleSeekBar(Context context) {
        this(context, null);
    }

    public CircleSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;
        int arcColor = ContextCompat.getColor(context, R.color.color_arc);
        int progressColor = ContextCompat.getColor(context, R.color.color_progress);
        int textColor = ContextCompat.getColor(context, R.color.color_text);

        mProgressWidth = (int) (mProgressWidth * density);
        mArcWidth = (int) (mArcWidth * density);
        mTextSize = (int) (mTextSize * density);

        mIndicatorIcon = ContextCompat.getDrawable(context, R.drawable.indicator);

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable
                    .CircleSeekBar, 0, 0);
            Drawable indicatorIcon = typedArray.getDrawable(R.styleable
                    .CircleSeekBar_indicatoricon);
            if (indicatorIcon != null) {
                mIndicatorIcon = indicatorIcon;
            }
            int indicatorIconHalfWidth = mIndicatorIcon.getIntrinsicWidth() / 2;
            int indicatorIconHalfHeight = mIndicatorIcon.getIntrinsicHeight() / 2;
            mIndicatorIcon.setBounds(-indicatorIconHalfWidth, -indicatorIconHalfHeight,
                    indicatorIconHalfWidth, indicatorIconHalfHeight);

            mPoints = typedArray.getInteger(R.styleable.CircleSeekBar_points, mPoints);
            mMin = typedArray.getInteger(R.styleable.CircleSeekBar_min, mMin);
            mMax = typedArray.getInteger(R.styleable.CircleSeekBar_max, mMax);
            mStep = typedArray.getInteger(R.styleable.CircleSeekBar_step, mStep);

            mProgressWidth = (int) typedArray.getDimension(R.styleable
                    .CircleSeekBar_progresswidth, mProgressWidth);
            progressColor = typedArray.getColor(R.styleable.CircleSeekBar_progresscolor,
                    progressColor);

            mArcWidth = (int) typedArray.getDimension(R.styleable.CircleSeekBar_arcwidth,
                    mArcWidth);
            arcColor = typedArray.getColor(R.styleable.CircleSeekBar_arccolor, arcColor);

            mTextSize = (int) typedArray.getDimension(R.styleable.CircleSeekBar_textsize,
                    mTextSize);
            textColor = typedArray.getColor(R.styleable.CircleSeekBar_textcolor, textColor);

            mClockWise = typedArray.getBoolean(R.styleable.CircleSeekBar_colockwise, mClockWise);
            mEnabled = typedArray.getBoolean(R.styleable.CircleSeekBar_enabled, mEnabled);
            typedArray.recycle();
        }

        mPoints = (mPoints > mMax) ? mMax : mPoints;
        mPoints = (mPoints < mMin) ? mMin : mPoints;

        mProgressSweep = (float) mPoints / valuePerDegree();

        mArcPaint = new Paint();
        mArcPaint.setColor(arcColor);
        mArcPaint.setAntiAlias(true);
        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeWidth(mArcWidth);

        mProgressPaint = new Paint();
        mProgressPaint.setColor(progressColor);
        mProgressPaint.setAntiAlias(true);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeWidth(mProgressWidth);

        mTextPaint = new Paint();
        mTextPaint.setColor(textColor);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(mTextSize);
    }

    private float valuePerDegree() {
        return (float) (mMax) / 360.0f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        final int min = Math.min(width, height);

        mTranslateX = (int) (width * 0.5f);
        mTranslateY = (int) (height * 0.5f);

        int arcDiameter = min - getPaddingLeft();
        mArcRadius = arcDiameter / 2;

        float top = height / 2 - (arcDiameter / 2);
        float left = width / 2 - (arcDiameter / 2);
        mArcRect.set(left, top, left + arcDiameter, top + arcDiameter);

        updateIndicatorIconPosition();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mClockWise) {
            canvas.scale(-1, 1, mArcRect.centerX(), mArcRect.centerY());
        }

        String textPoint = String.valueOf(mPoints);
        mTextPaint.getTextBounds(textPoint, 0, textPoint.length(), mTextRect);

        int xPos = canvas.getWidth() / 2 - mTextRect.width() / 2;
        int yPos = (int) ((mArcRect.centerY()) - ((mTextPaint.descent() + mTextPaint.ascent()) /
                2));
        canvas.drawText(textPoint, xPos, yPos, mTextPaint);

        canvas.drawArc(mArcRect, ANGLE_OFFSET, 360, false, mArcPaint);
        canvas.drawArc(mArcRect, ANGLE_OFFSET, mProgressSweep, false, mProgressPaint);

        if (mEnabled) {
            // draw the indicator icon
            canvas.translate(mTranslateX - mIndicatorIconX, mTranslateY - mIndicatorIconY);
            mIndicatorIcon.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEnabled) {
            this.getParent().requestDisallowInterceptTouchEvent(true);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mOnSeekPointsChangeListener != null) {
                        mOnSeekPointsChangeListener.onStartTrackingTouch(this);
                    }
                    updateOnTouch(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    updateOnTouch(event);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mOnSeekPointsChangeListener != null) {
                        mOnSeekPointsChangeListener.onStopTrackingTouch(this);
                    }
                    setPressed(false);
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }
        return false;
    }

    private void updateOnTouch(MotionEvent event) {
        setPressed(true);
        mTouchAngle = convertTouchEventPointToAngle(event.getX(), event.getY());
        int progress = convertAngleToProgress(mTouchAngle);
        updateProgress(progress, true);
    }

    private void updateProgress(int progress, boolean fromUser) {
        // detect points change closed to max or min
        final int maxDetectValue = (int)((double)mMax * 0.95f);
        final int minDetectValue = (int)((double)mMax*0.05f)+mMin;

        mUpdateTimes++;
        if (progress == INVALID_VALUE) {
            return;
        }
        if (mUpdateTimes == 1) {
            mCurrentProgress = progress;
        } else {
            mPreviousProgress = mCurrentProgress;
            mCurrentProgress = progress;
        }

        mPoints = progress - (progress%mStep);
        /**
         * Determine whether reach max or min to lock point update event.
         *
         * When reaching max, the progress will drop from max (or maxDetectPoints ~ max
         * to min (or min ~ minDetectPoints) and vice versa.
         *
         * If reach max or min, stop increasing / decreasing to avoid exceeding the max / min.
         */
        // 判斷超過最大值或最小值，最大最小值不重複判斷
        // 用數值範圍判斷預防轉太快直接略過最大最小值。
        // progress變化可能從98 -> 0/1 or 0/1 -> 98/97，而不會過0或100
//        if (mUpdateTimes > 1 && !isMin && !isMax) {
//            if (mPreviousProgress >= maxDetectValue && mCurrentProgress <= minDetectValue &&
//                    mPreviousProgress > mCurrentProgress) {
//                isMax = true;
//                progress = mMax;
//                mPoints = mMax;
////				System.out.println("Reach Max " + progress);
//                if (mOnSeekPointsChangeListener != null) {
//                    mOnSeekPointsChangeListener
//                            .onSeekPointsChanged(this, progress, fromUser);
//                    return;
//                }
//            } else if ((mCurrentProgress >= maxDetectValue
//                    && mPreviousProgress <= minDetectValue
//                    && mCurrentProgress > mPreviousProgress) || mCurrentProgress <= mMin) {
//                isMin = true;
//                progress = mMin;
//                mPoints = mMin;
////				Log.d("Reach", "Reach Min " + progress);
//                if (mOnSeekPointsChangeListener != null) {
//                    mOnSeekPointsChangeListener
//                            .onSeekPointsChanged(this, progress, fromUser);
//                    return;
//                }
//            }
//            invalidate();
//        } else {
//
//            // Detect whether decreasing from max or increasing from min, to unlock the update event.
//            // Make sure to check in detect range only.
//            if (isMax & (mCurrentProgress < mPreviousProgress) && mCurrentProgress >= maxDetectValue) {
//                isMax = false;
//            }
//            if (isMin
//                    && (mPreviousProgress < mCurrentProgress)
//                    && mPreviousProgress <= minDetectValue && mCurrentProgress <= minDetectValue
//                    && mPoints >= mMin) {
////				Log.d("Unlock", String.format("Unlock min %.0f, %.0f\n", mPreviousProgress, mCurrentProgress));
//                isMin = false;
//            }
//        }

//        if (!isMax && !isMin) {
//
//        }
        progress = (progress > mMax) ? mMax : progress;
        progress = (progress < mMin) ? mMin : progress;

        if (mOnSeekPointsChangeListener != null) {
            progress = progress - (progress % mStep);

            mOnSeekPointsChangeListener
                    .onSeekPointsChanged(this, progress, fromUser);
        }

        mProgressSweep = (float) progress / valuePerDegree();
//			if (mPreviousProgress != mCurrentProgress)
//				System.out.printf("-- %d, %d, %f\n", progress, mPoints, mProgressSweep);
        updateIndicatorIconPosition();
        invalidate();
    }

    private void updateIndicatorIconPosition() {
        int thumbAngle = (int)(mProgressSweep + 90);
        mIndicatorIconX = (int)(mArcRadius*Math.cos(Math.toRadians(thumbAngle)));
        mIndicatorIconY = (int)(mArcRadius*Math.sin(Math.toRadians(thumbAngle)));
    }

    private int convertAngleToProgress(double touchAngle) {
        return (int)Math.round(valuePerDegree()*touchAngle);
    }

    private double convertTouchEventPointToAngle(float xPos, float yPos) {
        float x = xPos - mTranslateX;
        float y = yPos - mTranslateY;

        x = mClockWise ? x : -x;
        double angle = Math.toDegrees(Math.atan2(y, x) + Math.PI / 2);
        angle = angle < 0 ? (angle + 360) : angle;
        Log.d(TAG, "(x,y,angle) = (" + x + "," + y + "," + angle + ")");
        return angle;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mIndicatorIcon != null && mIndicatorIcon.isStateful()) {
            int[] state = getDrawableState();
            mIndicatorIcon.setState(state);
        }
        invalidate();
    }
}
