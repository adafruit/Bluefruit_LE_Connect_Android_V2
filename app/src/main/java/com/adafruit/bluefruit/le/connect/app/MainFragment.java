package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.models.PeripheralModeViewModel;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;

import java.lang.ref.WeakReference;

public class MainFragment extends Fragment {
    // Log
    private final static String TAG = MainFragment.class.getSimpleName();

    // UI
    //private BottomNavigationView mNavigationView;

    // Data
    private WeakReference<Fragment> mCurrentFragmentReference;
    private int selectedFragmentId = 0;
    //private PeripheralModeViewModel mPeripheralModeViewModel;
    private boolean mIsInitialNavigationItemSelected = false;

    // region Fragment Lifecycle
    public MainFragment() {
        // Required empty public constructor
    }

    public static MainFragment newInstance() {
        return new MainFragment();
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
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /*
        // Setup bottom navigation view
        mNavigationView = view.findViewById(R.id.navigation);
        mNavigationView.setOnNavigationItemSelectedListener(this::selectFragment);
        updateActionBarTitle(mNavigationView.getSelectedItemId());       // Restore title (i.e. when a fragment is popped)
        */
        updateActionBarTitle(R.id.navigation_central);      // Restore title (i.e. when a fragment is popped)

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        FragmentActivity activity = getActivity();
        if (activity != null) {
            // ViewModels
            //mPeripheralModeViewModel = ViewModelProviders.of(activity).get(PeripheralModeViewModel.class);   // Note: shares viewModel with Activity

            // update options menu with current values
            activity.invalidateOptionsMenu();

            // Setup when activity is created for the first time
            if (!mIsInitialNavigationItemSelected) {
                // Set initial value
                //mNavigationView.setSelectedItemId(R.id.navigation_central);
                selectFragment(R.id.navigation_central);
                mIsInitialNavigationItemSelected = true;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // endregion

    // region Fragments
    private Fragment getCurrentFragment() {
        return mCurrentFragmentReference == null ? null : mCurrentFragmentReference.get();
    }

    /*
    private boolean selectFragment(@NonNull MenuItem item) {
        final int navigationSelectedItem = item.getItemId();
        return selectFragment(navigationSelectedItem);
    }*/


    @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
    private boolean selectFragment(int navigationSelectedItem) {

        // Check errors
        if (navigationSelectedItem == R.id.navigation_peripheral) {
            int errorMessageId = 0;
            if (!PeripheralModeViewModel.isPeripheralModeSupported()) {
                errorMessageId = R.string.peripheral_notsupported;
            }

            if (errorMessageId != 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                AlertDialog dialog = builder.setTitle(R.string.dialog_error).setMessage(errorMessageId)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
                return false;
            }
        }

        // Change fragment if different
        boolean isFragmentChanged = false;
        if (navigationSelectedItem != selectedFragmentId) {
            Fragment selectedFragment = null;
            switch (navigationSelectedItem) {
                case R.id.navigation_central:
                    selectedFragment = ScannerFragment.newInstance();
                    break;
                case R.id.navigation_peripheral:
                    selectedFragment = PeripheralModeFragment.newInstance();
                    break;
            }

            if (selectedFragment != null) {
                updateActionBarTitle(navigationSelectedItem);

                FragmentManager fragmentManager = getChildFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                if (mCurrentFragmentReference != null) {        // First time don't do any animation
                    boolean isAnimationForward = navigationSelectedItem == R.id.navigation_peripheral;
                    int enterAnimationId, exitAnimationId;
                    if (isAnimationForward) {
                        enterAnimationId = R.anim.slide_in_left;
                        exitAnimationId = R.anim.slide_out_right;
                    } else {
                        enterAnimationId = R.anim.slide_in_right;
                        exitAnimationId = R.anim.slide_out_left;
                    }
                    transaction.setCustomAnimations(enterAnimationId, exitAnimationId, R.anim.slide_in_right, R.anim.slide_out_left);
                }
                transaction.replace(R.id.navigationContentLayout, selectedFragment)
                        .commit();

                mCurrentFragmentReference = new WeakReference<>(selectedFragment);
                selectedFragmentId = navigationSelectedItem;
                isFragmentChanged = true;
            }
        }

        /*
        Context context = getContext();
        if (context != null) {
            // Start or stop advertising when the selected fragment is navigation_peripheral
            if (navigationSelectedItem == R.id.navigation_peripheral) {
                Log.d(TAG, "Start Advertising");
                final boolean result = mPeripheralModeViewModel.start(context.getApplicationContext());
                if (!result) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    AlertDialog dialog = builder.setTitle(R.string.dialog_error).setMessage(R.string.bluetooth_advertising_start_error)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                }
            } else {
                Log.d(TAG, "Stop Advertising");
                mPeripheralModeViewModel.stop(context.getApplicationContext());
            }
        }*/

        return isFragmentChanged;
    }

    private void updateActionBarTitle(int navigationSelectedItem) {
        int titleId = 0;
        switch (navigationSelectedItem) {
            case R.id.navigation_central:
                titleId = R.string.main_tabbar_singlemode;//R.string.main_tabbar_centralmode;
                break;
            case R.id.navigation_peripheral:
                titleId = R.string.main_tabbar_peripheralmode;
                break;
        }
        setActionBarTitle(getString(titleId));
    }

    private void setActionBarTitle(String title) {
        AppCompatActivity activity = ((AppCompatActivity) getActivity());
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setDisplayHomeAsUpEnabled(false);     // Don't show caret for MainFragment
            }
        }
    }

    // endregion

    // region Actions
    void startScanning() {
        // Send the message to the peripheral mode fragment, or ignore it if is not selected
        if (getCurrentFragment() instanceof ScannerFragment) {
            ((ScannerFragment) getCurrentFragment()).startScanning();
        }
    }

    void disconnectAllPeripherals() {
        // Send the message to the peripheral mode fragment, or ignore it if is not selected
        if (getCurrentFragment() instanceof ScannerFragment) {
            ((ScannerFragment) getCurrentFragment()).disconnectAllPeripherals();
        }
    }

    // endregion
}