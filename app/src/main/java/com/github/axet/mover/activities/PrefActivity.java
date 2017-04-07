package com.github.axet.mover.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;

import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.R;
import com.github.axet.mover.services.FileObserverService;

import java.io.File;

public class PrefActivity extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    static void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    static void updatePrefSummary(Preference pref) {
        if (pref instanceof EditTextPreference) {
            EditTextPreference listPref = (EditTextPreference) pref;
            String s = listPref.getText();
            if (s == null || s.isEmpty())
                s = "(not set)";
            pref.setSummary(s);
        }
    }

    static void initPrefs(final PreferenceManager manager, PreferenceScreen screen) {
        final Context context = screen.getContext();
        final PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
        final String n = context.getPackageName();
        Preference optimization = manager.findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
        if (Build.VERSION.SDK_INT < 23) {
            screen.removePreference(optimization);
        } else {
            SwitchPreference p = (SwitchPreference) optimization;
            p.setChecked(pm.isIgnoringBatteryOptimizations(n));
            p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (pm.isIgnoringBatteryOptimizations(n)) {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        context.startActivity(intent);
                    } else {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + n));
                        context.startActivity(intent);
                    }
                    return false;
                }
            });
        }

        initSummary(screen);

        final EditTextPreference p = (EditTextPreference) manager.findPreference("storage");
        if (p.getText() == null) {
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    p.getEditText().setText(new File(Environment.getExternalStorageDirectory(), "/private/mobile").getPath());
                    return true;
                }
            });
        }
    }

    @TargetApi(11)
    public static class PrefFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            initPrefs(getPreferenceManager(), getPreferenceScreen());
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePrefSummary(findPreference(key));
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            final PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            final String n =getActivity().getPackageName();
            if (Build.VERSION.SDK_INT >= 23) {
                SwitchPreference optimization = (SwitchPreference) findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
                if (optimization != null) {
                    optimization.setChecked(pm.isIgnoringBatteryOptimizations(n));
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < 11) {
            addPreferencesFromResource(R.xml.prefs);
            initPrefs(getPreferenceManager(), getPreferenceScreen());
        } else {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PrefFragment())
                    .commit();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        FileObserverService.update(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final String n = getPackageName();
        if (Build.VERSION.SDK_INT >= 23) {
            SwitchPreference optimization = (SwitchPreference) findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
            if (optimization != null) {
                optimization.setChecked(pm.isIgnoringBatteryOptimizations(n));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }
}
