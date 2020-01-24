package com.adafruit.bluefruit.le.connect.app.imagetransfer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.CommonHelpFragment;
import com.adafruit.bluefruit.le.connect.app.ConnectedPeripheralFragment;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartPacketManager;
import com.adafruit.bluefruit.le.connect.dfu.ProgressFragmentDialog;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.ImageMagickUtils;
import com.adafruit.bluefruit.le.connect.utils.ImageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class ImageTransferFragment extends ConnectedPeripheralFragment implements ImageCropFragment.OnImageCropListener, ImageTransferFormatSelectorDialogFragment.FormatSelectorListener {
    // Log
    private final static String TAG = ImageTransferFragment.class.getSimpleName();

    // Config
    private static final boolean kShowInterleaveControls = true;
    private static final int kDefaultInterlavedWithoutResponseCount = 50;
    private static final String kAuthorityField = ".fileprovider";          // Same as the authority field on the manifest provider

    // Constants
    private static final int kActivityRequestCode_pickFromGallery = 1;
    private static final int kActivityRequestCode_takePicture = 2;
    private static final int kActivityRequestCode_cropPicture = 3;
    private static final int kActivityRequestCode_requestCameraPermission = 4;
    private static final int kActivityRequestCode_requestReadExternalStoragePermission = 5;

    private final static String kPreferences = "ImageTransferFragment_prefs";
    private final static String kPreferences_resolutionWidth = "resolution_width";
    private final static String kPreferences_resolutionHeight = "resolution_height";
    private final static String kPreferences_interleavedWithoutResponseCount = "interleaved_withoutresponse_count";
    private final static String kPreferences_isColorSpace24Bits = "is_color_space_24_bits";
    private final static String kPreferences_isEInkModeEnabled = "is_eink_mode_enabled";

    private Size kDefaultResolution = new Size(64, 64);

    // UI
    private TextView mUartWaitingTextView;
    private ImageView mCameraImageView;
    private Button mResolutionButton;
    private ViewGroup mResolutionViewGroup;
    private ViewGroup mResolutionContainerViewGroup;
    private Button mTransferModeButton;
    private Button mColorSpaceButton;

    // Data
    private UartPacketManager mUartManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private BlePeripheralUart mBlePeripheralUart;
    private Size mResolution;
    private float mImageRotationDegrees;
    private int mInterleavedWithoutResponseCount;
    private boolean mIsColorSpace24Bits;
    private boolean mIsEInkModeEnabled;
    private Bitmap mBitmap;
    private ProgressFragmentDialog mProgressDialog;

    // Data - photo
    private String mTemporalPhotoPath;

    public static ImageTransferFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        ImageTransferFragment fragment = new ImageTransferFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public ImageTransferFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_imagetransfer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();
        if (context == null) return;

        // Update ActionBar
        setActionBarTitle(R.string.imagetransfer_tab_title);


        // set image cache temp directory in ImageMagick:
        FragmentActivity activity = getActivity();
        if (activity != null) {
            ImageMagickUtils.setCacheDir(activity);
        }

        // Init Data
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        final int resolutionWidth = preferences.getInt(kPreferences_resolutionWidth, kDefaultResolution.getWidth());
        final int resolutionHeight = preferences.getInt(kPreferences_resolutionHeight, kDefaultResolution.getHeight());
        mResolution = new Size(resolutionWidth, resolutionHeight);
        mInterleavedWithoutResponseCount = preferences.getInt(kPreferences_interleavedWithoutResponseCount, kDefaultInterlavedWithoutResponseCount);
        mIsColorSpace24Bits = preferences.getBoolean(kPreferences_isColorSpace24Bits, false);
        mIsEInkModeEnabled = preferences.getBoolean(kPreferences_isEInkModeEnabled, false);

        // UI
        mUartWaitingTextView = view.findViewById(R.id.uartWaitingTextView);
        mUartWaitingTextView.setVisibility(mBlePeripheralUart != null && mBlePeripheralUart.isUartEnabled() ? View.GONE : View.VISIBLE);

        mCameraImageView = view.findViewById(R.id.cameraImageView);
        mCameraImageView.setImageBitmap(null);      // Clear any image
        mResolutionViewGroup = view.findViewById(R.id.resolutionViewGroup);
        mResolutionContainerViewGroup = view.findViewById(R.id.resolutionContainerViewGroup);

        mResolutionButton = view.findViewById(R.id.resolutionButton);
        mResolutionButton.setOnClickListener(v -> chooseResolution());
        Button imageButton = view.findViewById(R.id.imageButton);
        imageButton.setOnClickListener(v -> chooseImage());

        mTransferModeButton = view.findViewById(R.id.transferModeButton);
        mTransferModeButton.setOnClickListener(v -> {
            SharedPreferences settings = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();

            if (kShowInterleaveControls) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.imagetransfer_transfermode_title);

                String[] items = {getString(R.string.imagetransfer_transfermode_value_withoutresponse), getString(R.string.imagetransfer_transfermode_value_withresponse), getString(R.string.imagetransfer_transfermode_value_interleaved)};

                builder.setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            mInterleavedWithoutResponseCount = Integer.MAX_VALUE;
                            updateTransferModeUI();

                            // Save selected mode
                            editor.putInt(kPreferences_interleavedWithoutResponseCount, mInterleavedWithoutResponseCount);
                            editor.apply();
                            break;

                        case 1:
                            mInterleavedWithoutResponseCount = 0;
                            updateTransferModeUI();

                            // Save selected mode
                            editor.putInt(kPreferences_interleavedWithoutResponseCount, mInterleavedWithoutResponseCount);
                            editor.apply();
                            break;

                        case 2: {

                            AlertDialog.Builder alert = new AlertDialog.Builder(context);
                            alert.setTitle(R.string.imagetransfer_transfermode_interleavedcount_title);
                            alert.setMessage(R.string.imagetransfer_transfermode_interleavedcount_message);
                            final EditText input = new EditText(context);
                            input.setHint(R.string.imagetransfer_transfermode_interleavedcount_hint);
                            input.setInputType(InputType.TYPE_CLASS_NUMBER);
                            input.setRawInputType(Configuration.KEYBOARD_12KEY);

                            // Add horizontal margin (https://stackoverflow.com/questions/27774414/add-bigger-margin-to-edittext-in-android-alertdialog)
                            FrameLayout container = new FrameLayout(context);
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.leftMargin = getResources().getDimensionPixelSize(R.dimen.alertview_embedded_edittext_horizontalmargin);
                            params.rightMargin = params.leftMargin;
                            input.setLayoutParams(params);
                            container.addView(input);

                            alert.setView(container);
                            alert.setPositiveButton(R.string.dialog_ok, (dialog2, whichButton) -> {
                                String valueString = String.valueOf(input.getText());
                                int value = 0;
                                try {
                                    value = Integer.parseInt(valueString);
                                } catch (Exception e) {
                                    Log.d(TAG, "Cannot parse value");
                                }


                                // Set selected value
                                mInterleavedWithoutResponseCount = value;
                                updateTransferModeUI();
                                editor.putInt(kPreferences_interleavedWithoutResponseCount, mInterleavedWithoutResponseCount);
                                editor.apply();

                            });
                            alert.setNegativeButton(android.R.string.cancel, (dialog2, whichButton) -> {
                            });
                            alert.show();

                            break;
                        }
                    }
                    dialog.dismiss();
                });

                builder.setNegativeButton(R.string.dialog_cancel, null);

                AlertDialog dialog = builder.create();
                dialog.show();

            } else {
                mInterleavedWithoutResponseCount = mInterleavedWithoutResponseCount == 0 ? Integer.MAX_VALUE : 0;
                updateTransferModeUI();

                // Save selected mode
                editor.putInt(kPreferences_interleavedWithoutResponseCount, mInterleavedWithoutResponseCount);
                editor.apply();
            }


        });

        mColorSpaceButton = view.findViewById(R.id.colorSpaceButton);
        updateColorSpaceUI();
        mColorSpaceButton.setOnClickListener(v -> {
            mIsColorSpace24Bits = !mIsColorSpace24Bits;
            updateColorSpaceUI();

            // Save to preferences
            SharedPreferences settings = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(kPreferences_isColorSpace24Bits, mIsColorSpace24Bits);
            editor.apply();
        });

        ImageButton rotateLeftButton = view.findViewById(R.id.rotateLeftButton);
        rotateLeftButton.setOnClickListener(v -> {
            final float rotation = (mImageRotationDegrees - 90) % 360;
            updateImage(mResolution, mIsEInkModeEnabled, rotation);
        });

        ImageButton rotateRightButton = view.findViewById(R.id.rotateRightButton);
        rotateRightButton.setOnClickListener(v -> {
            final float rotation = (mImageRotationDegrees + 90) % 360;
            updateImage(mResolution, mIsEInkModeEnabled, rotation);
        });

        Button sendButton = view.findViewById(R.id.sendButton);
        sendButton.setOnClickListener(view1 -> sendImage(mInterleavedWithoutResponseCount, mIsColorSpace24Bits));

        mResolutionContainerViewGroup.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mResolutionContainerViewGroup.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateImage(mResolution, mIsEInkModeEnabled, mImageRotationDegrees);
            }
        });

        updateTransferModeUI();

        // Setup
        if (mUartManager == null) {      // Don't setup if already init (because fragment was recreated)
            // UartManager
            mUartManager = new UartPacketManager(context, null, false, null);
            start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stop();
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
                    CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.imagetransfer_help_title), getString(R.string.imagetransfer_help_text));
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                            .replace(R.id.contentLayout, helpFragment, "Help");
                    fragmentTransaction.addToBackStack(null);
                    fragmentTransaction.commit();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    // region Uart
    private void start() {
        Log.d(TAG, "ImageTransfer start");

        // Enable Uart
        mBlePeripheralUart = new BlePeripheralUart(mBlePeripheral);
        mBlePeripheralUart.uartEnable(mUartManager, status -> mMainHandler.post(() -> {
            mUartWaitingTextView.setVisibility(status == BluetoothGatt.GATT_SUCCESS ? View.GONE : View.VISIBLE);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Done
                Log.d(TAG, "Uart enabled");

                // Set the default image
                Context context = getContext();
                if (context != null) {
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.imagetransfer_default);
                    setImage(bitmap);
                }

            } else {
                Log.d(TAG, "Uart error");
                Context context = getContext();
                if (context != null) {
                    WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(mBlePeripheralUart);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                if (strongBlePeripheralUart != null) {
                                    strongBlePeripheralUart.disconnect();
                                }
                            })
                            .show();
                    DialogUtils.keepDialogOnOrientationChanges(dialog);
                }
            }
        }));
    }

    private void stop() {
        Log.d(TAG, "ImageTransfer stop");
        dismissProgressDialog();
        mBlePeripheral.reset();
        mBlePeripheralUart = null;
    }

    // endregion

    // region UI
    private void updateTransferModeUI() {
        String text;

        if (mInterleavedWithoutResponseCount == 0) {
            text = getString(R.string.imagetransfer_transfermode_value_withresponse);
        } else if (mInterleavedWithoutResponseCount == Integer.MAX_VALUE) {
            text = getString(R.string.imagetransfer_transfermode_value_withoutresponse);

        } else {
            text = String.format(getString(R.string.imagetransfer_transfermode_value_interleaved_format), mInterleavedWithoutResponseCount);
        }

        mTransferModeButton.setText(text);
    }

    private void updateColorSpaceUI() {
        mColorSpaceButton.setText(mIsColorSpace24Bits ? R.string.imagetransfer_colorspace_24bit : R.string.imagetransfer_colorspace_16bit);
    }

    private void updateResolutionUI() {
        final String format = mIsEInkModeEnabled ? getString(R.string.imagetransfer_resolution_einkprefix) + " " + "%d x %d" : "%d x %d";
        final String text = String.format(Locale.US, format, mResolution.getWidth(), mResolution.getHeight());
        mResolutionButton.setText(text);
    }

    private void updateImage(Size resolution, boolean isEInkModeEnabled, float rotation) {
        Context context = getContext();
        if (context == null) return;

        mResolution = resolution;
        mIsEInkModeEnabled = isEInkModeEnabled;
        mImageRotationDegrees = rotation;
        final int width = mResolution.getWidth();
        final int height = mResolution.getHeight();

        // Save selected resolution
        SharedPreferences settings = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(kPreferences_resolutionWidth, width);
        editor.putInt(kPreferences_resolutionHeight, height);
        editor.putBoolean(kPreferences_isEInkModeEnabled, isEInkModeEnabled);
        editor.apply();

        // Change UI to adjust aspect ratio of the displayed image
        final float resolutionAspectRatio = resolution.getWidth() / (float) resolution.getHeight();

        final int maxWidth = mResolutionContainerViewGroup.getWidth();
        final int maxHeight = mResolutionContainerViewGroup.getHeight();

        RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) mResolutionViewGroup.getLayoutParams();
        if (maxWidth > maxHeight * resolutionAspectRatio) {
            relativeLayoutParams.height = maxHeight;//(int) MetricsUtils.convertPixelsToDp(context, maxHeight);
            relativeLayoutParams.width = (int) (relativeLayoutParams.height * resolutionAspectRatio);
        } else {
            relativeLayoutParams.width = maxWidth;//(int) MetricsUtils.convertPixelsToDp(context, maxWidth);
            relativeLayoutParams.height = (int) (relativeLayoutParams.width * (1.f / resolutionAspectRatio));

        }
        mResolutionViewGroup.requestLayout();

        // Calculate transformed image
        if (mBitmap != null) {
            Bitmap transformedBitmap = ImageUtils.scaleAndRotateImage(mBitmap, mResolution, mImageRotationDegrees, Color.BLACK);

            if (isEInkModeEnabled) {
                transformedBitmap = ImageUtils.applyEInkModeToImage(context, transformedBitmap);
            }

            BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), transformedBitmap);        // Create bitmap drawable to control filtering method
            bitmapDrawable.setFilterBitmap(false);

            mCameraImageView.setImageDrawable(bitmapDrawable);
        }

        //
        updateResolutionUI();
    }


    // endregion

    // region Actions

    private void chooseResolution() {
        Context context = getContext();
        if (context == null) return;

        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) return;

        ImageTransferFormatSelectorDialogFragment dialogFragment = ImageTransferFormatSelectorDialogFragment.newInstance(mIsEInkModeEnabled, mResolution);
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(fragmentManager, ImageTransferFormatSelectorDialogFragment.class.getSimpleName());
    }

    private void chooseImage() {
        Context context = getContext();
        if (context == null) return;

        PackageManager packageManager = context.getPackageManager();
        final boolean hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);

        boolean isCameraAvailable = false;
        if (hasCamera) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                isCameraAvailable = true;
            }
        }

        // Show image picker choices
        String[] imageChoices;
        if (isCameraAvailable) {
            imageChoices = new String[]{getString(R.string.imagetransfer_imagepicker_camera), getString(R.string.imagetransfer_imagepicker_photolibrary)};
        } else {
            imageChoices = new String[]{getString(R.string.imagetransfer_imagepicker_photolibrary)};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        boolean finalIsCameraAvailable = isCameraAvailable;
        builder.setTitle(R.string.imagetransfer_imageorigin_choose)
                .setItems(imageChoices, (dialog, which) -> {
                    boolean isCameraSelected = which == 0 && finalIsCameraAvailable;

                    if (isCameraSelected) {     // Get image from camera
                        chooseFromCameraAskingPermissionIfNeeded(context);
                    } else {            // Get image from gallery
                        chooseFromLibraryAskingPermissionIfNeeded(context);
                    }
                });
        builder.show();
    }

    private void chooseFromCameraAskingPermissionIfNeeded(@NonNull Context context) {
        int rc = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            chooseFromCamera(context);
        } else {
            requestCameraPermission();
        }
    }

    private void chooseFromCamera(@NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(packageManager) != null) {

            File photoFile = null;
            try {
                photoFile = createImageFile(context);
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.w(TAG, "Could not create file to save picture");
                new AlertDialog.Builder(context)
                        .setMessage(R.string.imagetransfer_cameranotavailable)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                final String authority = context.getApplicationContext().getPackageName() + kAuthorityField;
                Uri photoUri = FileProvider.getUriForFile(context.getApplicationContext(), authority, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                Log.d(TAG, "Start takePictureIntent");
                startActivityForResult(takePictureIntent, kActivityRequestCode_takePicture);
                Log.d(TAG, "Started takePictureIntent");
            }

        } else {
            Log.w(TAG, "Image capture not available");
        }
    }

    private void chooseFromLibraryAskingPermissionIfNeeded(@NonNull Context context) {
        int rc = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            chooseFromLibrary(context);
        } else {
            requestExternalReadPermission();
        }
    }

    private void chooseFromLibrary(@NonNull Context context) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        Intent pickPictureIntent = Intent.createChooser(intent, getString(R.string.imagetransfer_imageorigin_choose));
        if (pickPictureIntent.resolveActivity(context.getPackageManager()) != null) {
            startActivityForResult(pickPictureIntent, kActivityRequestCode_pickFromGallery);
        } else {
            Log.w(TAG, "There is no photo picker available");
        }
    }

    private void setImage(Bitmap bitmap) {
        mImageRotationDegrees = 0;      // reset rotation
        mBitmap = bitmap;
        updateImage(mResolution, mIsEInkModeEnabled, mImageRotationDegrees);
    }
    // endregion

    // region FormatSelectorListener
    @Override
    public void onResolutionSelected(Size resolution, boolean isEInkMode) {
        Log.d(TAG, "Resolution selected: " + resolution.getWidth() + ", " + resolution.getHeight() + " isEInk: " + isEInkMode);

        updateImage(resolution, isEInkMode, mImageRotationDegrees);
    }
    // endregion

    // region Image Picker

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Context context = getContext();
        if (context == null) return;

        if (requestCode == kActivityRequestCode_pickFromGallery && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {

                // Copy image to temporary file
                try {
                    InputStream input = context.getContentResolver().openInputStream(data.getData());
                    if (input != null) {
                        final File temporaryFile = File.createTempFile("imagetransfer_picture", null);
                        temporaryFile.deleteOnExit();
                        try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
                            byte[] buffer = new byte[4 * 1024];
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                output.write(buffer, 0, read);
                            }

                            output.flush();
                        }

                        cropImage(temporaryFile.getPath());
                    }

                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Error opening image: " + e);
                } catch (IOException e) {
                    Log.e(TAG, "Error creating temporary image: " + e);
                }

            } else {
                Log.w(TAG, "Couldn't pick a photo");
            }
        } else if (requestCode == kActivityRequestCode_takePicture && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Picture taken");

            addPictureToGallery(context, mTemporalPhotoPath);
            Uri photoUri = Uri.parse(mTemporalPhotoPath);
            String photoPath = photoUri.getPath();
            if (photoPath != null) {
                cropImage(photoPath);
            }
        } else if (requestCode == kActivityRequestCode_cropPicture && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "kActivityRequestCode_cropPicture");
        }

        super.onActivityResult(requestCode, resultCode, data);

    }

    private File createImageFile(@NonNull Context context) throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);

        // Save a file: path for use with ACTION_VIEW intents
        mTemporalPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }

    private void addPictureToGallery(@NonNull Context context, @NonNull String photoPath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        try {
            File f = new File(photoPath);
            Uri contentUri = Uri.fromFile(f);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
        } catch (NullPointerException e) {
            Log.e(TAG, "Error opening file: " + photoPath);
        }
    }
    // endregion


    // region Permissions
    private void requestCameraPermission() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        requestPermissions(permissions, kActivityRequestCode_requestCameraPermission);

        /* No need to explain rationale
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermissions(permissions, kActivityRequestCode_requestCameraPermission);
            return;
        }
        Toast.makeText(activity, R.string.imagetransfer_cameraneeded, Toast.LENGTH_LONG).show();
         */
    }

    private void requestExternalReadPermission() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        Log.w(TAG, "External read permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        requestPermissions(permissions, kActivityRequestCode_requestReadExternalStoragePermission);

        /* No need to explain rationale
        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            requestPermissions(permissions, kActivityRequestCode_requestReadExternalStoragePermission);
            return;
        }
        Toast.makeText(activity, R.string.imagetransfer_readexternalneeded, Toast.LENGTH_LONG).show();
        */
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Context context = getContext();
        if (context == null) return;

        if (requestCode == kActivityRequestCode_requestCameraPermission) {
            if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted - initialize the camera source");
                chooseFromCamera(context);
            } else {

                Log.e(TAG, "Permission not granted: results len = " + grantResults.length + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.imagetransfer_cameraneeded)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        } else if (requestCode == kActivityRequestCode_requestReadExternalStoragePermission) {
            if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "External read permission granted");
                chooseFromLibrary(context);
            } else {
                Log.e(TAG, "Permission not granted: results len = " + grantResults.length + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.imagetransfer_readexternalneeded)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        } else {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // endregion

    // region Transform Image

    private void cropImage(@NonNull String imagePath) {
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            ImageCropFragment imageCropFragment = ImageCropFragment.newInstance(imagePath, mResolution.getWidth(), mResolution.getHeight());
            imageCropFragment.setTargetFragment(ImageTransferFragment.this, 0);

            fragmentManager.beginTransaction()
                    .add(R.id.contentLayout, imageCropFragment)
                    .addToBackStack(ImageCropFragment.TAG)
                    .commitAllowingStateLoss();
        }
    }

    // endregion

    // region Send Image

    private void sendImage(int packetWithResponseEveryPacketCount, boolean isColorSpace24Bits) {
        Bitmap bitmap = ((BitmapDrawable) mCameraImageView.getDrawable()).getBitmap();
        //Bitmap bitmap = mBitmapDrawable.getBitmap();

        // Create 32bits
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        Bitmap rgbaBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rgbaBitmap);
        Paint paint = new Paint();
        canvas.drawBitmap(bitmap, 0, 0, paint);

        // get bytes
        final int rgbaSize = rgbaBitmap.getRowBytes() * rgbaBitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocate(rgbaSize);
        bitmap.copyPixelsToBuffer(byteBuffer);
        byte[] rgbaBytes = byteBuffer.array();

        //bitmap.recycle();
        byteBuffer = null;

        int rgbSize;
        byte[] rgbBytes;
        if (isColorSpace24Bits) {
            // Convert 32bit color data to 24bit (888)
            rgbSize = width * height * 3;
            rgbBytes = new byte[rgbSize];
            int k = 0;
            for (int i = 0; i < rgbaSize; i++) {
                if (i % 4 != 3) {
                    rgbBytes[k++] = rgbaBytes[i];
                }
            }
        } else {
            // Convert 32bit color data to 16bit (565)
            byte r = 0, g = 0;
            rgbSize = width * height * 2;
            rgbBytes = new byte[rgbSize];
            int k = 0;
            for (int i = 0; i < rgbaSize; i++) {
                int j = i % 4;
                if (j == 0) {
                    r = rgbaBytes[i];
                } else if (j == 1) {
                    g = rgbaBytes[i];

                } else if (j == 2) {
                    byte b = rgbaBytes[i];

                    int rShort = (r & 0xF8) & 0xffff;
                    int gShort = (g & 0xFC) & 0xffff;
                    int bShort = b & 0xff;
                    int rgb16 = (rShort << 8) | (gShort << 3) | (bShort >>> 3);

                    byte high = (byte) ((rgb16 >> 8) & 0xff);
                    byte low = (byte) (rgb16 & 0xff);

                    // Add as little endian
                    rgbBytes[k++] = low;
                    rgbBytes[k++] = high;
                }
            }
        }

        rgbaBitmap.recycle();

        // Send command
        ByteBuffer buffer = ByteBuffer.allocate(2 + 1 + 2 + 2 + rgbSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // Command: '!I'
        String prefix = "!I";
        buffer.put(prefix.getBytes());
        buffer.put((byte) (isColorSpace24Bits ? 24 : 16));
        buffer.putShort((short) width);
        buffer.putShort((short) height);
        buffer.put(rgbBytes);

        byte[] result = buffer.array();

        sendCrcData(result, packetWithResponseEveryPacketCount);

        rgbaBytes = null;
    }

    private void sendCrcData(byte[] data, int packetWithResponseEveryPacketCount) {
        Context context = getContext();
        if (context == null) return;

        if (mUartManager == null) {
            return;
        }

        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBlePeripheral.readPhy();
        }*/

        // Progress fragment
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            dismissProgressDialog();

            mProgressDialog = ProgressFragmentDialog.newInstance(context.getString(R.string.imagetransfer_transferring));
            mProgressDialog.show(fragmentManager, null);
            fragmentManager.executePendingTransactions();

            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setOnCancelListener(dialog -> {
                cancelCurrentSendCommand();
                dismissProgressDialog();
            });
        }

        final byte[] crcData = BlePeripheralUart.appendCrc(data);

        mUartManager.sendEachPacketSequentially(mBlePeripheralUart, crcData, packetWithResponseEveryPacketCount, progress -> {
            if (mProgressDialog != null) {
                //Log.d(TAG, "progress: " + ((int) (progress * 100)));
                mProgressDialog.setProgress((int) (progress * 100));
            }
        }, status -> {
            dismissProgressDialog();

            if (status != BluetoothGatt.GATT_SUCCESS) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                AlertDialog dialog = builder.setMessage(R.string.imagetransfer_senddata_error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
            }
        });
    }

    // endregion

    // region Progress

    private void cancelCurrentSendCommand() {
        if (mBlePeripheralUart != null) {     // mBlePeripheralUart could be null if the peripheral disconnected
            mUartManager.cancelOngoingSendPacketSequentiallyInThread(mBlePeripheralUart);
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
    // endregion

    // region ImageCropFragment
    public void onCropFinished(Bitmap bitmap) {
        Log.d(TAG, "onCropFinished");
        setImage(bitmap);

    }
    // endregion
}
