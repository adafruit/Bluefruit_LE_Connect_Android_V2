package com.adafruit.bluefruit.le.connect.ble.peripheral;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static android.content.Context.MODE_PRIVATE;

public class PeripheralService {
    // Constants
    private static final String kPreferences = "PeripheralService_prefs";

    // Interface
    interface Listener {
        void updateValue(@NonNull BluetoothDevice[] devices, @NonNull BluetoothGattCharacteristic characteristic);
        // TODO: add support for indications
    }

    // Data
    protected String mName;
    protected boolean mIsEnabled = true;
    protected BluetoothGattService mService;
    private Map<UUID, Set<BluetoothDevice>> mSubscribedCharacteristics = new HashMap<>();
    protected Listener mListener;

    PeripheralService(Context context) {
        mName = LocalizationManager.getInstance().getString(context, "peripheral_unknown_title");
    }

    BluetoothGattService getService() {
        return mService;
    }

    // region Setters / Getters
    public String getName() {
        return mName;
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setEnabled(@NonNull Context context, boolean enabled) {
        mIsEnabled = enabled;
        saveValues(context);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    // endregion

    // region Actions

    public BluetoothGattCharacteristic getCharacteristic(@NonNull UUID uuid) {
        return mService.getCharacteristic(uuid);
    }

    public void setCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
        characteristic.setValue(value);
    }

    public void subscribe(@NonNull UUID characteristicUuid, @NonNull BluetoothDevice device) {
        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            existingSubscribedCharacteristic.add(device);
        } else {
            Set<BluetoothDevice> devices = new HashSet<>();
            devices.add(device);
            mSubscribedCharacteristics.put(characteristicUuid, devices);
        }
    }

    public void unsubscribe(@NonNull UUID characteristicUuid, @NonNull BluetoothDevice device) {
        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            existingSubscribedCharacteristic.remove(device);
        }
    }

    public @Nullable BluetoothDevice[] getDevicesSubscribedToCharacteristic(@NonNull UUID characteristicUuid) {
        BluetoothDevice[] devices = null;

        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            devices = existingSubscribedCharacteristic.toArray(new BluetoothDevice[existingSubscribedCharacteristic.size()]);
        }

        return devices;
    }

    // region Persistence
    protected void loadValues(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, MODE_PRIVATE);
        mIsEnabled = preferences.getBoolean(mService.getUuid().toString() + "_isEnabled", true);
    }

    public void saveValues(Context context) {
        SharedPreferences.Editor preferencesEditor = context.getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
        preferencesEditor.putBoolean(mService.getUuid().toString() + "_isEnabled", mIsEnabled);
        List<BluetoothGattCharacteristic> characteristics = mService.getCharacteristics();
        preferencesEditor.apply();
    }
    // endregion

}
