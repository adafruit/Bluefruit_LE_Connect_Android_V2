package com.adafruit.bluefruit.le.connect.models;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.adafruit.bluefruit.le.connect.ble.peripheral.DeviceInformationPeripheralService;
import com.adafruit.bluefruit.le.connect.ble.peripheral.GattServer;
import com.adafruit.bluefruit.le.connect.ble.peripheral.PeripheralService;
import com.adafruit.bluefruit.le.connect.ble.peripheral.UartPeripheralService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class PeripheralModeManager implements GattServer.Listener {
    // Constants
    private final static String TAG = PeripheralModeManager.class.getSimpleName();

    // Data
    private GattServer mPeripheral;
    private DeviceInformationPeripheralService mDeviceInfomationPeripheralService;
    private UartPeripheralService mUartPeripheralService;
    private Semaphore mChangeNameSemaphore = new Semaphore(1, true);
    private WeakReference<GattServer.Listener> mWeakListener;

    // Singleton
    private static PeripheralModeManager sInstance = null;

    public static synchronized PeripheralModeManager getInstance() {
        if (sInstance == null) {
            sInstance = new PeripheralModeManager();
        }

        return sInstance;
    }

    private PeripheralModeManager() {
    }

    // region Setup

    boolean start(@NonNull Context context, @Nullable GattServer.Listener listener) {
        mWeakListener = new WeakReference<>(listener);

        if (GattServer.isPeripheralModeSupported()) {
            mPeripheral = new GattServer(context, this);
            mDeviceInfomationPeripheralService = new DeviceInformationPeripheralService(context);
            mUartPeripheralService = new UartPeripheralService(context);

            mPeripheral.addService(mDeviceInfomationPeripheralService);
            mPeripheral.addService(mUartPeripheralService);

            return mPeripheral.startAdvertising(context);
        } else {
            return false;       // Not available
        }
    }

    void stop(@NonNull Context context) {

        if (mPeripheral != null) {
            mPeripheral.stopAdvertising(/*context*/);
            mPeripheral.removeAllServices();
            mPeripheral.removeListener(context);
        }

        mUartPeripheralService = null;
        mDeviceInfomationPeripheralService = null;
        mPeripheral = null;
    }

    // endregion

    //  region Getters / Setters
    String getLocalName() {
        if (mPeripheral != null) {
            return mPeripheral.getLocalBluetoothName();
        } else {
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    void setLocalName(@NonNull Context context, @Nullable String name, boolean forceStartAdvertising) {
        if (mPeripheral == null) {
            Log.w(TAG, "Error: trying to set peripheral name with null peripheral");
            return;
        }

        try {
            mChangeNameSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mPeripheral.setLocalBluetoothName(name);
        // If it was advertising, stop and restart advertising so the name changes
        if (forceStartAdvertising || mPeripheral.isAdvertising()) {
            mPeripheral.startAdvertising(context);
        } else {
            mChangeNameSemaphore.release();
        }
    }

    public boolean isIncludingDeviceName(@NonNull Context context) {
        return mPeripheral != null && mPeripheral.isIncludingDeviceName(context);
    }

    public void setIncludeDeviceName(@NonNull Context context, boolean enabled) {
        if (mPeripheral != null) {
            mPeripheral.setIncludeDeviceName(context, enabled);

            // Force start advertising
            mPeripheral.startAdvertising(context);
        }
    }

    public @NonNull
    List<PeripheralService> getPeripheralServices() {
        if (mPeripheral != null) {
            return mPeripheral.getServices();
        } else {
            return new ArrayList<>();
        }
    }

    DeviceInformationPeripheralService getDeviceInfomationPeripheralService() {
        return mDeviceInfomationPeripheralService;
    }

    public UartPeripheralService getUartPeripheralService() {
        return mUartPeripheralService;
    }

    public boolean isPeripheralModeAvailable() {
        return mPeripheral != null && mPeripheral.isPeripheralModeAvailable();
    }

    // endregion

    // region Actions


    // endregion

    // region GattServer.Listener
    @Override
    public void onCentralConnectionStatusChanged(int status) {
        Log.d(TAG, "onCentralConnectionStatusChanged");
        mChangeNameSemaphore.release();     // Release if the bluetooth connection state changes

        GattServer.Listener listener = mWeakListener.get();
        if (listener != null) {
            listener.onCentralConnectionStatusChanged(status);
        }
    }

    @Override
    public void onWillStartAdvertising() {
        Log.d(TAG, "onWillStartAdvertising");
        GattServer.Listener listener = mWeakListener.get();
        if (listener != null) {
            listener.onWillStartAdvertising();
        }

    }

    @Override
    public void onDidStartAdvertising() {
        Log.d(TAG, "onDidStartAdvertising");
        mChangeNameSemaphore.release();

        GattServer.Listener listener = mWeakListener.get();
        if (listener != null) {
            listener.onDidStartAdvertising();
        }

    }

    @Override
    public void onDidStopAdvertising() {
        Log.d(TAG, "onDidStopAdvertising");
        GattServer.Listener listener = mWeakListener.get();
        if (listener != null) {
            listener.onDidStopAdvertising();
        }

    }

    @Override
    public void onAdvertisingError(int errorCode) {
        mChangeNameSemaphore.release();

        Log.d(TAG, "onAdvertisingError");
        GattServer.Listener listener = mWeakListener.get();
        if (listener != null) {
            listener.onAdvertisingError(errorCode);
        }
    }

    // endregion
}
