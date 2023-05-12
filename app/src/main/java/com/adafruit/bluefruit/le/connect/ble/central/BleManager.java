package com.adafruit.bluefruit.le.connect.ble.central;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

public class BleManager {
    // Log
    private final static String TAG = BleManager.class.getSimpleName();

    // Singleton
    private static BleManager mInstance = null;

    // Data
    private BluetoothManager mManager;
    private BluetoothAdapter mAdapter;

    public static BleManager getInstance() {
        if (mInstance == null) {
            mInstance = new BleManager();
        }
        return mInstance;
    }

    private BleManager() {
    }

    public boolean start(Context context) {

        // Init Manager
        mManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        // Init Adapter
        if (mManager != null) {
            mAdapter = mManager.getAdapter();
        } else {
            mAdapter = null;
        }

        final boolean isEnabled = mAdapter != null && mAdapter.isEnabled();
        if (!isEnabled) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }

        return isEnabled;
    }

    /*
    public @Nullable
    BluetoothAdapter getAdapter() {
        return mAdapter;
    }*/

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_SCAN)
    public void cancelDiscovery() {
        if (mAdapter != null) {
            mAdapter.cancelDiscovery();
        }
    }

    public boolean isAdapterEnabled() {
        return mAdapter != null && mAdapter.isEnabled();
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
    public @NonNull
    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> connectedDevices = new ArrayList<>();

        // Check if already initialized
        if (mManager == null) {
            return connectedDevices;
        }

        List<BluetoothDevice> devices = mManager.getConnectedDevices(BluetoothProfile.GATT);
        for (BluetoothDevice device : devices) {
            final int type = device.getType();
            if (type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                connectedDevices.add(device);
            }
        }

        return connectedDevices;
    }
}