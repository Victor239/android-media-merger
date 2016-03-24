package com.github.axet.mover.app;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

/**
 * Organize DCIM/Camera && Pictures/Screenshots folders
 */
public class Camera {

    private static final String TAG = "Camera";

    protected Context context;
    protected File targetDir;

    final static String SCREENSHOTS = "Screenshots";

    TreeMap<File, FileObserver> organizes = new TreeMap<>();

    // /sdcard/DCIM/
    public final File dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    // /sdcard/Pictures/
    public final File picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    // /sdcard/Pictures/Screenshots/
    public final File screenshotsPath = new File(picturesPath, SCREENSHOTS);

    ArrayList<File> watchingFolders = new ArrayList<>();

    ContentObserver mediaObserver;

    Handler handler = new Handler();

    public Camera(final Context context, final File targetDir) {
        this.context = context;
        this.targetDir = targetDir;
    }

    public void create() {
//        Android 6.0 has a bug preventing FileObserver to work with screenshots folder.
//        is simply do not fire on Screenshot file creation.
//
//        if (dcimPath.exists()) {
//            watchDirectory(dcimPath, null);
//        }
//        if (picturesPath.exists()) {
//            watchDirectory(picturesPath, SCREENSHOTS);
//        }

        monitorContentObserver();
        sync();
    }

    public void close() {
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

    public List<File> getFolders() {
        return watchingFolders;
    }

    public File getTargetDir() {
        return targetDir;
    }

    public void setTargetDir(File s) {
        targetDir = s;
    }

    public List<File> getMainFolders() {
        return Arrays.asList(dcimPath, picturesPath);
    }

    public ArrayList<File> readDcim() {
        ArrayList<File> dirs = new ArrayList<>();

        File[] ff = dcimPath.listFiles();
        if (ff != null) {
            for (File f : ff) {
                if (f.exists() && f.isDirectory() && !f.isHidden()) {
                    dirs.add(f);
                }
            }
        }

        return dirs;
    }

    public ArrayList<File> readDirs() {
        ArrayList<File> dirs = readDcim();

        if (screenshotsPath.exists() && screenshotsPath.isDirectory())
            dirs.add(screenshotsPath);

        return dirs;
    }

    public void sync() {
        watchingFolders = readDirs();
        moveDirs();
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

    void moveDirs() {
        for (File f : watchingFolders) {
            moveDirs(f);
        }
    }

    void moveDirs(File ff) {
        for (File f : ff.listFiles()) {
            if (f.isDirectory() || f.isHidden())
                continue;
            moveFile(f);
        }
    }

    static boolean isSame(File f, File t) {
        try {
            return f.getCanonicalPath().equals(t.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // check if file save to move (it is not open by another apps)
    boolean isSafe(File f) {
        try {
            FileUtils.touch(f);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    void moveFile(File f) {
        targetDir.mkdirs();

        Date date = new Date(f.lastModified());
        String dateString = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(date);
        String ext = FilenameUtils.getExtension(f.getName());
        File to;

        if (ext.isEmpty())
            to = new File(targetDir, dateString);
        else
            to = new File(targetDir, String.format("%s.%s", dateString, ext));

        if (isSame(f, to))
            return;

        if (!isSafe(f))
            return;

        int count = 0;
        while (to.exists()) {
            count++;
            to = new File(targetDir, String.format("%s %d.%s", dateString, count, ext));
        }

        if (isSame(f, to))
            return;

        final String log = "MOVE [" + f + " to " + to + "]";
        Log.d(TAG, log);

        f.renameTo(to);

        Uri contentUri = Uri.fromFile(to);
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        mediaScanIntent.setData(contentUri);
        context.sendBroadcast(mediaScanIntent);

        final File t = to;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "MOVE [" + t + "]", Toast.LENGTH_SHORT).show();
            }
        });
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
                    sync();
                }
            }
        };

        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
        );
    }
}
