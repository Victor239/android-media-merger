package com.github.axet.mover.activities;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.activities.AppCompatThemeActivity;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OpenStorageChoicer;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.mover.R;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.app.Storage;
import com.github.axet.mover.services.MoverService;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeMap;

public class MainActivity extends AppCompatThemeActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int RESULT_ADD_FOLDER = 1;
    public static final int RESULT_SET_FOLDER = 2;
    public static final int RESULT_STORAGE = 3;
    public static final int RESULT_ENABLE = 4;

    ListView list;
    FoldersAdapter adapter;
    View footer;
    View header;
    Storage storage;
    OpenChoicer choicer;

    CameraReceiver receiver = new CameraReceiver();

    public static void startActivity(Context context) {
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    public class FoldersAdapter implements ListAdapter {
        DataSetObserver listener;

        TreeMap<String, Boolean> auto = new TreeMap<>();
        ArrayList<Uri> manual = new ArrayList<>();

        public FoldersAdapter() {
            load();
        }

        public void load() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

            manual.clear();
            int c = shared.getInt(MoverApplication.MANUAL_COUNT, 0);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    String s = shared.getString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, "");
                    Uri u;
                    if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                        u = Uri.parse(s);
                    } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                        u = Uri.parse(s);
                    } else {
                        File f = new File(s);
                        u = Uri.fromFile(f);
                    }
                    manual.add(u);
                }
            }

            auto.clear();
            c = shared.getInt(MoverApplication.AUTO_COUNT, 0);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    String s = shared.getString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, "");
                    Boolean b = shared.getBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, true);
                    auto.put(s, b);
                }
            }

            changed();
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            listener = observer;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            listener = observer;
        }

        @Override
        public int getCount() {
            int count = 0;
            count += auto.size();
            count += manual.size();
            return count;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater i = LayoutInflater.from(MainActivity.this);
                convertView = i.inflate(R.layout.item, parent, false);
            }

            final SwitchCompat enabled = (SwitchCompat) convertView.findViewById(R.id.enabled);
            final TextView path = (TextView) convertView.findViewById(R.id.path);
            View trash = convertView.findViewById(R.id.trash);

            if (position < auto.size()) {
                final String[] keys = auto.keySet().toArray(new String[]{});
                boolean c = auto.get(keys[position]);

                path.setText(keys[position]);
                path.setOnClickListener(null);
                path.setClickable(false);

                trash.setVisibility(View.GONE);

                enabled.setVisibility(View.VISIBLE);
                enabled.setChecked(c);
                enabled.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        auto.put(keys[position], enabled.isChecked());
                        save();
                    }
                });
            } else {
                final int pos = position - auto.size();
                final Uri p = manual.get(pos);

                String n;
                String s = p.getScheme();
                if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    n = Storage.getDisplayName(MainActivity.this, p);
                } else {
                    n = p.getPath();
                }

                path.setText(n);
                path.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String p = storage.getStoragePath();
                        Uri old = storage.getStoragePath(p);
                        choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                            @Override
                            public void onResult(Uri uri) {
                                manual.set(pos, uri);
                                String s = uri.getScheme();
                                if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                                    path.setText(Storage.getDisplayName(MainActivity.this, uri));
                                } else {
                                    path.setText(uri.getPath());
                                }
                                save();
                            }
                        };
                        choicer.setPermissionsDialog(MainActivity.this, MoverService.PERMISSIONS, RESULT_SET_FOLDER);
                        choicer.setStorageAccessFramework(MainActivity.this, RESULT_SET_FOLDER);
                        choicer.show(old);
                    }
                });

                enabled.setVisibility(View.GONE);

                trash.setVisibility(View.VISIBLE);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.delete_folder);
                        builder.setMessage(Storage.getDisplayName(MainActivity.this, p) + "\n\n" + getString(R.string.are_you_sure));
                        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                manual.remove(pos);
                                changed();
                                save();
                            }
                        });
                        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                        builder.show();
                    }
                });
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        public void add(Uri path) {
            manual.add(path);
            changed();
        }

        void changed() {
            if (listener != null)
                listener.onChanged();
        }

        public void save() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SharedPreferences.Editor edit = shared.edit();
            String[] keys = auto.keySet().toArray(new String[]{});
            edit.putInt(MoverApplication.AUTO_COUNT, keys.length);
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                edit.putBoolean(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_ENABLED, auto.get(key));
                edit.putString(MoverApplication.AUTO_PREFIX + i + MoverApplication.AUTO_PATH, key);
            }

            edit.putInt(MoverApplication.MANUAL_COUNT, manual.size());
            for (int i = 0; i < manual.size(); i++)
                edit.putString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, manual.get(i).toString());
            edit.commit();
        }
    }

    public class CameraReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDirs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_ADD_FOLDER:
            case RESULT_SET_FOLDER:
            case RESULT_STORAGE:
            case RESULT_ENABLE:
                choicer.onRequestPermissionsResult(permissions, grantResults);
                break;
        }
    }

    @Override
    public int getAppTheme() {
        return MoverApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        storage = new Storage(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MoverService.STOP);
        filter.addAction(MoverService.UPDATE);
        registerReceiver(receiver, filter);

        list = (ListView) findViewById(R.id.list);

        list.setHeaderDividersEnabled(false);
        list.setFooterDividersEnabled(false);

        header = LayoutInflater.from(this).inflate(R.layout.header, list, false);
        list.addHeaderView(header);

        footer = LayoutInflater.from(this).inflate(R.layout.footer, list, false);
        list.addFooterView(footer);

        adapter = new FoldersAdapter();
        list.setAdapter(adapter);

        View browse = footer.findViewById(R.id.browse);

        browse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String p = storage.getStoragePath();
                Uri old = storage.getStoragePath(p);
                choicer = new OpenStorageChoicer(storage, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false, getString(R.string.folder_name)) {
                    @Override
                    public void onResult(Uri uri) {
                        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor edit = shared.edit();
                        edit.putString(MoverApplication.STORAGE, uri.toString());
                        edit.commit();
                        invalidateOptionsMenu();
                    }

                    @Override
                    public void onRequestPermissionsFailed(String[] permissions) {
                        SettingsActivity.warninig(MainActivity.this); // mandatory permissions, show warning
                    }
                };
                choicer.setTitle(getString(R.string.pref_storage_title));
                choicer.setPermissionsDialog(MainActivity.this, MoverService.PERMISSIONS, RESULT_STORAGE);
                choicer.setStorageAccessFramework(MainActivity.this, RESULT_STORAGE);
                choicer.show(old);
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String p = storage.getStoragePath();
                Uri old = storage.getStoragePath(p);
                choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                    @Override
                    public void onResult(Uri uri) {
                        adapter.add(uri);
                        adapter.save();
                        MoverService.update(MainActivity.this);
                    }
                };
                choicer.setPermissionsDialog(MainActivity.this, MoverService.PERMISSIONS, RESULT_ADD_FOLDER);
                choicer.setStorageAccessFramework(MainActivity.this, RESULT_ADD_FOLDER);
                choicer.show(old);
            }
        });

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        if (OptimizationPreferenceCompat.needKillWarning(this, MoverApplication.PREFERENCE_NEXT))
            OptimizationPreferenceCompat.buildKilledWarning(new ContextThemeWrapper(this, getAppTheme()), true, MoverApplication.PREFERENCE_OPTIMIZATION, MoverService.class).show();
        else if (OptimizationPreferenceCompat.needBootWarning(this, MoverApplication.PREFERENCE_BOOT, MoverApplication.PREFERENCE_INSTALL))
            OptimizationPreferenceCompat.buildBootWarning(this, MoverApplication.PREFERENCE_BOOT).show();

        if (Storage.permitted(this, MoverService.PERMISSIONS))
            MoverService.update(this);
    }

    void updateDirs() {
        adapter.load();

        String p = storage.getStoragePath();
        Uri u = storage.getStoragePath(p);

        boolean enabled = MoverService.isEnabled(this);

        TextView path = (TextView) footer.findViewById(R.id.path);

        if (enabled) {
            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText(R.string.syncing);
        } else {
            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText(R.string.not_syncing);
        }

        String text;
        if (u == null)
            text = getString(R.string.not_selected);
        else
            text = Storage.getDisplayName(this, u);

        path.setText(text);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem menuEnable = menu.findItem(R.id.action_enable);
        menuEnable.setChecked(MoverService.isEnabled(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_about) {
            AboutPreferenceCompat.showDialog(this, R.raw.about);
            return true;
        }

        if (id == R.id.action_enable) {
            final boolean b = !item.isChecked();

            String p = storage.getStoragePath();
            final Uri old = storage.getStoragePath(p);

            final Runnable enabled = new Runnable() {
                @Override
                public void run() {
                    if (MoverService.isEnabled(MainActivity.this, true) || !b) {
                        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean(MoverApplication.ENABLED, b);
                        editor.commit();
                        invalidateOptionsMenu();
                        MoverService.update(MainActivity.this);
                    } else {
                        choicer.show(old);
                    }
                }
            };

            choicer = new OpenStorageChoicer(storage, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false, getString(R.string.folder_name)) {
                @Override
                public void onResult(Uri uri) {
                    final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    SharedPreferences.Editor edit = shared.edit();
                    edit.putString(MoverApplication.STORAGE, uri.toString());
                    edit.commit();
                    item.setChecked(b);
                    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean(MoverApplication.ENABLED, b); // true
                    editor.commit();
                    invalidateOptionsMenu();
                    MoverService.startIfEnabled(MainActivity.this);
                    updateDirs();
                }

                @Override
                public void show(Uri old) {
                    // force show permissions dialog
                    if (a != null) {
                        if (!Storage.permitted(a, perms, permsresult))
                            return; // perms shown
                    }
                    if (f != null) {
                        if (!Storage.permitted(f, perms, permsresult))
                            return; // perms shown
                    }
                    super.show(old);
                }

                @Override
                public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
                    if (Storage.permitted(context, permissions)) {
                        enabled.run();
                    } else {
                        onRequestPermissionsFailed(permissions);
                    }
                }

                @Override
                public void onRequestPermissionsFailed(String[] permissions) {
                    SettingsActivity.warninig(MainActivity.this); // mandatory permissions, show warning
                }
            };
            choicer.setTitle(getString(R.string.pref_storage_title));
            choicer.setPermissionsDialog(MainActivity.this, MoverService.PERMISSIONS, RESULT_ENABLE);
            choicer.setStorageAccessFramework(MainActivity.this, RESULT_ENABLE);
            enabled.run();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MoverService.update(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_SET_FOLDER:
            case RESULT_ADD_FOLDER:
            case RESULT_ENABLE:
            case RESULT_STORAGE:
                choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateDirs();
    }
}
