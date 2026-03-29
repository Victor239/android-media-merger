package com.github.axet.androidlibrary.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

public class Firebase {
    public static final String FirebaseInitProvider = "com.google.firebase.provider.FirebaseInitProvider";

    public static boolean isEnabled(Context context) {
        try {
            Class GoogleApiAvailability = Class.forName("com.google.android.gms.common.GoogleApiAvailability");
            Method getInstance = GoogleApiAvailability.getDeclaredMethod("getInstance");
            Method isGooglePlayServicesAvailable = GoogleApiAvailability.getDeclaredMethod("isGooglePlayServicesAvailable", Context.class);
            isGooglePlayServicesAvailable.invoke(getInstance.invoke(null), context);
            Class ConnectionResult = Class.forName("com.google.android.gms.common.ConnectionResult");
            int SUCCESS = (int) ConnectionResult.getDeclaredField("SUCCESS").get(null);
            if ((int) isGooglePlayServicesAvailable.invoke(getInstance.invoke(null), context) != SUCCESS)
                return false;
        } catch (Exception ignore) {
        }
        ComponentName name = new ComponentName(context, FirebaseInitProvider);
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(name) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            return false;
        try {
            if (!pm.getProviderInfo(name, PackageManager.MATCH_DEFAULT_ONLY).enabled)
                return false;
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
        return true;
    }

    public static void setLogLevel(Context context, Object level) {
        try {
            Class FirebaseApp = Class.forName("com.google.firebase.FirebaseApp");
            Class FirebaseDatabase = Class.forName("com.google.firebase.database.FirebaseDatabase");
            Class Level = Class.forName("com.google.firebase.database.Logger$Level");
            FirebaseApp.getDeclaredMethod("initializeApp", Context.class).invoke(null, context); // to make setLogLevel call works
            if (level == null)
                level = Enum.valueOf(Level, "DEBUG");
            FirebaseDatabase.getDeclaredMethod("setLogLevel", Level).invoke(FirebaseDatabase.getDeclaredMethod("getInstance").invoke(null), level);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void attachBaseContext(Context context) {
        boolean detectedFirebase_10_2 = false;
        try {
            Class FirebaseDatabase = Class.forName("com.google.firebase.database.FirebaseDatabase");
            FirebaseDatabase.getMethod("getInstance", String.class); // new method added FirebaseDatabase.getInstance(String url)
            detectedFirebase_10_2 = true;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException ignore) {
        }
        if (Build.VERSION.SDK_INT < 14 && detectedFirebase_10_2) { // disable firebase for API14< and firebase10.2+
            ComponentName name = new ComponentName(context, FirebaseInitProvider);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
    }
}
