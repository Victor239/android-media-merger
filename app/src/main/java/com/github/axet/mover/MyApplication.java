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

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String syncConnPref = sharedPref.getString("storage", new File(Environment.getExternalStorageDirectory(), "/private/mobile").getPath());

        sharedPref.edit().putString("storage", syncConnPref).commit();

        mCamera = new Camera(this, new File(syncConnPref));
    }

    public void startFileObserver() {
        Intent myIntent = new Intent(this, FileObserverService.class);
        this.startService(myIntent);
    }
}
