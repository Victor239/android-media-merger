package com.github.axet.mover.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public Storage(Context context) {
        super(context);
    }

    @Override
    public File getStoragePath(File file) {
        if (ejected(file) || !file.canWrite())
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
            if (!permitted(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                return null;
            return u;
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = getFile(Uri.parse(path));
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS)) {
            return null;
        } else {
            f = getStoragePath(f);
            if (f == null)
                return null;
            return Uri.fromFile(f);
        }
    }

}
