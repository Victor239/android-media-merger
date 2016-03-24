package com.github.axet.mover.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.R;
import com.github.axet.mover.services.FileObserverService;

import java.io.File;

public class PrefActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static class PrefFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);

            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            initSummary(getPreferenceScreen());

            final EditTextPreference p = (EditTextPreference) findPreference("storage");
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

        private void initSummary(Preference p) {
            if (p instanceof PreferenceGroup) {
                PreferenceGroup pGrp = (PreferenceGroup) p;
                for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                    initSummary(pGrp.getPreference(i));
                }
            } else {
                updatePrefSummary(p);
            }
        }

        private void updatePrefSummary(Preference pref) {
            if (pref instanceof EditTextPreference) {
                EditTextPreference listPref = (EditTextPreference) pref;
                String s = listPref.getText();
                if (s == null || s.isEmpty())
                    s = "(not set)";

                pref.setSummary(s);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePrefSummary(findPreference(key));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefFragment())
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        FileObserverService.start(this);
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
