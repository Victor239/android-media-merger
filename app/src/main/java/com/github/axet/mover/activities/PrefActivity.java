package com.github.axet.mover.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.mover.R;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.services.FileObserverService;

import java.io.File;

public class PrefActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

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
        OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) manager.findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
        optimization.onResume();

        initSummary(screen);

        final EditTextPreference p = (EditTextPreference) manager.findPreference("storage");
        if (p.getText() == null) {
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    p.setText(new File(Environment.getExternalStorageDirectory(), "/private/mobile").getPath());
                    return true;
                }
            });
        }
    }

    public static class PrefFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
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
            OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
            optimization.onResume();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefFragment())
                .commit();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }
}
