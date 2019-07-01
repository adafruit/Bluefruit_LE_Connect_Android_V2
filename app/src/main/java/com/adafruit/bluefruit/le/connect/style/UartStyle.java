package com.adafruit.bluefruit.le.connect.style;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import androidx.annotation.NonNull;

public class UartStyle {
    public static @NonNull
    int[] defaultColors() {
        // Based on Chart joyful and colorful colors:  https://github.com/danielgindi/Charts
        return new int[]{
                Color.rgb(193, 37, 82),
                Color.rgb(58, 95, 201),     // extra blue
                Color.rgb(255, 102, 0),
                Color.rgb(245, 199, 0),
                Color.rgb(106, 150, 31),
                Color.rgb(179, 100, 53),

                Color.rgb(217, 80, 138),
                Color.rgb(254, 149, 7),
                Color.rgb(254, 247, 120),
                Color.rgb(106, 167, 134),
                Color.rgb(53, 194, 209)
        };
    }

    public static @NonNull
    DashPathEffect[] defaultDashPathEffects() {
        return new DashPathEffect[]{
                null,                                                   // -----------------------
                new DashPathEffect(new float[]{10, 4}, 0),       // -----  -----  -----  -----
                new DashPathEffect(new float[]{4, 6}, 0),        // --   --   --   --   --
                new DashPathEffect(new float[]{8, 8}, 0),        // ----    ----    -----
                new DashPathEffect(new float[]{2, 4}, 0),        // -  -  -  -  -  -  -  -
                new DashPathEffect(new float[]{6, 4, 2, 1}, 0),  // ---  -  ---  -
        };
    }
}
