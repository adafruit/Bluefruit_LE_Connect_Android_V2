package com.adafruit.bluefruit.le.connect.app;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class ThermalCameraFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = ThermalCameraFragment.class.getSimpleName();

    // Config
    private final static float kColdHue = 270;         // Hue for coldest color
    private final static float kHotHue = 0;            // Hue for hottest color

    // UI
    private TextView mUartWaitingTextView;
    private TextView mLowerTempTextView;
    private TextView mUpperTempTextView;
    private ImageView mCameraImageView;
    private ThermalGradientView mThermalScaleView;

    // Data
    private UartDataManager mUartDataManager;
    private BlePeripheralUart mBlePeripheralUart;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mIsColorEnabled = true;
    private boolean mIsFilterEnabled = true;
    private String mTextCachedBuffer = "";
    private Semaphore mTextCachedBufferSemaphore = new Semaphore(1, true);
    private float mMinTemperature = Float.MAX_VALUE;
    private float mMaxTemperature = -Float.MAX_VALUE;

    // region Fragment Lifecycle
    public static ThermalCameraFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        ThermalCameraFragment fragment = new ThermalCameraFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public ThermalCameraFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_thermalcamera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.thermalcamera_tab_title);

        // UI
        mUartWaitingTextView = view.findViewById(R.id.uartWaitingTextView);
        RadioGroup colorModeRadioGroup = view.findViewById(R.id.colorModeRadioGroup);
        colorModeRadioGroup.check(mIsColorEnabled ? R.id.colorModeColorButton : R.id.colorModeMonochromeButton);
        colorModeRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
            mIsColorEnabled = i == R.id.colorModeColorButton;
            mThermalScaleView.updateGradient();
        });

        RadioGroup magnificationRadioGroup = view.findViewById(R.id.magnificationRadioGroup);
        magnificationRadioGroup.check(mIsFilterEnabled ? R.id.magnificationFilteredButton : R.id.magnificationPixelatedButton);
        magnificationRadioGroup.setOnCheckedChangeListener((radioGroup, i) -> mIsFilterEnabled = i == R.id.magnificationFilteredButton);

        mCameraImageView = view.findViewById(R.id.cameraImageView);
        mLowerTempTextView = view.findViewById(R.id.lowerTempTextView);
        mUpperTempTextView = view.findViewById(R.id.upperTempTextView);
        mThermalScaleView = view.findViewById(R.id.thermalScaleView);
        mThermalScaleView.setThermalCameraFragment(this);

        // Setup
        Context context = getContext();
        if (context != null) {
            mUartDataManager = new UartDataManager(context, this, true);
            setupUart();
        }
    }

    @Override
    public void onDestroy() {
        if (mUartDataManager != null) {
            Context context = getContext();
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        if (mBlePeripheralUart != null) {
            mBlePeripheralUart.uartDisable();
        }
        mBlePeripheralUart = null;
        super.onDestroy();
    }

    /*
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.thermalcamera_help_title), getString(R.string.thermalcamera_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
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
    */
    // endregion

    // region Process
    private boolean isTemperatureReadReceived() {
        return mMinTemperature < Float.MAX_VALUE && mMaxTemperature > -Float.MAX_VALUE;
    }

    private void processBuffer(String dataString) {
        try {
            mTextCachedBufferSemaphore.acquire();
        } catch (InterruptedException e) {
            Log.w(TAG, "InterruptedException: " + e.toString());
        }

        mTextCachedBuffer += dataString;

        boolean finished = false;
        do {
            final int startOffset = mTextCachedBuffer.indexOf('[');
            final int endOffset = mTextCachedBuffer.indexOf(']');
            if (startOffset >= 0 && endOffset > startOffset) {
                String imageComponentString = mTextCachedBuffer.substring(startOffset + 1, endOffset);
                imageComponentString = imageComponentString.replace(" ", "").replace("\n", "").replace("\r", "");

                String[] imageComponents = imageComponentString.split(",");
                Float[] imageValues = new Float[imageComponents.length];
                for (int i = 0; i < imageComponents.length; i++) {
                    try {
                        imageValues[i] = Float.valueOf(imageComponents[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // Update max and min
                for (float value : imageValues) {
                    if (value > mMaxTemperature) {
                        mMaxTemperature = value;
                    }
                    if (value < mMinTemperature) {
                        mMinTemperature = value;
                    }
                }

                // Create updated image
                createImage(imageValues);

                // Remove processed text
                mTextCachedBuffer = endOffset < mTextCachedBuffer.length() ? mTextCachedBuffer.substring(endOffset + 1) : "";
            } else if (endOffset >= 0 && (startOffset < 0 || endOffset <= startOffset)) {
                // Remove orphaned text
                mTextCachedBuffer = endOffset < mTextCachedBuffer.length() ? mTextCachedBuffer.substring(endOffset + 1) : "";
            } else {
                finished = true;
            }

        } while (!finished);

        mTextCachedBufferSemaphore.release();
    }

    private Bitmap mCachedBitmap = null;
    private int mCachedBitmapDimen = 0;
    private int[] mCachedBitmapPixels;
    private BitmapDrawable mBitmapDrawable;

    private void createImage(Float[] values) {
        final Float temperatureRange = mMaxTemperature - mMinTemperature;

        // Generate bitmap if needed
        final int dimen = (int) Math.floor(Math.sqrt(values.length));
        if (dimen != mCachedBitmapDimen || mCachedBitmap == null) {
            mCachedBitmapDimen = dimen;
            if (mCachedBitmap != null) {
                mCachedBitmap.recycle();
            }
            mCachedBitmap = Bitmap.createBitmap(dimen, dimen, Bitmap.Config.RGB_565);
            mCachedBitmapPixels = new int[values.length];

            mBitmapDrawable = new BitmapDrawable(getResources(), mCachedBitmap);        // Create bitmap drawable to control filtering method
        }

        // Normalize values between 0 and 1
        for (int i = 0; i < values.length; i++) {
            final float normalizedValue = (values[i] - mMinTemperature) / temperatureRange;
            final int color = temperatureComponentsForValue(normalizedValue);
            mCachedBitmapPixels[i] = color;
        }
        mCachedBitmap.setPixels(mCachedBitmapPixels, 0, dimen, 0, 0, dimen, dimen);

        mMainHandler.post(() -> onImageUpdated(mBitmapDrawable));
    }

    // endregion

    // region UI
    @MainThread
    private void updateThermalUI(boolean isReady) {
        mUartWaitingTextView.setVisibility(isReady ? View.GONE : View.VISIBLE);
    }

    @MainThread
    private void onImageUpdated(BitmapDrawable bitmapDrawable) {

        bitmapDrawable.setFilterBitmap(mIsFilterEnabled);

        mCameraImageView.setImageDrawable(bitmapDrawable);

        if (mThermalScaleView.getAlpha() == 0 && isTemperatureReadReceived()) {
            mThermalScaleView.updateGradient();

            ObjectAnimator anim = ObjectAnimator.ofFloat(mThermalScaleView, "alpha", 1f);
            anim.setDuration(300);
            anim.start();
        }

        final Locale defaultLocale = Locale.getDefault();
        mLowerTempTextView.setText(String.format(defaultLocale, "%.2f", mMinTemperature));
        mUpperTempTextView.setText(String.format(defaultLocale, "%.2f", mMaxTemperature));
    }

    // endregion

    // region Color
    private float[] temperatureComponentsForValueCachedColor = new float[]{0f, 0.7f, 0.5f};        // To avoid allocating memory, preserve a float array for temporal colors

    public int temperatureComponentsForValue(float value) {

        if (mIsColorEnabled) {
            final float hue = kColdHue + (kHotHue - kColdHue) * value;
            temperatureComponentsForValueCachedColor[0] = hue;

            return Color.HSVToColor(temperatureComponentsForValueCachedColor);
        } else {
            final int valueByte = (int) (value * 255f);
            return Color.rgb(valueByte, valueByte, valueByte);
        }
    }

    // endregion

    // region Uart
    private void setupUart() {
        if (mBlePeripheral == null) {
            Log.e(TAG, "setupUart with blePeripheral null");
            return;
        }

        mBlePeripheralUart = new BlePeripheralUart(mBlePeripheral);
        mBlePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
            updateThermalUI(status == BluetoothGatt.GATT_SUCCESS);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Done
                Log.d(TAG, "Uart enabled");

            } else {
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

    /*
    private void uartRxCacheReset() {
        mUartDataManager.clearRxCache(mBlePeripheral.getIdentifier());
        mTextCachedBuffer = "";
    }*/

    // endregion

    // region UartDataManagerListener

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        String dataString = BleUtils.bytesToText(data, false);
        processBuffer(dataString);
        mUartDataManager.removeRxCacheFirst(data.length, peripheralIdentifier);
    }

    // endregion
}