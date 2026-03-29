package com.github.axet.androidlibrary.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;
import androidx.appcompat.view.WindowCallbackWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//
// Add users permission to app manifest:
//
// <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
//
public class OptimizationPreferenceCompat extends SwitchPreferenceCompat {
    public static String TAG = OptimizationPreferenceCompat.class.getSimpleName();

    // http://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it/35220476
    public static Intent huawei = IntentClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
    // http://stackoverflow.com/questions/37205106/how-do-i-avoid-that-my-app-enters-optimization-on-samsung-devices
    // http://stackoverflow.com/questions/34074955/android-exact-alarm-is-always-3-minutes-off/34085645#34085645
    public static Intent samsung = IntentClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity");
    // http://www.ithao123.cn/content-11070929.html
    public static Intent miui = IntentClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
    public static Intent vivo = IntentClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity");
    public static Intent oppo = IntentClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity");

    public static Intent[] ALL = new Intent[]{huawei, samsung, miui, vivo, oppo};
    public static Intent[] COMMON = new Intent[]{miui, vivo, oppo};

    public static int REFRESH = 15 * AlarmManager.MIN1;
    public static int CHECK_DELAY = 5 * AlarmManager.MIN1;
    public static boolean ICON = false; // for(api23+ true means){ show persistent icon option } else {persistent service, icon}; use setIcon if default true and service persisted

    public static long BOOT; // boot cache time, stable time

    public static int BOOT_DELAY = 2 * 60 * 1000;

    // checkbox for old phones, which fires 15 minutes event
    public static final String PING = OptimizationPreferenceCompat.class.getCanonicalName() + ".PING";
    public static final String PONG = OptimizationPreferenceCompat.class.getCanonicalName() + ".PONG";
    public static final String SERVICE_CHECK = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_CHECK";
    public static final String SERVICE_RESTART = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_RESTART";
    public static final String SERVICE_UPDATE = OptimizationPreferenceCompat.class.getCanonicalName() + ".SERVICE_UPDATE";
    public static final String ICON_UPDATE = OptimizationPreferenceCompat.class.getCanonicalName() + ".ICON_UPDATE";

    public static final String DONT_KEEP = "always_finish_activities"; // Don't keep activities global setting
    public static final String SHOW_ANR = "anr_show_background"; // Show all ANRs secure setting

    // all service related code, for old phones, where AlarmManager will be used to keep app running
    protected Class<? extends Service> service;

