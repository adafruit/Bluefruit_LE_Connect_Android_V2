package com.adafruit.bluefruit.le.connect.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class PermissionsUtils {
    public static String[] getNeededPermissionsForScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    public static boolean manageLocationServiceAvailabilityForScanning(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return true;
        } else {
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
    }

    public static boolean hasPermissions(@NonNull Context context, @NonNull String... permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasPermissionsForScanning(@NonNull Context context) {
        boolean result = false;

        final boolean areLocationServicesReadyForScanning = PermissionsUtils.manageLocationServiceAvailabilityForScanning(context);
        if (areLocationServicesReadyForScanning) {
            String[] permissions = PermissionsUtils.getNeededPermissionsForScanning();
            if (PermissionsUtils.hasPermissions(context, permissions)) {
                result = true;
            }
        }

        return result;
    }
}
