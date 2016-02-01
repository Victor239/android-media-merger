package com.github.axet.mover;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

public class FileObserverService extends Service {

    private static final String TAG = "FileObserverService";

    public FileObserverService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ((MyApplication) getBaseContext().getApplicationContext()).start();

        return super.onStartCommand(intent, flags, startId);
    }
}
