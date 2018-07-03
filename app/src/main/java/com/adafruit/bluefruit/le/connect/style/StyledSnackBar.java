package com.adafruit.bluefruit.le.connect.style;

import android.content.Context;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;

public class StyledSnackBar {

    public static void styleSnackBar(Snackbar snackbar, Context context) {
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));
        TextView textView = snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);     // Manually changing colors till theme styling is available for snackbars
        textView.setTextColor(ContextCompat.getColor(context, R.color.infotext));
    }
}
