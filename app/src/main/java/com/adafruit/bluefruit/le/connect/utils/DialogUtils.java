package com.adafruit.bluefruit.le.connect.utils;

import android.app.Dialog;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.Window;
import android.view.WindowManager;

import java.util.List;

public class DialogUtils {

    // Prevent dialog dismiss when orientation changes
    // http://stackoverflow.com/questions/7557265/prevent-dialog-dismissal-on-screen-rotation-in-android
    public static void keepDialogOnOrientationChanges(@NonNull Dialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            if (attributes != null) {
                lp.copyFrom(attributes);
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                dialog.getWindow().setAttributes(lp);
            }
        }
    }


    // https://stackoverflow.com/questions/11026234/how-to-check-if-the-current-activity-has-a-dialog-in-front/27239319
    public static boolean hasOpenedDialogs(@NonNull FragmentActivity activity) {
        List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
        if (fragments != null) {
            for (Fragment fragment : fragments) {
                if (fragment instanceof DialogFragment) {
                    return true;
                }
            }
        }

        return false;
    }

}
