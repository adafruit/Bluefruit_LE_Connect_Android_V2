package com.adafruit.bluefruit.le.connect.dfu;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;

public class ProgressFragmentDialog extends AppCompatDialogFragment {
    // Params
    final static String kParamMessage = "message";

    // UI
    private ProgressDialog mDialog;
    private DialogInterface.OnCancelListener mCancelListener;

    // Data
    private String mMessage;
    private int mProgress;
    private boolean mIndeterminate;

    // region Fragment Lifecycle
    public static ProgressFragmentDialog newInstance(@Nullable String message) {
        ProgressFragmentDialog fragment = new ProgressFragmentDialog();
        Bundle args = new Bundle();
        args.putString(kParamMessage, message);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);

        Bundle bundle = getArguments();
        if (bundle != null) {
            mMessage = bundle.getString(kParamMessage);
        }

        mDialog = new ProgressDialog(getActivity());
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setMax(100);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setCancelable(true);

        updateUI();

        return mDialog;
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();

        // Work around bug: http://code.google.com/p/android/issues/detail?id=17423
        if ((dialog != null) && getRetainInstance())
            dialog.setDismissMessage(null);

        super.onDestroyView();
    }

    @Override
    public void onCancel(DialogInterface dialog) {          // to avoid problems with setting oncancellistener after dialog has been created
        if (mCancelListener != null) {      // TODO get mCancelListener with onAttach NOT HERE to avoid losing it on recreation
            mCancelListener.onCancel(dialog);
        }

        super.onCancel(dialog);
    }

    // endregion

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        mCancelListener = listener;
    }

    /*
    public ProgressDialog getDialog() {
        return mDialog;
    }
    */

    public void setMessage(int messageId) {
        Context context = getContext();
        if (context != null) {
            setMessage(context.getString(messageId));
        }
    }

    public void setMessage(String message) {
        mMessage = message;
        mDialog.setMessage(message);
    }

    public void setProgress(int progress) {
        mProgress = progress;
        mDialog.setProgress(progress);
    }

    public void setIndeterminate(boolean indeterminate) {
        mIndeterminate = indeterminate;
        mDialog.setIndeterminate(indeterminate);
    }

    private void updateUI() {
        mDialog.setMessage(mMessage);
        mDialog.setProgress(mProgress);
        mDialog.setIndeterminate(mIndeterminate);
    }
}