package com.github.axet.mover.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.File;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public Storage(Context context) {
        super(context);
    }

    @Override
    public File getStoragePath(File file) {
        File parent = file.getParentFile();
        while (!parent.exists())
            parent = file.getParentFile();
        if ((file.canWrite() || parent.canWrite())) {
            return file;
        } else {
            return null;
        }
    }

    @Override
    public Uri getStoragePath(String path) {
        if(path == null)
            return null;
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = Uri.parse(path);
            if (permitted(u))
                return u;
            return null;
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
            return Uri.fromFile(getStoragePath(f));
        }
    }

}
