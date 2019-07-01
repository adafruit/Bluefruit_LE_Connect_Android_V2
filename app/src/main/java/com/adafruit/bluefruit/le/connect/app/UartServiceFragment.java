package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.UartPacket;
import com.adafruit.bluefruit.le.connect.ble.peripheral.UartPeripheralModePacketManager;
import com.adafruit.bluefruit.le.connect.ble.peripheral.UartPeripheralService;
import com.adafruit.bluefruit.le.connect.models.PeripheralModeManager;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class UartServiceFragment extends UartBaseFragment {
    // Log
    private final static String TAG = UartServiceFragment.class.getSimpleName();

    // Data
    private UartPeripheralService mUartPeripheralService;

    // region Fragment Lifecycle
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static UartServiceFragment newInstance(/*int peripheralServiceIndex*/) {
        UartServiceFragment fragment = new UartServiceFragment();
        return fragment;
    }

    public UartServiceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_uart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.uart_tab_title);

        // Setup Uart
        setupUart();
    }

    // endregion


    // region Uart
    @Override
    protected boolean isInMultiUartMode() {
        return false;
    }

    protected void setupUart() {
        // Init
        Context context = getContext();
        if (context == null) {
            return;
        }

        mUartData = new UartPeripheralModePacketManager(context, this, true, mMqttManager);
        mBufferItemAdapter.setUartData(mUartData);
        mUartPeripheralService = PeripheralModeManager.getInstance().getUartPeripheralService();
        mUartPeripheralService.uartEnable(data -> mUartData.onRxDataReceived(data, null, BluetoothGatt.GATT_SUCCESS));

        updateUartReadyUI(mUartPeripheralService != null);
    }

    // endregion

    // region Uart

    @Override
    protected void send(String message) {
        if (!(mUartData instanceof UartPeripheralModePacketManager)) {
            Log.e(TAG, "Error send with invalid uartData class");
            return;
        }

        if (mUartPeripheralService == null) {
            Log.e(TAG, "mUartPeripheralService not initialized");
            return;
        }

        UartPeripheralModePacketManager uartData = (UartPeripheralModePacketManager) mUartData;
        uartData.send(mUartPeripheralService, message, false);
    }

    // endregion

    // region UI

    @Override
    protected int colorForPacket(UartPacket packet) {
        return Color.BLACK;
    }

    // endregion

    // region Mqtt

    @MainThread
    @Override
    public void onMqttMessageArrived(String topic, @NonNull MqttMessage mqttMessage) {
        if (!(mUartData instanceof UartPeripheralModePacketManager)) {
            Log.e(TAG, "Error send with invalid uartData class");
            return;
        }

        if (mUartPeripheralService == null) {
            Log.e(TAG, "mUartPeripheralService not initialized");
            return;
        }

        UartPeripheralModePacketManager uartData = (UartPeripheralModePacketManager) mUartData;
        final String message = new String(mqttMessage.getPayload());

        uartData.send(mUartPeripheralService, message, true);
    }

    // endregion
}
