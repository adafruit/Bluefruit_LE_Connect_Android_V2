package com.adafruit.bluefruit.le.connect.dfu;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.utils.ScreenUtils;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class DfuProgressFragmentDialog extends ProgressFragmentDialog {
    // Log
    private final static String TAG = DfuProgressFragmentDialog.class.getSimpleName();

    // Interface
    public interface Listener {
        void onDeviceDisconnected(final String deviceAddress);

        void onDfuCompleted(final String deviceAddress);

        void onDfuAborted(final String deviceAddress);

        void onError(final String deviceAddress, final int error, final int errorType, final String message);
    }

    // Params
    private final static String kParamBlePeripheralAddress = "blePeripheralAddress";

    // Data
    private String mBlePeripheralAddress;
    private Listener mListener;

    // region Fragment Lifecycle
    public static DfuProgressFragmentDialog newInstance(@NonNull String blePeripheralAddress, @Nullable String message) {
        DfuProgressFragmentDialog fragment = new DfuProgressFragmentDialog();
        Bundle args = new Bundle();
        args.putString(kParamMessage, message);
        args.putString(kParamBlePeripheralAddress, blePeripheralAddress);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle != null) {
            mBlePeripheralAddress = bundle.getString(kParamBlePeripheralAddress);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            mListener = (Listener) context;
        } /*else if (getTargetFragment() instanceof Listener) {
            mListener = (Listener) getTargetFragment();
        } else if (getTargetFragment() != null && getTargetFragment().getActivity() instanceof Listener) {
            mListener = (Listener) getTargetFragment().getActivity();
        } */ else {
            throw new RuntimeException(context.toString() + " must implement Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        assert (mBlePeripheralAddress != null);
        Context context = getContext();
        if (context != null) {
            DfuServiceListenerHelper.registerProgressListener(context, mDfuProgressListener, mBlePeripheralAddress);
        }

        // Keep screen on
        FragmentActivity activity = getActivity();
        if (activity != null) {
            ScreenUtils.keepScreenOn(activity, true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Context context = getContext();
        if (context != null) {
            DfuServiceListenerHelper.unregisterProgressListener(context, mDfuProgressListener);
        }

        // Disable keep screen on
        FragmentActivity activity = getActivity();
        if (activity != null) {
            ScreenUtils.keepScreenOn(activity, false);
        }

    }
    // endregion

    // region DfuProgressListener

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDeviceConnecting");
            setIndeterminate(true);
            setMessage(R.string.dfu_status_connecting);
        }

        @Override
        public void onDeviceConnected(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDeviceConnected");
        }

        @Override
        public void onDfuProcessStarting(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDfuProcessStarting");

            setIndeterminate(true);
            setProgress(0);
            setMessage(R.string.dfu_status_starting);

        }

        @Override
        public void onEnablingDfuMode(@NonNull final String deviceAddress) {
            Log.d(TAG, "onEnablingDfuMode");

            setMessage(R.string.dfu_status_switching_to_dfu);
        }

        @Override
        public void onProgressChanged(@NonNull final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            Context context = getContext();

            //Log.d(TAG, "onProgressChanged: " + percent);
            if (context != null) {
                setIndeterminate(false);
                setProgress(percent);
                if (partsTotal > 1)
                    setMessage(context.getString(R.string.dfu_status_uploading_part, currentPart, partsTotal));
                else
                    setMessage(R.string.dfu_status_uploading);
            }
        }

        @Override
        public void onFirmwareValidating(@NonNull final String deviceAddress) {
            Log.d(TAG, "onFirmwareValidating");
            setMessage(R.string.dfu_status_validating);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            Log.d(TAG, "onDeviceDisconnecting");
            setMessage(R.string.dfu_status_disconnecting);
        }

        @Override
        public void onDeviceDisconnected(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDeviceDisconnected");
            mListener.onDeviceDisconnected(deviceAddress);
        }

        @Override
        public void onDfuCompleted(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDfuCompleted");
            setMessage(R.string.dfu_status_completed);
            mListener.onDfuCompleted(deviceAddress);
        }

        @Override
        public void onDfuAborted(@NonNull final String deviceAddress) {
            Log.d(TAG, "onDfuAborted");
            setMessage(R.string.dfu_status_aborted);
            mListener.onDfuAborted(deviceAddress);
        }

        @Override
        public void onError(@NonNull final String deviceAddress, final int error, final int errorType, final String message) {
            Log.d(TAG, "onError: " + message);
            if (mListener != null) {
                mListener.onError(deviceAddress, error, errorType, message);
            } else {
                Log.w(TAG, "onError with no listener");
            }
        }
    };

    // endregion
}
