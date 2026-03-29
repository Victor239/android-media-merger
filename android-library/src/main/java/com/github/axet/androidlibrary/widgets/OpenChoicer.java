package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.widget.Button;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.AssetsDexLoader;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class OpenChoicer {
    public static String TAG = OpenChoicer.class.getSimpleName();

    public static final String EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI";
    public static final String MIME_ALL = "*/*";

    public Context context;
    public OpenFileDialog.DIALOG_TYPE type;
    public boolean readonly;
    public Uri old;
    public Activity a;
    public Fragment f;
    public String[] perms;
    public int permsresult;
    public Activity sa;
    public Fragment sf;
    public int sresult;
    public String title;

    public static void activityCheck(Activity a) {
        PackageManager packageManager = a.getPackageManager();
        try {
            ActivityInfo info = packageManager.getActivityInfo(a.getComponentName(), 0);
            if ((info.configChanges & ActivityInfo.CONFIG_ORIENTATION) != ActivityInfo.CONFIG_ORIENTATION ||
                    (info.configChanges & ActivityInfo.CONFIG_SCREEN_SIZE) != ActivityInfo.CONFIG_SCREEN_SIZE) {
                String msg = "Please add 'android:configChanges=\"orientation|screenSize\' to manifest.xml to keep open file dialog"; // since we don't want to deal with save/load state
                Log.e(TAG, msg);
            }
            if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                String msg = "Please add android:launchMode=\"singleTop\" instead of singleInstance to manifest.xml"; // http://stackoverflow.com/questions/3354955/onactivityresult-called-prematurely
                Log.e(TAG, msg);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "activity check", e);
        }
    }

    public static boolean isExternalSDPortable(Context context) {
        String path = System.getenv(OpenFileDialog.ANDROID_STORAGE);
        if (path == null || path.isEmpty())
            path = OpenFileDialog.DEFAULT_STORAGE_PATH;

        File storage = new File(path);
        File[] ff = storage.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName();
                Matcher m = OpenFileDialog.DEFAULT_STORAGE_PATTERN.matcher(name);
                if (m.matches()) {
                    if (file.canWrite())
                        return false; // does we have rw access /storage/1234-1234/* if so, skip
                    return true;
                }
                return false;
            }
        });
        if (ff != null && ff.length > 0) // we have files like /storage/1234-1234
            return true;

        File ext = Environment.getExternalStorageDirectory();
        if (ext == null)
            return false;

        ff = ContextCompat.getExternalFilesDirs(context, ""); // can show no external dir: https://stackoverflow.com/questions/33350250
        int count = 0;
        for (File f : ff) {
            if (f == null || f.getPath().startsWith(ext.getPath())) // f can be null, if media unmounted
                continue;
            count++;
        }
        if (count > 0) // have external SD formatted as portable?
            return true;

        return false;
    }

    @TargetApi(19)
    public static boolean showStorageAccessFramework(Context context, String path, String[] ss, boolean readonly) {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null)
            return true;
        if (!readonly && isExternalSDPortable(context)) // does external SD card formatted as portable? internal sdcard hidden, use SAF to see booth
            return true;
        if (path != null && path.startsWith(ContentResolver.SCHEME_CONTENT)) // showed saf before?
            return true;
        if (ss == null) // no permission enabled, use saf as main dialog
            return true;
        return false;
    }

    @TargetApi(19)
    public static boolean showStorageAccessFramework(Context context, String path, String[] ss, Intent intent, boolean readonly) {
        if (!OptimizationPreferenceCompat.isCallable(context, intent)) // samsung 6.0 has no Intent.OPEN_DOCUMENT activity to start, check before call
            return false;
        return showStorageAccessFramework(context, path, ss, readonly);
    }

    public OpenChoicer(OpenFileDialog.DIALOG_TYPE type, boolean readonly) {
        this.type = type;
        this.readonly = readonly;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setPermissionsDialog(Fragment f, String[] ss, int code) {
        activityCheck(f.getActivity());
        this.context = f.getContext();
        this.f = f;
        this.perms = ss;
        this.permsresult = code;
    }

    public void setPermissionsDialog(Activity a, String[] ss, int code) {
        activityCheck(a);
        this.context = a;
        this.a = a;
        this.perms = ss;
        this.permsresult = code;
    }

    public void setStorageAccessFramework(Activity a, int code) {
        activityCheck(a);
        this.context = a;
        this.sa = a;
        this.sresult = code;
    }

    public void setStorageAccessFramework(Fragment f, int code) {
        activityCheck(f.getActivity());
        this.context = f.getContext();
        this.sf = f;
        this.sresult = code;
    }

    public void show(Uri old) {
        this.old = old;
        if (Build.VERSION.SDK_INT >= 21) {
            boolean nofile = a == null && f == null; // force SAF?
            if (context != null) { // 'a' or 'f' is set
                if (Build.VERSION.SDK_INT >= 30 && context.getApplicationInfo().targetSdkVersion >= 30) {
                    boolean ext = Storage.isLegacyManifest30(context);
                    if (!ext)
                        nofile = true; // MANAGE_EXTERNAL_STORAGE not set
                    else if (!Storage.isExternalStorageManager(context))
                        nofile = true; // MANGER not enabled, use settings pref to enable
                } else if (Build.VERSION.SDK_INT == 29) {
                    if (!Storage.isLegacyManifest29(context))
                        nofile = true;
                }
            }
            if (showSAF(nofile))
                return;
        }
        if (a != null) {
            if (Storage.permitted(a, perms, permsresult))
                fileDialog();
            return; // perms shown
        }
        if (f != null) {
            if (Storage.permitted(f, perms, permsresult))
                fileDialog();
            return; // perms shown
        }
        if (context != null) {
            if (Storage.permitted(context, readonly ? Storage.PERMISSIONS_RO : Storage.PERMISSIONS_RW))
                fileDialog();
            return; // perms shown
        }
        Log.e(TAG, "Neither setStorageAccessFramework or setPermissionsDialog called");
        onDismiss(); // all failed, dismissed
    }

    public boolean showSAF(Uri old) {
        this.old = old;
        return showSAF(true);
    }

    @TargetApi(21)
    public boolean showSAF(boolean force) {
        Intent intent;
        if (type == OpenFileDialog.DIALOG_TYPE.FILE_DIALOG) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(MIME_ALL);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (!readonly)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.putExtra(EXTRA_INITIAL_URI, old); // API 26+
        if (force || showStorageAccessFramework(context, old != null ? old.toString() : null, perms, intent, readonly)) {
            if (sa != null) {
                sa.startActivityForResult(intent, sresult);
                return true;
            }
            if (sf != null) {
                sf.startActivityForResult(intent, sresult);
                return true;
            }
        }
        return false;
    }

    public void showFallbackFolders() { // simple folder selection
        final List<String> ss = new ArrayList<>();
        File local = OpenFileDialog.getLocalInternal(context);
        ss.add(local.getPath());
        File[] ext = OpenFileDialog.getLocalExternals(context, readonly);
        if (ext != null) {
            for (File f : ext)
                ss.add(f.getPath());
        }
        AlertDialog.Builder b = showFallbackFoldersBuild(ss);
        AlertDialog d = b.create();
        d.show();
    }

    public AlertDialog.Builder showFallbackFoldersBuild(final List<String> ss) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        int index = 0;
        if (old != null) {
            File summ = Storage.getFile(old);
            index = ss.indexOf(summ.getPath());
        }
        builder.setSingleChoiceItems(ss.toArray(new CharSequence[]{}), index, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String fileName = ss.get(which);
                File f = new File(fileName);
                Uri u = Uri.fromFile(f);
                onResult(u, true);
                dialog.dismiss();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                OpenChoicer.this.onCancel();
            }
        });
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                OpenChoicer.this.onDismiss();
            }
        });
        return builder;
    }

    public void fileDialog() {
        if (!readonly && !Storage.permitted(context, Storage.PERMISSIONS_RW) && type != OpenFileDialog.DIALOG_TYPE.FILE_DIALOG) {
            showFallbackFolders();
        } else {
            final OpenFileDialog f = fileDialogBuild();
            if (title != null)
                f.setTitle(title);
            final AlertDialog d = f.create();
            if (!readonly) {
                f.setChangeFolderListener(new Runnable() {
                    @Override
                    public void run() {
                        File ff = f.getCurrentPath();
                        if (!ff.isDirectory())
                            ff = ff.getParentFile();
                        Button b2 = d.getButton(AlertDialog.BUTTON_POSITIVE);
                        if (!ff.canWrite())
                            b2.setEnabled(false);
                        else
                            b2.setEnabled(true);
                    }
                });
            }
            d.show();
        }
    }

    public OpenFileDialog fileDialogBuild() {
        final OpenFileDialog dialog = new OpenFileDialog(context, type, readonly);
        if (old != null) {
            String s = old.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE))
                dialog.setCurrentPath(Storage.getFile(old));
        }
        dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                File f = dialog.getCurrentPath();
                if (f == null)
                    onResult(null, false);
                else
                    onResult(Uri.fromFile(f), false);
            }
        });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                OpenChoicer.this.onCancel();
            }
        });
        dialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                OpenChoicer.this.onCancel();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                OpenChoicer.this.onDismiss();
            }
        });
        return dialog;
    }

    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            onCancel();
            onDismiss();
            return;
        }
        Uri u = data.getData();
        if (u != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                ContentResolver resolver = context.getContentResolver();
                try {
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if (!readonly)
                        flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    resolver.takePersistableUriPermission(u, flags);
                    onResult(u, false);
                } catch (SecurityException e) { // remote / sdcard SAF?
                    onResult(u, true);
                }
            }
        }
        onDismiss();
    }

    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        if (Storage.permitted(context, permissions))
            fileDialog();
        else
            onRequestPermissionsFailed(permissions);
    }

    public void onRequestPermissionsFailed(String[] permissions) {
        Toast.makeText(context, R.string.not_permitted, Toast.LENGTH_SHORT).show();
        if (showSAF(true))
            return;
        if (type == OpenFileDialog.DIALOG_TYPE.FILE_DIALOG) {
            onCancel();
            onDismiss();
        } else {
            showFallbackFolders(); // unable to show SAF, show simple folder dialog
        }
    }

    public void onDismiss() {
    }

    public void onCancel() {
    }

    public void onResult(Uri uri) {
    }

    public void onResult(Uri uri, boolean tmp) {
        onResult(uri);
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
