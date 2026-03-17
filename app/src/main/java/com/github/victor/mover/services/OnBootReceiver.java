package com.github.victor.mover.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.victor.mover.app.MoverApplication;

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        OptimizationPreferenceCompat.setPrefTime(context, MoverApplication.PREFERENCE_BOOT, System.currentTimeMillis());
        MoverService.startIfEnabled(context);
    }
}
