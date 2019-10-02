package com.adafruit.bluefruit.le.connect.ble.central;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BlePeripheralUart {
    // Log
    private final static String TAG = BlePeripheralUart.class.getSimpleName();

    // Constants
    private static final UUID kUartServiceUUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID kUartTxCharacteristicUUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID kUartRxCharacteristicUUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    //private static final int kUartTxMaxBytes = 20;
    private static final int kUartReplyDefaultTimeout = 2000;       // in millis

    // Interfaces
    public interface UartRxHandler {
        void onRxDataReceived(@NonNull byte[] data, @Nullable String identifier, int status);
    }

    // Data
    @NonNull
    private BlePeripheral mBlePeripheral;
    private BluetoothGattCharacteristic mUartTxCharacteristic;
    private BluetoothGattCharacteristic mUartRxCharacteristic;
    private int mUartTxCharacteristicWriteType;
    private boolean mIsSendSequentiallyCancelled = false;

    // region Initialization
    public BlePeripheralUart(@NonNull BlePeripheral blePeripheral) {
        super();
        mBlePeripheral = blePeripheral;
    }

    public void uartEnable(@Nullable UartRxHandler uartRxHandler, @Nullable BlePeripheral.CompletionHandler completionHandler) {

        // Get uart communications characteristic
        mUartTxCharacteristic = mBlePeripheral.getCharacteristic(kUartTxCharacteristicUUID, kUartServiceUUID);
        mUartRxCharacteristic = mBlePeripheral.getCharacteristic(kUartRxCharacteristicUUID, kUartServiceUUID);
        if (mUartTxCharacteristic != null && mUartRxCharacteristic != null) {
            Log.d(TAG, "Uart Enable for: " + getName());

            mUartTxCharacteristicWriteType = mUartTxCharacteristic.getWriteType();

            // Prepare notification handler
            WeakReference<UartRxHandler> weakUartRxHandler = new WeakReference<>(uartRxHandler);
            final String identifier = mBlePeripheral.getIdentifier();
            BlePeripheral.NotifyHandler notifyHandler = uartRxHandler == null ? null : status -> {
                UartRxHandler handler = weakUartRxHandler.get();
                if (handler != null) {
                    byte[] data = mUartRxCharacteristic.getValue();
                    handler.onRxDataReceived(data, identifier, status);
                }
            };

            // Check if already notifying (read client characteristic config descriptor to check it)
            mBlePeripheral.readDescriptor(mUartRxCharacteristic, BlePeripheral.kClientCharacteristicConfigUUID, status -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Enable notifications
                    if (!BlePeripheral.isCharacteristicNotifyingForCachedClientConfigDescriptor(mUartRxCharacteristic)) {
                        mBlePeripheral.characteristicEnableNotify(mUartRxCharacteristic, notifyHandler, completionHandler);
                    } else {
                        mBlePeripheral.characteristicUpdateNotify(mUartRxCharacteristic, notifyHandler);
                        if (completionHandler != null) {
                            completionHandler.completion(BluetoothGatt.GATT_SUCCESS);
                        }
                    }
                } else {
                    if (completionHandler != null) {
                        completionHandler.completion(status);
                    }
                }
            });
        }
    }

    public boolean isUartEnabled() {
        return mUartRxCharacteristic != null && mUartTxCharacteristic != null && BlePeripheral.isCharacteristicNotifyingForCachedClientConfigDescriptor(mUartRxCharacteristic);
    }

    public void uartDisable() {
        // Disable notify
        if (mUartRxCharacteristic != null) {
            Log.d(TAG, "Uart Disable");
            mBlePeripheral.characteristicDisableNotify(mUartRxCharacteristic, null);
        }

        // Clear all Uart specific data
        mUartRxCharacteristic = null;
        mUartTxCharacteristic = null;
    }

    public void disconnect() {
        mBlePeripheral.disconnect();
    }

    public String getIdentifier() {
        return mBlePeripheral.getIdentifier();
    }

    public String getName() {
        return mBlePeripheral.getName();
    }

    public void requestMtu(@IntRange(from = 23, to = 517) int mtuSize, BlePeripheral.CompletionHandler completionHandler) {
        mBlePeripheral.requestMtu(mtuSize, completionHandler);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void readPhy() {
        mBlePeripheral.readPhy();
    }

    // endregion

    // region Send
    void uartSend(@NonNull byte[] data, @Nullable BlePeripheral.CompletionHandler completionHandler) {
        if (mUartTxCharacteristic == null) {
            Log.e(TAG, "Command Error: characteristic no longer valid");
            if (completionHandler != null) {
                completionHandler.completion(BluetoothGatt.GATT_FAILURE);
            }
            return;
        }

        // Split data in kUartTxMaxBytes bytes packets
        int offset = 0;
        AtomicInteger worseStatus = new AtomicInteger(BluetoothGatt.GATT_SUCCESS);

        do {
            final int packetSize = Math.min(data.length - offset, mBlePeripheral.getMaxPacketLength());
            final byte[] packet = Arrays.copyOfRange(data, offset, offset + packetSize);
            offset += packetSize;

            final int finalOffset = offset;
            mBlePeripheral.writeCharacteristic(mUartTxCharacteristic, mUartTxCharacteristicWriteType, packet, status -> {
                //Log.d(TAG, "next offset:"+finalOffset);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "uart tx write (hex): " + BleUtils.bytesToHex2(packet));
                } else {
                    worseStatus.set(status);
                    Log.w(TAG, "Error " + status + " writing packet");
                }

                if (finalOffset >= data.length && completionHandler != null) {
                    completionHandler.completion(worseStatus.get());
                }
            });

        } while (offset < data.length);
    }

    void sendEachPacketSequentially(@NonNull byte[] data, int withResponseEveryPacketCount, BlePeripheral.ProgressHandler progressHandler, BlePeripheral.CompletionHandler completionHandler) {
        if (mUartTxCharacteristic == null) {
            Log.e(TAG, "Command Error: characteristic no longer valid");
            if (completionHandler != null) {
                completionHandler.completion(BluetoothGatt.GATT_FAILURE);
            }
            return;
        }

        mIsSendSequentiallyCancelled = false;

        uartSendPacket(data, 0, mUartTxCharacteristic, withResponseEveryPacketCount, withResponseEveryPacketCount, progressHandler, completionHandler);
    }

    void cancelOngoingSendPacketSequentiallyInThread() {
        mIsSendSequentiallyCancelled = true;
    }

    private void uartSendPacket(@NonNull byte[] data, int offset, BluetoothGattCharacteristic uartTxCharacteristic, int withResponseEveryPacketCount, int numPacketsRemainingForDelay, BlePeripheral.ProgressHandler progressHandler, BlePeripheral.CompletionHandler completionHandler) {
        final int packetSize = Math.min(data.length - offset, mBlePeripheral.getMaxPacketLength());
        final byte[] packet = Arrays.copyOfRange(data, offset, offset + packetSize);
        final int writeStartingOffset = offset;
        final int uartTxCharacteristicWriteType = numPacketsRemainingForDelay <= 0 ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;          // Send a packet WRITE_TYPE_DEFAULT to force wait until receive response and avoid dropping packets if the peripheral is not processing them fast enough

        mBlePeripheral.writeCharacteristic(uartTxCharacteristic, uartTxCharacteristicWriteType, packet, status -> {
            int writtenSize = writeStartingOffset;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Error " + status + " writing packet at offset" + writeStartingOffset + " Error: " + status);
            } else {
                Log.d(TAG, "uart tx " + (uartTxCharacteristicWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE ? "withoutResponse" : "withResponse") + " offset " + writeStartingOffset + ": " + BleUtils.bytesToHex2(packet));

                writtenSize += packet.length;

                if (!mIsSendSequentiallyCancelled && writtenSize < data.length) {
                    //int finalWrittenSize = writtenSize;
                    //handler.postDelayed(() -> uartSendPacket(handler, data, finalWrittenSize, uartTxCharacteristic, uartTxCharacteristicWriteType, delayBetweenPackets, progressHandler, completionHandler), delayBetweenPackets);
                    uartSendPacket(data, writtenSize, uartTxCharacteristic, withResponseEveryPacketCount, numPacketsRemainingForDelay <= 0 ? withResponseEveryPacketCount : numPacketsRemainingForDelay - 1, progressHandler, completionHandler);
                }
            }

            if (mIsSendSequentiallyCancelled) {
                completionHandler.completion(BluetoothGatt.GATT_SUCCESS);
            } else if (writtenSize >= data.length) {
                progressHandler.progress(1);
                completionHandler.completion(status);
            } else {
                progressHandler.progress(writtenSize / (float) data.length);
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    void uartSendAndWaitReply(@NonNull byte[] data, @Nullable BlePeripheral.CompletionHandler writeCompletionHandler, @NonNull BlePeripheral.CaptureReadCompletionHandler readCompletionHandler) {
        uartSendAndWaitReply(data, writeCompletionHandler, kUartReplyDefaultTimeout, readCompletionHandler);
    }

    void uartSendAndWaitReply(@NonNull byte[] data, @Nullable BlePeripheral.CompletionHandler writeCompletionHandler, int readTimeout, @NonNull BlePeripheral.CaptureReadCompletionHandler readCompletionHandler) {
        if (mUartTxCharacteristic == null || mUartRxCharacteristic == null) {
            Log.e(TAG, "Error: uart characteristics no longer valid");
            if (writeCompletionHandler != null) {
                writeCompletionHandler.completion(BluetoothGatt.GATT_FAILURE);
            } else {  // If no writeCompletion defined, move the error result to the readCompletion
                readCompletionHandler.read(BluetoothGatt.GATT_FAILURE, null);
            }
            return;
        }

        // Split data in kUartTxMaxBytes bytes packets
        int offset = 0;
        do {
            final int packetSize = Math.min(data.length - offset, mBlePeripheral.getMaxPacketLength());
            final byte[] packet = Arrays.copyOfRange(data, offset, offset + packetSize);
            offset += packetSize;

            final int finalOffset = offset;
            mBlePeripheral.writeCharacteristicAndCaptureNotify(mUartTxCharacteristic, mUartTxCharacteristicWriteType, packet, status -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "uart tx writeAndWait (hex): " + BleUtils.bytesToHex2(packet));
                } else {
                    Log.w(TAG, "Error " + status + " writing packet");
                }

                if (finalOffset >= data.length && writeCompletionHandler != null) {
                    writeCompletionHandler.completion(status);
                }
            }, mUartRxCharacteristic, readTimeout, readCompletionHandler);
        } while (offset < data.length);
    }

    public static byte[] appendCrc(byte[] data) {
        // Calculate checksum
        byte checksum = 0;
        for (byte aData : data) {
            checksum += aData;
        }
        checksum = (byte) (~checksum);       // Invert

        // Add crc to data
        byte[] dataCrc = new byte[data.length + 1];
        System.arraycopy(data, 0, dataCrc, 0, data.length);
        dataCrc[data.length] = checksum;

        return dataCrc;
    }

    // endregion

    // region Utils
    @SuppressWarnings("unused")
    public static boolean isUartAdvertised(@NonNull BlePeripheral blePeripheral) {
        List<ParcelUuid> serviceUuids = blePeripheral.getScanRecord().getServiceUuids();
        boolean found = false;

        if (serviceUuids != null) {
            int i = 0;
            while (i < serviceUuids.size() && !found) {
                found = serviceUuids.get(i).getUuid().equals(kUartServiceUUID);
                i++;
            }
        }
        return found;
    }

    public static boolean hasUart(@NonNull BlePeripheral peripheral) {
        return peripheral.getService(kUartServiceUUID) != null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isUartInitialized(@NonNull BlePeripheral blePeripheral, @NonNull List<BlePeripheralUart> blePeripheralUarts) {
        BlePeripheralUart blePeripheralUart = getBlePeripheralUart(blePeripheral, blePeripheralUarts);
        return blePeripheralUart != null && blePeripheralUart.isUartEnabled();
    }

    private @Nullable
    static BlePeripheralUart getBlePeripheralUart(@NonNull BlePeripheral blePeripheral, @NonNull List<BlePeripheralUart> blePeripheralUarts) {

        BlePeripheralUart result = null;

        String identifier = blePeripheral.getIdentifier();
        boolean found = false;
        int i = 0;
        while (i < blePeripheralUarts.size() && !found) {
            BlePeripheralUart blePeripheralUart = blePeripheralUarts.get(i);
            if (blePeripheralUart.getIdentifier().equals(identifier)) {
                found = true;
                result = blePeripheralUart;
            } else {
                i++;
            }
        }
        return result;
    }

    // endregion
}
