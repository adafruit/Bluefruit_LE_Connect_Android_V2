package com.adafruit.bluefruit.le.connect.ble;

import java.util.Arrays;

public class UartPacket {
    public static final int TRANSFERMODE_TX = 0;
    public static final int TRANSFERMODE_RX = 1;

    private String mPeripheralId;
    private long mTimestamp;        // in millis
    private int mMode;
    private byte[] mData;


    public UartPacket(String peripheralId, int mode, byte[] data) {
        this(peripheralId, System.currentTimeMillis(), mode, data);
    }

    public UartPacket(UartPacket uartPacket) {
        mPeripheralId = uartPacket.mPeripheralId;
        mTimestamp = uartPacket.mTimestamp;
        mMode = uartPacket.mMode;
        if (uartPacket.mData != null) {
            mData = Arrays.copyOf(uartPacket.mData, uartPacket.mData.length);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public UartPacket(String peripheralId, long timestamp, int mode, byte[] data) {
        mPeripheralId = peripheralId;
        mTimestamp = timestamp;
        mMode = mode;
        mData = data;
    }

    public String getPeripheralId() {
        return mPeripheralId;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public int getMode() {
        return mMode;
    }

    public byte[] getData() {
        return mData;
    }
}
