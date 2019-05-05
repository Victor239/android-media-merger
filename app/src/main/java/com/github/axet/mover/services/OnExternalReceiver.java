package com.github.axet.mover.services;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class OnExternalReceiver extends com.github.axet.androidlibrary.services.OnExternalReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isExternal(context))
            return;
        FileObserverService.startIfEnabled(context);
    }
}
