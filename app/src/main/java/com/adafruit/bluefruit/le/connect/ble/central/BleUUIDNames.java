package com.adafruit.bluefruit.le.connect.ble.central;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BleUUIDNames {
    // Log
    private final static String TAG = BleUUIDNames.class.getSimpleName();

    // Constants
    private final static String kFilename = "GattUUIDs.json";

    // Data
    private static JSONObject mGatUUIDs;

    public static String getNameForUUID(Context context, @NonNull String uuidString) {
        if (mGatUUIDs == null) {
            String gattUUIDsString = loadStringFromAsset(context);
            try {
                mGatUUIDs = new JSONObject(gattUUIDsString);
            } catch (JSONException e) {
                Log.e(TAG, "Error: can't load known service UUIDs");
            }
        }

        String result = null;
        String uppercaseUuidString = uuidString.toUpperCase();
        try {
            result = mGatUUIDs.getString(uppercaseUuidString);

        } catch (JSONException ignored) {
            // Check 16 bit uuids (0000xxxx-0000-1000-8000-00805F9B34FB)
            if (uppercaseUuidString.startsWith("0000") && uppercaseUuidString.endsWith("-0000-1000-8000-00805F9B34FB")) {
                final String shortUuidString = uppercaseUuidString.substring(4, 8);
                try {
                    result = mGatUUIDs.getString(shortUuidString);
                } catch (JSONException ignored2) {
                }
            }

        }

        return result;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static String loadStringFromAsset(Context context) {
        String json;
        try {
            InputStream is = context.getAssets().open(kFilename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}
