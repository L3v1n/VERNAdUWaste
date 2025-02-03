package com.vern.vernaduwaste;

import android.content.Context;
import android.content.SharedPreferences;

public class AppState {
    private static AppState instance;
    private boolean isOfflineMode = false;
    private final SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "AppStatePrefs";
    private static final String KEY_OFFLINE_MODE = "isOfflineMode";

    private AppState(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isOfflineMode = sharedPreferences.getBoolean(KEY_OFFLINE_MODE, false);
    }

    public static synchronized AppState getInstance(Context context) {
        if (instance == null) {
            instance = new AppState(context);
        }
        return instance;
    }

    public boolean isOfflineMode() {
        return isOfflineMode;
    }

    public void setOfflineMode(boolean offlineMode) {
        isOfflineMode = offlineMode;
        sharedPreferences.edit().putBoolean(KEY_OFFLINE_MODE, offlineMode).apply();
    }
}
