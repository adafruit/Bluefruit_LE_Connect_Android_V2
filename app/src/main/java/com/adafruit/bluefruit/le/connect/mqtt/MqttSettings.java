package com.adafruit.bluefruit.le.connect.mqtt;

import android.content.Context;
import android.content.SharedPreferences;

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
    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor getSharedPreferencesEditor(Context context) {
        return context.getSharedPreferences(kPreferences, Context.MODE_PRIVATE).edit();
    }

    public static String getServerAddress(Context context) {
        return getPrefsString(context, kPreferences_serveraddress, kDefaultServerAddress);
    }

    public static void setServerAddress(Context context, String address) {
        setPrefsString(context, kPreferences_serveraddress, address);
    }

    public static int getServerPort(Context context) {
        return getPrefsInt(context, kPreferences_serverport, kDefaultServerPort);
    }

    public static void setServerPort(Context context, String port) {
        int portInt = kDefaultServerPort;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException ignored) {
        }
        setPrefsInt(context, kPreferences_serverport, portInt);
    }

    public static boolean isConnected(Context context) {
        return getPrefsBoolean(context, kPreferences_connected, false);
    }

    public static void setConnectedEnabled(Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_connected, enabled);
    }

    public static boolean isPublishEnabled(Context context) {
        return getPrefsBoolean(context, kPreferences_publishenabled, true);
    }

    public static void setPublishEnabled(Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_publishenabled, enabled);
    }

    public static boolean isSubscribeEnabled(Context context) {
        return getPrefsBoolean(context, kPreferences_subscribeenabled, true);
    }

    public static void setSubscribeEnabled(Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_subscribeenabled, enabled);
    }

    public static int getPublishQos(Context context, int index) {
        return getPrefsInt(context, kPreferences_publishqos + index, MqttManager.MqqtQos_AtMostOnce);
    }

    public static void setPublishQos(Context context, int index, int qos) {
        setPrefsInt(context, kPreferences_publishqos + index, qos);
    }

    public static int getSubscribeQos(Context context) {
        return getPrefsInt(context, kPreferences_subscribeqos, MqttManager.MqqtQos_AtMostOnce);
    }

    public static void setSubscribeQos(Context context, int qos) {
        setPrefsInt(context, kPreferences_subscribeqos, qos);
    }

    public static int getSubscribeBehaviour(Context context) {
        return getPrefsInt(context, kPreferences_subscribebehaviour, kSubscribeBehaviour_LocalOnly);
    }

    public static void setSubscribeBehaviour(Context context, int behaviour) {
        setPrefsInt(context, kPreferences_subscribebehaviour, behaviour);
    }

    public static String getPublishTopic(Context context, int index) {
        return getPrefsString(context, kPreferences_publishtopic + index, index == 0 ? kDefaultPublishTopicTx : kDefaultPublishTopicRx);
    }

    public static void setPublishTopic(Context context, int index, String topic) {
        setPrefsString(context, kPreferences_publishtopic + index, topic);
    }

    public static String getSubscribeTopic(Context context) {
        return getPrefsString(context, kPreferences_subscribetopic, kDefaultSubscribeTopic);
    }

    public static void setSubscribeTopic(Context context, String topic) {
        setPrefsString(context, kPreferences_subscribetopic, topic);
    }


    public static String getUsername(Context context) {
        return getPrefsString(context, kPreferences_username, null);
    }

    public static void setUsername(Context context, String username) {
        setPrefsString(context, kPreferences_username, username);
    }

    public static String getPassword(Context context) {
        return getPrefsString(context, kPreferences_password, null);
    }

    public static void setPassword(Context context, String password) {
        setPrefsString(context, kPreferences_password, password);
    }

    public static boolean isSslConnection(Context context) {
        return getPrefsBoolean(context, kPreferences_sslconnection, false);
    }

    public static void setSslConnection(Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_sslconnection, enabled);
    }


    public static boolean isCleanSession(Context context) {
        return getPrefsBoolean(context, kPreferences_cleansession, true);
    }

    public static void setCleanSession(Context context, boolean enabled) {
        setPrefsBoolean(context, kPreferences_cleansession, enabled);
    }

    // region Utils
    private static String getPrefsString(Context context, String key, String defaultValue) {
        return getSharedPreferences(context).getString(key, defaultValue);
    }

    private static int getPrefsInt(Context context, String key, int defaultValue) {
        return getSharedPreferences(context).getInt(key, defaultValue);
    }

    private static boolean getPrefsBoolean(Context context, String key, boolean defaultValue) {
        return getSharedPreferences(context).getBoolean(key, defaultValue);
    }

    public static void setPrefsString(Context context, String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putString(key, value);
        editor.apply();
    }

    public static void setPrefsInt(Context context, String key, int value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putInt(key, value);
        editor.apply();
    }

    public static void setPrefsBoolean(Context context, String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor(context);
        editor.putBoolean(key, value);
        editor.apply();
    }
    // endregion
}