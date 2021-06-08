package com.adafruit.bluefruit.le.connect.models;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;
import com.adafruit.bluefruit.le.connect.utils.SingleLiveEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;

public class ScannerViewModel extends AndroidViewModel implements BleScanner.BleScannerListener {
    // region Constants
    private final static String TAG = ScannerViewModel.class.getSimpleName();
    // endregion

    // region Data - Scanning
    private BleScanner mScanner = BleScanner.getInstance();
    private final MutableLiveData<Boolean> mIsScanning = new MutableLiveData<>();
    private final MutableLiveData<List<BlePeripheral>> mBlePeripherals = new MutableLiveData<>();
    private final SingleLiveEvent<Integer> mScanningErrorCode = new SingleLiveEvent<>();
    // endregion

    // region Data - Filters
    private final MutableLiveData<String> mFilterName = new MutableLiveData<>();
    private final MutableLiveData<Integer> mRssiFilterValue = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mIsUnnamedEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mIsOnlyUartEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mIsFilterNameExact = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mIsFilterNameCaseInsensitive = new MutableLiveData<>();
    private final MediatorLiveData<FilterData> mFiltersLiveDataMerger = new MediatorLiveData<>();
    private final MediatorLiveData<ScanData> mScanFilterLiveDataMerger = new MediatorLiveData<>();

    private final MutableLiveData<Integer> mNumPeripheralsFilteredOut = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNumPeripheralsFiltered = new MutableLiveData<>();

    private final LiveData<String> mRssiFilterDescription = Transformations.map(mRssiFilterValue, rssi -> String.format(Locale.ENGLISH, getApplication().getString(R.string.scanner_filter_rssivalue_format), rssi));

    private final LiveData<Boolean> mIsAnyFilterEnabled = Transformations.map(mFiltersLiveDataMerger, FilterData::isAnyFilterEnabled);

    private final LiveData<String> mFiltersDescription = Transformations.map(mFiltersLiveDataMerger, input -> {
        String filtersDescription = input.getDescription();
        return filtersDescription != null ? String.format(Locale.ENGLISH, getApplication().getString(R.string.scanner_filter_currentfilter_format), filtersDescription) : getApplication().getString(R.string.scanner_filter_nofilter);
    });


    private final LiveData<List<BlePeripheral>> mFilteredBlePeripherals = Transformations.switchMap(mScanFilterLiveDataMerger, input -> {
        FilterData filterData = input.filterData;
        if (filterData == null) return null;     // Filter Data not initialized yet

        // Copy all existing results
        List<BlePeripheral> results = new ArrayList<>(input.blePeripherals);

        // Apply filters
        if (filterData.isOnlyUartEnabled) {
            for (Iterator<BlePeripheral> it = results.iterator(); it.hasNext(); ) {
                if (BleScanner.getDeviceType(it.next()) != BleScanner.kDeviceType_Uart) {
                    it.remove();
                }
            }
        }

        if (!filterData.isUnnamedEnabled) {
            for (Iterator<BlePeripheral> it = results.iterator(); it.hasNext(); ) {
                if (it.next().getDevice().getName() == null) {
                    it.remove();
                }
            }
        }

        if (filterData.name != null && !filterData.name.isEmpty()) {
            for (Iterator<BlePeripheral> it = results.iterator(); it.hasNext(); ) {
                String name = it.next().getDevice().getName();
                boolean testPassed = false;
                if (name != null) {
                    if (filterData.isNameMatchExact) {
                        if (filterData.isNameMatchCaseInSensitive) {
                            testPassed = name.compareToIgnoreCase(filterData.name) == 0;
                        } else {
                            testPassed = name.compareTo(filterData.name) == 0;
                        }
                    } else {
                        if (filterData.isNameMatchCaseInSensitive) {
                            testPassed = name.toLowerCase().contains(filterData.name.toLowerCase());
                        } else {
                            testPassed = name.contains(filterData.name);
                        }
                    }
                }
                if (!testPassed) {
                    it.remove();
                }
            }
        }

        for (Iterator<BlePeripheral> it = results.iterator(); it.hasNext(); ) {
            if (it.next().getRssi() < filterData.rssi) {
                it.remove();
            }
        }

        // Sort devices alphabetically
        Collections.sort(results, (o1, o2) -> getResultNameForOrdering(o1).compareTo(getResultNameForOrdering(o2)));


        // Update related variables
        mNumPeripheralsFiltered.setValue(results.size());
        final int numPeripheralsFilteredOut = input.blePeripherals.size() - results.size();
        mNumPeripheralsFilteredOut.setValue(numPeripheralsFilteredOut);

        // Create result
        MutableLiveData<List<BlePeripheral>> liveResults = new MutableLiveData<>();
        liveResults.setValue(results);

        return liveResults;
    });


