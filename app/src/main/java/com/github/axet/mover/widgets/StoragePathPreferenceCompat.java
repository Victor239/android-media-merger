package com.github.axet.mover.widgets;

import android.content.Context;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;

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
    public OpenFileDialog createDialog() {
        return new OpenFileDialogSuperUser(getContext(), OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);
    }
}
