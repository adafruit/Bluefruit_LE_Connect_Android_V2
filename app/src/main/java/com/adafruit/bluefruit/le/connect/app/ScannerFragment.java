package com.adafruit.bluefruit.le.connect.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.bluetooth.le.ScanCallback;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BleManager;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.dfu.ReleasesParser;
import com.adafruit.bluefruit.le.connect.models.DfuViewModel;
import com.adafruit.bluefruit.le.connect.models.ScannerViewModel;
import com.adafruit.bluefruit.le.connect.style.StyledSnackBar;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.KeyboardUtils;
import com.adafruit.bluefruit.le.connect.utils.LocalizationManager;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

import no.nordicsemi.android.support.v18.scanner.ScanRecord;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.MODE_PRIVATE;

public class ScannerFragment extends Fragment implements ScannerStatusFragmentDialog.onScannerStatusCancelListener {
    // Constants
    private final static String TAG = ScannerFragment.class.getSimpleName();

    private final static String kPreferences = "Scanner";
    private final static String kPreferences_filtersPanelOpen = "filtersPanelOpen";

    // Models
    private DfuViewModel mDfuViewModel;

    // Data -  Scanned Devices
    private ScannerFragmentListener mListener;
    private ScannerViewModel mScannerViewModel;
    private BlePeripheralsAdapter mBlePeripheralsAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // Data - Filters
    private View mFiltersPanelView;
    private ImageView mFiltersExpandImageView;
    private ImageButton mFiltersClearButton;
    private TextView mFiltersTitleTextView;
    private SeekBar mFiltersRssiSeekBar;
    private TextView mFiltersRssiValueTextView;
    private CheckBox mFiltersUnnamedCheckBox;
    private CheckBox mFiltersUartCheckBox;
    private TextView mFilteredPeripheralsCountTextView;

    // Data - Multiconnect
    private View mMultiConnectPanelView;
    private ImageView mMultiConnectExpandImageView;
    private CheckBox mMultiConnectCheckBox;
    private TextView mMultiConnectConnectedDevicesTextView;
    private Button mMultiConnectStartButton;

    // Data - Dialogs
    private ScannerStatusFragmentDialog mConnectingDialog;

    // region Fragment lifecycle
    public static ScannerFragment newInstance() {
        return new ScannerFragment();
    }

