package com.github.victor.mover.services;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
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
    public static int NOTIFICATION_QUOTA = 201;

    public static String[] PERMISSIONS = Storage.PERMISSIONS_RW;

    public static final String STOP = MoverService.class.getCanonicalName() + ".STOP";
    public static final String UPDATE = MoverService.class.getCanonicalName() + ".UPDATE";

    // Thread that owns all camera management. Main thread only dispatches to it.
    HandlerThread serviceThread;
    Handler serviceHandler;

    // Accessed only from serviceHandler thread.
    CameraMan camera;

    // Debounced restart runnable — posted to serviceHandler on pref changes.
    // Wrapped in try-catch(Throwable) to prevent serviceHandler thread death on unexpected errors.
    final Runnable restartRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!start())
                    stopSelf();
            } catch (Throwable t) {
                Log.e(TAG, "restart failed", t);
                stopSelf();
            }
        }
    };

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
        // Create serviceThread BEFORE super.onCreate() so serviceHandler is available
        // in onCreateOptimization() (called from super.onCreate()). Previously this was
        // created after super.onCreate(), leaving serviceHandler null during construction.
        serviceThread = new HandlerThread("MoverService-manager");
        serviceThread.setDaemon(true); // daemon: don't block JVM shutdown if process is killed
        serviceThread.start();
        serviceHandler = new Handler(serviceThread.getLooper());

        super.onCreate(); // calls onCreateOptimization() → posts create()+start() to serviceHandler

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        // Note: start() is now posted inside onCreateOptimization(), not here.
    }

    @Override
    public void onCreateOptimization() {
        try {
        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, NOTIFICATION_ICON, MoverApplication.PREFERENCE_OPTIMIZATION, MoverApplication.PREFERENCE_NEXT) {
            @Override
            public void check() {
                // Periodic check — delegate to serviceHandler so camera access is thread-safe.
                serviceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (camera != null)
                                camera.sync();
                        } catch (Throwable t) {
                            Log.e(TAG, "check failed", t);
                        }
                    }
                });
            }

            @Override
            public Notification build(Intent intent) {
                // Fast path: no PackageManager.getLaunchIntentForPackage() IPC.
                // The constructor calls this via icon.create() → startForeground(),
                // satisfying the 5-second FGS deadline without any Binder blocking.
                return new NotificationCompat.Builder(
                        MoverService.this,
                        MoverApplication.from(MoverService.this).channelStatus.channelId)
                        .setSmallIcon(R.drawable.ic_launcher_notification)
                        .setOngoing(true)
                        .build();
            }
        };
        // Constructor has already called startForeground() via our fast build() above.
        // Defer all blocking init to serviceHandler:
        //   optimization.create() — PackageManager.setComponentEnabledSetting(),
        //                           PowerManager.isIgnoringBatteryOptimizations(),
        //                           AlarmManager.set(), registerReceiver()
        // FIFO ordering guarantees create() completes before any onStartCommand() work.
        serviceHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    optimization.create();
                } catch (Throwable t) {
                    Log.e(TAG, "optimization.create() failed", t);
                }
                try {
                    start();
                } catch (Throwable t) {
                    Log.e(TAG, "initial start() failed", t);
                    stopSelf();
                }
            }
        });
        } catch (Throwable t) {
            // ForegroundServiceStartNotAllowedException or any other creation failure.
            // Stop cleanly — no crash, no zombie process, no ANR.
            Log.e(TAG, "Service creation failed, stopping: " + t);
            showServiceStoppedNotification(t);
            stopSelf();
            return;
        }
        Log.d(TAG, "Foreground service notification created");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved - service will be restarted by START_STICKY");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy - service destroyed");

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);

        serviceHandler.removeCallbacks(restartRunnable);

        // Null optimization BEFORE super.onDestroy() so PersistentService.onDestroy() skips it.
        // Close it here on the main thread — fast: unregisterReceiver + stopForeground + am.cancel.
        OptimizationPreferenceCompat.ServiceReceiver capturedOpt = optimization;
        optimization = null;
        if (capturedOpt != null) {
            try {
                capturedOpt.close(); // may throw IllegalArgumentException if create() not yet run
            } catch (Throwable t) {
                Log.e(TAG, "optimization.close() failed", t);
            }
        }

        serviceHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (camera != null) {
                        camera.close();
                        camera = null;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "camera close failed", t);
                }
                serviceThread.quitSafely();
            }
        });

        super.onDestroy(); // optimization == null here — PersistentService safely skips close
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand " + intent);
        // Post ALL work to serviceHandler — return START_STICKY with zero main-thread blocking.
        // optimization.onStartCommand() → PowerManager + AlarmManager IPC now runs off main thread.
        // FIFO ordering guarantees optimization.create() has run before this executes.
        final Intent capturedIntent = intent;
        final int capturedFlags = flags;
        final int capturedStartId = startId;
        serviceHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (optimization != null)
                        optimization.onStartCommand(capturedIntent, capturedFlags, capturedStartId);
                    startIntent(capturedIntent, capturedFlags, capturedStartId);
                } catch (Throwable t) {
                    Log.e(TAG, "onStartCommand handler failed", t);
                }
            }
        });
        return START_STICKY;
    }

    /**
     * Runs on serviceHandler thread. Determines whether the service should keep running.
     * Wrapped in try-catch at call sites to prevent serviceHandler thread death.
     */
    void startIntent(Intent intent, int flags, int startId) {
        try {
            if (!start()) {
                Log.d(TAG, "Service not needed, stopping");
                stopSelf();
            }
        } catch (Throwable t) {
            Log.e(TAG, "startIntent failed", t);
            stopSelf();
        }
    }

    /**
     * Initialize or restart the file monitoring service.
     * Must be called only from serviceHandler thread.
     * @return true if service is enabled and has valid storage path, false otherwise
     */
    boolean start() {
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
            Log.d(TAG, "Starting file monitoring service");
            camera = new CameraMan(this, u);
            camera.create();
            Intent i = new Intent(UPDATE);
            sendBroadcast(i);
            return true;
        } else {
            Log.d(TAG, "Service disabled or no storage path");
            CameraMan camera = new CameraMan(this, null);
            camera.updatePrefs();
            camera.close();
            Intent i = new Intent(STOP);
            sendBroadcast(i);
            return false;
        }
    }

    private void showServiceStoppedNotification(Throwable t) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        if (!(t instanceof android.app.ForegroundServiceStartNotAllowedException)) return;
        try {
            androidx.core.app.NotificationManagerCompat nm = androidx.core.app.NotificationManagerCompat.from(this);
            android.app.Notification n = new NotificationCompat.Builder(
                    this, MoverApplication.from(this).channelStatus.channelId)
                    .setSmallIcon(R.drawable.ic_launcher_notification)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.service_quota_exhausted))
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTIFICATION_QUOTA, n);
        } catch (Throwable ex) {
            Log.e(TAG, "showServiceStoppedNotification failed", ex);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Debounce: cancel any pending restart and schedule a new one after 200ms.
        // This prevents rapid Camera create/close churn when multiple prefs change at once.
        serviceHandler.removeCallbacks(restartRunnable);
        serviceHandler.postDelayed(restartRunnable, 200);
    }
}
