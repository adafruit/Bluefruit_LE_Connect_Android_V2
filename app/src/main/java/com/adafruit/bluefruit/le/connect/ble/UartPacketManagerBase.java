package com.adafruit.bluefruit.le.connect.ble;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class UartPacketManagerBase implements BlePeripheralUart.UartRxHandler {
    // Log
    private final static String TAG = UartPacketManagerBase.class.getSimpleName();

    // Listener
    public interface Listener {
        void onUartPacket(UartPacket packet);
    }

    // Data
    //private boolean mIsEnabled = false;
    protected final Handler mMainHandler = new Handler(Looper.getMainLooper());
    protected WeakReference<Listener> mWeakListener;
    protected List<UartPacket> mPackets = new ArrayList<>();
    protected Semaphore mPacketsSemaphore = new Semaphore(1, true);
    private boolean mIsPacketCacheEnabled;
    protected Context mContext;
    protected MqttManager mMqttManager;

    protected long mReceivedBytes = 0;
    protected long mSentBytes = 0;

    public UartPacketManagerBase(@NonNull Context context, @Nullable Listener listener, boolean isPacketCacheEnabled, @Nullable MqttManager mqttManager) {
        mContext = context.getApplicationContext();
        mIsPacketCacheEnabled = isPacketCacheEnabled;
        mMqttManager = mqttManager;
        mWeakListener = new WeakReference<>(listener);
    }

    // region Received data: UartRxHandler

    @Override
    public void onRxDataReceived(@NonNull byte[] data, @Nullable String identifier, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "onRxDataReceived error:" + status);
            return;
        }

        UartPacket uartPacket = new UartPacket(identifier, UartPacket.TRANSFERMODE_RX, data);

        // Mqtt publish to RX
        if (mMqttManager != null) {
            if (MqttSettings.isPublishEnabled(mContext)) {
                final String topic = MqttSettings.getPublishTopic(mContext, MqttSettings.kPublishFeed_RX);
                if (topic != null) {
                    final int qos = MqttSettings.getPublishQos(mContext, MqttSettings.kPublishFeed_RX);
                    mMqttManager.publish(topic, uartPacket.getData(), qos);
                }
            }
        }

        try {
            mPacketsSemaphore.acquire();
        } catch (InterruptedException e) {
            Log.w(TAG, "InterruptedException: " + e.toString());
        }
        mReceivedBytes += data.length;
        if (mIsPacketCacheEnabled) {
            mPackets.add(uartPacket);
        }

        // Send data to delegate
        Listener listener = mWeakListener.get();
        if (listener != null) {
            mMainHandler.post(() -> listener.onUartPacket(uartPacket));
        }

        mPacketsSemaphore.release();
    }

    public void clearPacketsCache() {
        mPackets.clear();
    }

    public List<UartPacket> getPacketsCache() {
        return mPackets;
    }

    /*
    public void setEnabled(@NonNull Context context, boolean enabled) {
        if (enabled != mIsEnabled) {
            mIsEnabled = enabled;

            if (enabled) {
                registerGattReceiver(context);
            } else {
                unregisterGattReceiver(context);
            }
        }
    }
    */
    // endregion

    // region Counters
    public void resetCounters() {
        mReceivedBytes = 0;
        mSentBytes = 0;
    }

    public long getReceivedBytes() {
        return mReceivedBytes;
    }

    public long getSentBytes() {
        return mSentBytes;
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
                    clearPacketsCache();
                } else if (BlePeripheral.kBlePeripheral_OnDisconnected.equals(action)) {
                    clearPacketsCache();
                    mPacketsSemaphore.release();     // Force signal if was waiting
                }
            } else {
                Log.w(TAG, "UartPacketManagerBase mGattUpdateReceiver with null peripheral");
            }
        }
    };
    // endregion
    */
}
