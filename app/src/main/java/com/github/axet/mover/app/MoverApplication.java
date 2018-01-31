package com.github.axet.mover.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.mover.R;
import com.github.axet.mover.services.FileObserverService;

public class MoverApplication extends Application {

    public static final String STORAGE = "storage";
    public static final String ENABLED = "enabled";

    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";

    public static final String PREFERENCE_NAME = "name";

    public static final String AUTO_COUNT = "AUTO_COUNT";
    public static final String AUTO_PREFIX = "AUTO_";
    public static final String AUTO_ENABLED = "_ENABLED";
    public static final String AUTO_PATH = "_PATH";

    public static final String MANUAL_COUNT = "MANUAL_COUNT";
    public static final String MANUAL_PREFIX = "MANUAL_";
    public static final String MANUAL_PATH = "_PATH";

    public static int getTheme(Context context, int light, int dark) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(PREFERENCE_THEME, "");
        if (theme.equals(context.getString(R.string.Theme_Dark))) {
            return dark;
        } else {
            return light;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        FileObserverService.startIfEnabled(this);
    }
}
