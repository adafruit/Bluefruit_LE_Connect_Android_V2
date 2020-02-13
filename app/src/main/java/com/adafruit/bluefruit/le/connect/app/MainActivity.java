package com.adafruit.bluefruit.le.connect.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.adafruit.bluefruit.le.connect.BluefruitApplication;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BleManager;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.dfu.DfuProgressFragmentDialog;
import com.adafruit.bluefruit.le.connect.dfu.DfuService;
import com.adafruit.bluefruit.le.connect.dfu.DfuUpdater;
import com.adafruit.bluefruit.le.connect.dfu.ReleasesParser;
import com.adafruit.bluefruit.le.connect.models.DfuViewModel;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;

public class MainActivity extends AppCompatActivity implements ScannerFragment.ScannerFragmentListener, PeripheralModulesFragment.PeripheralModulesFragmentListener, DfuProgressFragmentDialog.Listener {

    // Constants
    private final static String TAG = MainActivity.class.getSimpleName();

    // Config
    private final static boolean kAvoidPoppingFragmentsWhileOnDfu = false;

    // Permission requests
    private final static int PERMISSION_REQUEST_FINE_LOCATION = 1;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    public static final int kActivityRequestCode_PlayServicesAvailability = 2;

    // Models
    private DfuViewModel mDfuViewModel;

    // Data
    private MainFragment mMainFragment;
    private AlertDialog mRequestLocationDialog;
    private boolean hasUserAlreadyBeenAskedAboutBluetoothStatus = false;

