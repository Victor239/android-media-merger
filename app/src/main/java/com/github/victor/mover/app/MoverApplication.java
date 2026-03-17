package com.github.victor.mover.app;

import android.content.Context;
import androidx.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.victor.mover.R;
import com.github.victor.mover.services.MoverService;

public class MoverApplication extends MainApplication {
    public static final String STORAGE = "storage";
    public static final String ENABLED = "enabled";

    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";
    public static final String PREFERENCE_LEGACY = "legacy";

    public static final String PREFERENCE_NAME = "name";

    public static final String AUTO_COUNT = "AUTO_COUNT";
    public static final String AUTO_PREFIX = "AUTO_";
    public static final String AUTO_ENABLED = "_ENABLED";
    public static final String AUTO_PATH = "_PATH";

    public static final String MANUAL_COUNT = "MANUAL_COUNT";
    public static final String MANUAL_PREFIX = "MANUAL_";
    public static final String MANUAL_PATH = "_PATH";

    public static final String PREFERENCE_NEXT = "last";

    public static final String PREFERENCE_BOOT = "boot";

    public NotificationChannelCompat channelStatus;

    public static MoverApplication from(Context context) {
        return (MoverApplication) MainApplication.from(context);
    }

    public static int getTheme(Context context, int light, int dark) {
        return MainApplication.getTheme(context, PREFERENCE_THEME, light, dark);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        channelStatus = new NotificationChannelCompat(this, "status", "Persistent Notifications", NotificationManagerCompat.IMPORTANCE_LOW);

        OptimizationPreferenceCompat.setPersistentServiceIcon(this, true);

        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        MoverService.startIfEnabled(this);
    }
}
