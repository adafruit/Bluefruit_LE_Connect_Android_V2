package com.adafruit.bluefruit.le.connect.app.neopixel;

// Copy of com.larswerkman.holocolorpicker.SaturationBar and modified to handle ComponentW with the same visual style

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class NeopixelComponentWBar extends View {

    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_PARENT = "parent";
    private static final String STATE_COLOR = "color";
    private static final String STATE_SATURATION = "saturation";
    private static final String STATE_ORIENTATION = "orientation";

    /**
     * Constants used to identify orientation.
     */
    private static final boolean ORIENTATION_HORIZONTAL = true;
    private static final boolean ORIENTATION_VERTICAL = false;

    /**
     * Default orientation of the bar.
     */
    private static final boolean ORIENTATION_DEFAULT = ORIENTATION_HORIZONTAL;

    /**
     * The thickness of the bar.
     */
    private int mBarThickness;

    /**
     * The length of the bar.
     */
    private int mBarLength;
    private int mPreferredBarLength;

    /**
     * The radius of the pointer.
     */
    private int mBarPointerRadius;

    /**
     * The radius of the halo of the pointer.
     */
    private int mBarPointerHaloRadius;

    /**
     * The position of the pointer on the bar.
     */
    private int mBarPointerPosition;

    /**
     * {@code Paint} instance used to draw the bar.
     */
    private Paint mBarPaint;

    /**
     * {@code Paint} instance used to draw the pointer.
     */
    private Paint mBarPointerPaint;

    /**
     * {@code Paint} instance used to draw the halo of the pointer.
     */
    private Paint mBarPointerHaloPaint;

    /**
     * The rectangle enclosing the bar.
     */
    private RectF mBarRect = new RectF();

    /**
     * {@code Shader} instance used to fill the shader of the paint.
     */
    private Shader shader;

    /**
     * {@code true} if the user clicked on the pointer to start the move mode. <br>
     * {@code false} once the user stops touching the screen.
     *
     * @see #onTouchEvent(android.view.MotionEvent)
     */
    private boolean mIsMovingPointer;

    /**
     * The ARGB value of the currently selected color.
     */
    private int mColor;
    private float mWComponent;

    /**
     * An array of floats that can be build into a {@code Color} <br>
     * Where we can extract the color from.
     */
    private float[] mHSVColor = new float[3];

    /**
     * Factor used to calculate the position to the Opacity on the bar.
     */
    private float mPosToSatFactor;

    /**
     * Factor used to calculate the Opacity to the postion on the bar.
     */
    private float mSatToPosFactor;

    /**
     * Used to toggle orientation between vertical and horizontal.
     */
    private boolean mOrientation;

    /**
     * Interface and listener so that changes in SaturationBar are sent
     * to the host activity/fragment
     */
    private Listener mListener;

    /**
     * Saturation of the latest entry of the onSaturationChangedListener.
     */
    private float mOldWComponent;

    public interface Listener {
        void onWComponentChanged(float wComponent);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }


    public NeopixelComponentWBar(Context context) {
        super(context);
        init(null, 0);
    }

    public NeopixelComponentWBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public NeopixelComponentWBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                com.larswerkman.holocolorpicker.R.styleable.ColorBars, defStyle, 0);
        final Resources b = getContext().getResources();

        mBarThickness = a.getDimensionPixelSize(
                com.larswerkman.holocolorpicker.R.styleable.ColorBars_bar_thickness,
                b.getDimensionPixelSize(com.larswerkman.holocolorpicker.R.dimen.bar_thickness));
        mBarLength = a.getDimensionPixelSize(com.larswerkman.holocolorpicker.R.styleable.ColorBars_bar_length,
                b.getDimensionPixelSize(com.larswerkman.holocolorpicker.R.dimen.bar_length));
        mPreferredBarLength = mBarLength;
        mBarPointerRadius = a.getDimensionPixelSize(
                com.larswerkman.holocolorpicker.R.styleable.ColorBars_bar_pointer_radius,
                b.getDimensionPixelSize(com.larswerkman.holocolorpicker.R.dimen.bar_pointer_radius));
        mBarPointerHaloRadius = a.getDimensionPixelSize(
                com.larswerkman.holocolorpicker.R.styleable.ColorBars_bar_pointer_halo_radius,
                b.getDimensionPixelSize(com.larswerkman.holocolorpicker.R.dimen.bar_pointer_halo_radius));
        mOrientation = a.getBoolean(
                com.larswerkman.holocolorpicker.R.styleable.ColorBars_bar_orientation_horizontal, ORIENTATION_DEFAULT);

        a.recycle();

        mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPaint.setShader(shader);

        mBarPointerPosition = mBarLength + mBarPointerHaloRadius;

        mBarPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPointerHaloPaint.setColor(Color.BLACK);
        mBarPointerHaloPaint.setAlpha(0x50);

        mBarPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPointerPaint.setColor(Color.BLACK);     // Initial color for pointer

        mPosToSatFactor = 1 / ((float) mBarLength);
        mSatToPosFactor = ((float) mBarLength) / 1;

        mColor = Color.BLACK;       // Initial color
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int intrinsicSize = mPreferredBarLength
                + (mBarPointerHaloRadius * 2);

        // Variable orientation
        int measureSpec;
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            measureSpec = widthMeasureSpec;
        } else {
            measureSpec = heightMeasureSpec;
        }
        int lengthMode = MeasureSpec.getMode(measureSpec);
        int lengthSize = MeasureSpec.getSize(measureSpec);

        int length;
        if (lengthMode == MeasureSpec.EXACTLY) {
            length = lengthSize;
        } else if (lengthMode == MeasureSpec.AT_MOST) {
            length = Math.min(intrinsicSize, lengthSize);
        } else {
            length = intrinsicSize;
        }

        int barPointerHaloRadiusx2 = mBarPointerHaloRadius * 2;
        mBarLength = length - barPointerHaloRadiusx2;
        if (mOrientation == ORIENTATION_VERTICAL) {
            setMeasuredDimension(barPointerHaloRadiusx2,
                    (mBarLength + barPointerHaloRadiusx2));
        } else {
            setMeasuredDimension((mBarLength + barPointerHaloRadiusx2),
                    barPointerHaloRadiusx2);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Fill the rectangle instance based on orientation
        int x1, y1;
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            x1 = (mBarLength + mBarPointerHaloRadius);
            y1 = mBarThickness;
            mBarLength = w - (mBarPointerHaloRadius * 2);
            mBarRect.set(mBarPointerHaloRadius,
                    (mBarPointerHaloRadius - (mBarThickness / 2)),
                    (mBarLength + (mBarPointerHaloRadius)),
                    (mBarPointerHaloRadius + (mBarThickness / 2)));
        } else {
            x1 = mBarThickness;
            y1 = (mBarLength + mBarPointerHaloRadius);
            mBarLength = h - (mBarPointerHaloRadius * 2);
            mBarRect.set((mBarPointerHaloRadius - (mBarThickness / 2)),
                    mBarPointerHaloRadius,
                    (mBarPointerHaloRadius + (mBarThickness / 2)),
                    (mBarLength + (mBarPointerHaloRadius)));
        }

        // Update variables that depend of mBarLength.
        if (!isInEditMode()) {
            shader = new LinearGradient(mBarPointerHaloRadius, 0,
                    x1, y1, new int[]{
                    Color.BLACK,
                    Color.WHITE}, null,
                    Shader.TileMode.CLAMP);
        } else {
            shader = new LinearGradient(mBarPointerHaloRadius, 0,
                    x1, y1, new int[]{
                    Color.BLACK, Color.WHITE}, null, Shader.TileMode.CLAMP);
            Color.colorToHSV(0xff81ff00, mHSVColor);
        }

        mBarPaint.setShader(shader);
        mPosToSatFactor = 1 / ((float) mBarLength);
        mSatToPosFactor = ((float) mBarLength) / 1;

        //float[] hsvColor = new float[3];
        //Color.colorToHSV(mColor, hsvColor);
        float wComponent = Color.red(mColor) / 255f;

        if (!isInEditMode()) {
            mBarPointerPosition = Math.round((mSatToPosFactor * wComponent)+ mBarPointerHaloRadius);
        } else {
            mBarPointerPosition = mBarLength + mBarPointerHaloRadius;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the bar.
        canvas.drawRect(mBarRect, mBarPaint);

        // Calculate the center of the pointer.
        int cX, cY;
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            cX = mBarPointerPosition;
            cY = mBarPointerHaloRadius;
        } else {
            cX = mBarPointerHaloRadius;
            cY = mBarPointerPosition;
        }

        // Draw the pointer halo.
        canvas.drawCircle(cX, cY, mBarPointerHaloRadius, mBarPointerHaloPaint);
        // Draw the pointer.
        canvas.drawCircle(cX, cY, mBarPointerRadius, mBarPointerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);

        // Convert coordinates to our internal coordinate system
        float dimen;
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            dimen = event.getX();
        } else {
            dimen = event.getY();
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsMovingPointer = true;
                // Check whether the user pressed on (or near) the pointer
                if (dimen >= (mBarPointerHaloRadius)
                        && dimen <= (mBarPointerHaloRadius + mBarLength)) {
                    mBarPointerPosition = Math.round(dimen);
                    calculateColor(Math.round(dimen));
                    mBarPointerPaint.setColor(mColor);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsMovingPointer) {
                    // Move the the pointer on the bar.
                    if (dimen >= mBarPointerHaloRadius
                            && dimen <= (mBarPointerHaloRadius + mBarLength)) {
                        mBarPointerPosition = Math.round(dimen);
                        calculateColor(Math.round(dimen));
                        mBarPointerPaint.setColor(mColor);
                        invalidate();
                    } else if (dimen < mBarPointerHaloRadius) {
                        mBarPointerPosition = mBarPointerHaloRadius;
                        mColor = Color.BLACK;
                        mWComponent = 0f;
                        mBarPointerPaint.setColor(mColor);
                        invalidate();
                    } else if (dimen > (mBarPointerHaloRadius + mBarLength)) {
                        mBarPointerPosition = mBarPointerHaloRadius + mBarLength;
                        mColor = Color.WHITE;//Color.HSVToColor(mHSVColor);
                        mWComponent = 1f;
                        mBarPointerPaint.setColor(mColor);
                        invalidate();
                    }
                }
                if (mListener != null && mOldWComponent != mWComponent) {
                    mListener.onWComponentChanged(mWComponent);
                    mOldWComponent = mWComponent;
                }
                break;
            case MotionEvent.ACTION_UP:
                mIsMovingPointer = false;
                break;
        }
        return true;
    }

    /**
     * Set the bar color. <br>
     * <br>
     * Its discouraged to use this method.
     *
     * @param color
     */
    /*
    public void setColor(int color) {
        int x1, y1;
        if (mOrientation == ORIENTATION_HORIZONTAL) {
            x1 = (mBarLength + mBarPointerHaloRadius);
            y1 = mBarThickness;
        } else {
            x1 = mBarThickness;
            y1 = (mBarLength + mBarPointerHaloRadius);
        }

        Color.colorToHSV(color, mHSVColor);
        shader = new LinearGradient(mBarPointerHaloRadius, 0,
                x1, y1, new int[]{
                Color.BLACK, color}, null,
                Shader.TileMode.CLAMP);
        mBarPaint.setShader(shader);
        calculateColor(mBarPointerPosition);
        mBarPointerPaint.setColor(mColor);
        invalidate();
    }*/

    /**
     * Set the pointer on the bar. With the opacity value.
     *
     * @param saturation float between 0 and 1
     */
    /*
    public void setSaturation(float saturation) {
        mBarPointerPosition = Math.round((mSatToPosFactor * saturation))
                + mBarPointerHaloRadius;
        calculateColor(mBarPointerPosition);
        mBarPointerPaint.setColor(mColor);
        invalidate();
    }*/

    /**
     * Calculate the color selected by the pointer on the bar.
     *
     * @param coord Coordinate of the pointer.
     */
    private void calculateColor(int coord) {
        coord = coord - mBarPointerHaloRadius;
        if (coord < 0) {
            coord = 0;
        } else if (coord > mBarLength) {
            coord = mBarLength;
        }
        //mColor = Color.HSVToColor ( new float[]{mHSVColor[0], (mPosToSatFactor * coord), 1f});

        mWComponent = (coord / (float) mBarLength);
        final byte colorComponent = (byte) Math.round((mWComponent*255f));
        mColor = Color.argb(255, colorComponent, colorComponent, colorComponent);
    }

    public int getColor() {
        return mColor;
    }

    public float getWComponent() {
        return mWComponent;
    }

    public void setWComponent(float wComponent) {
        /*
        mWComponent = wComponent;
        byte colorComponent = (byte) Math.round(wComponent * 255);
        mColor = Color.argb(255, colorComponent, colorComponent, colorComponent);
        invalidate();
*/

        mBarPointerPosition = Math.round((mSatToPosFactor * wComponent) + mBarPointerHaloRadius);
        calculateColor(mBarPointerPosition);
        mBarPointerPaint.setColor(mColor);
        invalidate();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        Bundle state = new Bundle();
        state.putParcelable(STATE_PARENT, superState);
        /*
        state.putFloatArray(STATE_COLOR, mHSVColor);

        float[] hsvColor = new float[3];
        Color.colorToHSV(mColor, hsvColor);
        state.putFloat(STATE_SATURATION, hsvColor[1]);
*/
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle savedState = (Bundle) state;

        Parcelable superState = savedState.getParcelable(STATE_PARENT);
        super.onRestoreInstanceState(superState);
/*

        setColor(Color.HSVToColor(savedState.getFloatArray(STATE_COLOR)));
        setSaturation(savedState.getFloat(STATE_SATURATION));
        */
    }

}
