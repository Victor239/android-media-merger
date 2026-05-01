package com.github.victor.mover.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import android.view.MenuItem;

import com.github.axet.androidlibrary.activities.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.preferences.StoragePathPreferenceCompat;
import com.github.victor.mover.R;
import com.github.victor.mover.app.MoverApplication;
import com.github.victor.mover.services.MoverService;
import com.github.axet.androidlibrary.preferences.LegacyStoragePreferenceCompat;

public class SettingsActivity extends AppCompatSettingsThemeActivity {
    public static String[] PERMISSION = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static final int RESULT_PERMS = 1;
    public static final int RESULT_BROWSE = 2;

    public static void warninig(final Context context) {
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
            optimization.enable(MoverService.class);

            // Battery / scheduling: in scheduled mode (live=off) we show the interval
            // and hide the OptimizationPreferenceCompat (only meaningful for live mode).
            // In live mode (live=on) we hide the interval and show OptimizationPreferenceCompat.
            SwitchPreferenceCompat livePref = (SwitchPreferenceCompat) manager.findPreference(MoverApplication.PREFERENCE_LIVE_MODE);
            ListPreference intervalPref = (ListPreference) manager.findPreference(MoverApplication.PREFERENCE_SCHEDULE_INTERVAL);
            if (livePref != null) {
                applyModeVisibility(livePref.isChecked(), intervalPref, optimization);
                livePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        applyModeVisibility(Boolean.TRUE.equals(newValue), intervalPref, optimization);
                        return true;
                    }
                });
            }
        }

        private void applyModeVisibility(boolean liveMode, ListPreference intervalPref, OptimizationPreferenceCompat optimization) {
            if (intervalPref != null)
                intervalPref.setVisible(!liveMode);
            if (optimization != null)
                optimization.setVisible(liveMode);
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
            LegacyStoragePreferenceCompat legacy = (LegacyStoragePreferenceCompat) findPreference(MoverApplication.PREFERENCE_LEGACY);
            legacy.onResume();
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
                    if (!Storage.permitted(getContext(), PERMISSION))
                        warninig(getContext());
                    else
                        c.onRequestPermissionsResult(permissions, grantResults);
                    MoverService.update(getContext());
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

    @Override
    public int getAppTheme() {
        // NoActionBar variants — SettingsActivity supplies its own Toolbar via
        // R.layout.activity_settings, so the window must not have a decor action bar.
        return MoverApplication.getTheme(this,
                R.style.AppThemeLight_NoActionBar,
                R.style.AppThemeDark_NoActionBar,
                R.style.AppThemeDarkBlack_NoActionBar);
    }

    @Override
    public String getAppThemeKey() {
        return MoverApplication.PREFERENCE_THEME;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use an explicit layout with Toolbar + container so the preference list isn't
        // overlayed by the action bar. The previous replace(android.R.id.content, ...)
        // approach left the recycler view starting at y=below-status-bar, with the
        // action bar drawn on top of its first ~130px — clipping the first row.
        setContentView(R.layout.activity_settings);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new PrefFragment())
                    .commit();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(MoverApplication.STORAGE) || key.startsWith(MoverApplication.AUTO_PREFIX) || key.startsWith(MoverApplication.MANUAL_PREFIX))
            MoverService.update(this);
        if (MoverApplication.PREFERENCE_LIVE_MODE.equals(key) || MoverApplication.PREFERENCE_SCHEDULE_INTERVAL.equals(key)) {
            // Reschedule / switch FGS lifecycle as needed.
            sendBroadcast(new Intent(this,
                    com.github.victor.mover.services.SyncTriggerReceiver.class)
                    .setAction(com.github.victor.mover.services.SyncTriggerReceiver.ACTION_MODE_CHANGED));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