    // endregion

    // region Data - Connection
    private final SingleLiveEvent<BlePeripheral> mBlePeripheralsConnectionChanged = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> mBlePeripheralsConnectionErrorMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<BlePeripheral> mBlePeripheralDiscoveredServices = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> mIsMultiConnectEnabled = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNumDevicesConnected = new MutableLiveData<>();
    // endregionSe

    // region Setup
    public ScannerViewModel(@NonNull Application application) {
        super(application);

        // Add broadcast receiver
        registerGattReceiver();

        // Setup scanning
        mIsScanning.setValue(false);
        mScanner.setListener(this);

        // Setup mFiltersLiveDataMerger
        setDefaultFilters(true);
        mFiltersLiveDataMerger.addSource(mFilterName, name -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null) {
                filter.name = name;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        mFiltersLiveDataMerger.addSource(mRssiFilterValue, rssiValue -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null && rssiValue != null) {
                filter.rssi = rssiValue;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        mFiltersLiveDataMerger.addSource(mIsUnnamedEnabled, enabled -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null && enabled != null) {
                filter.isUnnamedEnabled = enabled;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        mFiltersLiveDataMerger.addSource(mIsOnlyUartEnabled, enabled -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null && enabled != null) {
                filter.isOnlyUartEnabled = enabled;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        mFiltersLiveDataMerger.addSource(mIsFilterNameExact, enabled -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null && enabled != null) {
                filter.isNameMatchExact = enabled;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        mFiltersLiveDataMerger.addSource(mIsFilterNameCaseInsensitive, enabled -> {
            FilterData filter = mFiltersLiveDataMerger.getValue();
            if (filter != null && enabled != null) {
                filter.isNameMatchCaseInSensitive = enabled;
                mFiltersLiveDataMerger.setValue(filter);
            }
        });

        // Setup mScanFilterLiveDataMerger
        ScanData scanData = new ScanData();
        mScanFilterLiveDataMerger.setValue(scanData);
        mScanFilterLiveDataMerger.addSource(mBlePeripherals, blePeripherals -> {
            ScanData data = mScanFilterLiveDataMerger.getValue();
            if (data != null) {
                data.blePeripherals = blePeripherals;
                mScanFilterLiveDataMerger.setValue(data);
            }
        });
        mScanFilterLiveDataMerger.addSource(mFiltersLiveDataMerger, filterData -> {
            ScanData data = mScanFilterLiveDataMerger.getValue();
            if (filterData != null && data != null) {
                data.filterData = filterData;
                mScanFilterLiveDataMerger.setValue(data);
            }
        });

        // Setup Connection
        mBlePeripheralDiscoveredServices.setValue(null);
        mIsMultiConnectEnabled.setValue(false);
        mNumDevicesConnected.setValue(0);
        mNumPeripheralsFilteredOut.setValue(0);
        mNumPeripheralsFiltered.setValue(0);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Stop and remove listener
        stop();
        if (mScanner.getListener() == this) {       // Replace only if is still myself
            mScanner.setListener(null);
        }
        saveFilters();      // optional: save filters (useful while debugging because onDestroy is not called and filters are not saved)

        // Unregister receiver
        unregisterGattReceiver();

        mScanner = null;
    }

    // endregion

    //  region Getters / Setters
    public LiveData<Boolean> isScanning() {
        return mIsScanning;
    }

    public LiveData<List<BlePeripheral>> getFilteredBlePeripherals() {
        return mFilteredBlePeripherals;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public @Nullable
    BlePeripheral getPeripheralAtFilteredPosition(int position) {
        List<BlePeripheral> blePeripherals = mFilteredBlePeripherals.getValue();
        if (blePeripherals != null && position >= 0 && position < blePeripherals.size()) {
            final BlePeripheral blePeripheral = blePeripherals.get(position);
            return blePeripheral;
        } else {
            return null;
        }
    }

    public @Nullable
    BlePeripheral getPeripheralWithIdentifier(@NonNull String identifier) {
        BlePeripheral result = null;
        int i = 0;
        List<BlePeripheral> blePeripherals = mBlePeripherals.getValue();
        if (blePeripherals != null) {
            while (i < blePeripherals.size() && result == null) {
                BlePeripheral blePeripheral = blePeripherals.get(i);
                if (identifier.equals(blePeripheral.getIdentifier())) {
                    result = blePeripheral;
                } else {
                    i++;
                }
            }
        }

        return result;
    }

    public LiveData<Integer> getScanningErrorCode() {
        return mScanningErrorCode;
    }

    public LiveData<String> getFiltersDescription() {
        return mFiltersDescription;
    }

    public LiveData<Integer> getRssiFilterValue() {
        return mRssiFilterValue;
    }

    public LiveData<String> getRssiFilterDescription() {
        return mRssiFilterDescription;
    }

    public void setDefaultFilters(boolean shouldRestoreSavedValues) {
        FilterData filterData = new FilterData(shouldRestoreSavedValues);

        mFilterName.setValue(filterData.name);
        mRssiFilterValue.setValue(filterData.rssi);
        mIsOnlyUartEnabled.setValue(filterData.isOnlyUartEnabled);
        mIsUnnamedEnabled.setValue(filterData.isUnnamedEnabled);
        mIsFilterNameExact.setValue(filterData.isNameMatchExact);
        mIsFilterNameCaseInsensitive.setValue(filterData.isNameMatchCaseInSensitive);

        mFiltersLiveDataMerger.setValue(filterData);
    }

    public LiveData<Boolean> isAnyFilterEnabled() {
        return mIsAnyFilterEnabled;
    }

    public LiveData<Boolean> isFilterUnnamedEnabled() {
        return mIsUnnamedEnabled;
    }

    public LiveData<Boolean> isFilterOnlyUartEnabled() {
        return mIsOnlyUartEnabled;
    }

    public LiveData<Boolean> isFilterNameExact() {
        return mIsFilterNameExact;
    }

    public LiveData<Boolean> isFilterNameCaseInsensitive() {
        return mIsFilterNameCaseInsensitive;
    }

    public SingleLiveEvent<BlePeripheral> getBlePeripheralsConnectionChanged() {
        return mBlePeripheralsConnectionChanged;
    }

    public SingleLiveEvent<String> getConnectionErrorMessage() {
        return mBlePeripheralsConnectionErrorMessage;
    }

    public LiveData<Boolean> isMultiConnectEnabled() {
        return mIsMultiConnectEnabled;
    }

    public boolean isMultiConnectEnabledValue() {
        return mIsMultiConnectEnabled.getValue() != null ? mIsMultiConnectEnabled.getValue() : false;
    }

    public LiveData<Integer> getNumDevicesConnected() {
        return mNumDevicesConnected;
    }

    public LiveData<Integer> getNumPeripheralsFilteredOut() {
        return mNumPeripheralsFilteredOut;
    }

    public LiveData<Integer> getNumPeripheralsFiltered() {
        return mNumPeripheralsFiltered;
    }

    public LiveData<BlePeripheral> getBlePeripheralDiscoveredServices() {
        return mBlePeripheralDiscoveredServices;
    }

    // endregion

    // region Actions
    public void refresh() {
        mScanner.refresh();
    }

    public void start() {
        if (!mScanner.isScanning()) {
            Log.d(TAG, "start scanning");
            mScanner.start();
        } else {
            Log.d(TAG, "start scanning: already was scanning");
        }
    }

    public void stop() {
        if (mScanner.isScanning()) {
            Log.d(TAG, "stop scanning");
            mScanner.stop();
        }
    }

    public void setNameFilter(String name) {
        mFilterName.setValue(name);
    }

    public void setFilterUnnamedEnabled(boolean enabled) {
        mIsUnnamedEnabled.setValue(enabled);
    }

    public void setFilterOnlyUartEnabled(boolean enabled) {
        mIsOnlyUartEnabled.setValue(enabled);
    }

    public void setFilterRssiValue(int rssi) {
        mRssiFilterValue.setValue(rssi);
    }

    public void setIsFilterNameExact(boolean enabled) {
        mIsFilterNameExact.setValue(enabled);
    }

    public void setIsFilterNameCaseInsensitive(boolean enabled) {
        mIsFilterNameCaseInsensitive.setValue(enabled);
    }

    public void setIsMultiConnectEnabled(boolean enabled) {
        mIsMultiConnectEnabled.setValue(enabled);

        if (!enabled) {
            disconnectAllPeripherals();
        }
    }

    public void disconnectAllPeripherals() {
        if (mScanner != null) {
            mScanner.disconnectFromAll();
        }
    }

    public void saveFilters() {
        ScanData data = mScanFilterLiveDataMerger.getValue();
        if (data != null) {
            data.saveFilters();
        }
    }

    // endregion

    // region BleScannerListener
    @Override
    public void onScanPeripheralsUpdated(List<BlePeripheral> blePeripherals) {
        mBlePeripherals.setValue(blePeripherals);
    }

    @Override
    public void onScanPeripheralsFailed(int errorCode) {
        mScanningErrorCode.setValue(errorCode);
    }

    @Override
    public void onScanStatusChanged(boolean isScanning) {
        mIsScanning.setValue(false);
    }

    // endregion

    // region Utils
    private @NonNull
    String getResultNameForOrdering(BlePeripheral result) {
        String name = result.getName();
        if (name == null) {
            String identifier = result.getIdentifier();
            name = "~" + (identifier != null ? identifier : "");     // Prefix with symbol so all the unknowns are pushed to the bottom
        }
        return name;
    }

    // endregion


    // region Data Classes
    private class FilterData {
        // Constants
        final static int kMaxRssiValue = -100;

        private final static String kPreferences = "PeripheralList_prefs";
        private final static String kPreferences_filtersName = "filtersName";
        private final static String kPreferences_filtersIsNameExact = "filtersIsNameExact";
        private final static String kPreferences_filtersIsNameCaseInsensitive = "filtersIsNameCaseInsensitive";
        private final static String kPreferences_filtersRssi = "filtersRssi";
        private final static String kPreferences_filtersUnnamedEnabled = "filtersUnnamedEnabled";
        private final static String kPreferences_filtersUartEnabled = "filtersUartEnabled";

        // Data
        String name;
        int rssi = kMaxRssiValue;
        boolean isOnlyUartEnabled = false;
        boolean isUnnamedEnabled = true;
        boolean isNameMatchExact = false;
        boolean isNameMatchCaseInSensitive = true;

        //
        FilterData(boolean shouldRestoreSavedValues) {
            if (shouldRestoreSavedValues) {
                load();
            }
        }

        private void load() {
            Log.d(TAG, "FilterData load");

            SharedPreferences preferences = getApplication().getSharedPreferences(kPreferences, MODE_PRIVATE);
            name = preferences.getString(kPreferences_filtersName, null);
            isNameMatchExact = preferences.getBoolean(kPreferences_filtersIsNameExact, false);
            isNameMatchCaseInSensitive = preferences.getBoolean(kPreferences_filtersIsNameCaseInsensitive, true);
            rssi = preferences.getInt(kPreferences_filtersRssi, kMaxRssiValue);
            isUnnamedEnabled = preferences.getBoolean(kPreferences_filtersUnnamedEnabled, true);
            isOnlyUartEnabled = preferences.getBoolean(kPreferences_filtersUartEnabled, false);
        }

        public void save() {
            Log.d(TAG, "FilterData save");
            SharedPreferences.Editor preferencesEditor = getApplication().getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
            preferencesEditor.putString(kPreferences_filtersName, name);
            preferencesEditor.putBoolean(kPreferences_filtersIsNameExact, isNameMatchExact);
            preferencesEditor.putBoolean(kPreferences_filtersIsNameCaseInsensitive, isNameMatchCaseInSensitive);
            preferencesEditor.putInt(kPreferences_filtersRssi, rssi);
            preferencesEditor.putBoolean(kPreferences_filtersUnnamedEnabled, isUnnamedEnabled);
            preferencesEditor.putBoolean(kPreferences_filtersUartEnabled, isOnlyUartEnabled);
            preferencesEditor.apply();
        }

        boolean isAnyFilterEnabled() {
            return (name != null && !name.isEmpty()) || rssi > kMaxRssiValue || isOnlyUartEnabled || !isUnnamedEnabled;
        }

        String getDescription() {
            String filtersTitle = null;

            if (name != null && !name.isEmpty()) {
                filtersTitle = name;
            }

            if (rssi > FilterData.kMaxRssiValue) {
                String rssiString = String.format(Locale.ENGLISH, getApplication().getString(R.string.scanner_filter_rssi_description_format), rssi);
                if (filtersTitle != null) {
                    filtersTitle = filtersTitle + ", " + rssiString;
                } else {
                    filtersTitle = rssiString;
                }
            }

            if (!isUnnamedEnabled) {
                String namedString = getApplication().getString(R.string.scanner_filter_unnamed_description);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + namedString;
                } else {
                    filtersTitle = namedString;
                }
            }

            if (isOnlyUartEnabled) {
                String uartString = getApplication().getString(R.string.scanner_filter_uart_description);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + uartString;
                } else {
                    filtersTitle = uartString;
                }
            }

            return filtersTitle;
        }
    }

    private class ScanData {
        // Data
        FilterData filterData;
        List<BlePeripheral> blePeripherals;

        ScanData() {
            blePeripherals = new ArrayList<>();
        }

        void saveFilters() {
            if (filterData != null) {
                filterData.save();
            }
        }
    }
    // endregion

    // region Broadcast Listener
    private void registerGattReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlePeripheral.kBlePeripheral_OnConnecting);
        filter.addAction(BlePeripheral.kBlePeripheral_OnConnected);
        filter.addAction(BlePeripheral.kBlePeripheral_OnDisconnected);
        LocalBroadcastManager.getInstance(getApplication()).registerReceiver(mGattUpdateReceiver, filter);
    }

    private void unregisterGattReceiver() {
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(mGattUpdateReceiver);
    }

    private final List<String> mPeripheralsConnectingOrDiscoveringServices = new ArrayList<>();            // Contains identifiers of peripherals that are connecting (connect + discovery)

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String identifier = intent.getStringExtra(BlePeripheral.kExtra_deviceAddress);

            if (identifier != null) {
                final BlePeripheral blePeripheral = getPeripheralWithIdentifier(identifier);

                if (blePeripheral != null) {
                    if (BlePeripheral.kBlePeripheral_OnConnected.equals(action)) {
                        // If connected, start service discovery
                        blePeripheral.discoverServices(status -> {
                            final Handler mainHandler = new Handler(Looper.getMainLooper());
                            final Runnable discoveredServicesRunnable = () -> {
                                mPeripheralsConnectingOrDiscoveringServices.remove(identifier);          // Connection setup finished
                                Log.d(TAG, "kBlePeripheral_OnConnected ConnectingOrDiscovering: " + Arrays.toString(mPeripheralsConnectingOrDiscoveringServices.toArray()));
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    // Discovery finished
                                    mBlePeripheralDiscoveredServices.setValue(blePeripheral);

                                } else {
                                    final String message = LocalizationManager.getInstance().getString(getApplication(), "peripheraldetails_errordiscoveringservices");
                                    blePeripheral.disconnect();
                                    mBlePeripheralsConnectionErrorMessage.setValue(message);
                                }
                            };
                            mainHandler.post(discoveredServicesRunnable);
                        });
                    } else if (BlePeripheral.kBlePeripheral_OnDisconnected.equals(action)) {
                        Log.d(TAG, "kBlePeripheral_OnDisconnected ConnectingOrDiscovering: " + Arrays.toString(mPeripheralsConnectingOrDiscoveringServices.toArray()));
                        if (mPeripheralsConnectingOrDiscoveringServices.contains(identifier)) {          // If connection setup was still ongoing
                            final boolean isExpected = intent.getStringExtra(BlePeripheral.kExtra_expectedDisconnect) != null;      // If parameter kExtra_expectedDisconnect is non-null, the disconnect was expected (and no message errors are displayed to the user)
                            Log.d(TAG, "Expected disconnect: " + isExpected);
                            if (!isExpected) {
                                final String message = LocalizationManager.getInstance().getString(getApplication(), "bluetooth_connecting_error");
                                mBlePeripheralsConnectionErrorMessage.setValue(message);
                            }
                            mPeripheralsConnectingOrDiscoveringServices.remove(identifier);
                        }
                    } else if (BlePeripheral.kBlePeripheral_OnConnecting.equals(action)) {
                        if (!mPeripheralsConnectingOrDiscoveringServices.contains(identifier)) {         // peripheral starts connection setup
                            mPeripheralsConnectingOrDiscoveringServices.add(identifier);
                        }
                        Log.d(TAG, "kBlePeripheral_OnConnecting ConnectingOrDiscovering: " + Arrays.toString(mPeripheralsConnectingOrDiscoveringServices.toArray()));
                    }

                    mBlePeripheralsConnectionChanged.setValue(blePeripheral);
                    final int numDevicesConnected = mScanner.getConnectedPeripherals().size();
                    mNumDevicesConnected.setValue(numDevicesConnected);

                } else {
                    Log.w(TAG, "ScannerViewModel mGattUpdateReceiver with null peripheral");
                }
            } else {
                Log.w(TAG, "ScannerViewModel mGattUpdateReceiver with null identifier");
            }
        }
    };
    // endregion
}