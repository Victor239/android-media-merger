package com.github.victor.mover.services;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.services.PersistentService;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.victor.mover.R;
import com.github.victor.mover.app.Camera;
import com.github.victor.mover.app.MoverApplication;
import com.github.victor.mover.app.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeMap;

public class MoverService extends PersistentService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = MoverService.class.getSimpleName();

    public static int NOTIFICATION_ICON = 200;

    public static String[] PERMISSIONS = Storage.PERMISSIONS_RW;

    public static final String STOP = MoverService.class.getCanonicalName() + ".STOP";
    public static final String UPDATE = MoverService.class.getCanonicalName() + ".UPDATE";

    CameraMan camera;

    public static void start(Context context) {
        start(context, new Intent(context, MoverService.class));
    }

    public static void stop(Context context) {
        stop(context, new Intent(context, MoverService.class));
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
        if (Storage.isLegacyRequred(context)) {
            ; // no permission check
        } else if (!Storage.permitted(context, PERMISSIONS))
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
        start(context);
    }

    public static void update(Context context) {
        Intent intent = new Intent(context, MoverService.class).setAction(UPDATE);
        context.startService(intent);
    }

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
                dirs.add(SCREENSHOTS_PATH);
                for (File f : dirs)
                    map.put(f.toString(), true);
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
            edit.apply();
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
                if (s.startsWith(ContentResolver.SCHEME_CONTENT))
                    u = Uri.parse(s);
                else if (s.startsWith(ContentResolver.SCHEME_FILE))
                    u = Uri.parse(s);
                else
                    u = Uri.fromFile(new File(s));
                dirs.add(u);
            }

            return dirs;
        }
    }

    public MoverService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate - initializing service");

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);

        // Initialize the service - will run as foreground service with notification
        start();
    }

    @Override
    public void onCreateOptimization() {
        // Create foreground service notification handler
        // This runs the service as a foreground service with persistent notification
        // ensuring it stays alive in the background
        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, NOTIFICATION_ICON, MoverApplication.PREFERENCE_OPTIMIZATION, MoverApplication.PREFERENCE_NEXT) {
            @Override
            public void check() {
                // Periodic check to sync files
                if (camera != null)
                    camera.sync();
            }

            @Override
            public Notification build(Intent intent) {
                // Build the persistent foreground service notification
                return new OptimizationPreferenceCompat.PersistentIconBuilder(context)
                        .create(MoverApplication.getTheme(context, R.style.AppThemeLight, R.style.AppThemeDark), MoverApplication.from(context).channelStatus)
                        .setAdaptiveIcon(R.drawable.ic_launcher_foreground)
                        .setSmallIcon(R.drawable.ic_launcher_notification)
                        .build();
            }
        };
        optimization.create();
        Log.d(TAG, "Foreground service notification created");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved - service will be restarted by START_STICKY");
        // Service will be restarted automatically due to START_STICKY
        // No need to explicitly restart here
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - service destroyed");
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
        if (camera != null) {
            camera.close();
            camera = null;
        }

        // If service was running when destroyed, schedule restart
        if (isEnabled(this)) {
            Log.d(TAG, "Service was enabled, will restart due to START_STICKY");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand " + intent);
        if (optimization != null)
            optimization.onStartCommand(intent, flags, startId);
        return startIntent(intent, flags, startId);
    }

    /**
     * Handle service start and determine restart behavior
     * @return START_STICKY to automatically restart if killed, START_NOT_STICKY otherwise
     */
    int startIntent(Intent intent, int flags, int startId) {
        if (start()) {
            // Service is enabled and camera is active
            // Return START_STICKY so Android will restart the service if it's killed
            // This keeps the service running in the background even when app is closed
            Log.d(TAG, "Service active, returning START_STICKY for auto-restart");
            return START_STICKY;
        } else {
            // Service is disabled or has no storage path configured
            // Stop self since there's nothing to do
            Log.d(TAG, "Service not needed, stopping");
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /**
     * Initialize or restart the file monitoring service
     * @return true if service is enabled and has valid storage path, false otherwise
     */
    boolean start() {
        // Clean up existing camera watcher if any
        if (camera != null) {
            camera.close();
            camera = null;
        }

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = sharedPref.getBoolean(MoverApplication.ENABLED, false);
        String storage = sharedPref.getString(MoverApplication.STORAGE, null);
        Storage s = new Storage(this);
        Uri u = s.getStoragePath(storage);

        if (enabled && u != null) {
            // Service is enabled with valid storage path - start monitoring
            Log.d(TAG, "Starting file monitoring service");
            camera = new CameraMan(this, u);
            camera.create();
            Intent i = new Intent(UPDATE);
            sendBroadcast(i);
            return true;
        } else {
            // Service is disabled or no storage path configured
            Log.d(TAG, "Service disabled or no storage path");
            CameraMan camera = new CameraMan(this, null);
            camera.updatePrefs();
            camera.close();
            Intent i = new Intent(STOP);
            sendBroadcast(i);
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!start())
            stopSelf();
    }
}
