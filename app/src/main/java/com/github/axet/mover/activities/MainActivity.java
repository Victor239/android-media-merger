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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.mover.R;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.app.Storage;
import com.github.axet.mover.services.FileObserverService;
import com.github.axet.mover.widgets.StoragePathPreferenceCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int RESULT_PERMS = 1;
    public static final int RESULT_STORAGE = 2;
    public static final int RESULT_BROWSE = 3;
    public static final int RESULT_BROWSE_SET = 4;

    ListView list;
    FoldersAdapter adapter;
    View footer;
    View header;
    Storage storage;
    int browseSetPos;
    TextView browseSetPath;

    CameraReceiver reciver = new CameraReceiver();

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
                    n = storage.getDisplayName(p);
                } else {
                    n = p.getPath();
                }

                path.setText(n);
                path.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String pp = storage.getStoragePath();
                        if (Build.VERSION.SDK_INT >= 21 && StoragePathPreferenceCompat.showStorageAccessFramework(MainActivity.this, pp, FileObserverService.PERMISSIONS)) {
                            showBrowseFolder(RESULT_BROWSE_SET);
                            browseSetPos = pos;
                            browseSetPath = path;
                        } else {
                            final OpenFileDialog f = new OpenFileDialog(MainActivity.this, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);
                            f.setCurrentPath(new File(p.getPath()));
                            f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    File ff = f.getCurrentPath();
                                    manual.set(pos, Uri.fromFile(ff));
                                    path.setText(ff.getPath());
                                    save();
                                }
                            });
                            f.show();
                        }
                    }
                });

                enabled.setVisibility(View.GONE);

                trash.setVisibility(View.VISIBLE);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.delete_folder);
                        builder.setMessage(storage.getDisplayName(p) + "\n\n" + getString(R.string.are_you_sure));
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
            for (int i = 0; i < manual.size(); i++) {
                edit.putString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, manual.get(i).toString());
            }
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
            case RESULT_PERMS:
                if (Storage.permitted(this, Storage.PERMISSIONS))
                    showBrowseStorage();
                else
                    SettingsActivity.warninig(this);
                FileObserverService.update(this);
                break;
            case RESULT_STORAGE:
                if (Storage.permitted(this, FileObserverService.PERMISSIONS)) {
                    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean(MoverApplication.ENABLED, FileObserverService.isEnabled(this, true));
                    editor.commit();
                    invalidateOptionsMenu();
                    FileObserverService.startIfEnabled(this);
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        storage = new Storage(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(FileObserverService.STOP);
        filter.addAction(FileObserverService.UPDATE);
        registerReceiver(reciver, filter);

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
                if (!Storage.permitted(MainActivity.this, FileObserverService.PERMISSIONS, RESULT_PERMS)) { // we need for Camera folders
                    return;
                }
                showBrowseStorage();
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String path = storage.getStoragePath();
                if (Build.VERSION.SDK_INT >= 21 && StoragePathPreferenceCompat.showStorageAccessFramework(MainActivity.this, path, FileObserverService.PERMISSIONS)) {
                    showBrowseFolder(RESULT_BROWSE);
                } else {
                    if (!Storage.permitted(MainActivity.this, FileObserverService.PERMISSIONS, RESULT_PERMS)) { // we need permissions for custom paths, even with SAF
                        return;
                    }
                    final OpenFileDialog f = new OpenFileDialog(MainActivity.this, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);
                    f.setCurrentPath(Environment.getExternalStorageDirectory());
                    f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            File ff = f.getCurrentPath();
                            adapter.add(Uri.fromFile(ff));
                            adapter.save();
                        }
                    });
                    f.show();
                }
            }
        });

//        Snackbar.make(view, "Syncing", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        if (OptimizationPreferenceCompat.needWarning(this))
            OptimizationPreferenceCompat.showWarning(this);

        if (Storage.permitted(this, FileObserverService.PERMISSIONS)) {
            FileObserverService.update(this);
        }
    }

    void showBrowseFolder(int i) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, i);
    }

    void showBrowseStorage() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        StoragePathPreferenceCompat c = new StoragePathPreferenceCompat(this);
        c.setStorage(storage);
        c.setPermissionsDialog(this, FileObserverService.PERMISSIONS, RESULT_PERMS);
        c.setStorageAccessFramework(this, RESULT_STORAGE);
        c.onSetInitialValue(false, shared.getString(MoverApplication.STORAGE, null));
        c.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MoverApplication.STORAGE, (String) newValue);
                edit.commit();
                return false;
            }
        });
        c.onClick();
    }

    void updateDirs() {
        adapter.load();

        String p = storage.getStoragePath();
        Uri u = storage.getStoragePath(p);

        boolean enabled = FileObserverService.isEnabled(this);

        TextView path = (TextView) footer.findViewById(R.id.path);

        if (enabled) {
            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText(R.string.sycing);
        } else {
            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText(R.string.not_syncing);
        }

        String text;
        if (u == null) {
            text = getString(R.string.not_selected);
        } else {
            text = storage.getDisplayName(u);
        }

        path.setText(text);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem menuEnable = menu.findItem(R.id.action_enable);
        menuEnable.setChecked(FileObserverService.isEnabled(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
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
            boolean b = !item.isChecked();
            if (!Storage.permitted(this, FileObserverService.PERMISSIONS, RESULT_STORAGE))
                b = false;
            if (!FileObserverService.isEnabled(this, b))
                b = false;
            item.setChecked(b);
            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(MoverApplication.ENABLED, b);
            editor.commit();
            if (b) {
                FileObserverService.startIfEnabled(this);
            }
            updateDirs();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FileObserverService.update(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        switch (requestCode) {
            case RESULT_BROWSE_SET:
                if (resultCode == RESULT_OK) {
                    Uri u = data.getData();
                    if (Build.VERSION.SDK_INT >= 21) {
                        ContentResolver resolver = getContentResolver();
                        resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                    adapter.manual.set(browseSetPos, u);
                    String s = u.getScheme();
                    if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        browseSetPath.setText(storage.getDisplayName(u));
                    } else {
                        browseSetPath.setText(u.getPath());
                    }
                    adapter.save();
                }
                break;
            case RESULT_BROWSE:
                if (resultCode == RESULT_OK) {
                    Uri u = data.getData();
                    if (Build.VERSION.SDK_INT >= 21) {
                        ContentResolver resolver = getContentResolver();
                        resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                    adapter.add(u);
                    adapter.save();
                }
                break;
            case RESULT_STORAGE:
                if (resultCode == RESULT_OK) {
                    SharedPreferences.Editor edit = sharedPref.edit();
                    edit.putString(MoverApplication.STORAGE, data.getData().toString());
                    edit.commit();
                    invalidateOptionsMenu();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        if (reciver != null) {
            unregisterReceiver(reciver);
            reciver = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateDirs();
    }
}
