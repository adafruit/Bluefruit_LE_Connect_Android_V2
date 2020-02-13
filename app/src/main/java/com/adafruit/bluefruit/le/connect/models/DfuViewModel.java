package com.adafruit.bluefruit.le.connect.models;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.dfu.DfuUpdater;
import com.adafruit.bluefruit.le.connect.dfu.ReleasesParser;
import com.adafruit.bluefruit.le.connect.utils.SingleLiveEvent;

import java.util.Map;

public class DfuViewModel extends ViewModel {
    // Constants
    private final static String TAG = DfuViewModel.class.getSimpleName();

    // Data structures
    public static class DfuCheckResult {
        public BlePeripheral blePeripheral;
        public boolean isUpdateAvailable;
        public ReleasesParser.FirmwareInfo firmwareInfo;
        public DfuUpdater.DeviceDfuInfo dfuInfo;

        DfuCheckResult(@NonNull BlePeripheral blePeripheral, boolean isUpdateAvailable, @Nullable ReleasesParser.FirmwareInfo firmwareInfo, @Nullable DfuUpdater.DeviceDfuInfo dfuInfo) {
            this.blePeripheral = blePeripheral;
            this.isUpdateAvailable = isUpdateAvailable;
            this.firmwareInfo = firmwareInfo;
            this.dfuInfo = dfuInfo;
        }
    }

    // Data
    private DfuUpdater mDfuUpdater = new DfuUpdater();
    private boolean mIsCheckingFirmwareUpdates = false;
    private final SingleLiveEvent<DfuCheckResult> mDfuCheckResult = new SingleLiveEvent<>();

    /*
    public DfuViewModel(@NonNull Application application) {
        super(application);

        //mFirmwareUpdater = new FirmwareUpdater(this, this);
    }*/

    // region Getters
    public SingleLiveEvent<DfuCheckResult> getDfuCheckResult() {
        return mDfuCheckResult;
    }
    // endregion

    // region Actions
    public void startUpdatesCheck(@NonNull Context context, @NonNull BlePeripheral blePeripheral) {
        if (mIsCheckingFirmwareUpdates) {
            Log.w(TAG, "Already checking firmware updates. Skipped...");
            return;
        }

        mIsCheckingFirmwareUpdates = true;

        DfuUpdater.checkUpdatesForPeripheral(context, blePeripheral, false, false, DfuUpdater.getIgnoredVersion(context),
                (isUpdateAvailable, latestRelease, deviceDfuInfo) -> {
                    mIsCheckingFirmwareUpdates = false;
                    final Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> mDfuCheckResult.setValue(new DfuCheckResult(blePeripheral, isUpdateAvailable, latestRelease, deviceDfuInfo)));
                });
    }

    public void setIgnoredVersion(@NonNull Context context, @Nullable String ignoredVersion) {
        DfuUpdater.setIgnoredVersion(context, ignoredVersion);
    }


    public void downloadAndInstall(@NonNull Context context, @NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.BasicVersionInfo versionInfo, @NonNull DfuUpdater.DownloadStateListener downloadStateListener) {
        mDfuUpdater.downloadAndInstall(context, blePeripheral, versionInfo, downloadStateListener);
    }

    /*
    public void install(@NonNull Context context, @NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.BasicVersionInfo versionInfo ) {
        mDfuUpdater.install(context, blePeripheral, versionInfo);
    }*/

    public void cancelInstall() {
        mDfuUpdater.cancelInstall();
    }


    public @Nullable
    Map<String, ReleasesParser.BoardInfo> getReleases(@NonNull Context context, boolean showBetaVersions) {
        return DfuUpdater.getReleases(context, showBetaVersions);
    }

    // endregion
}
