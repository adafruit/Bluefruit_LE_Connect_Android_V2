package com.adafruit.bluefruit.le.connect.dfu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.adafruit.bluefruit.le.connect.app.MainActivity;

public class NotificationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If this activity is the root activity of the task, the app is not running
        if (isTaskRoot()) {
            // Start the app before finishing
            final Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras); // copy all extras
            }
            startActivity(intent);
        }

        // Now finish, which will drop the user in to the activity that was at the top of the task stack
        finish();
    }
}