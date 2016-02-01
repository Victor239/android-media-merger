package com.github.axet.mover;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

public class MyApplication extends Application {

    Camera mCamera;

    @Override
    public void onCreate() {
        super.onCreate();

        create();
    }

    void create() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String syncConnPref = sharedPref.getString("storage", null);

        if (syncConnPref != null)
            mCamera = new Camera(this, new File(syncConnPref));
    }

    void start() {
        if (mCamera == null)
            create();
        if (mCamera == null)
            return;

        mCamera.readDirectories();
        mCamera.moveDir();
    }

    public void startFileObserver() {
        Intent myIntent = new Intent(this, FileObserverService.class);
        this.startService(myIntent);
    }
}
