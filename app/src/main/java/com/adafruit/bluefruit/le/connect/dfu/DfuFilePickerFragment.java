package com.adafruit.bluefruit.le.connect.dfu;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;

import static android.app.Activity.RESULT_OK;

public class DfuFilePickerFragment extends AppCompatDialogFragment {
    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_SelectFile_Hex = 0;
    private static final int kActivityRequestCode_SelectFile_Ini = 1;

    // Interface
    public interface OnFragmentInteractionListener {
        void onDfuFilePickerStartUpdate(@NonNull ReleasesParser.BasicVersionInfo versionInfo);

        void onDfuFilePickerDismissed();
    }

    // UI
    private String mMessage;
    private TextView mHexTextView;
    private TextView mIniTextView;
    private AlertDialog mDialog;

    // Data
    private ReleasesParser.BasicVersionInfo mVersionInfo = new ReleasesParser.BasicVersionInfo();
    private OnFragmentInteractionListener mListener;


    public DfuFilePickerFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static DfuFilePickerFragment newInstance() {
        DfuFilePickerFragment fragment = new DfuFilePickerFragment();
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);

        Context context = getContext();
        if (context != null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (inflater != null) {
                @SuppressLint("InflateParams") View contentView = inflater.inflate(R.layout.fragment_dfu_filepicker, null);
                mHexTextView = contentView.findViewById(R.id.hexFileTextView);
                mIniTextView = contentView.findViewById(R.id.iniFileTextView);

                Button hexChooseButton = contentView.findViewById(R.id.hexChooseButton);
                hexChooseButton.setOnClickListener(view -> openFileChooser(kActivityRequestCode_SelectFile_Hex));
                Button initChooseButton = contentView.findViewById(R.id.initChooseButton);
                initChooseButton.setOnClickListener(view -> openFileChooser(kActivityRequestCode_SelectFile_Ini));

                Bundle bundle = getArguments();
                if (bundle != null) {
                    mMessage = bundle.getString("message");
                    mVersionInfo.fileType = bundle.getInt("fileType");
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setView(contentView);
                builder.setMessage(mMessage)
                        .setPositiveButton(R.string.dfu_pickfiles_update_action, (dialog, id) -> {
                            if (mVersionInfo.hexFileUrl != null) {
                                mListener.onDfuFilePickerStartUpdate(mVersionInfo);
                            } else {
                                Toast.makeText(getActivity(), R.string.dfu_pickfiles_error_hexmissing, Toast.LENGTH_LONG).show();
                                mListener.onDfuFilePickerDismissed();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, id) -> mListener.onDfuFilePickerDismissed());
                mDialog = builder.create();

                updateUI();
            }
        }

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
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else if (getTargetFragment() instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement OnImageCropListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();

            if (requestCode == kActivityRequestCode_SelectFile_Hex) {
                mVersionInfo.hexFileUrl = uri;
            } else if (requestCode == kActivityRequestCode_SelectFile_Ini) {
                mVersionInfo.iniFileUrl = uri;
            }
            updateUI();
        }
    }

    private void updateUI() {
        Context context = getContext();
        if (context != null) {
            String hexName = getFilenameFromUri(context, mVersionInfo.hexFileUrl);
            mHexTextView.setText(hexName);
            String iniName = getFilenameFromUri(context, mVersionInfo.iniFileUrl);
            mIniTextView.setText(iniName);
        }
    }

    private String getFilenameFromUri(@NonNull Context context, @Nullable Uri uri) {
        String result = null;

        // Based on: https://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content
        if (uri != null) {
            String scheme = uri.getScheme();
            if (scheme != null && scheme.equals("content")) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                if (result != null) {
                    int cut = result.lastIndexOf('/');
                    if (cut != -1) {
                        result = result.substring(cut + 1);
                    }
                }
            }
        }

        return result;
    }

    // region FileExplorer

    private void openFileChooser(int operationId) {

        Context context = getContext();
        if (context == null) {
            return;
        }

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        /*
        final int fileType = DfuService.TYPE_APPLICATION;
        String types = fileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM;
        types += ";application/mac-binhex";    // hex is recognized as this mimetype (for dropbox)
        */
        intent.setType("*/*");      // Everything to avoid problems with GoogleDrive

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            // file browser has been found on the device
            startActivityForResult(intent, operationId);
        } else {
            // Alert user that no file browser app has been found on the device
            new AlertDialog.Builder(context)
                    .setTitle(R.string.dfu_pickfiles_error_noexplorer_title)
                    .setMessage(R.string.dfu_pickfiles_error_noexplorer_message)
                    .setCancelable(true)
                    .show();
        }
    }

    // endregion
}
