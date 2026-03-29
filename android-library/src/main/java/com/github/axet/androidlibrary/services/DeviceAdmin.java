package com.github.axet.androidlibrary.services;

import android.annotation.TargetApi;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.github.axet.androidlibrary.app.SuperUser;
import com.github.axet.androidlibrary.widgets.Toast;

// adb shell dpm set-device-owner com.github.axet.admin/com.github.axet.androidlibrary.services.DeviceAdmin
//
// <receiver
//    android:name="com.github.axet.androidlibrary.services.DeviceAdmin"
//    android:description="@string/app_description"
//    android:label="@string/app_name"
//    android:permission="android.permission.BIND_DEVICE_ADMIN">
//    <meta-data
//        android:name="android.app.device_admin"
//        android:resource="@xml/device_admin" />
//    <intent-filter>
//        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
//        <action android:name="android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED" />
//        <action android:name="android.intent.action.REBOOT" />
//    </intent-filter>
// </receiver>
//
public class DeviceAdmin extends DeviceAdminReceiver {
    public static String TAG = DeviceAdmin.class.getCanonicalName();

    public static final String DEVICE_ADMIN = "android.app.device_admin";

    // adb shell am broadcast -n com.github.axet.admin/com.github.axet.androidlibrary.services.DeviceAdmin -a android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED
    @TargetApi(24)
    public static void removeDeviceOwner(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.isProfileOwnerApp(context.getPackageName())) {
            ComponentName c = new ComponentName(context, DeviceAdmin.class);
            dpm.clearProfileOwner(c);
        }
        if (dpm.isDeviceOwnerApp(context.getPackageName())) {
            dpm.clearDeviceOwnerApp(context.getPackageName());
        }
    }

    // adb shell am broadcast -n com.github.axet.admin/com.github.axet.androidlibrary.services.DeviceAdmin -a android.intent.action.REBOOT
    public static void reboot(Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName c = new ComponentName(context, DeviceAdmin.class);
            if (dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.reboot(c);
                return;
            }
        }
        SuperUser.Result r = SuperUser.reboot();
        if (!r.ok()) {
            Exception e = r.errno();
            Log.d(TAG, "Unable reboot: ", e);
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void wipe(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName c = new ComponentName(context, DeviceAdmin.class);
        if (dpm.isAdminActive(c)) {
            dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
        }
    }

    public static void lock(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName c = new ComponentName(context, DeviceAdmin.class);
        if (dpm.isAdminActive(c)) {
            dpm.lockNow();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent);
        super.onReceive(context, intent);
        if (intent == null)
            return;
        String a = intent.getAction();
        if (a == null)
            return;
        if (a.equals(Intent.ACTION_REBOOT)) {
            reboot(context);
        }
        if (a.equals(ACTION_DEVICE_ADMIN_DISABLE_REQUESTED)) {
            removeDeviceOwner(context);
        }
    }

}
