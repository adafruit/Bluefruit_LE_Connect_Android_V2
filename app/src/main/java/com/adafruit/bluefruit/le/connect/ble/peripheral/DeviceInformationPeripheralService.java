package com.adafruit.bluefruit.le.connect.ble.peripheral;


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import android.util.Base64;

import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.util.List;
import java.util.UUID;

import static android.content.Context.MODE_PRIVATE;

public class DeviceInformationPeripheralService extends PeripheralService {
    // Specs: https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.device_information.xml

    // Constants
    private static final String kPreferences = "DeviceInformationPeripheralService_prefs";

    // Service
    private static final UUID kDisServiceUUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");

    // Characteristics
    private static UUID kManufacturerNameCharacteristicUUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB");
    private static UUID kModelNumberCharacteristicUUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB");
    private static UUID kSerialNumberCharacteristicUUID = UUID.fromString("00002A25-0000-1000-8000-00805F9B34FB");
    private static UUID kHardwareNumberCharacteristicUUID = UUID.fromString("00002A76-0000-1000-8000-00805F9B34FB");
    private static UUID kFirmwareRevisionCharacteristicUUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB");
    private static UUID kSoftwareRevisionCharacteristicUUID = UUID.fromString("00002A28-0000-1000-8000-00805F9B34FB");

    private BluetoothGattCharacteristic mManufacturerNameCharacteristic = new BluetoothGattCharacteristic(kManufacturerNameCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattCharacteristic mModelNumberCharacteristic = new BluetoothGattCharacteristic(kModelNumberCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattCharacteristic mSerialNumberCharacteristic = new BluetoothGattCharacteristic(kSerialNumberCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattCharacteristic mHardwareNumberCharacteristic = new BluetoothGattCharacteristic(kHardwareNumberCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattCharacteristic mFirmwareRevisionCharacteristic = new BluetoothGattCharacteristic(kFirmwareRevisionCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattCharacteristic mSoftwareRevisionCharacteristic = new BluetoothGattCharacteristic(kSoftwareRevisionCharacteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

    public DeviceInformationPeripheralService(Context context) {
        super(context);

        mName = LocalizationManager.getInstance().getString(context, "peripheral_dis_title");

        /*
        mCharacteristics.add(mManufacturerNameCharacteristic);
        mCharacteristics.add(mModelNumberCharacteristic);
        mCharacteristics.add(mSerialNumberCharacteristic);
        mCharacteristics.add(mHardwareNumberCharacteristic);
        mCharacteristics.add(mFirmwareRevisionCharacteristic);
        mCharacteristics.add(mSoftwareRevisionCharacteristic);
*/

        mService = new BluetoothGattService(kDisServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mService.addCharacteristic(mManufacturerNameCharacteristic);
        mService.addCharacteristic(mModelNumberCharacteristic);
        mService.addCharacteristic(mSerialNumberCharacteristic);
        mService.addCharacteristic(mHardwareNumberCharacteristic);
        mService.addCharacteristic(mFirmwareRevisionCharacteristic);
        mService.addCharacteristic(mSoftwareRevisionCharacteristic);

        loadValues(context);
    }

    public String getManufacturer() {
        return mManufacturerNameCharacteristic.getStringValue(0);
    }

    public void setManufacturer(String manufacturer) {
        mManufacturerNameCharacteristic.setValue(manufacturer);
    }

    public String getModelNumber() {
        return mModelNumberCharacteristic.getStringValue(0);
    }

    public void setModelNumber(String modelNumber) {
        mModelNumberCharacteristic.setValue(modelNumber);
    }

    public String getSerialNumber() {
        return mSerialNumberCharacteristic.getStringValue(0);
    }

    public void setSerialNumber(String serialNumber) {
        mSerialNumberCharacteristic.setValue(serialNumber);
    }

    public String getHardwareNumber() {
        return mHardwareNumberCharacteristic.getStringValue(0);
    }

    public void setHardwareNumber(String hardwareNumber) {
        mHardwareNumberCharacteristic.setValue(hardwareNumber);
    }

    public String getFirmwareNumber() {
        return mFirmwareRevisionCharacteristic.getStringValue(0);
    }

    public void setFirmwareNumber(String firmwareNumber) {
        mFirmwareRevisionCharacteristic.setValue(firmwareNumber);
    }

    public String getSoftwareRevision() {
        return mSoftwareRevisionCharacteristic.getStringValue(0);
    }

    public void setSoftwareRevision(String softwareRevision) {
        mSoftwareRevisionCharacteristic.setValue(softwareRevision);
    }

    // region Persistence
    @Override
    protected void loadValues(@NonNull Context context) {
        super.loadValues(context);
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, MODE_PRIVATE);
        List<BluetoothGattCharacteristic> characteristics = mService.getCharacteristics();
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            String encodedData = preferences.getString(characteristic.getUuid().toString(), null);
            if (encodedData != null) {
                byte[] data = Base64.decode(encodedData, Base64.NO_WRAP);
                characteristic.setValue(data);
            }
        }
    }

    @Override
    public void saveValues(@NonNull Context context) {
        super.saveValues(context);

        SharedPreferences.Editor preferencesEditor = context.getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
        List<BluetoothGattCharacteristic> characteristics = mService.getCharacteristics();
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            byte[] data = characteristic.getValue();
            if (data != null) {
                String encodedData = Base64.encodeToString(data, Base64.NO_WRAP);
                preferencesEditor.putString(characteristic.getUuid().toString(), encodedData);
            }
        }
        preferencesEditor.apply();
    }
    // endregion
}