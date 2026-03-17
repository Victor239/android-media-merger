package com.github.victor.mover.app;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    @TargetApi(21)
    public static Uri move(Context context, Uri f, Uri dir, String t) throws FileNotFoundException {
        ContentResolver resolver = context.getContentResolver();
        Uri u = createFile(context, dir, t);
        if (u == null)
            throw new RuntimeException("unable to create file " + t);
        try {
            InputStream is = resolver.openInputStream(f);
            OutputStream os = resolver.openOutputStream(u);
            IOUtils.copy(is, os);
            is.close();
            os.close();
            delete(context, f);
            return u;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File move(Context context, Uri f, File to) {
        ContentResolver resolver = context.getContentResolver();
        try {
            long last = getLastModified(context, f);
            InputStream in = resolver.openInputStream(f);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            delete(context, f);
            to.setLastModified(last);
            return to;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Uri move(Context context, Uri f, Uri t) throws FileNotFoundException {
        String s = t.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri root = getDocumentTreeUri(t);
            Uri to = move(context, f, root, getDocumentChildPath(t));
            deleteDatabase(context, f);
            return to;
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            String ext = getExt(context, t);
            String n = getNameNoExt(context, t);

            File tf = getFile(t);
            File td = tf.getParentFile();

            if (!td.exists() && !td.mkdirs())
                throw new RuntimeException("unable to create: " + td);

            File to = Storage.getNextFile(td, n, ext);
            Uri r = Uri.fromFile(move(context, f, to));
            deleteDatabase(context, f);
            return r;
        } else {
            throw new UnknownUri();
        }
    }

    public static void deleteDatabase(Context context, Uri f) {
        ContentResolver resolver = context.getContentResolver();
        String s = f.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            Uri e = MediaStore.Images.Media.getContentUri("external");
            resolver.delete(e, MediaStore.Images.ImageColumns.DATA + " LIKE ?", new String[]{f.getPath()});
        }
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
    }

    public Storage(Context context) {
        super(context);
    }

    public String getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getString(MoverApplication.STORAGE, null);
    }

    @Override
    public File getStoragePath(File file) {
        if (ejected(file))
            return null;
        return file;
    }

    @Override
    public Uri getStoragePath(String path) {
        if (path == null)
            return null;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (isEjected(context, u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return null;
            return u;
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE))
            f = getFile(Uri.parse(path));
        else
            f = new File(path);
        if (!permitted(context, PERMISSIONS_RW)) {
            return null;
        } else {
            f = getStoragePath(f);
            if (f == null)
                return null;
            return Uri.fromFile(f);
        }
    }

}
