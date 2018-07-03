package com.adafruit.bluefruit.le.connect.models;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

import com.adafruit.bluefruit.le.connect.ble.peripheral.DeviceInformationPeripheralService;

public class DeviceInformationServiceViewModel extends AndroidViewModel {

    public DeviceInformationServiceViewModel(@NonNull Application application) {
        super(application);
    }

    public DeviceInformationPeripheralService getDeviceInfomationPeripheralService() {
        return PeripheralModeManager.getInstance().getDeviceInfomationPeripheralService();
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Save current characteristic values
        PeripheralModeManager.getInstance().getDeviceInfomationPeripheralService().saveValues(getApplication());
    }
}
