package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;


public class NetworkUtils {

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public static boolean isNetworkAvailable(@NonNull Context context) {

        boolean result = false;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);     // Use getApplicationContext to solve a bug on Android M: https://stackoverflow.com/questions/41431409/connectivitymanager-leaking-not-sure-how-to-resolve
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                result = activeNetworkInfo.isConnected();
            }
        }

        return result;
    }
}
