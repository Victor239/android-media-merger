package com.github.axet.androidlibrary.preferences;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OpenStorageChoicer;

import java.io.File;

public class StoragePathPreferenceCompat extends EditTextPreference {
    public String def;
    public Storage storage = new Storage(getContext());
    public OpenStorageChoicer choicer;
    public View.OnLongClickListener clickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            return StoragePathPreferenceCompat.this.onLongClick();
        }
    };

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public StoragePathPreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
        choicer = new OpenStorageChoicer(storage, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
            @Override
            public void onResult(Uri uri) {
                String s;
                if (uri == null)
                    s = new File(StoragePathPreference.getDefault(), def).getAbsolutePath();
                else
                    s = uri.toString();
                if (callChangeListener(s))
                    setText(s);
            }
        };
        choicer.def = def;
        choicer.setTitle(getTitle().toString());
        choicer.setContext(getContext());
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnLongClickListener(clickListener);
    }

    @Override
    public void onClick() {
        onClickDialog();
    }

    public boolean onLongClick() {
        return false;
    }

    public void onClickDialog() {
        String f = StoragePathPreference.getPath(this);
        Uri u = storage.getStoragePath(f);
        choicer.show(u);
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        updatePath((String) newValue);
        return super.callChangeListener(newValue);
    }

    // load default value for sharedpropertiesmanager, or set it using xml.
    //
    // can't set dynamic values like '/sdcard'? he-he. so that what it for.
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        def = a.getString(index);
        File path = new File(StoragePathPreference.getDefault(), def);
        return path.getPath();
    }

    public void updatePath(String path) {
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = storage.getStoragePath(path);
            String n = Storage.getDisplayName(getContext(), u); // can be null
            setSummary(n);
            return;
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            Uri u = Uri.parse(path);
            f = Storage.getFile(u);
        } else {
            f = new File(path);
        }
        File p = storage.getStoragePath(f);
        String s = "";
        if (p != null) // support for 'not selected'
            s = p.toString();
        setSummary(s);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        String f = StoragePathPreference.getPath(this);
        updatePath(f);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    public void setPermissionsDialog(Fragment f, String[] ss, int code) {
        choicer.setPermissionsDialog(f, ss, code);
    }

    public void setPermissionsDialog(Activity a, String[] ss, int code) {
        choicer.setPermissionsDialog(a, ss, code);
    }

    public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        String f = StoragePathPreference.getPath(this);
        updatePath(f); // permission has changed, visible path may change
        choicer.onRequestPermissionsResult(permissions, grantResults);
    }

    public void setStorageAccessFramework(Activity a, int code) {
        choicer.setStorageAccessFramework(a, code);
    }

    public void setStorageAccessFramework(Fragment f, int code) {
        choicer.setStorageAccessFramework(f, code);
    }

    @TargetApi(19)
    public void onActivityResult(int resultCode, Intent data) {
        choicer.onActivityResult(resultCode, data);
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
        choicer.setStorage(storage);
    }
}
