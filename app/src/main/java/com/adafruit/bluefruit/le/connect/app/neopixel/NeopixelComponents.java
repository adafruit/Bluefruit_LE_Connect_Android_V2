package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
class NeopixelComponents {
    // Log
    private final static String TAG = NeopixelComponents.class.getSimpleName();

    // Constants
    static final int kComponents_rgb = 0;
    static final int kComponents_rbg = 1;
    static final int kComponents_grb = 2;
    static final int kComponents_gbr = 3;
    static final int kComponents_brg = 4;
    static final int kComponents_bgr = 5;

    //      RGBW NeoPixel permutations; all 4 offsets are distinct
    static final int kComponents_wrgb = 6;
    static final int kComponents_wrbg = 7;
    static final int kComponents_wgrb = 8;
    static final int kComponents_wgbr = 9;
    static final int kComponents_wbrg = 10;
    static final int kComponents_wbgr = 11;

    static final int kComponents_rwgb = 12;
    static final int kComponents_rwbg = 13;
    static final int kComponents_rgwb = 14;
    static final int kComponents_rgbw = 15;
    static final int kComponents_rbwg = 16;
    static final int kComponents_rbgw = 17;

    static final int kComponents_gwrb = 18;
    static final int kComponents_gwbr = 19;
    static final int kComponents_grwb = 20;
    static final int kComponents_grbw = 21;
    static final int kComponents_gbwr = 22;
    static final int kComponents_gbrw = 23;

    static final int kComponents_bwrg = 24;
    static final int kComponents_bwgr = 25;
    static final int kComponents_brwg = 26;
    static final int kComponents_brgw = 27;
    static final int kComponents_bgwr = 28;
    static final int kComponents_bgrw = 29;

    // Data
    private int mType;

    // region Lifecycle
    NeopixelComponents(int type) {
        mType = type;
    }

    // endregion

    // Accessors
    int getType() {
        return mType;
    }

    int getNumComponents() {
        if (mType == kComponents_rgb || mType == kComponents_rbg || mType == kComponents_grb || mType == kComponents_gbr || mType == kComponents_brg || mType == kComponents_bgr) {
            return 3;
        } else {
            return 4;
        }
    }

    static NeopixelComponents[] getAll() {
        return new NeopixelComponents[]{
                new NeopixelComponents(kComponents_rgb),
                new NeopixelComponents(kComponents_rbg),
                new NeopixelComponents(kComponents_grb),
                new NeopixelComponents(kComponents_gbr),
                new NeopixelComponents(kComponents_brg),
                new NeopixelComponents(kComponents_bgr),
                new NeopixelComponents(kComponents_wrgb),
                new NeopixelComponents(kComponents_wrbg),
                new NeopixelComponents(kComponents_wgrb),
                new NeopixelComponents(kComponents_wgbr),
                new NeopixelComponents(kComponents_wbrg),
                new NeopixelComponents(kComponents_wbgr),
                new NeopixelComponents(kComponents_rwgb),
                new NeopixelComponents(kComponents_rwbg),
                new NeopixelComponents(kComponents_rgwb),
                new NeopixelComponents(kComponents_rgbw),
                new NeopixelComponents(kComponents_rbwg),
                new NeopixelComponents(kComponents_rbgw),
                new NeopixelComponents(kComponents_gwrb),
                new NeopixelComponents(kComponents_gwbr),
                new NeopixelComponents(kComponents_grwb),
                new NeopixelComponents(kComponents_grbw),
                new NeopixelComponents(kComponents_gbwr),
                new NeopixelComponents(kComponents_gbrw),
                new NeopixelComponents(kComponents_bwrg),
                new NeopixelComponents(kComponents_bwgr),
                new NeopixelComponents(kComponents_brwg),
                new NeopixelComponents(kComponents_brgw),
                new NeopixelComponents(kComponents_bgwr),
                new NeopixelComponents(kComponents_bgrw),
        };
    }


