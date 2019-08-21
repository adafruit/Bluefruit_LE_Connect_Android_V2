package com.adafruit.bluefruit.le.connect.app;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartPacketManager;
import com.adafruit.bluefruit.le.connect.dfu.ProgressFragmentDialog;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.FileHelper;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class ImageTransferFragment extends ConnectedPeripheralFragment implements ImageCropFragment.OnImageCropListener {
    // Log
    private final static String TAG = ImageTransferFragment.class.getSimpleName();

    // Config
    private static final int kPreferredMtuSize = 517;
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
    private final static String kPreferences_withoutResponse = "without_response";

    private Size kDefaultResolution = new Size(64, 64);
    private Size[] kAcceptedResolutions = {
            new Size(4, 4),
            new Size(8, 8),
            new Size(16, 16),
            new Size(32, 32),
            new Size(64, 64),
            new Size(128, 128),
            new Size(128, 160),
            new Size(160, 80),
            new Size(168, 144),
            new Size(212, 104),
            new Size(240, 240),
            new Size(250, 122),
            new Size(256, 256),
            new Size(296, 128),
            new Size(300, 400),
            new Size(320, 240),
            new Size(480, 320),
            new Size(512, 512),
            // new Size(1024, 1024),
    };

    // UI
    private TextView mUartWaitingTextView;
    private ImageView mCameraImageView;
    private Button mResolutionButton;
    private ViewGroup mResolutionViewGroup;
    private ViewGroup mResolutionContainerViewGroup;
    private Button mTransferModeButton;

    // Data
    private UartPacketManager mUartManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private BlePeripheralUart mBlePeripheralUart;
    private Size mResolution;
    private float mImageRotationDegrees;
    private boolean mIsTransformModeWithoutResponse;
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

        // Init Data
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        final int resolutionWidth = preferences.getInt(kPreferences_resolutionWidth, kDefaultResolution.getWidth());
        final int resolutionHeight = preferences.getInt(kPreferences_resolutionHeight, kDefaultResolution.getHeight());
        mResolution = new Size(resolutionWidth, resolutionHeight);
        mIsTransformModeWithoutResponse = preferences.getBoolean(kPreferences_withoutResponse, false);

        // UI
        mUartWaitingTextView = view.findViewById(R.id.uartWaitingTextView);
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
            mIsTransformModeWithoutResponse = !mIsTransformModeWithoutResponse;
            updateTransferModeUI();

            // Save selected mode
            SharedPreferences settings = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(kPreferences_withoutResponse, mIsTransformModeWithoutResponse);
            editor.apply();
        });

        ImageButton rotateLeftButton = view.findViewById(R.id.rotateLeftButton);
        rotateLeftButton.setOnClickListener(v -> {
            final float rotation = (mImageRotationDegrees - 90) % 360;
            updateImage(mResolution, rotation);
        });

        ImageButton rotateRightButton = view.findViewById(R.id.rotateRightButton);
        rotateRightButton.setOnClickListener(v -> {
            final float rotation = (mImageRotationDegrees + 90) % 360;
            updateImage(mResolution, rotation);
        });

        Button sendButton = view.findViewById(R.id.sendButton);
        sendButton.setOnClickListener(view1 -> sendImage(mIsTransformModeWithoutResponse));

        mResolutionContainerViewGroup.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mResolutionContainerViewGroup.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateImage(mResolution, mImageRotationDegrees);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
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
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.imagetransfer_help_title), getString(R.string.imagetransfer_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                                .replace(R.id.contentLayout, helpFragment, "Help");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
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
                WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(mBlePeripheralUart);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
        mTransferModeButton.setText(mIsTransformModeWithoutResponse ? R.string.imagetransfer_transfermode_value_withoutresponse : R.string.imagetransfer_transfermode_value_withresponse);
    }


    private void updateResolutionUI() {
        final String text = String.format(Locale.US, "%d x %d", mResolution.getWidth(), mResolution.getHeight());
        mResolutionButton.setText(text);
    }

    private void updateImage(Size resolution, float rotation) {
        Context context = getContext();
        if (context == null) return;

        mResolution = resolution;
        mImageRotationDegrees = rotation;
        final int width = mResolution.getWidth();
        final int height = mResolution.getHeight();

        // Save selected resolution
        SharedPreferences settings = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(kPreferences_resolutionWidth, width);
        editor.putInt(kPreferences_resolutionHeight, height);
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
            // Bitmap transformedBitmap = getResizedBitmap(mBitmap, width, height);
            Bitmap transformedBitmap = scaleAndRotateImage(mBitmap, mResolution, mImageRotationDegrees, Color.BLACK);

            BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), transformedBitmap);        // Create bitmap drawable to control filtering method
            bitmapDrawable.setFilterBitmap(false);

            mCameraImageView.setImageDrawable(bitmapDrawable);
        }

        //
        updateResolutionUI();
    }

    @SuppressWarnings("SameParameterValue")
    private Bitmap scaleAndRotateImage(Bitmap image, Size resolution, float rotationDegress, int backgroundColor) {
        // Calculate resolution for fitted image
        final float widthRatio = resolution.getWidth() / (float) image.getWidth();
        final float heightRatio = resolution.getHeight() / (float) image.getHeight();

        Size fitResolution;
        if (heightRatio < widthRatio) {
            float width = Math.round((resolution.getHeight() / (float) image.getHeight()) * image.getWidth());
            fitResolution = new Size((int) (width), resolution.getHeight());
        } else {
            float height = Math.round((resolution.getWidth() / (float) image.getWidth()) * image.getHeight());
            fitResolution = new Size(resolution.getWidth(), (int) (height));
        }

        Bitmap fitImage = getResizedBitmap(image, fitResolution.getWidth(), fitResolution.getHeight(), rotationDegress);

        final int x = (resolution.getWidth() - fitImage.getWidth()) / 2;
        final int y = (resolution.getHeight() - fitImage.getHeight()) / 2;

        Bitmap newImage = Bitmap.createBitmap(resolution.getWidth(), resolution.getHeight(), fitImage.getConfig());
        Canvas canvas = new Canvas(newImage);
        canvas.drawColor(backgroundColor);
        canvas.drawBitmap(fitImage, x, y, null);
        return newImage;
    }

    private Bitmap getResizedBitmap(Bitmap bitmap, int newWidth, int newHeight, float rotationDegress) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        matrix.postRotate(rotationDegress);

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }
    /*


        if (bitmap != null) {
            // TODO
            final int width = mResolution.getWidth();
            final int height = mResolution.getHeight();
            Bitmap resizedBitmap = getResizedBitmap(bitmap, width, height);
//                bitmap.recycle();

            // Bitmap resizedBitmap = bitmap;

            setImage(resizedBitmap);
        }
     */


    // endregion

    // region Actions

    private void chooseResolution() {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.imagetransfer_resolution_choose);

        // add a radio button list
        int checkedItem = 0;
        final ArrayAdapter<String> resolutionsAdapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_singlechoice);
        for (int i = 0; i < kAcceptedResolutions.length; i++) {
            Size size = kAcceptedResolutions[i];
            resolutionsAdapter.add(String.format(Locale.US, "%d x %d", size.getWidth(), size.getHeight()));

            if (size.equals(mResolution)) {
                checkedItem = i;
            }
        }

        builder.setSingleChoiceItems(resolutionsAdapter, checkedItem, (dialog, which) -> {
            Size resolution = kAcceptedResolutions[which];
            updateImage(resolution, mImageRotationDegrees);
            dialog.dismiss();
        });
        /*
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });*/

        builder.setNegativeButton(R.string.dialog_cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
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
        updateImage(mResolution, mImageRotationDegrees);
    }
    // endregion

    // region Image Picker

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Context context = getContext();
        if (context == null) return;

        if (requestCode == kActivityRequestCode_pickFromGallery && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri photoUri = data.getData();

                Uri uri;
                String filePath = FileHelper.getPath(context, photoUri);
                if (filePath != null) {
                    uri = Uri.parse("file://" + filePath);
                } else {
                    uri = photoUri;
                }

                cropImage(uri);
            } else {
                Log.w(TAG, "Couldn't pick a photo");
            }
        } else if (requestCode == kActivityRequestCode_takePicture && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Picture taken");

            addPictureToGallery(context, mTemporalPhotoPath);
            Uri photoUri = Uri.parse(mTemporalPhotoPath);

            cropImage(photoUri);
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
        File f = new File(photoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        context.sendBroadcast(mediaScanIntent);
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

        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermissions(permissions, kActivityRequestCode_requestCameraPermission);
            return;
        }

        Toast.makeText(activity, R.string.imagetransfer_cameraneeded, Toast.LENGTH_LONG).show();
    }

    private void requestExternalReadPermission() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        Log.w(TAG, "External read permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            requestPermissions(permissions, kActivityRequestCode_requestReadExternalStoragePermission);
            return;
        }

        Toast.makeText(activity, R.string.imagetransfer_readexternalneeded, Toast.LENGTH_LONG).show();
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
                return;
            }

            Log.e(TAG, "Permission not granted: results len = " + grantResults.length + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.imagetransfer_cameraneeded)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else if (requestCode == kActivityRequestCode_requestReadExternalStoragePermission) {
            if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "External read permission granted - initialize the camera source");
                chooseFromLibrary(context);
                return;
            }

            Log.e(TAG, "Permission not granted: results len = " + grantResults.length + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.imagetransfer_readexternalneeded)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // endregion

    // region Transform Image

    private void cropImage(@NonNull Uri imageUri) {
        if (imageUri.getPath() == null) {
            return;
        }

        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            ImageCropFragment imageCropFragment = ImageCropFragment.newInstance(imageUri.getPath(), mResolution.getWidth(), mResolution.getHeight());
            imageCropFragment.setTargetFragment(ImageTransferFragment.this, 0);

            fragmentManager.beginTransaction()
                    .add(R.id.contentLayout, imageCropFragment)
                    .addToBackStack(ImageCropFragment.TAG)
                    .commitAllowingStateLoss();
        }
    }

    // endregion

    // region Send Image

    private void sendImage(boolean transferWithoutResponse) {
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

        // get 24bits bytes
        final int rgbSize = width * height * 3;
        byte[] rgbBytes = new byte[rgbSize];
        int k = 0;
        for (int i = 0; i < rgbaSize; i++) {
            if (i % 4 != 3) {
                rgbBytes[k++] = rgbaBytes[i];
            }
        }

        rgbaBitmap.recycle();


        // Send command
        ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + 2 + rgbSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // Command: '!I'
        String prefix = "!I";
        buffer.put(prefix.getBytes());
        buffer.putShort((short) width);
        buffer.putShort((short) height);
        buffer.put(rgbBytes);

        byte[] result = buffer.array();

        // Increase MTU packet size
        final int packetWithResponseEveryPacketCount = transferWithoutResponse ? Integer.MAX_VALUE : 0;
        mBlePeripheralUart.requestMtu(kPreferredMtuSize, status1 -> mMainHandler.post(() -> sendCrcData(result, packetWithResponseEveryPacketCount)));          // Note: requestMtu only affects to WriteWithoutResponse

        rgbaBytes = null;
    }

    private void sendCrcData(byte[] data, int packetWithResponseEveryPacketCount) {
        Context context = getContext();
        if (context == null) return;

        if (mUartManager == null) {
            return;
        }

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

        //final int kPacketWithResponseEveryPacketCount = 1;      // Note: don't use a bigger number or it will drop packets for big enough images
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
