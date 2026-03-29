package com.github.axet.androidlibrary.widgets;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.preferences.AdminPreferenceCompat;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.preferences.TTSPreferenceCompat;
import com.github.axet.androidlibrary.services.StorageProvider;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class ErrorDialog extends AlertDialog.Builder {
    public static final String TAG = ErrorDialog.class.getSimpleName();

    public static String ERROR = "Error"; // title

    public static Thread.UncaughtExceptionHandler OLD;
    public static Thread.UncaughtExceptionHandler UEH;

    public static String getInstallerPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                InstallSourceInfo info = pm.getInstallSourceInfo(context.getPackageName());
                return info.getInstallingPackageName();
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        } else {
            return pm.getInstallerPackageName(context.getPackageName());
        }
    }

    public static StringBuilder fullCrash(Context context, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(AboutPreferenceCompat.getApplicationName(context));
        sb.append(" ");
        sb.append(AboutPreferenceCompat.getVersion(context));
        sb.append("\n");
        sb.append("\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        sb.append(sw);
        return sb;
    }

    public static File saveCrash(Context context, Throwable e) {
        Log.e(TAG, "Error", e);
        StringBuilder sb = fullCrash(context, e);
        return saveCrash(context, sb);
    }

    public static File saveCrash(Context context, StringBuilder sb) {
        File d = context.getExternalFilesDir("");
        if (d == null)
            d = context.getFilesDir();
        d = new File(d, "crash_" + System.currentTimeMillis() + ".txt");
        try {
            FileUtils.write(d, sb, Charset.defaultCharset());
        } catch (IOException fe) {
            Log.d(TAG, "Write crash", fe);
        }
        return d;
    }

    public static void unhandled(final Context context, final boolean auto) {
        if (OLD != null) {
            if (!auto) {
                Thread.setDefaultUncaughtExceptionHandler(OLD);
                UEH = null;
            } else {
                return;
            }
        }
        if (UEH == null) {
            OLD = Thread.getDefaultUncaughtExceptionHandler();
            UEH = new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    unhandled(context, t, e, auto);
                }
            };
        }
        Thread.setDefaultUncaughtExceptionHandler(UEH);
    }

    public static void unhandled(final Context context, Thread thread) {
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                unhandled(context, t, e, false);
            }
        });
    }

    public static void unhandled(final Context context, final Thread t, final Throwable e, boolean auto) {
        boolean a = auto && AdminPreferenceCompat.getInstaller(context).equals(AdminPreferenceCompat.Installer.STORE);
        if (context instanceof Activity && !a) {
            ErrorDialog builder = new ErrorDialog(context, e);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (OLD != null)
                        OLD.uncaughtException(t, e);
                }
            });
            builder.show();
        } else {
            File f = saveCrash(context, e);
            Toast.makeText(context, ERROR + " " + toMessage(e) + "\nFile: " + f.getName(), Toast.LENGTH_SHORT).show();
            if (OLD != null)
                OLD.uncaughtException(t, e);
        }
    }

    public static void crashToast(Context context, File f) {
        Toast.makeText(context, "Crash: " + f.getName(), Toast.LENGTH_SHORT).show();
    }

    public static Throwable getCause(Throwable e) { // get to the bottom
        Throwable c = null;
        while (e != null) {
            c = e;
            e = e.getCause();
        }
        return c;
    }

    public static String toMessage(Throwable e) { // eat RuntimeException's
        Throwable p = e;
        while (e instanceof RuntimeException) {
            e = e.getCause();
            if (e != null)
                p = e;
        }
        String msg = p.getMessage();
        if (msg == null || msg.isEmpty())
            msg = p.getClass().getCanonicalName();
        return msg;
    }

    public ErrorDialog(@NonNull final Context context) {
        super(context);
        setTitle(ERROR);
        setIcon(android.R.drawable.ic_dialog_alert);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    public ErrorDialog(@NonNull final Context context, final Throwable e) {
        this(context);
        final StringBuilder sb = fullCrash(context, e);
        final File f = saveCrash(context, sb);
        crashToast(context, f);
        final String msg = toMessage(e);
        setMessage(msg);
        setNeutralButton(TTSPreferenceCompat.getImageText(context, R.drawable.ic_open_in_new_black_24dp, androidx.appcompat.R.attr.colorAccent), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = "crash.txt";
                Intent share = StorageProvider.shareIntent(context, name, sb.toString());
                if (!OptimizationPreferenceCompat.startActivity(getContext(), share))
                    Toast.makeText(getContext(), "Unsupported", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public ErrorDialog(@NonNull final Context context, final String msg) {
        this(context);
        setMessage(msg);
    }

    public static void Post(final Context context, final Throwable e) {
        File f = saveCrash(context, e);
        crashToast(context, f);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, toMessage(e));
            }
        });
    }

    public static void Post(final Context context, final String e) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(context, e);
            }
        });
    }

    public static AlertDialog Error(Context context, Throwable e) {
        ErrorDialog builder = new ErrorDialog(context, e);
        return builder.show();
    }

    public static AlertDialog Error(Context context, String msg) {
        ErrorDialog builder = new ErrorDialog(context, msg);
        return builder.show();
    }
}
