package com.github.axet.androidlibrary.app;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import androidx.preference.PreferenceManager;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.widgets.ErrorDialog;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class MainApplication extends Application {
    public static final String TAG = MainApplication.class.getSimpleName();

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static MainApplication from(Context context) {
        if (context instanceof MainApplication)
            return (MainApplication) context;
        if (context instanceof Service) {
            Application app = ((Service) context).getApplication();
            if (app instanceof MainApplication)
                return (MainApplication) app;
        }
        if (context instanceof Activity) {
            Application app = ((Activity) context).getApplication();
            if (app instanceof MainApplication)
                return (MainApplication) app;
        }
        Context app = context.getApplicationContext();
        if (app instanceof MainApplication)
            return (MainApplication) app;
        try {
            Class ContextImpl = Class.forName("android.app.ContextImpl");
            if (ContextImpl.isInstance(context)) {
                Object apk = AssetsDexLoader.getPrivateField(ContextImpl, "mPackageInfo").get(context);
                Class LoadedApk = Class.forName("android.app.LoadedApk");
                if (apk != null) {
                    Application a = (Application) AssetsDexLoader.getPrivateMethod(LoadedApk, "getApplication").invoke(apk);
                    if (a instanceof MainApplication)
                        return (MainApplication) a;
                }
                Class ActivityThread = Class.forName("android.app.ActivityThread");
                Object thread = AssetsDexLoader.getPrivateField(ContextImpl, "mMainThread").get(context);
                if (thread != null) {
                    Application a = (Application) AssetsDexLoader.getPrivateMethod(ActivityThread, "getApplication").invoke(thread);
                    if (a instanceof MainApplication)
                        return (MainApplication) a;
                }
            }
        } catch (Exception ignore) {
        }
        if (context instanceof ContextWrapper) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base != null)
                return from(base);
        }
        ApplicationInfo info = context.getApplicationInfo();
        if (info.className == null || info.className.isEmpty()) {
            throw new RuntimeException("manifest has no application value");
        } else {
            try {
                Class App = Class.forName(info.className);
                if (MainApplication.class.isAssignableFrom(App))
                    throw new RuntimeException("broken application context runtime"); // manifest is ok, but instance has no app
                else
                    throw new RuntimeException("manifest has no propper application value");
            } catch (ClassNotFoundException ignore) {
            }
            throw new RuntimeException("no application context");
        }
    }

    public static String formatTime(int tt) {
        return String.format("%02d", tt);
    }

    public static String formatSize(Context context, long s) {
        if (s > 0.1 * 1024 * 1024 * 1024) {
            float f = s / 1024f / 1024f / 1024f;
            return context.getString(R.string.size_gb, f);
        } else if (s > 0.1 * 1024 * 1024) {
            float f = s / 1024f / 1024f;
            return context.getString(R.string.size_mb, f);
        } else {
            float f = s / 1024f;
            return context.getString(R.string.size_kb, f);
        }
    }

    public static String formatDuration(Context context, long diff, boolean days, boolean hours, boolean ms) {
        int diffMilliseconds = (int) (diff % 1000);
        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        String str = "";

        if (diffDays > 0 || days)
            str = diffDays + context.getString(R.string.days_symbol) + " " + formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else if (diffHours > 0 || hours)
            str = formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else
            str = formatTime(diffMinutes) + ":" + formatTime(diffSeconds);

        if (ms)
            str += "." + diffMilliseconds;

        return str;
    }

    public static String formatDuration(Context context, long diff) {
        return formatDuration(context, diff, false, false, false);
    }

    public static String formatLeft(Context context, int diff) {
        String str = "";

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0)
            str = context.getResources().getQuantityString(R.plurals.days, diffDays, diffDays);
        else if (diffHours > 0)
            str = context.getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);
        else if (diffMinutes > 0)
            str = context.getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);
        else if (diffSeconds > 0)
            str = context.getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);

        return str;
    }

    public static String formatLeftExact(Context context, long diff) {
        String str = "";

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.days, diffDays, diffDays);

        if (diffHours > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);

        if (diffMinutes > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);

        if (diffDays == 0 && diffHours == 0 && diffMinutes == 0 && diffSeconds > 0)
            str += " " + context.getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);

        return str.trim();
    }

    public static int getTheme(Context context, String key, int light, int dark) {
        return getTheme(context, key, light, dark, -1);
    }

    public static int getTheme(Context context, String key, int light, int dark, int black) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(key, "");
        if (theme.isEmpty() || theme.equals(context.getString(R.string.Theme_System)))
            return getTheme(context, light, dark, black);
        if (theme.equals(context.getString(R.string.Theme_Dark))) {
            return dark;
        } else if (theme.equals(context.getString(R.string.Theme_Dark_Black))) {
            if (black != -1)
                return black;
            else
                return dark;
        }
        return light;
    }

    public static int getTheme(Context context, int light, int dark) {
        return getTheme(context, light, dark, -1);
    }

    public static int getTheme(Context context, int light, int dark, int black) {
        int system = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (system == Configuration.UI_MODE_NIGHT_YES) {
            if (black != -1) {
                String v = LineageSettings.System.getString(context.getContentResolver(), LineageSettings.BERRY_DARK_OVERLAY);
                if (v != null && v.equals(LineageSettings.OVERLAY_BLACK))
                    return black;
            }
            return dark;
        } else {
            return light;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ErrorDialog.unhandled(base, true);
        try { // MultiDex.install(this);
            Class.forName("android.support.multidex.MultiDex").getMethod("install", Context.class).invoke(null, this);
        } catch (Exception ignore) {
        }
        Prefs.create(base);
    }

    public int getVersion(String key, int id) {
        final SharedPreferences defaultValueSp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);
        if (!defaultValueSp.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            PreferenceManager.setDefaultValues(this, id, false);
            return -1;
        } else {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
            return shared.getInt(key, 0);
        }
    }
}
