package com.adafruit.bluefruit.le.connect.ble.peripheral;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static android.content.Context.MODE_PRIVATE;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class GattServer implements PeripheralService.Listener {
    // Log
    private final static String TAG = GattServer.class.getSimpleName();

    // Constants
    private final static String kPreferences = "GattServer";
    private final static String kPreferences_includeDeviceName = "includeDeviceName";

    // Config
    // private final static boolean kAddDelayAfterBeforeClosing = true;    // To Fix issue: https://stackoverflow.com/questions/29758890/bluetooth-gatt-callback-not-working-with-new-api-for-lollipop, https://issuetracker.google.com/issues/37057260

    // Listener
    public interface Listener {
        // Connection
        void onCentralConnectionStatusChanged(int status);

        // Advertising
        void onWillStartAdvertising();

        void onDidStartAdvertising();

        void onDidStopAdvertising();

        void onAdvertisingError(int errorCode);
    }

    // Data
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;

    private BluetoothLeAdvertiser mAdvertiser;
    private int mMtuSize;

    private boolean mShouldStartAdvertising = false;
    private boolean mIsAdvertising = false;

    private Listener mListener;

    private List<PeripheralService> mPeripheralServices = new ArrayList<>();
    private Semaphore mAddServicesSemaphore;

    // Data - preparedWrite
    class ServiceCharacteristicKey {
        // Based on https://stackoverflow.com/questions/14677993/how-to-create-a-hashmap-with-two-keys-key-pair-value
        private final UUID serviceUuid;
        private final UUID characteristicUuid;

        ServiceCharacteristicKey(UUID serviceUuid, UUID characteristicUuid) {
            this.serviceUuid = serviceUuid;
            this.characteristicUuid = characteristicUuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ServiceCharacteristicKey)) return false;
            ServiceCharacteristicKey key = (ServiceCharacteristicKey) o;
            return serviceUuid.equals(key.serviceUuid) && characteristicUuid.equals(key.characteristicUuid);
        }

        @Override
        public int hashCode() {
            int result = serviceUuid.hashCode();
            result = 31 * result + characteristicUuid.hashCode();
            return result;
        }
    }

    private Map<ServiceCharacteristicKey, byte[]> mPreparedWrites = new HashMap<>();


    // Static methods
    static public boolean isPeripheralModeSupported() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    //
    public GattServer(Context context, Listener listener) {
        mListener = listener;
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        // Update internal status
        onBluetoothStateChanged(context);

        // Set Ble status receiver
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        LocalBroadcastManager.getInstance(context).registerReceiver(mBleAdapterStateReceiver, filter);
    }

    public void removeListener(Context context) {
        // Unregister Ble Status
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mBleAdapterStateReceiver);

        mListener = null;
    }

    private final BroadcastReceiver mBleAdapterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                onBluetoothStateChanged(context);
            }
        }
    };

    private void onBluetoothStateChanged(Context context) {
        // Setup advertiser
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {

            if (!adapter.isEnabled()) {
                Log.w(TAG, "BluetoothLE is disabled");
            }

            if (!adapter.isMultipleAdvertisementSupported()) {
                Log.w(TAG, "Multiple advertisement not supported");
            }

            mAdvertiser = adapter.getBluetoothLeAdvertiser();

            if (mAdvertiser == null) {
                Log.w(TAG, "Device does not support Peripheral Mode");
            }
        } else {
            Log.w(TAG, "Device does not support Bluetooth");
        }

        if (mAdvertiser == null) {
            mIsAdvertising = false;
        }

        if (mShouldStartAdvertising && mAdvertiser != null) {
            startAdvertising(context);
        }
    }

    public boolean isPeripheralModeAvailable() {
        return mAdvertiser != null;
    }

    // region Local Name
    public String getLocalBluetoothName() {
        String name = null;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            name = bluetoothAdapter.getName();
        }
        return name;
    }

    public void setLocalBluetoothName(String name) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.setName(name);
        } else {
            Log.w(TAG, "Trying to set bluetooth name with null adapter");
        }
    }

    // endregion

    // region Service Management

    public @NonNull
    List<PeripheralService> getServices() {
        return mPeripheralServices;
    }

    public void addService(@NonNull PeripheralService service) {
        service.setListener(this);
        mPeripheralServices.add(service);
    }

    public void removeService(@NonNull PeripheralService service) {
        UUID serviceUuid = service.getService().getUuid();

        final int index = indexOfPeripheralServicesWithUuid(serviceUuid);
        if (index >= 0) {
            mPeripheralServices.remove(index);
            service.setListener(null);
        }
    }

    public void removeAllServices() {
        for (PeripheralService service : mPeripheralServices) {
            service.setListener(null);
        }

        mPeripheralServices.clear();
    }

    private int indexOfPeripheralServicesWithUuid(@NonNull UUID uuid) {
        boolean found = false;
        int i = 0;
        while (i < mPeripheralServices.size() && !found) {
            final UUID peripheralServiceUuid = mPeripheralServices.get(i).getService().getUuid();
            if (peripheralServiceUuid.equals(uuid)) {
                found = true;
            } else {
                i++;
            }
        }

        return found ? i : -1;
    }

    // endregion

    // region Advertising
    public synchronized boolean startAdvertising(@NonNull Context context) {
        mShouldStartAdvertising = true;

        if (mBluetoothManager == null || mAdvertiser == null) {
            Log.e(TAG, "startAdvertising with nil objects");
            return false;
        }

        // Clean / Setup
        stopAdvertising(/*context, */false);

        // Start Gatt Server
        Log.d(TAG, "startAdvertising");
        mAddServicesSemaphore = new Semaphore(1, true);
        mGattServer = mBluetoothManager.openGattServer(context.getApplicationContext(), mGattServerCallback);
        if (mGattServer != null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (!adapter.isMultipleAdvertisementSupported() && mPeripheralServices.size() > 1) {
                Log.w(TAG, "Trying to advertise multiple services but multipleAdvertisement is no supported on this device");
                return false;
            }

            for (PeripheralService peripheralService : mPeripheralServices) {
                if (peripheralService.isEnabled()) {
                    BluetoothGattService service = peripheralService.getService();
                    try {
                        mAddServicesSemaphore.acquire();                        // Semaphore to wait for onServiceAdded callback before adding a new service (check addService docs)
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    final boolean isAdded = mGattServer.addService(service);    // Note: Wait for onServiceAdded callback before adding another service
                    if (!isAdded) {
                        Log.e(TAG, "startGattServer service not added");
                    }
                }
            }

            //mAddServicesSemaphore.release();        // Force release any remaining permits (there are no more services that are going to be added)

            // Start advertising
            AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();

            AdvertiseData.Builder advertiseDataBuilder = new AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true);
            advertiseDataBuilder.setIncludeDeviceName(isIncludingDeviceName(context));         // Needed to show the local name

            final boolean isUuartServiceEnabled = indexOfPeripheralServicesWithUuid(UartPeripheralService.kUartServiceUUID) >= 0;
            if (isUuartServiceEnabled) {
                // If UART is enabled, add the UUID to the advertisement packet
                ParcelUuid serviceParcelUuid = new ParcelUuid(UartPeripheralService.kUartServiceUUID);
                advertiseDataBuilder.addServiceUuid(serviceParcelUuid);
            }
            AdvertiseData advertiseData = advertiseDataBuilder.build();

            if (mListener != null) {
                mListener.onWillStartAdvertising();
            }

            mAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertisingCallback);
            return true;
        } else {
            Log.e(TAG, "gatt server is null");
            return false;
        }
    }


    public boolean isIncludingDeviceName(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, MODE_PRIVATE);
        return preferences.getBoolean(kPreferences_includeDeviceName, false);
    }

    public void setIncludeDeviceName(@NonNull Context context, boolean enabled) {
        SharedPreferences.Editor preferencesEditor = context.getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
        preferencesEditor.putBoolean(kPreferences_includeDeviceName, enabled);
        preferencesEditor.apply();
    }

    private AdvertiseCallback mAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising onStartSuccess");
            mIsAdvertising = true;
            if (mListener != null) {
                mListener.onDidStartAdvertising();
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            /*
            if (errorCode == 3) { // Already advertising
                stopAdvertising();
            }*/

            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                Log.d(TAG, "Advertising onStartFailure because it was already advertising. Failure recovered");
                mIsAdvertising = true;
                if (mListener != null) {
                    mListener.onDidStartAdvertising();
                }
            } else {
                Log.e(TAG, "Advertising onStartFailure: " + errorCode);
                mIsAdvertising = false;
                if (mListener != null) {
                    mListener.onAdvertisingError(errorCode);
                }
            }
        }
    };

    private synchronized void stopAdvertising(/*@NonNull Context context, */boolean notifyListener) {
        mShouldStartAdvertising = false;

        if (mAdvertiser != null && mIsAdvertising) {
            try {
                mAdvertiser.stopAdvertising(mAdvertisingCallback);
            } catch (IllegalStateException e) {         // Discard IllegalStateException reported in Android vitals crashes
                Log.w(TAG, "stopAdvertising illegalstate: " + e);
            }
            mIsAdvertising = false;
            Log.d(TAG, "Advertising stopAdvertising");
        }

        if (mGattServer != null) {
            List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothGattServer.GATT_SERVER);
            for (int i = 0; i < devices.size(); i++) {
                mGattServer.cancelConnection(devices.get(i));
            }
            mGattServer.clearServices();

            /*
            if (kAddDelayAfterBeforeClosing) {      // Hack to avoid Android internal bugs. Exists on Android v6 and v7... maybe more...
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
            mGattServer.close();
            mGattServer = null;
        }

        if (notifyListener && mListener != null) {
            mListener.onDidStopAdvertising();
        }
    }

    public void stopAdvertising(/*@NonNull Context context*/) {
        stopAdvertising(/*context, */true);
    }


    public boolean isAdvertising() {
        return mIsAdvertising;
    }

    // end region


    // region Gatt Server

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG, "GattServer: onConnectionStateChange status: " + status + "state:" + newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "\tonConnectionStateChange error reported");
            }

            if (mListener != null) {
                mListener.onCentralConnectionStatusChanged(newState);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);

            Log.d(TAG, "GattServer: onServiceAdded");
            mAddServicesSemaphore.release();
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.d(TAG, "Mtu changed: " + mtu);
            mMtuSize = mtu;
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "GattServer: onCharacteristicReadRequest characteristic: " + characteristic.getUuid().toString() + " requestId: " + requestId + " offset: " + offset);

            boolean isCharacteristicValid = false;
            final UUID serviceUuid = characteristic.getService().getUuid();


            final int indexOfPeripheral = indexOfPeripheralServicesWithUuid(serviceUuid);
            if (indexOfPeripheral >= 0) {
                PeripheralService peripheralService = mPeripheralServices.get(indexOfPeripheral);
                final UUID characteristicUuid = characteristic.getUuid();
                BluetoothGattCharacteristic serviceCharacteristic = peripheralService.getCharacteristic(characteristicUuid);
                if (serviceCharacteristic != null) {
                    processReadRequest(device, requestId, offset, serviceCharacteristic.getValue());
                    isCharacteristicValid = true;
                }
            }

            if (!isCharacteristicValid) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
            }
        }

        private void processReadRequest(BluetoothDevice device, int requestId, int offset, byte[] bytes) {
            byte[] value = bytes != null ? bytes : new byte[]{0};
            if (offset > value.length) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, new byte[]{0});
            } else {
                final int responseSize = Math.min(mMtuSize, value.length - offset);
                byte[] responseChunck = new byte[responseSize];
                System.arraycopy(value, offset, responseChunck, 0, responseSize);
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseChunck);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, "GattServer: onCharacteristicWriteRequest characteristic: " + characteristic.getUuid().toString() + " requestId: " + requestId + " preparedWrite: " + preparedWrite + " responseNeeded: " + responseNeeded + " offset: " + offset);

            final UUID serviceUuid = characteristic.getService().getUuid();
            for (PeripheralService peripheralService : mPeripheralServices) {
                if (serviceUuid.equals(peripheralService.getService().getUuid())) {
                    final UUID characteristicUuid = characteristic.getUuid();
                    BluetoothGattCharacteristic serviceCharacteristic = peripheralService.getCharacteristic(characteristicUuid);
                    if (serviceCharacteristic != null) {
                        final ServiceCharacteristicKey key = new ServiceCharacteristicKey(serviceUuid, characteristicUuid);
                        byte[] currentValue = preparedWrite ? mPreparedWrites.get(key) : value;
                        currentValue = processWriteRequest(device, requestId, preparedWrite, responseNeeded, offset, value, currentValue);
                        if (preparedWrite) {
                            mPreparedWrites.put(key, currentValue);
                        } else {
                            //characteristic.setValue(currentValue);
                            peripheralService.setCharacteristic(characteristic, currentValue);
                        }
                    }
                }
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);

            Log.d(TAG, "GattServer: onExecuteWrite requestId: " + requestId + " execute: " + execute);

            if (execute) {
                for (ServiceCharacteristicKey key : mPreparedWrites.keySet()) {
                    byte[] value = mPreparedWrites.get(key);

                    byte[] currentValue = processWriteRequest(device, requestId, false, false, 0, value, null);
                    BluetoothGattCharacteristic characteristic = mGattServer.getService(key.serviceUuid).getCharacteristic(key.characteristicUuid);
                    //characteristic.setValue(currentValue);


                    for (PeripheralService peripheralService : mPeripheralServices) {
                        if (key.serviceUuid.equals(peripheralService.getService().getUuid())) {
                            peripheralService.setCharacteristic(characteristic, currentValue);
                        }
                    }
                }
            }

            mPreparedWrites.clear();

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
        }

        private @NonNull
        byte[] processWriteRequest(BluetoothDevice device, int requestId, boolean prepared, boolean responseNeeded, int offset, byte[] value, byte[] currentCharacteristicValue) {
            // Adjust currentValue to make room for the new data
            // Question: should the characteristic value be expanded to accommodate the value? Maybe not...
            if (currentCharacteristicValue == null || offset == 0) {
                currentCharacteristicValue = new byte[value.length];
            } else if (currentCharacteristicValue.length < offset + value.length) {
                byte[] newValue = new byte[offset + value.length];
                System.arraycopy(currentCharacteristicValue, 0, newValue, 0, currentCharacteristicValue.length);
                currentCharacteristicValue = newValue;
            }

            System.arraycopy(value, 0, currentCharacteristicValue, offset, value.length);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            return currentCharacteristicValue;
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, "GattServer: onDescriptorWriteRequest : " + descriptor.getUuid().toString() + " requestId: " + requestId + " preparedWrite: " + preparedWrite + " responseNeeded: " + responseNeeded + " offset: " + offset);

            // Check if is enabling notification
            if (descriptor.getUuid().equals(BlePeripheral.kClientCharacteristicConfigUUID)) {

                int status = BluetoothGatt.GATT_SUCCESS;
                if (value.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else {
                    BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                    boolean isNotify = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) || Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    boolean isNotifySupported = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;

                    if (isNotify && isNotifySupported) {
                        descriptor.setValue(value);
                        UUID serviceUUID = descriptor.getCharacteristic().getService().getUuid();
                        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                            Log.d(TAG, "subscribed to characteristic: " + descriptor.getCharacteristic().getUuid().toString());

                            for (PeripheralService service : mPeripheralServices) {
                                if (serviceUUID.equals(service.getService().getUuid())) {
                                    service.subscribe(characteristic.getUuid(), device);
                                }
                            }

                        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                            Log.d(TAG, "unsubscribed from characteristic: " + descriptor.getCharacteristic().getUuid().toString());

                            for (PeripheralService service : mPeripheralServices) {
                                if (serviceUUID.equals(service.getService().getUuid())) {
                                    service.unsubscribe(characteristic.getUuid(), device);
                                }
                            }

                        }
                    } else {
                        status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                    }

                    // TODO: add support for Indications

                }

                if (responseNeeded) {
                    mGattServer.sendResponse(device, requestId, status, offset, value);
                }
            }
        }
    };

    // end region

    // region PeripheralService.Listener
    @Override
    public void updateValue(@NonNull BluetoothDevice[] devices, @NonNull BluetoothGattCharacteristic characteristic) {
        for (BluetoothDevice device : devices) {
            mGattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }
    // endregion

}