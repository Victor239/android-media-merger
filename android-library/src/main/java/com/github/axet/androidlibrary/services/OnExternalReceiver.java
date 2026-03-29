package com.github.axet.androidlibrary.services;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.Network;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;

import java.io.File;

public class OnExternalReceiver extends BroadcastReceiver {
    public static final String TAG = OnExternalReceiver.class.getSimpleName();

    public static boolean isExternal(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            return isExternal(info.applicationInfo);
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return false;
    }

    public static boolean isExternal(ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
    }

    public static boolean mountTest(Context context) { // test sdcard and internet access on sdcard (failed mount)
        if (!OnExternalReceiver.isExternal(context))
            return true; // bug happened only when app installed on external sdcard
        File file = context.getExternalCacheDir();
        if (file != null && file.exists() && file.canRead() && !file.canWrite())
            return false;
        if (OptimizationPreferenceCompat.findPermission(context, Manifest.permission.INTERNET) && !Network.pingLocal())
            return false;
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive " + intent);
        if (!isExternal(context))
            return;
        onBootReceived(context);
    }

    public void onBootReceived(Context context) { // android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE received on external app == ON BOOT EVENT
    }
}
