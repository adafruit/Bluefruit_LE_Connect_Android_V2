package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.util.AttributeSet;
import android.view.View;

public class ThermalGradientView extends View {
    // Log
    private final static String TAG = ThermalGradientView.class.getSimpleName();

    // Config
    private static final int kNumColorSegments = 5; // Color gradient is more accurate with a bigger number of segments

    // Data
    private ThermalCameraFragment mThermalCameraFragment;           // A reference to the fragment is needed to compute the current colors. TODO: replace reference to fragment with a shared data model (for ThermalCameraFragment and ThermalGradientView)
    private Paint mPaint = new Paint();

    // region Lifecycle
    public ThermalGradientView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ThermalGradientView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public ThermalGradientView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ThermalGradientView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }


    private void init(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
    }

    void setThermalCameraFragment(ThermalCameraFragment fragment) {
        mThermalCameraFragment = fragment;
        updateGradient();
    }
    // endregion


    // region Draw
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawPaint(mPaint);
    }

    // endregion

    // region Layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        updateGradient();
    }

    public void updateGradient() {
        if (mThermalCameraFragment == null) {
            return;
        }

        int[] colors = new int[kNumColorSegments];
        float x = 0;
        for (int i = 0; i < kNumColorSegments; i++) {
            final int color = mThermalCameraFragment.temperatureComponentsForValue(x);
            colors[i] = color;
            x += 1f / (kNumColorSegments - 1);
        }

        LinearGradient linearGradient = new LinearGradient(0, 0, getWidth(), 0, colors, null, Shader.TileMode.CLAMP);
        mPaint.setShader(linearGradient);
        invalidate();
    }

    // endregion
}
