package com.github.axet.mover.widgets;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.widgets.StoragePathPreference;

import java.io.File;

public class StoragePathPreferenceCompat extends com.github.axet.androidlibrary.widgets.StoragePathPreferenceCompat {
    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoragePathPreferenceCompat(Context context) {
        super(context);
    }

    @Override
    public void onClick() {
        super.onClick();
    }

    @Override
    public void onSetInitialValue(boolean restoreValue, Object defaultValue) { // allow to show null
        String v = restoreValue ? getPersistedString(getText()) : (String) defaultValue;
        Uri u = storage.getStoragePath(v);
        if (u != null) {
            setSummary(storage.getDisplayName(u));
        }
    }

    @Override
    public Object onGetDefaultValue(TypedArray a, int index) {
        super.onGetDefaultValue(a, index);
        return null; // no default for storage merger
    }
}
