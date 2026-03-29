package com.github.axet.androidlibrary.services;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

//    <receiver android:name=".services.OnBootReceiver">
//        <intent-filter>
//            <action android:name="android.intent.action.BOOT_COMPLETED" />
//            <action android:name="android.intent.action.QUICKBOOT_POWERON" />
//            <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
//            <action android:name=".ACTION_START" />
//        </intent-filter>
//    </receiver>
public class OnBootReceiver extends BroadcastReceiver {
    public static final String TAG = OnBootReceiver.class.getSimpleName();

    public static void setComponentEnabled(Context context, Class<?> klass, boolean b) { // adb shell "pm disable com.github.axet.smsgate/.services.ImapSmsReplyService"
        ComponentName name = new ComponentName(context, klass);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name, b ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive " + intent);
    }
}