package com.adafruit.bluefruit.le.connect.dfu;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.utils.NetworkUtils;
import com.adafruit.bluefruit.le.connect.utils.ThreadUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;

public class DfuUpdater {
    // Log
    private final static String TAG = DfuUpdater.class.getSimpleName();

    // Config
    private static final int kForceNumberOfPacketsReceiptNotificationsValue = 4;        // Set to 0 to disable hack. Force a number of packets to avoid problems on some devices. For example, MotoG4 with Android 6
    public static final String kDefaultUpdateServerUrl = "https://raw.githubusercontent.com/adafruit/Adafruit_BluefruitLE_Firmware/master/releases.xml";

    private static final String kManufacturer = "Adafruit Industries";
    private static final String kDefaultBootloaderVersion = "0.0";

    private static final String kDefaultHexFilename = "firmware.hex";
    private static final String kDefaultIniFilename = "firmware.ini";

    // Constants
    private static final UUID kNordicDeviceFirmwareUpdateServiceUUID = UUID.fromString("00001530-1212-EFDE-1523-785FEABCD123");
    private static final UUID kDeviceInformationServiceUUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");
    private static final UUID kModelNumberCharacteristicUUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB");
    private static final UUID kManufacturerNameCharacteristicUUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB");
    private static final UUID kSoftwareRevisionCharacteristicUUID = UUID.fromString("00002A28-0000-1000-8000-00805F9B34FB");
    private static final UUID kFirmwareRevisionCharacteristicUUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB");

    private static final String kPreferencesKey_Releases = "updatemanager_releasesxml";
    private static final String kPreferencesKey_UpdateServer = "updatemanager_updateserver";
    private static final String kPreferencesKey_IgnoredVersion = "updatemanager_ignoredversion";

    private static final int kDownloadOperation_VersionsDatabase = 0;
    public static final int kDownloadOperation_Software_Hex = 1;
    public static final int kDownloadOperation_Software_Ini = 2;

    // Data Structures
    public static class DeviceDfuInfo {
        public String manufacturer;
        public String modelNumber;
        public String firmwareRevision;
        public String softwareRevision;

        @SuppressWarnings("UnnecessaryLocalVariable")
        String getBootloaderVersion() {
            String result = kDefaultBootloaderVersion;
            if (firmwareRevision != null) {
                int index = firmwareRevision.indexOf(", ");
                if (index >= 0) {
                    String bootloaderVersion = firmwareRevision.substring(index + 2);
                    result = bootloaderVersion;
                }
            }
            return result;
        }

        public boolean hasDefaultBootloaderVersion() {
            return getBootloaderVersion().equals(kDefaultBootloaderVersion);
        }
    }

    // region Refresh Database
    public interface UpdateDatabaseCompletionHandler {
        void completion(boolean success);
    }

    private static DownloadTask sDownloadTask;

    public static void refreshSoftwareUpdatesDatabase(@NonNull Context context, @Nullable UpdateDatabaseCompletionHandler completionHandler) {
        // Cancel previous downloads
        if (sDownloadTask != null) {
            sDownloadTask.cancel(true);
            sDownloadTask.setListener(null);
        }

        if (NetworkUtils.isNetworkAvailable(context)) {
            // Get server url from preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String updateServer = sharedPreferences.getString(kPreferencesKey_UpdateServer, kDefaultUpdateServerUrl);
            Log.d(TAG, "Get latest software version data from: " + updateServer);
            Uri updateServerUri = Uri.parse(updateServer);

            // Download from server
            sDownloadTask = new DownloadTask(context, kDownloadOperation_VersionsDatabase, new DownloadTask.Listener() {
                @Override
                public void onDownloadProgress(int operationId, int progress) {
                }

                @Override
                public void onDownloadCompleted(int operationId, @NonNull Uri uri, @Nullable ByteArrayOutputStream result) {
                    String contentString = null;
                    if (result != null) {
                        try {
                            contentString = result.toString("UTF-8");
                        } catch (UnsupportedEncodingException ignored) {
                        }
                    }

                    boolean success = false;
                    if (contentString != null) {
                        // Save in settings
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
                        sharedPreferencesEdit.putString(kPreferencesKey_Releases, contentString);
                        sharedPreferencesEdit.apply();
                        success = true;
                    } else {
                        Log.w(TAG, "Error processing releases.xml");
                    }
                    if (completionHandler != null) {
                        completionHandler.completion(success);
                    }
                }
            });
            sDownloadTask.execute(updateServerUri);
        } else {
            Log.d(TAG, "Can't update latest software info from server. Connection not available");

            if (completionHandler != null) {
                completionHandler.completion(false);
            }
        }
    }