    // region Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (savedInstanceState == null) {
            // Set mainmenu fragment
            mMainFragment = MainFragment.newInstance();
            fragmentManager.beginTransaction()
                    .add(R.id.contentLayout, mMainFragment, "Main")
                    .commit();

        } else {
            hasUserAlreadyBeenAskedAboutBluetoothStatus = savedInstanceState.getBoolean("hasUserAlreadyBeenAskedAboutBluetoothStatus");
            mMainFragment = (MainFragment) fragmentManager.findFragmentByTag("Main");
        }

        // Back navigation listener
        fragmentManager.addOnBackStackChangedListener(() -> {
            if (fragmentManager.getBackStackEntryCount() == 0) {        // Check if coming back
                mMainFragment.disconnectAllPeripherals();
            }
        });

        // ViewModels
        mDfuViewModel = new ViewModelProvider(this).get(DfuViewModel.class);

        // Check if there is any update to the firmware database
        if (savedInstanceState == null) {
            updateAndroidSecurityProvider(this);        // Call this before refreshSoftwareUpdatesDatabase because SSL connections will fail on Android 4.4 if this is not executed:  https://stackoverflow.com/questions/29916962/javax-net-ssl-sslhandshakeexception-javax-net-ssl-sslprotocolexception-ssl-han
            DfuUpdater.refreshSoftwareUpdatesDatabase(this, success -> Log.d(TAG, "refreshSoftwareUpdatesDatabase completed. Success: " + success));
        }
    }

    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("hasUserAlreadyBeenAskedAboutBluetoothStatus", hasUserAlreadyBeenAskedAboutBluetoothStatus);
    }

    private void updateAndroidSecurityProvider(Activity callingActivity) {
        try {
            ProviderInstaller.installIfNeeded(this);
        } catch (GooglePlayServicesRepairableException e) {
            // Thrown when Google Play Services is not installed, up-to-date, or enabled
            // Show dialog to allow users to install, update, or otherwise enable Google Play services.
            GooglePlayServicesUtil.getErrorDialog(e.getConnectionStatusCode(), callingActivity, 0);
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e("SecurityException", "Google Play Services not available.");
        }
    }

    // endregion

    @Override
    protected void onResume() {
        super.onResume();

        BluefruitApplication.activityResumed();
        checkPermissions();

        // Observe disconnections
        registerGattReceiver();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        popFragmentsIfNoPeripheralsConnected();         // check if peripherals were disconnected while the app was in background
    }

    @Override
    protected void onPause() {
        super.onPause();
        BluefruitApplication.activityPaused();
        unregisterGattReceiver();

        // Remove location dialog if present
        if (mRequestLocationDialog != null) {
            mRequestLocationDialog.cancel();
            mRequestLocationDialog = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_about:
                FragmentManager fragmentManager = getSupportFragmentManager();
                AboutFragment fragment = AboutFragment.newInstance();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                        .replace(R.id.contentLayout, fragment, "About");
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkPermissions() {

        final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();
        if (!areLocationServicesReadyForScanning) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            mRequestLocationDialog = builder.setMessage(R.string.bluetooth_locationpermission_disabled_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            //DialogUtils.keepDialogOnOrientationChanges(mRequestLocationDialog);
        } else {
            if (mRequestLocationDialog != null) {
                mRequestLocationDialog.cancel();
                mRequestLocationDialog = null;
            }

            // Bluetooth state
            if (!hasUserAlreadyBeenAskedAboutBluetoothStatus) {     // Don't repeat the check if the user was already informed to avoid showing the "Enable Bluetooth" system prompt several times
                final boolean isBluetoothEnabled = manageBluetoothAvailability();

                if (isBluetoothEnabled) {
                    // Request Bluetooth scanning permissions
                    final boolean isLocationPermissionGranted = requestFineLocationPermissionIfNeeded();

                    if (isLocationPermissionGranted) {
                        // All good. Start Scanning
                        BleManager.getInstance().start(MainActivity.this);
                        // Bluetooth was enabled, resume scanning
                        mMainFragment.startScanning();
                    }
                }
            }
        }
    }

    // region Permissions
    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }

        return areLocationServiceReady;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean requestFineLocationPermissionIfNeeded() {       // Starting with Android 10, Bluetooth scanning needs Location FINE Permission
        boolean permissionGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android Marshmallow Permission check 
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                mRequestLocationDialog = builder.setTitle(R.string.bluetooth_locationpermission_title)
                        .setMessage(R.string.bluetooth_locationpermission_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION))
                        .show();
            }
        }
        return permissionGranted;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");

                    checkPermissions();

                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
    // endregion

    // region Bluetooth Setup
    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getApplicationContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.bluetooth_unsupported;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.bluetooth_poweredoff;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }

        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                checkPermissions();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (!isFinishing()) {
                    hasUserAlreadyBeenAskedAboutBluetoothStatus = true;     // Remember that
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    AlertDialog dialog = builder.setMessage(R.string.bluetooth_poweredoff)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                }
            }
        }
    }

    private void popFragmentsIfNoPeripheralsConnected() {
        final int numConnectedPeripherals = BleManager.getInstance().getConnectedDevices().size();
        final boolean isLastConnectedPeripheral = numConnectedPeripherals == 0;

        if (isLastConnectedPeripheral && (!kAvoidPoppingFragmentsWhileOnDfu || !isIsDfuInProgress())) {
            Log.d(TAG, "No peripherals connected. Pop all fragments");
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            fragmentManager.executePendingTransactions();
        }
    }
    // endregion

    // region ScannerFragmentListener
    public void bluetoothAdapterIsDisabled() {
        checkPermissions();
    }

    public void scannerRequestLocationPermissionIfNeeded() {
        requestFineLocationPermissionIfNeeded();
    }

    public void startPeripheralModules(String peripheralIdentifier) {
        PeripheralModulesFragment fragment = PeripheralModulesFragment.newInstance(peripheralIdentifier);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(R.id.contentLayout, fragment, "Modules");
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    // endregion

    // region PeripheralModulesFragmentListener
    @Override
    public void startModuleFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(R.id.contentLayout, fragment, "Module");
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    // endregion

    // region Broadcast Listener
    private void registerGattReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlePeripheral.kBlePeripheral_OnDisconnected);
        LocalBroadcastManager.getInstance(this).registerReceiver(mGattUpdateReceiver, filter);
    }

    private void unregisterGattReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGattUpdateReceiver);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BlePeripheral.kBlePeripheral_OnDisconnected.equals(action)) {
                popFragmentsIfNoPeripheralsConnected();
            }
        }
    };
    // endregion


    // region DFU
    private DfuProgressFragmentDialog mDfuProgressDialog;

    private void dismissDfuProgressDialog() {
        if (mDfuProgressDialog != null) {
            mDfuProgressDialog.dismiss();
            mDfuProgressDialog = null;
        }
    }

    private boolean mIsDfuInProgress = false;

    public boolean isIsDfuInProgress() {
        return mIsDfuInProgress;
    }

    public void startUpdate(@NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.BasicVersionInfo versionInfo) {
        dismissDfuProgressDialog();

        String message = getString(versionInfo.fileType == DfuService.TYPE_APPLICATION ? R.string.dfu_download_firmware_message : R.string.dfu_download_bootloader_message);
        mDfuProgressDialog = DfuProgressFragmentDialog.newInstance(blePeripheral.getDevice().getAddress(), message);
        FragmentManager fragmentManager = getSupportFragmentManager();
        mDfuProgressDialog.show(fragmentManager, null);
        fragmentManager.executePendingTransactions();

        mDfuProgressDialog.setIndeterminate(true);
        mDfuProgressDialog.setOnCancelListener(dialog -> {
            mDfuViewModel.cancelInstall();
            dfuFinished();
        });

        mIsDfuInProgress = true;
        mDfuViewModel.downloadAndInstall(this, blePeripheral, versionInfo, new DfuUpdater.DownloadStateListener() {
            @Override
            public void onDownloadStarted(int type) {
                mDfuProgressDialog.setIndeterminate(true);
                mDfuProgressDialog.setMessage(type == DfuUpdater.kDownloadOperation_Software_Hex ? R.string.dfu_download_hex_message : R.string.dfu_download_init_message);
            }

            @Override
            public void onDownloadProgress(int percent) {
                if (mDfuProgressDialog != null) {       // Check null (Google crash logs)
                    mDfuProgressDialog.setIndeterminate(false);
                    mDfuProgressDialog.setProgress(percent);
                }
            }

            @Override
            public void onDownloadFailed() {
                dismissDfuProgressDialog();

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.dialog_error).setMessage(R.string.dfu_download_error_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    // endregion

    private void dfuFinished() {

        if (kAvoidPoppingFragmentsWhileOnDfu) {
            popFragmentsIfNoPeripheralsConnected();
        } else {
            mMainFragment.startScanning();
        }
    }

    // region DfuProgressFragmentDialog.Listener

    @Override
    public void onDeviceDisconnected(String deviceAddress) {
        mIsDfuInProgress = false;
        //dismissDfuProgressDialog();
        dfuFinished();
    }

    @Override
    public void onDfuCompleted(String deviceAddress) {
        dismissDfuProgressDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_completed).setMessage(R.string.dfu_updatecompleted_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDfuAborted(String deviceAddress) {
        dismissDfuProgressDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_error).setMessage(R.string.dfu_updateaborted_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onError(String deviceAddress, int error, int errorType, String message) {
        dismissDfuProgressDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dfu_status_error).setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // endregion
}