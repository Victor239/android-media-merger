package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class AssetsDexLoader {
    public static String TAG = AssetsDexLoader.class.getSimpleName();

    public static int DELAY_TEST = 1 * AlarmManager.MIN1; // load timeout
    public static String JSON = "gradle-android-dx.json";

    public static final String JAR = "jar";
    public static final String DEX = "dex";
    public static final String CLASSES = "classes.dex";
    public static final String CODE_CAHCE = "code_cache";

    public static DexFile[] getDexs(ClassLoader l) {
        try {
            Field mDexs = getPrivateField(l.getClass(), "mDexs");
            return (DexFile[]) mDexs.get(l);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File getCodeCacheDir(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            return context.getCodeCacheDir();
        } else {
            File file = new File(context.getApplicationInfo().dataDir, CODE_CAHCE);
            if (!Storage.mkdirs(file))
                throw new RuntimeException("unable to create: " + file);
            return file;
        }
    }

    public static File getExternalCodeCacheDir(Context context) {
        File ext = context.getExternalCacheDir();
        if (ext == null)
            return null;
        else
            ext = new File(ext.getParentFile(), CODE_CAHCE);
        if (!Storage.mkdirs(ext))
            return null;
        return ext;
    }

    public static File extract(Context context, String asset) throws IOException { // extract asset into .jar
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = new File(context.getCacheDir(), Storage.getNameNoExt(asset) + "." + JAR);
        FileOutputStream os = new FileOutputStream(tmp);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static File pack(Context context, String asset) throws IOException { // pack .dex into .jar/classes.dex
        AssetManager am = context.getAssets();
        InputStream is = am.open(asset);
        File tmp = new File(context.getCacheDir(), Storage.getNameNoExt(asset) + "." + JAR);
        ZipOutputStream os = new ZipOutputStream(new FileOutputStream(tmp));
        ZipEntry e = new ZipEntry(CLASSES);
        os.putNextEntry(e);
        IOUtils.copy(is, os);
        os.close();
        is.close();
        return tmp;
    }

    public static Field getPrivateField(Class cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static Method getPrivateMethod(Class c, String name, Class<?>... args) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    public static Constructor getPrivateConstructor(Class c, Class<?>... args) throws NoSuchMethodException {
        Constructor m = c.getDeclaredConstructor(args);
        m.setAccessible(true);
        return m;
    }

    public static Object newInstance(final Class clazz) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        if (Build.VERSION.SDK_INT < 11) { // API10
            return getPrivateMethod(ObjectInputStream.class, "newInstance", Class.class, Class.class).invoke(null, clazz, Object.class);
        } else if (Build.VERSION.SDK_INT < 18) {
            throw new NoSuchMethodException(); // API11-17
        } else { // API18+
            Class Unsafe = Class.forName("sun.misc.Unsafe");
            return Unsafe.getDeclaredMethod("allocateInstance", Class.class).invoke(Unsafe.getDeclaredMethod("getUnsafe").invoke(null), clazz);
        }
    }

    public static ClassLoader deps(Context context, Json json, String... deps) {
        ClassLoader parent = null;
        try {
            if (deps == null || deps.length == 0) {
                for (Json.Library dep : json.deps) {
                    for (Json.Library a : json.getDeps(dep))
                        parent = deps(context, parent, a.dex);
                }
            } else {
                for (String dep : deps) {
                    for (Json.Library a : json.getDeps(dep))
                        parent = deps(context, parent, a.dex);
                }
            }
            return parent; // return null if no jars/dex found
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean check(Json json, String... deps) { // check all deps exists. json based
        if (deps == null || deps.length == 0) {
            for (Json.Library dep : json.deps) {
                ArrayList<Json.Library> a = json.getDeps(dep);
                if (a.isEmpty())
                    return false;
            }
        } else {
            for (String dep : deps) {
                ArrayList<Json.Library> a = json.getDeps(dep);
                if (a.isEmpty())
                    return false;
            }
        }
        return true;
    }

    public static boolean filter(String a, String dep) {
        return a.startsWith(dep);
    }

    public static boolean filter(String a, String... deps) {
        for (String dep : deps) {
            String[] dd = dep.split(":");
            if (dd.length == 1) {
                if (filter(a, dep))
                    return true;
            }
        }
        return false;
    }

    public static String[] assets(Context context) throws IOException {
        AssetManager am = context.getAssets();
        return am.list("");
    }

    public static ClassLoader deps(Context context, String... deps) {
        ClassLoader parent = null;
        try {
            String[] aa = assets(context);
            if (aa == null)
                return parent;
            for (String a : aa) {
                if (filter(a, deps))
                    parent = deps(context, parent, a);
            }
            return parent; // return null if no jars/dex found
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean check(String[] aa, String... deps) { // check all deps exists. file based
        if (aa == null)
            return false;
        for (String a : aa) {
            if (!filter(a, deps))
                return false;
        }
        return true;
    }

    public static boolean check(Context context, String... deps) { // check all deps exists
        try {
            Json json = new Json(context);
            if (json.mm.isEmpty()) {
                String[] aa = assets(context);
                return check(aa, deps); // file based search
            } else {
                if (!check(json, deps)) // json based search
                    return false;
                String[] aa = assets(context);
                return check(aa, deps); // file based search
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader deps(Context context, ClassLoader parent, String a) throws IOException {
        if (a.endsWith("." + JAR)) {
            File tmp = extract(context, a);
            parent = load(context, tmp, parent);
            tmp.delete(); // getCodeCacheDir() should keep classes
        }
        if (a.endsWith("." + DEX)) {
            File tmp = pack(context, a);
            parent = load(context, tmp, parent);
            tmp.delete(); // getCodeCacheDir() should keep classes
        }
        return parent;
    }

    public static ClassLoader load(Context context, File tmp, ClassLoader parent) {
        Log.d(TAG, "Loading: " + tmp);
        if (parent == null)
            parent = context.getClassLoader();
        File ext = getExternalCodeCacheDir(context);
        if (ext == null)
            ext = getCodeCacheDir(context);
        return new DexClassLoader(tmp.getPath(), ext.getPath(), null, parent);
    }

    public static boolean setBootClassLoader(ClassLoader l) {
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            Field parent = getPrivateField(ClassLoader.class, "parent");
            ClassLoader old = (ClassLoader) parent.get(system);
            ArrayList<ClassLoader> ss = new ArrayList<>();
            ClassLoader cl = system;
            while (ss.add(cl) && (cl = (ClassLoader) parent.get(cl)) != null) {
                if (cl == l)
                    return true; // already installed
            }
            ClassLoader cc = null;
            cl = l;
            while ((cl = (ClassLoader) parent.get(cl)) != null && !ss.contains(cl))
                cc = cl;
            parent.set(cc, old);
            parent.set(system, l);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static class Json {
        public LinkedHashMap<String, Library> mm = new LinkedHashMap<>(); // all deps including depencecies
        public ArrayList<Library> deps; // top level deps (build.gradle list)

        public static class Library {
            public String group;
            public String id;
            public String v;
            public String dex;
            public String md5;
            public long size;
            public boolean empty;
            public ArrayList<Library> deps = new ArrayList<>();

            public String getId() {
                return group + ":" + id + ":" + v;
            }

            @Override
            public String toString() {
                return getId();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Library library = (Library) o;
                return AssetsDexLoader.equals(group, library.group) && AssetsDexLoader.equals(id, library.id) && AssetsDexLoader.equals(v, library.v);
            }

            @Override
            public int hashCode() {
                return (getId()).hashCode();
            }
        }

        public Json(Context context) {
            AssetManager am = context.getAssets();
            InputStream is = null;
            JSONArray json = null;
            try {
                is = am.open(JSON);
                String j = IOUtils.toString(is, Charset.defaultCharset());
                json = new JSONArray(j);
                deps = loadDeps(json);
            } catch (JSONException | IOException e) {
                Log.w(TAG, e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

        public ArrayList<Library> loadDeps(JSONArray aa) {
            ArrayList<Library> kk = new ArrayList<>();
            for (int i = 0; i < aa.length(); i++) {
                try {
                    JSONObject o = aa.getJSONObject(i);
                    Library info = new Library();
                    info.group = o.getString("group");
                    info.id = o.getString("id");
                    info.v = o.getString("v");
                    info.dex = o.getString("asset");
                    info.size = o.optLong("size");
                    info.md5 = o.optString("md5");
                    info.empty = o.optBoolean("empty", false);
                    JSONArray deps = o.optJSONArray("deps");
                    if (deps != null && deps.length() > 0)
                        info.deps = loadDeps(deps);
                    mm.put(info.getId(), info);
                    kk.add(info);
                } catch (JSONException e) {
                    Log.w(TAG, e);
                }
            }
            return kk;
        }

        public ArrayList<Library> getDeps(String dep) {
            String[] dd = dep.split(":");
            ArrayList<Library> kk = new ArrayList<>();
            if (dd.length == 3) { // group:artifact:version specified
                Library info = mm.get(dep);
                if (info != null)
                    return info.deps;
            } // no else
            if (dd.length == 1) { // artifact only specified
                for (String id : mm.keySet()) {
                    String[] ss = id.split(":");
                    if (ss[1].equals(dd[0])) {
                        Library info = mm.get(id);
                        for (Library l : getDeps(info)) {
                            if (kk.contains(l))
                                continue;
                            kk.add(l);
                        }
                    }
                }
            }
            return kk;
        }

        public ArrayList<Library> getDeps(Library info) {
            ArrayList<Library> kk = new ArrayList<>();
            if (info.deps != null) {
                for (Library l : info.deps) {
                    kk.addAll(getDeps(l));
                    if (!l.empty)
                        kk.add(l);
                }
            }
            if (!info.empty)
                kk.add(info);
            return kk;
        }
    }

    public static class ThreadLoader {
        public static final HashMap<Class, Object> locks = new HashMap<>();
        public static Thread thread;

        public Context context;
        public String[] deps; // delayed load
        public Handler handler;
        public Runnable test = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "loading dex timeout...");
                clear();
            }
        };

        public ThreadLoader(Context context) {
            this.context = context;
            this.handler = new Handler(Looper.getMainLooper());
        }

        public ThreadLoader(Context context, String... deps) {
            this(context);
            this.deps = Arrays.asList(deps).toArray(new String[]{});
        }

        public ThreadLoader(Context context, boolean block, String... deps) {
            this(context, deps);
            init(block);
        }

        public void init(boolean block) {
            if (need())
                load(block);
        }

        public boolean need() {
            return true;
        }

        public ClassLoader deps() {
            return AssetsDexLoader.deps(context, deps);
        }

        public void clear() {
            Log.d(TAG, "clear dexes");
            clear(getExternalCodeCacheDir(context));
            clear(getCodeCacheDir(context));
        }

        public void clear(File tmp) {
            if (tmp == null)
                return;
            File[] ff = tmp.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().endsWith("." + DEX);
                }
            });
            if (ff == null)
                return;
            for (File f : ff) {
                Log.d(TAG, "delete " + f);
                f.delete();
            }
        }

        public void load() { // load(block) only call
            Log.d(TAG, "loading...");
            handler.postDelayed(test, DELAY_TEST);
            try {
                done(deps());
            } finally {
                handler.removeCallbacks(test);
            }
            Log.d(TAG, "load done");
        }

        public Object lock() {
            Class k = getClass();
            synchronized (locks) {
                Object v = locks.get(k);
                if (v == null) {
                    v = new Object();
                    locks.put(k, v);
                }
                return v;
            }
        }

        public void load(boolean block) {
            Thread t;
            synchronized (lock()) {
                if (thread == null) {
                    if (block) {
                        load();
                    } else {
                        thread = new Thread("ThreadLoader") {
                            @Override
                            public void run() {
                                try {
                                    load();
                                } catch (Exception e) {
                                    Log.w(TAG, e);
                                    error(e);
                                }
                            }
                        };
                        thread.start();
                    }
                    return;
                } else {
                    t = thread;
                }
            }
            if (block) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } // else return
        }

        public void error(Exception e) {
        }

        public void done(ClassLoader l) {
        }
    }

    public static class JsonThreadLoader extends ThreadLoader {
        public Json json;

        public JsonThreadLoader(Context context) {
            super(context);
            json = new Json(context);
        }

        public JsonThreadLoader(Context context, String... deps) {
            super(context, deps);
            json = new Json(context);
        }

        public JsonThreadLoader(Context context, boolean block, String... deps) {
            super(context, deps);
            json = new Json(context);
            init(block);
        }

        @Override
        public void clear(File tmp) {
            if (tmp == null)
                return;
            File[] ff = tmp.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    for (Json.Library l : json.mm.values()) {
                        if (pathname.getName().endsWith(l.dex))
                            return true;
                    }
                    return false;
                }
            });
            if (ff == null)
                return;
            for (File f : ff) {
                Log.d(TAG, "delete " + f);
                f.delete();
            }
        }

        @Override
        public ClassLoader deps() {
            return AssetsDexLoader.deps(context, json, deps);
        }
    }
}