    public ScannerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mListener = (ScannerFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ScannerFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        if (context != null) {
            // Peripherals recycler view
            RecyclerView peripheralsRecyclerView = view.findViewById(R.id.peripheralsRecyclerView);
            DividerItemDecoration itemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable lineSeparatorDrawable = ContextCompat.getDrawable(context, R.drawable.simpledivideritemdecoration);
            assert lineSeparatorDrawable != null;
            itemDecoration.setDrawable(lineSeparatorDrawable);
            peripheralsRecyclerView.addItemDecoration(itemDecoration);

            RecyclerView.LayoutManager peripheralsLayoutManager = new LinearLayoutManager(getContext());
            peripheralsRecyclerView.setLayoutManager(peripheralsLayoutManager);

            // Disable update animation
            SimpleItemAnimator animator = (SimpleItemAnimator) peripheralsRecyclerView.getItemAnimator();
            if (animator != null) {
                animator.setSupportsChangeAnimations(false);
            }

            // Adapter
            mBlePeripheralsAdapter = new BlePeripheralsAdapter(context, blePeripheral -> {
                ScanRecord scanRecord = blePeripheral.getScanRecord();
                if (scanRecord != null) {
                    byte[] advertisementBytes = scanRecord.getBytes();
                    if (advertisementBytes == null) {
                        advertisementBytes = new byte[]{};
                    }
                    final String packetText = BleUtils.bytesToHexWithSpaces(advertisementBytes);
                    final String clipboardLabel = context.getString(R.string.scanresult_advertisement_rawdata_title);

                    new AlertDialog.Builder(context)
                            .setTitle(R.string.scanresult_advertisement_rawdata_title)
                            .setMessage(packetText)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(android.R.string.copy, (dialog, which) -> {
                                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    ClipData clip = ClipData.newPlainText(clipboardLabel, packetText);
                                    clipboard.setPrimaryClip(clip);
                                }
                            })
                            .show();
                }
            });
            peripheralsRecyclerView.setAdapter(mBlePeripheralsAdapter);

            // Swipe to refreshAll
            mSwipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            mSwipeRefreshLayout.setOnRefreshListener(() -> {
                if (BleManager.getInstance().isAdapterEnabled()) {
                    mScannerViewModel.refresh();
                } else {
                    mListener.bluetoothAdapterIsDisabled();
                }
                new Handler().postDelayed(() -> mSwipeRefreshLayout.setRefreshing(false), 500);
            });

            // Filters
            mFiltersPanelView = view.findViewById(R.id.filtersExpansionView);
            mFiltersExpandImageView = view.findViewById(R.id.filtersExpandImageView);
            mFiltersClearButton = view.findViewById(R.id.filtersClearButton);
            mFiltersTitleTextView = view.findViewById(R.id.filtersTitleTextView);
            EditText filtersNameEditText = view.findViewById(R.id.filtersNameEditText);
            filtersNameEditText.addTextChangedListener(new TextWatcher() {

                public void afterTextChanged(Editable s) {
                    String text = s.toString();
                    mScannerViewModel.setNameFilter(text);
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            SharedPreferences preferences = context.getSharedPreferences(kPreferences, MODE_PRIVATE);
            boolean filtersIsPanelOpen = preferences.getBoolean(kPreferences_filtersPanelOpen, false);
            openFiltersPanel(filtersIsPanelOpen, false);
        }

        ImageButton filterNameSettings = view.findViewById(R.id.filterNameSettings);
        filterNameSettings.setOnClickListener(view1 -> {
            final Boolean isFilterNameExact = mScannerViewModel.isFilterNameExact().getValue();
            final Boolean isFilterNameCaseInsensitive = mScannerViewModel.isFilterNameCaseInsensitive().getValue();

            if (isFilterNameExact != null && isFilterNameCaseInsensitive != null) {
                PopupMenu popup = new PopupMenu(getContext(), view1);
                popup.setOnMenuItemClickListener(item -> {
                    boolean processed = true;
                    switch (item.getItemId()) {
                        case R.id.scanfilter_name_contains:
                            mScannerViewModel.setIsFilterNameExact(false);
                            break;
                        case R.id.scanfilter_name_exact:
                            mScannerViewModel.setIsFilterNameExact(true);
                            break;
                        case R.id.scanfilter_name_sensitive:
                            mScannerViewModel.setIsFilterNameCaseInsensitive(false);
                            break;
                        case R.id.scanfilter_name_insensitive:
                            mScannerViewModel.setIsFilterNameCaseInsensitive(true);
                            break;
                        default:
                            processed = false;
                            break;
                    }
                    return processed;
                });
                MenuInflater inflater = popup.getMenuInflater();
                Menu menu = popup.getMenu();
                inflater.inflate(R.menu.menu_scan_filters_name, menu);

                menu.findItem(isFilterNameExact ? R.id.scanfilter_name_exact : R.id.scanfilter_name_contains).setChecked(true);
                menu.findItem(isFilterNameCaseInsensitive ? R.id.scanfilter_name_insensitive : R.id.scanfilter_name_sensitive).setChecked(true);
                popup.show();
            }
        });

        mFiltersRssiSeekBar = view.findViewById(R.id.filtersRssiSeekBar);
        mFiltersRssiSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final int rssiValue = -seekBar.getProgress();
                mScannerViewModel.setFilterRssiValue(rssiValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mFiltersRssiValueTextView = view.findViewById(R.id.filtersRssiValueTextView);
        mFiltersUnnamedCheckBox = view.findViewById(R.id.filtersUnnamedCheckBox);
        mFiltersUnnamedCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                mScannerViewModel.setFilterUnnamedEnabled(isChecked);
            }
        });
        mFiltersUartCheckBox = view.findViewById(R.id.filtersUartCheckBox);
        mFiltersUartCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                mScannerViewModel.setFilterOnlyUartEnabled(isChecked);
            }
        });


        ViewGroup filtersTitleGroupView = view.findViewById(R.id.filtersTitleGroupView);
        filtersTitleGroupView.setOnClickListener(view12 -> {        // onClickExpandFilters

            KeyboardUtils.dismissKeyboard(view12);

            // Get current preference
            SharedPreferences preferences1 = getContext().getSharedPreferences(kPreferences, MODE_PRIVATE);
            boolean filtersIsPanelOpen1 = preferences1.getBoolean(kPreferences_filtersPanelOpen, false);

            // Update
            openFiltersPanel(!filtersIsPanelOpen1, true);
        });

        mFiltersClearButton.setOnClickListener(view13 -> mScannerViewModel.setDefaultFilters(false));

        mFilteredPeripheralsCountTextView = view.findViewById(R.id.filteredPeripheralsCountTextView);

        // Multiple Connection
        mMultiConnectPanelView = view.findViewById(R.id.multiConnectExpansionView);
        mMultiConnectExpandImageView = view.findViewById(R.id.multiConnectExpandImageView);
        mMultiConnectCheckBox = view.findViewById(R.id.multiConnectCheckBox);
        mMultiConnectConnectedDevicesTextView = view.findViewById(R.id.multiConnectConnectedDevicesTextView);
        mMultiConnectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                mScannerViewModel.setIsMultiConnectEnabled(isChecked);
            }
        });

        openMultiConnectPanel(false, false);

        ViewGroup multiConnectTitleGroupView = view.findViewById(R.id.multiConnectTitleGroupView);
        multiConnectTitleGroupView.setOnClickListener(view15 -> {
            // onClickExpandMultiConnect
            KeyboardUtils.dismissKeyboard(view15);
            mScannerViewModel.setIsMultiConnectEnabled(!mScannerViewModel.isMultiConnectEnabledValue());
        });

        mMultiConnectStartButton = view.findViewById(R.id.multiConnectStartButton);
        mMultiConnectStartButton.setOnClickListener(view14 -> {
            Log.d(TAG, "Start multiconnect");

            // Go to peripheral modules
            if (mListener != null) {
                mListener.startPeripheralModules(null);
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ViewModel
        FragmentActivity activity = getActivity();
        if (activity != null) {
            mDfuViewModel = new ViewModelProvider(activity).get(DfuViewModel.class);
            mScannerViewModel = new ViewModelProvider(this, new ViewModelProvider.AndroidViewModelFactory(activity.getApplication())).get(ScannerViewModel.class);
        }

        // Scan results
        mScannerViewModel.getFilteredBlePeripherals().observe(this, blePeripherals -> mBlePeripheralsAdapter.setBlePeripherals(blePeripherals));

        // Scanning
        mScannerViewModel.getScanningErrorCode().observe(this, errorCode -> {
            Log.d(TAG, "Scanning error: " + errorCode);

            if (getActivity() != null && !getActivity().isFinishing()) {        // Check that is not finishing to avoid badtokenexceptions
                if (errorCode != null && errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {       // Check for known errors
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    AlertDialog dialog = builder.setTitle(R.string.dialog_error).setMessage(R.string.bluetooth_scanner_errorregisteringapp)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                } else {        // Ask for location permission
                    mListener.scannerRequestLocationPermissionIfNeeded();
                }
            }
        });

        // Filters
        mScannerViewModel.getFiltersDescription().observe(this, filterDescription -> mFiltersTitleTextView.setText(filterDescription));

        mScannerViewModel.getRssiFilterDescription().observe(this, rssiDescription -> mFiltersRssiValueTextView.setText(rssiDescription));

        mScannerViewModel.getRssiFilterValue().observe(this, value -> {
            if (value != null) {
                mFiltersRssiSeekBar.setProgress(-value);
            }
        });

        mScannerViewModel.isAnyFilterEnabled().observe(this, enabled -> mFiltersClearButton.setVisibility(Boolean.TRUE.equals(enabled) ? View.VISIBLE : View.GONE));

        mScannerViewModel.isFilterUnnamedEnabled().observe(this, enabled -> mFiltersUnnamedCheckBox.setChecked(Boolean.TRUE.equals(enabled)));

        mScannerViewModel.isFilterOnlyUartEnabled().observe(this, enabled -> mFiltersUartCheckBox.setChecked(Boolean.TRUE.equals(enabled)));

        mScannerViewModel.getBlePeripheralsConnectionChanged().observe(this, blePeripheral -> {
            mBlePeripheralsAdapter.notifyDataSetChanged();
            if (blePeripheral != null) {
                showConnectionStateDialog(blePeripheral);
            }
        });

        mScannerViewModel.getBlePeripheralDiscoveredServices().observe(this, this::showServiceDiscoveredStateDialog);

        mScannerViewModel.getConnectionErrorMessage().observe(this, this::showConnectionStateError);

        mScannerViewModel.isMultiConnectEnabled().observe(this, aBoolean -> {
            boolean isChecked = Boolean.TRUE.equals(aBoolean);
            openMultiConnectPanel(isChecked, true);
            if (mMultiConnectCheckBox.isChecked() != isChecked) {
                mMultiConnectCheckBox.setChecked(isChecked);
            }
        });

        // Connection
        mScannerViewModel.getNumDevicesConnected().observe(this, numConnectedDevices -> {
            Context context = getContext();
            if (context != null) {
                final int numDevices = numConnectedDevices != null ? numConnectedDevices : 0;
                final String message = String.format(Locale.ENGLISH, LocalizationManager.getInstance().getString(context, numDevices == 1 ? "multiconnect_connecteddevices_single_format" : "multiconnect_connecteddevices_multiple_format"), numConnectedDevices);
                mMultiConnectConnectedDevicesTextView.setText(message);
                mMultiConnectStartButton.setEnabled(numDevices >= 2);
            }
        });

        // Filtered-out peripherals
        mScannerViewModel.getNumPeripheralsFilteredOut().observe(this, numPeripheralsFilteredOutInteger -> {
            Context context = getContext();
            if (context != null) {
                final int numPeripheralsFilteredOut = numPeripheralsFilteredOutInteger != null ? numPeripheralsFilteredOutInteger : 0;
                Integer numPeripheralsFilteredInteger = mScannerViewModel.getNumPeripheralsFiltered().getValue();
                final int numPeripheralsFiltered = numPeripheralsFilteredInteger != null ? numPeripheralsFilteredInteger : 0;

                boolean isFilteredPeripheralCountLabelHidden = numPeripheralsFiltered > 0 || numPeripheralsFilteredOut == 0;
                mFilteredPeripheralsCountTextView.setVisibility(isFilteredPeripheralCountLabelHidden ? View.GONE : View.VISIBLE);
                String message = String.format(Locale.ENGLISH, LocalizationManager.getInstance().getString(context, numPeripheralsFilteredOut == 1 ? "scanner_filteredoutinfo_single_format" : "scanner_filteredoutinfo_multiple_format"), numPeripheralsFilteredOut);
                mFilteredPeripheralsCountTextView.setText(message);
            }
        });

        // Dfu Update
        mDfuViewModel.getDfuCheckResult().observe(this, dfuCheckResult -> {
            if (dfuCheckResult != null) {
                onDfuUpdateCheckResultReceived(dfuCheckResult.blePeripheral, dfuCheckResult.isUpdateAvailable, dfuCheckResult.firmwareInfo);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

            // Automatically starts scanning
            boolean isDfuInProgress = activity instanceof MainActivity && ((MainActivity) activity).isIsDfuInProgress();
            if (!isDfuInProgress) {
                startScanning();
            } else {
                Log.d(TAG, "Don't start scanning because DFU  is in progress");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mScannerViewModel.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Null out references to views to avoid leaks when the fragment is added to the backstack: https://stackoverflow.com/questions/59503689/could-navigation-arch-component-create-a-false-positive-memory-leak/59504797#59504797
        mBlePeripheralsAdapter = null;
        mSwipeRefreshLayout = null;
        mFiltersPanelView = null;
        mFiltersExpandImageView = null;
        mFiltersClearButton = null;
        mFiltersTitleTextView = null;
        mFiltersRssiSeekBar = null;
        mFiltersRssiValueTextView = null;
        mFiltersUnnamedCheckBox = null;
        mFiltersUartCheckBox = null;
        mFilteredPeripheralsCountTextView = null;

        mMultiConnectPanelView = null;
        mMultiConnectExpandImageView = null;
        mMultiConnectCheckBox = null;
        mMultiConnectConnectedDevicesTextView = null;
        mMultiConnectStartButton = null;

       // removeConnectionStateDialog();
    }

    @Override
    public void onDestroy() {
        mScannerViewModel.saveFilters();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_centralmode, menu);
    }

    // endregion

    // region Actions
    void startScanning() {
        mScannerViewModel.start();
    }

    void disconnectAllPeripherals() {
        mScannerViewModel.disconnectAllPeripherals();
    }

    // endregion

    // region Filters
    private void openFiltersPanel(final boolean isOpen, boolean animated) {

        // Save preference
        Context context = getContext();
        if (context != null) {
            SharedPreferences.Editor preferencesEditor = context.getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
            preferencesEditor.putBoolean(kPreferences_filtersPanelOpen, isOpen);
            preferencesEditor.apply();
        }

        // Animate
        long animationDuration = animated ? 300 : 0;

        RotateAnimation rotate = new RotateAnimation(isOpen ? -90 : 0, isOpen ? 0 : -90, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(animationDuration);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setFillAfter(true);
        mFiltersExpandImageView.startAnimation(rotate);

        mFiltersPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        mFiltersPanelView.animate()
                .alpha(isOpen ? 1.0f : 0)
                .setDuration(isOpen ? animationDuration : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (mFiltersPanelView != null) {
                            mFiltersPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
                        }
                    }
                });

    }
    // endregion

    // region Multiconnect
    @SuppressWarnings("SameParameterValue")
    private void openMultiConnectPanel(final boolean isOpen, boolean animated) {

        // Check if already in the right position
        if ((mMultiConnectPanelView.getVisibility() == View.VISIBLE && isOpen) || (mMultiConnectPanelView.getVisibility() == View.GONE && !isOpen)) {
            return;
        }

        // Animate changes
        final long animationDuration = animated ? 300 : 0;

        RotateAnimation rotate = new RotateAnimation(isOpen ? -90 : 0, isOpen ? 0 : -90, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(animationDuration);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setFillAfter(true);
        mMultiConnectExpandImageView.startAnimation(rotate);

        mMultiConnectPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        mMultiConnectPanelView.animate()
                .alpha(isOpen ? 1.0f : 0)
                .setDuration(isOpen ? animationDuration : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mMultiConnectPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
                    }
                });

    }
    // endregion

    // region Dialogs

    private void showConnectionStateDialog(BlePeripheral blePeripheral) {
        final int connectionState = blePeripheral.getConnectionState();

        switch (connectionState) {
            case BlePeripheral.STATE_DISCONNECTED:
                removeConnectionStateDialog();
                break;
            case BlePeripheral.STATE_CONNECTING:
                showConnectionStateDialog(R.string.peripheraldetails_connecting, blePeripheral);
                break;
            case BlePeripheral.STATE_CONNECTED:
                showConnectionStateDialog(R.string.peripheraldetails_discoveringservices, blePeripheral);
                break;
        }
    }

    private void removeConnectionStateDialog() {
        if (mConnectingDialog != null) {
            mConnectingDialog.dismiss();
//            mConnectingDialog.cancel();

            mConnectingDialog = null;
        }
    }

    @Override
    public void scannerStatusCancelled(@NonNull String blePeripheralIdentifier) {
        Log.d(TAG, "Connecting dialog cancelled");

        final BlePeripheral blePeripheral = mScannerViewModel.getPeripheralWithIdentifier(blePeripheralIdentifier);
        if (blePeripheral != null) {
            blePeripheral.disconnect();
        } else {
            Log.w(TAG, "status dialog cancelled for unknown peripheral");
        }
    }

    private void showConnectionStateDialog(@StringRes int messageId, final BlePeripheral blePeripheral) {
        // Show dialog
        final String message = getString(messageId);
        if (mConnectingDialog == null || !mConnectingDialog.isInitialized()) {
            removeConnectionStateDialog();

            FragmentActivity activity = getActivity();
            if (activity != null) {
                FragmentManager fragmentManager = getFragmentManager();
                if (fragmentManager != null) {
                    mConnectingDialog = ScannerStatusFragmentDialog.newInstance(message, blePeripheral.getIdentifier());
                    mConnectingDialog.setTargetFragment(this, 0);
                    mConnectingDialog.show(fragmentManager, "ConnectingDialog");
                }
            }
        } else {
            mConnectingDialog.setMessage(message);
        }
    }

    private void showServiceDiscoveredStateDialog(BlePeripheral blePeripheral) {
        Context context = getContext();

        if (blePeripheral != null && context != null) {

            if (blePeripheral.isDisconnected()) {
                Log.d(TAG, "Abort connection sequence. Peripheral disconnected");
            } else {
                final boolean isMultiConnectEnabled = mScannerViewModel.isMultiConnectEnabledValue();
                if (isMultiConnectEnabled) {
                    removeConnectionStateDialog();
                    //  Nothing to do, wait for more connections or start
                } else {
                    // Check updates if needed
                    Log.d(TAG, "Check firmware updates");
                    showConnectionStateDialog(R.string.peripheraldetails_checkingupdates, blePeripheral);
                    mDfuViewModel.startUpdatesCheck(context, blePeripheral);
                }
            }
        }
    }

    private void showConnectionStateError(String message) {
        removeConnectionStateDialog();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            View view = activity.findViewById(android.R.id.content);
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
            StyledSnackBar.styleSnackBar(snackbar, activity);
            snackbar.show();
        }
    }

    // endregion

    // region Dfu
    @MainThread
    private void onDfuUpdateCheckResultReceived(@NonNull BlePeripheral blePeripheral, boolean isUpdateAvailable, @Nullable ReleasesParser.FirmwareInfo latestRelease) {
        Log.d(TAG, "Update available: " + isUpdateAvailable);
        removeConnectionStateDialog();

        Context context = getContext();
        if (isUpdateAvailable && latestRelease != null && context != null) {
            // Ask user if should update
            String message = String.format(getString(R.string.autoupdate_description_format), latestRelease.version);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.autoupdate_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.autoupdate_startupdate, (dialog, which) -> startFirmwareUpdate(blePeripheral, latestRelease))
                    .setNeutralButton(R.string.autoupdate_later, (dialog, which) -> {
                        if (mListener != null) {
                            mListener.startPeripheralModules(blePeripheral.getIdentifier());
                        }
                    })
                    .setNegativeButton(R.string.autoupdate_ignore, (dialog, which) -> {
                        mDfuViewModel.setIgnoredVersion(context, latestRelease.version);
                        if (mListener != null) {
                            mListener.startPeripheralModules(blePeripheral.getIdentifier());
                        }
                    })
                    .setCancelable(false)
                    .show();
        } else {
            // Go to peripheral modules
            if (mListener != null) {
                mListener.startPeripheralModules(blePeripheral.getIdentifier());
            }
        }
    }
    // endregion

    private void startFirmwareUpdate(@NonNull BlePeripheral blePeripheral, @NonNull ReleasesParser.FirmwareInfo firmwareInfo) {
        removeConnectionStateDialog();       // hide current dialogs because software update will display a dialog
        mScannerViewModel.stop();

        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) activity;
            mainActivity.startUpdate(blePeripheral, firmwareInfo);
        }
    }

    // region Listeners
    interface ScannerFragmentListener {
        void bluetoothAdapterIsDisabled();

        void scannerRequestLocationPermissionIfNeeded();

        void startPeripheralModules(String singlePeripheralIdentifier);
    }

    // endregion
}