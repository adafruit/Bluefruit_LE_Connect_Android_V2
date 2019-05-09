package com.adafruit.bluefruit.le.connect.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;

// helper class with common behaviour for all peripheral modules
public class ConnectedPeripheralFragment extends Fragment {
    // Constants
    @SuppressWarnings("unused")
    private final static String TAG = ConnectedPeripheralFragment.class.getSimpleName();

    // Fragment parameters
    protected static final String ARG_SINGLEPERIPHERALIDENTIFIER = "SinglePeripheralIdentifier";

    // Common interfaces
    public interface SuccessHandler {
        void result(boolean success);
    }

    // Data
    protected BlePeripheral mBlePeripheral;

    // region Fragment Lifecycle
    protected static Bundle createFragmentArgs(@Nullable String singlePeripheralIdentifier) {      // if singlePeripheralIdentifier is null, uses multi-connect
        Bundle args = new Bundle();
        args.putString(ARG_SINGLEPERIPHERALIDENTIFIER, singlePeripheralIdentifier);
        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String singlePeripheralIdentifier = getArguments().getString(ARG_SINGLEPERIPHERALIDENTIFIER);
            mBlePeripheral = BleScanner.getInstance().getPeripheralWithIdentifier(singlePeripheralIdentifier);
        }

        setHasOptionsMenu(true);
    }

    // endregion

    // region Action Bar
    protected void setActionBarTitle(int titleStringId) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(titleStringId);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    // endregion
}
