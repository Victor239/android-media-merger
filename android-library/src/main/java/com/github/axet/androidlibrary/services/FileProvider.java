package com.github.axet.androidlibrary.services;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import androidx.annotation.Nullable;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// <application>
//   <provider
//     android:name="com.github.axet.androidlibrary.services.FileProvider"
//     android:authorities="com.github.axet.android-library"
//     android:exported="false"
//     android:grantUriPermissions="true">
//   </provider>
// </application>
//
// url example:
// content://com.github.axet.android-library/image.jpg

public class FileProvider extends ContentProvider {
    public static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    public static final int RO = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    public static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    public ProviderInfo info;
    public Map<Uri, String> types = new HashMap<>();
    public Map<Uri, String> names = new HashMap<>();
    public Map<Uri, File> files = new HashMap<>();

    protected static HashMap<Class, FileProvider> infos = new HashMap<>();

    public static FileProvider getProvider() {
        return infos.get(FileProvider.class);
    }

    public static void grantPermissions(Context context, Intent intent) {
        grantPermissions(context, intent, RO);
    }

    public static void grantPermissions(Context context, Intent intent, int flags) {
        intent.addFlags(flags);

        Object uri = null;
        Bundle e = intent.getExtras();
        if (e != null)
            uri = e.get(Intent.EXTRA_STREAM);
        if (uri == null)
            uri = intent.getData();

        if (uri instanceof Uri)
            grantPermissions(context, intent, (Uri) uri, flags);
        if (uri instanceof ArrayList) {
            for (Uri u : (ArrayList<Uri>) uri)
                grantPermissions(context, intent, u, flags);
        }
    }

    public static void grantPermissions(Context context, Intent intent, Uri u) {
        grantPermissions(context, intent, u, RO);
    }

    public static void grantPermissions(Context context, Intent intent, Uri u, int flags) {
        List<ResolveInfo> rr = context.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo r : rr) {
            String packageName = r.activityInfo.packageName;
            context.grantUriPermission(packageName, u, flags);
        }
    }

    public static String[] copyOf(String[] original, int newLength) {
        final String[] result = new String[newLength];
        System.arraycopy(original, 0, result, 0, newLength);
        return result;
    }

    public static Object[] copyOf(Object[] original, int newLength) {
        final Object[] result = new Object[newLength];
        System.arraycopy(original, 0, result, 0, newLength);
        return result;
    }

    public static int modeToMode(String mode) { // converts 'rw' into ParcelFileDescriptor.MODE_
        int modeBits;
        if ("r".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        return modeBits;
    }

    public Uri share(File file) {
        String n = file.getName();
        return share(Storage.getTypeByName(n), n, file);
    }

    public Uri share(String type, File file) {
        return share(type, file.getName(), file);
    }

    public Uri share(String type, String name, File file) {
        Uri u = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).path(name).build();
        types.put(u, type);
        names.put(u, name);
        files.put(u, file);
        return u;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        this.info = info;
        // Sanity check our security
        if (info.exported)
            throw new SecurityException("Provider must not be exported");
        if (!info.grantUriPermissions)
            throw new SecurityException("Provider must grant uri permissions");
        infos.put(getClass(), this);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = names.get(uri);
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = files.get(uri).length();
            }
        }

        cols = copyOf(cols, i);
        values = copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return types.get(uri);
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = files.get(uri);
        final int fileMode = modeToMode(mode);
        return ParcelFileDescriptor.open(file, fileMode);
    }
}
