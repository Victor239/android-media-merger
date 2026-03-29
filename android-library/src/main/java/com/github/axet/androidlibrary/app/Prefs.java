package com.github.axet.androidlibrary.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class Prefs {
    private static final String TAG = Prefs.class.getSimpleName();

    public static PrefsMap DELAYED = new PrefsMap();

    public static void create(Context context) {
        DELAYED.load(context);
    }

    public static Class<?> getCallingClass() {
        StackTraceElement[] ss = Thread.currentThread().getStackTrace();
        String k = Prefs.class.getCanonicalName();
        int i = 1;
        for (; i < ss.length; i++) {
            StackTraceElement s = ss[i];
            if (s.getClassName().equals(k))
                break;
        }
        for (; i < ss.length; i++) {
            StackTraceElement s = ss[i];
            if (!s.getClassName().equals(k))
                break;
        }
        if (i < ss.length) {
            StackTraceElement s = ss[i];
            try {
                return Class.forName(s.getClassName());
            } catch (ClassNotFoundException ignore) {
                return null;
            }
        }
        return null;
    }

    public static String PrefString(int key) {
        String s = new String();
        DELAYED.add(getCallingClass(), key, s);
        return s;
    }

    public static Long PrefLong(int key) {
        Long l = new Long(Long.MIN_VALUE);
        DELAYED.add(getCallingClass(), key, l);
        return l;
    }

    public static Integer PrefInt(int key) {
        Integer l = new Integer(Integer.MIN_VALUE);
        DELAYED.add(getCallingClass(), key, l);
        return l;
    }

    public static Boolean PrefBool(int key) {
        Boolean b = new Boolean(Boolean.FALSE);
        DELAYED.add(getCallingClass(), key, b);
        return b;
    }

    public static Prefs from(Context context) {
        return new Prefs(context);
    }

    public static class PrefDelayed {
        public Class c; // st
        public int hash; // System.identityHashCode
        public int key;

        public PrefDelayed(Class c, int key, int h) {
            this.c = c;
            this.key = key;
            this.hash = h;
        }
    }

    public static class PrefsMap extends HashMap<Integer, PrefDelayed> {
        public void load(Context context) {
            for (Integer k : keySet()) {
                PrefDelayed d = get(k);
                for (Field f : d.c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        try {
                            Object v = f.get(null);
                            if (System.identityHashCode(v) == d.hash) {
                                String dv = context.getString(d.key);
                                f.setAccessible(true);
                                f.set(null, dv);
                            }
                        } catch (IllegalAccessException e) {
                            Log.w(TAG, e);
                        }
                    }
                }
            }
        }

        public void add(Class<?> c, int key, Object o) {
            PrefDelayed d = new PrefDelayed(c, key, System.identityHashCode(o));
            put(key, d);
        }
    }

    public SharedPreferences shared;

    public Prefs(Context context) {
        shared = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public Prefs(SharedPreferences shared) {
        this.shared = shared;
    }
}
