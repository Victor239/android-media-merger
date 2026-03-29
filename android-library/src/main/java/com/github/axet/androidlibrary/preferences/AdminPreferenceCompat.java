package com.github.axet.androidlibrary.preferences;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.core.widget.TextViewCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.services.DeviceAdmin;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

public class AdminPreferenceCompat extends SwitchPreferenceCompat {
    public static final String TAG = AdminPreferenceCompat.class.getSimpleName();

    public enum Installer {
        ADB, // installed using 'adb'
        STORE, // installed from goole play store
        APK, // installed using android install apk dialog
        UNKNOWN
    }

    public static String TITLE = "Enable device admin access";
    public static String ENABLED = "(Device Owner enabled)";
    public static String MISSING = "MISSING";
    public static String VENDING = "com.android.vending";
    public static String PACKAGEINSTALLER = "com.android.packageinstaller";

    public static final String ERASE_ALL_DATA = "wipe-data";
    public static final String LOCK_SCREEN = "force-lock";

    public static final String USES_POLICIES = "uses-policies";

    public static boolean STORE_ONLY_DISCLOSURE = true; // show additional dialog only for google play users = true.

    public Activity a;
    public Fragment f;
    public int code;
    public String m; // description
    public String[] mm; // messages

    public static ActivityInfo findReceiver(Context context, String permission) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_RECEIVERS);
            for (ActivityInfo i : info.receivers) {
                if (i.permission != null && i.permission.contains(permission))
                    return i;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "unable to find receiver", e);
        }
        return null;
    }

    public static Installer getInstaller(Context context) {
        PackageManager pm = context.getPackageManager();
        String installer = pm.getInstallerPackageName(context.getPackageName());
        if (installer == null)
            return Installer.ADB;
        if (installer.startsWith(PACKAGEINSTALLER))
            return Installer.APK;
        if (installer.startsWith(VENDING))
            return Installer.STORE;
        return Installer.UNKNOWN;
    }

    @TargetApi(21)
    public AdminPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        onResume();
    }

    @TargetApi(21)
    public AdminPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        onResume();
    }

    public AdminPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        onResume();
    }

    public AdminPreferenceCompat(Context context) {
        super(context);
        onResume();
    }

    public void setActivity(Activity a, int code) {
        this.a = a;
        this.code = code;
    }

    public void setFragment(Fragment f, int code) {
        this.f = f;
        this.code = code;
    }

    public void setMessages(String m, String[] mm) {
        this.m = m;
        this.mm = mm;
    }

    public void setMessages(String m, Object... oo) {
        this.m = m;

        ArrayList<String> items = new ArrayList<>();
        try {
            ComponentName c = new ComponentName(getContext(), DeviceAdmin.class);
            ActivityInfo ai = getContext().getPackageManager().getReceiverInfo(c, PackageManager.GET_META_DATA);
            int res = (int) ai.metaData.get(DeviceAdmin.DEVICE_ADMIN);
            XmlPullParser xpp = getContext().getResources().getXml(res);
            int level = 0;
            int usesPolicies = 0;
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    if (usesPolicies == 1 && level == 2)
                        items.add(xpp.getName());
                    if (xpp.getName().equals(USES_POLICIES))
                        usesPolicies++;
                    level++;
                }
                if (xpp.getEventType() == XmlPullParser.END_TAG) {
                    if (xpp.getName().equals(USES_POLICIES))
                        usesPolicies--;
                    level--;
                }
                xpp.next();
            }
        } catch (PackageManager.NameNotFoundException | IOException | XmlPullParserException e) {
            Log.d(TAG, "Unable to read meta", e);
        }

        ArrayList<String> mm = new ArrayList<>();
        for (int i = 0; i < oo.length; i += 2) {
            String k = (String) oo[i];
            Object v = oo[i + 1];
            mm.add(k);
            if (v instanceof Integer)
                mm.add(getContext().getString((int) v));
            if (v instanceof String)
                mm.add((String) v);
            items.remove(k);
        }
        for (String k : items) {
            mm.add(k);
            mm.add(MISSING);
        }
        this.mm = mm.toArray(new String[]{});
    }

    public void setMessages(int m, Object... oo) {
        setMessages(getContext().getString(m), oo);
    }

    public void onResume() {
        if (findReceiver(getContext(), Manifest.permission.BIND_DEVICE_ADMIN) == null) {
            setVisible(false);
            return;
        }
        updateAdmin();
        setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                Context context = getContext();
                boolean b = (boolean) o;
                if (b) {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName c = new ComponentName(context, DeviceAdmin.class);
                    if (!dpm.isAdminActive(c)) {
                        if (Build.VERSION.SDK_INT >= 18) {
                            if (dpm.isDeviceOwnerApp(context.getPackageName())) // already device owner exit
                                return true; // allow change
                        }
                        requestAdmin();
                        return false; // cancel change
                    }
                } else {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName c = new ComponentName(context, DeviceAdmin.class);
                    if (dpm.isAdminActive(c)) {
                        dpm.removeActiveAdmin(c);
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        if (dpm.isDeviceOwnerApp(context.getPackageName())) { // device owner can changed
                            DeviceAdmin.removeDeviceOwner(context);
                        }
                    }
                    updateAdminSummary(); // update summary
                }
                return true; // allow change
            }
        });
    }

    public void updateAdminSummary() {
        Context context = getContext();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        String summary = TITLE;

        if (Build.VERSION.SDK_INT >= 18) {
            if (dpm.isDeviceOwnerApp(context.getPackageName())) { // device owner can't cahnge
                summary += " " + ENABLED;
            }
        }

        setSummary(summary);
    }

    public void updateAdmin() {
        Context context = getContext();

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName c = new ComponentName(context, DeviceAdmin.class);

        if (isChecked()) {
            boolean b = dpm.isAdminActive(c);
            if (Build.VERSION.SDK_INT >= 24) {
                b |= dpm.isDeviceOwnerApp(context.getPackageName());
            }
            setChecked(b);
        }

        updateAdminSummary();
    }

    public boolean requestAdmin() {
        Context context = getContext();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final ComponentName c = new ComponentName(context, DeviceAdmin.class);
        if (!dpm.isAdminActive(c)) {
            final Runnable run = new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, c);
                    if (a != null)
                        a.startActivityForResult(intent, code);
                    if (f != null)
                        f.startActivityForResult(intent, code);
                }
            };
            if (mm != null && (!STORE_ONLY_DISCLOSURE || getInstaller(context) == Installer.STORE)) {
                int p5 = ThemeUtils.dp2px(context, 5);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(TITLE);
                LinearLayout ll = new LinearLayout(context);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setPadding(ThemeUtils.dp2px(context, 20), p5, p5, p5);
                if (m != null) {
                    TextView message = new TextView(context);
                    TextViewCompat.setTextAppearance(message, androidx.appcompat.R.style.TextAppearance_AppCompat_Body1);
                    message.setText(m);
                    ll.addView(message);
                }
                for (int i = 0; i < mm.length; i += 2) {
                    LinearLayout llp = new LinearLayout(context);
                    llp.setOrientation(LinearLayout.VERTICAL);
                    llp.setPadding(ThemeUtils.dp2px(context, 30), p5, p5, p5);
                    TextView title = new TextView(context);
                    TextViewCompat.setTextAppearance(title, androidx.appcompat.R.style.TextAppearance_AppCompat_Subhead);
                    title.setText(mm[i]);
                    title.setTypeface(null, Typeface.BOLD);
                    llp.addView(title);
                    TextView message = new TextView(context);
                    TextViewCompat.setTextAppearance(message, androidx.appcompat.R.style.TextAppearance_AppCompat_Body1);
                    message.setText(mm[i + 1]);
                    llp.addView(message);
                    ll.addView(llp);
                }
                builder.setView(ll);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        run.run();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
                return true;
            } else {
                run.run();
            }
            return true;
        }
        return false;
    }

    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) // 0 - cancel, -1 - ok
            setChecked(true);
    }
}
