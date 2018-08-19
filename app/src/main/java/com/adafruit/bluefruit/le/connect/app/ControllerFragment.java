package com.adafruit.bluefruit.le.connect.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.utils.AdapterUtils;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class ControllerFragment extends ConnectedPeripheralFragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, SensorEventListener, ControllerColorPickerFragment.ControllerColorPickerFragmentListener, ControllerPadFragment.ControllerPadFragmentListener, UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = ControllerFragment.class.getSimpleName();

    // Config
    private final static boolean kKeepUpdatingParentValuesInChildActivities = true;
    private final static int kSendDataInterval = 500;   // milliseconds

    // Constants - Preferences
    private final static String kPreferences = "ControllerActivity_prefs";
    private final static String kPreferences_uartToolTip = "uarttooltip";

    // Constants - Sensor Types
    private static final int kSensorType_Quaternion = 0;
    private static final int kSensorType_Accelerometer = 1;
    private static final int kSensorType_Gyroscope = 2;
    private static final int kSensorType_Magnetometer = 3;
    private static final int kSensorType_Location = 4;
    private static final int kNumSensorTypes = 5;

    private static final int kModule_ControlPad = 0;
    private static final int kModule_ColorPicker = 1;
    private static final int kNumModules = 2;

    // UI
    private ControllerAdapter mControllerAdapter;
    private AlertDialog mRequestLocationDialog;

    // Data
    private GoogleApiClient mGoogleApiClient;
    private Handler sendDataHandler = new Handler();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient mFusedLocationClient;

    private UartDataManager mUartDataManager;
    private BlePeripheralUart mBlePeripheralUart;

    private SensorManager mSensorManager;
    private SensorData[] mSensorData;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mMagnetometer;

    private float[] mRotation = new float[9];
    private float[] mOrientation = new float[3];
    private float[] mQuaternion = new float[4];

    private boolean isSensorPollingEnabled = false;

    private WeakReference<ControllerPadFragment> mWeakControllerPadFragment = null;


    // region LocationCallback
    // Create a WeakReference to LocationCallback to avoid memory leaks in GoogleServices: https://github.com/googlesamples/android-play-location/issues/26
    private static class LocationCallbackReference extends LocationCallback {

        private WeakReference<LocationCallback> weakLocationCallback;

        LocationCallbackReference(LocationCallback locationCallback) {
            weakLocationCallback = new WeakReference<>(locationCallback);
        }

        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (weakLocationCallback.get() != null) {
                weakLocationCallback.get().onLocationResult(locationResult);
            }
        }
    }

    private LocationCallback mInternalLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            setLastLocation(location);
        }
    };

    private LocationCallbackReference mLocationCallback = new LocationCallbackReference(mInternalLocationCallback);
    // endregion

    // region Fragment Lifecycle
    public static ControllerFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        ControllerFragment fragment = new ControllerFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public ControllerFragment() {
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
        return inflater.inflate(R.layout.fragment_controller, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.controller_tab_title);

        // SensorData
        if (mSensorData == null) {     // Only setup when is null
            mSensorData = new SensorData[kNumSensorTypes];
            for (int i = 0; i < kNumSensorTypes; i++) {
                SensorData sensorData = new SensorData();
                sensorData.sensorType = i;
                sensorData.enabled = false;
                mSensorData[i] = sensorData;
            }
        }

        // UI
        final Context context = getContext();
        if (context != null) {
            ViewGroup uartTooltipViewGroup = view.findViewById(R.id.uartTooltipViewGroup);
            SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            final boolean showUartTooltip = preferences.getBoolean(kPreferences_uartToolTip, true);
            uartTooltipViewGroup.setVisibility(showUartTooltip ? View.VISIBLE : View.GONE);

            ImageButton closeTipButton = view.findViewById(R.id.closeTipButton);
            closeTipButton.setOnClickListener(view1 -> {
                SharedPreferences settings = getContext().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(kPreferences_uartToolTip, false);
                editor.apply();

                uartTooltipViewGroup.setVisibility(View.GONE);
            });

            // Recycler view
            RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            recyclerView.addItemDecoration(itemDecoration);

            RecyclerView.LayoutManager peripheralsLayoutManager = new LinearLayoutManager(getContext());
            recyclerView.setLayoutManager(peripheralsLayoutManager);

            // Disable update animation
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

            // Adapter
            WeakReference<ControllerFragment> weakThis = new WeakReference<>(this);
            mControllerAdapter = new ControllerAdapter(context, mSensorData, new ControllerAdapter.Listener() {
                @Override
                public void onSensorEnabled(int sensorId, boolean enabled) {
                    final Context context = getContext();
                    ControllerFragment controllerFragment = weakThis.get();
                    if (context == null || controllerFragment == null) return;

                    // Special check for location data
                    if (sensorId == kSensorType_Location && enabled) {
                        // Detect if location is enabled or warn user
                        final boolean isLocationEnabled = isLocationEnabled(context);
                        if (!isLocationEnabled) {
                            final String locationDeniedString = context.getString(R.string.controller_sensor_location_denied);
                            new AlertDialog.Builder(context)
                                    .setMessage(locationDeniedString)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    }

                    // Enable sensor
                    controllerFragment.mSensorData[sensorId].enabled = enabled;
                    controllerFragment.registerEnabledSensorListeners(context, true);
                }

                @Override
                public void onModuleSelected(int moduleId) {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        FragmentManager fragmentManager = activity.getSupportFragmentManager();
                        if (fragmentManager != null) {

                            Fragment fragment = null;
                            String fragmentTag = null;
                            switch (moduleId) {
                                case kModule_ControlPad:
                                    ControllerPadFragment controllerPadFragment = ControllerPadFragment.newInstance();
                                    fragment = controllerPadFragment;
                                    fragmentTag = "Control Pad";

                                    // Enable cache for control pad
                                    mWeakControllerPadFragment = new WeakReference<>(controllerPadFragment);
                                    mUartDataManager.clearRxCache(mBlePeripheral.getIdentifier());
                                    mUartDataManager.setListener(ControllerFragment.this);
                                    break;
                                case kModule_ColorPicker:
                                    fragment = ControllerColorPickerFragment.newInstance();
                                    fragmentTag = "Color Picker";
                                    break;
                            }

                            if (fragment != null) {
                                fragment.setTargetFragment(ControllerFragment.this, 0);
                                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                        .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                                        .replace(R.id.contentLayout, fragment, fragmentTag);
                                fragmentTransaction.addToBackStack(null);
                                fragmentTransaction.commitAllowingStateLoss();      // Allowing state loss to avoid detected crashes
                            }
                        }
                    }
                }
            });
            recyclerView.setAdapter(mControllerAdapter);

            // Sensor Manager
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

            mSensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);          // Use applicationContext to avoid memory leaks
            if (mSensorManager != null) {
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            } else {
                Log.w(TAG, "Sensor Service not available");
            }


            if (mUartDataManager == null) { // Only the first time
                // Google Play Services (used for location updates)
                buildGoogleApiClient(context);

                // Setup
                mUartDataManager = new UartDataManager(context, null, false);            // The listener will be set only for ControlPad
                setupUart();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.d(TAG, "onStart");
        mGoogleApiClient.connect();

        // Disable cache if coming back from Control Pad
        mUartDataManager.setListener(null);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");

        if (!kKeepUpdatingParentValuesInChildActivities) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();

        final Context context = getContext();

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        if (resultCode == ConnectionResult.SERVICE_MISSING ||
                resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ||
                resultCode == ConnectionResult.SERVICE_DISABLED) {

            Dialog googlePlayErrorDialog = apiAvailability.getErrorDialog(getActivity(), resultCode, MainActivity.kActivityRequestCode_PlayServicesAvailability);
            if (googlePlayErrorDialog != null) {
                googlePlayErrorDialog.show();
            }
        }

        // Setup listeners
        if (context != null) {
            registerEnabledSensorListeners(context, true);
        }

        // Setup send data task
        if (!isSensorPollingEnabled) {
            sendDataHandler.postDelayed(mPeriodicallySendData, kSendDataInterval);
            isSensorPollingEnabled = true;
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();

        final Context context = getContext();

        if (!kKeepUpdatingParentValuesInChildActivities) {
            if (context != null) {
                registerEnabledSensorListeners(context, false);
            }

            // Remove send data task
            sendDataHandler.removeCallbacksAndMessages(null);
            isSensorPollingEnabled = false;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        final Context context = getContext();

        if (kKeepUpdatingParentValuesInChildActivities) {
            // Remove all sensor polling
            if (context != null) {
                registerEnabledSensorListeners(context, false);
            }
            sendDataHandler.removeCallbacksAndMessages(null);
            isSensorPollingEnabled = false;
            disconnectGoogleApiClient();
        }
        mSensorManager = null;

        if (mUartDataManager != null) {
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        if (mBlePeripheralUart != null) {
            mBlePeripheralUart.uartDisable();
        }
        mBlePeripheralUart = null;

        // Remove location dialog if present
        if (mRequestLocationDialog != null) {
            mRequestLocationDialog.cancel();
            mRequestLocationDialog = null;
        }

        // Force release mLocationCallback to avoid memory leaks
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            mFusedLocationClient = null;
        }

        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MainActivity.kActivityRequestCode_PlayServicesAvailability) {
            Log.w(TAG, "kActivityRequestCode_PlayServicesAvailability result: " + resultCode);
        }
    }

    // endregion

    // region Uart
    private void setupUart() {
        if (mBlePeripheral == null) {
            Log.e(TAG, "setupUart with blePeripheral null");
            return;
        }

        mBlePeripheralUart = new BlePeripheralUart(mBlePeripheral);
        mBlePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Done
                Log.d(TAG, "Uart enabled");

            } else {
                Context context = getContext();
                if (context != null) {
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
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.controller_help_title), getString(R.string.controller_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
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

    // region Google Services
    private synchronized void buildGoogleApiClient(@NonNull Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context.getApplicationContext())         // Use getApplicationContext to prevent memory leak: https://stackoverflow.com/questions/35308231/memory-leak-with-googleapiclient-detected-by-android-studio
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private synchronized void disconnectGoogleApiClient() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient.unregisterConnectionCallbacks(this);
            mGoogleApiClient.unregisterConnectionFailedListener(this);
            mGoogleApiClient = null;
        }
    }

    // region GoogleApiClient.ConnectionCallbacks
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Google Play Services connected");

        checkPermissions();
    }

    private void checkPermissions() {
        final boolean isLocationPermissionGranted = requestFineLocationPermissionIfNeeded();

        if (isLocationPermissionGranted) {
            try {
                mFusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        // Logic to handle location object
                        mMainHandler.post(() -> setLastLocation(location));
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception requesting location updates: " + e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case MainActivity.PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Fine Location permission granted");

                    checkPermissions();

                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.bluetooth_locationpermission_notavailable_title);
                    builder.setMessage(R.string.bluetooth_locationpermission_notavailable_text);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(dialog -> {
                    });
                    builder.show();
                }
                break;
            }
            default:
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean requestFineLocationPermissionIfNeeded() {
        final Context context = getContext();
        if (context == null) return false;

        boolean permissionGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android Marshmallow Permission check 
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
                if (mRequestLocationDialog != null) {
                    mRequestLocationDialog.cancel();
                    mRequestLocationDialog = null;
                }

                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                mRequestLocationDialog = builder.setTitle(R.string.bluetooth_locationpermission_title)
                        .setMessage(R.string.controller_sensor_locationpermission_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.PERMISSION_REQUEST_FINE_LOCATION))
                        .show();
            }
        }
        return permissionGranted;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Google Play Services suspended");
    }
    // endregion

    // region GoogleApiClient.OnConnectionFailedListener
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "Google Play Services connection failed");
    }
    // endregion
    // endregion


    // region Sensors

    private Runnable mPeriodicallySendData = new Runnable() {
        @Override
        public void run() {
            final String[] prefixes = {"!Q", "!A", "!G", "!M", "!L"};     // same order that kSensorType

            for (int i = 0; i < mSensorData.length; i++) {
                SensorData sensorData = mSensorData[i];

                if (sensorData.enabled && sensorData.values != null) {
                    ByteBuffer buffer = ByteBuffer.allocate(2 + sensorData.values.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);

                    // prefix
                    String prefix = prefixes[sensorData.sensorType];
                    buffer.put(prefix.getBytes());

                    // values
                    for (int j = 0; j < sensorData.values.length; j++) {
                        buffer.putFloat(sensorData.values[j]);
                    }

                    byte[] result = buffer.array();
                    Log.d(TAG, "Send data for sensor: " + i);
                    sendCrcData(result);
                }
            }

            sendDataHandler.postDelayed(this, kSendDataInterval);
        }
    };

    private void sendCrcData(byte[] data) {
        if (mUartDataManager == null) {     // Check because crash found on logs (mUartDataManager is null)
            return;
        }

        byte[] crcData = BlePeripheralUart.appendCrc(data);
        mUartDataManager.send(mBlePeripheralUart, crcData, null);
    }

    private boolean isLocationEnabled(@NonNull Context context) {
        int locationMode;
        try {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

        } catch (Settings.SettingNotFoundException e) {
            locationMode = Settings.Secure.LOCATION_MODE_OFF;
        }

        return locationMode != Settings.Secure.LOCATION_MODE_OFF;

    }

    private void registerEnabledSensorListeners(@NonNull Context context, boolean register) {

        if (mSensorManager != null) {       // Check not null (crash detected when app is resumed and device has been disconnected and onDestroy tries to remove sensor manager listeners)
            // Accelerometer
            if (register && (mSensorData[kSensorType_Accelerometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                mSensorManager.unregisterListener(this, mAccelerometer);
            }

            // Gyroscope
            if (register && mSensorData[kSensorType_Gyroscope].enabled) {
                mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                mSensorManager.unregisterListener(this, mGyroscope);
            }

            // Magnetometer
            if (register && (mSensorData[kSensorType_Magnetometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
                if (mMagnetometer == null) {
                    new AlertDialog.Builder(context)
                            .setMessage(getString(R.string.controller_magnetometermissing))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    mSensorData[kSensorType_Magnetometer].enabled = false;
                    mSensorData[kSensorType_Quaternion].enabled = false;
                    mControllerAdapter.notifySensorChanged(kSensorType_Magnetometer);
                    mControllerAdapter.notifySensorChanged(kSensorType_Quaternion);
                } else {
                    mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
                }
            } else {
                mSensorManager.unregisterListener(this, mMagnetometer);
            }
        }

        // Location
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            if (register && mSensorData[kSensorType_Location].enabled) {
                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setInterval(2000);
                locationRequest.setFastestInterval(500);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                // Location updates should have already been granted to scan for bluetooth peripherals, so we don't ask for them again
                try {
                    mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, null);
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception requesting location updates: " + e);
                }
                mControllerAdapter.notifySensorChanged(kSensorType_Location);       // Show current values
            } else {
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            }
        }
    }

    private void setLastLocation(@Nullable Location location) {
        if (location != null) {
            SensorData sensorData = mSensorData[kSensorType_Location];

            float[] values = new float[3];
            values[0] = (float) location.getLatitude();
            values[1] = (float) location.getLongitude();
            values[2] = (float) location.getAltitude();
            sensorData.values = values;
        }

        mControllerAdapter.notifySensorChanged(kSensorType_Location);
    }

    @SuppressWarnings("ConstantConditions")
    private void updateOrientation() {
        float[] lastAccelerometer = mSensorData[kSensorType_Accelerometer].values;
        float[] lastMagnetometer = mSensorData[kSensorType_Magnetometer].values;
        if (lastAccelerometer != null && lastMagnetometer != null) {
            SensorManager.getRotationMatrix(mRotation, null, lastAccelerometer, lastMagnetometer);
            SensorManager.getOrientation(mRotation, mOrientation);

            final boolean kUse4Components = true;
            if (kUse4Components) {
                SensorManager.getQuaternionFromVector(mQuaternion, mOrientation);
                // Quaternions in Android are stored as [w, x, y, z], so we change it to [x, y, z, w]
                float w = mQuaternion[0];
                mQuaternion[0] = mQuaternion[1];
                mQuaternion[1] = mQuaternion[2];
                mQuaternion[2] = mQuaternion[3];
                mQuaternion[3] = w;

                mSensorData[kSensorType_Quaternion].values = mQuaternion;
            } else {
                mSensorData[kSensorType_Quaternion].values = mOrientation;
            }
        }
    }


    private class SensorData {
        public int sensorType;
        public float[] values;
        public boolean enabled;
    }


    // endregion

    // region SensorEventListener

    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            mSensorData[kSensorType_Accelerometer].values = event.values;
            updateOrientation();            // orientation depends on Accelerometer and Magnetometer
            mControllerAdapter.notifySensorChanged(kSensorType_Accelerometer);
            mControllerAdapter.notifySensorChanged(kSensorType_Quaternion);
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            mSensorData[kSensorType_Gyroscope].values = event.values;
            mControllerAdapter.notifySensorChanged(kSensorType_Gyroscope);
        } else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
            mSensorData[kSensorType_Magnetometer].values = event.values;
            updateOrientation();            // orientation depends on Accelerometer and Magnetometer
            mControllerAdapter.notifySensorChanged(kSensorType_Magnetometer);
            mControllerAdapter.notifySensorChanged(kSensorType_Quaternion);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    // endregion


    // region Adapter
    private static class ControllerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Config
        private static final int[] kSensorTitleKeys = {R.string.controller_sensor_quaternion, R.string.controller_sensor_accelerometer, R.string.controller_sensor_gyro, R.string.controller_sensor_magnetometer, R.string.controller_sensor_location};
        private static final int[] kModuleTitleKeys = {R.string.controller_module_pad, R.string.controller_module_colorpicker};

        // Constants
        private static final int kCellType_SectionTitle = 0;
        private static final int kCellType_SensorDataCell = 1;
        private static final int kCellType_ModuleCell = 2;

        private static final int kSensorDataCellsStartPosition = 1;
        private static final int kModuleCellsStartPosition = 1 + kNumSensorTypes + 1;

        // Interface
        interface Listener {
            void onSensorEnabled(int sensorId, boolean enabled);

            void onModuleSelected(int moduleId);
        }

        // Data Structures
        private class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;

            SectionViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
            }
        }

        void notifySensorChanged(int sensorId) {
            notifyItemChanged(kSensorDataCellsStartPosition + sensorId);
        }

        private class SensorDataViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            SwitchCompat enabledSwitch;
            private ViewGroup expandedViewGroup;
            TextView value0TextView;
            TextView value1TextView;
            TextView value2TextView;
            TextView value3TextView;
            TextView[] textViews;

            SensorDataViewHolder(View view) {
                super(view);
                nameTextView = view.findViewById(R.id.nameTextView);
                enabledSwitch = view.findViewById(R.id.enabledSwitch);
                expandedViewGroup = view.findViewById(R.id.expandedViewGroup);
                value0TextView = view.findViewById(R.id.value0TextView);
                value1TextView = view.findViewById(R.id.value1TextView);
                value2TextView = view.findViewById(R.id.value2TextView);
                value3TextView = view.findViewById(R.id.value3TextView);
                textViews = new TextView[]{value0TextView, value1TextView, value2TextView, value3TextView};
            }

            void animateExpanded() {
                final int index = getViewHolderId();
                if (mSensorData[index].enabled) {
                    AdapterUtils.expand(expandedViewGroup);
                } else {
                    AdapterUtils.collapse(expandedViewGroup);
                }
            }

            int getViewHolderId() {
                return getAdapterPosition() - kSensorDataCellsStartPosition;
            }

            boolean isExpanded() {
                final int position = getViewHolderId();
                return mSensorData != null && mSensorData.length >= position && mSensorData[position].enabled;
            }
        }

        private class ModuleViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            ViewGroup mainViewGroup;

            ModuleViewHolder(View view) {
                super(view);
                nameTextView = view.findViewById(R.id.nameTextView);
                mainViewGroup = view.findViewById(R.id.mainViewGroup);
            }
        }

        // Data
        private Context mContext;
        private SensorData[] mSensorData;
        private Listener mListener;

        ControllerAdapter(@NonNull Context context, @NonNull SensorData[] sensorData, @NonNull Listener listener) {
            mContext = context.getApplicationContext();
            mSensorData = sensorData;
            mListener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            super.getItemViewType(position);

            if (position == 0 || position == kModuleCellsStartPosition - 1) {
                return kCellType_SectionTitle;
            } else if (position > 0 && position < kModuleCellsStartPosition) {
                return kCellType_SensorDataCell;
            } else {
                return kCellType_ModuleCell;
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
                case kCellType_SensorDataCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_controller_sensordata_item, parent, false);
                    return new SensorDataViewHolder(view);
                }
                case kCellType_ModuleCell: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_textview_item, parent, false);
                    return new ModuleViewHolder(view);
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
                case kCellType_SensorDataCell:
                    final int sensorId = position - kSensorDataCellsStartPosition;
                    SensorData sensorData = mSensorData[sensorId];
                    Log.d(TAG, "sensorId: " + sensorId + " enabled: " + sensorData.enabled);
                    SensorDataViewHolder sensorDataViewHolder = (SensorDataViewHolder) holder;
                    sensorDataViewHolder.nameTextView.setText(kSensorTitleKeys[sensorId]);
                    sensorDataViewHolder.enabledSwitch.setChecked(sensorData.enabled);
                    sensorDataViewHolder.enabledSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                        if (compoundButton.isPressed()) {
                            if (mListener != null) {
                                mListener.onSensorEnabled(sensorId, isChecked);
                            }
                            sensorDataViewHolder.animateExpanded();
                        }
                    });

                    final boolean isExpanded = sensorDataViewHolder.isExpanded();
                    sensorDataViewHolder.expandedViewGroup.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

                    if (isExpanded) {

                        if (sensorData.sensorType == kSensorType_Location && sensorData.values == null) {
                            sensorDataViewHolder.value0TextView.setText(mContext.getString(R.string.controller_location_unknown));
                            sensorDataViewHolder.value0TextView.setVisibility(View.VISIBLE);
                            sensorDataViewHolder.value1TextView.setVisibility(View.GONE);
                            sensorDataViewHolder.value2TextView.setVisibility(View.GONE);
                            sensorDataViewHolder.value3TextView.setVisibility(View.GONE);
                        } else {
                            for (int i = 0; i < sensorDataViewHolder.textViews.length; i++) {
                                TextView textView = sensorDataViewHolder.textViews[i];
                                String valueString = null;
                                final boolean isDefined = sensorData.values != null && i < sensorData.values.length;
                                if (isDefined) {
                                    if (sensorData.sensorType == kSensorType_Location) {
                                        final int[] prefixId = {R.string.controller_component_lat, R.string.controller_component_long, R.string.controller_component_alt};
                                        valueString = mContext.getString(prefixId[i]) + ":\t " + sensorData.values[i];
                                    } else {
                                        final int[] prefixId = {R.string.controller_component_x, R.string.controller_component_y, R.string.controller_component_z, R.string.controller_component_w};
                                        valueString = mContext.getString(prefixId[i]) + ":\t " + sensorData.values[i];
                                    }
                                }

                                textView.setVisibility(isDefined ? View.VISIBLE : View.GONE);
                                textView.setText(valueString);
                            }
                        }
                    }
                    break;

                case kCellType_ModuleCell:
                    ModuleViewHolder moduleViewHolder = (ModuleViewHolder) holder;
                    final int moduleId = position - kModuleCellsStartPosition;
                    moduleViewHolder.nameTextView.setText(kModuleTitleKeys[position - kModuleCellsStartPosition]);
                    moduleViewHolder.mainViewGroup.setOnClickListener(view -> {
                        if (mListener != null) {
                            mListener.onModuleSelected(moduleId);
                        }
                    });
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return 1 + kNumSensorTypes + 1 + kNumModules;
        }
    }

    // endregion


    // region ControllerColorPickerFragmentListener
    @SuppressWarnings({"PointlessBitwiseExpression", "PointlessArithmeticExpression"})
    @Override
    public void onSendColorComponents(int color) {
        // Send selected color !Crgb
        final byte r = (byte) ((color >> 16) & 0xFF);
        final byte g = (byte) ((color >> 8) & 0xFF);
        final byte b = (byte) ((color >> 0) & 0xFF);

        ByteBuffer buffer = ByteBuffer.allocate(2 + 3 * 1).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // prefix
        String prefix = "!C";
        buffer.put(prefix.getBytes());

        // values
        buffer.put(r);
        buffer.put(g);
        buffer.put(b);

        byte[] result = buffer.array();
        sendCrcData(result);
    }

    // endregion


    // region ControllerPadFragmentListener
    @Override
    public void onSendControllerPadButtonStatus(int tag, boolean isPressed) {
        String data = "!B" + tag + (isPressed ? "1" : "0");
        ByteBuffer buffer = ByteBuffer.allocate(data.length()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put(data.getBytes());
        sendCrcData(buffer.array());
    }
    // endregion

    // region UartDataManagerListener

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        ControllerPadFragment controllerPadFragment = mWeakControllerPadFragment.get();
        if (controllerPadFragment != null) {
            String dataString = BleUtils.bytesToText(data, true);
            controllerPadFragment.addText(dataString);
            mUartDataManager.removeRxCacheFirst(data.length, peripheralIdentifier);
        }
    }

    // endregion

}
