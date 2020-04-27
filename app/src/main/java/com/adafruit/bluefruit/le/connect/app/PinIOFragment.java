package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.utils.AdapterUtils;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PinIOFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = PinIOFragment.class.getSimpleName();

    // Config
    private static final long CAPABILITY_QUERY_TIMEOUT = 15000;      // in milliseconds

    // Pin Constants
    private static final byte SYSEX_START = (byte) 0xF0;
    private static final byte SYSEX_END = (byte) 0xF7;

    private static final int DEFAULT_PINS_COUNT = 20;
    private static final int FIRST_DIGITAL_PIN = 3;
    private static final int LAST_DIGITAL_PIN = 8;
    private static final int FIRST_ANALOG_PIN = 14;
    private static final int LAST_ANALOG_PIN = 19;

    // UI
    private PinsAdapter mPinsAdapter;
    private AlertDialog mAlertDialog;

    // Uart
    private static final int kUartStatus_InputOutput = 0;       // Default mode (sending and receiving pin data)
    private static final int kUartStatus_QueryCapabilities = 1;
    private static final int kUartStatus_QueryAnalogMapping = 2;


    public class PinData {
        private static final int kMode_Unknown = 255;
        private static final int kMode_Input = 0;
        private static final int kMode_Output = 1;
        private static final int kMode_Analog = 2;
        private static final int kMode_PWM = 3;
        private static final int kMode_Servo = 4;
        private static final int kMode_InputPullup = 0xb;

        private static final int kDigitalValue_Low = 0;
        private static final int kDigitalValue_High = 1;

        int digitalPinId;
        int analogPinId = -1;
        boolean isInput;
        boolean isOutput;
        boolean isAnalog;
        boolean isPwm;
        boolean isInputPullup;

        int mode = kMode_Input;
        int digitalValue = kDigitalValue_Low;
        int analogValue = 0;

        PinData(int digitalPinId, boolean isInput, boolean isOutput, boolean isAnalog, boolean isPwm, boolean isInputPullup) {
            this.digitalPinId = digitalPinId;
            this.isInput = isInput;
            this.isOutput = isOutput;
            this.isAnalog = isAnalog;
            this.isPwm = isPwm;
            this.isInputPullup = isInputPullup;
        }
    }

    // Data
    private UartDataManager mUartDataManager;
    private BlePeripheralUart mBlePeripheralUart;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private int mUartStatus = kUartStatus_InputOutput;
    private List<PinData> mPins = new ArrayList<>();
    private Handler mQueryCapabilitiesTimerHandler;
    private Runnable mQueryCapabilitiesTimerRunnable = () -> {
        Log.d(TAG, "timeout: cancelQueryCapabilities");
        endPinQuery(true);
    };

    // region Fragment Lifecycle
    public static PinIOFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        PinIOFragment fragment = new PinIOFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public PinIOFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pinio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.pinio_tab_title);

        final Context context = getContext();
        if (context != null) {
            // Recycler view
            RecyclerView pinsRecyclerView = view.findViewById(R.id.recyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            pinsRecyclerView.addItemDecoration(itemDecoration);

            RecyclerView.LayoutManager peripheralsLayoutManager = new LinearLayoutManager(getContext());
            pinsRecyclerView.setLayoutManager(peripheralsLayoutManager);

            // Disable update animation
            SimpleItemAnimator animator = (SimpleItemAnimator) pinsRecyclerView.getItemAnimator();
            if (animator != null) {
                animator.setSupportsChangeAnimations(false);
            }

            // Adapter
            mPinsAdapter = new PinsAdapter(context, mPins, new PinsAdapter.Listener() {
                @Override
                public void onModeSelected(PinData pinData, int mode) {
                    setControlMode(pinData, mode);
                }

                @Override
                public void onDigitalValueSelected(PinData pinData, int mode) {
                    setDigitalValue(pinData, mode);
                }

                @Override
                public void onSetPMWValue(PinData pinData, int progress) {
                    setPMWValue(pinData, progress);
                }
            });
            pinsRecyclerView.setAdapter(mPinsAdapter);
        }

        // Swipe to refreshAll
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            reset();
            new Handler().postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 500);
        });

        // Setup
        final boolean isUartInitialized = mBlePeripheralUart != null && mBlePeripheralUart.isUartEnabled();
        if (context != null && !isUartInitialized) {
            mUartDataManager = new UartDataManager(context, this, false);
            mUartDataManager.setRxCacheEnabled(false);
            start();
        }
    }

    @Override
    public void onDestroy() {
        if (mUartDataManager != null) {
            Context context = getContext();
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        stop();
        dismissCurrentDialog();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.pinio_help_title), getString(R.string.pinio_help_text));
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                            .replace(R.id.contentLayout, helpFragment, "Help");
                    fragmentTransaction.addToBackStack(null);
                    fragmentTransaction.commit();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // endregion

    // region Uart
    private void start() {
        Log.d(TAG, "PinIO start");

        // Enable Uart
        mBlePeripheralUart = new BlePeripheralUart(mBlePeripheral);
        mBlePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
            Context context = getContext();
            if (getContext() != null) {     // Check that the fragment is still attached to avoid showing alerts when detached
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Done
                    Log.d(TAG, "Uart enabled");
                    if (mPins.size() == 0 && !isQueryingCapabilities()) {
                        startQueryCapabilitiesProcess();
                    }
                } else {
                    WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(mBlePeripheralUart);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
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

    private void stop() {
        Log.d(TAG, "PinIO stop");

        //  Cancel pending queries
        cancelQueryCapabilitiesTimer();

        /*
        if (mBlePeripheralUart != null) {
            mBlePeripheralUart.uartDisable();
        }*/
        mBlePeripheralUart = null;
    }

    // endregion

    // region Query Capabilities

    private void reset() {
        mUartStatus = kUartStatus_InputOutput;
        mPins.clear();

        mPinsAdapter.pinsUpdated();

        // Reset Firmata
        byte[] data = new byte[]{(byte) 0xff};
        mUartDataManager.send(mBlePeripheralUart, data, null);

        startQueryCapabilitiesProcess();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isQueryingCapabilities() {
        return mUartStatus != kUartStatus_InputOutput;
    }

    private void startQueryCapabilitiesProcess() {
        if (!isQueryingCapabilities()) {

            dismissCurrentDialog();
            // Show dialog
            mAlertDialog = new AlertDialog.Builder(getContext())
                    .setMessage(R.string.pinio_capabilityquery_querying_title)
                    .setCancelable(true)
                    .setOnCancelListener(dialog -> endPinQuery(true))
                    .create();

            mAlertDialog.show();

            // Start process
            queryCapabilities();
        } else {
            Log.d(TAG, "error: queryCapabilities called while querying capabilities");
        }
    }

    private List<Byte> mQueryCapabilitiesDataBuffer = new ArrayList<>();

    private void queryCapabilities() {
        Log.d(TAG, "queryCapabilities");

        // Set status
        mPins.clear();
        mUartStatus = kUartStatus_QueryCapabilities;
        mQueryCapabilitiesDataBuffer.clear();

        // Query Capabilities
        byte[] data = new byte[]{SYSEX_START, (byte) 0x6B, SYSEX_END};
        mUartDataManager.send(mBlePeripheralUart, data, null);


        mQueryCapabilitiesTimerHandler = new Handler();
        mQueryCapabilitiesTimerHandler.postDelayed(mQueryCapabilitiesTimerRunnable, CAPABILITY_QUERY_TIMEOUT);
    }

    private void receivedQueryCapabilities(byte[] data) {
        // Read received packet
        for (final byte dataByte : data) {
            mQueryCapabilitiesDataBuffer.add(dataByte);
            if (dataByte == SYSEX_END) {
                Log.d(TAG, "Finished receiving capabilities");
                queryAnalogMapping();
                break;
            }
        }
    }

    private void cancelQueryCapabilitiesTimer() {
        if (mQueryCapabilitiesTimerHandler != null) {
            mQueryCapabilitiesTimerHandler.removeCallbacks(mQueryCapabilitiesTimerRunnable);
            mQueryCapabilitiesTimerHandler = null;
        }
    }
    // endregion

    // region Query AnalogMapping

    private ArrayList<Byte> mQueryAnalogMappingDataBuffer = new ArrayList<>();

    private void queryAnalogMapping() {
        Log.d(TAG, "queryAnalogMapping");

        // Set status
        mUartStatus = kUartStatus_QueryAnalogMapping;
        mQueryAnalogMappingDataBuffer.clear();

        // Query Analog Mapping
        byte[] data = new byte[]{SYSEX_START, (byte) 0x69, SYSEX_END};
        mUartDataManager.send(mBlePeripheralUart, data, null);
    }

    private void receivedAnalogMapping(byte[] data) {
        cancelQueryCapabilitiesTimer();

        // Read received packet
        for (final byte dataByte : data) {
            mQueryAnalogMappingDataBuffer.add(dataByte);
            if (dataByte == SYSEX_END) {
                Log.d(TAG, "Finished receiving Analog Mapping");
                endPinQuery(false);
                break;
            }
        }
    }
    // endregion

    // region Process Capabilities
    private void endPinQuery(boolean abortQuery) {
        cancelQueryCapabilitiesTimer();
        mUartStatus = kUartStatus_InputOutput;

        boolean capabilitiesParsed = false;
        boolean mappingDataParsed = false;
        if (!abortQuery && mQueryCapabilitiesDataBuffer.size() > 0 && mQueryAnalogMappingDataBuffer.size() > 0) {
            capabilitiesParsed = parseCapabilities(mQueryCapabilitiesDataBuffer);
            mappingDataParsed = parseAnalogMappingData(mQueryAnalogMappingDataBuffer);
        }

        final boolean isDefaultConfigurationAssumed = abortQuery || !capabilitiesParsed || !mappingDataParsed;
        if (isDefaultConfigurationAssumed) {
            initializeDefaultPins();
        }
        enableReadReports();

        // Clean received data
        mQueryCapabilitiesDataBuffer.clear();
        mQueryAnalogMappingDataBuffer.clear();

        // Refresh
        mMainHandler.post(() -> {
            dismissCurrentDialog();
            mPinsAdapter.pinsUpdated();

            if (isDefaultConfigurationAssumed) {
                defaultCapabilitiesAssumedDialog();
            }
        });
    }

    private void defaultCapabilitiesAssumedDialog() {
        Log.d(TAG, "QueryCapabilities not found");

        dismissCurrentDialog();
        // Show dialog
        mAlertDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.pinio_capabilityquery_expired_title)
                .setMessage(R.string.pinio_capabilityquery_expired_message)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        mAlertDialog.show();
    }

    private void dismissCurrentDialog() {
        if (mAlertDialog != null) {
            if (mAlertDialog.isShowing()) {     // Check that is showing to avoid IllegalArgumentException
                mAlertDialog.dismiss();
            }
            mAlertDialog = null;
        }
    }

    private boolean parseCapabilities(List<Byte> capabilitiesData) {
        int endIndex = capabilitiesData.indexOf(SYSEX_END);
        if (capabilitiesData.size() > 2 && capabilitiesData.get(0) == SYSEX_START && capabilitiesData.get(1) == 0x6C && endIndex >= 0) {
            // Separate pin data
            ArrayList<ArrayList<Byte>> pinsBytes = new ArrayList<>();
            ArrayList<Byte> currentPin = new ArrayList<>();
            for (int i = 2; i < endIndex; i++) {        // Skip 2 header bytes and end byte
                byte dataByte = capabilitiesData.get(i);
                if (dataByte != 0x7f) {
                    currentPin.add(dataByte);
                } else {      // Finished current pin
                    pinsBytes.add(currentPin);
                    currentPin = new ArrayList<>();
                }
            }

            // Extract pin info
            mPins.clear();
            int pinNumber = 0;
            for (int j = 0; j < pinsBytes.size(); j++) {
                ArrayList<Byte> pinBytes = pinsBytes.get(j);

                boolean isInput = false, isOutput = false, isAnalog = false, isPWM = false, isInputPullup = false;

                if (pinBytes.size() > 0) {
                    int i = 0;
                    while (i < pinBytes.size()) {
                        int dataByte = pinBytes.get(i) & 0xff;
                        switch (dataByte) {
                            case 0x00:
                                isInput = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x01:
                                isOutput = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x02:
                                isAnalog = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x03:
                                isPWM = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x04:
                                // Servo
                                i++;        // skip resolution byte
                                break;
                            case 0x06:
                                // I2C
                                i++;        // skip resolution byte
                                break;
                            case 0x0b:
                                // INPUT_PULLUP
                                isInputPullup = true;
                                i++;        // skip resolution byte
                                break;
                            default:
                                i++;        // skip resolution byte for unknown commands
                                break;
                        }
                        i++;
                    }

                    PinData pinData = new PinData(pinNumber, isInput, isOutput, isAnalog, isPWM, isInputPullup);
                    Log.d(TAG, "pin id: " + pinNumber + " isInput: " + (pinData.isInput ? "yes" : "no") + " isOutput: " + (pinData.isOutput ? "yes" : "no") + " analog: " + (pinData.isAnalog ? "yes" : "no") + " isInputPullup: " + (pinData.isInputPullup ? "yes" : "no"));
                    mPins.add(pinData);
                }

                pinNumber++;
            }
            return true;

        } else {
            Log.d(TAG, "invalid capabilities received");
            if (capabilitiesData.size() <= 2) {
                Log.d(TAG, "capabilitiesData size <= 2");
            }
            if (capabilitiesData.get(0) != SYSEX_START) {
                Log.d(TAG, "SYSEX_START not present");
            }
            if (endIndex < 0) {
                Log.d(TAG, "SYSEX_END not present");
            }

            return false;
        }
    }

    private boolean parseAnalogMappingData(List<Byte> analogData) {
        int endIndex = analogData.indexOf(SYSEX_END);
        if (analogData.size() > 2 && analogData.get(0) == SYSEX_START && analogData.get(1) == 0x6A && endIndex >= 0) {
            int pinNumber = 0;

            for (int i = 2; i < endIndex; i++) {        // Skip 2 header bytes and end byte
                byte dataByte = analogData.get(i);
                if (dataByte != 0x7f) {
                    int indexOfPinNumber = indexOfPinWithDigitalId(pinNumber);
                    if (indexOfPinNumber >= 0) {
                        mPins.get(indexOfPinNumber).analogPinId = dataByte & 0xff;
                        Log.d(TAG, "pin id: " + pinNumber + " analog id: " + dataByte);
                    } else {
                        Log.d(TAG, "warning: trying to set analog id: " + dataByte + " for pin id: " + pinNumber);
                    }

                }
                pinNumber++;
            }
            return true;
        } else {
            Log.d(TAG, "invalid analog mapping received");
            return false;
        }
    }

    private int indexOfPinWithDigitalId(int digitalPinId) {
        int i = 0;
        while (i < mPins.size() && mPins.get(i).digitalPinId != digitalPinId) {
            i++;
        }
        return i < mPins.size() ? i : -1;
    }

    private int indexOfPinWithAnalogId(int analogPinId) {
        int i = 0;
        while (i < mPins.size() && mPins.get(i).analogPinId != analogPinId) {
            i++;
        }
        return i < mPins.size() ? i : -1;
    }
    // endregion


    // region Pin Management
    @SuppressWarnings("ConstantConditions")
    private void initializeDefaultPins() {
        mPins.clear();

        for (int i = 0; i < DEFAULT_PINS_COUNT; i++) {
            PinData pin = null;
            if (i == 3 || i == 5 || i == 6) {
                pin = new PinData(i, true, true, false, true, false);
            } else if (i >= FIRST_DIGITAL_PIN && i <= LAST_DIGITAL_PIN) {
                pin = new PinData(i, true, true, false, false, false);
            } else if (i >= FIRST_ANALOG_PIN && i <= LAST_ANALOG_PIN) {
                pin = new PinData(i, true, true, true, false, false);
                pin.analogPinId = i - FIRST_ANALOG_PIN;
            }

            if (pin != null) {
                mPins.add(pin);
            }
        }
    }

    private void enableReadReports() {

        // Enable read reports by port
        for (int i = 0; i <= 2; i++) {
            byte data0 = (byte) (0xd0 + i);     // start port 0 digital reporting (0xD0 + port#)
            byte data1 = 1;                     // enable
            byte[] data = new byte[]{data0, data1};
            mUartDataManager.send(mBlePeripheralUart, data, null);
        }

        // Set all pin modes active
        for (int i = 0; i < mPins.size(); i++) {
            // Write pin mode
            PinData pin = mPins.get(i);
            setControlMode(pin, pin.mode);
        }
    }

    private void setControlMode(PinData pin, int mode) {
        int previousMode = pin.mode;

        // Store
        pin.mode = mode;
        pin.digitalValue = PinData.kDigitalValue_Low;       // Reset dialog value when changing mode
        pin.analogValue = 0;                                // Reset analog value when changing mode

        // Write pin mode
        byte[] data = new byte[]{(byte) 0xf4, (byte) pin.digitalPinId, (byte) mode};
        mUartDataManager.send(mBlePeripheralUart, data, null);

        // Update reporting for Analog pins
        if (mode == PinData.kMode_Analog) {
            setAnalogValueReporting(pin, true);
        } else if (previousMode == PinData.kMode_Analog) {
            setAnalogValueReporting(pin, false);
        }
    }

    private void setAnalogValueReporting(PinData pin, boolean enabled) {
        // Write pin mode
        byte data0 = (byte) (0xc0 + pin.analogPinId);       // start analog reporting for pin (192 + pin#)
        byte data1 = (byte) (enabled ? 1 : 0);              // enable

        // send data
        byte[] data = {data0, data1};
        mUartDataManager.send(mBlePeripheralUart, data, null);
    }

    private void setDigitalValue(PinData pin, int value) {
        // Store
        pin.digitalValue = value;
        Log.d(TAG, "setDigitalValue: " + value + " for pin id: " + pin.digitalPinId);

        // Write value
        int port = pin.digitalPinId / 8;
        byte data0 = (byte) (0x90 + port);

        int offset = 8 * port;
        int state = 0;
        for (int i = 0; i <= 7; i++) {
            int pinIndex = indexOfPinWithDigitalId(offset + i);
            if (pinIndex >= 0) {
                int pinValue = mPins.get(pinIndex).digitalValue & 0x1;
                int pinMask = pinValue << i;
                state |= pinMask;
            }
        }

        byte data1 = (byte) (state & 0x7f);      // only 7 bottom bits
        byte data2 = (byte) (state >> 7);        // top bit in second byte

        // send data
        byte[] data = new byte[]{data0, data1, data2};
        mUartDataManager.send(mBlePeripheralUart, data, null);
    }

    private long lastSentAnalogValueTime = 0;

    @SuppressWarnings("UnusedReturnValue")
    private boolean setPMWValue(PinData pin, int value) {

        // Limit the amount of messages sent over Uart
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSentAnalogValueTime >= 100) {
            Log.d(TAG, "pwm elapsed: " + (currentTime - lastSentAnalogValueTime));
            lastSentAnalogValueTime = currentTime;

            // Store
            pin.analogValue = value;

            // Send
            byte[] data;
            if (pin.digitalPinId > 15) {
                // Extended analog
                byte data0 = (byte) (pin.digitalPinId);
                byte data1 = (byte) (value & 0x7f);      // only 7 bottom bits
                byte data2 = (byte) (value >> 7);        // top bit in second byte

                data = new byte[]{SYSEX_START, 0x6f, data0, data1, data2, SYSEX_END};

            } else {
                byte data0 = (byte) (0xe0 + pin.digitalPinId);
                byte data1 = (byte) (value & 0x7f);      // only 7 bottom bits
                byte data2 = (byte) (value >> 7);        // top bit in second byte

                data = new byte[]{data0, data1, data2};
            }
            mUartDataManager.send(mBlePeripheralUart, data, null);

            return true;
        } else {
            Log.d(TAG, "Won't send: Too many slider messages");
            return false;
        }
    }

    private List<Byte> mReceivedPinStateDataBuffer2 = new ArrayList<>();

    private int getUnsignedReceivedPinState(int index) {
        return mReceivedPinStateDataBuffer2.get(index) & 0xff;
    }

    private void receivedPinState(byte[] data) {

        // Append received bytes to buffer
        for (final byte dataByte : data) {
            mReceivedPinStateDataBuffer2.add(dataByte);
        }

        // Check if we received a pin state response
        int endIndex = mReceivedPinStateDataBuffer2.indexOf(SYSEX_END);
        if (mReceivedPinStateDataBuffer2.size() >= 5 && getUnsignedReceivedPinState(0) == SYSEX_START && getUnsignedReceivedPinState(1) == 0x6e && endIndex >= 0) {
            /* pin state response
            * -------------------------------
            * 0  START_SYSEX (0xF0) (MIDI System Exclusive)
            * 1  pin state response (0x6E)
            * 2  pin (0 to 127)
            * 3  pin mode (the currently configured mode)
            * 4  pin state, bits 0-6
            * 5  (optional) pin state, bits 7-13
            * 6  (optional) pin state, bits 14-20
            ...  additional optional bytes, as many as needed
            * N  END_SYSEX (0xF7)
            */

            int pinDigitalId = getUnsignedReceivedPinState(2);
            int pinMode = getUnsignedReceivedPinState(3);
            int pinState = getUnsignedReceivedPinState(4);

            int index = indexOfPinWithDigitalId(pinDigitalId);
            if (index >= 0) {
                PinData pin = mPins.get(index);
                pin.mode = pinMode;
                if (pinMode == PinData.kMode_Analog || pinMode == PinData.kMode_PWM || pinMode == PinData.kMode_Servo) {
                    if (mReceivedPinStateDataBuffer2.size() >= 6) {
                        pin.analogValue = pinState + (getUnsignedReceivedPinState(5) << 7);
                    } else {
                        Log.d(TAG, "Warning: received pinstate for analog pin without analogValue");
                    }
                } else {
                    if (pinState == PinData.kDigitalValue_Low || pinState == PinData.kDigitalValue_High) {
                        pin.digitalValue = pinState;
                    } else {
                        Log.d(TAG, "Warning: received pinstate with unknown digital value. Valid (0,1). Received: " + pinState);
                    }
                }

            } else {
                Log.d(TAG, "Warning: received pinstate for unknown digital pin id: " + pinDigitalId);
            }

            //  Remove from the buffer the bytes parsed
            for (int i = 0; i < endIndex; i++) {
                mReceivedPinStateDataBuffer2.remove(0);
            }
        } else {
            // Each pin message is 3 bytes long
            int data0 = getUnsignedReceivedPinState(0);
            boolean isDigitalReportingMessage = data0 >= 0x90 && data0 <= 0x9F;
            boolean isAnalogReportingMessage = data0 >= 0xe0 && data0 <= 0xef;
//            Log.d(TAG, "data0: "+data0);

            Log.d(TAG, "receivedPinStateDataBuffer size: " + mReceivedPinStateDataBuffer2.size());
            //          Log.d(TAG, "data[0]="+BleUtils.byteToHex(receivedPinStateDataBuffer.get(0))+ "data[1]="+BleUtils.byteToHex(receivedPinStateDataBuffer.get(1)));

            while (mReceivedPinStateDataBuffer2.size() >= 3 && (isDigitalReportingMessage || isAnalogReportingMessage)) {     // Check that current message length is at least 3 bytes
                if (isDigitalReportingMessage) {            // Digital Reporting (per port)
                    /* two byte digital data format, second nibble of byte 0 gives the port number (e.g. 0x92 is the third port, port 2)
                     * 0  digital data, 0x90-0x9F, (MIDI NoteOn, but different data format)
                     * 1  digital pins 0-6 bitmask
                     * 2  digital pin 7 bitmask
                     */

                    int port = getUnsignedReceivedPinState(0) - 0x90;
                    int pinStates = getUnsignedReceivedPinState(1);
                    pinStates |= getUnsignedReceivedPinState(2) << 7;        // PORT 0: use LSB of third byte for pin7, PORT 1: pins 14 & 15
                    updatePinsForReceivedStates(pinStates, port);
                } else if (isAnalogReportingMessage) {        // Analog Reporting (per pin)
                    /* analog 14-bit data format
                     * 0  analog pin, 0xE0-0xEF, (MIDI Pitch Wheel)
                     * 1  analog least significant 7 bits
                     * 2  analog most significant 7 bits
                     */

                    int analogPinId = getUnsignedReceivedPinState(0) - 0xe0;
                    int value = getUnsignedReceivedPinState(1) + (getUnsignedReceivedPinState(2) << 7);

                    int index = indexOfPinWithAnalogId(analogPinId);
                    if (index >= 0) {
                        PinData pin = mPins.get(index);
                        pin.analogValue = value;
                        Log.d(TAG, "received analog value: " + value + " pin analog id: " + analogPinId + " digital Id: " + index);
                    } else {
                        Log.d(TAG, "Warning: received pinstate for unknown analog pin id: " + index);
                    }
                }

                //  Remove from the buffer the bytes parsed
                for (int i = 0; i < 3; i++) {
                    mReceivedPinStateDataBuffer2.remove(0);
                }

                // Setup vars for next message
                if (mReceivedPinStateDataBuffer2.size() >= 3) {
                    data0 = getUnsignedReceivedPinState(0);
                    isDigitalReportingMessage = data0 >= 0x90 && data0 <= 0x9F;
                    isAnalogReportingMessage = data0 >= 0xe0 && data0 <= 0xef;

                } else {
                    isDigitalReportingMessage = false;
                    isAnalogReportingMessage = false;
                }
            }
        }

        // Refresh
        mMainHandler.post(() -> mPinsAdapter.pinsUpdated());
    }

    private void updatePinsForReceivedStates(int pinStates, int port) {
        int offset = 8 * port;

        // Iterate through all pins
        for (int i = 0; i <= 7; i++) {
            int mask = 1 << i;
            int state = (pinStates & mask) >> i;

            int digitalId = offset + i;

            int index = indexOfPinWithDigitalId(digitalId);
            if (index >= 0) {
                PinData pin = mPins.get(index);
                pin.digitalValue = state;
                //Log.d(TAG, "update pinid: " + digitalId + " digitalValue: " + state);
            }
        }
    }

    // endregion

    // region UartDataManagerListener

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        Log.d(TAG, "uart rx read (hex): " + BleUtils.bytesToHex2(data));

        switch (mUartStatus) {
            case kUartStatus_QueryCapabilities:
                receivedQueryCapabilities(data);
                break;
            case kUartStatus_QueryAnalogMapping:
                receivedAnalogMapping(data);
                break;
            default:
                receivedPinState(data);
                break;
        }
    }

    // endregion

    // region Adapter
    private static class PinsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Constants
        private static final int kCellType_SectionTitle = 0;
        private static final int kCellType_PinCell = 1;

        private static final int kPinCellsStartPosition = 1;

        interface Listener {
            void onModeSelected(PinIOFragment.PinData pinData, int mode);

            void onDigitalValueSelected(PinIOFragment.PinData pinData, int mode);

            void onSetPMWValue(PinIOFragment.PinData pinData, int progress);
        }

        // Data Structures
        private class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;

            SectionViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
            }
        }

        private class PinViewHolder extends RecyclerView.ViewHolder {
            ViewGroup titleViewGroup;
            TextView nameTextView;
            TextView valueTextView;
            TextView modeTextView;
            private ViewGroup expandedViewGroup;
            RadioGroup modeRadioGroup;
            RadioButton inputRadioButton;
            RadioButton outputRadioButton;
            RadioButton pwmRadioButton;
            RadioButton analogRadioButton;
            RadioGroup inputRadioGroup;
            RadioButton floatingRadioButton;
            RadioButton pullupRadioButton;
            RadioGroup stateRadioGroup;
            RadioButton lowRadioButton;
            RadioButton highRadioButton;
            SeekBar pmwSeekBar;
            View spacer2View;
            View spacer3View;

            PinViewHolder(View view) {
                super(view);
                titleViewGroup = view.findViewById(R.id.titleViewGroup);
                nameTextView = view.findViewById(R.id.nameTextView);
                valueTextView = view.findViewById(R.id.stateTextView);
                modeTextView = view.findViewById(R.id.modeTextView);
                expandedViewGroup = view.findViewById(R.id.expandedViewGroup);
                modeRadioGroup = view.findViewById(R.id.modeRadioGroup);
                inputRadioButton = view.findViewById(R.id.inputRadioButton);
                outputRadioButton = view.findViewById(R.id.outputRadioButton);
                pwmRadioButton = view.findViewById(R.id.pwmRadioButton);
                analogRadioButton = view.findViewById(R.id.analogRadioButton);
                inputRadioGroup = view.findViewById(R.id.inputRadioGroup);
                floatingRadioButton = view.findViewById(R.id.floatingRadioButton);
                pullupRadioButton = view.findViewById(R.id.pullupRadioButton);
                stateRadioGroup = view.findViewById(R.id.stateRadioGroup);
                lowRadioButton = view.findViewById(R.id.lowRadioButton);
                highRadioButton = view.findViewById(R.id.highRadioButton);
                pmwSeekBar = view.findViewById(R.id.pmwSeekBar);
                spacer2View = view.findViewById(R.id.spacer2View);
                spacer3View = view.findViewById(R.id.spacer3View);
            }

            void togleExpanded() {
                final int index = getPinViewHolderId();
                if (index >= 0 && index < mExpandedNodes.length) {
                    mExpandedNodes[index] = !mExpandedNodes[index];
                    animateExpanded();
                } else {
                    Log.d(TAG, "toggleExpanded invalid index");
                }
            }

            private void animateExpanded() {

                if (mExpandedNodes[getPinViewHolderId()]) {
                    AdapterUtils.expand(expandedViewGroup);
                } else {
                    AdapterUtils.collapse(expandedViewGroup);
                }
            }

            int getPinViewHolderId() {
                return getAdapterPosition() - kPinCellsStartPosition;
            }

            boolean isExpanded() {
                final int position = getPinViewHolderId();
                return mExpandedNodes != null && mExpandedNodes.length >= position && mExpandedNodes[position];
            }
        }

        // Data
        private Context mContext;
        private List<PinData> mPins;
        private Listener mListener;
        private boolean[] mExpandedNodes;

        // region Lifecycle
        PinsAdapter(@NonNull Context context, @NonNull List<PinData> pins, @NonNull Listener listener) {
            mContext = context.getApplicationContext();
            mPins = pins;
            mListener = listener;
            pinsUpdated();
        }

        void pinsUpdated() {            // Use instead of notifyDataSetChanged to update the mExpandedNodes array
            if (mExpandedNodes == null || mExpandedNodes.length < mPins.size()) {
                boolean[] newArray = new boolean[mPins.size()];
                Arrays.fill(newArray, false);
                if (mExpandedNodes != null) {
                    System.arraycopy(mExpandedNodes, 0, newArray, 0, mExpandedNodes.length);
                }
                mExpandedNodes = newArray;
            }

            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            super.getItemViewType(position);

            if (position == 0) {
                return kCellType_SectionTitle;
            } else {
                return kCellType_PinCell;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case kCellType_SectionTitle: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_section_item, parent, false);
                    return new SectionViewHolder(view);
                }
                case kCellType_PinCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_pinio_item, parent, false);
                    return new PinViewHolder(view);
                }
                default: {
                    Log.e(TAG, "Unknown cell type");
                    throw new AssertionError("Unknown cell type");
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LocalizationManager localizationManager = LocalizationManager.getInstance();
            final int viewType = getItemViewType(position);
            switch (viewType) {
                case kCellType_SectionTitle:
                    SectionViewHolder sectionViewHolder = (SectionViewHolder) holder;
                    sectionViewHolder.titleTextView.setText(localizationManager.getString(mContext, "pinio_pins_header"));
                    break;

                case kCellType_PinCell:
                    final int pinIndex = position - kPinCellsStartPosition;
                    PinViewHolder pinViewHolder = (PinViewHolder) holder;

                    PinData pin = mPins.get(pinIndex);

                    pinViewHolder.titleViewGroup.setOnClickListener(view -> pinViewHolder.togleExpanded());
                    pinViewHolder.expandedViewGroup.setVisibility(pinViewHolder.isExpanded() ? View.VISIBLE : View.GONE);

                    // UI: Name
                    String name;
                    if (pin.isAnalog) {
                        name = String.format(mContext.getString(R.string.pinio_pinname_analog_format), pin.digitalPinId, pin.analogPinId);
                    } else {
                        name = String.format(mContext.getString(R.string.pinio_pinname_digital_format), pin.digitalPinId);
                    }
                    pinViewHolder.nameTextView.setText(name);

                    // UI: Mode
                    pinViewHolder.modeTextView.setText(stringForPinMode(pin.mode));

                    // UI: State
                    String valueString;
                    switch (pin.mode) {
                        case PinData.kMode_Input:
                        case PinData.kMode_InputPullup:
                            valueString = stringForPinDigitalValue(pin.digitalValue);
                            break;
                        case PinData.kMode_Output:
                            valueString = stringForPinDigitalValue(pin.digitalValue);
                            break;
                        case PinData.kMode_Analog:
                            valueString = String.valueOf(pin.analogValue);
                            break;
                        case PinData.kMode_PWM:
                            valueString = String.valueOf(pin.analogValue);
                            break;
                        default:
                            valueString = "";
                            break;
                    }
                    pinViewHolder.valueTextView.setText(valueString);

                    // Setup mode
                    pinViewHolder.modeRadioGroup.setOnCheckedChangeListener(null);
                    pinViewHolder.inputRadioButton.setChecked(pin.mode == PinData.kMode_Input || pin.mode == PinData.kMode_InputPullup);
                    pinViewHolder.inputRadioButton.setVisibility(pin.isInput || pin.isInputPullup ? View.VISIBLE : View.GONE);

                    pinViewHolder.outputRadioButton.setChecked(pin.mode == PinData.kMode_Output);
                    pinViewHolder.outputRadioButton.setVisibility(pin.isOutput ? View.VISIBLE : View.GONE);

                    pinViewHolder.pwmRadioButton.setChecked(pin.mode == PinData.kMode_PWM);
                    pinViewHolder.pwmRadioButton.setVisibility(pin.isPwm ? View.VISIBLE : View.GONE);

                    pinViewHolder.analogRadioButton.setChecked(pin.mode == PinData.kMode_Analog);
                    pinViewHolder.analogRadioButton.setVisibility(pin.isAnalog ? View.VISIBLE : View.GONE);

                    pinViewHolder.modeRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                        int newMode = PinData.kMode_Unknown;
                        switch (i) {
                            case R.id.inputRadioButton:
                                newMode = PinData.kMode_Input;      // Reset mode to input
                                break;
                            case R.id.outputRadioButton:
                                newMode = PinData.kMode_Output;
                                break;
                            case R.id.pwmRadioButton:
                                newMode = PinData.kMode_PWM;
                                break;
                            case R.id.analogRadioButton:
                                newMode = PinData.kMode_Analog;
                                break;
                        }

                        if (newMode != PinData.kMode_Unknown) {
                            mListener.onModeSelected(pin, newMode);
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                    });

                    // Setup input mode
                    final boolean isInputModeVisible = pin.mode == PinData.kMode_Input || pin.mode == PinData.kMode_InputPullup;
                    pinViewHolder.inputRadioGroup.setVisibility(isInputModeVisible ? View.VISIBLE : View.GONE);
                    if (isInputModeVisible) {
                        pinViewHolder.inputRadioGroup.setOnCheckedChangeListener(null);
                        pinViewHolder.floatingRadioButton.setChecked(pin.mode == PinData.kMode_Input);
                        pinViewHolder.pullupRadioButton.setChecked(pin.mode == PinData.kMode_InputPullup);
                        pinViewHolder.inputRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                            int newMode = PinData.kMode_Input;
                            switch (i) {
                                case R.id.floatingRadioButton:
                                    newMode = PinData.kMode_Input;
                                    break;
                                case R.id.pullupRadioButton:
                                    newMode = PinData.kMode_InputPullup;
                                    break;
                            }

                            mListener.onModeSelected(pin, newMode);
                            notifyItemChanged(position);
                        });
                    }

                    // Setup output state
                    final boolean isStateVisible = pin.mode == PinData.kMode_Output;
                    pinViewHolder.stateRadioGroup.setVisibility(isStateVisible ? View.VISIBLE : View.GONE);
                    if (isStateVisible) {
                        pinViewHolder.stateRadioGroup.setOnCheckedChangeListener(null);
                        pinViewHolder.lowRadioButton.setChecked(pin.digitalValue == PinData.kDigitalValue_Low);
                        pinViewHolder.highRadioButton.setChecked(pin.digitalValue == PinData.kDigitalValue_High);
                        pinViewHolder.stateRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                            int newState = PinData.kDigitalValue_Low;
                            switch (i) {
                                case R.id.lowRadioButton:
                                    newState = PinData.kDigitalValue_Low;
                                    break;
                                case R.id.highRadioButton:
                                    newState = PinData.kDigitalValue_High;
                                    break;
                            }

                            mListener.onDigitalValueSelected(pin, newState);

                            notifyItemChanged(holder.getAdapterPosition());
                        });

                    }

                    // pwm slider bar
                    boolean isPwmBarVisible = pin.mode == PinData.kMode_PWM;
                    pinViewHolder.pmwSeekBar.setVisibility(isPwmBarVisible ? View.VISIBLE : View.GONE);
                    pinViewHolder.pmwSeekBar.setProgress(pin.analogValue);

                    pinViewHolder.pmwSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser) {
//                        pin.analogValue = progress;
                                mListener.onSetPMWValue(pin, progress);
                                pinViewHolder.valueTextView.setText(String.valueOf(progress));
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            mListener.onSetPMWValue(pin, pin.analogValue);
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                    });

                    // spacer visibility (spacers are shown if pwm or analog are visible)
                    final boolean isSpacer2Visible = pin.isPwm || pin.isAnalog;
                    pinViewHolder.spacer2View.setVisibility(isSpacer2Visible ? View.VISIBLE : View.GONE);

                    final boolean isSpacer3Visible = pin.isPwm && pin.isAnalog;
                    pinViewHolder.spacer3View.setVisibility(isSpacer3Visible ? View.VISIBLE : View.GONE);

                    break;
            }
        }

        @Override
        public int getItemCount() {
            return 1 + mPins.size();
        }

        // endregion

        // region Utils
        private String stringForPinMode(int mode) {
            int modeStringResourceId;
            switch (mode) {
                case PinData.kMode_Input:
                    modeStringResourceId = R.string.pinio_pintype_inputfloating_long;
                    break;
                case PinData.kMode_Output:
                    modeStringResourceId = R.string.pinio_pintype_output;
                    break;
                case PinData.kMode_Analog:
                    modeStringResourceId = R.string.pinio_pintype_analog;
                    break;
                case PinData.kMode_PWM:
                    modeStringResourceId = R.string.pinio_pintype_pwm;
                    break;
                case PinData.kMode_Servo:
                    modeStringResourceId = R.string.pinio_pintype_servo;
                    break;
                case PinData.kMode_InputPullup:
                    modeStringResourceId = R.string.pinio_pintype_inputpullup_long;
                    break;

                default:
                    modeStringResourceId = R.string.pinio_pintype_unknown;
                    break;
            }

            return mContext.getString(modeStringResourceId);
        }

        private String stringForPinDigitalValue(int digitalValue) {
            int stateStringResourceId;
            switch (digitalValue) {
                case PinData.kDigitalValue_Low:
                    stateStringResourceId = R.string.pinio_pintype_low;
                    break;
                case PinData.kDigitalValue_High:
                    stateStringResourceId = R.string.pinio_pintype_high;
                    break;
                default:
                    stateStringResourceId = R.string.pinio_pintype_unknown;
                    break;
            }

            return mContext.getString(stateStringResourceId);
        }
        // endregion

    }
    // endregion
}