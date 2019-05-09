package com.adafruit.bluefruit.le.connect.utils;

import android.content.Context;

public class LocalizationManager {
    // Constants
    private final static String TAG = LocalizationManager.class.getSimpleName();

    //private final static String kLanguageSettingsFile = "LanguageFile";

    // Singleton
    private static LocalizationManager sInstance = null;

    public static synchronized LocalizationManager getInstance() {
        if (sInstance == null) {
            sInstance = new LocalizationManager();
        }

        return sInstance;
    }

    private LocalizationManager() {
    }

    /*
    public void initLanguage(Context context) {
        SharedPreferences settings = context.getSharedPreferences(LocalizationManager.kLanguageSettingsFile, 0);
        String currentLanguage = settings.getString("language", null);

        if (currentLanguage == null) {
            // Set the default language
            currentLanguage = Locale.getDefault().getLanguage();
        }

		Log.v(TAG, "language:"+currentLanguage);
    }*/

    public String getString(Context context, String stringName) {
        int id = context.getResources().getIdentifier(stringName, "string", context.getPackageName());
        if (id == 0) return null;
        else return context.getResources().getString(id);
    }

    /*
    public void changeLocaleLanguage(Context context) {
        saveCurrentLanguage(context);
    }

    public void setLanguageCode(Context context, String languageCode) {
        saveCurrentLanguage(context);
    }

    private void saveCurrentLanguage(Context context) {
        SharedPreferences settings = context.getSharedPreferences(LocalizationManager.kLanguageSettingsFile, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("language", "en");
        editor.apply();
    }

    public String getLanguageCode() {
        return "en";
    }
    */
}
