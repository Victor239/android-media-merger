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
import java.util.Date;
import java.util.List;
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

    ArrayList<File> watchingFolders = new ArrayList<>();

    ContentObserver mediaObserver;

    public Camera(final Context context, final File targetDir) {
        this.context = context;
        this.targetDir = targetDir;

        readDirectories();

//        Android 6.0 has a bug preventing FileObserver to work with screenshots. is simply do not fire on Screenshot file creation.
//        if (dcimPath.exists()) {
//            watchDirectory(dcimPath, null);
//        }
//        if (picturesPath.exists()) {
//            watchDirectory(picturesPath, SCREENSHOTS);
//        }

        monitorContentObserver();
    }

    public List<File> getFolders() {
        return watchingFolders;
    }

    public File getTargetDir() {
        return targetDir;
    }

    public void readDirectories() {
        watchingFolders.clear();

        // add /sdcard/DCIM/*
        for (File f : dcimPath.listFiles()) {
            if (f.exists() && f.isDirectory() && !f.isHidden()) {
                watchingFolders.add(f);
            }
        }
        // add /sdcard/Pictures/Screenshots
        if (screenshotsPath.exists() && screenshotsPath.isDirectory())
            watchingFolders.add(screenshotsPath);
    }

    public void watch() {
        for (File f : watchingFolders) {
            watchFiles(f);
        }
    }

    // watch dirrectory (path) for subdirectories to emmerge
    //
    // filter - dirrectory name, only watch for it apperence or disapearnce
    public void watchDirectory(final File path, final String filter) {
        FileObserver fo = organizes.get(path);
        if (fo != null) {
            fo.stopWatching();
            organizes.remove(path);
        }

        fo = new FileObserver(path.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
            @Override
            public void onEvent(int event, String file) {
                if (!filter.equals(file)) {
                    return;
                }

                File ff = new File(path, file);

                if (event == FileObserver.CREATE && ff.isDirectory() && !ff.isHidden()) {
                    organizes.put(ff, watchFiles(ff));
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
    }

    public FileObserver watchFiles(final File path) {
        FileObserver fo = new FileObserver(path.getPath(), FileObserver.CREATE) {
            @Override
            public void onEvent(int event, String file) {
                File ff = new File(path, file);

                moveFile(ff);
            }
        };
        fo.startWatching();
        return fo;
    }

    void moveDir() {
        for (File f : watchingFolders) {
            moveDir(f);
        }
    }

    void moveDir(File ff) {
        for (File f : ff.listFiles()) {
            if (f.isDirectory() || f.isHidden())
                continue;
            moveFile(f);
        }
    }

    void moveFile(File f) {
        targetDir.mkdirs();

        Date date = new Date(f.lastModified());
        String dateString = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(date);
        String ext = FilenameUtils.getExtension(f.getName());
        File to = new File(targetDir, String.format("%s.%s", dateString, ext));

        int count = 0;
        while (to.exists()) {
            count++;
            to = new File(targetDir, String.format("%s %d.%s", dateString, count, ext));
        }

        Log.d(TAG, "MOVE [" + f + " to " + to + "]");
        f.renameTo(to);
    }

    void monitorContentObserver() {
        HandlerThread handlerThread = new HandlerThread("content_observer");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };

        if (mediaObserver != null) {
            context.getContentResolver().unregisterContentObserver(mediaObserver);
        }

        mediaObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);

                if (uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                    Log.d(TAG, "onChange " + uri.toString());

                    // rescan dirrectories, in case new were created
                    readDirectories();
                    moveDir();
                }
            }
        };

        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
        );
    }

    public void shutdown() {
        for (File f : organizes.keySet()) {
            FileObserver fo = organizes.get(f);
            fo.stopWatching();
        }
        organizes.clear();

        if (mediaObserver != null) {
            context.getContentResolver().unregisterContentObserver(mediaObserver);
        }
        mediaObserver = null;
    }
}
