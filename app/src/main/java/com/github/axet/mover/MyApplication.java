package com.github.axet.mover;

import android.app.Application;
import android.content.Intent;

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
