package com.github.axet.mover.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.mover.app.MyApplication;

/**
 *
 * http://stackoverflow.com/questions/2133986/how-to-know-my-android-application-has-been-upgraded-in-order-to-reset-an-alarm
 */
public class OnUpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ((MyApplication)context.getApplicationContext()).start();
    }
}
