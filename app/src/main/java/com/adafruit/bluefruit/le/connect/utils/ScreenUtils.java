package com.adafruit.bluefruit.le.connect.utils;

import android.app.Activity;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

public class ScreenUtils {

    public static void keepScreenOn(@NonNull Activity activity, boolean enabled) {
        Window window = activity.getWindow();
        if (enabled)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
