package com.github.axet.mover.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.mover.app.MoverApplication;
import com.github.axet.mover.R;
import com.github.axet.mover.services.FileObserverService;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    ListView list;
    FoldersAdapter adapter;
    View footer;
    View header;

    CameraReceiver reciver = new CameraReceiver();

    public class FoldersAdapter implements ListAdapter {
        DataSetObserver listener;

        TreeMap<String, Boolean> auto = new TreeMap<>();
        ArrayList<String> manual = new ArrayList<>();

        public FoldersAdapter() {
            load();
        }

        public void load() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

            manual.clear();
            int c = shared.getInt(MoverApplication.MANUAL_COUNT, 0);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    manual.add(shared.getString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, ""));
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
                final String p = manual.get(pos);

                path.setText(p);
                path.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final OpenFileDialog f = new OpenFileDialog(MainActivity.this);
                        f.setCurrentPath(new File(p));
                        f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                File ff = f.getCurrentPath();
                                String fileName = ff.getPath();
                                if (!ff.isDirectory())
                                    fileName = ff.getParent();
                                manual.set(pos, fileName);
                                path.setText(fileName);
                                save();
                            }
                        });
                        f.show();
                    }
                });

                enabled.setVisibility(View.GONE);

                trash.setVisibility(View.VISIBLE);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Delete folder");
                        builder.setMessage(p + "\n\nAre you sure?");
                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                manual.remove(pos);
                                changed();
                                save();
                            }
                        });
                        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
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

        public void add(String path) {
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
                edit.putString(MoverApplication.MANUAL_PREFIX + i + MoverApplication.MANUAL_PATH, manual.get(i));
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
            case 1:
                FileObserverService.update(this);
        }
    }

    // check if we have to ask for permission, then do not start service yet
    boolean permitted() {
        String[] ss = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, ss, 1);
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

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
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                String path = sharedPref.getString(MoverApplication.STORAGE, null);

                if (path == null) {
                    File ff = new File(Environment.getExternalStorageDirectory(), "/private/mobile");
                    if (!ff.exists())
                        ff = Environment.getExternalStorageDirectory();
                    path = ff.getPath();
                }

                f.setSelectFiles(false);
                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File ff = f.getCurrentPath();
                        String fileName = ff.getPath();
                        if (!ff.isDirectory())
                            fileName = ff.getParent();
                        SharedPreferences.Editor edit = sharedPref.edit();
                        edit.putString(MoverApplication.STORAGE, fileName);
                        edit.commit();
                    }
                });
                f.show();
            }
        });

        if (permitted()) {
            FileObserverService.update(this);
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);
                f.setCurrentPath(Environment.getExternalStorageDirectory());
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File ff = f.getCurrentPath();
                        String fileName = ff.getPath();
                        if (!ff.isDirectory())
                            fileName = ff.getParent();
                        adapter.add(fileName);
                        adapter.save();
                    }
                });
                f.show();
            }
        });

//        Snackbar.make(view, "Syncing", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    void updateDirs() {
        adapter.load();

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String to = shared.getString(MoverApplication.STORAGE, null);

        TextView path = (TextView) footer.findViewById(R.id.path);

        if (to == null) {
            to = "(not selected)";

            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText("Not Synching!\n\nPlease select 'storage_path' with 'Browse' button");
        } else {
            TextView text = (TextView) header.findViewById(R.id.path);
            text.setText("Synching...");
        }

        path.setText(to);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, PrefActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.mover/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.mover/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FileObserverService.update(this);
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
