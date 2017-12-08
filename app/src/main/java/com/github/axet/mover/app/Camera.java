package com.github.axet.mover.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyyMMdd\'T\'HHmmss");

    public final static String SCREENSHOTS = "Screenshots";

    // minimum refresh time, camera file flash recording video set to 10 seconds.
    // do not refresh more often, otherwise we may not detect current recording file video last write time change.
    public static final int REFRESH_TIME = 10 * 1000;

    public static boolean mask(int i, int mask) {
        return (i & mask) == mask;

    }

    protected Context context;

    protected Handler handler = new Handler();

    // where to put result files
    protected Uri targetDir;

    // /sdcard/DCIM/
    public final File dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    // /sdcard/Pictures/
    public final File picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    // /sdcard/Pictures/Screenshots/
    public final File screenshotsPath = new File(picturesPath, SCREENSHOTS);

    // current sync() folders list
    ArrayList<Uri> watchingFolders = new ArrayList<>();

    // previous sync() file list
    Map<Uri, Stats> old = new HashMap<>();

    ArrayList<Uri> open = new ArrayList<>();

    Storage storage;

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

    public static String getFormatted(String f, String ne, Date date) {
        f = f.replaceAll("%f", Storage.filterDups(ne));
        f = f.replaceAll("%t", "" + (date.getTime() / 1000));
        f = f.replaceAll("%d", SIMPLE.format(date));
        f = f.replaceAll("%i", ISO8601.format(date));
        return f;
    }

    public class Stats {
        public long last;
        public long size;

        public Stats(File f) {
            last = f.lastModified();
            size = f.length();
        }

        public Stats(Uri u) {
            last = storage.getLastModified(u);
            size = storage.getLength(u);
        }

        @Override
        public boolean equals(Object o) {
            Stats n = (Stats) o;
            return last == n.last && size == n.size;
        }
    }

    public Camera(final Context context, final Uri targetDir) {
        this.context = context;
        this.targetDir = targetDir;
        this.storage = new Storage(context);
    }

    public void create() {
        monitorContentObserver();
        sync();
        // Android 6.0 has a bug preventing FileObserver to work with screenshots folder.
        // is simply do not fire on Screenshot file creation.
        watch();
    }

    public void closeOrganizes() {
        for (File f : organizes.keySet()) {
            FileObserver fo = organizes.get(f);
            fo.stopWatching();
        }
        organizes.clear();
    }

    public void close() {
        closeOrganizes();
        open.clear();
        if (mediaObserver != null) {
            context.getContentResolver().unregisterContentObserver(mediaObserver);
        }
        mediaObserver = null;
        handler.removeCallbacks(sync);
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
    public ArrayList<Uri> generateDirs() {
        ArrayList<Uri> dd = new ArrayList<>();
        ArrayList<File> dirs = generateDcim();
        for (File f : dirs) {
            dd.add(Uri.fromFile(f));
        }
        if (screenshotsPath.exists() && screenshotsPath.isDirectory())
            dd.add(Uri.fromFile(screenshotsPath));
        return dd;
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

        closeOrganizes();
        watch();

        Map<Uri, Stats> list = generateFiles();

        if (list.isEmpty())
            return true;

        for (Uri f : new TreeSet<>(list.keySet())) {
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
        for (Uri d : watchingFolders) {
            if (Build.VERSION.SDK_INT >= 21 && Storage.isTreeUri(d)) { // create monitor for internal storage
                String id = DocumentsContract.getTreeDocumentId(d);
                String[] ss = id.split(":");
                if (ss[0].equals(Storage.STORAGE_PRIMARY)) {
                    File path = Environment.getExternalStorageDirectory();
                    if (ss.length > 1) // len == 1 if root folder
                        path = new File(path, ss[1]);
                    d = Uri.fromFile(path);
                }
            }
            String s = d.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File f = new File(d.getPath());
                watchFiles(f);
            }
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

    void removeOpen(Uri ff) {
        for (int i = 0; i < open.size(); i++) {
            Uri f = open.get(i);
            if (f.equals(ff)) {
                open.remove(i);
                return; // remove one
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
                if (mask(event, FileObserver.CREATE)) {
                    old.remove(f);
                }
                if (mask(event, FileObserver.OPEN)) {
                    open.add(Uri.fromFile(f));
                    old.remove(f);
                }
                if (mask(event, FileObserver.MODIFY) || mask(event, FileObserver.ACCESS)) {
                    old.remove(f);
                }
                if (mask(event, FileObserver.DELETE) || mask(event, FileObserver.MOVED_FROM)) {
                    removeOpen(Uri.fromFile(f));
                }
                if (mask(event, FileObserver.CLOSE_NOWRITE) || mask(event, FileObserver.CLOSE_WRITE) || mask(event, FileObserver.MOVED_TO)) {
                    removeOpen(Uri.fromFile(f));
                    sync(); //moveFile(ff);
                }
            }
        };
        fo.startWatching();
        organizes.put(path, fo);
        return fo;
    }

    // generate file list based on current folders ('watchingFolders')
    Map<Uri, Stats> generateFiles() {
        Map<Uri, Stats> ff = new HashMap<>();
        for (Uri f : watchingFolders) {
            try {
                for (Uri fd : generateFiles(f)) {
                    ff.put(fd, new Stats(fd));
                }
            } catch (SecurityException e) {
                Log.d(TAG, "unable to scan", e);
            }
        }
        return ff;
    }

    // load file list from dir
    List<Uri> generateFiles(Uri dir) {
        ArrayList<Uri> list = new ArrayList<>();
        String s = dir.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor childCursor = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                resolver.takePersistableUriPermission(dir, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Uri f = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getTreeDocumentId(dir));
                childCursor = resolver.query(f, null, null, null, null);
                if (childCursor != null) {
                    while (childCursor.moveToNext()) {
                        String mime = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                        if (mime.equals(DocumentsContract.Document.MIME_TYPE_DIR))
                            continue;
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        Uri doc = DocumentsContract.buildDocumentUriUsingTree(dir, id);
                        list.add(doc);
                    }
                }
            } finally {
                if (childCursor != null)
                    childCursor.close();
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File d = new File(dir.getPath());
            File[] ff = d.listFiles();
            if (ff != null) {
                for (File f : ff) {
                    if (f.isDirectory() || f.isHidden())
                        continue;
                    list.add(Uri.fromFile(f));
                }
            }
        } else {
            throw new RuntimeException("unknwon scheme");
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

    void moveFile(Uri f) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String s = shared.getString(MoverApplication.PREFERENCE_NAME, "%f");

        Date date = new Date(storage.getLastModified(f));

        s = getFormatted(s, storage.getNameNoExt(f), date);

        final Uri contentUri = targetDir;
        String q = contentUri.getScheme();

        if (Build.VERSION.SDK_INT >= 21 && q.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver resolver = context.getContentResolver();
            resolver.takePersistableUriPermission(contentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        String n = storage.getName(f);
        if (n == null)
            return; // unable to get name, broken or missing file
        String ext = Storage.getExt(n);

        Uri to = storage.getNextFile(contentUri, s, ext);

        try {
            to = storage.move(f, to);
        } catch (RuntimeException e) {
            Log.d(TAG, "move failed", e);
            Throwable th = e;
            while (th.getCause() != null)
                th = th.getCause();
            Toast.makeText(context, "Unable to MOVE " + th.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (to == null)
            return; // unable to move

        Log.d(TAG, "MOVE [" + f + " to " + storage.getDisplayName(to) + "]");

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(to);
        context.sendBroadcast(mediaScanIntent);

        Toast.makeText(context, "MOVE [" + storage.getDisplayName(to) + "]", Toast.LENGTH_SHORT).show();
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
