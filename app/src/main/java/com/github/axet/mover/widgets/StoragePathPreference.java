package com.github.axet.mover.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;

import java.io.File;

public class StoragePathPreference extends com.github.axet.androidlibrary.widgets.StoragePathPreference {
    public StoragePathPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoragePathPreference(Context context) {
        this(context, null);
    }

    @Override
    public String getDefault() {
        return new File(Environment.getExternalStorageDirectory(), def == null ? "" : def).getPath();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        def = a.getString(index);
        // we need no default value, user has to specify it's own
        return null;
    }

}
