package com.adafruit.bluefruit.le.connect.mqtt;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

public class MqttSettings {
    // Log
    private final static String TAG = MqttSettings.class.getSimpleName();

    // Constants
    public final static int kDefaultServerPort = 1883;
    public final static String kDefaultServerAddress = "io.adafruit.com";
    public final static String kDefaultPublishTopicTx = null;
    public final static String kDefaultPublishTopicRx = null;
    public final static String kDefaultSubscribeTopic = null;

    public static final int kNumPublishFeeds = 2;
    public static final int kPublishFeed_RX = 0;
    public static final int kPublishFeed_TX = 1;

    private final static String kPreferences = "MqttSettings_prefs";
    private final static String kPreferences_serveraddress = "serveraddress";
    private final static String kPreferences_serverport = "serverport";
    private final static String kPreferences_publishtopic = "publishtopic";
    private final static String kPreferences_publishqos = "publishqos";
    private final static String kPreferences_publishenabled = "publishenabled";
    private final static String kPreferences_subscribetopic = "subscribetopic";
    private final static String kPreferences_subscribeqos = "subscribeqos";
    private final static String kPreferences_subscribebehaviour = "subscribebehaviour";
    private final static String kPreferences_subscribeenabled = "subscribeenabled";
    private final static String kPreferences_connected = "connected";
    private final static String kPreferences_username = "username";
    private final static String kPreferences_password = "password";
    private final static String kPreferences_cleansession = "cleansession";
    private final static String kPreferences_sslconnection = "sslconnection";

    public static final int kSubscribeBehaviour_LocalOnly = 0;      // note: should have the same order than strings/mqtt_uart_subscribe_behaviour
    public static final int kSubscribeBehaviour_Transmit = 1;

    // Data
    private static SharedPreferences getSharedPreferences(@NonNull Context context) {
        return context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor getSharedPreferencesEditor(@NonNull Context context) {
        return context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE).edit();
    }

    public static String getServerAddress(@NonNull Context context) {
        return getPrefsString(context, kPreferences_serveraddress, kDefaultServerAddress);
    }

    public static void setServerAddress(@NonNull Context context, String address) {
        setPrefsString(context, kPreferences_serveraddress, address);
    }

    public static int getServerPort(@NonNull Context context) {
        return getPrefsInt(context, kPreferences_serverport, kDefaultServerPort);
    }

    public static void setServerPort(@NonNull Context context, String port) {
        int portInt = kDefaultServerPort;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException ignored) {
        }
        setPrefsInt(context, kPreferences_serverport, portInt);
    }

    public static boolean isConnected(@NonNull Context context) {
        return getPrefsBoolean(context, kPreferences_connected, false);
    }

    public static void setConnectedEnabled(@NonNull Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_connected, enabled);
    }

    public static boolean isPublishEnabled(@NonNull Context context) {
        return getPrefsBoolean(context, kPreferences_publishenabled, true);
    }

    public static void setPublishEnabled(@NonNull Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_publishenabled, enabled);
    }

    public static boolean isSubscribeEnabled(@NonNull Context context) {
        return getPrefsBoolean(context, kPreferences_subscribeenabled, true);
    }

    public static void setSubscribeEnabled(@NonNull Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_subscribeenabled, enabled);
    }

    public static int getPublishQos(@NonNull Context context, int index) {
        return getPrefsInt(context, kPreferences_publishqos + index, MqttManager.MqqtQos_AtMostOnce);
    }

    public static void setPublishQos(@NonNull Context context, int index, int qos) {
        setPrefsInt(context, kPreferences_publishqos + index, qos);
    }

    public static int getSubscribeQos(Context context) {
        return getPrefsInt(context, kPreferences_subscribeqos, MqttManager.MqqtQos_AtMostOnce);
    }

    public static void setSubscribeQos(@NonNull Context context, int qos) {
        setPrefsInt(context, kPreferences_subscribeqos, qos);
    }

    public static int getSubscribeBehaviour(@NonNull Context context) {
        return getPrefsInt(context, kPreferences_subscribebehaviour, kSubscribeBehaviour_LocalOnly);
    }

    public static void setSubscribeBehaviour(@NonNull Context context, int behaviour) {
        setPrefsInt(context, kPreferences_subscribebehaviour, behaviour);
    }

    public static String getPublishTopic(@NonNull Context context, int index) {
        return getPrefsString(context, kPreferences_publishtopic + index, index == 0 ? kDefaultPublishTopicTx : kDefaultPublishTopicRx);
    }

    public static void setPublishTopic(@NonNull Context context, int index, String topic) {
        setPrefsString(context, kPreferences_publishtopic + index, topic);
    }

    public static String getSubscribeTopic(@NonNull Context context) {
        return getPrefsString(context, kPreferences_subscribetopic, kDefaultSubscribeTopic);
    }

    public static void setSubscribeTopic(@NonNull Context context, String topic) {
        setPrefsString(context, kPreferences_subscribetopic, topic);
    }


    public static String getUsername(@NonNull Context context) {
        return getPrefsString(context, kPreferences_username, null);
    }

    public static void setUsername(@NonNull Context context, String username) {
        setPrefsString(context, kPreferences_username, username);
    }

    public static String getPassword(@NonNull Context context) {
        return getPrefsString(context, kPreferences_password, null);
    }

    public static void setPassword(@NonNull Context context, String password) {
        setPrefsString(context, kPreferences_password, password);
    }

    public static boolean isSslConnection(@NonNull Context context) {
        return getPrefsBoolean(context, kPreferences_sslconnection, false);
    }

    public static void setSslConnection(@NonNull Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_sslconnection, enabled);
    }


    public static boolean isCleanSession(@NonNull Context context) {
        return getPrefsBoolean(context, kPreferences_cleansession, true);
    }

    public static void setCleanSession(@NonNull Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_cleansession, enabled);
    }

    // region Utils
    private static String getPrefsString(@NonNull Context context, String key, String defaultValue) {
        return getSharedPreferences(context).getString(key, defaultValue);
    }

    private static int getPrefsInt(@NonNull Context context, @NonNull String key, int defaultValue) {
        return getSharedPreferences(context).getInt(key, defaultValue);
    }

    private static boolean getPrefsBoolean(@NonNull Context context, @NonNull String key, boolean defaultValue) {
        return getSharedPreferences(context).getBoolean(key, defaultValue);
    }

    public static void setPrefsString(@NonNull Context context, @NonNull String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putString(key, value);
        editor.apply();
    }

    public static void setPrefsInt(@NonNull Context context, @NonNull String key, int value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putInt(key, value);
        editor.apply();
    }

    public static void setPrefsBoolean(@NonNull Context context, @NonNull String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putBoolean(key, value);
        editor.apply();
    }
    // endregion
}