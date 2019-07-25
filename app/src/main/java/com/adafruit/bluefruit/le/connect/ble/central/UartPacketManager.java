package com.adafruit.bluefruit.le.connect.ble.central;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.util.Log;

import com.adafruit.bluefruit.le.connect.ble.UartPacket;
import com.adafruit.bluefruit.le.connect.ble.UartPacketManagerBase;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;

import java.nio.charset.Charset;

public class UartPacketManager extends UartPacketManagerBase {
    // Log
    private final static String TAG = UartPacketManager.class.getSimpleName();

    // region Lifecycle
    public UartPacketManager(@NonNull Context context, @Nullable UartPacketManagerBase.Listener listener, boolean isPacketCacheEnabled, @Nullable MqttManager mqttManager) {
        super(context, listener, isPacketCacheEnabled, mqttManager);

    }
    // endregion

    // region Send data

    public void send(@NonNull BlePeripheralUart uartPeripheral, @NonNull byte[] data, BlePeripheral.CompletionHandler completionHandler) {
        mSentBytes += data.length;
        uartPeripheral.uartSend(data, completionHandler);
    }

    public void sendAndWaitReply(@NonNull BlePeripheralUart uartPeripheral, @NonNull byte[] data, @NonNull BlePeripheral.CaptureReadCompletionHandler readCompletionHandler) {
        mSentBytes += data.length;
        uartPeripheral.uartSendAndWaitReply(data, null, readCompletionHandler);
    }

    public void sendAndWaitReply(@NonNull BlePeripheralUart uartPeripheral, @NonNull byte[] data, @Nullable BlePeripheral.CompletionHandler writeCompletionHandler, int readTimeout, @NonNull BlePeripheral.CaptureReadCompletionHandler readCompletionHandler) {
        mSentBytes += data.length;
        uartPeripheral.uartSendAndWaitReply(data, writeCompletionHandler, readTimeout, readCompletionHandler);
    }

    public void send(@NonNull BlePeripheralUart uartPeripheral, @NonNull String text) {
        send(uartPeripheral, text, false);
    }

    public void send(@NonNull BlePeripheralUart uartPeripheral, @NonNull String text, boolean wasReceivedFromMqtt) {
        if (mMqttManager != null) {
            // Mqtt publish to TX
            if (MqttSettings.isPublishEnabled(mContext)) {
                final String topic = MqttSettings.getPublishTopic(mContext, MqttSettings.kPublishFeed_TX);
                if (topic != null) {
                    final int qos = MqttSettings.getPublishQos(mContext, MqttSettings.kPublishFeed_TX);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }

        // Create data and send to Uart
        byte[] data = text.getBytes(Charset.forName("UTF-8"));
        UartPacket uartPacket = new UartPacket(uartPeripheral.getIdentifier(), UartPacket.TRANSFERMODE_TX, data);

        try {
            mPacketsSemaphore.acquire();        // don't append more data, till the delegate has finished processing it
        } catch (InterruptedException e) {
            Log.w(TAG, "InterruptedException: " + e.toString());
        }
        mPacketsSemaphore.release();

        Listener listener = mWeakListener.get();
        mPackets.add(uartPacket);
        if (listener != null) {
            mMainHandler.post(() -> listener.onUartPacket(uartPacket));
        }

        final boolean isMqttEnabled = mMqttManager != null;
        final boolean shouldBeSent = !wasReceivedFromMqtt || (isMqttEnabled && MqttSettings.getSubscribeBehaviour(mContext) == MqttSettings.kSubscribeBehaviour_Transmit);

        if (shouldBeSent) {
            send(uartPeripheral, data, null);
        }
    }

    public void sendEachPacketSequentially(@NonNull BlePeripheralUart uartPeripheral, @NonNull byte[] data, int withResponseEveryPacketCount, BlePeripheral.ProgressHandler progressHandler, BlePeripheral.CompletionHandler completionHandler) {
        mSentBytes += data.length;
        uartPeripheral.sendEachPacketSequentially(data, withResponseEveryPacketCount, progressHandler, completionHandler);
    }

    public void cancelOngoingSendPacketSequentiallyInThread(@NonNull BlePeripheralUart uartPeripheral) {
        uartPeripheral.cancelOngoingSendPacketSequentiallyInThread();
    }

    // endregion
}
