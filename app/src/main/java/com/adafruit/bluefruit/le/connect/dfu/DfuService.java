package com.adafruit.bluefruit.le.connect.dfu;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.adafruit.bluefruit.le.connect.BuildConfig;

import no.nordicsemi.android.dfu.DfuBaseService;

public class DfuService extends DfuBaseService {

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        /*
         * As a target activity the NotificationActivity is returned, not the MainActivity. This is because the notification must create a new task:
         *
         * intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         *
         * when user press it. Using NotificationActivity we can check whether the new activity is a root activity (that means no other activity was open before)
         * or that there is other activity already open. In the later case the notificationActivity will just be closed. System will restore the previous activity from
         * this application - the MainActivity. However if nRF Beacon has been closed during upload and user click the notification a NotificationActivity will
         * be launched as a root activity. It will create and start the MainActivity and finish itself.
         *
         * This method may be used to restore the target activity in case the application was closed or is open. It may also be used to recreate an activity history (see NotificationActivity).
         */
        return NotificationActivity.class;
    }


    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return BuildConfig.DEBUG;
    }

    @Override
    protected void updateForegroundNotification(@NonNull final NotificationCompat.Builder builder) {
        // Customize the foreground service notification here.
    }
}
