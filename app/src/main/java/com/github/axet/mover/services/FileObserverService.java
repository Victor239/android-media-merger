package com.github.axet.mover.services;

import android.Manifest;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.mover.app.Camera;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class FileObserverService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = FileObserverService.class.getSimpleName();

    public static String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static final String STOP = FileObserverService.class.getCanonicalName() + ".STOP";
    public static final String UPDATE = FileObserverService.class.getCanonicalName() + ".UPDATE";

    public static String[] toArray(List<File> list) {
        List<String> l = new ArrayList<>();
        for (File f : list) {
            l.add(f.toString());
        }
        return l.toArray(new String[]{});
    }

    CameraMan camera;

    OptimizationPreferenceCompat.ServiceReceiver optimization;

    public class CameraMan extends Camera {
        public CameraMan(Context context, Uri target) {
            super(context, target);
        }

        public void updatePrefs() {
            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

            TreeMap<String, Boolean> map = new TreeMap<>();

            // read dir's from sdcard
            {
                ArrayList<File> dirs = generateDcim();
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
        public void sync() {
            super.sync();
            updatePrefs();
        }

        @Override
        public ArrayList<Uri> generateDirs() {
            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

            ArrayList<Uri> dirs = super.generateDirs();

            // remove all disabled path's
            int c = sharedPref.getInt(MoverApplication.AUTO_COUNT, 0);
            for (int i = 0; i < c; i++) {
                boolean b = sharedPref.getBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, true);
                String s = sharedPref.getString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, "");
                if (!b) {
                    File f = new File(s);
                    dirs.remove(Uri.fromFile(f));
                }
            }

            // add all manual path's
            c = sharedPref.getInt(MoverApplication.MANUAL_COUNT, 0);
            for (int i = 0; i < c; i++) {
                String s = sharedPref.getString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, "");
                Uri u;
                if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    u = Uri.parse(s);
                } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                    u = Uri.parse(s);
                } else {
                    u = Uri.fromFile(new File(s));
                }
                dirs.add(u);
            }

            return dirs;
        }
    }

    public static boolean isEnabled(Context context) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = sharedPref.getBoolean(MoverApplication.ENABLED, false);
        return isEnabled(context, b);
    }

    public static boolean isEnabled(Context context, boolean b) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        if (!b)
            return false;
        if (!Storage.permitted(context, PERMISSIONS))
            return false;
        Storage storage = new Storage(context);
        String path = sharedPref.getString(MoverApplication.STORAGE, null);
        Uri u = storage.getStoragePath(path);
        if (u == null)
            return false;
        Uri local = Uri.fromFile(storage.getLocalStorage());
        if (u.equals(local))
            return false;
        return true;
    }

    public static void startIfEnabled(Context context) {
        if (!isEnabled(context))
            return;
        Intent myIntent = new Intent(context, FileObserverService.class);
        context.startService(myIntent);
    }

    public static void update(Context context) {
        Intent myIntent = new Intent(context, FileObserverService.class);
        myIntent.setAction(UPDATE);
        context.startService(myIntent);
    }

    public FileObserverService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, getClass(), MoverApplication.PREFERENCE_OPTIMIZATION) {
            @Override
            public void check() { // disable application chek (here is no application)
                if (camera != null)
                    camera.sync();
            }

            @Override
            public void register() {
                super.register();
                OptimizationPreferenceCompat.setKillCheck(context, next, MoverApplication.PREFERENCE_LAST);
            }
        };

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);

        start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory()");
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
        if (optimization != null) {
            optimization.close();
            optimization = null;
        }
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand " + intent);
        if (optimization.onStartCommand(intent, flags, startId)) {
            return startCamera(intent, flags, startId);
        }

        String a = intent.getAction();
        if (a == null) {
            return startCamera(intent, flags, startId);
        }

        if (a.equals(UPDATE)) {
            return startCamera(intent, flags, startId);
        }

        return startCamera(intent, flags, startId);
    }

    int startCamera(Intent intent, int flags, int startId) {
        if (camera == null) {
            stopSelf();
            return START_NOT_STICKY;
        } else {
            if (start()) {
                return super.onStartCommand(intent, flags, startId);
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
    }

    boolean start() {
        if (camera != null) {
            camera.close();
            camera = null;
        }
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean enabled = sharedPref.getBoolean(MoverApplication.ENABLED, true);
        String storage = sharedPref.getString(MoverApplication.STORAGE, null);
        Storage s = new Storage(this);
        Uri u = s.getStoragePath(storage);
        if (enabled && u != null) {
            camera = new CameraMan(this, u);
            camera.create();

            Intent i = new Intent(UPDATE);
            sendBroadcast(i);
            return true;
        } else {
            CameraMan man = new CameraMan(this, null);
            man.updatePrefs();
            man.close();

            Intent i = new Intent(STOP);
            sendBroadcast(i);
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!start()) {
            stopSelf();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        optimization.onTaskRemoved(rootIntent);
    }
}
