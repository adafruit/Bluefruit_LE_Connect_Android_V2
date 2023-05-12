package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;

import java.util.HashMap;

/**
 * Based on:
 * <a href="https://raw.githubusercontent.com/ravindu1024/android-keyboardlistener/master/keyboard-listener/src/main/java/com/rw/keyboardlistener/KeyboardUtils.java">...</a>
 */
@SuppressWarnings("WeakerAccess")
public class KeyboardUtils implements ViewTreeObserver.OnGlobalLayoutListener {
    private final static int MAGIC_NUMBER = 200;

    private SoftKeyboardToggleListener mCallback;
    private final View mRootView;
    private Boolean prevValue = null;
    private final float mScreenDensity;
    private static final HashMap<SoftKeyboardToggleListener, KeyboardUtils> sListenerMap = new HashMap<>();

    public interface SoftKeyboardToggleListener {
        void onToggleSoftKeyboard(boolean isVisible);
    }

    @Override
    public void onGlobalLayout() {
        Rect r = new Rect();
        mRootView.getWindowVisibleDisplayFrame(r);

        int heightDiff = mRootView.getRootView().getHeight() - (r.bottom - r.top);
        float dp = heightDiff / mScreenDensity;
        boolean isVisible = dp > MAGIC_NUMBER;

        if (mCallback != null && (prevValue == null || isVisible != prevValue)) {
            prevValue = isVisible;
            mCallback.onToggleSoftKeyboard(isVisible);
        }
    }

    /**
     * Add a new keyboard listener
     *
     * @param activity calling activity
     * @param listener callback
     */
    public static void addKeyboardToggleListener(Activity activity, SoftKeyboardToggleListener listener) {
        removeKeyboardToggleListener(listener);

        sListenerMap.put(listener, new KeyboardUtils(activity, listener));
    }

    /**
     * Remove a registered listener
     *
     * @param listener {@link SoftKeyboardToggleListener}
     */
    public static void removeKeyboardToggleListener(SoftKeyboardToggleListener listener) {
        if (sListenerMap.containsKey(listener)) {
            KeyboardUtils aListener = sListenerMap.get(listener);
            if (aListener != null) {
                aListener.removeListener();
            }

            sListenerMap.remove(listener);
        }
    }

    /**
     * Remove all registered keyboard listeners
     */
    public static void removeAllKeyboardToggleListeners() {
        for (SoftKeyboardToggleListener l : sListenerMap.keySet()) {
            KeyboardUtils aListener = sListenerMap.get(l);
            if (aListener != null) {
                aListener.removeListener();
            }
        }

        sListenerMap.clear();
    }


    private void removeListener() {
        mCallback = null;

        mRootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    private KeyboardUtils(Activity act, SoftKeyboardToggleListener listener) {
        mCallback = listener;

        mRootView = ((ViewGroup) act.findViewById(android.R.id.content)).getChildAt(0);
        mRootView.getViewTreeObserver().addOnGlobalLayoutListener(this);

        mScreenDensity = act.getResources().getDisplayMetrics().density;
    }

    // region Manage Keyboard
    public static void showKeyboard(Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public static void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    // endregion
}