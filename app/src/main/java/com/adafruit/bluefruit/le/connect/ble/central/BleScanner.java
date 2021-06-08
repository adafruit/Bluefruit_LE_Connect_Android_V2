package com.adafruit.bluefruit.le.connect.ble.central;

import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class BleScanner {
    // Config
    private static final int kScanReportDelay = 500;     // in milliseconds

    // Constants
    private final static String TAG = BleScanner.class.getSimpleName();

    // Singleton
    private static BleScanner mInstance = null;

    // Interfaces
    public interface BleScannerListener {
        void onScanPeripheralsUpdated(List<BlePeripheral> scanResults);

        void onScanPeripheralsFailed(int errorCode);

        void onScanStatusChanged(boolean isScanning);
    }

    // Data
    private WeakReference<BleScannerListener> mWeakListener;
    private final List<BlePeripheral> mPeripheralScanResults = new ArrayList<>();
    private List<ScanFilter> mScanFilters = new ArrayList<>();
    private boolean mIsScanning;

    private final ScanCallback mScanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            peripheralDiscovered(result);

            BleScannerListener listener = mWeakListener.get();
            if (listener != null) {
                listener.onScanPeripheralsUpdated(mPeripheralScanResults);
            }
        }

        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                peripheralDiscovered(result);
            }

            BleScannerListener listener = mWeakListener.get();
            if (listener != null) {
                //Log.d(TAG, "mPeripheralScanResults: " + mPeripheralScanResults.size());
                listener.onScanPeripheralsUpdated(mPeripheralScanResults);
            }
        }

        public void onScanFailed(int errorCode) {
            BleScannerListener listener = mWeakListener.get();
            if (listener != null) {
                listener.onScanPeripheralsFailed(errorCode);
            }
            stop();
        }
    };

    // region Setup
    public static BleScanner getInstance() {
        if (mInstance == null) {
            mInstance = new BleScanner();
        }
        return mInstance;
    }

    private BleScanner() {
        mIsScanning = false;
    }

    public BleScannerListener getListener() {
        return mWeakListener.get();
    }

    public void setListener(BleScannerListener listener) {
        mWeakListener = new WeakReference<>(listener);
    }

    // endregion

    // region Properties
    public boolean isScanning() {
        return mIsScanning;
    }

    public @NonNull
    List<BlePeripheral> getConnectedPeripherals() {
        List<BlePeripheral> connectedPeripherals = new ArrayList<>();
        for (BlePeripheral blePeripheral : mPeripheralScanResults) {
            final int state = blePeripheral.getConnectionState();
            if (state == BlePeripheral.STATE_CONNECTED) {
                connectedPeripherals.add(blePeripheral);
            }
        }

        return connectedPeripherals;
    }

    @SuppressWarnings("WeakerAccess")
    public @NonNull
    List<BlePeripheral> getConnectedOrConnectingPeripherals() {
        List<BlePeripheral> connectedPeripherals = new ArrayList<>();
        for (BlePeripheral blePeripheral : mPeripheralScanResults) {
            final int state = blePeripheral.getConnectionState();
            if (state == BlePeripheral.STATE_CONNECTED || state == BlePeripheral.STATE_CONNECTING) {
                connectedPeripherals.add(blePeripheral);
            }
        }

        return connectedPeripherals;
    }

    public void disconnectFromAll() {
        List<BlePeripheral> connectedPeriperals = getConnectedOrConnectingPeripherals();

        Log.d(TAG, "disconnectFromAll. Number of connected: " + connectedPeriperals.size());
        for (BlePeripheral blePeripheral : connectedPeriperals) {
            blePeripheral.disconnect();
        }
    }

    @Nullable
    public BlePeripheral getPeripheralWithIdentifier(@Nullable String identifier) {
        if (identifier == null) {
            return null;
        }

        int i = 0;
        BlePeripheral foundPeripheral = null;
        while (i < mPeripheralScanResults.size() && foundPeripheral == null) {
            BlePeripheral blePeripheral = mPeripheralScanResults.get(i);
            if (blePeripheral.getIdentifier().equals(identifier)) {
                foundPeripheral = blePeripheral;
            }
            i++;
        }

        return foundPeripheral;
    }

    // endregion

    // region Actions
    public void start() {
        startWithFilters(null);
    }

    @SuppressWarnings("unused")
    public void startFilteringServiceUuid(ParcelUuid uuid) {

        List<ScanFilter> scanFilters = new ArrayList<>();
        if (uuid != null) {
            scanFilters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
        }

        startWithFilters(scanFilters);
    }

    //     @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    private synchronized void startWithFilters(@Nullable List<ScanFilter> filters) {
        if (!mIsScanning) {
            BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(kScanReportDelay)
                    .setUseHardwareBatchingIfSupported(false).build();
            mScanFilters = filters;
            try {
                scanner.startScan(mScanFilters, settings, mScanCallback);
                mIsScanning = true;
                BleScannerListener listener = mWeakListener.get();
                if (listener != null) {
                    listener.onScanStatusChanged(true);
                }
            } catch (IllegalStateException e) {     // Exception if the BT adapter is not on
                Log.d(TAG, "startWithFilters illegalStateExcpetion" + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (mIsScanning) {
            BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(mScanCallback);
            mIsScanning = false;
            BleScannerListener listener = mWeakListener.get();
            if (listener != null) {
                listener.onScanStatusChanged(false);
            }
        }
    }

    public synchronized void refresh() {
        stop();

        // Don't remove connnected or connecting peripherals
        for (ListIterator<BlePeripheral> listIterator = mPeripheralScanResults.listIterator(); listIterator.hasNext(); ) {
            BlePeripheral blePeripheral = listIterator.next();
            if (blePeripheral.getConnectionState() == BlePeripheral.STATE_DISCONNECTED) {
                listIterator.remove();
            }
        }

        startWithFilters(mScanFilters);

        BleScannerListener listener = mWeakListener.get();
        if (listener != null) {
            listener.onScanPeripheralsUpdated(mPeripheralScanResults);
        }
    }
    // endregion

    // region

    private void peripheralDiscovered(@NonNull ScanResult result) {
        // Check that the device was not previously found
        final String resultAddress = result.getDevice().getAddress();

        int index = 0;
        boolean found = false;
        while (index < mPeripheralScanResults.size() && !found) {
            if (mPeripheralScanResults.get(index).getIdentifier().equals(resultAddress)) {
                found = true;
            } else {
                index++;
            }
        }

        if (found) {
            // Replace existing record
            mPeripheralScanResults.get(index).replaceScanResult(result);
        } else {
            // Add mew record
            BlePeripheral blePeripheral = new BlePeripheral(result);
            mPeripheralScanResults.add(blePeripheral);
        }
    }

    // endregion

    // region Utils
    @SuppressWarnings("WeakerAccess")
    public static final int kDeviceType_Unknown = 0;
    public static final int kDeviceType_Uart = 1;
    public static final int kDeviceType_Beacon = 2;
    public static final int kDeviceType_UriBeacon = 3;

    public static int getDeviceType(@NonNull BlePeripheral blePeripheral) {
        int type = kDeviceType_Unknown;

        ScanRecord scanRecord = blePeripheral.getScanRecord();
        if (scanRecord != null) {
            byte[] advertisedData = scanRecord.getBytes();

            // Check if is an iBeacon ( 0x02, 0x01, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
            final boolean isBeacon = advertisedData != null && advertisedData.length > 8 && advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;
            if (isBeacon) {
                type = kDeviceType_Beacon;
            } else {
                // Check if is an URIBeacon
                final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
                final boolean isUriBeacon = advertisedData != null && advertisedData.length > 7 && Arrays.equals(Arrays.copyOf(advertisedData, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

                if (isUriBeacon) {
                    type = kDeviceType_UriBeacon;
                } else {
                    // Check if Uart is contained in the uuids
                    boolean isUart = false;
                    List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
                    if (serviceUuids != null) {
                        ParcelUuid uartUuid = ParcelUuid.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
                        for (ParcelUuid serviceUuid : serviceUuids) {
                            if (serviceUuid.equals(uartUuid)) {
                                isUart = true;
                                break;
                            }
                        }
                    }

                    if (isUart) {
                        type = kDeviceType_Uart;
                    }
                }
            }
        }

        return type;
    }

    // endregion
}
