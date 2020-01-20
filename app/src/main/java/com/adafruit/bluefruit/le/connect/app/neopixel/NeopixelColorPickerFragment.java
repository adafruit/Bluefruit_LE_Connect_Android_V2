package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

public class NeopixelColorPickerFragment extends AppCompatDialogFragment implements ColorPicker.OnColorChangedListener {
    // Log
    @SuppressWarnings("unused")
    private final static String TAG = NeopixelColorPickerFragment.class.getSimpleName();

    // Constants
    private static final String ARG_COLOR = "color";
    private static final String ARG_WCOMPONENT = "wComponent";
    private static final String ARG_IS4COMPONENTSENABLED = "is4ComponentsEnabled";

    private View mRgbColorView;
    private View wComponentColorView;
    private TextView mRgbTextView;
    private TextView mHexTextView;
    private NeopixelComponentWBar mComponentWBar;

    // Data
    private boolean mIs4ComponentsEnabled;
    private int mColor;
    private float mWComponent;
    private NeopixelColorPickerFragmentListener mListener;

    // region Lifecycle
    public static NeopixelColorPickerFragment newInstance(int color, float wComponent, boolean is4ComponentsEnabled) {
        NeopixelColorPickerFragment fragment = new NeopixelColorPickerFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLOR, color);
        args.putFloat(ARG_WCOMPONENT, wComponent);
        args.putBoolean(ARG_IS4COMPONENTSENABLED, is4ComponentsEnabled);
        fragment.setArguments(args);
        return fragment;
    }

    public NeopixelColorPickerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            mColor = bundle.getInt(ARG_COLOR);
            mWComponent = bundle.getFloat(ARG_WCOMPONENT);
            mIs4ComponentsEnabled = bundle.getBoolean(ARG_IS4COMPONENTSENABLED);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Remove title
        AppCompatDialog dialog = (AppCompatDialog) getDialog();
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_neopixel_colorpicker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Dismiss on click outside
        getDialog().setCanceledOnTouchOutside(true);

        // UI
        mRgbColorView = view.findViewById(R.id.rgbColorView);
        TextView wComponentTextView = view.findViewById(R.id.wComponentTextView);
        wComponentColorView = view.findViewById(R.id.wComponentColorView);

        mRgbTextView = view.findViewById(R.id.rgbTextView);
        mHexTextView = view.findViewById(R.id.hexTextView);

        SVBar mBrightness = view.findViewById(R.id.brightnessbar);
        ColorPicker colorPicker = view.findViewById(R.id.colorPicker);
        mComponentWBar = view.findViewById(R.id.wComponentBar);
        mComponentWBar.setWComponent(mWComponent);
        mComponentWBar.setListener(wComponent -> onColorChanged(colorPicker.getColor()));

        colorPicker.addSVBar(mBrightness);
        colorPicker.setOnColorChangedListener(this);

        colorPicker.setOldCenterColor(mColor);
        colorPicker.setColor(mColor);


        Button selectColorButton = view.findViewById(R.id.selectColorButton);
        selectColorButton.setOnClickListener(view1 -> {
            mListener.onColorSelected(mColor, mWComponent);
            dismiss();
        });


        // Components
        wComponentTextView.setVisibility(mIs4ComponentsEnabled ? View.VISIBLE : View.GONE);
        mComponentWBar.setVisibility(mIs4ComponentsEnabled ? View.VISIBLE : View.GONE);
        wComponentColorView.setVisibility(mIs4ComponentsEnabled ? View.VISIBLE : View.GONE);

    }

    private String getColorHexString(int color) {
        // https://stackoverflow.com/questions/6539879/how-to-convert-a-color-integer-to-a-hex-string-in-android
        return String.format("#%06X", (0xFFFFFF & color));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof NeopixelColorPickerFragmentListener) {
            mListener = (NeopixelColorPickerFragmentListener) context;
        } else if (getTargetFragment() instanceof NeopixelColorPickerFragmentListener) {
            mListener = (NeopixelColorPickerFragmentListener) getTargetFragment();
        } else {
            throw new RuntimeException(context.toString() + " must implement NeopixelColorPickerFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
    // endregion

    // region OnColorChangedListener

    @SuppressWarnings("PointlessBitwiseExpression")
    @Override
    public void onColorChanged(int color) {

        // Update UI
        mRgbColorView.setBackgroundColor(color);

        final float wComponent = mComponentWBar.getWComponent();
        wComponentColorView.setBackgroundColor(mComponentWBar.getColor());

        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = (color >> 0) & 0xFF;
        final String rgbHexString = getColorHexString(color);

        if (mIs4ComponentsEnabled) {
            final int w = (int) (wComponent * 255.0) & 0xff;
            final String wHex = String.format("%02X", w);
            mRgbTextView.setText(String.format(getString(R.string.colorpicker_rgbw_format), r, g, b, w));
            mHexTextView.setText(String.format(getString(R.string.colorpicker_hex_format), rgbHexString + wHex));

        } else {
            mRgbTextView.setText(String.format(getString(R.string.colorpicker_rgb_format), r, g, b));
            mHexTextView.setText(String.format(getString(R.string.colorpicker_hex_format), rgbHexString));
        }

        // Save selected color
        mColor = color;
        mWComponent = wComponent;
    }

    // endregion

    // region Listener
    public interface NeopixelColorPickerFragmentListener {
        void onColorSelected(int color, float wComponent);
    }

    // endregion
}