package com.github.axet.mover.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.github.axet.mover.app.Camera;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileObserverService extends Service {
    private static final String TAG = FileObserverService.class.getSimpleName();

    public static final String EMPTY = FileObserverService.class.getCanonicalName() + ".EMPTY";
    public static final String FOLDERS = FileObserverService.class.getCanonicalName() + ".FOLDERS";

    Camera camera;

    public FileObserverService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    String[] toArray(List<File> list) {
        List<String> l = new ArrayList<>();
        for (File f : list) {
            l.add(f.toString());
        }
        return l.toArray(new String[]{});
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String syncConnPref = sharedPref.getString("storage", null);

        if (syncConnPref != null) {
            if (camera != null)
                camera.close();

            camera = new Camera(this, new File(syncConnPref));
            camera.create();

            Intent i = new Intent(FOLDERS);

            if (camera.getFolders().isEmpty()) {
                i.putExtra("folders", toArray(camera.getMainFolders()));
            } else {
                i.putExtra("folders", toArray(camera.getFolders()));
            }
            i.putExtra("target", camera.getTargetDir().toString());
            sendBroadcast(i);

            return super.onStartCommand(intent, flags, startId);
        } else {
            Intent i = new Intent(EMPTY);
            sendBroadcast(i);

            stopSelf();
            return START_NOT_STICKY;
        }
    }
}
