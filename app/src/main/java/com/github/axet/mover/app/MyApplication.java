package com.github.axet.mover.app;

import android.app.Application;
import android.content.Intent;

import com.github.axet.mover.services.FileObserverService;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        start();
    }

    public void start() {
        Intent myIntent = new Intent(this, FileObserverService.class);
        startService(myIntent);
    }
}
