package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;

import magick.Magick;

// Based on: https://github.com/paulasiimwe/Android-ImageMagick
public class ImageMagickUtils {
    // Log
    private final static String TAG = ImageMagickUtils.class.getSimpleName();

    public static void setCacheDir(Context context) {
        File maxDir = null;
        long maxSpace = -1;
        String path;

        File[] dirs = context.getExternalCacheDirs();

        for (int k = 0; k < dirs.length; ++k) {
            File dir = dirs[k];
            long dirFreeSpace = dir.getFreeSpace();

            // testing:
            path = dir.getAbsolutePath();
            Log.d(TAG, "- #" + k + " cache path: " + path);

            if (dirFreeSpace > maxSpace) {
                maxSpace = dirFreeSpace;
                maxDir = dir;
            }
        }

        if (maxDir != null) {
            path = maxDir.getAbsolutePath();
            Log.d(TAG, "- best cache path: " + path);
            Magick.setCacheDir(path);
        } else
            Log.d(TAG, "- best cache dir null");
    }
}
