package com.github.axet.androidlibrary.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.TransactionTooLargeException;
import android.util.Log;

import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;

import java.lang.reflect.InvocationTargetException;

public class NotificationManagerCompat {
    public static final String TAG = NotificationManagerCompat.class.getSimpleName();

    public static final int IMPORTANCE_NONE = NotificationManager.IMPORTANCE_NONE;
    public static final int IMPORTANCE_MIN = NotificationManager.IMPORTANCE_MIN;
    public static final int IMPORTANCE_LOW = NotificationManager.IMPORTANCE_LOW;
    public static final int IMPORTANCE_DEFAULT = NotificationManager.IMPORTANCE_DEFAULT;
    public static final int IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH;
    public static final int IMPORTANCE_MAX = NotificationManager.IMPORTANCE_MAX;
    public static final int IMPORTANCE_UNSPECIFIED = NotificationManager.IMPORTANCE_UNSPECIFIED;

    public NotificationManager nm;
    public androidx.core.app.NotificationManagerCompat nmc;

    public static NotificationManagerCompat from(Context context) {
        return new NotificationManagerCompat(context);
    }

    public NotificationManagerCompat(Context context) {
        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nmc = androidx.core.app.NotificationManagerCompat.from(context);
    }

    @TargetApi(26)
    public void createNotificationChannel(NotificationChannelCompat channel) {
        try {
            Class NotificationManager = nm.getClass();
            NotificationManager.getDeclaredMethod("createNotificationChannel", channel.NotificationChannel).invoke(nm, channel.channel);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @TargetApi(26)
    public void deleteNotificationChannel(NotificationChannelCompat channel) {
        try {
            Class NotificationManager = nm.getClass();
            NotificationManager.getDeclaredMethod("deleteNotificationChannel", channel.NotificationChannel).invoke(nm, channel.channel);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isNotificationPolicyAccessGranted() {
        if (Build.VERSION.SDK_INT >= 23)
            return nm.isNotificationPolicyAccessGranted();
        else
            return true;
    }

    public void cancel(int id) {
        nmc.cancel(id);
    }

    public void cancel(String tag, int id) {
        nmc.cancel(tag, id);
    }

    public void notify(int id, Notification n) {
        try {
            nmc.notify(id, n);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 16 && n.bigContentView != null) { // API16+
                Throwable c = e;
                while (c != null) {
                    if (c instanceof TransactionTooLargeException) { // API15+
                        Log.e(TAG, "notify", e);
                        n.contentView = n.bigContentView;
                        n.bigContentView = null;
                        nmc.notify(id, n);
                        return;
                    }
                    c = c.getCause();
                }
            }
            throw e;
        }
    }

    public void notify(String tag, int id, Notification n) {
        try {
            nmc.notify(tag, id, n);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 16 && n.bigContentView != null) { // API16+
                Throwable c = e;
                while (c != null) {
                    if (c instanceof TransactionTooLargeException) { // API15+
                        Log.e(TAG, "notify", e);
                        n.contentView = n.bigContentView;
                        n.bigContentView = null;
                        nmc.notify(tag, id, n);
                        return;
                    }
                    c = c.getCause();
                }
            }
            throw e;
        }
    }
}
