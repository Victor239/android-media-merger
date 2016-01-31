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

    Camera mCamera;

    public FileObserverService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mCamera == null)
            mCamera = new Camera(this, new File(Environment.getExternalStorageDirectory() + "/private/mobile"));

        mCamera.readDirectories();
        mCamera.moveDir();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}