package com.github.axet.mover.app;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.mover.activities.MainActivity;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public Storage(Context context) {
        super(context);
    }

    public String getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getString(MoverApplication.STORAGE, null);
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
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
            if (ejected(u))
                return null;
            if (ejected(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return null;
            return u;
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = getFile(Uri.parse(path));
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS_RW)) {
            return null;
        } else {
            f = getStoragePath(f);
            if (f == null)
                return null;
            return Uri.fromFile(f);
        }
    }

    @TargetApi(21)
    public Uri move(Uri f, Uri dir, String t) {
        Uri u = createFile(dir, t);
        if (u == null)
            throw new RuntimeException("unable to create file " + t);
        try {
            InputStream is = resolver.openInputStream(f);
            OutputStream os = resolver.openOutputStream(u);
            IOUtils.copy(is, os);
            is.close();
            os.close();
            delete(f);
            return u;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File move(Uri f, File to) {
        try {
            long last = getLastModified(f);
            InputStream in = resolver.openInputStream(f);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(in, out);
            in.close();
            out.close();
            delete(f);
            to.setLastModified(last);
            return to;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Uri move(Uri f, Uri t) {
        String s = t.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri root = getDocumentTreeUri(t);
            Uri to = move(f, root, getDocumentChildPath(t));
            deleteDatabase(f);
            return to;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            String ext = getExt(t);
            String n = getNameNoExt(t);

            File tf = getFile(t);
            File td = tf.getParentFile();

            if (!td.exists() && !td.mkdirs())
                throw new RuntimeException("unable to create: " + td);

            File to = Storage.getNextFile(td, n, ext);
            Uri r = Uri.fromFile(move(f, to));
            deleteDatabase(f);
            return r;
        } else {
            throw new UnknownUri();
        }
    }

    public void deleteDatabase(Uri f) {
        String s = f.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            Uri e = MediaStore.Images.Media.getContentUri("external");
            resolver.delete(e, MediaStore.Images.ImageColumns.DATA + " LIKE ?", new String[]{f.getPath()});
        }
    }
}
