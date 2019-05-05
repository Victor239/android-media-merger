package com.github.axet.mover.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.mover.R;
import com.github.axet.mover.services.FileObserverService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    public static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat TIME = new SimpleDateFormat("HH.mm.ss");
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

    ContentObserver mediaObserver;
    TreeMap<File, FileObserver> organizes = new TreeMap<>();

    final Object lock = new Object();
    Thread thread;
    Runnable sync = new Runnable() { // sync runnable
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

    public static boolean isHidden(Storage.Node n) {
        return n.name.startsWith(".");
    }

    // check if file save to move (it is not open by another apps)
    //
    // seems like android allow to write currently writting file. so. this trick does not work.
    public static boolean isSafe(File f) {
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

    public static Uri moveFile(Context context, Uri f, Uri to) {
        to = Storage.move(context, f, to);
        if (to == null)
            return null; // unable to move
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(to);
        context.sendBroadcast(mediaScanIntent);
        return to;
    }

    public static String getFormatted(Context context, String f, Uri targetUri, Date date) {
        String ne = Storage.getNameNoExt(context, targetUri);

        String p = "."; // root

        String s = targetUri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id = DocumentsContract.getTreeDocumentId(targetUri);
            String[] ss = id.split(Storage.COLON, 2);
            if (!ss[1].isEmpty())
                p = ss[1];
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File a = Storage.getFile(targetUri);
            a = a.getParentFile();
            if (a != null)
                p = a.getName();
        } else {
            throw new Storage.UnknownUri();
        }

        f = f.replaceAll("%f", Storage.filterDups(ne));
        f = f.replaceAll("%t", "" + (date.getTime() / 1000));
        f = f.replaceAll("%d", SIMPLE.format(date));
        f = f.replaceAll("%i", ISO8601.format(date));
        f = f.replaceAll("%p", p);
        f = f.replaceAll("%D", DATE.format(date));
        f = f.replaceAll("%T", TIME.format(date));
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
            last = Storage.getLastModified(context, u);
            size = Storage.getLength(context, u);
        }

        public Stats(Storage.Node n) {
            last = n.last;
            size = n.size;
        }

        @Override
        public boolean equals(Object o) {
            Stats n = (Stats) o;
            return last == n.last && size == n.size;
        }
    }

    public class LastModified implements Comparator<Uri> {
        @Override
        public int compare(Uri o1, Uri o2) {
            final long result = Storage.getLastModified(context, o1) - Storage.getLastModified(context, o2);
            if (result < 0) {
                return -1;
            } else if (result > 0) {
                return 1;
            } else {
                return 0;
            }
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
        if (mediaObserver != null)
            context.getContentResolver().unregisterContentObserver(mediaObserver);
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
        for (File f : dirs)
            dd.add(Uri.fromFile(f));
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
        if (last + REFRESH_TIME > cur)
            return false;

        last = cur;
        watchingFolders = generateDirs();

        closeOrganizes();
        watch();

        Map<Uri, Stats> list = list();

        if (list.isEmpty())
            return true;

        synchronized (lock) {
            if (thread != null)
                return false;
        }

        final ArrayList<Uri> mm = new ArrayList<>();

        for (Uri f : new TreeSet<>(list.keySet())) {
            if (old.containsKey(f) && !open.contains(f)) {
                Stats sold = old.get(f);
                Stats snew = list.get(f);
                if (sold.equals(snew)) {
                    mm.add(f);
                    list.remove(f);
                } else {
                    Log.d(TAG, "Delaying: " + f);
                }
            }
        }

        old = list;

        thread = new Thread("sync") {
            @Override
            public void run() {
                try {
                    Collections.sort(mm, new LastModified());
                    String[] ss = new String[mm.size()];
                    for (int i = 0; i < mm.size(); i++) {
                        Uri f = mm.get(i);
                        ss[i] = getFormatted(f);
                    }
                    Uri[] tt = new Uri[mm.size()];
                    for (int i = 0; i < mm.size(); i++) {
                        if (tt[i] == null) {
                            Uri f = mm.get(i);
                            String s = ss[i];
                            int count = 0;
                            for (int k = i + 1; k < mm.size(); k++) {
                                String m = ss[k];
                                if (s.equals(m)) {
                                    tt[i] = getMoveTo(f, s, 1);
                                    tt[k] = getMoveTo(f, m, count + 2);
                                    count++;
                                }
                            }
                        }
                    }
                    for (int i = 0; i < mm.size(); i++) {
                        Uri f = mm.get(i);
                        Uri t = tt[i];
                        if (t == null)
                            t = getMoveTo(f, ss[i], 0);
                        if (!FileObserverService.isEnabled(context))
                            return;
                        Uri to = moveFile(context, f, t);
                        Log.d(TAG, "MOVE [" + f + " to " + Storage.getDisplayName(context, to) + "]");
                        Toast.Post(context, context.getString(R.string.file_moved, Storage.getDisplayName(context, to)));
                    }
                } catch (RuntimeException e) {
                    Log.d(TAG, "MOVE FAILED", e);
                    Toast.Post(context, context.getString(R.string.move_failed, ErrorDialog.toMessage(e)));
                } finally {
                    synchronized (lock) {
                        thread = null;
                    }
                }
            }
        };
        thread.start();

        return false; // rescan again, moveFile can be slow, more files appear
    }

    public void watch() {
        for (Uri d : watchingFolders) {
            if (Build.VERSION.SDK_INT >= 21 && Storage.isTreeUri(d)) // create monitor for internal storage
                d = StorageProvider.filterFolderIntent(context, d);
            String s = d.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE))
                watchFiles(Storage.getFile(d));
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
                    sync(); // moveFile(ff);
                }
            }
        };
        fo.startWatching();
        organizes.put(path, fo);
        return fo;
    }

    // generate file list based on current folders ('watchingFolders')
    Map<Uri, Stats> list() {
        Map<Uri, Stats> ff = new HashMap<>();
        for (Uri f : watchingFolders) {
            try {
                for (Storage.Node n : list(f)) {
                    ff.put(n.uri, new Stats(n));
                }
            } catch (SecurityException e) {
                Log.d(TAG, "unable to scan", e);
            }
        }
        return ff;
    }

    // load file list from uri
    List<Storage.Node> list(Uri uri) {
        return storage.list(context, uri, new Storage.NodeFilter() {
            @Override
            public boolean accept(Storage.Node n) {
                return !n.dir && !isHidden(n);
            }
        });
    }

    public String getFormatted(Uri f) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String s = shared.getString(MoverApplication.PREFERENCE_NAME, "%f");

        Date date = new Date(Storage.getLastModified(context, f));

        return getFormatted(context, s, f, date);
    }

    public Uri getMoveTo(Uri f, String s, int i) {
        final Uri contentUri = targetDir;
        String q = contentUri.getScheme();

        if (Build.VERSION.SDK_INT >= 21 && q.equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver resolver = context.getContentResolver();
            resolver.takePersistableUriPermission(contentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        String n = Storage.getName(context, f);
        if (n == null)
            return null; // unable to get name, broken or missing file
        String ext = Storage.getExt(n);

        return Storage.getNextFile(context, contentUri, s, i, ext);
    }

    public void monitorContentObserver() {
        ContentResolver resolver = context.getContentResolver();
        if (mediaObserver != null)
            resolver.unregisterContentObserver(mediaObserver);

        mediaObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                if (uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()))
                    sync();
            }
        };

        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
    }
}
