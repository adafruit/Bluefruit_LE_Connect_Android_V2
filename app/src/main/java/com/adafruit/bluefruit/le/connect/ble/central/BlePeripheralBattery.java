package com.adafruit.bluefruit.le.connect.ble.central;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.util.UUID;

public class BlePeripheralBattery {
    // Log
    private final static String TAG = BlePeripheralBattery.class.getSimpleName();

    // Constants
    private static final UUID kBatteryServiceUuid = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    private static final UUID kBatteryCharacteristicUuid = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");

    // Interfaces
    public interface UpdateHandler {
        void onBatteryLevelChanged(int level);
    }

    // Data
    @NonNull
    private final BlePeripheral mBlePeripheral;
    private int mCurrentBatteryLevel = -1;

    // region Initialization
    public BlePeripheralBattery(@NonNull BlePeripheral blePeripheral) {
        super();
        mBlePeripheral = blePeripheral;
    }

    // endregion

    // region Actions

    public static boolean hasBattery(BlePeripheral peripheral) {
        return peripheral.getService(kBatteryServiceUuid) != null;
    }

    public String getIdentifier() {
        return mBlePeripheral.getIdentifier();
    }

    public int getCurrentBatteryLevel() {
        return mCurrentBatteryLevel;            // -1 if value has not been read
    }

    private int getBatteryLevel(@NonNull BluetoothGattCharacteristic batteryCharacteristic) {
        return batteryCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
    }

    public void startReadingBatteryLevel(@NonNull UpdateHandler updateHandler) {
        BluetoothGattCharacteristic batteryCharacteristic = mBlePeripheral.getCharacteristic(kBatteryCharacteristicUuid, kBatteryServiceUuid);

        if (batteryCharacteristic == null) {
            Log.w(TAG, "Error starting read battery level. characteristic not found");
            return;
        }

        // Read current value
        mBlePeripheral.readCharacteristic(batteryCharacteristic, (status, data) -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCurrentBatteryLevel = getBatteryLevel(batteryCharacteristic);
                updateHandler.onBatteryLevelChanged(mCurrentBatteryLevel);
            } else {
                Log.w(TAG, "Error reading battery level");
            }
        });

        // Enable notifications to receive value changes
        mBlePeripheral.characteristicEnableNotify(batteryCharacteristic, status -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCurrentBatteryLevel = getBatteryLevel(batteryCharacteristic);
                updateHandler.onBatteryLevelChanged(mCurrentBatteryLevel);
            } else {
                Log.w(TAG, "Error reading notify battery level");
            }
        }, null);
    }

    public void stopReadingBatteryLevel(@Nullable BlePeripheral.CompletionHandler completionHandler) {
        BluetoothGattCharacteristic batteryCharacteristic = mBlePeripheral.getCharacteristic(kBatteryCharacteristicUuid, kBatteryServiceUuid);
        if (batteryCharacteristic == null) {
            Log.w(TAG, "Error stopping battery level. characteristic not found");
            return;
        }

        mBlePeripheral.characteristicDisableNotify(batteryCharacteristic, status -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Stopped reading battery level");
            } else {
                Log.w(TAG, "Error stopping notify battery level");
            }

            if (completionHandler != null) {
                completionHandler.completion(status);
            }
        });
    }

    // endregion
}
