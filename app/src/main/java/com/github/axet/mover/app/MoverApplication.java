package com.github.axet.mover.app;

import android.app.Application;
import android.content.Intent;

import com.github.axet.mover.services.FileObserverService;

public class MoverApplication extends Application {

    public static final String STORAGE = "storage";

    public static final String AUTO_COUNT = "AUTO_COUNT";
    public static final String AUTO_PREFIX = "AUTO_";
    public static final String AUTO_ENABLED = "_ENABLED";
    public static final String AUTO_PATH = "_PATH";

    public static final String MANUAL_COUNT = "MANUAL_COUNT";
    public static final String MANUAL_PREFIX = "MANUAL_";
    public static final String MANUAL_PATH = "_PATH";

    @Override
    public void onCreate() {
        super.onCreate();

        FileObserverService.start(this);
    }
}
