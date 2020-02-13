package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.CommonHelpFragment;
import com.adafruit.bluefruit.le.connect.app.ConnectedPeripheralFragment;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.UartPacketManager;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.utils.FileUtils;
import com.adafruit.bluefruit.le.connect.utils.MetricsUtils;
import com.adafruit.bluefruit.le.connect.utils.TwoDimensionScrollView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NeopixelFragment extends ConnectedPeripheralFragment implements NeopixelColorPickerFragment.NeopixelColorPickerFragmentListener, NeopixelBoardSelectorFragment.NeopixelBoardSelectorFragmentListener, NeopixelComponentSelectorFragment.NeopixelComponentSelectorFragmentListener {
    // Log
    private final static String TAG = NeopixelFragment.class.getSimpleName();

    // Config
    private final static NeopixelComponents kDefaultComponents = new NeopixelComponents(BuildConfig.DEBUG ? NeopixelComponents.kComponents_grbw : NeopixelComponents.kComponents_grb);

    // Constants
    private final static String kPreferences = "NeopixelActivity_prefs";
    private final static String kPreferences_isSketchTooltipEnabled = "showSketchTooltip";
    private final static String kPreferences_isUsingStandardBoards = "isUsingStandardBoards";
    private final static String kPreferences_standardBoardIndex = "standardBoardIndex";
    private final static String kPreferences_lineBoardLength = "lineBoardLength";
    private final static String kPreferences_components = "components";
    private final static String kPreferences_isUsing400Hz = "isUsing400Hz";

    private final static String kSketchVersion = "Neopixel v2.";

    // Config
    private static final int kDefaultLedColor = Color.WHITE;
    private final static float kMinBoardScale = 0.1f;
    private final static float kMaxBoardScale = 10f;
    private final static float kLedPixelSize = 44;

    // UI
    private TextView mStatusTextView;
    private Button mConnectButton;
    private ViewGroup mBoardContentView;
    private TwoDimensionScrollView mCustomPanningView;
    private ViewGroup mRotationViewGroup;
    private Button mColorPickerButton;
    private View mColorPickerWComponentView;

    // Data
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private int mCurrentColor = Color.RED;
    private float mColorW = 0;
    private UartPacketManager mUartManager;
    private Board mBoard;                   // The current connected board. Is null if not connected to a board
    private Board mLastSelectedBoard;       // Board selected even if it was not connected
    private NeopixelComponents mComponents;
    private BlePeripheralUart mBlePeripheralUart;
    private List<Integer> mBoardCachedColors;

    private float mBoardScale = 1f;
    private float mBoardRotation = 0f;
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    // Neopixel
    private boolean mIsSketchChecked = false;
    private boolean mIsSketchDetected = false;
    private boolean isSketchTooltipAlreadyShown = false;


    // region Fragment Lifecycle
    public static NeopixelFragment newInstance(@NonNull String singlePeripheralIdentifier) {
        NeopixelFragment fragment = new NeopixelFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public NeopixelFragment() {
        // Required empty public constructor
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
        return inflater.inflate(R.layout.fragment_neopixel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.neopixels_tab_title);

        // UI
        mStatusTextView = view.findViewById(R.id.statusTextView);
        mColorPickerWComponentView = view.findViewById(R.id.colorPickerWComponentView);
        mColorPickerWComponentView.setElevation(1000);          // Is a view that should appear above the button. Take into account that buttons have elevation from API21
        mConnectButton = view.findViewById(R.id.connectButton);
        mConnectButton.setEnabled(false);
        mConnectButton.setOnClickListener(view12 -> onClickConnect());
        mBoardContentView = view.findViewById(R.id.boardContentView);

        mColorPickerButton = view.findViewById(R.id.colorPickerButton);
        setViewBackgroundColor(mColorPickerButton, mCurrentColor);
        mColorPickerButton.setOnClickListener(view14 -> {

            FragmentActivity activity = getActivity();
            if (activity != null) {
                FragmentManager fragmentManager = activity.getSupportFragmentManager();
                NeopixelColorPickerFragment colorPickerFragment = NeopixelColorPickerFragment.newInstance(mCurrentColor, mColorW, mComponents.getNumComponents() == 4);
                colorPickerFragment.setTargetFragment(this, 0);
                colorPickerFragment.show(fragmentManager, "ColorPicker");
            }
        });

        SeekBar brightnessSeekBar = view.findViewById(R.id.brightnessSeekBar);
        if (brightnessSeekBar != null) {
            brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    setBrightness(seekBar.getProgress() / 100.0f, null);
                }
            });
        }

        ImageButton colorClearButton = view.findViewById(R.id.colorClearButton);
        colorClearButton.setOnClickListener(view13 -> onClickClear());

        ImageButton rotateButton = view.findViewById(R.id.rotateButton);
        rotateButton.setOnClickListener(view1 -> {
            mBoardRotation = (mBoardRotation + 90) % 360;
            mRotationViewGroup.setRotation(mBoardRotation);
        });

        RecyclerView paletteRecyclerView = view.findViewById(R.id.paletteRecyclerView);
        Context context = getContext();
        if (context != null) {
            paletteRecyclerView.setHasFixedSize(true);
            paletteRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            RecyclerView.Adapter paletteAdapter = new PaletteAdapter(context, color -> {
                mCurrentColor = color;
                updatePickerColorButton(false);
            });
            paletteRecyclerView.setAdapter(paletteAdapter);

            mRotationViewGroup = view.findViewById(R.id.rotationViewGroup);
            mCustomPanningView = view.findViewById(R.id.customPanningView);

            setupGestures();

            // Tooltip
            final SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            boolean showSketchTooltip = preferences.getBoolean(kPreferences_isSketchTooltipEnabled, true);

            if (!isSketchTooltipAlreadyShown && showSketchTooltip) {

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.dialog_notice).setMessage(R.string.neopixel_sketch_tooltip)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(R.string.dialog_dontshowagain, (dialog, which) -> {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(kPreferences_isSketchTooltipEnabled, false);
                            editor.apply();
                        });
                builder.create().show();

                isSketchTooltipAlreadyShown = true;
            }

            // Data
            final byte componentsValue = (byte) preferences.getInt(kPreferences_components, NeopixelComponents.kComponents_grb);
            mComponents = NeopixelComponents.componentFromValue(componentsValue);
            if (mComponents == null) {
                mComponents = kDefaultComponents;
            }

        }

        // Setup
        if (context != null) {
            // UartManager
            mUartManager = new UartPacketManager(context, null, false, null);
            start();
        }


        updatePickerColorButton(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Board needs to be recreated when the view is recreated, so stop and free uartManager
        stop();
        mUartManager = null;
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_neopixel, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_boardSelector:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    NeopixelBoardSelectorFragment boardSelectorFragment = NeopixelBoardSelectorFragment.newInstance();
                    boardSelectorFragment.setTargetFragment(this, 0);
                    boardSelectorFragment.show(fragmentManager, "BoardSelector");
                }
                return true;

            case R.id.action_boardComponentsSelector:
                if (activity != null) {

                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    NeopixelComponentSelectorFragment boardComponentSelectorFragment = NeopixelComponentSelectorFragment.newInstance(mComponents.getType(), is400khzEnabled());
                    boardComponentSelectorFragment.setTargetFragment(this, 0);
                    boardComponentSelectorFragment.show(fragmentManager, "BoardComponentSelector");
                }
                return true;

            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.neopixel_help_title), getString(R.string.neopixel_help_text));
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
    // endregion

    // region Actions
    private void start() {
        Log.d(TAG, "Neopixel start");

        // Enable Uart
        mBlePeripheralUart = new BlePeripheralUart(mBlePeripheral);
        mBlePeripheralUart.uartEnable(mUartManager, status -> mMainHandler.post(() -> {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Done
                Log.d(TAG, "Uart enabled");

                Context context = getContext();
                if (context != null) {
                    mConnectButton.setEnabled(true);

                    // Setup
                    final SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
                    final boolean isUsingStandardBoards = preferences.getBoolean(kPreferences_isUsingStandardBoards, true);
                    Board board;
                    if (isUsingStandardBoards) {
                        final int standardBoardIndex = preferences.getInt(kPreferences_standardBoardIndex, 0);
                        board = new Board(context, standardBoardIndex);
                    } else {
                        final int lineBoardLength = preferences.getInt(kPreferences_lineBoardLength, 8);
                        board = createLineBoard(lineBoardLength);
                    }

                    changeComponents(mComponents, is400khzEnabled());
                    createBoardUI(board);
                    connectNeopixel(board);
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
        Log.d(TAG, "Neopixel stop");
        mBlePeripheral.reset();
        /*
        if (mBlePeripheralUart != null) {
            mBlePeripheralUart.uartDisable();
        }*/
        mBlePeripheralUart = null;
    }

    private boolean isReady() {
        return mBlePeripheralUart != null && mBlePeripheralUart.isUartEnabled();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isBoardConfigured() {
        return mBoard != null;
    }

    private boolean is400khzEnabled() {
        boolean result = false;

        FragmentActivity activity = getActivity();
        if (activity != null) {
            final SharedPreferences preferences = activity.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            result = preferences.getBoolean(kPreferences_isUsing400Hz, false);

        }
        return result;
    }

    private void setIs400khzEnabled(boolean enabled) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            final SharedPreferences preferences = activity.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(kPreferences_isUsing400Hz, enabled);
            editor.apply();
        }
    }

    private void setAndSaveComponents(NeopixelComponents components) {
        mComponents = components;
        FragmentActivity activity = getActivity();
        if (activity != null) {
            final SharedPreferences preferences = activity.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(kPreferences_components, components.getComponentValue());
            editor.apply();
        }
    }

    private void changeComponents(NeopixelComponents components, boolean is400HkzEnabled) {
        setAndSaveComponents(components);
        setIs400khzEnabled(is400HkzEnabled);
        final int numComponents = components.getNumComponents();
        mColorPickerWComponentView.setVisibility(numComponents == 4 ? View.VISIBLE : View.GONE);
    }

    private void changeBoard(@NonNull Board board) {
        createBoardUI(board);
        resetBoard();
        connectNeopixel(board);
    }

    private void onClickConnect() {
        if (mLastSelectedBoard != null) {
            connectNeopixel(mLastSelectedBoard);
        }
    }

    private void connectNeopixel(@NonNull Board board) {
        mLastSelectedBoard = board;
        updateStatusUI(true);

        checkNeopixelSketch(isDetected -> {
            if (isDetected) {
                setupNeopixel(board, mComponents, is400khzEnabled(), success -> mMainHandler.post(() -> {
                    if (success) {
                        onClickClear();
                    }
                    updateStatusUI(false);
                }));
            } else {
                mMainHandler.post(() -> updateStatusUI(false));
            }
        });
    }

    private void onClickClear() {
        for (int i = 0; i < mBoardContentView.getChildCount(); i++) {
            View ledView = mBoardContentView.getChildAt(i);
            Button ledButton = ledView.findViewById(R.id.ledButton);
            setViewBackgroundColor(ledButton, mCurrentColor);
        }

        if (mBoard != null) {
            final int boardSize = Math.max(0, mBoard.width * mBoard.height);
            mBoardCachedColors = new ArrayList<>(Collections.nCopies(boardSize, mCurrentColor));
            clearBoard(mCurrentColor, mColorW, null);
        }
    }

    // endregion

    // region UI
    private void updateStatusUI(boolean isWaitingResponse) {
        mConnectButton.setEnabled(!isWaitingResponse && (!mIsSketchDetected || !mIsSketchChecked) || (isReady() && !isBoardConfigured()));

        mCustomPanningView.setAlpha(mIsSketchDetected ? 1.0f : 0.2f);

        int statusMessageId;
        if (!isReady()) {
            statusMessageId = R.string.neopixels_waitingforuart;
        } else if (!mIsSketchChecked) {
            statusMessageId = R.string.neopixels_status_readytoconnect;
        } else {
            if (mIsSketchDetected) {
                if (!isBoardConfigured()) {
                    if (isWaitingResponse) {
                        statusMessageId = R.string.neopixels_status_waitingsetup;
                    } else {
                        statusMessageId = R.string.neopixels_status_readyforsetup;
                    }
                } else {
                    statusMessageId = R.string.neopixels_status_connected;
                }
            } else {
                if (isWaitingResponse) {
                    statusMessageId = R.string.neopixels_status_checkingsketch;
                } else {
                    statusMessageId = R.string.neopixels_status_notdetected;
                }
            }
        }

        mStatusTextView.setText(statusMessageId);
    }

    private static void setViewBackgroundColor(View view, int color) {
        setViewBackgroundColor(view, color, 0, 0);
    }

    private static void setViewBackgroundColor(View view, int color, int borderColor, int borderWidth) {
        GradientDrawable backgroundDrawable = (GradientDrawable) view.getBackground();
        backgroundDrawable.setColor(color);
        backgroundDrawable.setStroke(borderWidth, borderColor);
    }

    private void updatePickerColorButton(boolean isSelected) {
        final int borderWidth = (int) MetricsUtils.convertDpToPixel(getContext(), isSelected ? 4f : 2f);
        setViewBackgroundColor(mColorPickerButton, mCurrentColor, borderWidth, isSelected ? Color.WHITE : darkerColor(mCurrentColor, 0.5f));
        int colorWInt = (int) (mColorW * 255f);
        mColorPickerWComponentView.setBackgroundColor(Color.argb(255, colorWInt, colorWInt, colorWInt));
    }

    @SuppressWarnings("SameParameterValue")
    private static int darkerColor(int color, float factor) {
        final int a = Color.alpha(color);
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);

        return Color.argb(a,
                Math.max((int) (r * factor), 0),
                Math.max((int) (g * factor), 0),
                Math.max((int) (b * factor), 0));
    }

    private void setupGestures() {
        mGestureDetector = new GestureDetector(getContext(), new GestureListener());

        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = 1 - detector.getScaleFactor();

                mBoardScale += scale;

                if (mBoardScale < kMinBoardScale) mBoardScale = kMinBoardScale;
                if (mBoardScale > kMaxBoardScale) mBoardScale = kMaxBoardScale;
                Log.d(TAG, "Board scale: " + mBoardScale);

                mBoardContentView.setScaleX(1f / mBoardScale);
                mBoardContentView.setScaleY(1f / mBoardScale);
                return true;
            }
        });

        mCustomPanningView.setOnTouchListener((view, motionEvent) -> {      // Make the GestureDetectors work: https://stackoverflow.com/questions/11421368/android-fragment-oncreateview-with-gestures/11421565#11421565
            final boolean scaleResult = mGestureDetector.onTouchEvent(motionEvent);
            /*final boolean gestureResult = */
            mScaleDetector.onTouchEvent(motionEvent);
            //Log.d(TAG, "scaleResult: " + scaleResult + " gestureResult: " + gestureResult);
            return scaleResult;// || gestureResult;
        });
    }

    /*
    private void restoreCachedBoardColors() {
        for (int i = 0; i < mBoardContentView.getChildCount(); i++) {
            View ledView = mBoardContentView.getChildAt(i);
            Button ledButton = ledView.findViewById(R.id.ledButton);

            int color = mBoardCachedColors.get(i);
            setViewBackgroundColor(ledButton, color);
        }
    }*/

    private void createBoardUI(@NonNull Board board) {
        final ViewGroup canvasView = mBoardContentView;
        canvasView.removeAllViews();

        final int kLedSize = (int) MetricsUtils.convertDpToPixel(getContext(), kLedPixelSize);
        final int canvasViewWidth = canvasView.getWidth();
        final int canvasViewHeight = canvasView.getHeight();
        final int boardWidth = board.width * kLedSize;
        final int boardHeight = board.height * kLedSize;

        final int marginLeft = (canvasViewWidth - boardWidth) / 2;
        final int marginTop = (canvasViewHeight - boardHeight) / 2;

        for (int j = 0, k = 0; j < board.height; j++) {
            for (int i = 0; i < board.width; i++, k++) {
                View ledView = LayoutInflater.from(getContext()).inflate(R.layout.layout_neopixel_led, canvasView, false);
                Button ledButton = ledView.findViewById(R.id.ledButton);
                ledButton.setOnClickListener(view -> {
                    if (mBoard != null) {
                        int tag = (Integer) view.getTag();
                        byte x = (byte) (tag % mBoard.width);
                        byte y = (byte) (tag / mBoard.width);
                        Log.d(TAG, "led (" + x + "," + y + ")");

                        setViewBackgroundColor(view, mCurrentColor);
                        setPixelColor(mCurrentColor, mColorW, x, y, null);

                        mBoardCachedColors.set(y * mBoard.width + x, mCurrentColor);
                    }
                });
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(kLedSize, kLedSize);
                layoutParams.leftMargin = i * kLedSize + marginLeft;
                layoutParams.topMargin = j * kLedSize + marginTop;
                ledView.setLayoutParams(layoutParams);
                ledButton.setTag(k);

                setViewBackgroundColor(ledButton, kDefaultLedColor);
                canvasView.addView(ledView);
            }
        }

        // Setup initial scroll and scale
        resetScaleAndPanning(board);
    }

    private void resetScaleAndPanning(@NonNull Board board) {
        final int kLedSize = (int) MetricsUtils.convertDpToPixel(getContext(), kLedPixelSize);
        final int canvasViewWidth = mBoardContentView.getWidth();
        final int canvasViewHeight = mBoardContentView.getHeight();
        final int boardWidth = board.width * kLedSize;
        final int boardHeight = board.height * kLedSize;

        int panningViewWidth = mCustomPanningView.getWidth();
        mBoardScale = 1f / Math.min(1f, (panningViewWidth / (float) boardWidth) * 0.85f) + 0;
        mBoardContentView.setScaleX(1f / mBoardScale);
        mBoardContentView.setScaleY(1f / mBoardScale);
        mRotationViewGroup.setRotation(0);
        Log.d(TAG, "Initial scale: " + mBoardScale);


        int offsetX = Math.max(0, (canvasViewWidth - boardWidth) / 2);
        int offsetY = Math.max(0, (canvasViewHeight - boardHeight) / 2);
        mCustomPanningView.scrollTo(offsetX, offsetY);
    }

    // endregion

    // region Neopixel Commands

    private void checkNeopixelSketch(SuccessHandler successHandler) {
        // Send version command and check if returns a valid response
        Log.d(TAG, "Command: get Version");

        final byte[] command = {0x56};

        // Reset status
        mIsSketchChecked = false;
        mIsSketchDetected = false;

        mUartManager.sendAndWaitReply(mBlePeripheralUart, command, (status, value) -> {
            boolean isSketchDetected = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value != null) {
                    final String result = BleUtils.bytesToText(value, false);
                    if (result.startsWith(kSketchVersion)) {
                        isSketchDetected = true;
                    } else {
                        Log.w(TAG, "Error: sketch wrong version:" + result + ". Expecting: " + kSketchVersion);
                    }
                }
            } else {
                Log.e(TAG, "Error: checkNeopixelSketch: " + status);
            }

            Log.d(TAG, "isNeopixelAvailable: " + isSketchDetected);
            mIsSketchChecked = true;
            mIsSketchDetected = isSketchDetected;

            successHandler.result(isSketchDetected);
        });
    }

    private void setupNeopixel(@NonNull Board device, @NonNull NeopixelComponents components, boolean is400HzEnabled, @Nullable SuccessHandler successHandler) {
        Log.d(TAG, "Command: Setup");

        final byte[] command = {0x53, device.width, device.height, device.stride, components.getComponentValue(), (byte) (is400HzEnabled ? 1 : 0)};
        mUartManager.sendAndWaitReply(mBlePeripheralUart, command, (status, value) -> {
            boolean success = false;
            if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                final String valueString = BleUtils.bytesToText(value, false);
                success = valueString.startsWith("OK");
            }

            Log.d(TAG, "setup success: " + success);
            if (success) {
                mBoard = device;
                setAndSaveComponents(components);
                setIs400khzEnabled(is400HzEnabled);
            }
            if (successHandler != null) {
                successHandler.result(success);
            }
        });
    }

    private void resetBoard() {
        mBoard = null;
    }

    @SuppressWarnings("SameParameterValue")
    private void setPixelColor(int color, float colorW, byte x, byte y, @Nullable SuccessHandler successHandler) {
        Log.d(TAG, "Command: set Pixel");

        final int numComponents = mComponents.getNumComponents();
        if (numComponents != 3 && numComponents != 4) {
            Log.e(TAG, "Error: unsupported numComponents: " + numComponents);
            if (successHandler != null) {
                successHandler.result(false);
            }
            return;
        }

        byte red = (byte) Color.red(color);
        byte green = (byte) Color.green(color);
        byte blue = (byte) Color.blue(color);

        byte[] command;     // Command: 'P'
        if (numComponents == 4) {
            byte colorWValue = (byte) (colorW * 255);
            command = new byte[]{0x50, x, y, red, green, blue, colorWValue};
        } else {
            command = new byte[]{0x50, x, y, red, green, blue};
        }
        sendCommand(command, successHandler);
    }

    @SuppressWarnings("SameParameterValue")
    private void clearBoard(int color, float colorW, @Nullable SuccessHandler successHandler) {
        Log.d(TAG, "Command: Clear");

        final int numComponents = mComponents.getNumComponents();
        if (numComponents != 3 && numComponents != 4) {
            Log.e(TAG, "Error: unsupported numComponents: " + numComponents);
            if (successHandler != null) {
                successHandler.result(false);
            }
            return;
        }

        byte red = (byte) Color.red(color);
        byte green = (byte) Color.green(color);
        byte blue = (byte) Color.blue(color);

        byte[] command;     // Command: 'C'
        if (numComponents == 4) {
            byte colorWValue = (byte) (colorW * 255);
            command = new byte[]{0x43, red, green, blue, colorWValue};
        } else {
            command = new byte[]{0x43, red, green, blue};
        }
        sendCommand(command, successHandler);
    }

    @SuppressWarnings("SameParameterValue")
    private void setBrightness(float brightness, SuccessHandler successHandler) {
        Log.d(TAG, "Command: set Brightness" + brightness);

        byte brightnessValue = (byte) (brightness * 255);
        byte[] command = {0x42, brightnessValue};       // Command: 'B'
        sendCommand(command, successHandler);
    }

    private void sendCommand(@NonNull byte[] command, @Nullable SuccessHandler successHandler) {
        if (mBoard == null) {
            Log.w(TAG, "sendCommand: unknown board");
            if (successHandler != null) {
                successHandler.result(false);
            }
        }

        mUartManager.sendAndWaitReply(mBlePeripheralUart, command, (status, value) -> {
            boolean success = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value != null) {
                    final String result = BleUtils.bytesToText(value, false);
                    success = result.startsWith("OK");
                }
            } else {
                Log.e(TAG, "Error: sendDataToUart:" + status);
            }

            Log.d(TAG, "result: " + success);
            if (successHandler != null) {
                successHandler.result(success);
            }
        });
    }

    // endregion

    // region Palette

    public static class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.ViewHolder> {
        private List<Integer> mDefaultPalette;
        private OnColorListener mOnColorSelectedListener;

        interface OnColorListener {
            void onColorSelected(int color);
        }

        PaletteAdapter(@NonNull Context context, @NonNull OnColorListener onColorSelectedListener) {
            mOnColorSelectedListener = onColorSelectedListener;

            // Read palette data
            String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelDefaultPalette.json", context.getAssets());
            try {
                mDefaultPalette = new ArrayList<>();
                JSONArray paletteArray = new JSONArray(boardsJsonString);
                for (int i = 0; i < paletteArray.length(); i++) {
                    String colorString = paletteArray.getString(i);
                    int color = Color.parseColor("#" + colorString);
                    mDefaultPalette.add(color);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error decoding default palette");
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            Button mColorButton;

            ViewHolder(ViewGroup view) {
                super(view);
                mColorButton = view.findViewById(R.id.colorButton);
            }
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup paletteColorView = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_neopixel_palette_item, parent, false);

            final ViewHolder viewHolder = new ViewHolder(paletteColorView);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final int color = mDefaultPalette.get(position);
            setViewBackgroundColor(holder.mColorButton, color);

            holder.mColorButton.setOnClickListener(view -> mOnColorSelectedListener.onColorSelected(color));
        }

        @Override
        public int getItemCount() {
            return mDefaultPalette.size();
        }
    }

    // endregion

    // region NeopixelColorPickerFragmentListener
    @Override
    public void onColorSelected(int color, float wComponent) {
        mCurrentColor = color;
        mColorW = wComponent;
        updatePickerColorButton(true);
    }
    // endregion

    // region NeopixelBoardSelectorFragmentListener
    @Override
    public void onBoardIndexSelected(int index) {
        Context context = getContext();
        if (context != null) {
            // Save selected board
            final SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(kPreferences_isUsingStandardBoards, true);
            editor.putInt(kPreferences_standardBoardIndex, index);
            editor.apply();

            // Change board
            Board board = new Board(context, index);
            changeBoard(board);
        }
    }

    @Override
    public void onLineStripSelected(int stripLength) {
        Context context = getContext();
        if (context != null) {
            // Save selected board
            final SharedPreferences preferences = context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(kPreferences_isUsingStandardBoards, false);
            editor.putInt(kPreferences_lineBoardLength, stripLength);
            editor.apply();

            // Change board
            Board board = createLineBoard(stripLength);
            changeBoard(board);
        }
    }

    private Board createLineBoard(int stripLength) {
        return new Board("1x" + stripLength, (byte) stripLength, (byte) 1, (byte) stripLength);
    }
    // endregion


    // region NeopixelComponentSelectorFragmentListener
    @Override
    public void onComponentsSelected(NeopixelComponents components, boolean is400KhzEnabled) {
        changeComponents(components, is400KhzEnabled);
        onClickConnect();
    }
    // endregion

    // region Board

    static class Board {
        String name;
        byte width, height;
        byte stride;


        Board(@NonNull String name, byte width, byte height, byte stride) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.stride = stride;
        }

        Board(@NonNull Context context, int standardIndex) {

            String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelBoards.json", context.getAssets());
            try {
                JSONArray boardsArray = new JSONArray(boardsJsonString);
                JSONObject boardJson = boardsArray.getJSONObject(standardIndex);

                name = boardJson.getString("name");
                width = (byte) boardJson.getInt("width");
                height = (byte) boardJson.getInt("height");
                stride = (byte) boardJson.getInt("stride");

            } catch (JSONException e) {
                Log.e(TAG, "Invalid board parameters");
                e.printStackTrace();
            }
        }
    }

    // endregion

    // region GestureListener
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        // event when double tap occurs
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mBoard != null) {
                resetScaleAndPanning(mBoard);
            }
            return true;
        }
    }

    // endregion
}