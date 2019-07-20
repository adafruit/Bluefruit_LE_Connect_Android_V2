package com.adafruit.bluefruit.le.connect.ble.peripheral;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class UartPeripheralService extends PeripheralService {
    // Specs: https://learn.adafruit.com/introducing-adafruit-ble-bluetooth-low-energy-friend/uart-service

    // Interfaces
    public interface UartRXListener {
        void onRxDataReceived(@NonNull byte[] data);
    }

    // Service
    static UUID kUartServiceUUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    // Characteristics
    private static UUID kUartTxCharacteristicUUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static UUID kUartRxCharacteristicUUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private BluetoothGattCharacteristic mTxCharacteristic = new BluetoothGattCharacteristic(kUartTxCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
    private BluetoothGattCharacteristic mRxCharacteristic = new BluetoothGattCharacteristic(kUartRxCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattDescriptor mRxConfigDescriptor = new BluetoothGattDescriptor(BlePeripheral.kClientCharacteristicConfigUUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);

    // Data
    //private WeakReference<UartRXListener> mWeakRxListener;
    private UartRXListener mRxListener;

    //
    public UartPeripheralService(Context context) {
        super(context);

        mName = LocalizationManager.getInstance().getString(context, "peripheral_uart_title");
        mRxCharacteristic.addDescriptor(mRxConfigDescriptor);
        // TODO: add TX characteristic extended properties descriptor
        mService = new BluetoothGattService(kUartServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mService.addCharacteristic(mTxCharacteristic);
        mService.addCharacteristic(mRxCharacteristic);
    }

    @Override
    public void setCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
        // Override behaviour for tx and rx
        UUID characteristicUUID = characteristic.getUuid();
        if (characteristicUUID.equals(kUartTxCharacteristicUUID)) {
            setTx(value);
        } else if (characteristicUUID.equals(kUartRxCharacteristicUUID)) {
            setRx(value);
        } else {
            super.setCharacteristic(characteristic, value);
        }
    }

    public byte[] getTx() {
        return mTxCharacteristic.getValue();
    }

    private void setTx(byte[] data) {
        mTxCharacteristic.setValue(data);

        UartRXListener rxListener = mRxListener;//mWeakRxListener.get();
        if (rxListener != null) {
            rxListener.onRxDataReceived(data);
        }
    }

    public byte[] getRx() {
        return mRxCharacteristic.getValue();
    }

    void setRx(byte[] data) {
        mRxCharacteristic.setValue(data);

        if (mListener != null) {
            BluetoothDevice[] devices = getDevicesSubscribedToCharacteristic(kUartRxCharacteristicUUID);
            if (devices != null) {
                mListener.updateValue(devices, mRxCharacteristic);
            }
        }
    }

    public void uartEnable(@Nullable UartRXListener listener) {
        //mWeakRxListener = new WeakReference<>(listener);
        mRxListener = listener;
    }
}