    @SuppressWarnings("PointlessBitwiseExpression")
    byte getComponentValue() {
        switch (mType) {
            // Offset:  W          R          G          B
            case kComponents_rgb:
                return ((0 << 6) | (0 << 4) | (1 << 2) | (2));
            case kComponents_rbg:
                return ((0 << 6) | (0 << 4) | (2 << 2) | (1));
            case kComponents_grb:
                return ((1 << 6) | (1 << 4) | (0 << 2) | (2));
            case kComponents_gbr:
                return (byte) ((2 << 6) | (2 << 4) | (0 << 2) | (1));
            case kComponents_brg:
                return (byte) ((1 << 6) | (1 << 4) | (2 << 2) | (0));
            case kComponents_bgr:
                return (byte) ((2 << 6) | (2 << 4) | (1 << 2) | (0));

            // RGBW NeoPixel permutations; all 4 offsets are distinct
            // Offset:   W          R          G          B
            case kComponents_wrgb:
                return ((0 << 6) | (1 << 4) | (2 << 2) | (3));
            case kComponents_wrbg:
                return ((0 << 6) | (1 << 4) | (3 << 2) | (2));
            case kComponents_wgrb:
                return ((0 << 6) | (2 << 4) | (1 << 2) | (3));
            case kComponents_wgbr:
                return ((0 << 6) | (3 << 4) | (1 << 2) | (2));
            case kComponents_wbrg:
                return ((0 << 6) | (2 << 4) | (3 << 2) | (1));
            case kComponents_wbgr:
                return ((0 << 6) | (3 << 4) | (2 << 2) | (1));

            case kComponents_rwgb:
                return ((1 << 6) | (0 << 4) | (2 << 2) | (3));
            case kComponents_rwbg:
                return ((1 << 6) | (0 << 4) | (3 << 2) | (2));
            case kComponents_rgwb:
                return (byte) ((2 << 6) | (0 << 4) | (1 << 2) | (3));
            case kComponents_rgbw:
                return (byte) ((3 << 6) | (0 << 4) | (1 << 2) | (2));
            case kComponents_rbwg:
                return (byte) ((2 << 6) | (0 << 4) | (3 << 2) | (1));
            case kComponents_rbgw:
                return (byte) ((3 << 6) | (0 << 4) | (2 << 2) | (1));

            case kComponents_gwrb:
                return ((1 << 6) | (2 << 4) | (0 << 2) | (3));
            case kComponents_gwbr:
                return ((1 << 6) | (3 << 4) | (0 << 2) | (2));
            case kComponents_grwb:
                return (byte) ((2 << 6) | (1 << 4) | (0 << 2) | (3));
            case kComponents_grbw:
                return (byte) ((3 << 6) | (1 << 4) | (0 << 2) | (2));
            case kComponents_gbwr:
                return (byte) ((2 << 6) | (3 << 4) | (0 << 2) | (1));
            case kComponents_gbrw:
                return (byte) ((3 << 6) | (2 << 4) | (0 << 2) | (1));

            case kComponents_bwrg:
                return ((1 << 6) | (2 << 4) | (3 << 2) | (0));
            case kComponents_bwgr:
                return ((1 << 6) | (3 << 4) | (2 << 2) | (0));
            case kComponents_brwg:
                return (byte) ((2 << 6) | (1 << 4) | (3 << 2) | (0));
            case kComponents_brgw:
                return (byte) ((3 << 6) | (1 << 4) | (2 << 2) | (0));
            case kComponents_bgwr:
                return (byte) ((2 << 6) | (3 << 4) | (1 << 2) | (0));
            case kComponents_bgrw:
                return (byte) ((3 << 6) | (2 << 4) | (1 << 2) | (0));
            default:
                Log.e(TAG, "Undefined component id");
                return -1;
        }
    }

    @Nullable
    static NeopixelComponents componentFromValue(byte value) {
        NeopixelComponents result = null;

        NeopixelComponents[] allComponents = getAll();
        boolean found = false;
        int i = 0;
        while (i < allComponents.length && !found) {
            NeopixelComponents components = allComponents[i];
            if (components.getComponentValue() == value) {
                found = true;
                result = components;
            }
            i++;
        }

        return result;
    }

    @NonNull
    String getComponentName() {
        switch (mType) {
            // Offset:  W          R          G          B
            case kComponents_rgb:
                return "RGB";
            case kComponents_rbg:
                return "RBG";
            case kComponents_grb:
                return "GRB";
            case kComponents_gbr:
                return "GBR";
            case kComponents_brg:
                return "BRG";
            case kComponents_bgr:
                return "BGR";

            // RGBW NeoPixel permutations; all 4 offsets are distinct
            // Offset:   W          R          G          B
            case kComponents_wrgb:
                return "WRGB";
            case kComponents_wrbg:
                return "WRGB";
            case kComponents_wgrb:
                return "WGRB";
            case kComponents_wgbr:
                return "WGBR";
            case kComponents_wbrg:
                return "WBRG";
            case kComponents_wbgr:
                return "WBGR";

            case kComponents_rwgb:
                return "RWGB";
            case kComponents_rwbg:
                return "RWBG";
            case kComponents_rgwb:
                return "RGWB";
            case kComponents_rgbw:
                return "RGBW";
            case kComponents_rbwg:
                return "RBWG";
            case kComponents_rbgw:
                return "RBGW";

            case kComponents_gwrb:
                return "GWRB";
            case kComponents_gwbr:
                return "GWBR";
            case kComponents_grwb:
                return "GRWB";
            case kComponents_grbw:
                return "GRBW";
            case kComponents_gbwr:
                return "GBWR";
            case kComponents_gbrw:
                return "GBRW";

            case kComponents_bwrg:
                return "BWRG";
            case kComponents_bwgr:
                return "BWGR";
            case kComponents_brwg:
                return "BRWG";
            case kComponents_brgw:
                return "BRGW";
            case kComponents_bgwr:
                return "BGWR";
            case kComponents_bgrw:
                return "BGRW";
            default:
                Log.e(TAG, "Undefined component id");
                return "";
        }
    }

    // endregion
}
