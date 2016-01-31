package com.github.axet.mover;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartServiceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent myIntent = new Intent(context, FileObserverService.class);
        context.startService(myIntent);
    }
}
