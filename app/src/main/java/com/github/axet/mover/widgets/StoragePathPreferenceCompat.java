package com.github.axet.mover.widgets;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;

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
    public void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        String v = restoreValue ? getPersistedString(getText()) : (String) defaultValue;
        setText(v);
        if (v.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri uri = Uri.parse(v);
            setSummary(storage.getTargetName(uri));
        } else {
            setSummary(v);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return null; // no default for storage merger
    }
}
