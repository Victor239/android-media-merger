package com.github.axet.mover.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.mover.app.MyApplication;

public class StartServiceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ((MyApplication) context.getApplicationContext()).start();
    }
}
