package com.github.axet.mover.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.androidlibrary.app.Storage;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Organize DCIM/Camera && Pictures/Screenshots folders
 */
public class Camera {

    private static final String TAG = "Camera";

    final static String SCREENSHOTS = "Screenshots";

    // minimum refresh time, camera file flash recording video set to 10 seconds.
    // do not refresh more often, otherwise we may not detect current recording file video last write time change.
    public static final int REFRESH_TIME = 10 * 1000;

    protected Context context;

    protected Handler handler = new Handler();

    // where to put result files
    protected File targetDir;

    // /sdcard/DCIM/
    public final File dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    // /sdcard/Pictures/
    public final File picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    // /sdcard/Pictures/Screenshots/
    public final File screenshotsPath = new File(picturesPath, SCREENSHOTS);

    // current sync() folders list
    ArrayList<File> watchingFolders = new ArrayList<>();

    // previous sync() file list
    Map<File, Stats> old = new HashMap<>();

    ArrayList<File> open = new ArrayList<>();

    // last sync() time
    long last;

    // sync runnable
    Runnable sync = new Runnable() {
        @Override
        public void run() {
            if (!fsync()) {
                handler.removeCallbacks(sync);
                handler.postDelayed(sync, REFRESH_TIME);
            } else {
                handler.removeCallbacks(sync);
            }
        }
    };

    ContentObserver mediaObserver;
    TreeMap<File, FileObserver> organizes = new TreeMap<>();

    public static class Stats {
        public long last;
        public long size;

        public Stats(File f) {
            last = f.lastModified();
            size = f.length();
        }

        @Override
        public boolean equals(Object o) {
            Stats n = (Stats) o;
            return last == n.last && size == n.size;
        }
    }

    public Camera(final Context context, final File targetDir) {
        this.context = context;
        this.targetDir = targetDir;
    }

    public void create() {
        monitorContentObserver();
        sync();
        // Android 6.0 has a bug preventing FileObserver to work with screenshots folder.
        // is simply do not fire on Screenshot file creation.
        for (File d : watchingFolders) {
            watchFiles(d);
        }
    }

    public void close() {
        for (File f : organizes.keySet()) {
            FileObserver fo = organizes.get(f);
            fo.stopWatching();
        }
        organizes.clear();
        open.clear();
        if (mediaObserver != null) {
            context.getContentResolver().unregisterContentObserver(mediaObserver);
        }
        mediaObserver = null;
        handler.removeCallbacks(sync);
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

    // scan DCIM folder for sub folders
    public ArrayList<File> generateDcim() {
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

    // load current sync dirrectories
    public ArrayList<File> generateDirs() {
        ArrayList<File> dirs = generateDcim();
        if (screenshotsPath.exists() && screenshotsPath.isDirectory())
            dirs.add(screenshotsPath);
        return dirs;
    }

    public void sync() {
        handler.removeCallbacks(sync);
        handler.post(sync);
    }

    // return done - true
    public boolean fsync() {
        long cur = System.currentTimeMillis();
        if (last + REFRESH_TIME > cur) {
            return false;
        }

        last = cur;
        watchingFolders = generateDirs();
        Map<File, Stats> list = generateFiles();

        if (list.isEmpty())
            return true;

        for (File f : new TreeSet<>(list.keySet())) {
            if (old.containsKey(f) && !open.contains(f)) {
                Stats sold = old.get(f);
                Stats snew = list.get(f);
                if (sold.equals(snew)) {
                    moveFile(f);
                    list.remove(f);
                } else {
                    Log.d(TAG, "Delaying: " + f);
                }
            }
        }

        old = list;

        if (list.isEmpty())
            return true;

        return false;
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
        fo = new FileObserver(path.getPath()) {
            @Override
            public void onEvent(int event, String file) {
                if (filter != null && !filter.equals(file)) {
                    return;
                }

                File ff = path;
                if (file != null)
                    ff = new File(path, file);

                if (event == FileObserver.CREATE && ff.isDirectory() && !ff.isHidden()) {
                    watchFiles(ff);
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

    void openClose(File ff) {
        for (int i = 0; i < open.size(); i++) {
            File f = open.get(i);
            try {
                if (f.getCanonicalPath().equals(ff.getCanonicalPath())) {
                    open.remove(i);
                    return; // remove one
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public FileObserver watchFiles(final File path) {
        FileObserver fo = organizes.get(path);
        if (fo != null) {
            fo.stopWatching();
            organizes.remove(path);
        }
        fo = new FileObserver(path.getPath()) {
            @Override
            public void onEvent(int event, String file) {
                if (file == null)
                    return;
                File f = new File(path, file);
                switch (event) {
                    case FileObserver.CREATE:
                        old.remove(f);
                        break;
                    case FileObserver.OPEN:
                        open.add(f);
                        old.remove(f);
                        break;
                    case FileObserver.MODIFY:
                    case FileObserver.ACCESS:
                        old.remove(f);
                        break;
                    case FileObserver.DELETE:
                    case FileObserver.MOVED_FROM:
                        openClose(f);
                        break;
                    case FileObserver.CLOSE_NOWRITE:
                    case FileObserver.CLOSE_WRITE:
                        openClose(f);
                        // no break
                    case FileObserver.MOVED_TO:
                        sync(); //moveFile(ff);
                        break;
                }
            }
        };
        fo.startWatching();
        organizes.put(path, fo);
        return fo;
    }

    // generate file list based on current folders ('watchingFolders')
    Map<File, Stats> generateFiles() {
        Map<File, Stats> ff = new HashMap<>();
        for (File f : watchingFolders) {
            for (File fd : generateFiles(f)) {
                ff.put(fd, new Stats(fd));
            }
        }
        return ff;
    }

    // load file list from dir
    List<File> generateFiles(File dir) {
        ArrayList<File> list = new ArrayList<>();
        File[] ff = dir.listFiles();
        if (ff != null) {
            for (File f : ff) {
                if (f.isDirectory() || f.isHidden())
                    continue;
                list.add(f);
            }
        }
        return list;
    }

    // check if file save to move (it is not open by another apps)
    //
    // seems like android allow to write currently writting file. so. this function does not work.
    boolean isSafe(File f) {
        try {
            FileOutputStream fis = new FileOutputStream(f, true);
            FileLock lock = fis.getChannel().tryLock();
            if (lock != null) {
                lock.release();
                fis.close();
                return true;
            }
            fis.close();
            return false;
        } catch (NonWritableChannelException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    void moveFile(File f) {
        File parent = f.getParentFile();
        if (Storage.isSame(parent, targetDir))
            return;

        targetDir.mkdirs();

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String s = shared.getString(MoverApplication.PREFERENCE_NAME, "%f");

        Date date = new Date(f.lastModified());
        String dateString = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(date);
        String ext = FilenameUtils.getExtension(f.getName());

        s = s.replaceAll("%f", Storage.filterDups(Storage.getNameNoExt(f)));
        s = s.replaceAll("%t", "" + System.currentTimeMillis());
        s = s.replaceAll("%d", dateString);

        File to = Storage.getNextFile(targetDir, s, ext);

        if (Storage.isSame(f, to))
            return;

        final String log = "MOVE [" + f + " to " + to + "]";
        Log.d(TAG, log);

        Storage.move(f, to);

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
