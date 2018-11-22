package com.github.axet.mover.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.view.MenuItem;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.StoragePathPreferenceCompat;
import com.github.axet.mover.R;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.services.FileObserverService;
import com.github.axet.mover.widgets.NameFormatPreferenceCompat;

public class SettingsActivity extends AppCompatSettingsThemeActivity implements PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {

    public static String[] PERMISSION = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static final int RESULT_PERMS = 1;
    public static final int RESULT_BROWSE = 2;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (preference instanceof NameFormatPreferenceCompat) {
                preference.setSummary(((NameFormatPreferenceCompat) preference).getFormatted(stringValue));
            } else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragmentCompat caller, Preference pref) {
        if (pref instanceof NameFormatPreferenceCompat) {
            NameFormatPreferenceCompat.show(caller, pref.getKey());
            return true;
        }
        return false;
    }

    public static class PrefFragment extends PreferenceFragmentCompat {
        void initPrefs(final PreferenceManager manager, PreferenceScreen screen) {
            bindPreferenceSummaryToValue(manager.findPreference(MoverApplication.PREFERENCE_NAME));
            bindPreferenceSummaryToValue(manager.findPreference(MoverApplication.PREFERENCE_THEME));

            setHasOptionsMenu(true);

            StoragePathPreferenceCompat c = (StoragePathPreferenceCompat) findPreference(MoverApplication.STORAGE);
            c.setPermissionsDialog(this, PERMISSION, RESULT_PERMS);
            if (Build.VERSION.SDK_INT >= 21)
                c.setStorageAccessFramework(this, RESULT_BROWSE);

            OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) manager.findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
            optimization.enable(FileObserverService.class);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.prefs);
            initPrefs(getPreferenceManager(), getPreferenceScreen());
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public void onResume() {
            super.onResume();
            OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) findPreference(MoverApplication.PREFERENCE_OPTIMIZATION);
            optimization.onResume();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            StoragePathPreferenceCompat c = (StoragePathPreferenceCompat) findPreference(MoverApplication.STORAGE);

            switch (requestCode) {
                case RESULT_PERMS:
                    if (!Storage.permitted(getContext(), PERMISSION)) {
                        warninig(getContext());
                    } else {
                        c.onRequestPermissionsResult(permissions, grantResults);
                    }
                    FileObserverService.update(getContext());
                    break;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            StoragePathPreferenceCompat c = (StoragePathPreferenceCompat) findPreference(MoverApplication.STORAGE);

            switch (requestCode) {
                case RESULT_BROWSE:
                    c.onActivityResult(resultCode, data);
                    break;
            }
        }
    }

    static void warninig(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.permission_title);
        builder.setMessage(R.string.permission_message);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Storage.showPermissions(context);
            }
        });
        builder.show();
    }

    @Override
    public int getAppTheme() {
        return MoverApplication.getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark);
    }

    @Override
    public String getAppThemeKey() {
        return MoverApplication.PREFERENCE_THEME;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefFragment())
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        FileObserverService.update(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void onStart() {
        super.onStart();
    }

    @Override
    public void onBackPressed() {
        finish();
        MainActivity.startActivity(this);
    }
}
