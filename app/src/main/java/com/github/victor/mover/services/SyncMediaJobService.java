package com.github.victor.mover.services;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Content-URI fast path for scheduled mode (API 24+).
 *
 * Wakes the app immediately when MediaStore Images / Video tables change so
 * newly captured photos/videos move within seconds, without keeping a
 * resident foreground service. Each fire re-arms itself (Android single-shots
 * content jobs).
 */
@TargetApi(24)
public class SyncMediaJobService extends JobService {
    private static final String TAG = "SyncMediaJobService";

    public static final int JOB_ID = 4711;

    public static void enable(Context context) {
        if (Build.VERSION.SDK_INT < 24) return;
        scheduleSelf(context);
    }

    public static void disable(Context context) {
        if (Build.VERSION.SDK_INT < 24) return;
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.cancel(JOB_ID);
    }

    @TargetApi(24)
    private static void scheduleSelf(Context context) {
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        ComponentName component = new ComponentName(context, SyncMediaJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, component)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                // Debounce burst-mode bursts; system may delay a bit longer to batch.
                .setTriggerContentUpdateDelay(2_000L)
                .setTriggerContentMaxDelay(10_000L);

        try {
            int result = js.schedule(builder.build());
            if (result != JobScheduler.RESULT_SUCCESS)
                Log.w(TAG, "schedule returned " + result);
            else
                Log.d(TAG, "Content-trigger job scheduled");
        } catch (Exception e) {
            Log.e(TAG, "schedule failed", e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob: MediaStore changed");

        // Re-arm immediately — content jobs are one-shot.
        scheduleSelf(this);

        if (MoverService.isEnabled(this)) {
            Intent svc = new Intent(this, MoverService.class)
                    .setAction(MoverService.ACTION_SYNC_ONCE);
            try {
                ContextCompat.startForegroundService(this, svc);
            } catch (Exception e) {
                Log.w(TAG, "startForegroundService failed: " + e);
            }
        }
        // Work is delegated to the FGS; this job has nothing more to do.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Job's only work was to start the FGS, which is independent of this job.
        return false;
    }
}
