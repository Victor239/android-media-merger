package com.github.axet.mover.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.github.axet.mover.app.Camera;
import com.github.axet.mover.app.MoverApplication;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

public class FileObserverService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = FileObserverService.class.getSimpleName();

    public static final String STOP = FileObserverService.class.getCanonicalName() + ".STOP";
    public static final String UPDATE = FileObserverService.class.getCanonicalName() + ".UPDATE";

    Camera camera;

    public static void start(Context context) {
        Intent myIntent = new Intent(context, FileObserverService.class);
        context.startService(myIntent);
    }

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
    public void onCreate() {
        super.onCreate();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (start()) {
            return super.onStartCommand(intent, flags, startId);
        } else {
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    boolean start() {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String storage = sharedPref.getString(MoverApplication.STORAGE, null);

        if (storage != null) {
            if (camera != null)
                camera.close();

            camera = new Camera(this, new File(storage)) {
                @Override
                public void sync() {
                    super.sync();

                    TreeMap<String, Boolean> map = new TreeMap<>();

                    // read dir's from sdcard
                    {
                        ArrayList<File> dirs = readDcim();
                        dirs.add(screenshotsPath);
                        for (File f : dirs) {
                            map.put(f.toString(), true);
                        }
                    }

                    // update status on remaining directories only. forget settings for gone directories
                    int c = sharedPref.getInt(MoverApplication.AUTO_COUNT, 0);
                    for (int i = 0; i < c; i++) {
                        String s = sharedPref.getString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, "");
                        boolean b = sharedPref.getBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, true);
                        if (map.containsKey(s)) {
                            map.put(s, b);
                        }
                    }

                    // save new dir list
                    SharedPreferences.Editor edit = sharedPref.edit();
                    String[] keys = map.keySet().toArray(new String[]{});
                    c = keys.length;
                    edit.putInt(MoverApplication.AUTO_COUNT, c);
                    for (int i = 0; i < c; i++) {
                        String key = keys[i];
                        edit.putString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, key);
                        edit.putBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, map.get(key));
                    }
                    edit.commit();
                }

                @Override
                public ArrayList<File> readDirs() {
                    ArrayList<File> dirs = super.readDirs();

                    // remove all disabled path's
                    int c = sharedPref.getInt(MoverApplication.AUTO_COUNT, 0);
                    for (int i = 0; i < c; i++) {
                        boolean b = sharedPref.getBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, true);
                        String s = sharedPref.getString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, "");
                        if (!b) {
                            dirs.remove(new File(s));
                        }
                    }

                    // add all manual path's
                    c = sharedPref.getInt(MoverApplication.MANUAL_COUNT, 0);
                    for (int i = 0; i < c; i++) {
                        String s = sharedPref.getString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, "");
                        dirs.add(new File(s));
                    }

                    return dirs;
                }
            };
            camera.create();

            Intent i = new Intent(UPDATE);
            sendBroadcast(i);
            return true;
        } else {
            Intent i = new Intent(STOP);
            sendBroadcast(i);
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        start();
    }
}
