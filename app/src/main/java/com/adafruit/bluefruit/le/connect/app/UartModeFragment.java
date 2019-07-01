package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.UartPacket;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.ble.central.UartPacketManager;
import com.adafruit.bluefruit.le.connect.style.UartStyle;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UartModeFragment extends UartBaseFragment {
    // Log
    private final static String TAG = UartModeFragment.class.getSimpleName();

    // Data
    private Map<String, Integer> mColorForPeripheral = new HashMap<>();
    private String mMultiUartSendToPeripheralIdentifier = null;     // null = all peripherals

    // region Fragment Lifecycle
    public static UartModeFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        UartModeFragment fragment = new UartModeFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public UartModeFragment() {
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

        // UI
        Context context = getContext();
        if (context != null) {
            final boolean isInMultiUartMode = isInMultiUartMode();

            if (isInMultiUartMode) {
                mSendPeripheralSpinner.setAdapter(new PeripheralSelectorAdapter(context, mBlePeripheralsUart));
                mSendPeripheralSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                        if (pos == 0) {
                            mMultiUartSendToPeripheralIdentifier = null;    // All peripherals
                        }
                        else if (pos < mBlePeripheralsUart.size() - 1) {     // Check boundaries
                            mMultiUartSendToPeripheralIdentifier = mBlePeripheralsUart.get(pos).getIdentifier();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
            }
        }

        // Setup Uart
        setupUart();
    }

    // endregion

    // region PeripheralSelectorAdapter
    static class PeripheralSelectorAdapter implements SpinnerAdapter {
        // Data
        private Context mContext;
        private List<BlePeripheralUart> mBlePeripheralsUart;

        PeripheralSelectorAdapter(@NonNull Context context, @NonNull List<BlePeripheralUart> blePeripheralsUart) {
            super();
            mContext = context;
            mBlePeripheralsUart = blePeripheralsUart;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            ((TextView) convertView).setText(getItem(position));
            return convertView;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public int getCount() {
            return mBlePeripheralsUart.size() + 1;      // +1 All
        }

        @Override
        public String getItem(int i) {
            if (i == 0) {
                return mContext.getString(R.string.uart_send_toall_action);
            } else {
                return mBlePeripheralsUart.get(i - 1).getName();
            }
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_spinner_item, parent, false);
            textView.setText(getItem(position));
            return textView;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    // endregion

    // region Uart
    @Override
    protected boolean isInMultiUartMode() {
        return mBlePeripheral == null;
    }

    protected void setupUart() {
        // Init
        Context context = getContext();
        if (context == null) {
            return;
        }
        mUartData = new UartPacketManager(context, this, true, mMqttManager);           // Note: mqttmanager should have been initialized previously
        mBufferItemAdapter.setUartData(mUartData);

        // Colors assigned to peripherals
        final int[] colors = UartStyle.defaultColors();

        // Enable uart
        if (isInMultiUartMode()) {
            mColorForPeripheral.clear();        // Reset colors assigned to peripherals
            List<BlePeripheral> connectedPeripherals = BleScanner.getInstance().getConnectedPeripherals();
            for (int i = 0; i < connectedPeripherals.size(); i++) {
                final boolean isLastPeripheral = i == connectedPeripherals.size() - 1;
                BlePeripheral blePeripheral = connectedPeripherals.get(i);
                mColorForPeripheral.put(blePeripheral.getIdentifier(), colors[i % colors.length]);

                if (!BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mUartData, status -> {

                        String peripheralName = blePeripheral.getName();
                        if (peripheralName == null) {
                            peripheralName = blePeripheral.getIdentifier();
                        }

                        String finalPeripheralName = peripheralName;
                        mMainHandler.post(() -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                // Done
                                Log.d(TAG, "Uart enabled for: " + finalPeripheralName);

                                if (isLastPeripheral) {
                                    updateUartReadyUI(true);
                                }

                            } else {
                                //WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                AlertDialog dialog = builder.setMessage(String.format(getString(R.string.uart_error_multipleperiperipheralinit_format), finalPeripheralName))
                                        .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        /*
                                            BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }*/
                                        })
                                        .show();
                                DialogUtils.keepDialogOnOrientationChanges(dialog);
                            }
                        });

                    });
                } else if (isLastPeripheral) {
                    updateUartReadyUI(true);
                }
            }
        } else {
            if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                updateUartReadyUI(false);
                mColorForPeripheral.clear();        // Reset colors assigned to peripherals
                mColorForPeripheral.put(mBlePeripheral.getIdentifier(), colors[0]);
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mUartData, status -> mMainHandler.post(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Done
                        Log.d(TAG, "Uart enabled");
                        updateUartReadyUI(true);
                    } else {
                        WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                        Context context1 = getContext();
                        if (context1 != null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context1);
                            AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }
                                    })
                                    .show();
                            DialogUtils.keepDialogOnOrientationChanges(dialog);
                        }
                    }
                }));
            }
        }
    }

    @Override
    protected void send(String message) {
        if (!(mUartData instanceof UartPacketManager)) {
            Log.e(TAG, "Error send with invalid uartData class");
            return;
        }

        if (mBlePeripheralsUart.size() == 0) {
            Log.e(TAG, "mBlePeripheralsUart not initialized");
            return;
        }

        UartPacketManager uartData = (UartPacketManager) mUartData;

        if (isInMultiUartMode()) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart) {
                if (mMultiUartSendToPeripheralIdentifier == null || mMultiUartSendToPeripheralIdentifier.equals(blePeripheralUart.getIdentifier())) {
                    uartData.send(blePeripheralUart, message);
                }
            }
        } else {
            BlePeripheralUart blePeripheralUart = mBlePeripheralsUart.get(0);
            uartData.send(blePeripheralUart, message);
        }
    }

    // endregion

    // region UI
    @Override
    protected int colorForPacket(UartPacket packet) {
        int color = Color.BLACK;
        final String peripheralId = packet.getPeripheralId();
        if (peripheralId != null) {
            Integer peripheralColor = mColorForPeripheral.get(peripheralId);
            if (peripheralColor != null) {
                color = peripheralColor;
            }
        }

        return color;
    }

    // endregion

    // region Mqtt

    @MainThread
    @Override
    public void onMqttMessageArrived(String topic, @NonNull MqttMessage mqttMessage) {
        if (!(mUartData instanceof UartPacketManager)) {
            Log.e(TAG, "Error send with invalid uartData class");
            return;
        }
        if (mBlePeripheralsUart.size() == 0) {
            Log.e(TAG, "mBlePeripheralsUart not initialized");
            return;
        }

        BlePeripheralUart blePeripheralUart = mBlePeripheralsUart.get(0);
        final String message = new String(mqttMessage.getPayload());

        ((UartPacketManager) mUartData).send(blePeripheralUart, message, true);          // Don't republish to mqtt something received from mqtt

        /*
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        })*/
    }

    // endregion
}