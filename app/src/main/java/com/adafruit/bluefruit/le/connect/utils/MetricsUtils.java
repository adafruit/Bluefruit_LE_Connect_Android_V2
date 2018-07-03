package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;

// http://stackoverflow.com/questions/4605527/converting-pixels-to-dp
public class MetricsUtils {

    public static float convertPixelsToDp(final Context context, final float px) {
        if (context != null) {
            return px / context.getResources().getDisplayMetrics().density;
        } else {
            return 0f;
        }
    }

    public static float convertDpToPixel(final Context context, final float dp) {
        if (context != null) {
            return dp * context.getResources().getDisplayMetrics().density;
        } else {
            return 0f;
        }
    }
}