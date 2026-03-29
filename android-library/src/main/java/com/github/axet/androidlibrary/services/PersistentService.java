package com.github.axet.androidlibrary.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.util.Log;

import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;

// Several services types available:
//
// 1) AppService (Service Running until app exits: Torrent Client)
//    - Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null)
// 2) EventService (service depends on AlarmManager to start, Hourly Reminder)
//    - Battery Optimization settings
//    - Persistent Icon option (PersistentService.isPersistent mandatory call)
// 3) PersistentService (Call Recorder / Media Merger)
//    - Battery Optimization settings
//    - Persistent Icon option (OptimizationPreferenceCompat.setIcon() mandatory call)
// 4) LongOperationService (Audio Recorder)
//    - No Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null, ServiceReceiver.isOptimization() {return true})
// 5) LongOperationService no kill check (Hourly Reminder: FireAlarmService)
//    - No Battery Optimization settings
//    - No Persistent Icon option (override PersistentService.updateIcon() to keep intent != null, override onCreateOptimization() {})
//
// We have to test: <API23, API23+, API26+
public class PersistentService extends Service {
    public static final String TAG = PersistentService.class.getSimpleName();

    protected OptimizationPreferenceCompat.ServiceReceiver optimization;

    public static void start(Context context, Intent intent) {
        OptimizationPreferenceCompat.startService(context, intent);
    }

    public static void stop(Context context, Intent intent) {
        context.stopService(intent);
    }

    public PersistentService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        onCreateOptimization();
    }

    public void onCreateOptimization() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (optimization != null) {
            optimization.close();
            optimization = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (optimization != null && optimization.onStartCommand(intent, flags, startId)) {
            Log.d(TAG, "onStartCommand restart"); // crash fail
            onRestartCommand();
        }
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);
            onStartCommand(intent);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void onRestartCommand() {
    }

    public void onStartCommand(Intent intent) {
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (optimization != null)
            optimization.onTaskRemoved(rootIntent);
    }
}
