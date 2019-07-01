package com.adafruit.bluefruit.le.connect.ble.central;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

// Basic Uart Management. Use it to cache all data received and help parsing it
public class UartDataManager implements BlePeripheralUart.UartRxHandler {
    // Constants
    private final static String TAG = UartDataManager.class.getSimpleName();

    // Listener
    public interface UartDataManagerListener {
        void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier);  // data contents depends on the isRxCacheEnabled flag
    }

    // Data
    //private boolean mIsEnabled = false;
    private boolean mIsRxCacheEnabled;     // If cache is enabled, onUartRx sends the cachedData. Cache can be cleared using removeRxCacheFirst or clearRxCache. If not enabled, onUartRx sends only the latest data received

    private UartDataManagerListener mListener;

    private Map<String, byte[]> mRxDatas = new HashMap<>();
    private Semaphore mRxDataSemaphore = new Semaphore(1, true);

    public UartDataManager(@NonNull Context context, @Nullable UartDataManagerListener listener, boolean isRxCacheEnabled) {     // Is enabled automatically. Call setEnabled(false) to release internal receiver
        mListener = listener;
        mIsRxCacheEnabled = isRxCacheEnabled;
        setEnabled(context, true);
    }

    public void setListener(@Nullable UartDataManagerListener listener) {
        mListener = listener;
    }

    public void setEnabled(@NonNull Context context, boolean enabled) {
        /*
        if (enabled != mIsEnabled) {
            mIsEnabled = enabled;

            if (enabled) {
                registerGattReceiver(context);
            } else {
                unregisterGattReceiver(context);
            }
        }*/
    }

    public void setRxCacheEnabled(boolean enabled) {
        if (!mIsRxCacheEnabled) {
            Log.d(TAG, "Clearing all rx caches");
            mRxDatas.clear();
        }
    }

    public void clearRxCache(@Nullable String peripheralIdentifier) {
        mRxDatas.remove(peripheralIdentifier);
    }

    public void removeRxCacheFirst(int n, @Nullable String peripheralIdentifier) {
        final byte[] rxData = mRxDatas.get(peripheralIdentifier);
        if (rxData != null) {
            if (n < rxData.length) {
                final byte[] data = Arrays.copyOfRange(rxData, n, rxData.length);
                mRxDatas.put(peripheralIdentifier, data);
            } else {
                clearRxCache(peripheralIdentifier);
            }
        }
    }

    @SuppressWarnings("unused")
    public void flushRxCache(@Nullable String peripheralIdentifier) {
        final byte[] rxData = mRxDatas.get(peripheralIdentifier);
        if (rxData != null && rxData.length > 0) {
            try {
                mRxDataSemaphore.acquire();
            } catch (InterruptedException e) {
                Log.w(TAG, "InterruptedException: " + e.toString());
            }

            if (mListener != null) {
                mListener.onUartRx(rxData, peripheralIdentifier);
            }
            mRxDataSemaphore.release();
        }
    }

    // region Send data
    public void send(@NonNull BlePeripheralUart uartPeripheral, @NonNull byte[] data, @Nullable BlePeripheral.CompletionHandler completionHandler) {
        uartPeripheral.uartSend(data, completionHandler);
    }

    // endregion

    // region Received data: UartRxHandler

    @Override
    public void onRxDataReceived(@NonNull byte[] data, @Nullable String identifier, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            return;
        }

        if (mIsRxCacheEnabled) {
            try {
                mRxDataSemaphore.acquire();
            } catch (InterruptedException e) {
                Log.w(TAG, "InterruptedException: " + e.toString());
            }

            // Append new data to previous data
            final byte[] previousData = mRxDatas.get(identifier);
            final int previousDataLength = previousData == null ? 0 : previousData.length;
            byte[] destinationData = new byte[previousDataLength + data.length];
            if (previousData != null) {
                System.arraycopy(previousData, 0, destinationData, 0, previousDataLength);
            }
            System.arraycopy(data, 0, destinationData, previousDataLength, data.length);

            mRxDatas.put(identifier, destinationData);

            // Send data to delegate
            if (mListener != null) {
                mListener.onUartRx(destinationData, identifier);
            }

            mRxDataSemaphore.release();
        } else {
            if (mListener != null) {
                mListener.onUartRx(data, identifier);
            }
        }
    }

    // endregion

    /*
    // region Broadcast Listener
    private void registerGattReceiver(@NonNull Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlePeripheral.kBlePeripheral_OnConnected);
        filter.addAction(BlePeripheral.kBlePeripheral_OnDisconnected);
        LocalBroadcastManager.getInstance(context).registerReceiver(mGattUpdateReceiver, filter);
    }

    private void unregisterGattReceiver(@NonNull Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mGattUpdateReceiver);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String identifier = intent.getStringExtra(BlePeripheral.kExtra_deviceAddress);
            if (identifier != null) {
                if (BlePeripheral.kBlePeripheral_OnConnected.equals(action)) {
                    clearRxCache(identifier);
                } else if (BlePeripheral.kBlePeripheral_OnDisconnected.equals(action)) {
                    clearRxCache(identifier);
                    mRxDataSemaphore.release();     // Force signal if was waiting
                }
            } else {
                Log.w(TAG, "UartDataManager mGattUpdateReceiver with null peripheral");
            }
        }
    };
    // endregion
    */
}