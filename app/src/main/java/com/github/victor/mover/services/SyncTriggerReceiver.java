package com.github.victor.mover.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.github.victor.mover.app.MoverApplication;

/**
 * Trigger entry-point for scheduled-mode sync passes. Dispatches:
 *  - alarm firings (chains the next alarm, then starts a one-shot FGS)
 *  - manual "run now" requests from the UI / settings
 *  - mode/interval changes from the settings screen
 *
 * Only fired via explicit PendingIntent or in-process broadcasts; not exported.
 */
public class SyncTriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "SyncTriggerReceiver";

    public static final String ACTION_ALARM_SYNC =
            SyncTriggerReceiver.class.getCanonicalName() + ".ALARM_SYNC";
    public static final String ACTION_RUN_NOW =
            SyncTriggerReceiver.class.getCanonicalName() + ".RUN_NOW";
    public static final String ACTION_MODE_CHANGED =
            SyncTriggerReceiver.class.getCanonicalName() + ".MODE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onReceive action=" + action);
        if (action == null) return;

        if (ACTION_ALARM_SYNC.equals(action)) {
            // Chain the next alarm BEFORE starting work, so a crash in the
            // FGS doesn't leave us un-scheduled.
            SyncScheduler.rescheduleNext(context);
            triggerSyncIfEnabled(context);
        } else if (ACTION_RUN_NOW.equals(action)) {
            triggerSyncIfEnabled(context);
        } else if (ACTION_MODE_CHANGED.equals(action)) {
            applyModeChange(context);
        } else {
            Log.w(TAG, "Unknown action: " + action);
        }
    }

    private static void triggerSyncIfEnabled(Context context) {
        if (!MoverService.isEnabled(context)) {
            Log.d(TAG, "Service not enabled / no storage; skipping sync");
            return;
        }
        // ACTION_SYNC_ONCE path returns START_NOT_STICKY and self-stops after one pass.
        Intent svc = new Intent(context, MoverService.class)
                .setAction(MoverService.ACTION_SYNC_ONCE);
        try {
            ContextCompat.startForegroundService(context, svc);
        } catch (Exception e) {
            // Includes ForegroundServiceStartNotAllowedException on API 31+.
            // Receiver-from-alarm is normally exempt, but background restrictions
            // (battery saver, App Standby) can still block. Try a regular
            // startService as a best-effort fallback; if even that fails, the
            // next alarm fire will retry.
            Log.w(TAG, "startForegroundService failed: " + e);
            try {
                context.startService(svc);
            } catch (Exception ee) {
                Log.e(TAG, "startService fallback also failed", ee);
            }
        }
    }

    private static void applyModeChange(Context context) {
        // Cancel any pending alarm + content-trigger job; re-arm based on current prefs.
        SyncScheduler.cancel(context);
        if (Build.VERSION.SDK_INT >= 24) {
            SyncMediaJobService.disable(context);
        }
        if (!MoverService.isEnabled(context)) {
            return;
        }
        if (MoverService.isScheduledMode(context)) {
            // Stop any live-mode FGS that may be running.
            MoverService.stop(context);
            SyncScheduler.rescheduleNext(context);
            if (Build.VERSION.SDK_INT >= 24) {
                SyncMediaJobService.enable(context);
            }
        } else {
            // Live mode: kick the always-on FGS.
            MoverService.start(context);
        }
    }
}