    public static boolean findPermission(Context context, String p) { // pm.checkPermission() - current package method
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            for (String i : info.requestedPermissions) {
                if (i.equals(p))
                    return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "unable to find permission", e);
        }
        return false;
    }

    public static boolean findPermissions(Context context, String[] pp) {
        for (String p : pp) {
            if (!findPermission(context, p))
                return false;
        }
        return true;
    }

    public static long getBootTime() { // rounded time, to keep it a bit more stable
        if (BOOT != 0)
            return BOOT;
        ArrayList<Pair<Long, Long>> list = new ArrayList<>();
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            long start = System.nanoTime();
            long el;
            if (Build.VERSION.SDK_INT >= 17)
                el = SystemClock.elapsedRealtimeNanos();
            else
                el = SystemClock.elapsedRealtime() * 1000 * 1000;
            long last = System.currentTimeMillis() * 1000 * 1000;
            long end = System.nanoTime();
            long d = end - start;
            long time = last - el;
            min = Math.min(min, time);
            max = Math.max(max, time);
            list.add(new Pair<>(d, time));
        }
        Collections.sort(list, new Comparator<Pair<Long, Long>>() {
            @Override
            public int compare(Pair<Long, Long> o1, Pair<Long, Long> o2) {
                return Long.compare(o1.first, o2.first);
            }
        });
        int i = 0;
        long d = list.get(i).first;
        long total = list.get(i).second;
        long count = 1;
        i++;
        while (i < list.size() && list.get(i).first == d) {
            total += list.get(i).second;
            count++;
            i++;
        }
        long time = total / count / 1000 / 1000 / 1000 * 1000;
        BOOT = time;
        return time;
    }

    public static ComponentName startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26) {
            Class k = context.getClass();
            try {
                Log.d(TAG, "startForegroundService(" + intent.getComponent().flattenToShortString() + ")");
                Method m = k.getMethod("startForegroundService", Intent.class);
                return (ComponentName) m.invoke(context, intent);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            return context.startService(intent);
        }
    }

    public static void setPersistentServiceIcon(Context context, boolean b) { // Persistent service icon false for API26+
        if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26)
            b = false; // api 26 requires mandatory persistent icon, hide option
        ICON = b;
    }

    public static void setEventServiceIcon(boolean b) { // Event Service Icon (AlarmManager depend service) always TRUE
        ICON = b;
    }

    public static Intent serviceCheck(Context context, Class<? extends Service> service) {
        Intent intent = new Intent(context, service);
        intent.setAction(SERVICE_CHECK);
        return intent;
    }

    public static void disableKill(Context context, Class<?> klass) {
        ComponentName name = new ComponentName(context, klass);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    public static Intent IntentClassName(String p, String n) {
        Intent intent = new Intent();
        intent.setClassName(p, n);
        return intent;
    }

    public static boolean isBackgroundRestricted(Context context) {
        if (Build.VERSION.SDK_INT >= 28) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            try {
                return (boolean) am.getClass().getDeclaredMethod("isBackgroundRestricted").invoke(am);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @TargetApi(23)
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final String n = context.getPackageName();
        return pm.isIgnoringBatteryOptimizations(n);
    }

    @TargetApi(19)
    public static String getUserSerial(Context context) {
        Object userManager = context.getSystemService(Context.USER_SERVICE);
        if (null == userManager)
            return "";
        try {
            Method myUserHandleMethod = android.os.Process.class.getMethod("myUserHandle", (Class<?>[]) null);
            Object myUserHandle = myUserHandleMethod.invoke(android.os.Process.class, (Object[]) null);
            Method getSerialNumberForUser = userManager.getClass().getMethod("getSerialNumberForUser", myUserHandle.getClass());
            Long userSerial = (Long) getSerialNumberForUser.invoke(userManager, myUserHandle);
            if (userSerial != null)
                return String.valueOf(userSerial);
            else
                return "";
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException |
                 IllegalAccessException ignored) {
        }
        return "";
    }

    public static void huaweiProtectedApps(Context context) {
        try {
            String cmd = "am start -n " + huawei.getComponent().flattenToShortString();
            if (Build.VERSION.SDK_INT >= 17)
                cmd += " --user " + getUserSerial(context);
            Runtime.getRuntime().exec(cmd);
        } catch (IOException ignored) {
        }
    }

    public static boolean isCallable(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static boolean isHuawei(Context context) {
        return isCallable(context, huawei);
    }

    public static boolean isSamsung(Context context) {
        return isCallable(context, samsung);
    }

    public static boolean startActivity(Context context, Intent intent) { // for open / share intents
        try { // TODO replace exception catching with isCallable like method
            context.startActivity(intent);
            return true;
        } catch (Exception e) { // SecurityException and others
            Log.d(TAG, "unable to start activity", e);
            return false;
        }
    }

    @TargetApi(23)
    public static void showOptimization(Context context) {
        final String n = context.getPackageName();
        if (isIgnoringBatteryOptimizations(context)) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(context, intent);
        } else {
            if (!findPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                Log.e(TAG, "Permission not granted: " + Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + n));
            if (!startActivity(context, intent)) { // some samsung phones does not have this
                intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(context, intent);
            }
        }
    }

    // TODO: Stubbed - PreferenceViewHolder constructor is not public in AndroidX
    @SuppressLint("RestrictedApi")
    public static PreferenceViewHolder inflate(Preference p, ViewGroup root) {
        // PreferenceViewHolder(View) constructor is not accessible in AndroidX
        // This method cannot be implemented without using reflection or other workarounds
        return null;
    }

    public static Context themedContext(Context context) {
        final TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(androidx.preference.R.attr.preferenceTheme, tv, true);
        int theme = tv.resourceId;
        if (theme == 0)
            throw new IllegalStateException("Must specify preferenceTheme in theme");
        return new ContextThemeWrapper(context, theme);
    }

    public static void build(final WarningBuilder builder, String msg, DialogInterface.OnClickListener click) {
        final Context context = builder.getContext();
        builder.builder.setTitle(R.string.optimization_dialog);
        final DialogInterface.OnClickListener opt = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showOptimization(context);
            }
        };
        if (ICON) {
            if (click != null)
                builder.builder.setNeutralButton(R.string.menu_settings, click);
            LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.VERTICAL);
            int dp5 = ThemeUtils.dp2px(context, 5);
            ll.setPadding(dp5, dp5, dp5, dp5);
            TextView desc = new TextView(context);
            TextViewCompat.setTextAppearance(desc, androidx.appcompat.R.style.TextAppearance_AppCompat_Body1);
            desc.setText(msg);
            ll.addView(desc);
            builder.icon = new SwitchPreferenceCompat(themedContext(context));
            builder.icon.setTitle(context.getString(R.string.optimization_icon));
            builder.icon.setSummary(context.getString(R.string.optimization_icon_summary));
            builder.iconHolder = inflate(builder.icon, null);
            builder.icon.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean b = (boolean) newValue;
                    builder.setIcon(b);
                    return false;
                }
            });
            builder.updateIcon();
            if (Build.VERSION.SDK_INT >= 23) {
                ll.addView(builder.iconHolder.itemView); // Persistnet Icon Option
                builder.optimization = new SwitchPreferenceCompat(themedContext(context));
                builder.optimization.setTitle(context.getString(R.string.optimization_system));
                builder.optimization.setSummary(context.getString(R.string.optimization_system_summary));
                Drawable d = context.getDrawable(R.drawable.ic_open_in_new_black_24dp);
                d = DrawableCompat.wrap(d);
                DrawableCompat.setTint(d, ThemeUtils.getThemeColor(context, android.R.attr.colorForeground));
                builder.optimization.setIcon(d);
                builder.optimizationHolder = inflate(builder.optimization, null);
                builder.optimization.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        opt.onClick(null, 0);
                        return false;
                    }
                });
                builder.updateOptimization();
                ll.addView(builder.optimizationHolder.itemView);
            } else {
                final SwitchPreferenceCompat alive = new SwitchPreferenceCompat(themedContext(context));
                alive.setTitle(context.getString(R.string.optimization_alive));
                alive.setSummary(context.getString(R.string.optimization_alive_summary));
                alive.setChecked(getState23(builder.context, builder.key).service);
                final PreferenceViewHolder h = inflate(alive, null);
                final Runnable update = new Runnable() {
                    @Override
                    public void run() {
                        if (alive.isChecked()) {
                            builder.iconHolder.itemView.setVisibility(View.VISIBLE);
                        } else {
                            builder.iconHolder.itemView.setVisibility(View.GONE);
                            builder.setIcon(false);
                        }
                    }
                };
                alive.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        builder.setService(b);
                        alive.setChecked(b);
                        alive.onBindViewHolder(h);
                        update.run();
                        return false;
                    }
                });
                update.run();
                ll.addView(h.itemView);
                ll.addView(builder.iconHolder.itemView); // Persistent Icon below, depends on Keep Alive Service API23<
            }
            if (Build.VERSION.SDK_INT >= 28 && isBackgroundRestricted(context)) {
                builder.restricted = new SwitchPreferenceCompat(themedContext(context));
                builder.restricted.setTitle("Background Restricted");
                builder.restricted.setSummary("Please disable 'Advanced/Battery/Background restriction' option to let app work properly");
                builder.restricted.setChecked(true);
                Drawable d = context.getDrawable(R.drawable.ic_open_in_new_black_24dp);
                d = DrawableCompat.wrap(d);
                DrawableCompat.setTint(d, ThemeUtils.getThemeColor(context, android.R.attr.colorForeground));
                builder.restricted.setIcon(d);
                builder.restrictedHolder = inflate(builder.restricted, null);
                builder.restricted.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        if (!b)
                            Storage.showPermissions(context);
                        return false;
                    }
                });
                ll.addView(builder.restrictedHolder.itemView);
            }
            ScrollView scroll = new ScrollView(context);
            scroll.addView(ll);
            builder.builder.setView(scroll);
            builder.builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        } else {
            builder.builder.setMessage(msg);
            builder.builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            if (Build.VERSION.SDK_INT >= 23) {
                if (click != null)
                    builder.builder.setNeutralButton(R.string.menu_settings, click);
                builder.builder.setPositiveButton(android.R.string.yes, opt);
            } else {
                builder.builder.setPositiveButton(android.R.string.yes, click);
            }
        }
    }

    public static WarningBuilder buildKilledWarning(final Context context, boolean showCommons, String key) {
        WarningBuilder b = buildWarning(context, showCommons, key);
        b.builder.setMessage(R.string.optimization_killed);
        return b;
    }

    public static WarningBuilder buildKilledWarning(final Context context, boolean showCommons, String key, Class service) {
        WarningBuilder b = buildKilledWarning(context, showCommons, key);
        final SettingsReceiver settings = new SettingsReceiver(new Intent(context, service), key);
        b.serviceEnable = new Runnable() {
            @Override
            public void run() {
                settings.start(context);
            }
        };
        b.serviceDisable = new Runnable() {
            @Override
            public void run() {
                settings.stop(context);
            }
        };
        return b;
    }

    public static WarningBuilder buildWarning(final Context context, boolean showCommons, String key) {
        WarningBuilder builder = new WarningBuilder(context, key);
        if (isHuawei(context)) {
            build(builder, "You have to change the power plan to “normal” under settings → power saving to let application be exact on time.", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    huaweiProtectedApps(context);
                }
            });
            return builder;
        } else if (isSamsung(context)) {
            build(builder, "Consider disabling Samsung SmartManager to keep application running in background.", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (!startActivity(context, samsung))
                        Toast.makeText(context, "Unable to show settings", Toast.LENGTH_SHORT).show();
                }
            });
            return builder;
        } else {
            for (Intent intent : COMMON) {
                if (isCallable(context, intent)) {
                    final Intent i = intent;
                    build(builder, context.getString(R.string.optimization_message), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!startActivity(context, i))
                                Toast.makeText(context, "Unable to show settings", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return builder;
                }
            }
        }
        if (showCommons || ICON) {
            build(builder, context.getString(R.string.optimization_message), null);
            return builder;
        } else {
            return null;
        }
    }

    public static void showWarning(Context context, String key) {
        WarningBuilder builder = buildWarning(context, true, key);
        showWarning(context, builder);
    }

    public static void showWarning(Context context, WarningBuilder builder) {
        if (builder != null)
            showWarning(context, builder.create());
        else
            showWarning(context, (AlertDialog) null);
    }

    public static void showWarning(Context context, final AlertDialog d) {
        if (d != null) {
            d.show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23)
            showOptimization(context);
    }

    public static void setKillCheck(Context context, long time, String key) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putString(key, System.currentTimeMillis() + ";" + time);
        edit.apply();
    }

    public static boolean needKillWarning(Context context, String key) { // true - need show warning dialog
        SharedPreferences shared = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        Object n = shared.getAll().get(key);
        long set; // alarm set time
        long next; // alarm next time
        if (n == null) {
            set = System.currentTimeMillis();
            next = 0;
        } else if (n instanceof Long) { // old version
            set = System.currentTimeMillis();
            next = (Long) n;
        } else {
            String[] nn = ((String) n).split(";");
            set = Long.valueOf(nn[0]);
            next = Long.valueOf(nn[1]);
        }
        if (next == 0)
            return false; // no missed alarm
        long now = System.currentTimeMillis();
        if (next > now)
            return false; // alarm in the future
        long boot = getBootTime();
        if (next < boot)
            return false; // we lost alarm, while device were offline, skip warning
        if (set < boot)
            return false; // we did reboot device between set alarm and boot time, skip warning
        return true;
    }

    public static boolean isPersistent(Context context, String key) { // do we need to start EventService even if we have no job to do?
        if (Build.VERSION.SDK_INT >= 23) {
            return OptimizationPreferenceCompat.getState(context, key).icon;
        } else {
            OptimizationPreferenceCompat.State23 state = OptimizationPreferenceCompat.getState23(context, key);
            return state.service || state.icon;
        }
    }

    public static boolean isPersistent(Context context, String key, boolean b) {
        return isPersistent(context, key) || Build.VERSION.SDK_INT < 26 && b;
    }

    public static State23 getState23(Context context, String key) { // <API23
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            String json = shared.getString(key, "");
            return new State23(json);
        } catch (ClassCastException | JSONException e) {
            boolean b = shared.getBoolean(key, false);
            return new State23(b);
        }
    }

    public static State getState(Context context, String key) { // API23+
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            String json = shared.getString(key, "");
            return new State(json);
        } catch (ClassCastException | JSONException e) {
            return new State();
        }
    }

    public static void saveState(Context context, State state, String key) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        try {
            edit.putString(key, state.save().toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        edit.apply();
    }

    public static long getInstallTime(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            if (info.lastUpdateTime > info.firstInstallTime)
                return info.lastUpdateTime;
            else
                return info.firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    public static boolean needBootWarning(Context context, String bootpref, String startpref) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (!shared.getBoolean(startpref, false))
            return false;
        return needBootWarning(context, bootpref);
    }

    public static boolean needBootWarning(Context context, String bootpref) {
        if (!findPermission(context, Manifest.permission.RECEIVE_BOOT_COMPLETED))
            return false;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        long auto = shared.getLong(bootpref, -1);
        long install = getInstallTime(context);
        if (auto == -1 && install == -1)
            return false; // freshly installed app, never ran before
        long now = System.currentTimeMillis();
        long boot = getBootTime();
        if (install > boot)
            return false; // app was installed after boot
        if (boot + BOOT_DELAY > now) // give 2 minutes to receive boot event
            return false; // 2 minutes maybe not enougth to receive boot event
        return boot > auto; // boot > auto = boot event never received
    }

    public static AlertDialog buildBootWarning(final Context context, final String boot) {
        return new AlertDialog.Builder(context)
                .setMessage("Application never received BOOT event, check if it has been removed from autostart")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        setPrefTime(context, boot, System.currentTimeMillis());
                    }
                }).create();
    }

    public static void setPrefTime(Context context, String pref, long time) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.edit().putLong(pref, time).apply();
    }

    public static <T> Class<T> forceInit(Class<T> klass) {
        try {
            Class.forName(klass.getName(), true, klass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return klass;
    }

    public static class WarningBuilder {
        public String key;
        public Context context;
        public AlertDialog.Builder builder;
        public AlertDialog dialog;
        public SwitchPreferenceCompat icon;
        public PreferenceViewHolder iconHolder;
        public SwitchPreferenceCompat optimization;
        public PreferenceViewHolder optimizationHolder;
        public SwitchPreferenceCompat restricted;
        public PreferenceViewHolder restrictedHolder;
        public Runnable serviceEnable;
        public Runnable serviceDisable;
        public OptimizationPreferenceCompat pref;

        public WarningBuilder(Context context, String key) {
            this.key = key;
            this.context = context;
            this.builder = new AlertDialog.Builder(context);
        }

        public Context getContext() {
            return context;
        }

        public AlertDialog create() {
            dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    WarningBuilder.this.onShow();
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    WarningBuilder.this.onDismiss();
                }
            });
            return dialog;
        }

        public void updateIcon() {
            State state = getState(builder.getContext(), key);
            icon.setChecked(state.icon);
            icon.onBindViewHolder(iconHolder);
        }

        public void updateOptimization() {
            boolean b = isIgnoringBatteryOptimizations(builder.getContext());
            optimization.setChecked(b);
            optimization.onBindViewHolder(optimizationHolder);
        }

        public void setIcon(boolean b) {
            State state;
            if (Build.VERSION.SDK_INT < 23) {
                state = getState23(context, key);
                state.icon = b;
                saveState(context, state, key);
            } else {
                state = getState(context, key);
                state.icon = b;
                saveState(context, state, key);
            }
            updateIcon(); // update icon switch
            context.sendBroadcast(new Intent(ICON_UPDATE));
        }

        public void setService(boolean b) {
            if (b) {
                State23 state = getState23(getContext(), key);
                state.service = true;
                saveState(getContext(), state, key);
                getContext().sendBroadcast(new Intent(SERVICE_UPDATE));
                if (serviceEnable != null)
                    serviceEnable.run();
            } else {
                State23 state = getState23(getContext(), key);
                state.service = false;
                saveState(getContext(), state, key);
                getContext().sendBroadcast(new Intent(SERVICE_UPDATE));
                if (serviceDisable != null)
                    serviceDisable.run();
            }
        }

        public void show() {
            if (dialog == null)
                create();
            dialog.show();
        }

        public void onDismiss() {
            if (pref != null)
                pref.onResume();
        }

        @SuppressLint("RestrictedApi")
        public void onShow() {
            Window w = dialog.getWindow();
            w.setCallback(new WindowCallbackWrapper(w.getCallback()) {
                @SuppressLint("RestrictedApi")
                @Override
                public void onWindowFocusChanged(boolean hasFocus) {
                    super.onWindowFocusChanged(hasFocus);
                    WarningBuilder.this.onWindowFocusChanged(hasFocus);
                }
            });
        }

        public void onWindowFocusChanged(boolean hasFocus) {
            if (ICON) {
                if (Build.VERSION.SDK_INT >= 23 && optimization != null) // call show() not from settings activity
                    updateOptimization();
            }
            if (restricted != null) {
                restricted.setChecked(isBackgroundRestricted(context));
                restricted.onBindViewHolder(restrictedHolder);
            }
        }
    }

    public static class ApplicationReceiver extends BroadcastReceiver {
        public Context context;
        public Class<? extends Service> service;
        public IntentFilter filters = new IntentFilter();

        public ApplicationReceiver(Context context, Class<? extends Service> klass) {
            this.context = context;
            this.service = klass;
            filters.addAction(service.getCanonicalName() + PING);
        }

        public void register() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filters, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(this, filters);
            }
        }

        public void unregister() {
            context.unregisterReceiver(this);
        }

        public void close() {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(service.getCanonicalName() + PING)) {
                Intent pong = new Intent(service.getCanonicalName() + PONG);
                context.sendBroadcast(pong);
            }
        }
    }

    public static class OptimizationReceiver extends BroadcastReceiver {
        public Context context;
        public AlarmManager am;
        public String key;
        public Handler handler = new Handler();
        public Class<? extends Service> service;
        public long next;
        public IntentFilter filters;
        public Runnable check = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(context, service);
                intent.setAction(SERVICE_RESTART);
                OptimizationPreferenceCompat.startService(context, intent);
            }
        };

        public OptimizationReceiver(final Context context, final Class<? extends Service> service, String key) {
            this.key = key;
            this.context = context;
            this.am = new AlarmManager(context);
            this.service = service;
            this.filters = new IntentFilter();
            this.filters.addAction(SERVICE_UPDATE);
            this.filters.addAction(service.getCanonicalName() + PONG);
        }

        public void create() {
            disableKill(context, service);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filters, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(this, filters);
            }
            register();
        }

        public void close() {
            context.unregisterReceiver(this);
            unregister();
        }

        public boolean onStartCommand(Intent intent, int flags, int startId) { // return true if app need to be started
            register();
            if (intent == null)
                return true; // null if service were restarted by system after crash / low memory
            String a = intent.getAction();
            if (a == null)
                return false;
            if (a.equals(SERVICE_CHECK))
                check();
            if (a.equals(SERVICE_RESTART))
                return true;
            return false;
        }

        public void check() { // override when here is ApplicationReceiver and call ping()
        }

        public void ping() {
            handler.postDelayed(check, CHECK_DELAY);
            Intent i = new Intent(service.getCanonicalName() + PING);
            context.sendBroadcast(i);
        }

        public void onTaskRemoved(Intent intent) {
            next = System.currentTimeMillis() + 10 * AlarmManager.SEC1;
            register();
        }

        public boolean isOptimization() {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!isIgnoringBatteryOptimizations(context)) {
                    unregister();
                    return false;
                }
            } else {
                State23 state = getState23(context, key);
                if (!state.service) {
                    unregister();
                    return false;
                }
            }
            return true;
        }

        public void register() {
            if (!isOptimization())
                return;
            next();
            am.set(next, serviceCheck(context, service));
        }

        public void next() {
            long cur = System.currentTimeMillis();
            if (next < cur)
                next = cur + REFRESH;
        }

        public void unregister() {
            am.cancel(serviceCheck(context, service));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(service.getCanonicalName() + PONG))
                handler.removeCallbacks(check);
            if (a.equals(SERVICE_UPDATE))
                register();
        }
    }

    public static class ServiceReceiver extends OptimizationReceiver {
        public String keyNext;
        public OptimizationIcon icon;

        public ServiceReceiver(Service service, int id, String key, String next) {
            super(service, service.getClass(), key);
            this.keyNext = next;
            this.filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
            onCreateIcon(service, id);
        }

        public void onCreateIcon(Service service, int id) {
            icon = new OptimizationIcon(service, id, key) {
                @Override
                public void updateIcon() { // moving updateIcon() to the ServiceReceiver level
                    ServiceReceiver.this.updateIcon();
                }

                @Override
                public Notification build(Intent intent) { // moving build() to the ServiceReceiver level
                    return ServiceReceiver.this.build(intent);
                }
            };
            icon.create();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            String a = intent.getAction();
            if (a != null && a.equals(OptimizationPreferenceCompat.ICON_UPDATE))
                updateIcon();
        }

        public void updateIcon() { // Override with icon.updateIcon(new Intent())
            icon.updateIcon((Intent) null);
        }

        public Notification build(Intent intent) {
            return new PersistentIconBuilder(context).setWhen(icon.notification).create().build();
        }

        @Override
        public boolean isOptimization() {
            return super.isOptimization();
        }

        @Override
        public void register() {
            super.register();
            OptimizationPreferenceCompat.setKillCheck(context, next, keyNext);
        }

        @Override
        public void unregister() {
            super.unregister();
            OptimizationPreferenceCompat.setKillCheck(context, 0, keyNext);
        }

        @Override
        public void close() {
            super.close();
            icon.close();
        }
    }

    public static class SettingsReceiver extends BroadcastReceiver {
        public String key; // "optimization"
        public Intent intent; // start / stop intent
        public IntentFilter filters = new IntentFilter();

        public SettingsReceiver(Intent intent, String key) {
            this.key = key;
            this.intent = intent;
            filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
            filters.addAction(OptimizationPreferenceCompat.SERVICE_UPDATE); // <API23
        }

        public void register(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filters, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(this, filters);
            }
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        public void start(Context context) {
            OptimizationPreferenceCompat.startService(context, intent);
        }

        public void stop(Context context) {
            context.stopService(intent);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            State23 state = OptimizationPreferenceCompat.getState23(context, key);
            if (state.icon || state.service)
                start(context);
            else
                stop(context);
        }
    }

    public static class State23 extends State { // API23<
        public boolean service;

        public State23() {
        }

        public State23(boolean b) {
            service = b;
        }

        public State23(String json) throws JSONException {
            load(json);
        }

        public JSONObject save() throws JSONException {
            JSONObject j = super.save();
            j.put("service", service);
            return j;
        }

        public void load(JSONObject json) throws JSONException {
            super.load(json);
            service = json.optBoolean("service", false);
        }
    }

    public static class State { // state API23+
        public boolean icon;

        public State() {
        }

        public State(String json) throws JSONException {
            load(json);
        }

        public JSONObject save() throws JSONException {
            JSONObject j = new JSONObject();
            j.put("icon", icon);
            return j;
        }

        public void load(JSONObject json) throws JSONException {
            icon = json.optBoolean("icon", false);
        }

        public void load(String json) throws JSONException {
            if (json == null || json.isEmpty())
                return;
            load(new JSONObject(json));
        }
    }

    @SuppressLint("RestrictedApi")
    public static class PersistentIconBuilder extends RemoteNotificationCompat.Low {
        public PersistentIconBuilder(Context context) {
            super(context, R.layout.remoteview);
        }

        public PersistentIconBuilder create() {
            return create(getAppTheme(), getChannelStatus());
        }

        public PersistentIconBuilder create(int theme, NotificationChannelCompat channel) {
            PackageManager pm = getContext().getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(getContext().getPackageName());
            PendingIntent main = PendingIntent.getActivity(getContext(), 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            setTheme(theme)
                    .setChannel(channel)
                    .setImageViewTint(R.id.icon_circle, getThemeColor(androidx.appcompat.R.attr.colorPrimary))
                    .setTitle(AboutPreferenceCompat.getApplicationName(getContext()))
                    .setText(getContext().getString(R.string.optimization_alive))
                    .setMainIntent(main)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_circle);

            return this;
        }

        public PersistentIconBuilder setWhen(Notification n) {
            super.setWhen(n);
            return this;
        }

        public int getAppTheme() {
            return R.style.AppThemeLightLib;
        }

        public NotificationChannelCompat getChannelStatus() {
            return new NotificationChannelCompat(getContext(), "status", "Status", NotificationManagerCompat.IMPORTANCE_LOW);
        }
    }

    public static class PersistentIcon {
        public Context context;
        public Notification notification;
        public int id;

        public PersistentIcon(Context context, int id) {
            this.context = context;
            this.id = id;
        }

        public void create() { // onCreate()
            updateIcon();
        }

        public void close() { // onDestory()
            hideIcon();
        }

        public Notification build(Intent intent) {
            return new PersistentIconBuilder(context).setWhen(notification).create().build();
        }

        public void updateIcon() { // Override with updateIcon(null), to make default persistent service behaviour
            updateIcon(new Intent()); // null == hide
        }

        public boolean isOptimization() { // not showing icon by default, unless config is on or api>26
            return false;
        }

        public void updateIcon(Intent intent) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (intent != null || isOptimization()) {
                Notification n = build(intent);
                if (notification == null) {
                    showIcon(n);
                } else {
                    String co = NotificationChannelCompat.getChannelId(notification);
                    String cn = NotificationChannelCompat.getChannelId(n);
                    if (co == null && cn != null || co != null && cn == null || co != null && cn != null && !co.equals(cn))
                        nm.cancel(id);
                    updateIcon(n);
                }
                notification = n;
            } else {
                hideIcon();
            }
        }

        public void showIcon(Notification n) { // initial showup
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.notify(id, n);
        }

        public void updateIcon(Notification n) { // update previous
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.notify(id, n);
        }

        public void hideIcon() {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancel(id);
            notification = null;
        }
    }

    public static class NotificationIcon extends PersistentIcon {
        public Service context;

        public NotificationIcon(Service context, int id) {
            super(context, id);
            this.context = context;
        }

        @Override
        public void updateIcon() { // Override with updateIcon(null), to make default persistent service behaviour
            updateIcon(new Intent()); // null == hide
        }

        @Override
        public boolean isOptimization() { // not showing icon by default, unless config is on or api>26
            return false;
        }

        @Override
        public void showIcon(Notification n) {
            Log.d(TAG, "startForeground(" + new ComponentName(context, context.getClass()).flattenToShortString() + ")");
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    // specialUse foreground service type requires the 3-arg form (API 29+).
                    // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE (API 34) is a compile-time
                    // constant (0x40000000) — safe at any runtime API level.
                    context.startForeground(id, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    context.startForeground(id, n);
                }
            } catch (RuntimeException e) {
                // ForegroundServiceStartNotAllowedException (API 31+): thrown when the app is not
                // allowed to start a foreground service (e.g. background restriction, policy
                // violation). Stop cleanly instead of crashing the main thread.
                Log.e(TAG, "startForeground failed, stopping service: " + e);
                context.stopSelf();
            }
        }

        @Override
        public void hideIcon() {
            context.stopForeground(true);
            super.hideIcon();
        }
    }

    public static class OptimizationIcon extends NotificationIcon {
        public String key;

        public OptimizationIcon(Service context, int id, String key) {
            super(context, id);
            this.key = key;
        }

        public boolean isOptimization() {
            return OptimizationPreferenceCompat.getState(context, key).icon || Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26;
        }

        @Override
        public void updateIcon() { // Override with updateIcon(new Intent())
            updateIcon((Intent) null); // default: null = hide (service behaviour)
        }
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @TargetApi(21)
    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OptimizationPreferenceCompat(Context context) {
        super(context);
    }

    public void enable(Class<? extends Service> service) {
        this.service = service;
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT < 23) { // 1) devices below 23
            for (Intent intent : ALL) {
                if (isCallable(getContext(), intent)) { // 2) devices in special supported list below 23
                    setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            showWarning(getContext(), getKey()); // show commons
                            return false;
                        }
                    });
                    setVisible(true);
                    return;
                }
            }
            if (service != null) { // 3) apps with service/ping mechanics below 23 getKey() used to store service and icon booleans
                State23 state = getState23(getContext(), getKey());
                setChecked(state.service || state.icon);
                setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        final Runnable enable = new Runnable() {
                            @Override
                            public void run() {
                                setChecked(true);
                            }
                        };
                        Runnable disable = new Runnable() {
                            @Override
                            public void run() {
                                setChecked(false);
                            }
                        };
                        final WarningBuilder builder = buildWarning(getContext(), true, getKey());
                        builder.serviceEnable = enable;
                        builder.serviceDisable = disable;
                        if (ICON) {
                            showWarning(getContext(), builder); // show commons
                            return false;
                        }
                        if (b) {
                            builder.builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    builder.setService(true);
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            builder.setService(false);
                        }
                        return false;
                    }
                });
                setVisible(true);
                return;
            }
            if (ICON) { // 4) apps with persistent icon and no service settings below 23
                State state = getState(getContext(), getKey());
                setChecked(state.icon);
                setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean b = (boolean) newValue;
                        if (b) {
                            WarningBuilder builder = buildWarning(getContext(), true, getKey());
                            builder.builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    State state = getState(getContext(), getKey());
                                    state.icon = true;
                                    saveState(getContext(), state, getKey());
                                    setChecked(true);
                                }
                            });
                            showWarning(getContext(), builder); // show commons
                        } else {
                            State state = getState(getContext(), getKey());
                            state.icon = false;
                            saveState(getContext(), state, getKey());
                            setChecked(false);
                        }
                        return false;
                    }
                });
                setVisible(true);
                return;
            }
            setVisible(false);
        } else { // 5) getKey() icon boolean stored
            boolean b = isIgnoringBatteryOptimizations(getContext());
            if (ICON) {
                State state = getState(getContext(), getKey());
                b |= state.icon;
            }
            setChecked(b);
            setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    WarningBuilder builder = buildWarning(getContext(), !isIgnoringBatteryOptimizations(getContext()), getKey());  // hide commons
                    if (builder != null)
                        builder.pref = OptimizationPreferenceCompat.this;
                    showWarning(getContext(), builder);
                    return false;
                }
            });
        }
    }
}
