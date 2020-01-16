package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.style.UartStyle;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlotterFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = PlotterFragment.class.getSimpleName();

    // Config
    private final static int xMaxEntriesMin = 6;
    private final static int xMaxEntriesMax = 100;

    // UI
    private SeekBar xMaxEntriesSeekBar;
    private LineChart mChart;

    // Data
    private UartDataManager mUartDataManager;
    private long mOriginTimestamp;
    private List<BlePeripheralUart> mBlePeripheralsUart = new ArrayList<>();
    private boolean mIsAutoScrollEnabled = true;
    private int mVisibleInterval = 20;        // in seconds
    private Map<String, DashPathEffect> mLineDashPathEffectForPeripheral = new HashMap<>();
    private Map<String, List<LineDataSet>> mDataSetsForPeripheral = new HashMap<>();
    private LineDataSet mLastDataSetModified;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // region Fragment Lifecycle
    public static PlotterFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        PlotterFragment fragment = new PlotterFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public PlotterFragment() {
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
        return inflater.inflate(R.layout.fragment_plotter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.plotter_tab_title);

        // UI
        mChart = view.findViewById(R.id.chart);
        WeakReference<PlotterFragment> weakThis = new WeakReference<>(this);
        SwitchCompat autoscrollSwitch = view.findViewById(R.id.autoscrollSwitch);
        autoscrollSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                PlotterFragment fragment = weakThis.get();          // Fix detected memory leak
                if (fragment != null) {
                    fragment.mIsAutoScrollEnabled = isChecked;
                    fragment.mChart.setDragEnabled(!isChecked);
                    fragment.notifyDataSetChanged();
                }
            }
        });
        xMaxEntriesSeekBar = view.findViewById(R.id.xMaxEntriesSeekBar);
        xMaxEntriesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    final float factor = progress / 100.f;
                    mVisibleInterval = Math.round((xMaxEntriesMax - xMaxEntriesMin) * factor + xMaxEntriesMin);
                    notifyDataSetChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        autoscrollSwitch.setChecked(mIsAutoScrollEnabled);
        mChart.setDragEnabled(!mIsAutoScrollEnabled);
        setXMaxEntriesValue(mVisibleInterval);

        // Setup
        Context context = getContext();
        if (context != null) {
            mUartDataManager = new UartDataManager(context, this, true);
            mOriginTimestamp = System.currentTimeMillis();

            setupChart();
            setupUart();
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

        if (mBlePeripheralsUart != null) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart) {
                blePeripheralUart.uartDisable();
            }
            mBlePeripheralsUart.clear();
            mBlePeripheralsUart = null;
        }

        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.plotter_help_title), getString(R.string.plotter_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .replace(R.id.contentLayout, helpFragment, "Help");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // endregion


    // region Uart

    private boolean isInMultiUartMode() {
        return mBlePeripheral == null;
    }

    private void setupUart() {
        // Line dashes assigned to peripherals
        final DashPathEffect[] dashPathEffects = UartStyle.defaultDashPathEffects();

        // Enable uart
        if (isInMultiUartMode()) {
            mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
            List<BlePeripheral> connectedPeripherals = BleScanner.getInstance().getConnectedPeripherals();
            for (int i = 0; i < connectedPeripherals.size(); i++) {
                BlePeripheral blePeripheral = connectedPeripherals.get(i);
                mLineDashPathEffectForPeripheral.put(blePeripheral.getIdentifier(), dashPathEffects[i % dashPathEffects.length]);

                if (!BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mUartDataManager, status -> {

                        String peripheralName = blePeripheral.getName();
                        if (peripheralName == null) {
                            peripheralName = blePeripheral.getIdentifier();
                        }

                        String finalPeripheralName = peripheralName;
                        mMainHandler.post(() -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                // Done
                                Log.d(TAG, "Uart enabled for: " + finalPeripheralName);
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
                }
            }

        } else {       //  Single peripheral mode
            if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
                mLineDashPathEffectForPeripheral.put(mBlePeripheral.getIdentifier(), dashPathEffects[0]);
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Done
                        Log.d(TAG, "Uart enabled");
                    } else {
                        Context context = getContext();
                        if (context != null) {
                            WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
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
        }
    }

    // endregion

    // region Line Chart
    private void setupChart() {

        mChart.getDescription().setEnabled(false);
        mChart.getXAxis().setGranularityEnabled(true);
        mChart.getXAxis().setGranularity(5);

        mChart.setExtraOffsets(10, 10, 10, 0);
        mChart.getLegend().setEnabled(false);

        mChart.setNoDataTextColor(Color.BLACK);
        mChart.setNoDataText(getString(R.string.plotter_nodata));
    }

    private void setXMaxEntriesValue(int value) {
        final float percent = Math.max(0, (value - xMaxEntriesMin)) / (float) (xMaxEntriesMax - xMaxEntriesMin);
        final int progress = Math.round(percent * xMaxEntriesSeekBar.getMax());
        xMaxEntriesSeekBar.setProgress(progress);
    }

    private void addEntry(@NonNull String peripheralIdentifier, int index, float value, float timestamp) {
        Entry entry = new Entry(timestamp, value);

        boolean dataSetExists = false;
        List<LineDataSet> dataSets = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (dataSets != null) {
            if (index < dataSets.size()) {
                // Add entry to existing dataset
                LineDataSet dataSet = dataSets.get(index);
                dataSet.addEntry(entry);
                dataSetExists = true;
            }
        }

        if (!dataSetExists) {
            appendDataset(peripheralIdentifier, entry, index);

            List<ILineDataSet> allDataSets = new ArrayList<>();
            for (List<LineDataSet> dataSetLists : mDataSetsForPeripheral.values()) {
                allDataSets.addAll(dataSetLists);
            }
            final LineData lineData = new LineData(allDataSets);
            mChart.setData(lineData);
        }

        List<LineDataSet> dataSets2 = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (dataSets2 != null && index < dataSets2.size()) {
            mLastDataSetModified = dataSets2.get(index);
        }
    }

    private void notifyDataSetChanged() {
        if (mChart.getData() != null) {
            mChart.getData().notifyDataChanged();
        }
        mChart.notifyDataSetChanged();
        mChart.invalidate();
        mChart.setVisibleXRangeMaximum(mVisibleInterval);
        mChart.setVisibleXRangeMinimum(mVisibleInterval);

        if (mLastDataSetModified != null && mIsAutoScrollEnabled) {
            final List<Entry> values = mLastDataSetModified.getValues();

            float x = 0;
            if (values != null && values.size() > 0) {
                Entry value = values.get(values.size() - 1);
                if (value != null) {
                    x = value.getX();
                }
            }

            final float xOffset = x - (mVisibleInterval - 1);
            mChart.moveViewToX(xOffset);
        }
    }

    private void appendDataset(@NonNull String peripheralIdentifier, @NonNull Entry entry, int index) {
        LineDataSet dataSet = new LineDataSet(null, "Values[" + peripheralIdentifier + ":" + index + "]");
        dataSet.addEntry(entry);
        dataSet.addEntry(entry);

        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2);
        final int[] colors = UartStyle.defaultColors();
        final int color = colors[index % colors.length];
        dataSet.setColor(color);
        final DashPathEffect dashPatternEffect = mLineDashPathEffectForPeripheral.get(peripheralIdentifier);
        dataSet.setFormLineDashEffect(dashPatternEffect);

        List<LineDataSet> previousDataSets = mDataSetsForPeripheral.get(peripheralIdentifier);
        if (previousDataSets != null) {
            previousDataSets.add(dataSet);
        } else {
            List<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(dataSet);
            mDataSetsForPeripheral.put(peripheralIdentifier, dataSets);
        }
    }

    // endregion

    // region UartDataManagerListener
    private static final byte kLineSeparator = 10;

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        /*
        Log.d(TAG, "uart rx read (hex): " + BleUtils.bytesToHex2(data));
        try {
            Log.d(TAG, "uart rx read (utf8): " + new String(data, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }*/

        // Find last separator
        boolean found = false;
        int i = data.length - 1;
        while (i >= 0 && !found) {
            if (data[i] == kLineSeparator) {
                found = true;
            } else {
                i--;
            }
        }
        final int lastSeparator = i + 1;

        //
        if (found) {
            final byte[] subData = Arrays.copyOfRange(data, 0, lastSeparator);
            final float currentTimestamp = (System.currentTimeMillis() - mOriginTimestamp) / 1000.f;
            final String dataString = new String(subData, StandardCharsets.UTF_8);
            //Log.d(TAG, "data: " + dataString);

            final String[] lineStrings = dataString.replace("\r", "").split("\n");
            for (String lineString : lineStrings) {
//                    Log.d(TAG, "line: " + lineString);
                final String[] valuesStrings = lineString.split("[,; \t]");
                int j = 0;
                for (String valueString : valuesStrings) {
                    boolean isValid = true;
                    float value = 0;
                    if (valueString != null) {
                        try {
                            value = Float.parseFloat(valueString);
                        } catch (NumberFormatException ignored) {
                            isValid = false;
                        }
                    } else {
                        isValid = false;
                    }

                    if (isValid && peripheralIdentifier != null) {
                        //Log.d(TAG, "value " + j + ": (" + currentTimestamp + ", " + value + ")");
                        //Log.d(TAG, "value " + j + ": " + value);
                        addEntry(peripheralIdentifier, j, value, currentTimestamp);
                        j++;
                    }
                }

                mMainHandler.post(this::notifyDataSetChanged);
            }

        }

        mUartDataManager.removeRxCacheFirst(lastSeparator, peripheralIdentifier);
    }

    // endregion
}
