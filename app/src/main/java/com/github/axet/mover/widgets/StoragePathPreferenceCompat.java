package com.github.axet.mover.widgets;

import android.content.Context;
import android.content.res.TypedArray;
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
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        defaultValue = restoreValue ? getPersistedString(getText()) : (String) defaultValue;
        setText((String) defaultValue);
        setSummary((String) defaultValue);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return null; // no default for storage merger
    }
}
