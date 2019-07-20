package com.adafruit.bluefruit.le.connect.ble.peripheral;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.util.HashMap;
import java.util.HashSet;
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
    String mName;
    private boolean mIsEnabled = true;
    BluetoothGattService mService;
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

    BluetoothGattCharacteristic getCharacteristic(@NonNull UUID uuid) {
        return mService.getCharacteristic(uuid);
    }

    public void setCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
        characteristic.setValue(value);
    }

    void subscribe(@NonNull UUID characteristicUuid, @NonNull BluetoothDevice device) {
        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            existingSubscribedCharacteristic.add(device);
        } else {
            Set<BluetoothDevice> devices = new HashSet<>();
            devices.add(device);
            mSubscribedCharacteristics.put(characteristicUuid, devices);
        }
    }

    void unsubscribe(@NonNull UUID characteristicUuid, @NonNull BluetoothDevice device) {
        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            existingSubscribedCharacteristic.remove(device);
        }
    }

    @Nullable
    BluetoothDevice[] getDevicesSubscribedToCharacteristic(@NonNull UUID characteristicUuid) {
        BluetoothDevice[] devices = null;

        Set<BluetoothDevice> existingSubscribedCharacteristic = mSubscribedCharacteristics.get(characteristicUuid);
        if (existingSubscribedCharacteristic != null) {
            devices = existingSubscribedCharacteristic.toArray(new BluetoothDevice[0]);
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
        //List<BluetoothGattCharacteristic> characteristics = mService.getCharacteristics();
        preferencesEditor.apply();
    }
    // endregion

}
