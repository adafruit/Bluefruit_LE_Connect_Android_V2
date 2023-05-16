package com.adafruit.bluefruit.le.connect.app;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UartModeFragment extends UartBaseFragment {
    // Log
    private final static String TAG = UartModeFragment.class.getSimpleName();

    // Data
    private final Map<String, Integer> mColorForPeripheral = new HashMap<>();
    private String mMultiUartSendToPeripheralIdentifier = null;     // null = all peripherals
    private String mTerminalTitle = null;

    // region Fragment Lifecycle
    public static UartModeFragment newInstance(@Nullable String singlePeripheralIdentifier, int mode) {
        UartModeFragment fragment = new UartModeFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier, mode));
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
        updateUartReadyUI(false);

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
                        } else if (pos < mBlePeripheralsUart.size() - 1) {     // Check boundaries
                            mMultiUartSendToPeripheralIdentifier = mBlePeripheralsUart.get(pos).getIdentifier();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
            }

            // Register onConnect listener to setup Uart if peripheral is reconnected
            registerGattReceiver(context);
        }

        // Setup Uart
        try {
            setupUart(false);
        } catch (SecurityException e) {
            Log.e(TAG, "onViewCreated security exception: " + e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Context context = getContext();
        if (context != null) {
            unregisterGattReceiver(context);
        }
    }

    // endregion

    // region PeripheralSelectorAdapter
    static class PeripheralSelectorAdapter implements SpinnerAdapter {
        // Data
        private final Context mContext;
        private final List<BlePeripheralUart> mBlePeripheralsUart;

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

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
    protected void setupUart(boolean force) {
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

                if (force || !BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mMode, mUartData, status -> {

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
            if (force || !BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                updateUartReadyUI(false);
                mColorForPeripheral.clear();        // Reset colors assigned to peripherals
                mColorForPeripheral.put(mBlePeripheral.getIdentifier(), colors[0]);
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mMode, mUartData, status -> mMainHandler.post(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Done
                        Log.d(TAG, "Uart enabled");
                        updateUartReadyUI(true);
                    } else {
                        Log.d(TAG, "Uart enable error");
                        WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                        Context context1 = getContext();
                        if (context1 != null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context1);
                            AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            try {
                                                strongBlePeripheralUart.disconnect();
                                            } catch (SecurityException e) {
                                                Log.e(TAG, "disconnect security exception: " + e);
                                            }
                                        }
                                    })
                                    .show();
                            DialogUtils.keepDialogOnOrientationChanges(dialog);
                        }
                    }
                }));
            } else {
                updateUartReadyUI(true);
            }
        }
    }

    @Override
    protected void send(byte[] data) {
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
                    uartData.send(blePeripheralUart, data);
                }
            }
        } else {
            BlePeripheralUart blePeripheralUart = mBlePeripheralsUart.get(0);
            uartData.send(blePeripheralUart, data);
        }
    }

    //    private static final String oscTitleRegex = "\\\\u001B]0;(?<title>.+?(?=\\\\u001B\\\\\\\\))";
    private static final String oscTitleRegex = "\\u001B]0;(?<title>.+?(?=\\u001B\\\\))";
    private static final Pattern oscTitlePattern = Pattern.compile(oscTitleRegex);

    @Override
    protected UartPacket onUartPacketTextPreProcess(UartPacket packet) {
        // Terminal OSC commands
        if (mDisplayMode != UARTDISPLAYMODE_TERMINAL || packet.getMode() != UartPacket.TRANSFERMODE_RX)
            return packet;

        final byte[] bytes = packet.getData();
        String text = new String(bytes, StandardCharsets.UTF_8);

        // ]0;🐍BLE:Ok | Done | 8.0.0-beta.0-5-g65ec12afd\
        // "\u001B]0;\uD83D\uDC0DBLE:Ok | Done | 8.0.0-beta.0-5-g65ec12afd\u001B\\"
        Matcher matcher = oscTitlePattern.matcher(text);

        // Check all occurrences
        String title = null;
        String remainingText = null;
        if (matcher.find()) {
            //Log.d(TAG, "Start index: " + matcher.start());
            //Log.d(TAG, " End index: " + matcher.end());
            String group = matcher.group();
            Log.d(TAG, " Found: " + group);

            // Remove characters pre-title
            if (group.length() > 3) {
                title = group.substring(4);
                Log.d(TAG, "OSC title found: " + title);
            }

            // Remove title + characters post-title (with hacks to avoid problems with Java escaping sequences)
            text = text.replace("\\", "\\\\");      // Hack: Java .replaceAll can't replace a single \ character (Unrecognized backslash escape sequence in pattern)
            int oscEndIndex = Math.min(text.length(), matcher.end() + 3);
            String toBeReplaced = text.substring(matcher.start(), oscEndIndex);
            remainingText = text.replaceAll(toBeReplaced, "");
            if (remainingText.length() >= 3) {      // Hack to remove the remaining ||\
                remainingText = remainingText.substring(3);
            }
        }

        if (title != null) {
            mTerminalTitle = title;
            updateTerminalTitle();
            return new UartPacket(packet.getPeripheralId(), packet.getMode(), remainingText.getBytes(StandardCharsets.UTF_8));
        } else {
            return packet;
        }
    }

    private void updateTerminalTitle() {
        final boolean isHidden = mDisplayMode != UARTDISPLAYMODE_TERMINAL || mTerminalTitle == null || mTerminalTitle.isEmpty();
        mTerminalTitleTextView.setVisibility(isHidden ? View.GONE : View.VISIBLE);
        mTerminalTitleTextView.setText(mTerminalTitle);
    }

    // endregion

    // region UI
    @Override
    protected int colorForPacket(UartPacket packet) {
        int color = Color.BLACK;

        if (mDisplayMode != UARTDISPLAYMODE_TERMINAL) {
            final String peripheralId = packet.getPeripheralId();
            if (peripheralId != null) {
                Integer peripheralColor = mColorForPeripheral.get(peripheralId);
                if (peripheralColor != null) {
                    color = peripheralColor;
                }
            }
        }

        return color;
    }

    // endregion


    // region Broadcast Listener
    private void registerGattReceiver(@NonNull Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlePeripheral.kBlePeripheral_OnConnected);
        filter.addAction(BlePeripheral.kBlePeripheral_OnReconnecting);
        LocalBroadcastManager.getInstance(context).registerReceiver(mGattUpdateReceiver, filter);
    }

    private void unregisterGattReceiver(@NonNull Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mGattUpdateReceiver);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String identifier = intent.getStringExtra(BlePeripheral.kExtra_deviceAddress);
            if (identifier != null) {
                if (BlePeripheral.kBlePeripheral_OnConnected.equals(action)) {
                    try {
                        Log.d(TAG, "Reconnection detected. Setup UART");

                        mBlePeripheral.discoverServices(status -> {
                            final Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> setupUart(true));
                        });
                    } catch (SecurityException e) {
                        Log.e(TAG, "kBlePeripheral_OnConnected security exception: " + e);
                    }
                }
            } else if (BlePeripheral.kBlePeripheral_OnReconnecting.equals(action)) {
                Log.d(TAG, "Disconnection detected. Disconnect UART");
                updateUartReadyUI(false);
            } else {
                Log.w(TAG, "UartModeFragment mGattUpdateReceiver with null peripheral");
            }
        }
    };
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
        final byte[] data = mqttMessage.getPayload();

        ((UartPacketManager) mUartData).send(blePeripheralUart, data, true);          // Don't republish to mqtt something received from mqtt
    }

    // endregion
}