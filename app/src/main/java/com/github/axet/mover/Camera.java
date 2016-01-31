package com.github.axet.mover;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TreeMap;

/**
 * Organize DCIM/Camera && Pictures/Screenshots folders
 */
public class Camera {

    private static final String TAG = "Camera";

    Context context;
    File targetDir;

    final static String SCREENSHOTS = "Screenshots";

    TreeMap<File, FileObserver> organizes = new TreeMap<>();

    final File dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    final File picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    final File screenshotsPath = new File(picturesPath, SCREENSHOTS);

    public FileObserver organize(final File path) {
        Log.d(TAG, "ORGANAZLING [" + path + "]");

        if (!path.exists() || !path.isDirectory()) {
            throw new RuntimeException("no folder exist " + path);
        }

        FileObserver ff = new FileObserver(path.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
            @Override
            public void onEvent(int event, String file) {
                if (event == FileObserver.CREATE) {
                    move();
                }
            }
        };
        ff.startWatching();

        return ff;
    }

    public void watch(final File path, final String filter) {
        if (!path.exists() || !path.isDirectory()) {
            throw new RuntimeException("no folder exist " + path);
        }

        Log.d(TAG, "WATCHING / [" + path + "]");
        FileObserver fo = new FileObserver(path.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
            @Override
            public void onEvent(int event, String file) {
                Log.d(TAG, "CREATED/ [" + file + "]");

                if (file == null)
                    return;

                event &= FileObserver.ALL_EVENTS;

                if (filter != null) {
                    if (file != filter)
                        return;
                }

                File ff = new File(path, file);
                Log.d(TAG, "CREATED/ [" + ff + "]");
                if (event == FileObserver.CREATE && ff.isDirectory() && !ff.isHidden()) {
                    organizes.put(ff, organize(ff));
                }
                if (event == FileObserver.DELETE) {
                    FileObserver fo = organizes.get(ff);
                    if (fo != null) {
                        fo.stopWatching();
                        organizes.remove(ff);
                    }
                }
            }
        };
        fo.startWatching();
        organizes.put(path, fo);

        for (File f : path.listFiles()) {
            if (filter != null) {
                if (!f.getName().equals(filter))
                    continue;
            }
            if (f.isDirectory() && !f.isHidden()) {
                organizes.put(f, organize(f));
            }
        }
    }

    void move() {
        for (File f : dcimPath.listFiles()) {
            if (f.exists() && f.isDirectory() && !f.isHidden()) {
                move(f);
            }
        }
        if (screenshotsPath.exists() && screenshotsPath.isDirectory() && !screenshotsPath.isHidden()) {
            move(screenshotsPath);
        }
    }

    void move(File ff) {
        for (File f : ff.listFiles()) {
            if (f.isDirectory() || f.isHidden())
                continue;
            moveFile(f);
        }
    }

    void moveFile(File f) {
        targetDir.mkdirs();

        Date date = new Date(f.lastModified());
        String newstring = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(date);
        String ext = FilenameUtils.getExtension(f.getName());
        File to = new File(targetDir, String.format("%s.%s", newstring, ext));

        int count = 0;
        while (to.exists()) {
            count++;
            to = new File(targetDir, String.format("%s %d.%s", newstring, count, ext));
        }

        Log.d(TAG, "MOVE [" + f + " to " + to + "]");
        f.renameTo(to);
    }

    public Camera(final Context context, final File targetDir) {
        this.context = context;
        this.targetDir = targetDir;

        // Android 6.0 has a bug preventing FileObserver to work with screenshots

//        if (dcimPath.exists()) {
//            watch(dcimPath, null);
//        }
//
//        if (picturesPath.exists()) {
//            watch(picturesPath, SCREENSHOTS);
//        }

        monitorContent();
    }

    void monitorContent() {
        HandlerThread handlerThread = new HandlerThread("content_observer");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };

        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        Log.d(TAG, "onChange " + uri.toString());
                        if (uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                            move();
                        }
                        super.onChange(selfChange, uri);
                    }
                }
        );
    }

    public void shutdown() {
        Log.d(TAG, "SHUTDOWN");

        for (File f : organizes.keySet()) {
            FileObserver fo = organizes.get(f);
            fo.stopWatching();
        }

        organizes.clear();
    }
}
