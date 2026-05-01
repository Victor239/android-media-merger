package com.github.victor.mover.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.github.victor.mover.app.MoverApplication;

/**
 * Single source of truth for the periodic AlarmManager firing in scheduled mode.
 *
 * Uses inexact, non-wakeup RTC alarms so the device may batch firings with other
 * system events and remain in Doze when appropriate. The pending intent targets
 * {@link SyncTriggerReceiver} which restarts the alarm chain after each fire.
 */
public final class SyncScheduler {
    private static final String TAG = "SyncScheduler";

    private SyncScheduler() {}

    private static PendingIntent alarmPendingIntent(Context context) {
        Intent intent = new Intent(context, SyncTriggerReceiver.class)
                .setAction(SyncTriggerReceiver.ACTION_ALARM_SYNC);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /** Schedule (or reschedule) the next periodic sync based on user preferences. */
    public static void rescheduleNext(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        int minutes = getIntervalMinutes(sp);
        long when = System.currentTimeMillis() + minutes * 60_000L;
        scheduleAt(context, when);
        sp.edit().putLong(MoverApplication.PREFERENCE_NEXT, when).apply();
    }

    public static void scheduleAt(Context context, long whenMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pe = alarmPendingIntent(context);
        // RTC (NOT RTC_WAKEUP): inexact, Doze-deferrable, batched. The whole point of
        // this refactor is to stop forcing the device awake on a 15-min cadence.
        try {
            am.set(AlarmManager.RTC, whenMs, pe);
            Log.d(TAG, "Next sync scheduled at " + whenMs);
        } catch (SecurityException e) {
            // Should not happen for inexact RTC, but defensively log.
            Log.e(TAG, "Failed to schedule alarm", e);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarmPendingIntent(context));
        Log.d(TAG, "Sync alarm cancelled");
    }

    public static int getIntervalMinutes(SharedPreferences sp) {
        String s = sp.getString(MoverApplication.PREFERENCE_SCHEDULE_INTERVAL,
                String.valueOf(MoverApplication.DEFAULT_INTERVAL_MIN));
        try {
            int i = Integer.parseInt(s);
            return i > 0 ? i : MoverApplication.DEFAULT_INTERVAL_MIN;
        } catch (NumberFormatException e) {
            return MoverApplication.DEFAULT_INTERVAL_MIN;
        }
    }
}
