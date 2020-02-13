package com.adafruit.bluefruit.le.connect.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.dfu.DfuFilePickerFragment;
import com.adafruit.bluefruit.le.connect.dfu.DfuService;
import com.adafruit.bluefruit.le.connect.dfu.DfuUpdater;
import com.adafruit.bluefruit.le.connect.dfu.ReleasesParser;
import com.adafruit.bluefruit.le.connect.models.DfuViewModel;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DfuFragment extends ConnectedPeripheralFragment implements DfuFilePickerFragment.OnFragmentInteractionListener {
    // Log
    private final static String TAG = DfuFragment.class.getSimpleName();

    // Models
    private DfuViewModel mDfuViewModel;

    // UI
    private ProgressBar mWaitView;
    private DfuFilePickerFragment mFilePickerFragment;

    // Data
    private boolean mShowBetaVersions;
    private DfuAdapter mAdapter;

    public DfuFragment() {
        // Required empty public constructor
    }

    // region Fragment Lifecycle
    public static DfuFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        DfuFragment fragment = new DfuFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dfu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.peripheralmodules_title);

        mWaitView = view.findViewById(R.id.waitView);

        final Context context = getContext();
        if (context != null) {
            // Read settings
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            mShowBetaVersions = sharedPreferences.getBoolean("pref_showbetaversions", false);

            // Peripherals recycler view
            RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            recyclerView.addItemDecoration(itemDecoration);

            recyclerView.setHasFixedSize(false);
            RecyclerView.LayoutManager mPeripheralsLayoutManager = new LinearLayoutManager(getContext());
            recyclerView.setLayoutManager(mPeripheralsLayoutManager);

            mAdapter = new DfuAdapter(context, mBlePeripheral, new DfuAdapter.Listener() {
                @Override
                public void onReleaseSelected(@NonNull ReleasesParser.BasicVersionInfo versionInfo, @Nullable DfuUpdater.DeviceDfuInfo deviceDfuInfo) {
                    startUpdate(versionInfo);
                }

                @Override
                public void onCustomFirmwareSelected() {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        dismissFilePickerDialog();

                        mFilePickerFragment = DfuFilePickerFragment.newInstance();
                        mFilePickerFragment.setTargetFragment(DfuFragment.this, 0);
                        Bundle arguments = new Bundle();
                        arguments.putString("message", getString(R.string.dfu_pickfiles_customfirmware_title));          // message should be set before oncreate
                        arguments.putInt("fileType", DfuService.TYPE_APPLICATION);
                        mFilePickerFragment.setArguments(arguments);
                        mFilePickerFragment.show(activity.getSupportFragmentManager(), null);
                    }
                }
            });
            recyclerView.setAdapter(mAdapter);

        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ViewModel
        FragmentActivity activity = getActivity();
        if (activity != null) {
            mDfuViewModel = new ViewModelProvider(activity).get(DfuViewModel.class);

            // Dfu Update
            mDfuViewModel.getDfuCheckResult().observe(this, dfuCheckResult -> {
                if (dfuCheckResult != null) {
                    onDfuUpdateCheckResultReceived(dfuCheckResult.blePeripheral, dfuCheckResult.isUpdateAvailable, dfuCheckResult.dfuInfo, dfuCheckResult.firmwareInfo);
                }
            });

            // Check updates if needed
            Log.d(TAG, "Check firmware updates");
            mWaitView.setVisibility(View.VISIBLE);
            mDfuViewModel.startUpdatesCheck(activity, mBlePeripheral);
        }
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
                    CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.dfu_help_title), getString(R.string.dfu_help_text));
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
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

    @SuppressWarnings("unused")
    @MainThread
    private void onDfuUpdateCheckResultReceived(@NonNull BlePeripheral blePeripheral, boolean isUpdateAvailable, @Nullable DfuUpdater.DeviceDfuInfo deviceDfuInfo, @Nullable ReleasesParser.FirmwareInfo latestRelease) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        mWaitView.setVisibility(View.GONE);

        ReleasesParser.BoardInfo boardRelease = null;
        Map<String, ReleasesParser.BoardInfo> allReleases = mDfuViewModel.getReleases(context, mShowBetaVersions);
        if (allReleases != null) {
            if (deviceDfuInfo != null) {
                if (deviceDfuInfo.modelNumber != null) {
                    boardRelease = allReleases.get(deviceDfuInfo.modelNumber);
                } else {
                    Log.d(TAG, "Warning: no releases found for this board");
                }
            } else {
                Log.d(TAG, "Warning: no deviceDfuInfo found");
            }
        } else {
            Log.d(TAG, "Warning: no releases found");
        }

        // Update UI
        mAdapter.setReleases(allReleases, boardRelease, deviceDfuInfo);
    }

    private void dismissFilePickerDialog() {
        if (mFilePickerFragment != null) {
            mFilePickerFragment.dismiss();
            mFilePickerFragment = null;
        }
    }
    // endregion

    private void startUpdate(@NonNull ReleasesParser.BasicVersionInfo versionInfo) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.startUpdate(mBlePeripheral, versionInfo);
        }

    }

    // region DfuFilePickerFragment.OnImageCropListener
    @Override
    public void onDfuFilePickerStartUpdate(@NonNull ReleasesParser.BasicVersionInfo versionInfo) {
        startUpdate(versionInfo);
    }

    @Override
    public void onDfuFilePickerDismissed() {
        dismissFilePickerDialog();
    }

    // region Adapter
    private static class DfuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        // Constants
        private static final int kCellType_SectionTitle = 0;
        private static final int kCellType_CurrentFirmware = 1;
        private static final int kCellType_Firmware = 2;
        private static final int kCellType_CustomFirmware = 3;
        private static final int kCellType_Bootloader = 4;

        private static final int kCurrentFirmwareCellStartPosition = 1;
        private static final int kFirmwareCellsStartPosition = 3;
        // Data
        private Context mContext;
        private Map<String, ReleasesParser.BoardInfo> mAllReleases = new LinkedHashMap<>();
        private ReleasesParser.BoardInfo mBoardRelease;
        private DfuUpdater.DeviceDfuInfo mDeviceDfuInfo;
        private BlePeripheral mBlePeripheral;
        private Listener mListener;

        DfuAdapter(@NonNull Context context, @NonNull BlePeripheral blePeripheral, @NonNull Listener listener) {
            mContext = context.getApplicationContext();
            mBlePeripheral = blePeripheral;
            mListener = listener;
        }

        void setReleases(@Nullable Map<String, ReleasesParser.BoardInfo> releases, @Nullable ReleasesParser.BoardInfo boardRelease, @Nullable DfuUpdater.DeviceDfuInfo deviceDfuInfo) {
            mAllReleases = releases;
            mBoardRelease = boardRelease;
            mDeviceDfuInfo = deviceDfuInfo;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            super.getItemViewType(position);

            int result;
            final int customFirmwarePosition = getCustomFirmwareStartPosition();
            if (position == kCurrentFirmwareCellStartPosition - 1 || position == kFirmwareCellsStartPosition - 1 || position == customFirmwarePosition + 1) {
                result = kCellType_SectionTitle;
            } else if (position < kFirmwareCellsStartPosition) {
                result = kCellType_CurrentFirmware;
            } else if (position < customFirmwarePosition) {
                result = kCellType_Firmware;
            } else if (position == customFirmwarePosition) {
                result = kCellType_CustomFirmware;
            } else {
                result = kCellType_Bootloader;
            }

            return result;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case kCellType_SectionTitle: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_common_section_item, parent, false);
                    return new SectionViewHolder(view);
                }
                case kCellType_CurrentFirmware: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_dfu_release_item, parent, false);
                    return new VersionViewHolder(view);
                }
                case kCellType_Firmware: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_dfu_release_item, parent, false);
                    return new VersionViewHolder(view);
                }
                case kCellType_CustomFirmware: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_dfu_custom_item, parent, false);
                    return new CustomVersionViewHolder(view);
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
                case kCellType_SectionTitle: {
                    String stringId;
                    if (position == kCurrentFirmwareCellStartPosition - 1) {
                        stringId = "dfu_currentversion_title";
                    } else if (position == kFirmwareCellsStartPosition - 1) {
                        stringId = "dfu_firmwarereleases_title";
                    } else {
                        stringId = "dfu_bootloaderreleases_title";
                    }

                    SectionViewHolder sectionViewHolder = (SectionViewHolder) holder;
                    sectionViewHolder.titleTextView.setText(localizationManager.getString(mContext, stringId));
                    break;
                }
                case kCellType_CurrentFirmware: {
                    String firmwareString = null;
                    if (mDeviceDfuInfo.softwareRevision != null) {
                        firmwareString = String.format(mContext.getString(R.string.dfu_firmware_format), mDeviceDfuInfo.softwareRevision);
                    }
                    VersionViewHolder versionViewHolder = (VersionViewHolder) holder;
                    versionViewHolder.titleTextView.setText(mBlePeripheral.getName());
                    versionViewHolder.descriptionTextView.setText(firmwareString);
                    break;
                }

                case kCellType_Firmware: {
                    int row = position - kFirmwareCellsStartPosition;
                    ReleasesParser.FirmwareInfo firmwareInfo = getFirmwareInfoForPosition(row);
                    if (firmwareInfo != null) {
                        int versionFormatStringId = firmwareInfo.isBeta ? R.string.dfu_betaversion_format : R.string.dfu_version_format;

                        VersionViewHolder versionViewHolder = (VersionViewHolder) holder;
                        versionViewHolder.titleTextView.setText(String.format(mContext.getString(versionFormatStringId), firmwareInfo.version));
                        versionViewHolder.descriptionTextView.setText(firmwareInfo.boardName);
                        versionViewHolder.mainView.setOnClickListener(view -> mListener.onReleaseSelected(firmwareInfo, mDeviceDfuInfo));
                    }
                    break;
                }

                case kCellType_CustomFirmware: {
                    CustomVersionViewHolder filePickerViewHolder = (CustomVersionViewHolder) holder;
                    filePickerViewHolder.chooseButton.setOnClickListener(view -> mListener.onCustomFirmwareSelected());
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {

            int numItems = 0;
            if (mDeviceDfuInfo != null) {
                final int firstSectionItems = 1 + 1; // title + item
                final int secondSectionItems = 1 + getNumReleases() + 1; // title + releases + picker
                numItems = firstSectionItems + secondSectionItems;       // +1 current installed firmware +1 Custom Firmware Button
            }
            return numItems;
        }

        private int getNumReleases() {
            int numReleases = 0;
            if (mBoardRelease != null && mBoardRelease.firmwareReleases != null) {
                numReleases = mBoardRelease.firmwareReleases.size();
            } else {      // Show all releases
                for (String key : mAllReleases.keySet()) {
                    ReleasesParser.BoardInfo boardInfo = mAllReleases.get(key);
                    if (boardInfo != null) {
                        numReleases += boardInfo.firmwareReleases.size();
                    }
                }
            }
            return numReleases;
        }

        private int getCustomFirmwareStartPosition() {
            return kFirmwareCellsStartPosition + getNumReleases();
        }

        private ReleasesParser.FirmwareInfo getFirmwareInfoForPosition(int row) {
            ReleasesParser.FirmwareInfo firmwareInfo = null;

            if (mBoardRelease != null && mBoardRelease.firmwareReleases != null) {      // If showing releases for a specific board
                firmwareInfo = mBoardRelease.firmwareReleases.get(row);
            } else {       // If showing all available releases
                int currentRow = 0;
                int currentBoardIndex = 0;

                String[] sortedKeys = mAllReleases.keySet().toArray(new String[0]);
                Arrays.sort(sortedKeys);

                while (currentRow <= row) {
                    String currentKey = sortedKeys[currentBoardIndex];
                    ReleasesParser.BoardInfo boardRelease = mAllReleases.get(currentKey);
                    if (boardRelease != null) {
                        List<ReleasesParser.FirmwareInfo> firmwareReleases = boardRelease.firmwareReleases;
                        int numReleases = firmwareReleases.size();
                        int remaining = row - currentRow;
                        if (remaining < numReleases) {
                            firmwareInfo = firmwareReleases.get(remaining);
                        } else {
                            currentBoardIndex++;
                        }
                        currentRow += numReleases;
                    }
                }
            }

            return firmwareInfo;
        }

        // Interface
        interface Listener {
            void onReleaseSelected(@NonNull ReleasesParser.BasicVersionInfo versionInfo, @Nullable DfuUpdater.DeviceDfuInfo deviceDfuInfo);

            void onCustomFirmwareSelected();
        }

        // Data Structures
        class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;

            SectionViewHolder(View view) {
                super(view);
                titleTextView = view.findViewById(R.id.titleTextView);
            }
        }

        class VersionViewHolder extends RecyclerView.ViewHolder {
            ViewGroup mainView;
            TextView titleTextView;
            TextView descriptionTextView;

            VersionViewHolder(View view) {
                super(view);
                mainView = view.findViewById(R.id.mainViewGroup);
                titleTextView = view.findViewById(R.id.titleTextView);
                descriptionTextView = view.findViewById(R.id.descriptionTextView);
            }
        }

        class CustomVersionViewHolder extends RecyclerView.ViewHolder {
            Button chooseButton;

            CustomVersionViewHolder(View view) {
                super(view);
                chooseButton = view.findViewById(R.id.chooseButton);
            }
        }
    }
    // endregion


}