    public static @Nullable
    Map<String, ReleasesParser.BoardInfo> getReleases(@NonNull Context context, boolean showBetaVersions) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String releasesXml = sharedPreferences.getString(kPreferencesKey_Releases, null);

        return ReleasesParser.parseReleasesXml(releasesXml, showBetaVersions);
    }

    // endregion

    // region Check Updates
    public interface CheckUpdatesCompletionHandler {
        void completion(boolean isUpdateAvailable, @Nullable ReleasesParser.FirmwareInfo latestRelease, @Nullable DeviceDfuInfo deviceDfuInfo);
    }

    public synchronized static void checkUpdatesForPeripheral(@NonNull Context context, @NonNull BlePeripheral blePeripheral, boolean shouldDiscoverServices, boolean shouldRecommendBetaReleases, @Nullable String versionToIgnore, @NonNull CheckUpdatesCompletionHandler completionHandler) {

        if (shouldDiscoverServices) {
            blePeripheral.discoverServices(status -> servicesDiscovered(context, blePeripheral, shouldRecommendBetaReleases, versionToIgnore, completionHandler));
        } else {
            servicesDiscovered(context, blePeripheral, shouldRecommendBetaReleases, versionToIgnore, completionHandler);
        }
    }


    public static String getIgnoredVersion(@NonNull Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(kPreferencesKey_IgnoredVersion, "");
    }

    public static void setIgnoredVersion(@NonNull Context context, @Nullable String version) {
        // Remembers that the user doesn't want to be notified about the this version anymore
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString(kPreferencesKey_IgnoredVersion, version);
        sharedPreferencesEdit.apply();
    }

    @MainThread
    private static void servicesDiscovered(@NonNull Context context, @NonNull BlePeripheral blePeripheral, boolean shouldRecommendBetaReleases, @Nullable String versionToIgnore, @NonNull CheckUpdatesCompletionHandler completionHandler) {
        BluetoothGattService dfuService = blePeripheral.getService(kNordicDeviceFirmwareUpdateServiceUUID);
        BluetoothGattService disService = blePeripheral.getService(kDeviceInformationServiceUUID);
        if (dfuService == null || disService == null) {
            Log.d(TAG, "Updates: Peripheral has no DFU or DIS service available");
            completionHandler.completion(false, null, null);
            return;
        }

        Log.d(TAG, "Read DIS characteristics");
        DeviceDfuInfo deviceDfuInfo = new DeviceDfuInfo();
        ThreadUtils.DispatchGroup dispatchGroup = new ThreadUtils.DispatchGroup();

        // Manufacturer
        readCharacteristic(blePeripheral, disService, kManufacturerNameCharacteristicUUID, dispatchGroup, data -> deviceDfuInfo.manufacturer = data);

        // Model Number
        readCharacteristic(blePeripheral, disService, kModelNumberCharacteristicUUID, dispatchGroup, data -> deviceDfuInfo.modelNumber = data);

        // Firmware Revision
        readCharacteristic(blePeripheral, disService, kFirmwareRevisionCharacteristicUUID, dispatchGroup, data -> deviceDfuInfo.firmwareRevision = data);

        // Software Revision
        readCharacteristic(blePeripheral, disService, kSoftwareRevisionCharacteristicUUID, dispatchGroup, data -> deviceDfuInfo.softwareRevision = data);

        // All read
        dispatchGroup.notify(() -> {
            Log.d(TAG, "Device Info Data received");
            checkUpdatesForDeviceInfoService(context, deviceDfuInfo, shouldRecommendBetaReleases, versionToIgnore, completionHandler);
        });
    }

    public interface StringReadHandler {
        void completion(@Nullable String data);
    }

    private static void readCharacteristic(@NonNull BlePeripheral blePeripheral, @NonNull BluetoothGattService service, UUID characteristicUUID, @NonNull ThreadUtils.DispatchGroup dispatchGroup, @NonNull StringReadHandler stringReadHandler) {
        dispatchGroup.enter();
        blePeripheral.readCharacteristic(service, characteristicUUID, (status, data) -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String stringValue = BleUtils.bytesToText(data, false);
                stringReadHandler.completion(stringValue);
            }
            dispatchGroup.leave();
        });
    }

    private static void checkUpdatesForDeviceInfoService(@NonNull Context context, @NonNull DeviceDfuInfo deviceDfuInfo, boolean shouldRecommendBetaReleases, @Nullable String versionToIgnore, @NonNull CheckUpdatesCompletionHandler completionHandler) {
        boolean isFirmwareUpdateAvailable = false;
        ReleasesParser.FirmwareInfo latestRelease = null;

        Map<String, ReleasesParser.BoardInfo> allReleases = getReleases(context, shouldRecommendBetaReleases);

        boolean isManufacturerCorrect = deviceDfuInfo.manufacturer != null && deviceDfuInfo.manufacturer.equalsIgnoreCase(kManufacturer);
        if (isManufacturerCorrect && deviceDfuInfo.modelNumber != null && allReleases != null) {
            ReleasesParser.BoardInfo boardInfo = allReleases.get(deviceDfuInfo.modelNumber);
            if (boardInfo != null) {
                List<ReleasesParser.FirmwareInfo> modelReleases = boardInfo.firmwareReleases;
                if (modelReleases != null && !modelReleases.isEmpty()) {
                    // Get the latest release (discard all beta releases)
                    int selectedRelease = 0;
                    do {
                        latestRelease = modelReleases.get(selectedRelease);
                        selectedRelease++;
                    }
                    while ((latestRelease.isBeta && !shouldRecommendBetaReleases) && selectedRelease < modelReleases.size());

                    // Check if the bootloader is compatible with this version
                    if (ReleasesParser.versionCompare(deviceDfuInfo.getBootloaderVersion(), latestRelease.minBootloaderVersion) >= 0) {

                        // Check if the user chose to ignore this version
                        if (ReleasesParser.versionCompare(latestRelease.version, versionToIgnore) != 0) {

                            if (deviceDfuInfo.softwareRevision != null) {
                                final boolean isNewerVersion = ReleasesParser.versionCompare(latestRelease.version, deviceDfuInfo.softwareRevision) > 0;
                                isFirmwareUpdateAvailable = isNewerVersion;

                                if (BuildConfig.DEBUG) {
                                    if (isNewerVersion) {
                                        Log.d(TAG, "Updates: New version found. Ask the user to install: " + latestRelease.version);
                                    } else {
                                        Log.d(TAG, "Updates: Device has already latest version: " + deviceDfuInfo.softwareRevision);
                                    }
                                }
                            } else {
                                Log.d(TAG, "Updates: softwareRevision is null. Skipping...");
                            }
                        } else {
                            Log.d(TAG, "Updates: User ignored version: " + versionToIgnore + ". Skipping...");
                        }
                    } else {
                        Log.d(TAG, "Updates: Bootloader version " + deviceDfuInfo.getBootloaderVersion() + " below minimum needed: " + latestRelease.minBootloaderVersion);
                    }
                } else {
                    Log.d(TAG, "Updates: No firmware releases found for model: " + deviceDfuInfo.modelNumber);
                }
            } else {
                Log.d(TAG, "Updates: No releases found for model: " + deviceDfuInfo.modelNumber);
            }
        } else {
            if (!isManufacturerCorrect) {
                Log.d(TAG, "Updates: No updates for unknown manufacturer " + deviceDfuInfo.manufacturer);
            } else {
                Log.d(TAG, "Updates: No releases for modelNumber: " + deviceDfuInfo.modelNumber);
            }
        }

        completionHandler.completion(isFirmwareUpdateAvailable, latestRelease, deviceDfuInfo);
    }

    // endregion

    // region Dfu Update

    public interface DownloadStateListener {
        void onDownloadStarted(int downloadId);

        void onDownloadProgress(int percent);

        void onDownloadFailed();
    }

    public void downloadAndInstall(@NonNull Context context, @NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.BasicVersionInfo versionInfo, @NonNull DownloadStateListener downloadStateListener) {
        // Cancel previous download task if still running
        if (sDownloadTask != null) {
            sDownloadTask.cancel(true);
            sDownloadTask.setListener(null);
        }

        Log.d(TAG, "Downloading " + versionInfo.hexFileUrl);

        sDownloadTask = new DownloadTask(context, kDownloadOperation_Software_Hex, new DownloadTask.Listener() {
            @Override
            public void onDownloadProgress(int operationId, int progress) {
                downloadStateListener.onDownloadProgress(progress);
            }

            @Override
            public void onDownloadCompleted(int operationId, @NonNull Uri uri, @Nullable ByteArrayOutputStream result) {
                if (result != null) {
                    File file = writeSoftwareDownload(context, uri, result, kDefaultHexFilename);
                    final boolean success = file != null;
                    if (success) {
                        // Check if we also need to download an ini file, or we are good
                        if (versionInfo.iniFileUrl == null) {
                            // No init file so, go to install firmware
                            install(context, blePeripheral, file.getAbsolutePath(), null);
                            sDownloadTask = null;
                        } else {
                            // We have to download the ini file too
                            Log.d(TAG, "Downloading " + versionInfo.iniFileUrl);
                            sDownloadTask = new DownloadTask(context, kDownloadOperation_Software_Ini, new DownloadTask.Listener() {
                                @Override
                                public void onDownloadProgress(int operationId, int progress) {
                                    downloadStateListener.onDownloadProgress(progress);
                                }

                                @Override
                                public void onDownloadCompleted(int operationId, @NonNull Uri uri, @Nullable ByteArrayOutputStream result) {
                                    if (result != null) {
                                        File file = writeSoftwareDownload(context, uri, result, kDefaultIniFilename);
                                        final boolean success = file != null;
                                        if (success) {
                                            // We already had the hex file downloaded, and now we also have the ini file. Let's go
                                            String hexLocalFile = new File(context.getCacheDir(), kDefaultHexFilename).getAbsolutePath();          // get path from the previously downloaded hex file
                                            install(context, blePeripheral, hexLocalFile, file.getAbsolutePath());
                                        } else {
                                            downloadStateListener.onDownloadFailed();
                                            sDownloadTask = null;
                                        }
                                    } else {
                                        downloadStateListener.onDownloadFailed();
                                    }
                                }
                            });

                            downloadStateListener.onDownloadStarted(kDownloadOperation_Software_Ini);
                            sDownloadTask.execute(versionInfo.iniFileUrl);
                        }
                    } else {
                        downloadStateListener.onDownloadFailed();
                        sDownloadTask = null;
                    }

                } else {
                    downloadStateListener.onDownloadFailed();
                }
            }
        });

        downloadStateListener.onDownloadStarted(kDownloadOperation_Software_Hex);
        sDownloadTask.execute(versionInfo.hexFileUrl);


    }

    private DfuServiceController mDfuServiceController;

    private void install(@NonNull Context context, @NonNull BlePeripheral blePeripheral, @NonNull String localHexPath, @Nullable String localIniPath) {

        //final boolean keepBond = false;
        final DfuServiceInitiator starter = new DfuServiceInitiator(blePeripheral.getDevice().getAddress())
                .setDeviceName(blePeripheral.getName())
                //.setKeepBond(keepBond);
                ;

        if (kForceNumberOfPacketsReceiptNotificationsValue != 0) {
            starter.setPacketsReceiptNotificationsEnabled(true);
            starter.setPacketsReceiptNotificationsValue(kForceNumberOfPacketsReceiptNotificationsValue);
        }

        /*
        // Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
        // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
        if (versionInfo.fileType == DfuService.TYPE_AUTO)
            starter.setZip(versionInfo.zipFileUrl, null);
        else {
            starter.setBinOrHex(versionInfo.fileType, versionInfo.hexFileUrl, null);
            if (versionInfo.iniFileUrl != null) {
                starter.setInitFile(versionInfo.iniFileUrl, null);
            }
        }*/


        Log.d(TAG, "install hex: " + localHexPath);
        starter.setBinOrHex(DfuService.TYPE_APPLICATION, null, localHexPath);
        if (localIniPath != null) {
            Log.d(TAG, "install ini: " + localIniPath);
            starter.setInitFile(null, localIniPath);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(context);
        }

        mDfuServiceController = starter.start(context, DfuService.class);
    }

    public void cancelInstall() {
        Log.d(TAG, "cancelInstall");

        // Abort downloads
        if (sDownloadTask != null) {
            sDownloadTask.cancel(true);
            sDownloadTask.setListener(null);
            sDownloadTask = null;
        }

        // Abort updates
        if (mDfuServiceController != null) {
            mDfuServiceController.abort();
        }
    }

    // endregion

    // region Utils

    private File writeSoftwareDownload(@NonNull Context context, @NonNull Uri uri, @NonNull ByteArrayOutputStream result, @NonNull String filename) {

        Log.d(TAG, "Downloaded version: " + uri.toString() + " size: " + result.size() + " -> " + filename);

        File resultFile;
        File file = new File(context.getCacheDir(), filename);

        BufferedOutputStream bos;
        boolean success = true;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            bos.write(result.toByteArray());
            bos.flush();
            bos.close();
        } catch (IOException e) {
            success = false;
            Log.e(TAG, "Error writing downloaded version");
        }

        resultFile = success ? file : null;

        return resultFile;
    }
    // endregion
}
