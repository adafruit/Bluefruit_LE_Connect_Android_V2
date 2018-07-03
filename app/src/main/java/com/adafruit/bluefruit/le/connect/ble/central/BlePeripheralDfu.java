package com.adafruit.bluefruit.le.connect.ble.central;

import java.util.UUID;

public class BlePeripheralDfu {
    private static final UUID kDfuServiceUuid = UUID.fromString("00001530-1212-EFDE-1523-785FEABCD123");

    public static boolean hasDfu(BlePeripheral peripheral) {
        return peripheral.getService(kDfuServiceUuid) != null;
    }
}
